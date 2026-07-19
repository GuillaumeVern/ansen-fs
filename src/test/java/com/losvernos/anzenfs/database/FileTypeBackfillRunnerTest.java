package com.losvernos.anzenfs.database;

import com.losvernos.anzenfs.files.FileRepository;
import com.losvernos.anzenfs.support.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeBackfillRunnerTest {

    private JdbcTemplate jdbcTemplate;
    private FileRepository fileRepository;
    private FileTypeBackfillRunner runner;

    @BeforeEach
    void setUp() {
        jdbcTemplate = TestDb.newJdbcTemplate();
        fileRepository = new FileRepository(jdbcTemplate);
        runner = new FileTypeBackfillRunner(fileRepository);
    }

    private void insertLegacyRow(String name, String type) {
        jdbcTemplate.update(
                "INSERT INTO files (external_id, parent_id, name, type) VALUES (?, NULL, ?, ?)",
                "ext-" + name, name, type);
    }

    @Test
    void reclassifiesRowsStillTaggedWithTheLegacyGenericType() {
        insertLegacyRow("photo.jpg", "FILE");
        insertLegacyRow("clip.mp4", "FILE");
        insertLegacyRow("root", "FOLDER");

        runner.run();

        String photoType = jdbcTemplate.queryForObject(
                "SELECT type FROM files WHERE name = 'photo.jpg'", String.class);
        String clipType = jdbcTemplate.queryForObject(
                "SELECT type FROM files WHERE name = 'clip.mp4'", String.class);
        String rootType = jdbcTemplate.queryForObject(
                "SELECT type FROM files WHERE name = 'root'", String.class);

        assertThat(photoType).isEqualTo("IMAGE");
        assertThat(clipType).isEqualTo("VIDEO");
        assertThat(rootType).isEqualTo("FOLDER");
    }

    @Test
    void isIdempotentOnceEverythingIsReclassified() {
        insertLegacyRow("photo.jpg", "FILE");

        runner.run();
        runner.run();

        Integer remainingLegacyRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM files WHERE type = 'FILE'", Integer.class);
        assertThat(remainingLegacyRows).isZero();

        String photoType = jdbcTemplate.queryForObject(
                "SELECT type FROM files WHERE name = 'photo.jpg'", String.class);
        assertThat(photoType).isEqualTo("IMAGE");
    }

    @Test
    void doesNothingWhenNoLegacyRowsExist() {
        insertLegacyRow("photo.jpg", "IMAGE");

        runner.run();

        String photoType = jdbcTemplate.queryForObject(
                "SELECT type FROM files WHERE name = 'photo.jpg'", String.class);
        assertThat(photoType).isEqualTo("IMAGE");
    }
}
