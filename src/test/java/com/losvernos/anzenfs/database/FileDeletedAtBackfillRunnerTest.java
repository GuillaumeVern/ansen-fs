package com.losvernos.anzenfs.database;

import com.losvernos.anzenfs.files.FileRepository;
import com.losvernos.anzenfs.support.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class FileDeletedAtBackfillRunnerTest {

    private JdbcTemplate jdbcTemplate;
    private FileRepository fileRepository;
    private FileDeletedAtBackfillRunner runner;

    @BeforeEach
    void setUp() {
        jdbcTemplate = TestDb.newJdbcTemplate();
        fileRepository = new FileRepository(jdbcTemplate);
        runner = new FileDeletedAtBackfillRunner(fileRepository);
    }

    @Test
    void doesNothingWhenTheDeletedAtColumnAlreadyExists() {
        jdbcTemplate.update(
                "INSERT INTO files (external_id, parent_id, name, type) VALUES (?, NULL, ?, ?)",
                "root-uuid", "root", "FOLDER");

        runner.run();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM files", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void addsTheDeletedAtColumnForDatabasesPredatingIt() throws Exception {
        var dbFile = Files.createTempFile("anzenfs-legacy-", ".db");
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

        FileRepository legacyRepository = new FileRepository(legacyJdbcTemplate);
        FileDeletedAtBackfillRunner legacyRunner = new FileDeletedAtBackfillRunner(legacyRepository);

        legacyRunner.run();

        assertThat(legacyRepository.hasDeletedAtColumn()).isTrue();
    }
}
