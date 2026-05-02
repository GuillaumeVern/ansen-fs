package com.losvernos.anzenfs.jobs;

import java.util.List;

public record UploadJobCreationRequest(
    String parentUuid,
    int totalFiles,
    List<String> manifest) {
}
