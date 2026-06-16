-- 审批流一致性：同一业务在任意时刻只能存在一个待审批节点。
-- 执行前如已有重复待审批节点，唯一索引创建会失败；应先人工确认并处理重复数据。

SET NAMES utf8mb4;

SET @exists_pending_key := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'approval_records'
      AND COLUMN_NAME = 'pending_business_key'
);

SET @sql_pending_key := IF(
    @exists_pending_key = 0,
    'ALTER TABLE approval_records ADD COLUMN pending_business_key varchar(128) GENERATED ALWAYS AS (CASE WHEN inspection_form_status = 0 AND is_delete = 0 THEN CONCAT(COALESCE(biz_type_ext, CONCAT(''code:'', approval_record_type)), '':'', inspection_form_id) ELSE NULL END) STORED COMMENT ''待审批业务唯一键''',
    'SELECT 1'
);
PREPARE stmt_pending_key FROM @sql_pending_key;
EXECUTE stmt_pending_key;
DEALLOCATE PREPARE stmt_pending_key;

SET @exists_pending_unique := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'approval_records'
      AND INDEX_NAME = 'uk_ar_pending_business'
);

SET @sql_pending_unique := IF(
    @exists_pending_unique = 0,
    'ALTER TABLE approval_records ADD UNIQUE INDEX uk_ar_pending_business (pending_business_key)',
    'SELECT 1'
);
PREPARE stmt_pending_unique FROM @sql_pending_unique;
EXECUTE stmt_pending_unique;
DEALLOCATE PREPARE stmt_pending_unique;
