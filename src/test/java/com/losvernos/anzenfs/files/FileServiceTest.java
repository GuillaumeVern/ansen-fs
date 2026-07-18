package com.losvernos.anzenfs.files;

import com.losvernos.anzenfs.rbac.role.Role;
import com.losvernos.anzenfs.rbac.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    private FileService fileService;
    private Path storageRoot;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        fileService = new FileService();
        ReflectionTestUtils.setField(fileService, "fileRepository", fileRepository);

        storageRoot = tempDir.resolve("data");
        ReflectionTestUtils.setField(fileService, "storageRoot", storageRoot);
    }

    // --- getHomeFolder ---

    @Test
    void getHomeFolderReturnsSyntheticRootForAdmin() {
        Role admin = new Role(1L, "ADMIN", null);
        User user = User.builder().username("root").userRoles(List.of(admin)).build();

        FileNode home = fileService.getHomeFolder(user);

        assertThat(home).isEqualTo(new FileNode("root-uuid", null, "root", "FOLDER", null, 0L));
    }

    @Test
    void getHomeFolderLooksUpPersonalFolderForRegularUser() {
        Role userRole = new Role(2L, "USER_ROLE", null);
        User user = User.builder().username("alice").userRoles(List.of(userRole)).build();

        when(fileRepository.findIdByNameAndParent("root", null)).thenReturn(Optional.of(10));
        FileNode expected = new FileNode("alice-uuid", "root-uuid", "alice", "FOLDER", null, 0L);
        when(fileRepository.findByNameAndParent("alice", 10)).thenReturn(Optional.of(expected));

        assertThat(fileService.getHomeFolder(user)).isEqualTo(expected);
    }

    @Test
    void getHomeFolderThrowsWhenRootFolderMissing() {
        User user = User.builder().username("alice").userRoles(List.of()).build();
        when(fileRepository.findIdByNameAndParent("root", null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getHomeFolder(user))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getHomeFolderThrowsWhenPersonalFolderMissing() {
        User user = User.builder().username("alice").userRoles(List.of()).build();
        when(fileRepository.findIdByNameAndParent("root", null)).thenReturn(Optional.of(10));
        when(fileRepository.findByNameAndParent("alice", 10)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getHomeFolder(user))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- getResourceAndName ---

    @Test
    void getResourceAndNameThrowsWhenNoDbRecord() {
        when(fileRepository.getFullPath("missing-uuid")).thenReturn(null);

        assertThatThrownBy(() -> fileService.getResourceAndName("missing-uuid"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("not found in database");
    }

    @Test
    void getResourceAndNameThrowsWhenPhysicalFileAbsent() {
        when(fileRepository.getFullPath("ghost-uuid")).thenReturn("root/alice/ghost.txt");

        assertThatThrownBy(() -> fileService.getResourceAndName("ghost-uuid"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("does not exist on the filesystem");
    }

    @Test
    void getResourceAndNameThrowsWhenPathIsDirectory() throws Exception {
        Files.createDirectories(storageRoot.resolve("root/alice/folder"));
        when(fileRepository.getFullPath("folder-uuid")).thenReturn("root/alice/folder");

        assertThatThrownBy(() -> fileService.getResourceAndName("folder-uuid"))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void getResourceAndNameReturnsResourceForExistingFile() throws Exception {
        Path filePath = storageRoot.resolve("root/alice/report.txt");
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "hello world", StandardCharsets.UTF_8);

        when(fileRepository.getFullPath("report-uuid")).thenReturn("root/alice/report.txt");

        ResourceAndName result = fileService.getResourceAndName("report-uuid");

        assertThat(result.fileName()).isEqualTo("report.txt");
        assertThat(result.resource().getContentAsString(StandardCharsets.UTF_8)).isEqualTo("hello world");
    }

    // --- processFolderUpload ---

    @Test
    void processFolderUploadStoresFlatFileAtParentAncestorPath() throws Exception {
        when(fileRepository.findIdByUuid("home-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.getFullPathById(5)).thenReturn("root/alice");

        MultipartFile file = new MockMultipartFile("files", "photo.jpg", "image/jpeg", "binarydata".getBytes());

        fileService.processFolderUpload("job-1", "home-uuid", new MultipartFile[]{file});

        Path expected = storageRoot.resolve("root/alice/photo.jpg");
        assertThat(Files.exists(expected)).isTrue();
        assertThat(Files.readString(expected)).isEqualTo("binarydata");

        verify(fileRepository).insertFile(eq(5), eq("photo.jpg"), eq("FILE"), anyString());
    }

    @Test
    void processFolderUploadCreatesMissingSubfolderHierarchy() throws Exception {
        when(fileRepository.findIdByUuid("home-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.findIdByNameAndParent("sub", 5)).thenReturn(Optional.empty());
        when(fileRepository.createFolder(5, "sub")).thenReturn(42);
        when(fileRepository.getFullPathById(42)).thenReturn("root/alice/sub");

        MultipartFile file = new MockMultipartFile("files", "sub/nested.txt", "text/plain", "nested".getBytes());

        fileService.processFolderUpload("job-2", "home-uuid", new MultipartFile[]{file});

        Path expected = storageRoot.resolve("root/alice/sub/nested.txt");
        assertThat(Files.exists(expected)).isTrue();

        verify(fileRepository).createFolder(5, "sub");
        verify(fileRepository).insertFile(eq(42), eq("nested.txt"), eq("FILE"), anyString());
    }

    @Test
    void processFolderUploadReusesExistingSubfolder() throws Exception {
        when(fileRepository.findIdByUuid("home-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.findIdByNameAndParent("sub", 5)).thenReturn(Optional.of(42));
        when(fileRepository.getFullPathById(42)).thenReturn("root/alice/sub");

        MultipartFile file = new MockMultipartFile("files", "sub/nested.txt", "text/plain", "nested".getBytes());

        fileService.processFolderUpload("job-3", "home-uuid", new MultipartFile[]{file});

        verify(fileRepository, never()).createFolder(any(), any());
        verify(fileRepository).insertFile(eq(42), eq("nested.txt"), eq("FILE"), anyString());
    }

    @Test
    void processFolderUploadRejectsPathEscapingStorageRoot() {
        when(fileRepository.findIdByUuid("home-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.getFullPathById(5)).thenReturn("../../outside");

        MultipartFile file = new MockMultipartFile("files", "evil.txt", "text/plain", "nope".getBytes());

        fileService.processFolderUpload("job-4", "home-uuid", new MultipartFile[]{file});

        verify(fileRepository, never()).insertFile(any(), any(), any(), any());
    }

    @Test
    void processFolderUploadSkipsFilesWithoutOriginalName() {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(null);

        fileService.processFolderUpload("job-5", "home-uuid", new MultipartFile[]{file});

        verify(fileRepository, never()).insertFile(any(), any(), any(), any());
        verify(fileRepository, never()).findIdByUuid(any());
    }

    // --- getChildrenAfter ---

    @Test
    void getChildrenAfterResolvesParentUuidToId() {
        User user = User.builder().username("alice").userRoles(List.of(new Role(1L, "USER_ROLE", null))).build();
        when(fileRepository.findIdByUuid("folder-uuid")).thenReturn(Optional.of(7));
        when(fileRepository.getChildrenAfter(eq(7), any(), eq("folder-uuid"), eq(50), any())).thenReturn(List.of());

        fileService.getChildrenAfter("folder-uuid", null, 50, user);

        verify(fileRepository).getChildrenAfter(eq(7), any(), eq("folder-uuid"), eq(50), eq(user.getUserRoles()));
    }

    @Test
    void getChildrenAfterThrowsWhenParentUuidNotFound() {
        User user = User.builder().username("alice").userRoles(List.of()).build();
        when(fileRepository.findIdByUuid("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getChildrenAfter("missing", null, 50, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Folder not found");
    }

    @Test
    void getChildrenAfterUsesEmptyRoleListWhenUserHasNone() {
        User user = User.builder().username("alice").userRoles(null).build();

        fileService.getChildrenAfter(null, null, 50, user);

        verify(fileRepository).getChildrenAfter(isNull(), isNull(), isNull(), eq(50), eq(List.of()));
    }

    // --- deleteItemByExternalId ---

    @Test
    void deleteItemByExternalIdReturnsFalseWhenRecordMissing() {
        when(fileRepository.getFullPath("missing")).thenReturn(null);

        assertThat(fileService.deleteItemByExternalId("missing")).isFalse();
        verify(fileRepository, never()).deleteItemAndGetDescendantPaths(any());
    }

    @Test
    void deleteItemByExternalIdReturnsFalseWhenNoDescendants() {
        when(fileRepository.getFullPath("orphan")).thenReturn("root/alice/orphan.txt");
        when(fileRepository.deleteItemAndGetDescendantPaths("orphan")).thenReturn(List.of());

        assertThat(fileService.deleteItemByExternalId("orphan")).isFalse();
    }

    @Test
    void deleteItemByExternalIdRemovesPhysicalFile() throws Exception {
        Path filePath = storageRoot.resolve("root/alice/doc.txt");
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "content");

        when(fileRepository.getFullPath("doc-uuid")).thenReturn("root/alice/doc.txt");
        when(fileRepository.deleteItemAndGetDescendantPaths("doc-uuid"))
                .thenReturn(List.<String[]>of(new String[]{"doc-uuid", "doc.txt", "FILE"}));

        assertThat(fileService.deleteItemByExternalId("doc-uuid")).isTrue();
        assertThat(Files.exists(filePath)).isFalse();
    }

    @Test
    void deleteItemByExternalIdRemovesFolderTreeRecursively() throws Exception {
        Path folderPath = storageRoot.resolve("root/alice/folder");
        Files.createDirectories(folderPath);
        Files.writeString(folderPath.resolve("inner.txt"), "content");

        when(fileRepository.getFullPath("folder-uuid")).thenReturn("root/alice/folder");
        when(fileRepository.getFullPath("inner-uuid")).thenReturn("root/alice/folder/inner.txt");
        when(fileRepository.deleteItemAndGetDescendantPaths("folder-uuid")).thenReturn(List.<String[]>of(
                new String[]{"folder-uuid", "folder", "FOLDER"},
                new String[]{"inner-uuid", "inner.txt", "FILE"}
        ));

        assertThat(fileService.deleteItemByExternalId("folder-uuid")).isTrue();
        assertThat(Files.exists(folderPath)).isFalse();
    }
}
