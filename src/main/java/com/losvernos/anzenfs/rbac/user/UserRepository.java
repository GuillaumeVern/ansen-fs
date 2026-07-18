package com.losvernos.anzenfs.rbac.user;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.losvernos.anzenfs.rbac.permission.Permission;
import com.losvernos.anzenfs.rbac.role.Role;

@Service
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserRepository(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    private final RowMapper<User> userRowMapper = (rs, rowNum) -> {
        User user = new User();
        user.setID(rs.getLong("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        return user;
    };

    public List<User> getAll() {
        String sql = "SELECT * FROM users;";
        return jdbcTemplate.query(sql, userRowMapper);
    }

    public List<User> getAllWithRoles() {
        String sql = """
                SELECT
                  u.user_id, u.username, u.password,
                  r.role_id, r.role_name
                FROM users u
                LEFT JOIN user_roles ur ON ur.user_id = u.user_id
                LEFT JOIN roles r ON r.role_id = ur.role_id
                ORDER BY u.user_id;
                """;

        Map<Long, User> usersById = new LinkedHashMap<>();

        jdbcTemplate.query(sql, rs -> {
            long userId = rs.getLong("user_id");
            User user = usersById.computeIfAbsent(userId, id -> {
                User u = new User();
                try {
                    u.setID(id);
                    u.setUsername(rs.getString("username"));
                    u.setPassword(rs.getString("password"));
                    u.setUserRoles(new ArrayList<>());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                return u;
            });

            long roleId = rs.getLong("role_id");
            if (!rs.wasNull()) {
                Role role = new Role();
                role.setID(roleId);
                role.setName(rs.getString("role_name"));
                role.setPermissions(new ArrayList<>());
                user.getUserRoles().add(role);
            }
        });

        return new ArrayList<>(usersById.values());
    }

    @Transactional
    public void replaceUserRoles(long userId, List<Long> roleIds) {
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        for (Long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
    }

    @Transactional
    public void deleteById(long userId) {
        // SQLite foreign-key enforcement isn't enabled on this connection, so ON DELETE
        // CASCADE in schema.sql doesn't actually run - clean up dependents explicitly.
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    public void updatePassword(long userId, String encodedPassword) {
        jdbcTemplate.update("UPDATE users SET password = ? WHERE user_id = ?", encodedPassword, userId);
    }

    public Optional<User> get(long ID) {
        String sql = "SELECT * FROM users WHERE user_id = ?;";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, ID);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?;";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, username);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByUsernameWithRolesAndPermissions(String username) {
        String sql = """
                SELECT
                  u.user_id, u.username, u.password,
                  r.role_id, r.role_name,
                  p.permission_id, p.permission_name
                FROM users u
                LEFT JOIN user_roles ur ON ur.user_id = u.user_id
                LEFT JOIN roles r ON r.role_id = ur.role_id
                LEFT JOIN role_permissions rp ON rp.role_id = r.role_id
                LEFT JOIN permissions p ON p.permission_id = rp.permission_id
                WHERE u.username = ?;
                """;

        Map<Long, Role> rolesById = new LinkedHashMap<>();
        Map<Long, Set<Long>> permissionIdsByRole = new HashMap<>();
        final User[] userContainer = new User[1];

        jdbcTemplate.query(sql, rs -> {
            if (userContainer[0] == null) {
                userContainer[0] = new User();
                userContainer[0].setID(rs.getLong("user_id"));
                userContainer[0].setUsername(rs.getString("username"));
                userContainer[0].setPassword(rs.getString("password"));
            }

            long roleId = rs.getLong("role_id");
            if (!rs.wasNull()) {
                Role role = rolesById.computeIfAbsent(roleId, id -> {
                    Role r = new Role();
                    try {
                        r.setID(id);
                        r.setName(rs.getString("role_name"));
                        r.setPermissions(new ArrayList<>());
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    permissionIdsByRole.put(id, new HashSet<>());
                    return r;
                });

                long permissionId = rs.getLong("permission_id");
                if (!rs.wasNull() && permissionIdsByRole.get(roleId).add(permissionId)) {
                    Permission permission = new Permission();
                    permission.setID(permissionId);
                    permission.setName(rs.getString("permission_name"));
                    role.getPermissions().add(permission);
                }
            }
        }, username);

        if (userContainer[0] == null) {
            return Optional.empty();
        }

        userContainer[0].setUserRoles(new ArrayList<>(rolesById.values()));
        return Optional.of(userContainer[0]);
    }

    @Transactional
    public void save(User elementToSave) {
        String userSql = "INSERT INTO users (username, password) VALUES (?, ?);";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, elementToSave.getUsername());
            ps.setString(2, passwordEncoder.encode(elementToSave.getPassword()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new RuntimeException("Failed to create user: No generated key returned.");
        }
        long userId = key.longValue();

        if (elementToSave.getUserRoles() != null) {
            for (Role role : elementToSave.getUserRoles()) {
                long roleId = getOrCreateRoleId(role.getName());
                linkUserRole(userId, roleId);

                if (role.getPermissions() != null) {
                    for (Permission permission : role.getPermissions()) {
                        long permissionId = getOrCreatePermissionId(permission.getName());
                        linkRolePermission(roleId, permissionId);
                    }
                }
            }
        }
    }

    private long getOrCreateRoleId(String roleName) {
        String selectSql = "SELECT role_id FROM roles WHERE role_name = ?;";
        try {
            return jdbcTemplate.queryForObject(selectSql, Long.class, roleName);
        } catch (EmptyResultDataAccessException e) {
            String insertSql = "INSERT INTO roles (role_name) VALUES (?);";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, roleName);
                return ps;
            }, keyHolder);
            return Objects.requireNonNull(keyHolder.getKey()).longValue();
        }
    }

    private long getOrCreatePermissionId(String permissionName) {
        String selectSql = "SELECT permission_id FROM permissions WHERE permission_name = ?;";
        try {
            return jdbcTemplate.queryForObject(selectSql, Long.class, permissionName);
        } catch (EmptyResultDataAccessException e) {
            String insertSql = "INSERT INTO permissions (permission_name) VALUES (?);";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, permissionName);
                return ps;
            }, keyHolder);
            return Objects.requireNonNull(keyHolder.getKey()).longValue();
        }
    }

    private void linkUserRole(long userId, long roleId) {
        String sql = """
                INSERT INTO user_roles (user_id, role_id)
                SELECT ?, ?
                WHERE NOT EXISTS (
                  SELECT 1 FROM user_roles WHERE user_id = ? AND role_id = ?
                );
                """;
        jdbcTemplate.update(sql, userId, roleId, userId, roleId);
    }

    private void linkRolePermission(long roleId, long permissionId) {
        String sql = """
                INSERT INTO role_permissions (role_id, permission_id)
                SELECT ?, ?
                WHERE NOT EXISTS (
                  SELECT 1 FROM role_permissions WHERE role_id = ? AND permission_id = ?
                );
                """;
        jdbcTemplate.update(sql, roleId, permissionId, roleId, permissionId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initAdminAccount() {
        User adminInDatabase = this.findByUsername("admin").orElse(new User());
        if (null == adminInDatabase.getUsername()) {
            User adminAccount = User.builder()
                    .username("admin")
                    .password("admin")
                    .userRoles(
                            List.of(
                                    Role.builder()
                                            .name("ADMIN")
                                            .permissions(
                                                    List.of(
                                                            Permission.builder().name("ADMIN_READ").build(),
                                                            Permission.builder().name("ADMIN_WRITE").build() // Fixed likely copy-paste duplicate
                                                    )
                                            )
                                            .build()
                            )
                    )
                    .build();

            save(adminAccount);
        }
    }

    public void update(User elementToUpdate, String[] params) {
        System.out.println("update user not implemented");
    }

    public void delete(User elementToDelete) {
        System.out.println("delete user not implemented");
    }

    public long createPersonalFolder(String folderName, String externalUuid) {
        String sql = "INSERT INTO files (name, type, external_id, parent_id, file_hash) VALUES (?, 'FOLDER', ?, NULL, '');";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, folderName);
            ps.setString(2, externalUuid);
            return ps;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }
}