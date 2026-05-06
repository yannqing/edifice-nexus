CREATE TABLE IF NOT EXISTS `sys_department` (
  `department_id` bigint NOT NULL COMMENT '部门id',
  `oa_department_id` int DEFAULT NULL COMMENT 'OA部门id',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父部门id',
  `oa_parent_id` int DEFAULT NULL COMMENT 'OA父部门id',
  `name` varchar(128) NOT NULL COMMENT '部门名称',
  `leader_user_id` bigint DEFAULT NULL COMMENT '部门负责人用户id',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `synced_at` datetime DEFAULT NULL COMMENT '最近同步时间',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`department_id`),
  UNIQUE KEY `uk_sys_department_oa` (`oa_department_id`),
  KEY `idx_sys_department_parent` (`parent_id`),
  KEY `idx_sys_department_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门镜像表';

CREATE TABLE IF NOT EXISTS `sys_position` (
  `position_id` bigint NOT NULL COMMENT '岗位id',
  `oa_position_id` int DEFAULT NULL COMMENT 'OA岗位id',
  `name` varchar(128) NOT NULL COMMENT '岗位名称',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `synced_at` datetime DEFAULT NULL COMMENT '最近同步时间',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`position_id`),
  UNIQUE KEY `uk_sys_position_oa` (`oa_position_id`),
  KEY `idx_sys_position_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位镜像表';

CREATE TABLE IF NOT EXISTS `sys_user_department` (
  `id` bigint NOT NULL COMMENT '主键id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `department_id` bigint NOT NULL COMMENT '部门id',
  `oa_department_id` int DEFAULT NULL COMMENT 'OA部门id',
  `is_primary` tinyint NOT NULL DEFAULT 0 COMMENT '是否主部门',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_department` (`user_id`, `department_id`, `is_delete`),
  KEY `idx_sys_user_department_dept` (`department_id`),
  KEY `idx_sys_user_department_oa_dept` (`oa_department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户部门关联镜像表';

ALTER TABLE `sys_user`
  ADD COLUMN `oa_admin_id` int DEFAULT NULL COMMENT 'OA员工id' AFTER `position`,
  ADD COLUMN `oa_userid` varchar(100) DEFAULT NULL COMMENT 'OA userid/外部用户标识' AFTER `oa_admin_id`,
  ADD COLUMN `department_id` bigint DEFAULT NULL COMMENT '主部门id' AFTER `oa_userid`,
  ADD COLUMN `oa_department_id` int DEFAULT NULL COMMENT 'OA主部门id' AFTER `department_id`,
  ADD COLUMN `position_id` bigint DEFAULT NULL COMMENT '岗位id' AFTER `oa_department_id`,
  ADD COLUMN `oa_position_id` int DEFAULT NULL COMMENT 'OA岗位id' AFTER `position_id`,
  ADD COLUMN `sync_source` varchar(32) DEFAULT NULL COMMENT '主数据来源' AFTER `remark`,
  ADD COLUMN `synced_at` datetime DEFAULT NULL COMMENT '最近同步时间' AFTER `sync_source`,
  ADD UNIQUE KEY `uk_sys_user_oa_admin` (`oa_admin_id`),
  ADD KEY `idx_sys_user_department` (`department_id`),
  ADD KEY `idx_sys_user_position_id` (`position_id`);
