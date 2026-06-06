-- 产值分配办理人字段：创建指定确认人，确认指定审批人，审批指定发放人。
ALTER TABLE output_value
    ADD COLUMN confirm_user_id BIGINT NULL COMMENT '确认人id' AFTER submit_user_id,
    ADD COLUMN approve_user_id BIGINT NULL COMMENT '审批人id' AFTER confirm_user_id,
    ADD COLUMN pay_user_id BIGINT NULL COMMENT '发放人id' AFTER approve_user_id,
    ADD COLUMN current_handler_id BIGINT NULL COMMENT '当前办理人id' AFTER pay_user_id;

CREATE INDEX idx_output_value_current_handler ON output_value (current_handler_id);
