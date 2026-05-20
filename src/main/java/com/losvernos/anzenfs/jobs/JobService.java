package com.losvernos.anzenfs.jobs;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JobService {

  @Autowired
  private JobRepository jobRepository;

  public String createUploadJob(String parentUuid, List<String> manifest) {
    String jobId = UUID.randomUUID().toString();
    var totalFiles = manifest.size();

    //TODO: handle manifest

    jobRepository.insertJob(jobId, parentUuid, totalFiles, "UPLOAD");

    return jobId;
  }
}
