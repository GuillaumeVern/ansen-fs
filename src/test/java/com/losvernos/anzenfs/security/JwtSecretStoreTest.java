package com.losvernos.anzenfs.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecretStoreTest {

    @Test
    void generatesAndPersistsASecretOnFirstCall(@TempDir Path dataDir) {
        String secret = JwtSecretStore.loadOrGenerate(dataDir.toFile());

        assertThat(secret).isNotBlank();
        assertThat(Files.exists(dataDir.resolve("jwt.secret"))).isTrue();
    }

    @Test
    void reusesThePersistedSecretOnSubsequentCalls(@TempDir Path dataDir) {
        String first = JwtSecretStore.loadOrGenerate(dataDir.toFile());
        String second = JwtSecretStore.loadOrGenerate(dataDir.toFile());

        assertThat(second).isEqualTo(first);
    }

    @Test
    void generatesADifferentSecretPerDataDir(@TempDir Path dataDirA, @TempDir Path dataDirB) {
        String secretA = JwtSecretStore.loadOrGenerate(dataDirA.toFile());
        String secretB = JwtSecretStore.loadOrGenerate(dataDirB.toFile());

        assertThat(secretA).isNotEqualTo(secretB);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void restrictsTheSecretFileToItsOwner(@TempDir Path dataDir) throws Exception {
        JwtSecretStore.loadOrGenerate(dataDir.toFile());

        var permissions = Files.getPosixFilePermissions(dataDir.resolve("jwt.secret"));

        assertThat(permissions).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
        );
    }
}
