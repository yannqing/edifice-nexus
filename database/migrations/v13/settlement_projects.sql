-- Apply once, after a database backup and before starting the matching backend.
-- Existing projects remain independent. No contracts, stages or approvals are rewritten.
ALTER TABLE project
    ADD COLUMN parent_project_id BIGINT NULL COMMENT 'Independent settlement project parent',
    ADD INDEX idx_project_parent (parent_project_id, is_delete, created_time);
