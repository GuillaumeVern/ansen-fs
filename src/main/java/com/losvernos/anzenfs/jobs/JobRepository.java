package com.losvernos.anzenfs.jobs;

import com.losvernos.anzenfs.database.DBManager;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

@Service
public class JobRepository {

    public void insertJob(String jobId, String parentUuid, Integer totalFiles) {
        var conn = DBManager.getInstance().getConnection();
        String sql = "INSERT INTO upload_jobs (job_id, parent_uuid, total_files) VALUES (?, ?, ?);";

        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            stmt.setString(2, parentUuid);
            stmt.setInt(3, totalFiles);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
