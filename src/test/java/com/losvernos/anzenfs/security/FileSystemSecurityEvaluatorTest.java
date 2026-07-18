package com.losvernos.anzenfs.security;

import com.losvernos.anzenfs.rbac.role.Role;
import com.losvernos.anzenfs.rbac.user.User;
import com.losvernos.anzenfs.support.TestDb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemSecurityEvaluatorTest {

    private JdbcTemplate jdbcTemplate;
    private FileSystemSecurityEvaluator evaluator;

    @BeforeEach
    void setUp() {
        jdbcTemplate = TestDb.newJdbcTemplate();
        evaluator = new FileSystemSecurityEvaluator(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private long insertFile(String name, Long parentId, String externalId) {
        jdbcTemplate.update(
                "INSERT INTO files (external_id, parent_id, name, type) VALUES (?, ?, ?, 'FOLDER')",
                externalId, parentId, name);
        return jdbcTemplate.queryForObject("SELECT file_id FROM files WHERE external_id = ?", Long.class, externalId);
    }

    private void grant(long fileId, long roleId, String level) {
        jdbcTemplate.update(
                "INSERT INTO file_roles (file_id, role_id, permission_level) VALUES (?, ?, ?)",
                fileId, roleId, level);
    }

    @Test
    void deniesWhenNoAuthentication() {
        SecurityContextHolder.clearContext();
        assertThat(evaluator.hasAccess("root-uuid", "READ")).isFalse();
    }

    @Test
    void deniesWhenPrincipalIsNotAUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("not-a-user", null, List.of()));
        assertThat(evaluator.hasAccess("root-uuid", "READ")).isFalse();
    }

    @Test
    void deniesWhenUserHasNoRoles() {
        User user = User.builder().username("nobody").userRoles(List.of()).build();
        authenticateAs(user);
        assertThat(evaluator.hasAccess("root-uuid", "READ")).isFalse();
    }

    @Test
    void adminAlwaysHasAccess() {
        Role admin = new Role(1L, "ADMIN", null);
        User user = User.builder().username("root").userRoles(List.of(admin)).build();
        authenticateAs(user);

        assertThat(evaluator.hasAccess("anything-at-all", "WRITE")).isTrue();
    }

    @Test
    void deniesWhenNoGrantsExistForFile() {
        insertFile("orphan", null, "orphan-uuid");
        Role role = new Role(5L, "USER_ROLE", null);
        User user = User.builder().username("eve").userRoles(List.of(role)).build();
        authenticateAs(user);

        assertThat(evaluator.hasAccess("orphan-uuid", "READ")).isFalse();
    }

    @Test
    void grantsReadWhenRoleHasReadPermission() {
        long fileId = insertFile("doc", null, "doc-uuid");
        Role role = new Role(7L, "USER_ROLE", null);
        grant(fileId, 7L, "READ");

        User user = User.builder().username("frank").userRoles(List.of(role)).build();
        authenticateAs(user);

        assertThat(evaluator.hasAccess("doc-uuid", "READ")).isTrue();
        assertThat(evaluator.hasAccess("doc-uuid", "WRITE")).isFalse();
    }

    @Test
    void writeGrantAlsoSatisfiesReadRequirement() {
        long fileId = insertFile("doc", null, "doc-uuid");
        Role role = new Role(8L, "USER_ROLE", null);
        grant(fileId, 8L, "WRITE");

        User user = User.builder().username("gina").userRoles(List.of(role)).build();
        authenticateAs(user);

        assertThat(evaluator.hasAccess("doc-uuid", "READ")).isTrue();
        assertThat(evaluator.hasAccess("doc-uuid", "WRITE")).isTrue();
    }

    @Test
    void nearestAncestorGrantTakesPrecedenceOverFartherOne() {
        long rootId = insertFile("root", null, "root-uuid-x");
        long folderAId = insertFile("folderA", rootId, "folder-a");
        long folderBId = insertFile("folderB", folderAId, "folder-b");
        insertFile("leaf", folderBId, "leaf-uuid");

        Role role = new Role(9L, "USER_ROLE", null);
        grant(folderAId, 9L, "WRITE"); // farther ancestor grants WRITE
        grant(folderBId, 9L, "READ");  // nearer ancestor only grants READ

        User user = User.builder().username("hank").userRoles(List.of(role)).build();
        authenticateAs(user);

        assertThat(evaluator.hasAccess("leaf-uuid", "READ")).isTrue();
        assertThat(evaluator.hasAccess("leaf-uuid", "WRITE")).isFalse();
    }

    @Test
    void nullOrBlankExternalIdResolvesToRootUuid() {
        long rootId = insertFile("root", null, "root-uuid");
        Role role = new Role(10L, "USER_ROLE", null);
        grant(rootId, 10L, "READ");

        User user = User.builder().username("ivan").userRoles(List.of(role)).build();
        authenticateAs(user);

        assertThat(evaluator.hasAccess(null, "READ")).isTrue();
        assertThat(evaluator.hasAccess("", "READ")).isTrue();
    }

    @Test
    void deniesWhenUserRoleDoesNotMatchGrantedRole() {
        long fileId = insertFile("doc", null, "doc-uuid");
        grant(fileId, 11L, "READ");

        Role differentRole = new Role(12L, "OTHER_ROLE", null);
        User user = User.builder().username("judy").userRoles(List.of(differentRole)).build();
        authenticateAs(user);

        assertThat(evaluator.hasAccess("doc-uuid", "READ")).isFalse();
    }
}
