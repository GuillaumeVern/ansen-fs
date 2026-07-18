package com.losvernos.anzenfs.jobs;

import com.losvernos.anzenfs.support.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class JobRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private JobRepository jobRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = TestDb.newJdbcTemplate();
        jobRepository = new JobRepository(jdbcTemplate);
    }

    @Test
    void insertJobPersistsAllFields() {
        jobRepository.insertJob("job-1", "parent-uuid", 3, "UPLOAD");

        String jobType = jdbcTemplate.queryForObject(
                "SELECT job_type FROM jobs WHERE job_id = ?", String.class, "job-1");
        Integer totalFiles = jdbcTemplate.queryForObject(
                "SELECT total_files FROM jobs WHERE job_id = ?", Integer.class, "job-1");
        String parentUuid = jdbcTemplate.queryForObject(
                "SELECT parent_uuid FROM jobs WHERE job_id = ?", String.class, "job-1");
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM jobs WHERE job_id = ?", String.class, "job-1");

        assertThat(jobType).isEqualTo("UPLOAD");
        assertThat(totalFiles).isEqualTo(3);
        assertThat(parentUuid).isEqualTo("parent-uuid");
        assertThat(status).isEqualTo("PENDING");
    }
}
