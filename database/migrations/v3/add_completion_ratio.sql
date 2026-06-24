-- v3: 阶段部分完成支持 — 新增 completion_ratio 字段
-- project_stage.completion_ratio: 已审批通过的累计完成比例（%，0-100）
-- inspection_form.completion_ratio: 本次验工申请的完成比例（%，0-100），历史数据默认 100

ALTER TABLE project_stage
    ADD COLUMN completion_ratio DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '已审批通过的累计完成比例（%，0-100）'
    AFTER benefit_inclusion_ratio;

-- 已完成（status=6）的阶段，completion_ratio 设为 100
UPDATE project_stage SET completion_ratio = 100.00 WHERE stage_status = 6;

ALTER TABLE inspection_form
    ADD COLUMN completion_ratio DECIMAL(5,2) NOT NULL DEFAULT 100.00 COMMENT '本次验工申请的完成比例（%，0-100），历史数据默认100'
    AFTER file_ids;
