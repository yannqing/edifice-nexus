-- 转正申请功能（可重复执行）

CREATE TABLE IF NOT EXISTS `oa_employee_regularization` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `admin_id` int(11) NOT NULL DEFAULT 0 COMMENT '申请人ID',
  `did` int(11) NOT NULL DEFAULT 0 COMMENT '所属部门ID',
  `applicant_name` varchar(100) NOT NULL DEFAULT '' COMMENT '申请人姓名快照',
  `application_time` bigint(11) NOT NULL DEFAULT 0 COMMENT '申请时间',
  `gender` varchar(20) NOT NULL DEFAULT '' COMMENT '性别快照',
  `birth_date` varchar(50) NOT NULL DEFAULT '' COMMENT '出生年月快照',
  `graduate_school` varchar(255) NOT NULL DEFAULT '' COMMENT '毕业院校快照',
  `speciality` varchar(255) NOT NULL DEFAULT '' COMMENT '专业快照',
  `highest_education` varchar(100) NOT NULL DEFAULT '' COMMENT '最高学历快照',
  `professional_title` varchar(100) NOT NULL DEFAULT '' COMMENT '职称快照',
  `position_id` int(11) NOT NULL DEFAULT 0 COMMENT '岗位ID快照',
  `position_name` varchar(100) NOT NULL DEFAULT '' COMMENT '岗位名称快照',
  `employee_grade` varchar(100) NOT NULL DEFAULT '' COMMENT '员工职级快照',
  `entry_date` bigint(11) NOT NULL DEFAULT 0 COMMENT '入职日期快照',
  `department_name` varchar(100) NOT NULL DEFAULT '' COMMENT '所属部门名称快照',
  `probation_start_date` bigint(11) NOT NULL DEFAULT 0 COMMENT '试用开始日期',
  `probation_end_date` bigint(11) NOT NULL DEFAULT 0 COMMENT '试用结束日期',
  `work_summary` mediumtext COMMENT '工作总结',
  `main_achievements` mediumtext COMMENT '主要业绩',
  `check_status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '审核状态：0待审核 1审核中 2审核通过 3审核不通过 4撤销审核',
  `check_flow_id` int(11) NOT NULL DEFAULT 0 COMMENT '审核流程ID',
  `check_step_sort` int(11) NOT NULL DEFAULT 0 COMMENT '当前审核步骤',
  `check_uids` varchar(500) NOT NULL DEFAULT '' COMMENT '当前审核人ID',
  `check_last_uid` int(11) NOT NULL DEFAULT 0 COMMENT '上一审核人ID',
  `check_history_uids` text COMMENT '历史审核人ID',
  `check_copy_uids` varchar(500) NOT NULL DEFAULT '' COMMENT '抄送人ID',
  `check_time` bigint(11) NOT NULL DEFAULT 0 COMMENT '审核通过时间',
  `create_time` bigint(11) NOT NULL DEFAULT 0 COMMENT '创建时间',
  `update_time` bigint(11) NOT NULL DEFAULT 0 COMMENT '更新时间',
  `delete_time` bigint(11) NOT NULL DEFAULT 0 COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_admin_status` (`admin_id`, `check_status`, `delete_time`),
  KEY `idx_department` (`did`),
  KEY `idx_probation_period` (`probation_start_date`, `probation_end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工转正申请表';

INSERT INTO `oa_template` (
  `title`, `name`, `types`, `check_types`, `remark`, `msg_link`,
  `msg_title_0`, `msg_content_0`, `msg_title_1`, `msg_content_1`,
  `msg_title_2`, `msg_content_2`, `msg_title_3`, `msg_content_3`,
  `email_link`, `status`, `admin_id`, `create_time`, `update_time`, `delete_time`
)
SELECT
  '转正审批', 'employee_regularization', 2, 0, '', '/user/regularization/view/id/{action_id}',
  '{from_user}提交了一个『转正申请』，请及时审批', '您有一个新的『转正申请』需要处理。',
  '您提交的『转正申请』已被审批通过。', '您在{create_time}提交的『转正申请』已于{date}被审批通过。',
  '您提交的『转正申请』已被驳回拒绝。', '您在{create_time}提交的『转正申请』已于{date}被驳回拒绝。',
  '{from_user}提交的『转正申请』已被审批通过并抄送给你',
  '{from_user}在{create_time}提交的『转正申请』已被审批通过并抄送给你，请及时查看详情。',
  '', 1, 1, UNIX_TIMESTAMP(), 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM `oa_template` WHERE `name` = 'employee_regularization' AND `delete_time` = 0
);

SET @regularization_template_id = (
  SELECT `id` FROM `oa_template`
  WHERE `name` = 'employee_regularization' AND `delete_time` = 0
  ORDER BY `id` ASC LIMIT 1
);

INSERT INTO `oa_flow_cate` (
  `title`, `name`, `module_id`, `check_table`, `icon`, `department_ids`, `sort`,
  `is_copy`, `is_file`, `is_export`, `is_back`, `is_reversed`, `form`,
  `add_url`, `view_url`, `form_id`, `is_list`, `status`, `template_id`,
  `create_time`, `update_time`
)
SELECT
  '转正申请', 'employee_regularization', 5, 'employee_regularization', 'icon-yuangong', '', 1,
  1, 1, 0, 1, 0, 1, '/user/regularization/add', '/user/regularization/view', 0, 1, 1,
  @regularization_template_id, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_flow_cate` WHERE `name` = 'employee_regularization');

SET @regularization_cate_id = (
  SELECT `id` FROM `oa_flow_cate`
  WHERE `name` = 'employee_regularization'
  ORDER BY `id` ASC LIMIT 1
);

UPDATE `oa_flow_cate`
SET `title` = '转正申请',
    `module_id` = 5,
    `check_table` = 'employee_regularization',
    `icon` = 'icon-yuangong',
    `add_url` = '/user/regularization/add',
    `view_url` = '/user/regularization/view',
    `form` = 1,
    `is_list` = 1,
    `status` = 1,
    `template_id` = @regularization_template_id,
    `update_time` = UNIX_TIMESTAMP()
WHERE `id` = @regularization_cate_id;

INSERT INTO `oa_flow` (
  `title`, `cate_id`, `check_type`, `department_ids`, `copy_uids`, `flow_list`,
  `status`, `remark`, `admin_id`, `create_time`, `update_time`, `delete_time`
)
SELECT
  '转正审批', @regularization_cate_id, 1, '', '', '', 1, '', 1, UNIX_TIMESTAMP(), 0, 0
WHERE @regularization_cate_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `oa_flow`
    WHERE `cate_id` = @regularization_cate_id AND `delete_time` = 0
  );

INSERT INTO `oa_admin_rule` (`id`, `pid`, `src`, `title`, `name`, `module`, `icon`, `menu`, `sort`, `status`, `create_time`, `update_time`)
SELECT 700002000, 3, 'user/regularization/datalist', '转正申请', '转正申请', 'user', 'icon-yuangong', 1, 2, 1, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_admin_rule` WHERE `id` = 700002000 OR `src` = 'user/regularization/datalist');

SET @regularization_rule_id = (SELECT `id` FROM `oa_admin_rule` WHERE `src` = 'user/regularization/datalist' LIMIT 1);

INSERT INTO `oa_admin_rule` (`id`, `pid`, `src`, `title`, `name`, `module`, `icon`, `menu`, `sort`, `status`, `create_time`, `update_time`)
SELECT 700002001, @regularization_rule_id, 'user/regularization/add', '新建/编辑', '转正申请', 'user', '', 2, 1, 1, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_admin_rule` WHERE `id` = 700002001 OR `src` = 'user/regularization/add');

INSERT INTO `oa_admin_rule` (`id`, `pid`, `src`, `title`, `name`, `module`, `icon`, `menu`, `sort`, `status`, `create_time`, `update_time`)
SELECT 700002002, @regularization_rule_id, 'user/regularization/view', '查看', '转正申请', 'user', '', 2, 1, 1, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_admin_rule` WHERE `id` = 700002002 OR `src` = 'user/regularization/view');

INSERT INTO `oa_admin_rule` (`id`, `pid`, `src`, `title`, `name`, `module`, `icon`, `menu`, `sort`, `status`, `create_time`, `update_time`)
SELECT 700002003, @regularization_rule_id, 'user/regularization/del', '删除', '转正申请', 'user', '', 2, 1, 1, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_admin_rule` WHERE `id` = 700002003 OR `src` = 'user/regularization/del');

SET @regularization_add_rule_id = (SELECT `id` FROM `oa_admin_rule` WHERE `src` = 'user/regularization/add' LIMIT 1);
SET @regularization_view_rule_id = (SELECT `id` FROM `oa_admin_rule` WHERE `src` = 'user/regularization/view' LIMIT 1);
SET @regularization_del_rule_id = (SELECT `id` FROM `oa_admin_rule` WHERE `src` = 'user/regularization/del' LIMIT 1);

UPDATE `oa_admin_group`
SET `rules` = CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM `rules`), ''), CAST(@regularization_rule_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_general_ci)
WHERE `id` = 1 AND @regularization_rule_id IS NOT NULL
  AND COALESCE(FIND_IN_SET(CAST(@regularization_rule_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_general_ci, `rules` COLLATE utf8mb4_general_ci), 0) = 0;

UPDATE `oa_admin_group`
SET `rules` = CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM `rules`), ''), CAST(@regularization_add_rule_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_general_ci)
WHERE `id` = 1 AND @regularization_add_rule_id IS NOT NULL
  AND COALESCE(FIND_IN_SET(CAST(@regularization_add_rule_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_general_ci, `rules` COLLATE utf8mb4_general_ci), 0) = 0;

UPDATE `oa_admin_group`
SET `rules` = CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM `rules`), ''), CAST(@regularization_view_rule_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_general_ci)
WHERE `id` = 1 AND @regularization_view_rule_id IS NOT NULL
  AND COALESCE(FIND_IN_SET(CAST(@regularization_view_rule_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_general_ci, `rules` COLLATE utf8mb4_general_ci), 0) = 0;

UPDATE `oa_admin_group`
SET `rules` = CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM `rules`), ''), CAST(@regularization_del_rule_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_general_ci)
WHERE `id` = 1 AND @regularization_del_rule_id IS NOT NULL
  AND COALESCE(FIND_IN_SET(CAST(@regularization_del_rule_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_general_ci, `rules` COLLATE utf8mb4_general_ci), 0) = 0;
