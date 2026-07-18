package com.losvernos.anzenfs.rbac.permission;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PermissionRepository {

  private final JdbcTemplate jdbcTemplate;

  public PermissionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<Permission> getAll() {
    String sql = "SELECT * FROM permissions;";
    return jdbcTemplate.query(sql, (rs, rowNum) -> {
      Permission permission = new Permission();
      permission.setID(rs.getLong("permission_id"));
      permission.setName(rs.getString("permission_name"));
      return permission;
    });
  }

  public Optional<Permission> get(long ID) {
    String sql = "SELECT * FROM permissions WHERE permission_id = ?;";
    try {
      Permission permission = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
        Permission p = new Permission();
        p.setID(rs.getLong("permission_id"));
        p.setName(rs.getString("permission_name"));
        return p;
      }, ID);
      return Optional.ofNullable(permission);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public void save(Permission elementToSave) {
    String sql = "INSERT INTO permissions (permission_name) VALUES (?);";
    jdbcTemplate.update(sql, elementToSave.getName());
  }

  public void update(Permission elementToUpdate, String[] params) {
    System.out.println("update permission not implemented");
  }

  public void delete(Permission elementToDelete) {
    System.out.println("delete permission not implemented");
  }
}