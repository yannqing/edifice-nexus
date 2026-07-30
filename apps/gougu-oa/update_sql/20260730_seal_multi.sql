CREATE TABLE IF NOT EXISTS `oa_seal_item` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT,
  `seal_id` int(11) UNSIGNED NOT NULL DEFAULT 0 COMMENT '用章申请ID',
  `seal_cate_id` int(11) UNSIGNED NOT NULL DEFAULT 0 COMMENT '印章类型ID',
  `sort` int(11) UNSIGNED NOT NULL DEFAULT 0 COMMENT '选择顺序',
  `create_time` bigint(11) NOT NULL DEFAULT 0 COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_seal_cate` (`seal_id`,`seal_cate_id`) USING BTREE,
  KEY `idx_seal_id` (`seal_id`) USING BTREE,
  KEY `idx_seal_cate_id` (`seal_cate_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COMMENT = '用章申请印章类型关联表';

INSERT IGNORE INTO `oa_seal_item` (`seal_id`, `seal_cate_id`, `sort`, `create_time`)
SELECT `id`, `seal_cate_id`, 1, `create_time`
FROM `oa_seal`
WHERE `seal_cate_id` > 0;
