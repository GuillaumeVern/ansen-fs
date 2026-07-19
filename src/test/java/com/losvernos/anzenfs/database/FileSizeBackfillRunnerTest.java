package com.losvernos.anzenfs.database;

import com.losvernos.anzenfs.files.FileRepository;
import com.losvernos.anzenfs.support.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileSizeBackfillRunnerTest {

    private JdbcTemplate jdbcTemplate;
    private FileRepository fileRepository;
    private FileSizeBackfillRunner runner;

    @BeforeEach
    void setUp() {
        jdbcTemplate = TestDb.newJdbcTemplate();
        fileRepository = new FileRepository(jdbcTemplate);
        runner = new FileSizeBackfillRunner(fileRepository);
    }

    @Test
    void doesNothingWhenTheSizeColumnAlreadyExists() {
        jdbcTemplate.update(
                "INSERT INTO files (external_id, parent_id, name, type) VALUES (?, NULL, ?, ?)",
                "root-uuid", "root", "FOLDER");

        runner.run();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM files", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void addsTheSizeColumnAndBackfillsFileSizesFromDiskForDatabasesPredatingIt(@TempDir Path tempDir) throws Exception {
        Path dbFile = Files.createTempFile("anzenfs-legacy-", ".db");
        dbFile.toFile().deleteOnExit();

        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                "jdbc:sqlite:" + dbFile.toAbsolutePath(), true);
        dataSource.setDriverClassName("org.sqlite.JDBC");
        JdbcTemplate legacyJdbcTemplate = new JdbcTemplate(dataSource);

        legacyJdbcTemplate.execute("""
                CREATE TABLE files (
                    file_id     INTEGER PRIMARY KEY,
                    external_id TEXT UNIQUE NOT NULL,
                    parent_id   INTEGER,
                    name        TEXT NOT NULL,
                    type        TEXT NOT NULL,
                    file_hash   TEXT
                )
                """);
        legacyJdbcTemplate.update(
                "INSERT INTO files (external_id, parent_id, name, type) VALUES (?, ?, ?, ?)",
                "root-uuid", null, "root", "FOLDER");
        Long rootId = legacyJdbcTemplate.queryForObject(
                "SELECT file_id FROM files WHERE external_id = ?", Long.class, "root-uuid");
        legacyJdbcTemplate.update(
                "INSERT INTO files (external_id, parent_id, name, type) VALUES (?, ?, ?, ?)",
                "doc-uuid", rootId, "doc.txt", "TEXT");

        Path physicalFile = tempDir.resolve("root/doc.txt");
        Files.createDirectories(physicalFile.getParent());
        Files.writeString(physicalFile, "hello world");

        FileRepository legacyRepository = new FileRepository(legacyJdbcTemplate);
        FileSizeBackfillRunner legacyRunner = new FileSizeBackfillRunner(legacyRepository);
        ReflectionTestUtils.setField(legacyRunner, "storageRoot", tempDir);

        legacyRunner.run();

        assertThat(legacyRepository.hasSizeColumn()).isTrue();

        Long size = legacyJdbcTemplate.queryForObject(
                "SELECT size_bytes FROM files WHERE external_id = ?", Long.class, "doc-uuid");
        assertThat(size).isEqualTo(Files.size(physicalFile));
    }

    @Test
    void skipsEntriesWithNoMatchingPhysicalFile(@TempDir Path tempDir) throws Exception {
        Path dbFile = Files.createTempFile("anzenfs-legacy-", ".db");
        dbFile.toFile().deleteOnExit();

        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                "jdbc:sqlite:" + dbFile.toAbsolutePath(), true);
        dataSource.setDriverClassName("org.sqlite.JDBC");
        JdbcTemplate legacyJdbcTemplate = new JdbcTemplate(dataSource);

        legacyJdbcTemplate.execute("""
                CREATE TABLE files (
                    file_id     INTEGER PRIMARY KEY,
                    external_id TEXT UNIQUE NOT NULL,
                    parent_id   INTEGER,
                    name        TEXT NOT NULL,
                    type        TEXT NOT NULL,
                    file_hash   TEXT
                )
                """);
        legacyJdbcTemplate.update(
                "INSERT INTO files (external_id, parent_id, name, type) VALUES (?, ?, ?, ?)",
                "ghost-uuid", null, "ghost.txt", "TEXT");

        FileRepository legacyRepository = new FileRepository(legacyJdbcTemplate);
        FileSizeBackfillRunner legacyRunner = new FileSizeBackfillRunner(legacyRepository);
        ReflectionTestUtils.setField(legacyRunner, "storageRoot", tempDir);

        legacyRunner.run();

        Long size = legacyJdbcTemplate.queryForObject(
                "SELECT size_bytes FROM files WHERE external_id = ?", Long.class, "ghost-uuid");
        assertThat(size).isEqualTo(0L);
    }
}
