package com.losvernos.anzenfs.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds a fresh, file-backed SQLite {@link JdbcTemplate} with the production schema applied.
 * A single kept-open JDBC connection is used so that in-flight state is visible across calls
 * (a plain per-call connection pool would otherwise reset each time for file-based SQLite).
 */
public final class TestDb {

    private TestDb() {
    }

    public static JdbcTemplate newJdbcTemplate() {
        try {
            Path dbFile = Files.createTempFile("anzenfs-test-", ".db");
            dbFile.toFile().deleteOnExit();

            SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                    "jdbc:sqlite:" + dbFile.toAbsolutePath(), true);
            dataSource.setDriverClassName("org.sqlite.JDBC");

            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

            try (InputStream in = TestDb.class.getResourceAsStream("/schema.sql")) {
                if (in == null) {
                    throw new IllegalStateException("schema.sql not found on test classpath");
                }
                String schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                for (String statement : schema.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        jdbcTemplate.execute(trimmed);
                    }
                }
            }

            return jdbcTemplate;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
