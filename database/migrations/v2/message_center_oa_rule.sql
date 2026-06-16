-- OA 权限树：消息中心为所有权限组默认必选节点。

SET NAMES utf8mb4;

INSERT INTO oa_admin_rule (id, pid, src, title, name, module, icon, menu, sort, status, create_time, update_time)
VALUES
    (900001000, 0, '', 'edifice 工程管理', 'edifice', 'edifice', 'icon-jichupeizhi', 0, 900, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
    (900001018, 900001000, '/message-center', '消息中心', 'edifice:menu:message-center', 'edifice', '', 0, 18, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP())
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
SET rules = TRIM(BOTH ',' FROM CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM rules), ''), '900001018'))
WHERE FIND_IN_SET('900001018', rules) = 0;
