package com.qsy.edifice.service;

import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class FileUploadCleanup {
    private static final Logger log = LoggerFactory.getLogger(FileUploadCleanup.class);
    @Resource private JdbcTemplate jdbc;
    @Resource private UnifiedFileUploadService uploads;

    @Scheduled(initialDelay = 300000, fixedDelay = 3600000)
    public void expiredUploads() {
        jdbc.query("SELECT upload_id,user_id FROM file_upload_session WHERE status='uploading' AND expires_time<NOW() LIMIT 100",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> {
                    try { uploads.cancel(row.getString(1), row.getLong(2)); }
                    catch (Exception e) { log.warn("Temporary upload cleanup failed: {}", row.getString(1), e); }
                });
    }
}
