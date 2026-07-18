package com.losvernos.anzenfs.rbac.role;

import com.losvernos.anzenfs.rbac.permission.Permission;
import com.losvernos.anzenfs.support.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RoleRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = TestDb.newJdbcTemplate();
        roleRepository = new RoleRepository(jdbcTemplate);
    }

    @Test
    void saveThenFindByName() {
        roleRepository.save(new Role("ADMIN"));

        Optional<Role> found = roleRepository.findByName("ADMIN");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("ADMIN");
    }

    @Test
    void findByNameIsEmptyWhenMissing() {
        assertThat(roleRepository.findByName("MISSING")).isEmpty();
    }

    @Test
    void getReturnsRoleById() {
        roleRepository.save(new Role("USER_ROLE"));
        long id = jdbcTemplate.queryForObject("SELECT role_id FROM roles WHERE role_name = 'USER_ROLE'", Long.class);

        assertThat(roleRepository.get(id)).isPresent();
        assertThat(roleRepository.get(999999L)).isEmpty();
    }

    @Test
    void getAllReturnsEverySavedRole() {
        roleRepository.save(new Role("ADMIN"));
        roleRepository.save(new Role("USER_ROLE"));

        assertThat(roleRepository.getAll()).extracting(Role::getName).containsExactlyInAnyOrder("ADMIN", "USER_ROLE");
    }

    @Test
    void updateAndDeleteAreNoOpsThatDoNotThrow() {
        Role role = new Role("TRANSIENT");
        roleRepository.update(role, new String[]{});
        roleRepository.delete(role);
    }

    private long insertPermission(String name) {
        jdbcTemplate.update("INSERT INTO permissions (permission_name) VALUES (?)", name);
        return jdbcTemplate.queryForObject("SELECT permission_id FROM permissions WHERE permission_name = ?", Long.class, name);
    }

    @Test
    void createRoleWithPermissionsLinksAllGivenPermissions() {
        long readId = insertPermission("READ");
        long writeId = insertPermission("WRITE");

        Role created = roleRepository.createRoleWithPermissions("EDITOR", List.of(readId, writeId));

        assertThat(created.getName()).isEqualTo("EDITOR");
        Integer linkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_permissions WHERE role_id = ?", Integer.class, created.getID());
        assertThat(linkCount).isEqualTo(2);
    }

    @Test
    void createRoleWithPermissionsAllowsEmptyPermissionList() {
        Role created = roleRepository.createRoleWithPermissions("BARE", List.of());

        assertThat(created.getName()).isEqualTo("BARE");
        Integer linkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_permissions WHERE role_id = ?", Integer.class, created.getID());
        assertThat(linkCount).isZero();
    }

    @Test
    void getAllWithPermissionsAggregatesNestedPermissions() {
        long readId = insertPermission("READ");
        Role role = roleRepository.createRoleWithPermissions("VIEWER", List.of(readId));
        roleRepository.createRoleWithPermissions("EMPTY_ROLE", List.of());

        List<Role> all = roleRepository.getAllWithPermissions();

        Role viewer = all.stream().filter(r -> r.getID() == role.getID()).findFirst().orElseThrow();
        assertThat(viewer.getPermissions()).extracting(Permission::getName).containsExactly("READ");

        Role empty = all.stream().filter(r -> r.getName().equals("EMPTY_ROLE")).findFirst().orElseThrow();
        assertThat(empty.getPermissions()).isEmpty();
    }

    @Test
    void renameRoleUpdatesTheName() {
        Role role = roleRepository.createRoleWithPermissions("OLD_NAME", List.of());

        roleRepository.renameRole(role.getID(), "NEW_NAME");

        assertThat(roleRepository.get(role.getID())).isPresent();
        assertThat(roleRepository.get(role.getID()).get().getName()).isEqualTo("NEW_NAME");
    }

    @Test
    void replaceRolePermissionsSwapsPermissionSet() {
        long readId = insertPermission("READ");
        long writeId = insertPermission("WRITE");
        Role role = roleRepository.createRoleWithPermissions("SWAPPER", List.of(readId));

        roleRepository.replaceRolePermissions(role.getID(), List.of(writeId));

        List<Long> permissionIds = jdbcTemplate.queryForList(
                "SELECT permission_id FROM role_permissions WHERE role_id = ?", Long.class, role.getID());
        assertThat(permissionIds).containsExactly(writeId);
    }

    @Test
    void deleteByIdRemovesRoleAndItsPermissionLinks() {
        long readId = insertPermission("READ");
        Role role = roleRepository.createRoleWithPermissions("DISPOSABLE", List.of(readId));

        roleRepository.deleteById(role.getID());

        assertThat(roleRepository.get(role.getID())).isEmpty();
        Integer linkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_permissions WHERE role_id = ?", Integer.class, role.getID());
        assertThat(linkCount).isZero();
    }
}
