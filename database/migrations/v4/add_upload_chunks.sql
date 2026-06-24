-- v4: 分片上传支持
-- upload_chunks 表：记录每个分片的上传状态

CREATE TABLE IF NOT EXISTS `upload_chunks` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `upload_id` varchar(64) NOT NULL COMMENT '上传会话ID（前端生成UUID）',
    `chunk_index` int NOT NULL COMMENT '分片序号（0-based）',
    `chunk_size` bigint NOT NULL COMMENT '分片大小（字节）',
    `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
    `total_chunks` int NOT NULL COMMENT '总分片数',
    `total_size` bigint NOT NULL COMMENT '文件总大小（字节）',
    `file_type` varchar(32) NOT NULL DEFAULT 'document' COMMENT '文件类型：document/image/audio',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-上传中/1-已完成',
    `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_upload_id` (`upload_id`),
    INDEX `idx_upload_chunk` (`upload_id`, `chunk_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分片上传记录';
