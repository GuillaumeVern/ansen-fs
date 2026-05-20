package com.losvernos.anzenfs.files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.losvernos.anzenfs.jobs.UploadJobSummary;

@RestController
@RequestMapping("/api/files")
public class FileController {

  @Autowired
  private FileService fileService;

  private final Path stagingDir = new File(FileUtils.getDataDir(), "staging").toPath();

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<UploadJobSummary> upload(
      @RequestParam(required = false) String parentUuid,
      @RequestPart("files") MultipartFile[] files) throws IOException {

    String jobId = java.util.UUID.randomUUID().toString();
    long totalBytes = java.util.Arrays.stream(files)
        .mapToLong(MultipartFile::getSize)
        .sum();

    Files.createDirectories(stagingDir);

    for (int i = 0; i < files.length; i++) {
      Path dest = stagingDir.resolve("file_" + i);
      files[i].transferTo(dest);
    }

    Thread.ofVirtual().start(() -> {
      fileService.processFolderUpload(jobId, parentUuid, files);
      FileUtils.deleteDirectory(stagingDir);
    });

    UploadJobSummary summary = new UploadJobSummary(
        jobId,
        files.length,
        0,
        totalBytes,
        0L,
        "PROCESSING");

    return ResponseEntity.status(HttpStatus.ACCEPTED).body(summary);
  }

  @GetMapping("")
  public ResponseEntity<List<FileNode>> scrollDirectory(
      @RequestParam(required = false) String parentUuid,
      @RequestParam(required = false) String lastFileName,
      @RequestParam(defaultValue = "50") int size) {

    return ResponseEntity.ok(fileService.getChildrenAfter(parentUuid, lastFileName, size));
  }

  @GetMapping("/download/{externalId}")
  public ResponseEntity<Resource> downloadFile(@PathVariable String externalId) {
    try {
      ResourceAndName downloadInfo = fileService.getResourceAndName(externalId);
      long contentLength = downloadInfo.resource().contentLength();

      return ResponseEntity.ok()
              .contentType(MediaType.APPLICATION_OCTET_STREAM)
              .contentLength(contentLength)
              .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadInfo.fileName() + "\"")
              .body(downloadInfo.resource());

    } catch (FileNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @GetMapping("/preview/{externalId}")
  public ResponseEntity<Resource> getFilePreview(@PathVariable String externalId) {
    try {
      ResourceAndName fileInfo = fileService.getResourceAndName(externalId);
      Path originalPath = Paths.get(fileInfo.resource().getURI());

      String contentType = Files.probeContentType(originalPath);
      if (contentType == null) {
        contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
      }

      Path previewPath = resolvePreviewMirrorPath(originalPath);

      Resource resource = new UrlResource(previewPath.toUri());

      return ResponseEntity.ok()
              .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
              .contentType(MediaType.parseMediaType(contentType))
              .contentLength(resource.contentLength())
              .body(resource);

    } catch (FileNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @DeleteMapping("/{externalId}")
  public ResponseEntity<String> deleteFileOrFolder(@PathVariable String externalId) {
    try {
      boolean isDeleted = fileService.deleteItemByExternalId(externalId);

      if (!isDeleted) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Item with ID " + externalId + " not found.");
      }

      return ResponseEntity
              .ok("Item successfully deleted.");

    } catch (Exception e) {
      return ResponseEntity
              .status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body("An error occurred while deleting the item: " + e.getMessage());
    }
  }

  private Path resolvePreviewMirrorPath(Path originalPath) {
    return originalPath;
  }

}
