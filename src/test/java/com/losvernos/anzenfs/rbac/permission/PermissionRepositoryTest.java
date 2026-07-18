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
        permissionRepository.save(Permission.builder().name("READ").build());
        long id = jdbcTemplate.queryForObject("SELECT permission_id FROM permissions WHERE permission_name = 'READ'", Long.class);

        assertThat(permissionRepository.get(id)).isPresent();
        assertThat(permissionRepository.get(id).get().getName()).isEqualTo("READ");
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
}
