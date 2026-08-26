-- Preserve cent precision for personnel output-value distributions.
-- Existing upgraded databases may still have the legacy INT amount column,
-- even though fresh installations define it as DECIMAL(20,2).

ALTER TABLE output_value_distribution
    MODIFY COLUMN amount DECIMAL(20, 2) NOT NULL DEFAULT 0.00 COMMENT '分配金额（元）';

-- Repair only allocation rows that have an auditable planned-amount snapshot.
-- company_reserve was calculated from these precise values before insertion,
-- so restoring amount also restores the allocation total invariant.
UPDATE output_value_distribution
SET amount = CASE
        WHEN is_active = 0 THEN 0.00
        WHEN COALESCE(completion_ratio, 100.0000) < 100.0000
            THEN ROUND(planned_amount * COALESCE(completion_ratio, 100.0000) / 100.0000, 2)
        ELSE planned_amount
    END,
    updated_time = updated_time
WHERE is_delete = 0
  AND planned_amount IS NOT NULL
  AND amount <> CASE
        WHEN is_active = 0 THEN 0.00
        WHEN COALESCE(completion_ratio, 100.0000) < 100.0000
            THEN ROUND(planned_amount * COALESCE(completion_ratio, 100.0000) / 100.0000, 2)
        ELSE planned_amount
    END;
