-- 统一待办：抄送与催办。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS approval_cc (
    cc_id bigint NOT NULL COMMENT '抄送ID',
    record_id bigint NOT NULL COMMENT '来源审批记录ID',
    biz_type_ext varchar(32) NOT NULL COMMENT '业务类型',
    biz_id bigint NOT NULL COMMENT '业务ID',
    cc_user_id bigint NOT NULL COMMENT '抄送接收人',
    from_user_id bigint NOT NULL COMMENT '抄送发起人',
    comment varchar(500) DEFAULT NULL COMMENT '抄送说明',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    is_delete tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (cc_id),
    UNIQUE KEY uk_approval_cc_user_biz (cc_user_id, biz_type_ext, biz_id, is_delete),
    KEY idx_approval_cc_user_time (cc_user_id, created_time),
    KEY idx_approval_cc_record (record_id),
    KEY idx_approval_cc_biz (biz_type_ext, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批抄送';

CREATE TABLE IF NOT EXISTS approval_urge (
    urge_id bigint NOT NULL COMMENT '催办ID',
    record_id bigint NOT NULL COMMENT '当前待审批记录ID',
    biz_type_ext varchar(32) NOT NULL COMMENT '业务类型',
    biz_id bigint NOT NULL COMMENT '业务ID',
    from_user_id bigint NOT NULL COMMENT '催办发起人',
    to_user_id bigint NOT NULL COMMENT '催办接收人',
    comment varchar(500) DEFAULT NULL COMMENT '催办说明',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    is_delete tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (urge_id),
    KEY idx_approval_urge_to_time (to_user_id, created_time),
    KEY idx_approval_urge_from_record_time (from_user_id, record_id, created_time),
    KEY idx_approval_urge_biz (biz_type_ext, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批催办';
