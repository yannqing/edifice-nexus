-- allocation_v2: project-type/stage work allocation rules and auditable pool snapshots.

CREATE TABLE IF NOT EXISTS output_allocation_rule_version (
    rule_version_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_type_id      BIGINT         NOT NULL COMMENT '项目类型id',
    version_no           INT            NOT NULL COMMENT '项目类型内版本号',
    employee_pool_rate   DECIMAL(10,4)  NOT NULL DEFAULT 40.0000 COMMENT '名义员工池占产值比例(%)',
    company_base_rate    DECIMAL(10,4)  NOT NULL DEFAULT 60.0000 COMMENT '公司基础留存占产值比例(%)',
    status               TINYINT        NOT NULL DEFAULT 1 COMMENT '0-历史版本/1-当前生效',
    created_by           BIGINT         NULL COMMENT '创建人',
    effective_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间',
    created_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time         DATETIME       NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete            TINYINT        NOT NULL DEFAULT 0,
    UNIQUE KEY uk_output_rule_version (project_type_id, version_no),
    KEY idx_output_rule_active (project_type_id, status, is_delete)
) COMMENT '产值分配规则版本';

CREATE TABLE IF NOT EXISTS output_allocation_rule_item (
    rule_item_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_version_id      BIGINT         NOT NULL COMMENT '规则版本id',
    stage_name           VARCHAR(255)   NOT NULL COMMENT '项目阶段名称快照',
    stage_order          INT            NOT NULL COMMENT '阶段顺序',
    work_type            TINYINT        NOT NULL COMMENT '0-管理工作/1-基础工作/2-智励工作',
    work_weight          DECIMAL(10,4)  NOT NULL COMMENT '该阶段40%名义员工池内工作权重(%)',
    project_cap_rate     DECIMAL(10,4)  NULL COMMENT '项目人员最多可分配占本期产值比例(%), NULL表示不封顶',
    created_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time         DATETIME       NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete            TINYINT        NOT NULL DEFAULT 0,
    UNIQUE KEY uk_output_rule_item (rule_version_id, stage_name, work_type),
    KEY idx_output_rule_item_version (rule_version_id, stage_order, work_type)
) COMMENT '产值分配阶段工作权重';

CREATE TABLE IF NOT EXISTS output_value_work_pool (
    work_pool_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    output_value_id      BIGINT         NOT NULL COMMENT '产值分配单id',
    rule_version_id      BIGINT         NOT NULL COMMENT '使用的规则版本id',
    rule_version_no      INT            NOT NULL COMMENT '规则版本号快照',
    work_type            TINYINT        NOT NULL COMMENT '0-管理工作/1-基础工作/2-智励工作',
    stage_work_ratio     DECIMAL(10,4)  NOT NULL COMMENT '本阶段工作权重(%)',
    gross_rate           DECIMAL(10,4)  NOT NULL COMMENT '该工作类型占本期产值毛比例(%)',
    gross_amount         DECIMAL(20,2)  NOT NULL COMMENT '该工作类型名义金额',
    project_rate         DECIMAL(10,4)  NOT NULL COMMENT '项目人员可分配占本期产值比例(%)',
    project_amount       DECIMAL(20,2)  NOT NULL COMMENT '项目人员可分配金额',
    company_rate         DECIMAL(10,4)  NOT NULL COMMENT '该工作类型转公司占本期产值比例(%)',
    company_amount       DECIMAL(20,2)  NOT NULL COMMENT '该工作类型转公司金额',
    created_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time         DATETIME       NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete            TINYINT        NOT NULL DEFAULT 0,
    UNIQUE KEY uk_output_work_pool (output_value_id, work_type),
    KEY idx_output_work_pool_output (output_value_id)
) COMMENT '产值分配工作类型资金池快照';

DROP PROCEDURE IF EXISTS edifice_add_column_if_missing_v6;
DELIMITER $$
CREATE PROCEDURE edifice_add_column_if_missing_v6(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_definition VARCHAR(512)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `',
                          p_column_name, '` ', p_column_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL edifice_add_column_if_missing_v6('output_value', 'allocation_version',
    'VARCHAR(32) NULL COMMENT ''人员分配计算版本''');
CALL edifice_add_column_if_missing_v6('output_value', 'allocation_rule_version_id',
    'BIGINT NULL COMMENT ''人员分配规则版本id''');
CALL edifice_add_column_if_missing_v6('output_value', 'employee_pool_amount',
    'DECIMAL(20,2) NULL COMMENT ''名义员工池金额''');
CALL edifice_add_column_if_missing_v6('output_value', 'company_base_amount',
    'DECIMAL(20,2) NULL COMMENT ''公司基础留存金额''');
CALL edifice_add_column_if_missing_v6('output_value', 'work_transfer_amount',
    'DECIMAL(20,2) NULL COMMENT ''工作类型超限转公司金额''');
CALL edifice_add_column_if_missing_v6('output_value', 'project_pool_amount',
    'DECIMAL(20,2) NULL COMMENT ''项目人员可分配金额''');

CALL edifice_add_column_if_missing_v6('output_value_distribution', 'work_pool_id',
    'BIGINT NULL COMMENT ''工作类型资金池id''');
CALL edifice_add_column_if_missing_v6('output_value_distribution', 'role_alloc_ratio',
    'DECIMAL(10,4) NULL COMMENT ''对应工作类型资金池内分配比例(%)''');
CALL edifice_add_column_if_missing_v6('output_value_distribution', 'planned_amount',
    'DECIMAL(20,2) NULL COMMENT ''人员计划分配金额''');
CALL edifice_add_column_if_missing_v6('output_value_distribution', 'company_delta',
    'DECIMAL(20,2) NULL COMMENT ''兑现不足或离职转公司金额''');

DROP PROCEDURE edifice_add_column_if_missing_v6;

-- Seed the first active rule version for the five built-in project types.
INSERT INTO output_allocation_rule_version
    (project_type_id, version_no, employee_pool_rate, company_base_rate, status, effective_time)
SELECT pt.project_type_id, 1, 40.0000, 60.0000, 1, NOW()
FROM project_type pt
WHERE pt.project_type_code IN ('A', 'B', 'C', 'D', 'E')
  AND pt.is_delete = 0
  AND NOT EXISTS (
      SELECT 1
      FROM output_allocation_rule_version rv
      WHERE rv.project_type_id = pt.project_type_id
        AND rv.is_delete = 0
  );

INSERT INTO output_allocation_rule_item
    (rule_version_id, stage_name, stage_order, work_type, work_weight, project_cap_rate)
SELECT rv.rule_version_id, seed.stage_name, seed.stage_order, seed.work_type,
       seed.work_weight, seed.project_cap_rate
FROM output_allocation_rule_version rv
JOIN project_type pt ON pt.project_type_id = rv.project_type_id AND pt.is_delete = 0
JOIN (
    SELECT 'A' type_code, '项目策划' stage_name, 1 stage_order, 0 work_type, 70.0000 work_weight, 4.0000 project_cap_rate
    UNION ALL SELECT 'A', '项目策划', 1, 1, 10.0000, NULL
    UNION ALL SELECT 'A', '项目策划', 1, 2, 20.0000, 4.0000
    UNION ALL SELECT 'A', '初稿编制', 2, 0, 15.0000, 4.0000
    UNION ALL SELECT 'A', '初稿编制', 2, 1, 70.0000, NULL
    UNION ALL SELECT 'A', '初稿编制', 2, 2, 15.0000, 4.0000
    UNION ALL SELECT 'A', '终稿编制', 3, 0, 20.0000, 4.0000
    UNION ALL SELECT 'A', '终稿编制', 3, 1, 45.0000, NULL
    UNION ALL SELECT 'A', '终稿编制', 3, 2, 35.0000, 4.0000
    UNION ALL SELECT 'A', '核对', 4, 0, 15.0000, 4.0000
    UNION ALL SELECT 'A', '核对', 4, 1, 35.0000, NULL
    UNION ALL SELECT 'A', '核对', 4, 2, 50.0000, 4.0000
    UNION ALL SELECT 'A', '后期服务', 5, 0, 40.0000, 4.0000
    UNION ALL SELECT 'A', '后期服务', 5, 1, 15.0000, NULL
    UNION ALL SELECT 'A', '后期服务', 5, 2, 45.0000, 4.0000

    UNION ALL SELECT 'B', '控制价编制', 1, 0, 30.0000, 4.0000
    UNION ALL SELECT 'B', '控制价编制', 1, 1, 55.0000, NULL
    UNION ALL SELECT 'B', '控制价编制', 1, 2, 15.0000, 4.0000
    UNION ALL SELECT 'B', '过程服务', 2, 0, 15.0000, 4.0000
    UNION ALL SELECT 'B', '过程服务', 2, 1, 70.0000, NULL
    UNION ALL SELECT 'B', '过程服务', 2, 2, 15.0000, 4.0000
    UNION ALL SELECT 'B', '结算审核', 3, 0, 20.0000, 4.0000
    UNION ALL SELECT 'B', '结算审核', 3, 1, 30.0000, NULL
    UNION ALL SELECT 'B', '结算审核', 3, 2, 50.0000, 4.0000
    UNION ALL SELECT 'B', '成本分析', 4, 0, 15.0000, 4.0000
    UNION ALL SELECT 'B', '成本分析', 4, 1, 40.0000, NULL
    UNION ALL SELECT 'B', '成本分析', 4, 2, 45.0000, 4.0000
    UNION ALL SELECT 'B', '后期配合', 5, 0, 40.0000, 4.0000
    UNION ALL SELECT 'B', '后期配合', 5, 1, 20.0000, NULL
    UNION ALL SELECT 'B', '后期配合', 5, 2, 40.0000, 4.0000

    UNION ALL SELECT 'C', '方案计划编制', 1, 0, 60.0000, 4.0000
    UNION ALL SELECT 'C', '方案计划编制', 1, 1, 10.0000, NULL
    UNION ALL SELECT 'C', '方案计划编制', 1, 2, 30.0000, 4.0000
    UNION ALL SELECT 'C', '成果编制', 2, 0, 15.0000, 4.0000
    UNION ALL SELECT 'C', '成果编制', 2, 1, 60.0000, NULL
    UNION ALL SELECT 'C', '成果编制', 2, 2, 25.0000, 4.0000
    UNION ALL SELECT 'C', '后期配合', 3, 0, 20.0000, 4.0000
    UNION ALL SELECT 'C', '后期配合', 3, 1, 30.0000, NULL
    UNION ALL SELECT 'C', '后期配合', 3, 2, 50.0000, 4.0000

    UNION ALL SELECT 'D', '方案计划编制', 1, 0, 60.0000, 4.0000
    UNION ALL SELECT 'D', '方案计划编制', 1, 1, 10.0000, NULL
    UNION ALL SELECT 'D', '方案计划编制', 1, 2, 30.0000, 4.0000
    UNION ALL SELECT 'D', '成果编制', 2, 0, 15.0000, 4.0000
    UNION ALL SELECT 'D', '成果编制', 2, 1, 60.0000, NULL
    UNION ALL SELECT 'D', '成果编制', 2, 2, 25.0000, 4.0000
    UNION ALL SELECT 'D', '后期配合', 3, 0, 20.0000, 4.0000
    UNION ALL SELECT 'D', '后期配合', 3, 1, 30.0000, NULL
    UNION ALL SELECT 'D', '后期配合', 3, 2, 50.0000, 4.0000

    UNION ALL SELECT 'E', '方案计划编制', 1, 0, 70.0000, 4.0000
    UNION ALL SELECT 'E', '方案计划编制', 1, 1, 0.0000, NULL
    UNION ALL SELECT 'E', '方案计划编制', 1, 2, 30.0000, 4.0000
    UNION ALL SELECT 'E', '编制移交', 2, 0, 10.0000, 4.0000
    UNION ALL SELECT 'E', '编制移交', 2, 1, 60.0000, NULL
    UNION ALL SELECT 'E', '编制移交', 2, 2, 30.0000, 4.0000
) seed ON seed.type_code = pt.project_type_code
WHERE rv.status = 1
  AND rv.is_delete = 0
  AND NOT EXISTS (
      SELECT 1
      FROM output_allocation_rule_item item
      WHERE item.rule_version_id = rv.rule_version_id
        AND item.stage_name = seed.stage_name
        AND item.work_type = seed.work_type
        AND item.is_delete = 0
  );
