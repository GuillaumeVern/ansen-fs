package com.losvernos.anzenfs.jobs;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class JobRepository {

    private final JdbcTemplate jdbcTemplate;

    // Constructor injection provides the Spring-managed JdbcTemplate
    public JobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertJob(String jobId, String parentUuid, Integer totalFiles, String jobType) {
        String sql = "INSERT INTO jobs (job_id, parent_uuid, total_files, job_type) VALUES (?, ?, ?, ?);";
        jdbcTemplate.update(sql, jobId, parentUuid, totalFiles, jobType);
    }
}