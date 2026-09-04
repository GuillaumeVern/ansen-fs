package com.losvernos.anzenfs.files;

import com.losvernos.anzenfs.files.preview.PreviewThumbnailGenerator;
import com.losvernos.anzenfs.files.preview.PreviewThumbnailGeneratorRegistry;
import com.losvernos.anzenfs.rbac.role.Role;
import com.losvernos.anzenfs.rbac.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    private FileService fileService;
    private Path storageRoot;
    private Path thumbnailRoot;
    private PreviewThumbnailGenerator videoThumbnailGenerator;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        fileService = new FileService();
        ReflectionTestUtils.setField(fileService, "fileRepository", fileRepository);

        storageRoot = tempDir.resolve("data");
        ReflectionTestUtils.setField(fileService, "storageRoot", storageRoot);

        thumbnailRoot = tempDir.resolve("thumbnails");
        ReflectionTestUtils.setField(fileService, "thumbnailRoot", thumbnailRoot);

        videoThumbnailGenerator = mock(PreviewThumbnailGenerator.class);
        when(videoThumbnailGenerator.supportedType()).thenReturn(FileType.VIDEO);
        PreviewThumbnailGeneratorRegistry registry =
                new PreviewThumbnailGeneratorRegistry(List.of(videoThumbnailGenerator));
        ReflectionTestUtils.setField(fileService, "thumbnailGeneratorRegistry", registry);
    }

    // --- getHomeFolder ---

    @Test
    void getHomeFolderReturnsSyntheticRootForAdmin() {
        Role admin = new Role(1L, "ADMIN", null);
        User user = User.builder().username("root").userRoles(List.of(admin)).build();

        FileNode home = fileService.getHomeFolder(user);

        assertThat(home).isEqualTo(new FileNode("root-uuid", null, "root", FileType.FOLDER, null, 0L));
    }

    @Test
    void getHomeFolderLooksUpPersonalFolderForRegularUser() {
        Role userRole = new Role(2L, "USER_ROLE", null);
        User user = User.builder().username("alice").userRoles(List.of(userRole)).build();

        when(fileRepository.findIdByNameAndParent("root", null)).thenReturn(Optional.of(10));
        FileNode expected = new FileNode("alice-uuid", "root-uuid", "alice", FileType.FOLDER, null, 0L);
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

        verify(fileRepository).insertFile(eq(5), eq("photo.jpg"), eq(FileType.IMAGE), anyString(), eq(10L));
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
        verify(fileRepository).insertFile(eq(42), eq("nested.txt"), eq(FileType.TEXT), anyString(), eq(6L));
    }

    @Test
    void processFolderUploadReusesExistingSubfolder() throws Exception {
        when(fileRepository.findIdByUuid("home-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.findIdByNameAndParent("sub", 5)).thenReturn(Optional.of(42));
        when(fileRepository.getFullPathById(42)).thenReturn("root/alice/sub");

        MultipartFile file = new MockMultipartFile("files", "sub/nested.txt", "text/plain", "nested".getBytes());

        fileService.processFolderUpload("job-3", "home-uuid", new MultipartFile[]{file});

        verify(fileRepository, never()).createFolder(any(), any());
        verify(fileRepository).insertFile(eq(42), eq("nested.txt"), eq(FileType.TEXT), anyString(), eq(6L));
    }

    @Test
    void processFolderUploadRejectsPathEscapingStorageRoot() {
        when(fileRepository.findIdByUuid("home-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.getFullPathById(5)).thenReturn("../../outside");

        MultipartFile file = new MockMultipartFile("files", "evil.txt", "text/plain", "nope".getBytes());

        fileService.processFolderUpload("job-4", "home-uuid", new MultipartFile[]{file});

        verify(fileRepository, never()).insertFile(any(), any(), any(), any(), anyLong());
    }

    @Test
    void processFolderUploadSkipsFilesWithoutOriginalName() {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(null);

        fileService.processFolderUpload("job-5", "home-uuid", new MultipartFile[]{file});

        verify(fileRepository, never()).insertFile(any(), any(), any(), any(), anyLong());
        verify(fileRepository, never()).findIdByUuid(any());
    }

    @Test
    void processFolderUploadGeneratesThumbnailForVideoFiles() throws Exception {
        when(fileRepository.findIdByUuid("home-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.getFullPathById(5)).thenReturn("root/alice");
        when(fileRepository.insertFile(eq(5), eq("clip.mp4"), eq(FileType.VIDEO), anyString(), eq(10L)))
                .thenReturn("clip-external-id");

        MultipartFile file = new MockMultipartFile("files", "clip.mp4", "video/mp4", "moviebytes".getBytes());

        fileService.processFolderUpload("job-6", "home-uuid", new MultipartFile[]{file});

        Path expectedSource = storageRoot.resolve("root/alice/clip.mp4");
        Path expectedThumbnail = thumbnailRoot.resolve("clip-external-id.jpg");
        verify(videoThumbnailGenerator).generate(expectedSource, expectedThumbnail);
    }

    @Test
    void processFolderUploadDoesNotGenerateThumbnailForNonThumbnailedTypes() throws Exception {
        when(fileRepository.findIdByUuid("home-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.getFullPathById(5)).thenReturn("root/alice");

        MultipartFile file = new MockMultipartFile("files", "photo.jpg", "image/jpeg", "binarydata".getBytes());

        fileService.processFolderUpload("job-7", "home-uuid", new MultipartFile[]{file});

        verify(videoThumbnailGenerator, never()).generate(any(), any());
    }

    // --- getPreviewResource ---

    @Test
    void getPreviewResourceServesCachedThumbnailForVideo() throws Exception {
        Path videoPath = storageRoot.resolve("root/alice/clip.mp4");
        Files.createDirectories(videoPath.getParent());
        Files.writeString(videoPath, "moviebytes");
        when(fileRepository.getFullPath("clip-uuid")).thenReturn("root/alice/clip.mp4");

        Files.createDirectories(thumbnailRoot);
        Path thumbnailPath = thumbnailRoot.resolve("clip-uuid.jpg");
        Files.writeString(thumbnailPath, "jpegbytes");

        ResourceAndName result = fileService.getPreviewResource("clip-uuid");

        assertThat(result.fileName()).isEqualTo("clip-uuid.jpg");
        assertThat(result.resource().getContentAsString(StandardCharsets.UTF_8)).isEqualTo("jpegbytes");
    }

    @Test
    void getPreviewResourceThrowsWhenVideoThumbnailNotYetGenerated() throws Exception {
        Path videoPath = storageRoot.resolve("root/alice/clip.mp4");
        Files.createDirectories(videoPath.getParent());
        Files.writeString(videoPath, "moviebytes");
        when(fileRepository.getFullPath("clip-uuid")).thenReturn("root/alice/clip.mp4");

        assertThatThrownBy(() -> fileService.getPreviewResource("clip-uuid"))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void getPreviewResourceServesOriginalFileForNonThumbnailedTypes() throws Exception {
        Path imagePath = storageRoot.resolve("root/alice/photo.jpg");
        Files.createDirectories(imagePath.getParent());
        Files.writeString(imagePath, "imagebytes");
        when(fileRepository.getFullPath("photo-uuid")).thenReturn("root/alice/photo.jpg");

        ResourceAndName result = fileService.getPreviewResource("photo-uuid");

        assertThat(result.fileName()).isEqualTo("photo.jpg");
        assertThat(result.resource().getContentAsString(StandardCharsets.UTF_8)).isEqualTo("imagebytes");
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
    void getChildrenAfterReplacesFolderSizeWithRecursiveTotalButLeavesFilesAsIs() {
        User user = User.builder().username("alice").userRoles(List.of(new Role(1L, "USER_ROLE", null))).build();
        FileNode folder = new FileNode("folder-uuid", "root-uuid", "Docs", FileType.FOLDER, null, 0L);
        FileNode file = new FileNode("file-uuid", "root-uuid", "a.txt", FileType.TEXT, "hash", 42L);

        when(fileRepository.getChildrenAfter(isNull(), isNull(), isNull(), eq(50), any()))
                .thenReturn(List.of(folder, file));
        when(fileRepository.getFolderSize("folder-uuid")).thenReturn(999L);

        List<FileNode> result = fileService.getChildrenAfter(null, null, 50, user);

        assertThat(result).extracting(FileNode::uuid, FileNode::size)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("folder-uuid", 999L),
                        org.assertj.core.groups.Tuple.tuple("file-uuid", 42L));
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

    // --- permanentlyDeleteItemByExternalId ---

    @Test
    void permanentlyDeleteItemByExternalIdReturnsFalseWhenRecordMissing() {
        when(fileRepository.getFullPath("missing")).thenReturn(null);

        assertThat(fileService.permanentlyDeleteItemByExternalId("missing")).isFalse();
        verify(fileRepository, never()).deleteItemAndGetDescendantPaths(any());
    }

    @Test
    void permanentlyDeleteItemByExternalIdReturnsFalseWhenNoDescendants() {
        when(fileRepository.getFullPath("orphan")).thenReturn("root/alice/orphan.txt");
        when(fileRepository.deleteItemAndGetDescendantPaths("orphan")).thenReturn(List.of());

        assertThat(fileService.permanentlyDeleteItemByExternalId("orphan")).isFalse();
    }

    @Test
    void permanentlyDeleteItemByExternalIdRemovesPhysicalFile() throws Exception {
        Path filePath = storageRoot.resolve("root/alice/doc.txt");
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "content");

        when(fileRepository.getFullPath("doc-uuid")).thenReturn("root/alice/doc.txt");
        when(fileRepository.deleteItemAndGetDescendantPaths("doc-uuid"))
                .thenReturn(List.<String[]>of(new String[]{"doc-uuid", "doc.txt", "FILE"}));

        assertThat(fileService.permanentlyDeleteItemByExternalId("doc-uuid")).isTrue();
        assertThat(Files.exists(filePath)).isFalse();
    }

    @Test
    void permanentlyDeleteItemByExternalIdRemovesAssociatedThumbnail() throws Exception {
        Path filePath = storageRoot.resolve("root/alice/clip.mp4");
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "moviebytes");

        Files.createDirectories(thumbnailRoot);
        Path thumbnailPath = thumbnailRoot.resolve("clip-uuid.jpg");
        Files.writeString(thumbnailPath, "jpegbytes");

        when(fileRepository.getFullPath("clip-uuid")).thenReturn("root/alice/clip.mp4");
        when(fileRepository.deleteItemAndGetDescendantPaths("clip-uuid"))
                .thenReturn(List.<String[]>of(new String[]{"clip-uuid", "clip.mp4", "VIDEO"}));

        assertThat(fileService.permanentlyDeleteItemByExternalId("clip-uuid")).isTrue();

        assertThat(Files.exists(filePath)).isFalse();
        assertThat(Files.exists(thumbnailPath)).isFalse();
    }

    @Test
    void permanentlyDeleteItemByExternalIdRemovesFolderTreeRecursively() throws Exception {
        Path folderPath = storageRoot.resolve("root/alice/folder");
        Files.createDirectories(folderPath);
        Files.writeString(folderPath.resolve("inner.txt"), "content");

        when(fileRepository.getFullPath("folder-uuid")).thenReturn("root/alice/folder");
        when(fileRepository.getFullPath("inner-uuid")).thenReturn("root/alice/folder/inner.txt");
        when(fileRepository.deleteItemAndGetDescendantPaths("folder-uuid")).thenReturn(List.<String[]>of(
                new String[]{"folder-uuid", "folder", "FOLDER"},
                new String[]{"inner-uuid", "inner.txt", "FILE"}
        ));

        assertThat(fileService.permanentlyDeleteItemByExternalId("folder-uuid")).isTrue();
        assertThat(Files.exists(folderPath)).isFalse();
    }

    // --- softDeleteItem / restoreItem ---

    @Test
    void softDeleteItemDelegatesToRepository() {
        when(fileRepository.softDeleteItem("doc-uuid")).thenReturn(true);

        assertThat(fileService.softDeleteItem("doc-uuid")).isTrue();
    }

    @Test
    void restoreItemDelegatesToRepository() {
        when(fileRepository.restoreItem("doc-uuid")).thenReturn(true);

        assertThat(fileService.restoreItem("doc-uuid")).isTrue();
    }

    // --- getTrashedItems ---

    @Test
    void getTrashedItemsReturnsEverythingForAdmin() {
        Role admin = new Role(1L, "ADMIN", null);
        User user = User.builder().username("root").userRoles(List.of(admin)).build();

        when(fileRepository.findDeletedItems()).thenReturn(List.of(
                new FileRepository.TrashRow("doc-uuid", 5, "doc.txt", FileType.TEXT, "hash", 10L, "2026-01-01 00:00:00")));
        when(fileRepository.getFullPath("doc-uuid")).thenReturn("root/alice/doc.txt");

        List<TrashedFileNode> trashed = fileService.getTrashedItems(user);

        assertThat(trashed).containsExactly(
                new TrashedFileNode("doc-uuid", "doc.txt", FileType.TEXT, 10L, "root/alice", "2026-01-01 00:00:00"));
    }

    @Test
    void getTrashedItemsFiltersToTheUsersHomeFolderForRegularUser() {
        Role userRole = new Role(2L, "USER_ROLE", null);
        User user = User.builder().username("alice").userRoles(List.of(userRole)).build();

        when(fileRepository.findIdByNameAndParent("root", null)).thenReturn(Optional.of(10));
        FileNode home = new FileNode("home-uuid", null, "alice", FileType.FOLDER, null, 0L);
        when(fileRepository.findByNameAndParent("alice", 10)).thenReturn(Optional.of(home));
        when(fileRepository.getFullPath("home-uuid")).thenReturn("root/alice");

        when(fileRepository.findDeletedItems()).thenReturn(List.of(
                new FileRepository.TrashRow("mine-uuid", 5, "mine.txt", FileType.TEXT, "hash", 10L, "2026-01-01 00:00:00"),
                new FileRepository.TrashRow("other-uuid", 6, "other.txt", FileType.TEXT, "hash", 20L, "2026-01-01 00:00:00")));
        when(fileRepository.getFullPath("mine-uuid")).thenReturn("root/alice/mine.txt");
        when(fileRepository.getFullPath("other-uuid")).thenReturn("root/bob/other.txt");

        List<TrashedFileNode> trashed = fileService.getTrashedItems(user);

        assertThat(trashed).extracting(TrashedFileNode::uuid).containsExactly("mine-uuid");
    }

    // --- getOrCreateWebDavRoot ---

    @Test
    void getOrCreateWebDavRootReturnsExistingPhotosFolder() {
        Role userRole = new Role(2L, "USER_ROLE", null);
        User user = User.builder().username("alice").userRoles(List.of(userRole)).build();

        when(fileRepository.findIdByNameAndParent("root", null)).thenReturn(Optional.of(10));
        when(fileRepository.findIdByNameAndParent("alice", 10)).thenReturn(Optional.of(5));
        FileNode photos = new FileNode("photos-uuid", null, "Photos", FileType.FOLDER, null, 0L);
        when(fileRepository.findByNameAndParent("Photos", 5)).thenReturn(Optional.of(photos));

        assertThat(fileService.getOrCreateWebDavRoot(user)).isEqualTo(photos);
        verify(fileRepository, never()).createFolder(any(), any());
    }

    @Test
    void getOrCreateWebDavRootCreatesPhotosFolderWhenMissing() {
        Role userRole = new Role(2L, "USER_ROLE", null);
        User user = User.builder().username("alice").userRoles(List.of(userRole)).build();

        when(fileRepository.findIdByNameAndParent("root", null)).thenReturn(Optional.of(10));
        when(fileRepository.findIdByNameAndParent("alice", 10)).thenReturn(Optional.of(5));
        FileNode photos = new FileNode("photos-uuid", null, "Photos", FileType.FOLDER, null, 0L);
        when(fileRepository.findByNameAndParent("Photos", 5))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(photos));

        assertThat(fileService.getOrCreateWebDavRoot(user)).isEqualTo(photos);
        verify(fileRepository).createFolder(5, "Photos");
    }

    @Test
    void getOrCreateWebDavRootUsesSystemRootAsHomeForAdmin() {
        Role admin = new Role(1L, "ADMIN", null);
        User user = User.builder().username("root").userRoles(List.of(admin)).build();

        when(fileRepository.findIdByNameAndParent("root", null)).thenReturn(Optional.of(10));
        FileNode photos = new FileNode("photos-uuid", null, "Photos", FileType.FOLDER, null, 0L);
        when(fileRepository.findByNameAndParent("Photos", 10)).thenReturn(Optional.of(photos));

        assertThat(fileService.getOrCreateWebDavRoot(user)).isEqualTo(photos);
    }

    // --- resolveNode ---

    private static final FileNode WEBDAV_ROOT = new FileNode("root-uuid", null, "Photos", FileType.FOLDER, null, 0L);

    @Test
    void resolveNodeReturnsRootWhenPathBlank() {
        assertThat(fileService.resolveNode(WEBDAV_ROOT, "")).contains(WEBDAV_ROOT);
        assertThat(fileService.resolveNode(WEBDAV_ROOT, null)).contains(WEBDAV_ROOT);
    }

    @Test
    void resolveNodeWalksSegmentsAndReturnsTerminalNode() {
        when(fileRepository.findIdByUuid("root-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.findIdByNameAndParent("2026", 5)).thenReturn(Optional.of(6));
        FileNode expected = new FileNode("img-uuid", null, "img.jpg", FileType.IMAGE, "hash", 10L);
        when(fileRepository.findByNameAndParent("img.jpg", 6)).thenReturn(Optional.of(expected));

        assertThat(fileService.resolveNode(WEBDAV_ROOT, "2026/img.jpg")).contains(expected);
    }

    @Test
    void resolveNodeReturnsEmptyWhenIntermediateSegmentMissing() {
        when(fileRepository.findIdByUuid("root-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.findIdByNameAndParent("2026", 5)).thenReturn(Optional.empty());

        assertThat(fileService.resolveNode(WEBDAV_ROOT, "2026/img.jpg")).isEmpty();
    }

    @Test
    void resolveNodeReturnsEmptyWhenLeafSegmentMissing() {
        when(fileRepository.findIdByUuid("root-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.findByNameAndParent("img.jpg", 5)).thenReturn(Optional.empty());

        assertThat(fileService.resolveNode(WEBDAV_ROOT, "img.jpg")).isEmpty();
    }

    // --- createCollection ---

    @Test
    void createCollectionCreatesMissingFolderAndReturnsTrue() {
        when(fileRepository.findIdByUuid("root-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.findIdByNameAndParent("2026", 5)).thenReturn(Optional.empty());

        assertThat(fileService.createCollection(WEBDAV_ROOT, "2026")).isTrue();
        verify(fileRepository).createFolder(5, "2026");
    }

    @Test
    void createCollectionReturnsFalseWhenAlreadyExists() {
        when(fileRepository.findIdByUuid("root-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.findIdByNameAndParent("2026", 5)).thenReturn(Optional.of(6));

        assertThat(fileService.createCollection(WEBDAV_ROOT, "2026")).isFalse();
        verify(fileRepository, never()).createFolder(any(), any());
    }

    @Test
    void createCollectionCreatesMissingAncestorsForNestedPath() {
        when(fileRepository.findIdByUuid("root-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.findIdByNameAndParent("2026", 5)).thenReturn(Optional.empty());
        when(fileRepository.createFolder(5, "2026")).thenReturn(6);
        when(fileRepository.findIdByNameAndParent("09", 6)).thenReturn(Optional.empty());

        assertThat(fileService.createCollection(WEBDAV_ROOT, "2026/09")).isTrue();
        verify(fileRepository).createFolder(5, "2026");
        verify(fileRepository).createFolder(6, "09");
    }

    // --- putFile ---

    @Test
    void putFileStoresNewFileAtRootAndInsertsRow() throws Exception {
        when(fileRepository.findIdByUuid("root-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.getFullPathById(5)).thenReturn("root/alice/Photos");
        when(fileRepository.findByNameAndParent("img.jpg", 5)).thenReturn(Optional.empty());
        when(fileRepository.insertFile(eq(5), eq("img.jpg"), eq(FileType.IMAGE), anyString(), eq(10L)))
                .thenReturn("img-uuid");

        InputStream content = new ByteArrayInputStream("binarydata".getBytes(StandardCharsets.UTF_8));
        FileNode result = fileService.putFile(WEBDAV_ROOT, "img.jpg", content);

        Path expected = storageRoot.resolve("root/alice/Photos/img.jpg");
        assertThat(Files.exists(expected)).isTrue();
        assertThat(Files.readString(expected)).isEqualTo("binarydata");
        assertThat(result.uuid()).isEqualTo("img-uuid");
        verify(fileRepository, never()).updateFileContent(any(), any(), anyLong());
    }

    @Test
    void putFileOverwritesExistingFileInPlace() throws Exception {
        when(fileRepository.findIdByUuid("root-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.getFullPathById(5)).thenReturn("root/alice/Photos");

        Path existingPath = storageRoot.resolve("root/alice/Photos/img.jpg");
        Files.createDirectories(existingPath.getParent());
        Files.writeString(existingPath, "old content");

        FileNode existing = new FileNode("img-uuid", null, "img.jpg", FileType.IMAGE, "oldhash", 11L);
        when(fileRepository.findByNameAndParent("img.jpg", 5)).thenReturn(Optional.of(existing));

        InputStream content = new ByteArrayInputStream("new content!".getBytes(StandardCharsets.UTF_8));
        FileNode result = fileService.putFile(WEBDAV_ROOT, "img.jpg", content);

        assertThat(Files.readString(existingPath)).isEqualTo("new content!");
        assertThat(result.uuid()).isEqualTo("img-uuid");
        verify(fileRepository).updateFileContent(eq("img-uuid"), anyString(), eq(12L));
        verify(fileRepository, never()).insertFile(any(), any(), any(), any(), anyLong());
    }

    @Test
    void putFileCreatesMissingSubfoldersFromRelativePath() throws Exception {
        when(fileRepository.findIdByUuid("root-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.findIdByNameAndParent("2026", 5)).thenReturn(Optional.empty());
        when(fileRepository.createFolder(5, "2026")).thenReturn(6);
        when(fileRepository.getFullPathById(6)).thenReturn("root/alice/Photos/2026");
        when(fileRepository.findByNameAndParent("img.jpg", 6)).thenReturn(Optional.empty());

        InputStream content = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));
        fileService.putFile(WEBDAV_ROOT, "2026/img.jpg", content);

        assertThat(Files.exists(storageRoot.resolve("root/alice/Photos/2026/img.jpg"))).isTrue();
        verify(fileRepository).createFolder(5, "2026");
    }

    @Test
    void putFileRejectsPathEscapingStorageRoot() {
        when(fileRepository.findIdByUuid("root-uuid")).thenReturn(Optional.of(5));
        when(fileRepository.getFullPathById(5)).thenReturn("../../outside");

        InputStream content = new ByteArrayInputStream("nope".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> fileService.putFile(WEBDAV_ROOT, "evil.txt", content))
                .isInstanceOf(SecurityException.class);
    }
}
