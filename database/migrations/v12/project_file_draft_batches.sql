-- Back up the database first. Apply once, before starting the new backend.
-- Existing file IDs, files and approval chains are not rewritten.
ALTER TABLE project_files
    MODIFY COLUMN file_id BIGINT NULL COMMENT 'Physical file ID; batch headers have no file',
    ADD COLUMN batch_header TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN batch_id BIGINT NULL,
    ADD COLUMN draft_expires_at DATETIME NULL,
    ADD COLUMN submitted_time DATETIME NULL,
    ADD INDEX idx_pf_batch (batch_id, is_delete),
    ADD INDEX idx_pf_draft_file (project_id, file_id, upload_user_id, approval_status, is_delete),
    ADD INDEX idx_pf_draft (upload_user_id, approval_status, draft_expires_at, is_delete),
    ADD INDEX idx_pf_expiry (approval_status, draft_expires_at, is_delete);

-- Draft expiration is logical only. Do not delete MinIO objects: files may be
-- referenced elsewhere, and the application's storage credentials cannot delete.
