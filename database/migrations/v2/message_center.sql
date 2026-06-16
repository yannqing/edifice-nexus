-- edifice 统一消息中心：已读状态表与默认菜单权限。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS user_message_read (
    id bigint NOT NULL COMMENT '主键',
    user_id bigint NOT NULL COMMENT '用户ID',
    source_type varchar(32) NOT NULL COMMENT '来源类型',
    source_id bigint NOT NULL COMMENT '来源记录ID',
    read_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '已读时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_read_user_source (user_id, source_type, source_id),
    KEY idx_message_read_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户消息已读状态';

INSERT INTO sys_permission (permission_id, permission_name, permission_code, permission_type, parent_id, path, is_delete)
VALUES (900000000000001018, '消息中心', 'menu:message-center', 1, 0, '/message-center', 0)
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
JOIN sys_permission p ON p.permission_code = 'menu:message-center'
WHERE r.status = 1
  AND r.is_delete = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.role_id = r.role_id
        AND existing.permission_id = p.permission_id
        AND existing.is_delete = 0
  );
