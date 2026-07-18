package com.losvernos.anzenfs.jobs;

import com.losvernos.anzenfs.files.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobControllerTest {

    @Mock
    private FileService fileService;
    @Mock
    private JobService jobService;

    private MockMvc mockMvc;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        JobController controller = new JobController();
        ReflectionTestUtils.setField(controller, "fileService", fileService);
        ReflectionTestUtils.setField(controller, "jobService", jobService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createJobReturnsGeneratedJobId() throws Exception {
        when(jobService.createUploadJob(eq("parent-uuid"), eq(List.of("a.txt", "b.txt"))))
                .thenReturn("job-123");

        String body = new ObjectMapper().writeValueAsString(
                new UploadJobCreationRequest("parent-uuid", 2, List.of("a.txt", "b.txt")));

        mockMvc.perform(post("/api/files/jobs/new")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"));
    }

    @Test
    void uploadFileForJobDelegatesToFileServiceSynchronously() throws Exception {
        MockMultipartFile part = new MockMultipartFile("files", "a.txt", "text/plain", "hi".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/files/jobs/job-1/upload")
                        .file(part)
                        .param("parentUuid", "folder-uuid"))
                .andExpect(status().isAccepted());

        verify(fileService).processFolderUpload(eq("job-1"), eq("folder-uuid"), any());
    }
}
