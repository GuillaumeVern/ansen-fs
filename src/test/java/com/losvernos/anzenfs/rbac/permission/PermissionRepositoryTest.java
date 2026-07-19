package com.losvernos.anzenfs.rbac.permission;

import com.losvernos.anzenfs.support.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private PermissionRepository permissionRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = TestDb.newJdbcTemplate();
        permissionRepository = new PermissionRepository(jdbcTemplate);
    }

    @Test
    void saveThenGetById() {
        long id = permissionRepository.save(Permission.builder().name("READ").build());

        assertThat(permissionRepository.get(id)).isPresent();
        assertThat(permissionRepository.get(id).get().getName()).isEqualTo("READ");
    }

    @Test
    void saveReturnsTheGeneratedId() {
        long id = permissionRepository.save(Permission.builder().name("EXEC").build());

        String storedName = jdbcTemplate.queryForObject(
                "SELECT permission_name FROM permissions WHERE permission_id = ?", String.class, id);
        assertThat(storedName).isEqualTo("EXEC");
    }

    @Test
    void getIsEmptyWhenMissing() {
        assertThat(permissionRepository.get(999999L)).isEmpty();
    }

    @Test
    void getAllReturnsEverySavedPermission() {
        permissionRepository.save(Permission.builder().name("READ").build());
        permissionRepository.save(Permission.builder().name("WRITE").build());

        assertThat(permissionRepository.getAll()).extracting(Permission::getName).containsExactlyInAnyOrder("READ", "WRITE");
    }

    @Test
    void updateAndDeleteAreNoOpsThatDoNotThrow() {
        Permission permission = Permission.builder().name("TRANSIENT").build();
        permissionRepository.update(permission, new String[]{});
        permissionRepository.delete(permission);
    }

    @Test
    void deleteByIdRemovesPermissionAndItsRoleLinks() {
        long id = permissionRepository.save(Permission.builder().name("DISPOSABLE").build());
        jdbcTemplate.update("INSERT INTO roles (role_name) VALUES ('HOLDER')");
        long roleId = jdbcTemplate.queryForObject("SELECT role_id FROM roles WHERE role_name = 'HOLDER'", Long.class);
        jdbcTemplate.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)", roleId, id);

        permissionRepository.deleteById(id);

        assertThat(permissionRepository.get(id)).isEmpty();
        Integer linkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_permissions WHERE permission_id = ?", Integer.class, id);
        assertThat(linkCount).isZero();
    }
}
