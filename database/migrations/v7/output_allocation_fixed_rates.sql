-- allocation_v3: fixed aggregate work-type rates from the approved allocation table.

CREATE TABLE IF NOT EXISTS output_allocation_rule_pool_rate (
    pool_rate_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_version_id      BIGINT         NOT NULL COMMENT '规则版本id',
    work_type            TINYINT        NOT NULL COMMENT '0-管理工作/1-基础工作/2-智励工作',
    gross_rate           DECIMAL(10,4)  NOT NULL COMMENT '工作类型占总收入比例(%)',
    project_rate         DECIMAL(10,4)  NOT NULL COMMENT '项目人员分配占总收入比例(%)',
    company_rate         DECIMAL(10,4)  NOT NULL COMMENT '公司内部留存占总收入比例(%)',
    created_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time         DATETIME       NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete            TINYINT        NOT NULL DEFAULT 0,
    UNIQUE KEY uk_output_rule_pool_rate (rule_version_id, work_type),
    KEY idx_output_rule_pool_rate_version (rule_version_id, is_delete)
) COMMENT '产值分配固定汇总比例';

INSERT INTO output_allocation_rule_pool_rate
    (rule_version_id, work_type, gross_rate, project_rate, company_rate)
SELECT rv.rule_version_id, seed.work_type, seed.gross_rate, seed.project_rate, seed.company_rate
FROM output_allocation_rule_version rv
JOIN project_type pt ON pt.project_type_id = rv.project_type_id AND pt.is_delete = 0
JOIN (
    SELECT 'A' type_code, 0 work_type, 8.0600 gross_rate, 4.0000 project_rate, 4.0600 company_rate
    UNION ALL SELECT 'A', 1, 20.2800, 20.2800, 0.0000
    UNION ALL SELECT 'A', 2, 11.6600, 4.0000, 7.6600

    UNION ALL SELECT 'B', 0, 8.2000, 4.0000, 4.2000
    UNION ALL SELECT 'B', 1, 20.0000, 20.0000, 0.0000
    UNION ALL SELECT 'B', 2, 11.8000, 4.0000, 7.8000

    UNION ALL SELECT 'C', 0, 8.0000, 4.0000, 4.0000
    UNION ALL SELECT 'C', 1, 20.8000, 20.8000, 0.0000
    UNION ALL SELECT 'C', 2, 11.2000, 4.0000, 7.2000

    UNION ALL SELECT 'D', 0, 9.2000, 4.0000, 5.2000
    UNION ALL SELECT 'D', 1, 18.0000, 18.0000, 0.0000
    UNION ALL SELECT 'D', 2, 12.8000, 4.0000, 8.8000

    UNION ALL SELECT 'E', 0, 7.6000, 4.0000, 3.6000
    UNION ALL SELECT 'E', 1, 20.4000, 20.4000, 0.0000
    UNION ALL SELECT 'E', 2, 12.0000, 4.0000, 8.0000
) seed ON seed.type_code = pt.project_type_code
WHERE rv.is_delete = 0
  AND NOT EXISTS (
      SELECT 1
      FROM output_allocation_rule_pool_rate rate
      WHERE rate.rule_version_id = rv.rule_version_id
        AND rate.work_type = seed.work_type
        AND rate.is_delete = 0
  );
