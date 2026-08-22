-- 审批流程级自审开关。默认关闭，保持现有审批规则。

SET NAMES utf8mb4;

SET @exists_allow_self_approval := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'approval_flow_config'
      AND COLUMN_NAME = 'allow_self_approval'
);

SET @sql_allow_self_approval := IF(
    @exists_allow_self_approval = 0,
    'ALTER TABLE approval_flow_config ADD COLUMN allow_self_approval tinyint NOT NULL DEFAULT 0 COMMENT ''是否允许申请人审批自己的流程'' AFTER allow_starter_select_next',
    'SELECT 1'
);
PREPARE stmt_allow_self_approval FROM @sql_allow_self_approval;
EXECUTE stmt_allow_self_approval;
DEALLOCATE PREPARE stmt_allow_self_approval;
