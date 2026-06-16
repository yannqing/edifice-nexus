-- edifice 操作审计日志表与菜单权限。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS operation_audit_log (
    audit_log_id bigint NOT NULL COMMENT '审计日志ID',
    operator_id bigint DEFAULT NULL COMMENT '操作人ID',
    operator_name varchar(100) DEFAULT NULL COMMENT '操作人',
    module_name varchar(100) NOT NULL DEFAULT '' COMMENT '模块',
    operation_name varchar(200) NOT NULL DEFAULT '' COMMENT '操作',
    http_method varchar(16) NOT NULL DEFAULT '' COMMENT 'HTTP方法',
    request_path varchar(255) NOT NULL DEFAULT '' COMMENT '请求路径',
    client_ip varchar(64) DEFAULT NULL COMMENT '客户端IP',
    status tinyint NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    cost_ms bigint DEFAULT NULL COMMENT '耗时毫秒',
    request_summary text COMMENT '请求摘要',
    error_message varchar(1000) DEFAULT NULL COMMENT '错误信息',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (audit_log_id),
    KEY idx_audit_created_time (created_time),
    KEY idx_audit_operator_id (operator_id),
    KEY idx_audit_module_name (module_name),
    KEY idx_audit_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志';

INSERT INTO sys_permission (permission_id, permission_name, permission_code, permission_type, parent_id, path, is_delete)
VALUES (900000000000001017, '操作审计', 'menu:audit-logs', 1, 0, '/audit-logs', 0)
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
JOIN sys_permission p ON p.permission_code = 'menu:audit-logs'
WHERE r.role_code IN ('SUPER_ADMIN', 'OA_GROUP_1')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.role_id = r.role_id
        AND existing.permission_id = p.permission_id
        AND existing.is_delete = 0
  );
