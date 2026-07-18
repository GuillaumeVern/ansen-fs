package com.losvernos.anzenfs.files;

import com.losvernos.anzenfs.rbac.role.Role;
import com.losvernos.anzenfs.support.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private FileRepository fileRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = TestDb.newJdbcTemplate();
        fileRepository = new FileRepository(jdbcTemplate);
    }

    private long insertRaw(String name, Long parentId, String type, String externalId) {
        jdbcTemplate.update(
                "INSERT INTO files (external_id, parent_id, name, type) VALUES (?, ?, ?, ?)",
                externalId, parentId, name, type);
        return jdbcTemplate.queryForObject("SELECT file_id FROM files WHERE external_id = ?", Long.class, externalId);
    }

    @Test
    void findIdByNameAndParentFindsTopLevelEntry() {
        insertRaw("root", null, "FOLDER", "root-uuid");

        assertThat(fileRepository.findIdByNameAndParent("root", null)).isPresent();
        assertThat(fileRepository.findIdByNameAndParent("missing", null)).isEmpty();
    }

    @Test
    void findIdByNameAndParentFindsNestedEntry() {
        long rootId = insertRaw("root", null, "FOLDER", "root-uuid");
        insertRaw("alice", rootId, "FOLDER", "alice-uuid");

        assertThat(fileRepository.findIdByNameAndParent("alice", (int) rootId)).isPresent();
        assertThat(fileRepository.findIdByNameAndParent("alice", 999)).isEmpty();
    }

    @Test
    void findByNameAndParentReturnsFileNode() {
        long rootId = insertRaw("root", null, "FOLDER", "root-uuid");
        insertRaw("alice", rootId, "FOLDER", "alice-uuid");

        Optional<FileNode> found = fileRepository.findByNameAndParent("alice", (int) rootId);

        assertThat(found).isPresent();
        assertThat(found.get().uuid()).isEqualTo("alice-uuid");
        assertThat(found.get().name()).isEqualTo("alice");
        assertThat(found.get().type()).isEqualTo("FOLDER");
    }

    @Test
    void findIdByUuidHandlesFoundMissingAndNull() {
        insertRaw("root", null, "FOLDER", "root-uuid");

        assertThat(fileRepository.findIdByUuid("root-uuid")).isPresent();
        assertThat(fileRepository.findIdByUuid("does-not-exist")).isEmpty();
        assertThat(fileRepository.findIdByUuid(null)).isEmpty();
    }

    @Test
    void getChildrenAfterReturnsEmptyWhenRolesEmpty() {
        List<FileNode> result = fileRepository.getChildrenAfter(null, null, null, 50, List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void getChildrenAfterOrdersByNameAndRespectsLimitAndCursor() {
        long rootId = insertRaw("root", null, "FOLDER", "root-uuid");
        insertRaw("banana.txt", rootId, "FILE", "f-banana");
        insertRaw("apple.txt", rootId, "FILE", "f-apple");
        insertRaw("cherry.txt", rootId, "FILE", "f-cherry");

        List<Role> roles = List.of(new Role(1L, "USER_ROLE", null));

        List<FileNode> firstPage = fileRepository.getChildrenAfter((int) rootId, null, "root-uuid", 2, roles);
        assertThat(firstPage).extracting(FileNode::name).containsExactly("apple.txt", "banana.txt");

        List<FileNode> secondPage = fileRepository.getChildrenAfter(
                (int) rootId, firstPage.get(firstPage.size() - 1).name(), "root-uuid", 2, roles);
        assertThat(secondPage).extracting(FileNode::name).containsExactly("cherry.txt");
    }

    @Test
    void getChildrenAfterFiltersByNullParentForTopLevel() {
        insertRaw("root", null, "FOLDER", "root-uuid");
        insertRaw("otherRoot", null, "FOLDER", "other-uuid");

        List<Role> roles = List.of(new Role(1L, "USER_ROLE", null));
        List<FileNode> topLevel = fileRepository.getChildrenAfter(null, null, null, 50, roles);

        assertThat(topLevel).extracting(FileNode::name).containsExactly("otherRoot", "root");
    }

    @Test
    void insertFileCreatesRowWithGeneratedExternalId() {
        fileRepository.insertFile(null, "doc.txt", "FILE", "somehash");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM files WHERE name = ? AND type = 'FILE' AND file_hash = ?",
                Integer.class, "doc.txt", "somehash");
        assertThat(count).isEqualTo(1);

        String externalId = jdbcTemplate.queryForObject(
                "SELECT external_id FROM files WHERE name = ?", String.class, "doc.txt");
        assertThat(externalId).isNotBlank();
    }

    @Test
    void createFolderGeneratesExternalIdWhenNotProvided() {
        Integer folderId = fileRepository.createFolder(null, "generated");

        assertThat(folderId).isNotNull();
        String externalId = jdbcTemplate.queryForObject(
                "SELECT external_id FROM files WHERE file_id = ?", String.class, folderId);
        assertThat(externalId).isNotBlank();
    }

    @Test
    void createFolderUsesProvidedExternalId() {
        Integer folderId = fileRepository.createFolder(null, "fixed", "fixed-uuid");

        String externalId = jdbcTemplate.queryForObject(
                "SELECT external_id FROM files WHERE file_id = ?", String.class, folderId);
        assertThat(externalId).isEqualTo("fixed-uuid");
    }

    @Test
    void getFullPathBuildsAncestryFromRootToFile() {
        long rootId = insertRaw("root", null, "FOLDER", "root-uuid");
        long aliceId = insertRaw("alice", rootId, "FOLDER", "alice-uuid");
        long subId = insertRaw("docs", aliceId, "FOLDER", "docs-uuid");
        insertRaw("report.txt", subId, "FILE", "report-uuid");

        assertThat(fileRepository.getFullPath("report-uuid")).isEqualTo("root/alice/docs/report.txt");
    }

    @Test
    void getFullPathReturnsNullWhenExternalIdUnknown() {
        assertThat(fileRepository.getFullPath("nope")).isNull();
    }

    @Test
    void getFullPathByIdBuildsAncestryFromRootToFolder() {
        long rootId = insertRaw("root", null, "FOLDER", "root-uuid");
        long aliceId = insertRaw("alice", rootId, "FOLDER", "alice-uuid");

        assertThat(fileRepository.getFullPathById((int) aliceId)).isEqualTo("root/alice");
    }

    @Test
    void getFullPathByIdReturnsEmptyStringForNullId() {
        assertThat(fileRepository.getFullPathById(null)).isEmpty();
    }

    @Test
    void deleteItemAndGetDescendantPathsRemovesFolderAndChildren() {
        long rootId = insertRaw("root", null, "FOLDER", "root-uuid");
        long folderId = insertRaw("folder", rootId, "FOLDER", "folder-uuid");
        insertRaw("inner.txt", folderId, "FILE", "inner-uuid");

        List<String[]> deleted = fileRepository.deleteItemAndGetDescendantPaths("folder-uuid");

        assertThat(deleted).hasSize(2);
        assertThat(deleted).extracting(row -> row[0]).containsExactlyInAnyOrder("folder-uuid", "inner-uuid");

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM files WHERE external_id IN ('folder-uuid', 'inner-uuid')", Integer.class);
        assertThat(remaining).isZero();

        Integer rootStillThere = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM files WHERE external_id = 'root-uuid'", Integer.class);
        assertThat(rootStillThere).isEqualTo(1);
    }

    @Test
    void deleteItemAndGetDescendantPathsReturnsEmptyForUnknownId() {
        assertThat(fileRepository.deleteItemAndGetDescendantPaths("does-not-exist")).isEmpty();
    }

    @Test
    void linkAndUnlinkFileFromRole() {
        long fileId = insertRaw("doc", null, "FILE", "doc-uuid");

        fileRepository.linkFileToRole(fileId, 1L, "read");
        String level = jdbcTemplate.queryForObject(
                "SELECT permission_level FROM file_roles WHERE file_id = ? AND role_id = ?",
                String.class, fileId, 1L);
        assertThat(level).isEqualTo("READ");

        fileRepository.linkFileToRole(fileId, 1L, "write");
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_roles WHERE file_id = ? AND role_id = ?",
                Integer.class, fileId, 1L);
        assertThat(rowCount).isEqualTo(1);
        String updatedLevel = jdbcTemplate.queryForObject(
                "SELECT permission_level FROM file_roles WHERE file_id = ? AND role_id = ?",
                String.class, fileId, 1L);
        assertThat(updatedLevel).isEqualTo("WRITE");

        fileRepository.unlinkFileFromRole(fileId, 1L);
        Integer afterUnlink = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_roles WHERE file_id = ? AND role_id = ?",
                Integer.class, fileId, 1L);
        assertThat(afterUnlink).isZero();
    }
}
