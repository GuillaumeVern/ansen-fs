package com.losvernos.anzenfs.rbac.user;

import com.losvernos.anzenfs.rbac.permission.Permission;
import com.losvernos.anzenfs.rbac.role.Role;
import com.losvernos.anzenfs.support.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        jdbcTemplate = TestDb.newJdbcTemplate();
        userRepository = new UserRepository(jdbcTemplate, passwordEncoder);
    }

    @Test
    void saveEncodesPasswordAndLinksRolesAndPermissions() {
        Role role = Role.builder()
                .name("EDITOR")
                .permissions(List.of(
                        Permission.builder().name("READ").build(),
                        Permission.builder().name("WRITE").build()))
                .build();
        User user = User.builder().username("alice").password("plaintext").userRoles(List.of(role)).build();

        userRepository.save(user);

        String storedPassword = jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE username = ?", String.class, "alice");
        assertThat(storedPassword).isNotEqualTo("plaintext");
        assertThat(passwordEncoder.matches("plaintext", storedPassword)).isTrue();

        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE role_name = 'EDITOR'", Integer.class);
        assertThat(roleCount).isEqualTo(1);

        Integer permissionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM permissions WHERE permission_name IN ('READ','WRITE')", Integer.class);
        assertThat(permissionCount).isEqualTo(2);

        Integer userRoleLinks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_roles ur JOIN users u ON u.user_id = ur.user_id WHERE u.username = 'alice'",
                Integer.class);
        assertThat(userRoleLinks).isEqualTo(1);
    }

    @Test
    void saveReusesExistingRoleAndPermissionRows() {
        Role role = Role.builder().name("SHARED").permissions(List.of(Permission.builder().name("READ").build())).build();
        userRepository.save(User.builder().username("first").password("pw1").userRoles(List.of(role)).build());
        userRepository.save(User.builder().username("second").password("pw2").userRoles(List.of(role)).build());

        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE role_name = 'SHARED'", Integer.class);
        assertThat(roleCount).isEqualTo(1);

        Integer permissionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM permissions WHERE permission_name = 'READ'", Integer.class);
        assertThat(permissionCount).isEqualTo(1);
    }

    @Test
    void findByUsernameReturnsUserWithoutRoles() {
        userRepository.save(User.builder().username("bare").password("pw").build());

        Optional<User> found = userRepository.findByUsername("bare");
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("bare");
    }

    @Test
    void findByUsernameIsEmptyWhenMissing() {
        assertThat(userRepository.findByUsername("nobody")).isEmpty();
    }

    @Test
    void getReturnsUserById() {
        userRepository.save(User.builder().username("lookup").password("pw").build());
        long id = jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE username = 'lookup'", Long.class);

        assertThat(userRepository.get(id)).isPresent();
        assertThat(userRepository.get(id).get().getUsername()).isEqualTo("lookup");
        assertThat(userRepository.get(999999L)).isEmpty();
    }

    @Test
    void getAllReturnsEverySavedUser() {
        userRepository.save(User.builder().username("u1").password("pw").build());
        userRepository.save(User.builder().username("u2").password("pw").build());

        assertThat(userRepository.getAll()).extracting(User::getUsername).containsExactlyInAnyOrder("u1", "u2");
    }

    @Test
    void findByUsernameWithRolesAndPermissionsAggregatesNestedData() {
        Role role = Role.builder()
                .name("EDITOR")
                .permissions(List.of(
                        Permission.builder().name("READ").build(),
                        Permission.builder().name("WRITE").build()))
                .build();
        userRepository.save(User.builder().username("carol").password("pw").userRoles(List.of(role)).build());

        Optional<User> result = userRepository.findByUsernameWithRolesAndPermissions("carol");

        assertThat(result).isPresent();
        User user = result.get();
        assertThat(user.getUserRoles()).hasSize(1);
        Role loadedRole = user.getUserRoles().get(0);
        assertThat(loadedRole.getName()).isEqualTo("EDITOR");
        assertThat(loadedRole.getPermissions()).extracting(Permission::getName).containsExactlyInAnyOrder("READ", "WRITE");
    }

    @Test
    void findByUsernameWithRolesAndPermissionsHandlesUserWithNoRoles() {
        userRepository.save(User.builder().username("norole").password("pw").build());

        Optional<User> result = userRepository.findByUsernameWithRolesAndPermissions("norole");

        assertThat(result).isPresent();
        assertThat(result.get().getUserRoles()).isEmpty();
    }

    @Test
    void findByUsernameWithRolesAndPermissionsIsEmptyForUnknownUser() {
        assertThat(userRepository.findByUsernameWithRolesAndPermissions("ghost")).isEmpty();
    }

    @Test
    void initAdminAccountCreatesAdminOnlyOnce() {
        userRepository.initAdminAccount();

        Optional<User> admin = userRepository.findByUsername("admin");
        assertThat(admin).isPresent();

        userRepository.initAdminAccount();

        Integer adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = 'admin'", Integer.class);
        assertThat(adminCount).isEqualTo(1);
    }

    @Test
    void createPersonalFolderInsertsFolderRow() {
        long folderId = userRepository.createPersonalFolder("dave", "dave-uuid");

        String type = jdbcTemplate.queryForObject(
                "SELECT type FROM files WHERE file_id = ?", String.class, folderId);
        assertThat(type).isEqualTo("FOLDER");

        String externalId = jdbcTemplate.queryForObject(
                "SELECT external_id FROM files WHERE file_id = ?", String.class, folderId);
        assertThat(externalId).isEqualTo("dave-uuid");
    }

    @Test
    void updateAndDeleteAreNoOpsThatDoNotThrow() {
        User user = User.builder().username("noop").build();
        userRepository.update(user, new String[]{});
        userRepository.delete(user);
    }
}
