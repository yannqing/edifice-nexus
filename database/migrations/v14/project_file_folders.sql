CREATE TABLE project_file_folder (
    folder_id BIGINT NOT NULL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    parent_folder_id BIGINT NOT NULL DEFAULT 0,
    folder_name VARCHAR(128) NOT NULL,
    created_by BIGINT NOT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_project_folder_name (project_id, parent_folder_id, folder_name, is_delete),
    KEY idx_project_folder_parent (project_id, parent_folder_id, is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE project_files ADD COLUMN folder_id BIGINT NULL AFTER project_stage_id,
    ADD INDEX idx_project_files_folder (project_id, folder_id, batch_header, approval_status, is_delete);
