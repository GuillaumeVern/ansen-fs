package com.losvernos.anzenfs.database;

import com.losvernos.anzenfs.files.FileRepository;
import com.losvernos.anzenfs.rbac.role.Role;
import com.losvernos.anzenfs.rbac.role.RoleRepository;
import com.losvernos.anzenfs.support.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DBSeederTest {

    private JdbcTemplate jdbcTemplate;
    private FileRepository fileRepository;
    private RoleRepository roleRepository;
    private DBSeeder dbSeeder;

    @BeforeEach
    void setUp() {
        jdbcTemplate = TestDb.newJdbcTemplate();
        fileRepository = new FileRepository(jdbcTemplate);
        roleRepository = new RoleRepository(jdbcTemplate);
        dbSeeder = new DBSeeder(fileRepository, roleRepository);
    }

    @Test
    void runCreatesCoreRolesAndRootFolder() {
        dbSeeder.run();

        assertThat(roleRepository.findByName("USER_ROLE")).isPresent();
        assertThat(roleRepository.findByName("ADMIN")).isPresent();
        assertThat(fileRepository.findIdByNameAndParent("root", null)).isPresent();
    }

    @Test
    void runGrantsAdminWriteOnRoot() {
        dbSeeder.run();

        long rootId = fileRepository.findIdByNameAndParent("root", null).orElseThrow();
        long adminRoleId = roleRepository.findByName("ADMIN").orElseThrow().getID();

        String level = jdbcTemplate.queryForObject(
                "SELECT permission_level FROM file_roles WHERE file_id = ? AND role_id = ?",
                String.class, rootId, adminRoleId);
        assertThat(level).isEqualTo("WRITE");
    }

    @Test
    void runRevokesStaleUserRoleGrantOnRoot() {
        dbSeeder.run();
        long rootId = fileRepository.findIdByNameAndParent("root", null).orElseThrow();
        long userRoleId = roleRepository.findByName("USER_ROLE").orElseThrow().getID();

        // Simulate the stale grant this migration step is designed to clean up.
        fileRepository.linkFileToRole(rootId, userRoleId, "READ");

        dbSeeder.run();

        Integer grantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_roles WHERE file_id = ? AND role_id = ?",
                Integer.class, rootId, userRoleId);
        assertThat(grantCount).isZero();
    }

    @Test
    void runIsIdempotent() {
        dbSeeder.run();
        dbSeeder.run();

        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE role_name IN ('ADMIN', 'USER_ROLE')", Integer.class);
        assertThat(roleCount).isEqualTo(2);

        Integer rootCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM files WHERE name = 'root' AND parent_id IS NULL", Integer.class);
        assertThat(rootCount).isEqualTo(1);
    }
}
