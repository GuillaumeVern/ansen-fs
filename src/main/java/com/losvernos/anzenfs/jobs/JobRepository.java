package com.losvernos.anzenfs.jobs;

import com.losvernos.anzenfs.database.DBManager;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

@Service
public class JobRepository {

    public void insertJob(String jobId, String parentUuid, Integer totalFiles, String jobType) {
        var conn = DBManager.getInstance().getConnection();
        String sql = "INSERT INTO jobs (job_id, parent_uuid, total_files, job_type) VALUES (?, ?, ?, ?);";

        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            stmt.setString(2, parentUuid);
            stmt.setInt(3, totalFiles);
            stmt.setString(4, jobType);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
