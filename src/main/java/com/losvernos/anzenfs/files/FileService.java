package com.losvernos.anzenfs.files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.losvernos.anzenfs.files.preview.PreviewThumbnailGeneratorRegistry;
import com.losvernos.anzenfs.rbac.role.Role;
import com.losvernos.anzenfs.rbac.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
  @Autowired
  private FileRepository fileRepository;

  @Autowired
  private PreviewThumbnailGeneratorRegistry thumbnailGeneratorRegistry;

  private final Path storageRoot = new File(FileUtils.getDataDir(), "data").toPath();

  private final Path stagingDir = new File(FileUtils.getDataDir(), "staging").toPath();

  private final Path thumbnailRoot = new File(FileUtils.getDataDir(), "thumbnails").toPath();

  public FileNode getHomeFolder(User currentUser) {
    boolean isAdmin = currentUser.getUserRoles() != null
            && currentUser.getUserRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("ADMIN"));

    if (isAdmin) {
      return new FileNode("root-uuid", null, "root", FileType.FOLDER, null, 0L);
    }

    Integer rootId = fileRepository.findIdByNameAndParent("root", null)
            .orElseThrow(() -> new IllegalStateException("System root folder missing"));

    return fileRepository.findByNameAndParent(currentUser.getUsername(), rootId)
            .orElseThrow(() -> new IllegalStateException("Home folder missing for user " + currentUser.getUsername()));
  }

  public ResourceAndName getResourceAndName(String externalId) throws FileNotFoundException, MalformedURLException {
    String relativePath = fileRepository.getFullPath(externalId);

    if (relativePath == null) {
      throw new FileNotFoundException("File record not found in database.");
    }

    Path physicalPath = storageRoot.resolve(relativePath).normalize();

    if (!Files.exists(physicalPath) || Files.isDirectory(physicalPath)) {
      throw new FileNotFoundException("File does not exist on the filesystem.");
    }

    Resource resource = new UrlResource(physicalPath.toUri());
    String fileName = physicalPath.getFileName().toString();

    return new ResourceAndName(resource, fileName);
  }

  /**
   * Resolves the resource to show for an in-app preview. For types with a generated
   * thumbnail (currently video), this serves the cached thumbnail image instead of the
   * original file; everything else falls through to the original file, same as download.
   */
  public ResourceAndName getPreviewResource(String externalId) throws FileNotFoundException, MalformedURLException {
    ResourceAndName original = getResourceAndName(externalId);
    FileType type = FileType.fromFilename(original.fileName());

    if (thumbnailGeneratorRegistry.forType(type).isPresent()) {
      Path thumbnailPath = thumbnailPathFor(externalId);
      if (!Files.exists(thumbnailPath)) {
        throw new FileNotFoundException("No thumbnail available for: " + externalId);
      }
      return new ResourceAndName(new UrlResource(thumbnailPath.toUri()), externalId + ".jpg");
    }

    return original;
  }

  private Path thumbnailPathFor(String externalId) {
    return thumbnailRoot.resolve(externalId + ".jpg");
  }

  public void processFolderUpload(String jobId, String rootParentUuid, MultipartFile[] files) {
    for (MultipartFile file : files) {
      try {
        String originalName = file.getOriginalFilename();
        if (originalName == null) continue;

        Path incomingPath = Path.of(originalName);
        if (incomingPath.isAbsolute()) {
          incomingPath = incomingPath.getRoot().relativize(incomingPath);
        }

        Integer rootParentId = fileRepository.findIdByUuid(rootParentUuid).orElse(null);
        Integer folderId = resolveFolderHierarchy(rootParentId, incomingPath);

        String ancestorPath = fileRepository.getFullPathById(folderId);
        Path destinationDir = ancestorPath.isEmpty() ? storageRoot : storageRoot.resolve(ancestorPath);

        String fileName = incomingPath.getFileName().toString();
        Path targetLocation = destinationDir.resolve(fileName).normalize();
        if (!targetLocation.startsWith(storageRoot)) {
          throw new SecurityException("Escape attempt detected: " + incomingPath);
        }

        Files.createDirectories(targetLocation.getParent());
        file.transferTo(targetLocation.toFile());

        String hash = generateHeuristicHash(targetLocation);
        FileType fileType = FileType.fromFilename(fileName);
        long fileSize = Files.size(targetLocation);
        String externalId = fileRepository.insertFile(folderId, fileName, fileType, hash, fileSize);

        thumbnailGeneratorRegistry.forType(fileType)
            .ifPresent(generator -> generator.generate(targetLocation, thumbnailPathFor(externalId)));

      } catch (Exception e) {
        System.err.println(e);
      }
    }
  }

  public List<FileNode> getChildrenAfter(String parentUuid, String lastFileName, int limit, User currentUser) {
    Integer parentId = null;

    if (parentUuid != null && !Objects.equals(parentUuid, "")) {
      parentId = fileRepository.findIdByUuid(parentUuid).orElseThrow(() -> new RuntimeException("Folder not found"));
    }

    List<Role> roles = currentUser.getUserRoles() != null
            ? currentUser.getUserRoles()
            : List.of();

    List<FileNode> children = fileRepository.getChildrenAfter(parentId, lastFileName, parentUuid, limit, roles);

    return children.stream()
        .map(node -> node.type() == FileType.FOLDER
            ? new FileNode(node.uuid(), node.parentUuid(), node.name(), node.type(), node.hash(),
                fileRepository.getFolderSize(node.uuid()))
            : node)
        .toList();
  }

  private Integer resolveFolderHierarchy(Integer rootId, Path path) {
    path = path.getParent();
    if (null == path)
      return rootId;

    Integer currentParentId = rootId;

    for (Path part : path) {
      String folderName = part.toString();

      Optional<Integer> existing = fileRepository.findIdByNameAndParent(folderName, currentParentId);

      if (existing.isPresent()) {
        currentParentId = existing.get();
      } else {
        currentParentId = fileRepository.createFolder(currentParentId, folderName);
      }
    }

    return currentParentId;
  }

  private String generateHeuristicHash(Path fullPath) throws IOException {
    try {
      String name = storageRoot.relativize(fullPath).toString();
      long size = Files.size(fullPath);
      long timestamp = Files.getLastModifiedTime(fullPath).toMillis();

      String rawInput = String.format("%s:%d:%d", name, size, timestamp);

      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] encodedHash = digest.digest(rawInput.getBytes(StandardCharsets.UTF_8));

      return HexFormat.of().formatHex(encodedHash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not found", e);
    }
  }

  public boolean deleteItemByExternalId(String externalId) {
    String primaryRelativePath = fileRepository.getFullPath(externalId);
    if (primaryRelativePath == null) {
      return false;
    }

    List<String[]> targets = fileRepository.deleteItemAndGetDescendantPaths(externalId);
    if (targets.isEmpty()) {
      return false;
    }

    for (String[] metadata : targets) {
      String itemExternalId = metadata[0];
      String itemType = metadata[2];

      try {
        if (!FileType.FOLDER.name().equals(itemType)) {
          String fileRelativePath = fileRepository.getFullPath(itemExternalId);

          if (fileRelativePath == null) {
            fileRelativePath = primaryRelativePath;
          }

          Path physicalFilePath = storageRoot.resolve(fileRelativePath).normalize();
          if (Files.exists(physicalFilePath) && !Files.isDirectory(physicalFilePath)) {
            Files.delete(physicalFilePath);
          }

          Files.deleteIfExists(thumbnailPathFor(itemExternalId));
        }
      } catch (IOException e) {
        System.err.println("Failed to delete physical file asset: " + e.getMessage());
      }
    }

    try {
      Path rootTargetFolder = storageRoot.resolve(primaryRelativePath).normalize();
      if (Files.exists(rootTargetFolder) && Files.isDirectory(rootTargetFolder)) {
        try (var walk = Files.walk(rootTargetFolder)) {
          walk.sorted(java.util.Comparator.reverseOrder())
                  .map(Path::toFile)
                  .forEach(File::delete);
        }
      } else if (Files.exists(rootTargetFolder)) {
        Files.delete(rootTargetFolder);
      }
    } catch (IOException e) {
      System.err.println("Error cleaning filesystem directory structures: " + e.getMessage());
    }

    return true;
  }
}
