SET @has_current_record_id := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'oa_application'
    AND column_name = 'current_record_id'
);
SET @sql := IF(
  @has_current_record_id = 0,
  'ALTER TABLE `oa_application` ADD COLUMN `current_record_id` bigint DEFAULT NULL COMMENT ''当前待审记录id'' AFTER `attachment_ids`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_current_record_idx := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'oa_application'
    AND index_name = 'idx_oa_application_current_record'
);
SET @sql := IF(
  @has_current_record_idx = 0,
  'ALTER TABLE `oa_application` ADD INDEX `idx_oa_application_current_record` (`current_record_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
