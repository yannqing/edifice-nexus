-- 项目生命周期看板：Edifice 业务库菜单权限。
-- 请在 edifice_db 中执行。

SET NAMES utf8mb4;

INSERT INTO sys_permission (permission_id, permission_name, permission_code, permission_type, parent_id, path, is_delete)
VALUES (900000000000001024, '项目生命周期', 'menu:project-lifecycle', 1, 0, '/project-lifecycle', 0)
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    parent_id = VALUES(parent_id),
    path = VALUES(path),
    is_delete = VALUES(is_delete);

SET @role_permission_id := COALESCE(
    (SELECT MAX(id) FROM sys_role_permission WHERE id >= 900000000000100000 AND id < 900000000000200000),
    900000000000100000
);

INSERT INTO sys_role_permission (id, role_id, permission_id, is_delete)
SELECT @role_permission_id := @role_permission_id + 1, r.role_id, p.permission_id, 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'menu:project-lifecycle'
WHERE r.role_code IN ('SUPER_ADMIN', 'PROJECT_ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.role_id = r.role_id
        AND existing.permission_id = p.permission_id
        AND existing.is_delete = 0
  );
