-- Apply to the Edifice database before deploying the optional-contract backend.
-- Existing contract references and file objects are preserved. Safe to re-run.
ALTER TABLE contract
    MODIFY COLUMN contract_file BIGINT NULL DEFAULT NULL COMMENT '合同主文件id（选填，可后续补充）';
