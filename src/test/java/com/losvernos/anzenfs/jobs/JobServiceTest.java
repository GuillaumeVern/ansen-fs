package com.losvernos.anzenfs.jobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    private JobService jobService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        jobService = new JobService();
        ReflectionTestUtils.setField(jobService, "jobRepository", jobRepository);
    }

    @Test
    void createUploadJobGeneratesIdAndPersistsManifestSize() {
        String jobId = jobService.createUploadJob("parent-uuid", List.of("a.txt", "b.txt", "c.txt"));

        assertThat(jobId).isNotBlank();
        verify(jobRepository).insertJob(jobId, "parent-uuid", 3, "UPLOAD");
    }

    @Test
    void createUploadJobHandlesEmptyManifest() {
        String jobId = jobService.createUploadJob("parent-uuid", List.of());

        verify(jobRepository).insertJob(jobId, "parent-uuid", 0, "UPLOAD");
    }
}
