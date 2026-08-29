-- Person-specific benefit adjustment accounts.
-- Historical rows stay as component_type=0; new correction rows reference their source distribution.

ALTER TABLE output_value
    ADD COLUMN base_ratio_snapshot DECIMAL(10, 4) NULL COMMENT '创建时阶段基本比例快照' AFTER benefit_amount_snapshot,
    ADD COLUMN benefit_ratio_snapshot DECIMAL(10, 4) NULL COMMENT '创建时阶段效益比例快照' AFTER base_ratio_snapshot,
    ADD COLUMN person_adjustment_amount DECIMAL(20, 2) NOT NULL DEFAULT 0.00 COMMENT '本单人员效益补差/扣回' AFTER adjustment_amount,
    ADD COLUMN company_adjustment_amount DECIMAL(20, 2) NOT NULL DEFAULT 0.00 COMMENT '本单公司效益补差/扣回' AFTER person_adjustment_amount,
    ADD COLUMN pending_person_adjustment_amount DECIMAL(20, 2) NOT NULL DEFAULT 0.00 COMMENT '本单后仍待扣回的人员金额' AFTER company_adjustment_amount;

ALTER TABLE output_value_distribution
    ADD COLUMN component_type TINYINT NOT NULL DEFAULT 0 COMMENT '组成类型：0-当前阶段正常分配/1-历史效益补差扣回' AFTER output_value_id,
    ADD COLUMN source_distribution_id BIGINT NULL COMMENT '补差来源历史人员分配行id' AFTER component_type,
    ADD COLUMN source_output_value_id BIGINT NULL COMMENT '补差来源历史产值分配单id' AFTER source_distribution_id,
    ADD COLUMN source_project_stage_id BIGINT NULL COMMENT '补差来源历史阶段id' AFTER source_output_value_id,
    ADD COLUMN adjustment_target_amount DECIMAL(20, 2) NULL COMMENT '截至本单该来源人员累计应补扣金额' AFTER company_delta,
    ADD COLUMN previous_adjusted_amount DECIMAL(20, 2) NULL COMMENT '本单前该来源人员累计已补扣金额' AFTER adjustment_target_amount,
    ADD COLUMN remaining_adjustment_amount DECIMAL(20, 2) NULL COMMENT '本单后该来源人员剩余待补扣金额' AFTER previous_adjusted_amount,
    ADD KEY idx_dist_source_component (source_distribution_id, component_type, is_delete),
    ADD KEY idx_dist_source_output (source_output_value_id, component_type, is_delete);

ALTER TABLE output_value_adjustment_detail
    ADD COLUMN person_adjustment_amount DECIMAL(20, 2) NOT NULL DEFAULT 0.00 COMMENT '本次人员补差/扣回金额' AFTER adjustment_amount,
    ADD COLUMN company_adjustment_amount DECIMAL(20, 2) NOT NULL DEFAULT 0.00 COMMENT '本次公司补差/扣回金额' AFTER person_adjustment_amount,
    ADD COLUMN remaining_person_adjustment_amount DECIMAL(20, 2) NOT NULL DEFAULT 0.00 COMMENT '本次后仍待扣回的人员金额' AFTER company_adjustment_amount;
