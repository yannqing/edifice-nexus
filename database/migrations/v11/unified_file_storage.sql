-- Additive migration. Preserve all existing file ids, locations and business references.
ALTER TABLE files
    ADD COLUMN storage_bucket VARCHAR(128) NULL,
    ADD COLUMN object_key VARCHAR(768) NULL,
    ADD COLUMN object_version VARCHAR(256) NULL,
    ADD COLUMN storage_verified_time DATETIME NULL;

CREATE TABLE file_upload_session (
    upload_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    total_size BIGINT NOT NULL,
    chunk_size INT NOT NULL,
    total_parts INT NOT NULL,
    storage_type VARCHAR(16) NOT NULL,
    storage_bucket VARCHAR(128) NULL,
    object_key VARCHAR(768) NOT NULL,
    multipart_id TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'uploading',
    file_id BIGINT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_time DATETIME NOT NULL,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_upload_owner (user_id, status, expires_time)
);

CREATE TABLE file_upload_part (
    upload_id VARCHAR(64) NOT NULL,
    part_number INT NOT NULL,
    size BIGINT NOT NULL,
    etag VARCHAR(256) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    PRIMARY KEY (upload_id, part_number)
);

CREATE TABLE file_storage_migration (
    file_id BIGINT PRIMARY KEY,
    original_storage_type VARCHAR(32) NULL,
    original_file_path TEXT NULL,
    target_bucket VARCHAR(128) NOT NULL,
    target_key VARCHAR(768) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    size BIGINT NOT NULL,
    object_version VARCHAR(256) NULL,
    status VARCHAR(16) NOT NULL,
    verified_time DATETIME NULL,
    switched_time DATETIME NULL
);
