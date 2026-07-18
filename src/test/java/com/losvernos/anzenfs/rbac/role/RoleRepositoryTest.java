package com.losvernos.anzenfs.rbac.role;

import com.losvernos.anzenfs.support.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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
}
