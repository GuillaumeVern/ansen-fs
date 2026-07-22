package com.losvernos.anzenfs.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPasswordStoreTest {

    @Test
    void generatesAndPersistsAPasswordOnFirstCall(@TempDir Path dataDir) {
        String password = AdminPasswordStore.loadOrGenerate(dataDir.toFile());

        assertThat(password).isNotBlank();
        assertThat(Files.exists(dataDir.resolve("admin.initial-password"))).isTrue();
    }

    @Test
    void reusesThePersistedPasswordOnSubsequentCalls(@TempDir Path dataDir) {
        String first = AdminPasswordStore.loadOrGenerate(dataDir.toFile());
        String second = AdminPasswordStore.loadOrGenerate(dataDir.toFile());

        assertThat(second).isEqualTo(first);
    }

    @Test
    void generatesADifferentPasswordPerDataDir(@TempDir Path dataDirA, @TempDir Path dataDirB) {
        String passwordA = AdminPasswordStore.loadOrGenerate(dataDirA.toFile());
        String passwordB = AdminPasswordStore.loadOrGenerate(dataDirB.toFile());

        assertThat(passwordA).isNotEqualTo(passwordB);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void restrictsThePasswordFileToItsOwner(@TempDir Path dataDir) throws Exception {
        AdminPasswordStore.loadOrGenerate(dataDir.toFile());

        var permissions = Files.getPosixFilePermissions(dataDir.resolve("admin.initial-password"));

        assertThat(permissions).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
        );
    }
}
