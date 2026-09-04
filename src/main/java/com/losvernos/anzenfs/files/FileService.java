package com.losvernos.anzenfs.files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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

    return ensureFolderPath(rootId, segmentsOf(path));
  }

  /**
   * Walks (creating as needed) a chain of folder names under {@code rootId}, returning the id of
   * the final folder in the chain. Shared by regular multi-file upload (which only needs to
   * create a file's ancestor folders, cf. {@link #resolveFolderHierarchy}) and WebDAV MKCOL
   * (which creates the target collection itself, cf. {@link #createCollection}).
   */
  private Integer ensureFolderPath(Integer rootId, List<String> segments) {
    Integer currentParentId = rootId;

    for (String folderName : segments) {
      Optional<Integer> existing = fileRepository.findIdByNameAndParent(folderName, currentParentId);

      if (existing.isPresent()) {
        currentParentId = existing.get();
      } else {
        currentParentId = fileRepository.createFolder(currentParentId, folderName);
      }
    }

    return currentParentId;
  }

  private List<String> segmentsOf(Path path) {
    List<String> segments = new ArrayList<>();
    for (Path part : path) {
      segments.add(part.toString());
    }
    return segments;
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

  // --- WebDAV ---

  private static final String WEBDAV_ROOT_FOLDER_NAME = "Photos";

  /**
   * The WebDAV mount point for a given user: a dedicated subfolder of their home folder, created
   * on first use. Kept separate from the home folder itself so an auto-sync client never sees or
   * writes into the user's regular files, and so its content doesn't clutter their normal browsing.
   */
  public FileNode getOrCreateWebDavRoot(User currentUser) {
    Integer homeId = resolveHomeFolderId(currentUser);

    return fileRepository.findByNameAndParent(WEBDAV_ROOT_FOLDER_NAME, homeId)
        .orElseGet(() -> {
          fileRepository.createFolder(homeId, WEBDAV_ROOT_FOLDER_NAME);
          return fileRepository.findByNameAndParent(WEBDAV_ROOT_FOLDER_NAME, homeId)
              .orElseThrow(() -> new IllegalStateException("Failed to create WebDAV root folder"));
        });
  }

  private Integer resolveHomeFolderId(User currentUser) {
    boolean isAdmin = currentUser.getUserRoles() != null
            && currentUser.getUserRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("ADMIN"));

    Integer rootId = fileRepository.findIdByNameAndParent("root", null)
            .orElseThrow(() -> new IllegalStateException("System root folder missing"));

    if (isAdmin) {
      return rootId;
    }

    return fileRepository.findIdByNameAndParent(currentUser.getUsername(), rootId)
            .orElseThrow(() -> new IllegalStateException("Home folder missing for user " + currentUser.getUsername()));
  }

  /**
   * Resolves a slash-separated path relative to a WebDAV root (e.g. {@code "2026/09/img.jpg"})
   * without creating anything - used by GET/PROPFIND/DELETE, which must fail on a missing
   * resource rather than materialize it. An empty/blank path resolves to the root itself.
   */
  public Optional<FileNode> resolveNode(FileNode webDavRoot, String relativePath) {
    if (relativePath == null || relativePath.isBlank()) {
      return Optional.of(webDavRoot);
    }

    List<String> segments = segmentsOf(Path.of(relativePath));
    Integer rootId = requireFolderId(webDavRoot);

    Integer currentParentId = rootId;
    for (int i = 0; i < segments.size() - 1; i++) {
      Optional<Integer> next = fileRepository.findIdByNameAndParent(segments.get(i), currentParentId);
      if (next.isEmpty()) {
        return Optional.empty();
      }
      currentParentId = next.get();
    }

    return fileRepository.findByNameAndParent(segments.get(segments.size() - 1), currentParentId);
  }

  /**
   * WebDAV MKCOL: creates the collection at {@code relativePath} (and any missing ancestor
   * collections along the way, more lenient than strict RFC 4918 but harmless for a client we
   * control). Returns {@code false} without creating anything if the collection already exists,
   * matching MKCOL's "405 if it already exists" semantics.
   */
  public boolean createCollection(FileNode webDavRoot, String relativePath) {
    List<String> segments = segmentsOf(Path.of(relativePath));
    String targetName = segments.get(segments.size() - 1);
    List<String> parentSegments = segments.subList(0, segments.size() - 1);

    Integer parentId = ensureFolderPath(requireFolderId(webDavRoot), parentSegments);

    if (fileRepository.findIdByNameAndParent(targetName, parentId).isPresent()) {
      return false;
    }

    fileRepository.createFolder(parentId, targetName);
    return true;
  }

  /**
   * WebDAV PUT: stores {@code content} at {@code relativePath} under {@code webDavRoot},
   * creating missing ancestor folders as needed (same folder-creation behavior as regular
   * multi-file upload). A pre-existing file at that path is overwritten in place - same
   * external_id, refreshed hash/size - rather than duplicated, matching PUT's replace semantics.
   */
  public FileNode putFile(FileNode webDavRoot, String relativePath, InputStream content) throws IOException {
    Path incomingPath = Path.of(relativePath);
    Integer folderId = resolveFolderHierarchy(requireFolderId(webDavRoot), incomingPath);

    String ancestorPath = fileRepository.getFullPathById(folderId);
    Path destinationDir = ancestorPath.isEmpty() ? storageRoot : storageRoot.resolve(ancestorPath);

    String fileName = incomingPath.getFileName().toString();
    Path targetLocation = destinationDir.resolve(fileName).normalize();
    if (!targetLocation.startsWith(storageRoot)) {
      throw new SecurityException("Escape attempt detected: " + relativePath);
    }

    Files.createDirectories(targetLocation.getParent());
    Files.copy(content, targetLocation, StandardCopyOption.REPLACE_EXISTING);

    String hash = generateHeuristicHash(targetLocation);
    FileType fileType = FileType.fromFilename(fileName);
    long fileSize = Files.size(targetLocation);

    Optional<FileNode> existing = fileRepository.findByNameAndParent(fileName, folderId);
    String externalId;
    if (existing.isPresent()) {
      externalId = existing.get().uuid();
      fileRepository.updateFileContent(externalId, hash, fileSize);
    } else {
      externalId = fileRepository.insertFile(folderId, fileName, fileType, hash, fileSize);
    }

    thumbnailGeneratorRegistry.forType(fileType)
        .ifPresent(generator -> generator.generate(targetLocation, thumbnailPathFor(externalId)));

    return new FileNode(externalId, webDavRoot.uuid(), fileName, fileType, hash, fileSize);
  }

  private Integer requireFolderId(FileNode folder) {
    return fileRepository.findIdByUuid(folder.uuid())
        .orElseThrow(() -> new IllegalStateException("WebDAV folder missing in database: " + folder.uuid()));
  }

  /** Moves an item into the bin. It stays in the database (and on disk) until restored or permanently deleted. */
  public boolean softDeleteItem(String externalId) {
    return fileRepository.softDeleteItem(externalId);
  }

  /** Moves an item out of the bin, back to where it was before it was deleted. */
  public boolean restoreItem(String externalId) {
    return fileRepository.restoreItem(externalId);
  }

  /**
   * Lists everything currently in the bin. Admins see every deleted item; everyone else only
   * sees items that were under their own home folder, mirroring the ownership boundary used
   * for normal browsing.
   */
  public List<TrashedFileNode> getTrashedItems(User currentUser) {
    boolean isAdmin = currentUser.getUserRoles() != null
            && currentUser.getUserRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("ADMIN"));

    String homePathPrefix = null;
    if (!isAdmin) {
      String homePath = fileRepository.getFullPath(getHomeFolder(currentUser).uuid());
      homePathPrefix = (homePath == null ? "" : homePath) + "/";
    }

    List<TrashedFileNode> result = new ArrayList<>();
    for (FileRepository.TrashRow row : fileRepository.findDeletedItems()) {
      String fullPath = fileRepository.getFullPath(row.externalId());
      if (fullPath == null) continue;
      if (homePathPrefix != null && !fullPath.startsWith(homePathPrefix)) continue;

      String originalPath = fullPath.contains("/") ? fullPath.substring(0, fullPath.lastIndexOf('/')) : "";
      long size = row.type() == FileType.FOLDER ? fileRepository.getFolderSize(row.externalId()) : row.size();

      result.add(new TrashedFileNode(row.externalId(), row.name(), row.type(), size, originalPath, row.deletedAt()));
    }

    return result;
  }

  /** Permanently removes an item (and, for a folder, everything under it) from the database and disk. */
  public boolean permanentlyDeleteItemByExternalId(String externalId) {
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
