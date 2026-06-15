CREATE TABLE IF NOT EXISTS oa_contract_project_mapping
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    oa_contract_id       INT                                NOT NULL COMMENT 'OA 合同 ID',
    edifice_project_id   BIGINT                             NOT NULL COMMENT 'Edifice 工程项目 ID',
    edifice_contract_id  BIGINT                             NOT NULL COMMENT 'Edifice 工程合同 ID',
    created_by           BIGINT                             NOT NULL COMMENT '创建项目的 Edifice 用户 ID',
    created_time         DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time         DATETIME DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete            TINYINT  DEFAULT 0                 NOT NULL,
    UNIQUE KEY uk_oa_contract (oa_contract_id),
    UNIQUE KEY uk_edifice_project (edifice_project_id),
    KEY idx_edifice_contract (edifice_contract_id)
) COMMENT 'OA 销售合同与 Edifice 工程项目唯一映射';
