package com.losvernos.anzenfs.jobs;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.losvernos.anzenfs.files.FileService;

@RestController
@RequestMapping("/api/files/jobs")
public class JobController {
  @Autowired
  private FileService fileService;

  @Autowired
  private JobService jobService;

  @PostMapping("/new")
  @PreAuthorize("@fsSecurity.hasAccess(#request.parentUuid() != null ? #request.parentUuid() : 'root-uuid', 'WRITE')")
  public ResponseEntity<?> createJob(@RequestBody UploadJobCreationRequest request) {

    var jobId = jobService.createUploadJob(request.parentUuid(), request.manifest());
    return ResponseEntity.ok(Map.of("jobId", jobId));
  }

  @PostMapping("/{jobId}/upload")
  @PreAuthorize("@fsSecurity.hasAccess(#parentUuid != null ? #parentUuid : 'root-uuid', 'WRITE')")
  public ResponseEntity<?> uploadFile(
      @PathVariable String jobId,
      @RequestParam("files") MultipartFile[] files,
      @RequestParam("parentUuid") String parentUuid) {

    fileService.processFolderUpload(jobId, parentUuid, files);
    return ResponseEntity.accepted().build();
  }

}
