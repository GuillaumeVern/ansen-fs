package com.losvernos.anzenfs.files;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.losvernos.anzenfs.database.DBManager;

@Service
public class FileRepository {

  public Optional<Integer> findIdByNameAndParent(String name, Integer parentId) {
    var sql = "SELECT file_id FROM files WHERE name = ? AND " +
        (parentId == null ? "parent_id IS NULL" : "parent_id = ?");
    try (var stmt = DBManager.getInstance().getConnection().prepareStatement(sql)) {
      stmt.setString(1, name);
      if (parentId != null)
        stmt.setInt(2, parentId);
      try (var resultSet = stmt.executeQuery()) {
        if (resultSet.next()) {
          return Optional.of(resultSet.getInt("file_id"));
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return Optional.empty();
  }

  public Optional<Integer> findIdByUuid(String uuid) {
    if (uuid == null)
      return Optional.empty();
    var sql = "SELECT file_id FROM files WHERE external_id = ?;";
    try (var stmt = DBManager.getInstance().getConnection().prepareStatement(sql)) {
      stmt.setString(1, uuid);
      try (var rs = stmt.executeQuery()) {
        if (rs.next())
          return Optional.of(rs.getInt("file_id"));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return Optional.empty();
  }

  public List<FileNode> getChildrenAfter(Integer parentId, String lastFileName, String parentUuid, int limit) {
    var result = new ArrayList<FileNode>();

    var sql = """
        SELECT * FROM files
        WHERE (? IS NULL AND parent_id IS NULL) OR (parent_id = ?)
        AND (? IS NULL OR name > ?)
        ORDER BY name
        LIMIT ?;
        """;
    try (var stmt = DBManager.getInstance().getConnection().prepareStatement(sql)) {
      if (parentId == null) {
        stmt.setNull(1, java.sql.Types.INTEGER);
        stmt.setNull(2, java.sql.Types.INTEGER);
      } else {
        stmt.setInt(1, parentId);
        stmt.setInt(2, parentId);
      }

      if (lastFileName == null) {
        stmt.setNull(3, java.sql.Types.VARCHAR);
        stmt.setNull(4, java.sql.Types.VARCHAR);
      } else {
        stmt.setString(3, lastFileName);
        stmt.setString(4, lastFileName);
      }
      stmt.setInt(5, limit);
      try (var rs = stmt.executeQuery()) {
        while (rs.next()) {
          result.add(new FileNode(
              rs.getString("external_id"),
              parentUuid,
              rs.getString("name"),
              rs.getString("type"),
              rs.getString("file_hash"),
              0L));
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return result;
  }

  public void insertFile(Integer parentId, String name, String type, String hash) {
    String sql = "INSERT INTO files (parent_id, name, type, file_hash, external_id) VALUES (?, ?, ?, ?, ?);";

    try (var stmt = DBManager.getInstance().getConnection().prepareStatement(sql)) {
      String externalId = java.util.UUID.randomUUID().toString();

      if (parentId == null)
        stmt.setNull(1, java.sql.Types.INTEGER);
      else
        stmt.setInt(1, parentId);

      stmt.setString(2, name);
      stmt.setString(3, type);
      stmt.setString(4, hash);
      stmt.setString(5, externalId);
      stmt.executeUpdate();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  public Integer createFolder(Integer parentId, String name) {
    String sql = "INSERT INTO files (parent_id, name, type, external_id) VALUES (?, ?, 'FOLDER', ?)";

    try (var stmt = DBManager.getInstance().getConnection().prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
      String externalId = java.util.UUID.randomUUID().toString();

      if (parentId == null) {
        stmt.setNull(1, java.sql.Types.INTEGER);
      } else {
        stmt.setInt(1, parentId);
      }

      stmt.setString(2, name);
      stmt.setString(3, externalId);
      stmt.executeUpdate();

      try (var rs = stmt.getGeneratedKeys()) {
        if (rs.next())
          return rs.getInt(1);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  public String getFullPath(String externalId) {
    var maxDepth = 10000;
    String resultPath = null;

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

    try (var stmt = DBManager.getInstance().getConnection().prepareStatement(sql)) {
      stmt.setString(1, externalId);
      stmt.setInt(2, maxDepth);
      try (var rs = stmt.executeQuery()) {
        List<String> pathParts = new ArrayList<>();
        while (rs.next()) {
          pathParts.add(rs.getString("name"));
        }
        resultPath = String.join("/", pathParts);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return resultPath;
  }

  public List<String[]> deleteItemAndGetDescendantPaths(String externalId) {
    List<String[]> deletedMetadata = new ArrayList<>();

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

    try {
      var connection = DBManager.getInstance().getConnection();
      connection.setAutoCommit(false);

      try {
        try (var selectStmt = connection.prepareStatement(selectSql)) {
          selectStmt.setString(1, externalId);
          try (var rs = selectStmt.executeQuery()) {
            while (rs.next()) {
              deletedMetadata.add(new String[]{
                      rs.getString("external_id"),
                      rs.getString("name"),
                      rs.getString("type")
              });
            }
          }
        }

        if (!deletedMetadata.isEmpty()) {
          try (var deleteStmt = connection.prepareStatement(deleteSql)) {
            deleteStmt.setString(1, externalId);
            deleteStmt.executeUpdate();
          }
        }

        connection.commit();
      } catch (SQLException ex) {
        connection.rollback();
        throw ex;
      } finally {
        connection.setAutoCommit(true);
      }

    } catch (SQLException e) {
      e.printStackTrace();
    }

    return deletedMetadata;
  }
}
