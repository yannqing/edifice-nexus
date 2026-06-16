-- 合同管理 + 项目归档增强：归档独立字段、合同变更日志。
-- 请在 edifice_db 中执行。

SET NAMES utf8mb4;

SET @exists_archive_status := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'project'
      AND COLUMN_NAME = 'archive_status'
);
SET @sql_archive_status := IF(
    @exists_archive_status = 0,
    'ALTER TABLE project ADD COLUMN archive_status tinyint NOT NULL DEFAULT 0 COMMENT ''归档状态：0-未归档/1-已归档'' AFTER project_end_time',
    'SELECT 1'
);
PREPARE stmt_archive_status FROM @sql_archive_status;
EXECUTE stmt_archive_status;
DEALLOCATE PREPARE stmt_archive_status;

SET @exists_archive_time := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'project'
      AND COLUMN_NAME = 'archive_time'
);
SET @sql_archive_time := IF(
    @exists_archive_time = 0,
    'ALTER TABLE project ADD COLUMN archive_time datetime NULL COMMENT ''归档时间'' AFTER archive_status',
    'SELECT 1'
);
PREPARE stmt_archive_time FROM @sql_archive_time;
EXECUTE stmt_archive_time;
DEALLOCATE PREPARE stmt_archive_time;

SET @exists_archive_user := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'project'
      AND COLUMN_NAME = 'archive_user_id'
);
SET @sql_archive_user := IF(
    @exists_archive_user = 0,
    'ALTER TABLE project ADD COLUMN archive_user_id bigint NULL COMMENT ''归档操作人'' AFTER archive_time',
    'SELECT 1'
);
PREPARE stmt_archive_user FROM @sql_archive_user;
EXECUTE stmt_archive_user;
DEALLOCATE PREPARE stmt_archive_user;

SET @exists_archive_remark := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'project'
      AND COLUMN_NAME = 'archive_remark'
);
SET @sql_archive_remark := IF(
    @exists_archive_remark = 0,
    'ALTER TABLE project ADD COLUMN archive_remark varchar(500) NULL COMMENT ''归档备注'' AFTER archive_user_id',
    'SELECT 1'
);
PREPARE stmt_archive_remark FROM @sql_archive_remark;
EXECUTE stmt_archive_remark;
DEALLOCATE PREPARE stmt_archive_remark;

SET @exists_project_archive_index := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'project'
      AND INDEX_NAME = 'idx_project_archive_status'
);
SET @sql_project_archive_index := IF(
    @exists_project_archive_index = 0,
    'ALTER TABLE project ADD INDEX idx_project_archive_status (archive_status, updated_time)',
    'SELECT 1'
);
PREPARE stmt_project_archive_index FROM @sql_project_archive_index;
EXECUTE stmt_project_archive_index;
DEALLOCATE PREPARE stmt_project_archive_index;

UPDATE project
SET archive_status = 1,
    archive_time = COALESCE(archive_time, updated_time)
WHERE project_status = 4
  AND archive_status = 0
  AND is_delete = 0;

CREATE TABLE IF NOT EXISTS contract_change_log (
    change_log_id bigint NOT NULL COMMENT '合同变更日志ID',
    contract_id bigint NOT NULL COMMENT '合同ID',
    project_id bigint NULL COMMENT '项目ID',
    field_name varchar(64) NOT NULL COMMENT '字段名',
    field_label varchar(100) NOT NULL COMMENT '字段中文名',
    old_value text NULL COMMENT '变更前',
    new_value text NULL COMMENT '变更后',
    operator_id bigint NULL COMMENT '操作人ID',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    is_delete tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (change_log_id),
    KEY idx_contract_change_contract (contract_id, created_time),
    KEY idx_contract_change_project (project_id, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同字段变更日志';
