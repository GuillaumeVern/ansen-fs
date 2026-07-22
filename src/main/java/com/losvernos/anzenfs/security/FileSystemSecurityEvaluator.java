package com.losvernos.anzenfs.security;

import com.losvernos.anzenfs.rbac.role.Role;
import com.losvernos.anzenfs.rbac.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("fsSecurity")
public class FileSystemSecurityEvaluator {

    private static final Logger log = LoggerFactory.getLogger(FileSystemSecurityEvaluator.class);

    private record RoleGrant(int depth, long roleId, String level) {}

    private final JdbcTemplate jdbcTemplate;

    public FileSystemSecurityEvaluator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasAccess(String externalId, String requiredLevel) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            log.warn("Access denied: no authenticated user (externalId={}, requiredLevel={})", externalId, requiredLevel);
            return false;
        }

        List<Role> userRoles = user.getUserRoles();
        if (userRoles == null || userRoles.isEmpty()) {
            log.warn("Access denied: user={} has no roles (externalId={}, requiredLevel={})", user.getUsername(), externalId, requiredLevel);
            return false;
        }

        if (externalId == null || externalId.isBlank()) {
            externalId = "root-uuid";
        }

        boolean isAdmin = userRoles.stream().anyMatch(r -> r.getName().equalsIgnoreCase("ADMIN"));
        if (isAdmin) return true;

        String sql = """
            WITH RECURSIVE file_hierarchy(file_id, parent_id, depth) AS (
                SELECT file_id, parent_id, 0
                FROM files
                WHERE external_id = ?
                UNION ALL
                SELECT f.file_id, f.parent_id, fh.depth + 1
                FROM files f
                JOIN file_hierarchy fh ON f.file_id = fh.parent_id
            )
            SELECT fh.depth, fr.role_id, fr.permission_level
            FROM file_hierarchy fh
            JOIN file_roles fr ON fh.file_id = fr.file_id
            ORDER BY fh.depth ASC;
        """;

        List<RoleGrant> grants = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new RoleGrant(rs.getInt("depth"), rs.getLong("role_id"), rs.getString("permission_level")),
                externalId
        );

        if (grants.isEmpty()) {
            log.warn("Access denied: user={} no grants found for externalId={} (requiredLevel={})", user.getUsername(), externalId, requiredLevel);
            return false;
        }

        int nearestDepth = grants.stream().mapToInt(RoleGrant::depth).min().orElseThrow();

        List<Long> userRoleIds = userRoles.stream().map(Role::getID).toList();

        List<String> actualLevels = grants.stream()
                .filter(g -> g.depth() == nearestDepth && userRoleIds.contains(g.roleId()))
                .map(RoleGrant::level)
                .toList();

        boolean hasWritePermission = actualLevels.stream().anyMatch("WRITE"::equalsIgnoreCase);
        boolean hasReadPermission = actualLevels.stream().anyMatch("READ"::equalsIgnoreCase);

        boolean granted;
        if ("WRITE".equalsIgnoreCase(requiredLevel)) {
            granted = hasWritePermission;
        } else if ("READ".equalsIgnoreCase(requiredLevel)) {
            granted = hasReadPermission || hasWritePermission;
        } else {
            granted = false;
        }

        if (!granted) {
            log.warn("Access denied: user={} lacks {} permission on externalId={}", user.getUsername(), requiredLevel, externalId);
        }

        return granted;
    }
}