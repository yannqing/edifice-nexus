-- 绩效规则菜单权限。规则保存接口同时兼容原项目配置入口。

SET NAMES utf8mb4;

INSERT INTO sys_permission
    (permission_id, permission_name, permission_code, permission_type, parent_id, path, is_delete)
VALUES
    (900000000000001026, '绩效规则', 'menu:performance-rules', 1, 0, '/performance-rules', 0)
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    parent_id = VALUES(parent_id),
    path = VALUES(path),
    is_delete = VALUES(is_delete);

INSERT INTO office_db.oa_admin_rule
    (id, pid, src, title, name, module, icon, menu, sort, status, create_time, update_time)
VALUES
    (900001026, 900001000, '/performance-rules', '绩效规则',
     'edifice:menu:performance-rules', 'edifice', '', 0, 26, 1,
     UNIX_TIMESTAMP(), UNIX_TIMESTAMP())
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

UPDATE office_db.oa_admin_group
SET rules = TRIM(BOTH ',' FROM CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM rules), ''), '900001026'))
WHERE id = 1
  AND FIND_IN_SET('900001026', COALESCE(rules, '')) = 0;

SET @performance_rule_permission_id := COALESCE(
    (SELECT MAX(id)
     FROM sys_role_permission
     WHERE id >= 900000000000100000
       AND id < 900000000000200000),
    900000000000100000
);

INSERT INTO sys_role_permission (id, role_id, permission_id, is_delete)
SELECT @performance_rule_permission_id := @performance_rule_permission_id + 1,
       role.role_id,
       permission.permission_id,
       0
FROM sys_role role
JOIN sys_permission permission
  ON permission.permission_code = 'menu:performance-rules'
WHERE role.role_code = 'SUPER_ADMIN'
  AND role.is_delete = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission existing
      WHERE existing.role_id = role.role_id
        AND existing.permission_id = permission.permission_id
        AND existing.is_delete = 0
  );
