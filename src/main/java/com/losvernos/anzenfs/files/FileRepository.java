package com.losvernos.anzenfs.files;

import com.losvernos.anzenfs.rbac.role.Role;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FileRepository {

    private final JdbcTemplate jdbcTemplate;

    public FileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * schema.sql's CREATE TABLE IF NOT EXISTS only takes effect for brand-new databases, so
     * this is how {@link com.losvernos.anzenfs.database.FileSizeBackfillRunner} detects
     * installs from before file sizes were tracked.
     */
    public boolean hasSizeColumn() {
        List<String> columns = jdbcTemplate.query(
                "PRAGMA table_info(files)", (rs, rowNum) -> rs.getString("name"));
        return columns.contains("size_bytes");
    }

    public void addSizeColumn() {
        jdbcTemplate.execute("ALTER TABLE files ADD COLUMN size_bytes INTEGER DEFAULT 0");
    }

    public record IdAndExternalId(int fileId, String externalId) {
    }

    public List<IdAndExternalId> findNonFolderIdsAndExternalIds() {
        return jdbcTemplate.query(
                "SELECT file_id, external_id FROM files WHERE type != 'FOLDER'",
                (rs, rowNum) -> new IdAndExternalId(rs.getInt("file_id"), rs.getString("external_id")));
    }

    public void updateFileSize(int fileId, long size) {
        jdbcTemplate.update("UPDATE files SET size_bytes = ? WHERE file_id = ?", size, fileId);
    }

    public Optional<Integer> findIdByNameAndParent(String name, Integer parentId) {
        String sql = "SELECT file_id FROM files WHERE name = ? AND " +
                (parentId == null ? "parent_id IS NULL" : "parent_id = ?");

        try {
            Integer id = parentId == null
                    ? jdbcTemplate.queryForObject(sql, Integer.class, name)
                    : jdbcTemplate.queryForObject(sql, Integer.class, name, parentId);
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<FileNode> findByNameAndParent(String name, Integer parentId) {
        String sql = "SELECT external_id, name, type, file_hash FROM files WHERE name = ? AND " +
                (parentId == null ? "parent_id IS NULL" : "parent_id = ?");

        try {
            FileNode node = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new FileNode(
                    rs.getString("external_id"),
                    null,
                    rs.getString("name"),
                    FileType.valueOf(rs.getString("type")),
                    rs.getString("file_hash"),
                    0L
            ), parentId == null ? new Object[]{name} : new Object[]{name, parentId});
            return Optional.ofNullable(node);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Integer> findIdByUuid(String uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        String sql = "SELECT file_id FROM files WHERE external_id = ?;";
        try {
            Integer id = jdbcTemplate.queryForObject(sql, Integer.class, uuid);
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<FileNode> getChildrenAfter(Integer parentId, String lastFileName, String parentUuid, int limit, List<Role> roles) {
        if (roles.isEmpty()) {
            return new ArrayList<>();
        }

        String sql = """
                SELECT f.* FROM files f
                WHERE ((? IS NULL AND f.parent_id IS NULL) OR (f.parent_id = ?))
                  AND (? IS NULL OR f.name > ?)
                ORDER BY f.name LIMIT ?;
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new FileNode(
                rs.getString("external_id"),
                parentUuid,
                rs.getString("name"),
                FileType.valueOf(rs.getString("type")),
                rs.getString("file_hash"),
                rs.getLong("size_bytes")
        ), parentId, parentId, lastFileName, lastFileName, limit);
    }

    public String insertFile(Integer parentId, String name, FileType type, String hash, long size) {
        String sql = "INSERT INTO files (parent_id, name, type, file_hash, external_id, size_bytes) VALUES (?, ?, ?, ?, ?, ?);";
        String externalId = UUID.randomUUID().toString();
        jdbcTemplate.update(sql, parentId, name, type.name(), hash, externalId, size);
        return externalId;
    }

    /**
     * Sums size_bytes across a folder and everything under it. Folder rows themselves have no
     * meaningful size_bytes of their own (SUM ignores their default of 0/NULL contribution
     * beyond what their descendant files carry), so this recursion is what makes a folder's
     * size reflect its actual contents.
     */
    public long getFolderSize(String externalId) {
        String sql = """
                WITH RECURSIVE descendants(file_id) AS (
                  SELECT file_id FROM files WHERE external_id = ?
                  UNION ALL
                  SELECT f.file_id FROM files f JOIN descendants d ON f.parent_id = d.file_id
                )
                SELECT COALESCE(SUM(size_bytes), 0) FROM files WHERE file_id IN (SELECT file_id FROM descendants);
                """;

        Long total = jdbcTemplate.queryForObject(sql, Long.class, externalId);
        return total != null ? total : 0L;
    }

    public Integer createFolder(Integer parentId, String name) {
        return this.createFolder(parentId, name, null);
    }

    public Integer createFolder(Integer parentId, String name, String externalId) {
        String sql = "INSERT INTO files (parent_id, name, type, external_id) VALUES (?, ?, 'FOLDER', ?)";
        if (null == externalId) {
            externalId = UUID.randomUUID().toString();
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();

        String finalExternalId = externalId;
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, parentId); // setObject handles null conversions naturally
            ps.setString(2, name);
            ps.setString(3, finalExternalId);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key != null ? key.intValue() : null;
    }

    public String getFullPath(String externalId) {
        int maxDepth = 10000;
        String sql = """
                WITH RECURSIVE path_builder(level, name, parent_id) AS (
                  SELECT 0, name, parent_id
                  FROM files
                  WHERE external_id = ?
                  UNION ALL
                  SELECT pb.level + 1, f.name, f.parent_id
                  FROM files f
                  JOIN path_builder pb ON f.file_id = pb.parent_id
                  WHERE pb.level < ?
                )
                SELECT name FROM path_builder ORDER BY level DESC;
                """;

        List<String> pathParts = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("name"), externalId, maxDepth);
        return pathParts.isEmpty() ? null : String.join("/", pathParts);
    }

    public String getFullPathById(Integer fileId) {
        if (fileId == null) {
            return "";
        }

        int maxDepth = 10000;
        String sql = """
                WITH RECURSIVE path_builder(level, name, parent_id) AS (
                  SELECT 0, name, parent_id
                  FROM files
                  WHERE file_id = ?
                  UNION ALL
                  SELECT pb.level + 1, f.name, f.parent_id
                  FROM files f
                  JOIN path_builder pb ON f.file_id = pb.parent_id
                  WHERE pb.level < ?
                )
                SELECT name FROM path_builder ORDER BY level DESC;
                """;

        List<String> pathParts = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("name"), fileId, maxDepth);
        return String.join("/", pathParts);
    }

    @Transactional
    public List<String[]> deleteItemAndGetDescendantPaths(String externalId) {
        String selectSql = """
                WITH RECURSIVE item_tree(file_id, external_id, name, type, parent_id) AS (
                  SELECT file_id, external_id, name, type, parent_id
                  FROM files
                  WHERE external_id = ?
                  UNION ALL
                  SELECT f.file_id, f.external_id, f.name, f.type, f.parent_id
                  FROM files f
                  JOIN item_tree it ON f.parent_id = it.file_id
                )
                SELECT external_id, name, type FROM item_tree;
                """;

        String deleteSql = """
                WITH RECURSIVE item_tree(file_id) AS (
                  SELECT file_id FROM files WHERE external_id = ?
                  UNION ALL
                  SELECT f.file_id FROM files f
                  JOIN item_tree it ON f.parent_id = it.file_id
                )
                DELETE FROM files WHERE file_id IN (SELECT file_id FROM item_tree);
                """;

        List<String[]> deletedMetadata = jdbcTemplate.query(selectSql, (rs, rowNum) -> new String[]{
                rs.getString("external_id"),
                rs.getString("name"),
                rs.getString("type")
        }, externalId);

        if (!deletedMetadata.isEmpty()) {
            jdbcTemplate.update(deleteSql, externalId);
        }

        return deletedMetadata;
    }

    public record IdAndName(int fileId, String name) {
    }

    /**
     * Looks up rows by a raw type string rather than {@link FileType}, since callers such as the
     * legacy-data backfill need to match values (e.g. the old generic "FILE") that predate the enum
     * and are intentionally not members of it.
     */
    public List<IdAndName> findIdsAndNamesByRawType(String rawType) {
        String sql = "SELECT file_id, name FROM files WHERE type = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new IdAndName(rs.getInt("file_id"), rs.getString("name")), rawType);
    }

    public void updateType(int fileId, FileType type) {
        jdbcTemplate.update("UPDATE files SET type = ? WHERE file_id = ?", type.name(), fileId);
    }

    public void linkFileToRole(long fileId, long roleId, String permissionLevel) {
        String sql = """
        INSERT INTO file_roles (file_id, role_id, permission_level)
        VALUES (?, ?, ?)
        ON CONFLICT (file_id, role_id) 
        DO UPDATE SET permission_level = EXCLUDED.permission_level;
    """;

        jdbcTemplate.update(sql, fileId, roleId, permissionLevel.toUpperCase());
    }

    public void unlinkFileFromRole(long fileId, long roleId) {
        String sql = "DELETE FROM file_roles WHERE file_id = ? AND role_id = ?;";
        jdbcTemplate.update(sql, fileId, roleId);
    }
}