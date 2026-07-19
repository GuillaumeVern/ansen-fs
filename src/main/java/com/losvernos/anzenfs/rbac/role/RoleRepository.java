package com.losvernos.anzenfs.rbac.role;

import com.losvernos.anzenfs.rbac.permission.Permission;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class RoleRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Role> getAll() {
        String sql = "SELECT * FROM roles;";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Role role = new Role();
            role.setID(rs.getLong("role_id"));
            role.setName(rs.getString("role_name"));
            return role;
        });
    }

    public Optional<Role> get(long ID) {
        String sql = "SELECT * FROM roles WHERE role_id = ?;";
        try {
            Role role = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Role r = new Role();
                r.setID(rs.getLong("role_id"));
                r.setName(rs.getString("role_name"));
                return r;
            }, ID);
            return Optional.ofNullable(role);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void save(Role elementToSave) {
        String sql = "INSERT INTO roles (role_name) VALUES (?);";
        jdbcTemplate.update(sql, elementToSave.getName());
    }

    public void update(Role elementToUpdate, String[] params) {
        System.out.println("update role not implemented");
    }

    public void delete(Role elementToDelete) {
        System.out.println("delete role not implemented");
    }

    public Optional<Role> findByName(String name) {
        String sql = "SELECT role_id, role_name FROM roles WHERE role_name = ?;";

        try {
            Role role = jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                            new Role(
                                    rs.getLong("role_id"),
                                    rs.getString("role_name"),
                                    null
                            ),
                    name
            );
            return Optional.ofNullable(role);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Role> getAllWithPermissions() {
        String sql = """
                SELECT
                  r.role_id, r.role_name,
                  p.permission_id, p.permission_name
                FROM roles r
                LEFT JOIN role_permissions rp ON rp.role_id = r.role_id
                LEFT JOIN permissions p ON p.permission_id = rp.permission_id
                ORDER BY r.role_id;
                """;

        Map<Long, Role> rolesById = new LinkedHashMap<>();

        jdbcTemplate.query(sql, rs -> {
            long roleId = rs.getLong("role_id");
            Role role = rolesById.computeIfAbsent(roleId, id -> {
                Role r = new Role();
                try {
                    r.setID(id);
                    r.setName(rs.getString("role_name"));
                    r.setPermissions(new ArrayList<>());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                return r;
            });

            long permissionId = rs.getLong("permission_id");
            if (!rs.wasNull()) {
                Permission permission = new Permission();
                permission.setID(permissionId);
                permission.setName(rs.getString("permission_name"));
                role.getPermissions().add(permission);
            }
        });

        return new ArrayList<>(rolesById.values());
    }

    @Transactional
    public Role createRoleWithPermissions(String name, List<Long> permissionIds) {
        String sql = "INSERT INTO roles (role_name) VALUES (?);";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            return ps;
        }, keyHolder);

        long roleId = Objects.requireNonNull(keyHolder.getKey()).longValue();

        for (Long permissionId : permissionIds) {
            jdbcTemplate.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)", roleId, permissionId);
        }

        Role role = new Role();
        role.setID(roleId);
        role.setName(name);
        role.setPermissions(new ArrayList<>());
        return role;
    }

    public void renameRole(long id, String name) {
        jdbcTemplate.update("UPDATE roles SET role_name = ? WHERE role_id = ?", name, id);
    }

    @Transactional
    public void replaceRolePermissions(long roleId, List<Long> permissionIds) {
        jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = ?", roleId);
        for (Long permissionId : permissionIds) {
            jdbcTemplate.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)", roleId, permissionId);
        }
    }

    @Transactional
    public void deleteById(long id) {
        // SQLite foreign-key enforcement isn't enabled on this connection, so ON DELETE
        // CASCADE in schema.sql doesn't actually run - clean up dependents explicitly.
        jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = ?", id);
        jdbcTemplate.update("DELETE FROM user_roles WHERE role_id = ?", id);
        jdbcTemplate.update("DELETE FROM file_roles WHERE role_id = ?", id);
        jdbcTemplate.update("DELETE FROM roles WHERE role_id = ?", id);
    }
}