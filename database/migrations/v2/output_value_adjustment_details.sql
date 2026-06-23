CREATE TABLE IF NOT EXISTS output_value_adjustment_detail
(
    adjustment_detail_id        BIGINT        NOT NULL COMMENT '补差明细id'
        PRIMARY KEY,
    output_value_id             BIGINT        NOT NULL COMMENT '本次产值分配单id',
    source_output_value_id      BIGINT        NOT NULL COMMENT '补差来源历史产值分配单id',
    source_project_stage_id     BIGINT        NOT NULL COMMENT '补差来源历史阶段id',
    source_stage_name           VARCHAR(128)  NULL COMMENT '补差来源历史阶段名称快照',
    source_base_ratio           DECIMAL(10, 4) DEFAULT 0.0000 NOT NULL COMMENT '来源阶段基本比例快照',
    source_benefit_ratio        DECIMAL(10, 4) DEFAULT 0.0000 NOT NULL COMMENT '来源阶段效益比例快照',
    old_base_amount_snapshot    DECIMAL(20, 2) NULL COMMENT '历史单创建时基本金额快照',
    old_benefit_amount_snapshot DECIMAL(20, 2) NULL COMMENT '历史单创建时效益金额快照',
    old_stage_amount            DECIMAL(20, 2) DEFAULT 0.00 NOT NULL COMMENT '历史阶段原计算纯阶段金额',
    new_base_amount_snapshot    DECIMAL(20, 2) DEFAULT 0.00 NOT NULL COMMENT '本单创建时基本金额快照',
    new_benefit_amount_snapshot DECIMAL(20, 2) DEFAULT 0.00 NOT NULL COMMENT '本单创建时效益金额快照',
    new_stage_amount            DECIMAL(20, 2) DEFAULT 0.00 NOT NULL COMMENT '按本单金额重算后的历史阶段金额',
    already_adjusted_amount     DECIMAL(20, 2) DEFAULT 0.00 NOT NULL COMMENT '之前已对该历史阶段补差/扣回金额',
    adjustment_amount           DECIMAL(20, 2) DEFAULT 0.00 NOT NULL COMMENT '本次补差/扣回金额',
    created_time                DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time                DATETIME DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete                   TINYINT  DEFAULT 0 NOT NULL COMMENT '逻辑删除',
    KEY idx_ov_adjust_output (output_value_id),
    KEY idx_ov_adjust_source_output (source_output_value_id),
    KEY idx_ov_adjust_source_stage (source_project_stage_id)
)
    COMMENT '产值分配历史阶段补差明细表';

SET @exists_current_stage_amount := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'output_value'
      AND COLUMN_NAME = 'current_stage_amount'
);
SET @sql_current_stage_amount := IF(
    @exists_current_stage_amount = 0,
    'ALTER TABLE output_value ADD COLUMN current_stage_amount DECIMAL(20,2) NULL COMMENT ''当前阶段纯产值，不含历史补差'' AFTER benefit_snapshot',
    'SELECT 1'
);
PREPARE stmt_current_stage_amount FROM @sql_current_stage_amount;
EXECUTE stmt_current_stage_amount;
DEALLOCATE PREPARE stmt_current_stage_amount;

SET @exists_adjustment_amount := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'output_value'
      AND COLUMN_NAME = 'adjustment_amount'
);
SET @sql_adjustment_amount := IF(
    @exists_adjustment_amount = 0,
    'ALTER TABLE output_value ADD COLUMN adjustment_amount DECIMAL(20,2) NULL COMMENT ''历史阶段补差合计，可正可负'' AFTER current_stage_amount',
    'SELECT 1'
);
PREPARE stmt_adjustment_amount FROM @sql_adjustment_amount;
EXECUTE stmt_adjustment_amount;
DEALLOCATE PREPARE stmt_adjustment_amount;

SET @exists_base_amount_snapshot := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'output_value'
      AND COLUMN_NAME = 'base_amount_snapshot'
);
SET @sql_base_amount_snapshot := IF(
    @exists_base_amount_snapshot = 0,
    'ALTER TABLE output_value ADD COLUMN base_amount_snapshot DECIMAL(20,2) NULL COMMENT ''创建时合同基本金额快照'' AFTER adjustment_amount',
    'SELECT 1'
);
PREPARE stmt_base_amount_snapshot FROM @sql_base_amount_snapshot;
EXECUTE stmt_base_amount_snapshot;
DEALLOCATE PREPARE stmt_base_amount_snapshot;

SET @exists_benefit_amount_snapshot := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'output_value'
      AND COLUMN_NAME = 'benefit_amount_snapshot'
);
SET @sql_benefit_amount_snapshot := IF(
    @exists_benefit_amount_snapshot = 0,
    'ALTER TABLE output_value ADD COLUMN benefit_amount_snapshot DECIMAL(20,2) NULL COMMENT ''创建时合同效益金额快照'' AFTER base_amount_snapshot',
    'SELECT 1'
);
PREPARE stmt_benefit_amount_snapshot FROM @sql_benefit_amount_snapshot;
EXECUTE stmt_benefit_amount_snapshot;
DEALLOCATE PREPARE stmt_benefit_amount_snapshot;

SET @exists_calculation_version := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'output_value'
      AND COLUMN_NAME = 'calculation_version'
);
SET @sql_calculation_version := IF(
    @exists_calculation_version = 0,
    'ALTER TABLE output_value ADD COLUMN calculation_version VARCHAR(64) NULL COMMENT ''产值计算版本'' AFTER benefit_amount_snapshot',
    'SELECT 1'
);
PREPARE stmt_calculation_version FROM @sql_calculation_version;
EXECUTE stmt_calculation_version;
DEALLOCATE PREPARE stmt_calculation_version;

UPDATE output_value
SET current_stage_amount = COALESCE(current_stage_amount, stage_cumulative_amount, total_amount),
    adjustment_amount = COALESCE(adjustment_amount, 0),
    base_amount_snapshot = COALESCE(base_amount_snapshot, NULL),
    benefit_amount_snapshot = COALESCE(benefit_amount_snapshot, benefit_snapshot),
    calculation_version = COALESCE(calculation_version, 'legacy_v0_4')
WHERE is_delete = 0;
