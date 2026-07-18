package com.losvernos.anzenfs.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileUtilsTest {

    @Test
    void getDataDirReturnsExistingAnzenfsDirectory() {
        File dataDir = FileUtils.getDataDir();

        assertThat(dataDir.exists()).isTrue();
        assertThat(dataDir.isDirectory()).isTrue();
        assertThat(dataDir.getName()).isEqualTo("anzenfs");
    }

    @Test
    void getDataDirIsIdempotentAcrossCalls() {
        File first = FileUtils.getDataDir();
        File second = FileUtils.getDataDir();

        assertThat(first).isEqualTo(second);
        assertThat(second.exists()).isTrue();
    }

    @Test
    void deleteDirectoryRemovesFilesAndSubfolders(@TempDir Path tempDir) throws Exception {
        Path nested = tempDir.resolve("sub");
        Files.createDirectories(nested);
        Files.writeString(tempDir.resolve("top.txt"), "content");
        Files.writeString(nested.resolve("inner.txt"), "content");

        FileUtils.deleteDirectory(tempDir);

        assertThat(Files.exists(tempDir)).isFalse();
    }
}
