package com.losvernos.anzenfs.files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.losvernos.anzenfs.rbac.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
  @PreAuthorize("@fsSecurity.hasAccess(#parentUuid != null ? #parentUuid : 'root-uuid', 'WRITE')")
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

  @GetMapping("/home")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<FileNode> getHomeFolder(@AuthenticationPrincipal User currentUser) {
    return ResponseEntity.ok(fileService.getHomeFolder(currentUser));
  }

  @GetMapping("")
  @PreAuthorize("@fsSecurity.hasAccess(#parentUuid != null ? #parentUuid : 'root-uuid', 'READ')")
  public ResponseEntity<List<FileNode>> scrollDirectory(
      @RequestParam(required = false) String parentUuid,
      @RequestParam(required = false) String lastFileName,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal User currentUser) {

    return ResponseEntity.ok(fileService.getChildrenAfter(parentUuid, lastFileName, size, currentUser));
  }

  @GetMapping("/download/{externalId}")
  @PreAuthorize("@fsSecurity.hasAccess(#externalId, 'READ')")
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
  @PreAuthorize("@fsSecurity.hasAccess(#externalId, 'READ')")
  public ResponseEntity<ResourceRegion> getFilePreview(
      @PathVariable String externalId,
      @RequestHeader HttpHeaders headers) {
    try {
      ResourceAndName previewInfo = fileService.getPreviewResource(externalId);
      Resource resource = previewInfo.resource();
      long contentLength = resource.contentLength();

      MediaType mediaType = MediaTypeFactory.getMediaType(previewInfo.fileName())
              .orElse(MediaType.APPLICATION_OCTET_STREAM);

      List<HttpRange> ranges = headers.getRange();
      ResourceRegion region;
      HttpStatus status;

      if (ranges.isEmpty()) {
        region = new ResourceRegion(resource, 0, contentLength);
        status = HttpStatus.OK;
      } else {
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);
        region = new ResourceRegion(resource, start, end - start + 1);
        status = HttpStatus.PARTIAL_CONTENT;
      }

      return ResponseEntity.status(status)
              .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
              .contentType(mediaType)
              .body(region);

    } catch (FileNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @DeleteMapping("/{externalId}")
  @PreAuthorize("@fsSecurity.hasAccess(#externalId, 'WRITE')")
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

}
