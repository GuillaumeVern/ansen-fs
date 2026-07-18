package com.losvernos.anzenfs.database;

import com.losvernos.anzenfs.files.FileRepository;
import com.losvernos.anzenfs.rbac.role.Role;
import com.losvernos.anzenfs.rbac.role.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DBSeeder implements CommandLineRunner {

    private final FileRepository fileRepository;
    private final RoleRepository roleRepository;

    public DBSeeder(FileRepository fileRepository, RoleRepository roleRepository) {
        this.fileRepository = fileRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (roleRepository.findByName("USER_ROLE").isEmpty()) {
            roleRepository.save(new Role("USER_ROLE"));
        }

        if (roleRepository.findByName("ADMIN").isEmpty()) {
            roleRepository.save(new Role("ADMIN"));
        }

        if (fileRepository.findIdByNameAndParent("root", null).isEmpty()) {
            fileRepository.createFolder(null, "root", "root-uuid");
        }

        long rootId = fileRepository.findIdByNameAndParent("root", null)
                .orElseThrow(() -> new IllegalStateException("System root creation validation failure"));

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role validation failure"));

        fileRepository.linkFileToRole(rootId, adminRole.getID(), "WRITE");

        // Root used to be granted READ to every USER_ROLE holder, which let any user browse
        // into any other user's folder. Revoke it here so upgrading an existing database
        // also drops the stale grant, not just fresh installs.
        roleRepository.findByName("USER_ROLE")
                .ifPresent(userRole -> fileRepository.unlinkFileFromRole(rootId, userRole.getID()));
    }
}