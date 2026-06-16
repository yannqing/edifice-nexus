-- 流程配置中心 + 业务规则配置：OA 权限树迁移。
-- 请在 OA 数据库中执行。

SET NAMES utf8mb4;

INSERT INTO oa_admin_rule (id, pid, src, title, name, module, icon, menu, sort, status, create_time, update_time)
VALUES
    (900001000, 0, '', 'edifice 工程管理', 'edifice', 'edifice', 'icon-jichupeizhi', 0, 900, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001020, 900001000, '/flow-config', '流程配置中心', 'edifice:menu:flow-config', 'edifice', '', 0, 20, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001021, 900001000, '/business-rule-config', '业务规则配置', 'edifice:menu:business-rule-config', 'edifice', '', 0, 21, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP())
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
WHERE FIND_IN_SET('900001000', rules) = 0;

UPDATE oa_admin_group
SET rules = TRIM(BOTH ',' FROM CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM rules), ''), '900001020'))
WHERE id = 1 AND FIND_IN_SET('900001020', rules) = 0;

UPDATE oa_admin_group
SET rules = TRIM(BOTH ',' FROM CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM rules), ''), '900001021'))
WHERE id = 1 AND FIND_IN_SET('900001021', rules) = 0;
