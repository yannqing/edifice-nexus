CREATE TABLE IF NOT EXISTS `oa_user_sync_outbox` (
  `id` bigint NOT NULL,
  `event_type` varchar(32) NOT NULL COMMENT 'upsert/delete',
  `user_id` bigint NOT NULL,
  `payload` json NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-pending 1-success 2-failed',
  `retry_count` int NOT NULL DEFAULT 0,
  `last_error` varchar(1000) DEFAULT NULL,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_oa_user_sync_status_retry` (`status`, `retry_count`, `created_time`),
  KEY `idx_oa_user_sync_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OA 用户同步队列表';
