-- 统一消息中心：审批结果通知所需的流程发起人字段。
-- 请在 edifice_db 中手动执行。

SET NAMES utf8mb4;

SET @exists_apply_user_id := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'approval_records'
      AND COLUMN_NAME = 'apply_user_id'
);

SET @sql_apply_user_id := IF(
    @exists_apply_user_id = 0,
    'ALTER TABLE approval_records ADD COLUMN apply_user_id bigint NULL COMMENT ''审批流程发起人id'' AFTER approver',
    'SELECT 1'
);
PREPARE stmt_apply_user_id FROM @sql_apply_user_id;
EXECUTE stmt_apply_user_id;
DEALLOCATE PREPARE stmt_apply_user_id;

SET @exists_apply_user_index := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'approval_records'
      AND INDEX_NAME = 'idx_ar_apply_user'
);

SET @sql_apply_user_index := IF(
    @exists_apply_user_index = 0,
    'ALTER TABLE approval_records ADD INDEX idx_ar_apply_user (apply_user_id)',
    'SELECT 1'
);
PREPARE stmt_apply_user_index FROM @sql_apply_user_index;
EXECUTE stmt_apply_user_index;
DEALLOCATE PREPARE stmt_apply_user_index;

SET @exists_approver_status_index := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'approval_records'
      AND INDEX_NAME = 'idx_ar_approver_status'
);

SET @sql_approver_status_index := IF(
    @exists_approver_status_index = 0,
    'ALTER TABLE approval_records ADD INDEX idx_ar_approver_status (approver, inspection_form_status)',
    'SELECT 1'
);
PREPARE stmt_approver_status_index FROM @sql_approver_status_index;
EXECUTE stmt_approver_status_index;
DEALLOCATE PREPARE stmt_approver_status_index;

SET @exists_apply_status_time_index := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'approval_records'
      AND INDEX_NAME = 'idx_ar_apply_status_time'
);

SET @sql_apply_status_time_index := IF(
    @exists_apply_status_time_index = 0,
    'ALTER TABLE approval_records ADD INDEX idx_ar_apply_status_time (apply_user_id, inspection_form_status, updated_time)',
    'SELECT 1'
);
PREPARE stmt_apply_status_time_index FROM @sql_apply_status_time_index;
EXECUTE stmt_apply_status_time_index;
DEALLOCATE PREPARE stmt_apply_status_time_index;
