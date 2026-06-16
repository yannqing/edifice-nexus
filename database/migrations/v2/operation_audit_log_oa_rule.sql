-- OA 权限树：新增 edifice / 操作审计节点。
-- 在 OA 库执行；如 OA 与 edifice 在同一 MySQL 实例，也可按实际库名调整前缀。

SET NAMES utf8mb4;

INSERT INTO oa_admin_rule (id, pid, src, title, name, module, icon, menu, sort, status, create_time, update_time)
VALUES
    (900001000, 0, '', 'edifice 工程管理', 'edifice', 'edifice', 'icon-jichupeizhi', 0, 900, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001017, 900001000, '/audit-logs', '操作审计', 'edifice:menu:audit-logs', 'edifice', '', 0, 17, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP())
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

UPDATE oa_admin_group
SET rules = TRIM(BOTH ',' FROM CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM rules), ''), '900001000'))
WHERE id = 1
  AND FIND_IN_SET('900001000', rules) = 0;

UPDATE oa_admin_group
SET rules = TRIM(BOTH ',' FROM CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM rules), ''), '900001017'))
WHERE id = 1
  AND FIND_IN_SET('900001017', rules) = 0;
