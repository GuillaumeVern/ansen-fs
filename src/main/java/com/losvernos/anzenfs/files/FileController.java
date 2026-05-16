package com.losvernos.anzenfs.files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
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
      ResourceAndName downloadInfo = fileService.prepareDownload(externalId);
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

}
