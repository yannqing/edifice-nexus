-- 流程配置中心 + 业务规则配置：Edifice 业务库迁移。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS approval_flow_config (
    flow_config_id bigint NOT NULL COMMENT '流程配置ID',
    biz_type varchar(32) NOT NULL COMMENT '业务类型',
    flow_name varchar(100) NOT NULL COMMENT '流程名称',
    enabled tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
    allow_withdraw tinyint NOT NULL DEFAULT 1 COMMENT '允许撤回',
    allow_urge tinyint NOT NULL DEFAULT 1 COMMENT '允许催办',
    allow_cc tinyint NOT NULL DEFAULT 1 COMMENT '允许抄送',
    allow_starter_select_next tinyint NOT NULL DEFAULT 1 COMMENT '允许发起人/审批人自选下一级',
    version int NOT NULL DEFAULT 1 COMMENT '版本号',
    status tinyint NOT NULL DEFAULT 1 COMMENT '状态：1-有效/0-停用',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    created_by bigint DEFAULT NULL COMMENT '创建人',
    updated_by bigint DEFAULT NULL COMMENT '更新人',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (flow_config_id),
    UNIQUE KEY uk_flow_config_biz_delete (biz_type, is_delete),
    KEY idx_flow_config_enabled (enabled, biz_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批流程配置';

CREATE TABLE IF NOT EXISTS approval_flow_node (
    flow_node_id bigint NOT NULL COMMENT '流程节点ID',
    flow_config_id bigint NOT NULL COMMENT '流程配置ID',
    node_order int NOT NULL COMMENT '节点顺序',
    node_name varchar(100) NOT NULL COMMENT '节点名称',
    approver_source_type varchar(32) NOT NULL COMMENT '审批人来源：user/role/position/starter_select',
    approver_source_id varchar(64) DEFAULT NULL COMMENT '审批人来源ID',
    allow_terminate tinyint NOT NULL DEFAULT 0 COMMENT '允许在此节点终审',
    required_node tinyint NOT NULL DEFAULT 1 COMMENT '是否必经节点',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (flow_node_id),
    KEY idx_flow_node_config_order (flow_config_id, node_order),
    KEY idx_flow_node_source (approver_source_type, approver_source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批流程节点配置';

CREATE TABLE IF NOT EXISTS business_rule_config (
    rule_config_id bigint NOT NULL COMMENT '业务规则ID',
    biz_type varchar(32) NOT NULL COMMENT '业务类型',
    rule_key varchar(64) NOT NULL COMMENT '规则编码',
    rule_name varchar(100) NOT NULL COMMENT '规则名称',
    rule_value text NOT NULL COMMENT '规则值',
    value_type varchar(16) NOT NULL COMMENT '值类型：boolean/number/string/json',
    enabled tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
    description varchar(500) DEFAULT NULL COMMENT '规则说明',
    updated_by bigint DEFAULT NULL COMMENT '更新人',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (rule_config_id),
    UNIQUE KEY uk_business_rule_key_delete (biz_type, rule_key, is_delete),
    KEY idx_business_rule_biz_enabled (biz_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务规则配置';

INSERT INTO sys_permission (permission_id, permission_name, permission_code, permission_type, parent_id, path, is_delete)
VALUES
    (900000000000001020, '流程配置中心', 'menu:flow-config', 1, 0, '/flow-config', 0),
    (900000000000001021, '业务规则配置', 'menu:business-rule-config', 1, 0, '/business-rule-config', 0)
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    parent_id = VALUES(parent_id),
    path = VALUES(path),
    is_delete = VALUES(is_delete);

INSERT INTO approval_flow_config
    (flow_config_id, biz_type, flow_name, enabled, allow_withdraw, allow_urge, allow_cc, allow_starter_select_next, version, status, remark, created_by, updated_by, is_delete)
VALUES
    (910000000000001001, 'inspection', '验工单默认审批流程', 1, 1, 1, 1, 1, 1, 1, '初始化配置：当前业务仍保留原审批逻辑，后续逐步接入配置读取。', NULL, NULL, 0),
    (910000000000001002, 'file', '项目文件默认审批流程', 1, 1, 1, 1, 1, 1, 1, '初始化配置：模拟三级项目文件审批。', NULL, NULL, 0),
    (910000000000001003, 'bid', '投标默认审批流程', 1, 1, 1, 1, 1, 1, 1, '初始化配置。', NULL, NULL, 0),
    (910000000000001004, 'output', '产值分配默认审批流程', 1, 0, 1, 1, 1, 1, 1, '初始化配置：产值撤回暂走原业务页。', NULL, NULL, 0),
    (910000000000001005, 'acceptance', '验收默认审批流程', 1, 1, 1, 1, 1, 1, 1, '初始化配置。', NULL, NULL, 0),
    (910000000000001006, 'oa_application', 'OA申请默认审批流程', 1, 1, 1, 1, 1, 1, 1, '初始化配置。', NULL, NULL, 0),
    (910000000000001007, 'timesheet', '工时默认审批流程', 1, 0, 1, 1, 1, 1, 1, '初始化配置：工时撤回暂走原业务页。', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    flow_name = VALUES(flow_name),
    enabled = VALUES(enabled),
    allow_withdraw = VALUES(allow_withdraw),
    allow_urge = VALUES(allow_urge),
    allow_cc = VALUES(allow_cc),
    allow_starter_select_next = VALUES(allow_starter_select_next),
    status = VALUES(status),
    remark = VALUES(remark),
    updated_time = CURRENT_TIMESTAMP,
    is_delete = 0;

INSERT INTO approval_flow_node
    (flow_node_id, flow_config_id, node_order, node_name, approver_source_type, approver_source_id, allow_terminate, required_node, is_delete)
VALUES
    (910000000000002001, 910000000000001001, 1, '验工初审', 'starter_select', NULL, 0, 1, 0),
    (910000000000002002, 910000000000001001, 2, '验工复审', 'starter_select', NULL, 0, 1, 0),
    (910000000000002003, 910000000000001001, 3, '验工终审', 'starter_select', NULL, 1, 1, 0),
    (910000000000002004, 910000000000001002, 1, '项目负责人审批', 'starter_select', NULL, 0, 1, 0),
    (910000000000002005, 910000000000001002, 2, '专业主管审批', 'starter_select', NULL, 0, 1, 0),
    (910000000000002006, 910000000000001002, 3, '总工审批', 'starter_select', NULL, 1, 1, 0),
    (910000000000002007, 910000000000001003, 1, '投标审批', 'starter_select', NULL, 1, 1, 0),
    (910000000000002008, 910000000000001004, 1, '分配单确认', 'starter_select', NULL, 0, 1, 0),
    (910000000000002009, 910000000000001004, 2, '分配单审批', 'starter_select', NULL, 0, 1, 0),
    (910000000000002010, 910000000000001004, 3, '发放确认', 'starter_select', NULL, 1, 1, 0),
    (910000000000002011, 910000000000001005, 1, '验收审批', 'starter_select', NULL, 1, 1, 0),
    (910000000000002012, 910000000000001006, 1, 'OA申请审批', 'starter_select', NULL, 1, 1, 0),
    (910000000000002013, 910000000000001007, 1, '工时审批', 'starter_select', NULL, 1, 1, 0)
ON DUPLICATE KEY UPDATE
    node_order = VALUES(node_order),
    node_name = VALUES(node_name),
    approver_source_type = VALUES(approver_source_type),
    approver_source_id = VALUES(approver_source_id),
    allow_terminate = VALUES(allow_terminate),
    required_node = VALUES(required_node),
    updated_time = CURRENT_TIMESTAMP,
    is_delete = 0;

INSERT INTO business_rule_config
    (rule_config_id, biz_type, rule_key, rule_name, rule_value, value_type, enabled, description, updated_by, is_delete)
VALUES
    (910000000000003001, 'output', 'require_stage_inspection_passed', '产值分配前必须验工通过', 'true', 'boolean', 1, '只有验工单通过的项目阶段才允许新建产值分配单。', NULL, 0),
    (910000000000003002, 'output', 'prevent_duplicate_confirmed_stage', '禁止同阶段重复确认产值', 'true', 'boolean', 1, '同一项目阶段已有确认中或已确认分配单时，不允许重复确认。', NULL, 0),
    (910000000000003003, 'output', 'allow_negative_output', '允许负产值', 'false', 'boolean', 1, '控制产值计算结果为负时是否允许继续提交。', NULL, 0),
    (910000000000003004, 'inspection', 'require_materials', '验工材料必填', 'true', 'boolean', 1, '发起验工时必须上传验收材料。', NULL, 0),
    (910000000000003005, 'file', 'require_approval', '项目文件上传必须审批', 'true', 'boolean', 1, '项目文件上传后进入审批流程，通过后才归档为正式文件。', NULL, 0),
    (910000000000003006, 'file', 'allow_image_upload', '允许上传图片文件', 'true', 'boolean', 1, '控制项目文件上传入口是否允许图片类型。', NULL, 0),
    (910000000000003007, 'inspection', 'block_after_project_archive', '项目归档后禁止发起验工', 'true', 'boolean', 1, '项目归档后禁止继续发起验工。', NULL, 0),
    (910000000000003008, 'file', 'block_after_project_archive', '项目归档后禁止上传文件', 'true', 'boolean', 1, '项目归档后禁止继续上传项目文件。', NULL, 0),
    (910000000000003009, 'output', 'block_after_project_archive', '项目归档后禁止产值分配', 'true', 'boolean', 1, '项目归档后禁止继续发起产值分配。', NULL, 0)
ON DUPLICATE KEY UPDATE
    rule_name = VALUES(rule_name),
    rule_value = VALUES(rule_value),
    value_type = VALUES(value_type),
    enabled = VALUES(enabled),
    description = VALUES(description),
    updated_time = CURRENT_TIMESTAMP,
    is_delete = 0;
