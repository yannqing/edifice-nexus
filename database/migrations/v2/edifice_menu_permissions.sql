-- edifice 侧边栏菜单权限初始化。
-- OA 负责人员/组织主数据，edifice 负责自身功能模块权限。

SET NAMES utf8mb4;

SET @exists_source := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user_role'
      AND COLUMN_NAME = 'source'
);
SET @sql_source := IF(
    @exists_source = 0,
    'ALTER TABLE sys_user_role ADD COLUMN source varchar(32) DEFAULT ''MANUAL'' NOT NULL COMMENT ''角色来源：MANUAL/OA_SYNC'' AFTER role_id',
    'SELECT 1'
);
PREPARE stmt_source FROM @sql_source;
EXECUTE stmt_source;
DEALLOCATE PREPARE stmt_source;

SET @exists_ref := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user_role'
      AND COLUMN_NAME = 'source_ref'
);
SET @sql_ref := IF(
    @exists_ref = 0,
    'ALTER TABLE sys_user_role ADD COLUMN source_ref varchar(64) NULL COMMENT ''来源标识，如 OA_ADMIN_GROUP:1'' AFTER source',
    'SELECT 1'
);
PREPARE stmt_ref FROM @sql_ref;
EXECUTE stmt_ref;
DEALLOCATE PREPARE stmt_ref;

DROP TABLE IF EXISTS edifice_oa_role_mapping;
UPDATE sys_permission SET is_delete = 1 WHERE permission_code = 'menu:permission-management';

INSERT INTO sys_role (role_id, role_name, role_code, role_desc, status, is_delete)
VALUES
    (900000000000000001, '超级管理员', 'SUPER_ADMIN', '拥有 edifice 全部功能权限', 1, 0),
    (900000000000000002, '普通员工', 'STAFF', '默认员工权限，可访问个人工作相关功能', 1, 0),
    (900000000000000003, '项目管理员', 'PROJECT_ADMIN', '管理项目、验工、文件审批、投标等项目功能', 1, 0),
    (900000000000000004, '财务人员', 'FINANCE', '管理产值及财务相关报表', 1, 0),
    (900000000000000005, '管理层', 'MANAGER', '查看管理视图、报表和审批相关功能', 1, 0)
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    role_desc = VALUES(role_desc),
    status = VALUES(status),
    is_delete = VALUES(is_delete);

INSERT INTO sys_permission (permission_id, permission_name, permission_code, permission_type, parent_id, path, is_delete)
VALUES
    (900000000000001001, '工作台', 'menu:workbench', 1, 0, '/', 0),
    (900000000000001002, '我的项目', 'menu:my-projects', 1, 0, '/my-projects', 0),
    (900000000000001003, '验工审批', 'menu:inspection-approval', 1, 0, '/inspection-approval', 0),
    (900000000000001004, '工时填报', 'menu:timesheet', 1, 0, '/timesheet', 0),
    (900000000000001005, 'OA 申请', 'menu:oa-applications', 1, 0, '/oa/applications', 0),
    (900000000000001006, '全部项目', 'menu:all-projects', 1, 0, '/all-projects', 0),
    (900000000000001007, '验工单管理', 'menu:inspection-management', 1, 0, '/inspection-management', 0),
    (900000000000001008, '项目文件审批', 'menu:project-files-approval', 1, 0, '/project-files/approval', 0),
    (900000000000001009, '投标管理', 'menu:bids', 1, 0, '/bids', 0),
    (900000000000001010, '产值分配', 'menu:output-value', 1, 0, '/output-value', 0),
    (900000000000001011, '回款记录', 'menu:collection', 1, 0, '/collection', 1),
    (900000000000001012, '统计报表', 'menu:statistics', 1, 0, '/statistics', 0),
    (900000000000001013, '个人绩效', 'menu:performance', 1, 0, '/performance', 0),
    (900000000000001014, '人员分配汇总', 'menu:personnel-quarter', 1, 0, '/reports/personnel-quarter', 0),
    (900000000000001015, '用户管理', 'menu:user-management', 1, 0, '/user-management', 0),
    (900000000000001016, '公告管理', 'menu:announcement-management', 1, 0, '/announcement-management', 0),
    (900000000000001025, '绩效还原', 'menu:performance-restore', 1, 0, '/performance-restore', 0)
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    parent_id = VALUES(parent_id),
    path = VALUES(path),
    is_delete = VALUES(is_delete);

-- 回款记录功能暂不启用：保留权限定义用于历史兼容，但不再展示或授权。
UPDATE sys_role_permission
SET is_delete = 1
WHERE permission_id = 900000000000001011;

-- 将 edifice 权限节点写入 OA 的“系统管理 / 角色权限”树。
-- 后续只需要在 OA 的角色权限里勾选这些节点；edifice 定时同步 OA 权限组并据此显示/拦截模块。
INSERT INTO office_db.oa_admin_rule (id, pid, src, title, name, module, icon, menu, sort, status, create_time, update_time)
VALUES
    (900001000, 0, '', 'edifice 工程管理', 'edifice', 'edifice', 'icon-jichupeizhi', 0, 900, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001001, 900001000, '/', '工作台', 'edifice:menu:workbench', 'edifice', '', 0, 1, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001002, 900001000, '/my-projects', '我的项目', 'edifice:menu:my-projects', 'edifice', '', 0, 2, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001003, 900001000, '/inspection-approval', '验工审批', 'edifice:menu:inspection-approval', 'edifice', '', 0, 3, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001004, 900001000, '/timesheet', '工时填报', 'edifice:menu:timesheet', 'edifice', '', 0, 4, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001005, 900001000, '/oa/applications', 'OA 申请', 'edifice:menu:oa-applications', 'edifice', '', 0, 5, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001006, 900001000, '/all-projects', '全部项目', 'edifice:menu:all-projects', 'edifice', '', 0, 6, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001007, 900001000, '/inspection-management', '验工单管理', 'edifice:menu:inspection-management', 'edifice', '', 0, 7, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001008, 900001000, '/project-files/approval', '项目文件审批', 'edifice:menu:project-files-approval', 'edifice', '', 0, 8, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001009, 900001000, '/bids', '投标管理', 'edifice:menu:bids', 'edifice', '', 0, 9, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001010, 900001000, '/output-value', '产值分配', 'edifice:menu:output-value', 'edifice', '', 0, 10, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001012, 900001000, '/statistics', '统计报表', 'edifice:menu:statistics', 'edifice', '', 0, 12, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001013, 900001000, '/performance', '个人绩效', 'edifice:menu:performance', 'edifice', '', 0, 13, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001014, 900001000, '/reports/personnel-quarter', '人员分配汇总', 'edifice:menu:personnel-quarter', 'edifice', '', 0, 14, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001015, 900001000, '/user-management', '用户管理', 'edifice:menu:user-management', 'edifice', '', 0, 15, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001016, 900001000, '/announcement-management', '公告管理', 'edifice:menu:announcement-management', 'edifice', '', 0, 16, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001025, 900001000, '/performance-restore', '绩效还原', 'edifice:menu:performance-restore', 'edifice', '', 0, 25, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP())
ON DUPLICATE KEY UPDATE
    pid = VALUES(pid),
    src = VALUES(src),
    title = VALUES(title),
    name = VALUES(name),
    module = VALUES(module),
    menu = VALUES(menu),
    sort = VALUES(sort),
    status = VALUES(status),
    update_time = UNIX_TIMESTAMP();

UPDATE office_db.oa_admin_rule
SET status = 0, update_time = UNIX_TIMESTAMP()
WHERE id = 900001011 OR name = 'edifice:menu:collection';

UPDATE office_db.oa_admin_group
SET rules = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', rules, ','), ',900001011,', ','))
WHERE FIND_IN_SET('900001011', rules);

SET @edifice_required_rule_ids := '900001000,900001001,900001002,900001013';
UPDATE office_db.oa_admin_group
SET rules = TRIM(BOTH ',' FROM CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM rules), ''), '900001000'))
WHERE FIND_IN_SET('900001000', rules) = 0;
UPDATE office_db.oa_admin_group
SET rules = TRIM(BOTH ',' FROM CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM rules), ''), '900001001'))
WHERE FIND_IN_SET('900001001', rules) = 0;
UPDATE office_db.oa_admin_group
SET rules = TRIM(BOTH ',' FROM CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM rules), ''), '900001002'))
WHERE FIND_IN_SET('900001002', rules) = 0;
UPDATE office_db.oa_admin_group
SET rules = TRIM(BOTH ',' FROM CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM rules), ''), '900001013'))
WHERE FIND_IN_SET('900001013', rules) = 0;

SET @edifice_rule_ids := '900001000,900001001,900001002,900001003,900001004,900001005,900001006,900001007,900001008,900001009,900001010,900001012,900001013,900001014,900001015,900001016,900001025';
UPDATE office_db.oa_admin_group
SET rules = CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM rules), ''), @edifice_rule_ids)
WHERE id = 1
  AND FIND_IN_SET('900001001', rules) = 0;

SET @role_permission_id := COALESCE(
    (SELECT MAX(id) FROM sys_role_permission WHERE id >= 900000000000100000 AND id < 900000000000200000),
    900000000000100000
);

INSERT INTO sys_role_permission (id, role_id, permission_id, is_delete)
SELECT @role_permission_id := @role_permission_id + 1, r.role_id, p.permission_id, 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'menu:workbench',
    'menu:my-projects',
    'menu:inspection-approval',
    'menu:timesheet',
    'menu:oa-applications',
    'menu:all-projects',
    'menu:inspection-management',
    'menu:project-files-approval',
    'menu:bids',
    'menu:output-value',
    'menu:statistics',
    'menu:performance',
    'menu:personnel-quarter',
    'menu:user-management',
    'menu:announcement-management',
    'menu:performance-restore'
)
WHERE r.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.role_id = r.role_id
        AND existing.permission_id = p.permission_id
        AND existing.is_delete = 0
  );

INSERT INTO sys_role_permission (id, role_id, permission_id, is_delete)
SELECT @role_permission_id := @role_permission_id + 1, r.role_id, p.permission_id, 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'menu:workbench',
    'menu:my-projects',
    'menu:timesheet',
    'menu:oa-applications',
    'menu:performance'
)
WHERE r.role_code = 'STAFF'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.role_id = r.role_id
        AND existing.permission_id = p.permission_id
        AND existing.is_delete = 0
  );

INSERT INTO sys_role_permission (id, role_id, permission_id, is_delete)
SELECT @role_permission_id := @role_permission_id + 1, r.role_id, p.permission_id, 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'menu:workbench',
    'menu:my-projects',
    'menu:inspection-approval',
    'menu:timesheet',
    'menu:oa-applications',
    'menu:all-projects',
    'menu:inspection-management',
    'menu:project-files-approval',
    'menu:bids',
    'menu:statistics'
)
WHERE r.role_code = 'PROJECT_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.role_id = r.role_id
        AND existing.permission_id = p.permission_id
        AND existing.is_delete = 0
  );

INSERT INTO sys_role_permission (id, role_id, permission_id, is_delete)
SELECT @role_permission_id := @role_permission_id + 1, r.role_id, p.permission_id, 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'menu:workbench',
    'menu:output-value',
    'menu:statistics',
    'menu:personnel-quarter',
    'menu:performance-restore'
)
WHERE r.role_code = 'FINANCE'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.role_id = r.role_id
        AND existing.permission_id = p.permission_id
        AND existing.is_delete = 0
  );

INSERT INTO sys_role_permission (id, role_id, permission_id, is_delete)
SELECT @role_permission_id := @role_permission_id + 1, r.role_id, p.permission_id, 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'menu:workbench',
    'menu:my-projects',
    'menu:inspection-approval',
    'menu:oa-applications',
    'menu:all-projects',
    'menu:inspection-management',
    'menu:project-files-approval',
    'menu:bids',
    'menu:output-value',
    'menu:statistics',
    'menu:personnel-quarter',
    'menu:announcement-management'
)
WHERE r.role_code = 'MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.role_id = r.role_id
        AND existing.permission_id = p.permission_id
        AND existing.is_delete = 0
  );

SET @user_role_id := COALESCE(
    (SELECT MAX(id) FROM sys_user_role WHERE id >= 900000000000200000 AND id < 900000000000300000),
    900000000000200000
);

INSERT INTO sys_user_role (id, user_id, role_id, source, source_ref, project_id, is_delete)
SELECT @user_role_id := @user_role_id + 1, u.user_id, r.role_id, 'MANUAL', 'INIT', 0, 0
FROM sys_user u
JOIN sys_role r ON r.role_code = 'SUPER_ADMIN'
WHERE u.username = 'admin'
  AND u.is_delete = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role existing
      WHERE existing.user_id = u.user_id
        AND existing.role_id = r.role_id
        AND existing.is_delete = 0
  );

INSERT INTO sys_user_role (id, user_id, role_id, source, source_ref, project_id, is_delete)
SELECT @user_role_id := @user_role_id + 1, u.user_id, r.role_id, 'OA_SYNC', 'DEFAULT', 0, 0
FROM sys_user u
JOIN sys_role r ON r.role_code = 'STAFF'
WHERE u.username <> 'admin'
  AND u.is_delete = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role existing
      WHERE existing.user_id = u.user_id
        AND existing.is_delete = 0
  );
