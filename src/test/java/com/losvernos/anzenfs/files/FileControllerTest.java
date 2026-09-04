package com.losvernos.anzenfs.files;

import com.losvernos.anzenfs.rbac.role.Role;
import com.losvernos.anzenfs.rbac.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerTest {

    @Mock
    private FileService fileService;

    private MockMvc mockMvc;
    private AutoCloseable mocks;
    private final User user = User.builder().username("alice").userRoles(List.of(new Role(1L, "USER_ROLE", null))).build();

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        FileController controller = new FileController();
        ReflectionTestUtils.setField(controller, "fileService", fileService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void getHomeFolderReturnsServiceResult() throws Exception {
        when(fileService.getHomeFolder(user)).thenReturn(new FileNode("home-uuid", null, "alice", FileType.FOLDER, null, 0L));

        mockMvc.perform(get("/api/files/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value("home-uuid"))
                .andExpect(jsonPath("$.name").value("alice"));
    }

    @Test
    void scrollDirectoryDelegatesToService() throws Exception {
        when(fileService.getChildrenAfter(eq("folder-uuid"), isNull(), eq(50), eq(user)))
                .thenReturn(List.of(new FileNode("f1", "folder-uuid", "a.txt", FileType.TEXT, null, 0L)));

        mockMvc.perform(get("/api/files").param("parentUuid", "folder-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uuid").value("f1"));
    }

    @Test
    void downloadFileReturnsResourceWithContentDisposition(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("report.txt");
        Files.writeString(file, "hello", StandardCharsets.UTF_8);
        when(fileService.getResourceAndName("doc-uuid"))
                .thenReturn(new ResourceAndName(new FileSystemResource(file), "report.txt"));

        mockMvc.perform(get("/api/files/download/doc-uuid"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"report.txt\""))
                .andExpect(content().bytes("hello".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void downloadFileReturns404WhenMissing() throws Exception {
        when(fileService.getResourceAndName("missing")).thenThrow(new FileNotFoundException("nope"));

        mockMvc.perform(get("/api/files/download/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadFileReturns500OnUnexpectedError() throws Exception {
        when(fileService.getResourceAndName("boom")).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/files/download/boom"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void previewFileReturnsResourceForImage(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("pic.jpg");
        Files.writeString(file, "content", StandardCharsets.UTF_8);
        when(fileService.getPreviewResource("pic-uuid"))
                .thenReturn(new ResourceAndName(new FileSystemResource(file), "pic.jpg"));

        mockMvc.perform(get("/api/files/preview/pic-uuid"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline"))
                .andExpect(header().string("Content-Type", "image/jpeg"));
    }

    @Test
    void previewFileHonorsRangeHeaderWithPartialContent(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("clip.mp4");
        Files.writeString(file, "0123456789", StandardCharsets.UTF_8);
        when(fileService.getPreviewResource("clip-uuid"))
                .thenReturn(new ResourceAndName(new FileSystemResource(file), "clip.mp4"));

        mockMvc.perform(get("/api/files/preview/clip-uuid").header("Range", "bytes=0-3"))
                .andExpect(status().isPartialContent())
                .andExpect(content().bytes("0123".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void previewFileReturns404WhenMissing() throws Exception {
        when(fileService.getPreviewResource("missing")).thenThrow(new FileNotFoundException("nope"));

        mockMvc.perform(get("/api/files/preview/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteFileReturnsOkWhenDeleted() throws Exception {
        when(fileService.softDeleteItem("doc-uuid")).thenReturn(true);

        mockMvc.perform(delete("/api/files/doc-uuid"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteFileReturns404WhenNotFound() throws Exception {
        when(fileService.softDeleteItem("missing")).thenReturn(false);

        mockMvc.perform(delete("/api/files/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteFileReturns500OnException() throws Exception {
        when(fileService.softDeleteItem("boom")).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(delete("/api/files/boom"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void listTrashReturnsServiceResult() throws Exception {
        when(fileService.getTrashedItems(user)).thenReturn(
                List.of(new TrashedFileNode("doc-uuid", "report.txt", FileType.TEXT, 10L, "alice", "2026-01-01 00:00:00")));

        mockMvc.perform(get("/api/files/trash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uuid").value("doc-uuid"));
    }

    @Test
    void restoreFromTrashReturnsOkWhenRestored() throws Exception {
        when(fileService.restoreItem("doc-uuid")).thenReturn(true);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/files/trash/doc-uuid/restore"))
                .andExpect(status().isOk());
    }

    @Test
    void restoreFromTrashReturns404WhenNotFound() throws Exception {
        when(fileService.restoreItem("missing")).thenReturn(false);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/files/trash/missing/restore"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePermanentlyReturnsOkWhenDeleted() throws Exception {
        when(fileService.permanentlyDeleteItemByExternalId("doc-uuid")).thenReturn(true);

        mockMvc.perform(delete("/api/files/trash/doc-uuid"))
                .andExpect(status().isOk());
    }

    @Test
    void deletePermanentlyReturns404WhenNotFound() throws Exception {
        when(fileService.permanentlyDeleteItemByExternalId("missing")).thenReturn(false);

        mockMvc.perform(delete("/api/files/trash/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadAcceptsFilesAndProcessesInBackground() throws Exception {
        MockMultipartFile part = new MockMultipartFile("files", "a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/files/upload").file(part).param("parentUuid", "folder-uuid"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.totalFiles").value(1))
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        verify(fileService, timeout(2000)).processFolderUpload(anyString(), eq("folder-uuid"), any());
    }
}
