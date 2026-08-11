-- 补卡申请功能（可重复执行）

CREATE TABLE IF NOT EXISTS `oa_attendance_fix` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `admin_id` int(11) NOT NULL DEFAULT 0 COMMENT '申请人ID',
  `did` int(11) NOT NULL DEFAULT 0 COMMENT '部门ID',
  `fix_date` bigint(11) NOT NULL DEFAULT 0 COMMENT '补卡日期（时间戳）',
  `fix_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '补卡类型：1上班补卡 2下班补卡',
  `fix_time` varchar(10) NOT NULL DEFAULT '' COMMENT '补卡时间（HH:mm）',
  `reason` varchar(500) NOT NULL DEFAULT '' COMMENT '补卡原因',
  `file_ids` varchar(255) NOT NULL DEFAULT '' COMMENT '附件ID',
  `check_status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '审核状态：0待审核 1审核中 2审核通过 3审核不通过 4撤销审核',
  `check_flow_id` int(11) NOT NULL DEFAULT 0 COMMENT '审核流程id',
  `check_step_sort` int(11) NOT NULL DEFAULT 0 COMMENT '当前审核步骤',
  `check_uids` varchar(255) NOT NULL DEFAULT '' COMMENT '当前审核人id',
  `check_last_uid` int(11) NOT NULL DEFAULT 0 COMMENT '上一审核人',
  `check_history_uids` text COMMENT '历史审核人id',
  `check_copy_uids` varchar(255) NOT NULL DEFAULT '' COMMENT '抄送人id',
  `check_time` bigint(11) NOT NULL DEFAULT 0 COMMENT '审核通过时间',
  `create_time` bigint(11) NOT NULL DEFAULT 0 COMMENT '创建时间',
  `update_time` bigint(11) NOT NULL DEFAULT 0 COMMENT '更新时间',
  `delete_time` bigint(11) NOT NULL DEFAULT 0 COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_check_status` (`check_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补卡申请表';

INSERT INTO `oa_template` (
  `title`, `name`, `types`, `check_types`, `remark`, `msg_link`,
  `msg_title_0`, `msg_content_0`, `msg_title_1`, `msg_content_1`,
  `msg_title_2`, `msg_content_2`, `msg_title_3`, `msg_content_3`,
  `email_link`, `status`, `admin_id`, `create_time`, `update_time`, `delete_time`
)
SELECT
  '补卡审批', 'attendance_fix', 2, 0, '', '/home/attendance_fix/view/id/{action_id}',
  '{from_user}提交了一个『补卡申请』，请及时审批', '您有一个新的『补卡申请』需要处理。',
  '您提交的『补卡申请』已被审批通过。', '您在{create_time}提交的『补卡申请』已于{date}被审批通过。',
  '您提交的『补卡申请』已被驳回拒绝。', '您在{create_time}提交的『补卡申请』已于{date}被驳回拒绝。',
  '{from_user}提交的『补卡审批』已被审批通过并抄送给你',
  '{from_user}在{create_time}提交的『补卡审批』已被审批通过并抄送给你，请及时查看详情。',
  '', 1, 1, UNIX_TIMESTAMP(), 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM `oa_template` WHERE `name` = 'attendance_fix' AND `delete_time` = 0
);

SET @attendance_fix_template_id = (
  SELECT `id` FROM `oa_template`
  WHERE `name` = 'attendance_fix' AND `delete_time` = 0
  ORDER BY `id` ASC LIMIT 1
);

INSERT INTO `oa_flow_cate` (
  `title`, `name`, `module_id`, `check_table`, `icon`, `department_ids`, `sort`,
  `is_copy`, `is_file`, `is_export`, `is_back`, `is_reversed`, `form`,
  `add_url`, `view_url`, `form_id`, `is_list`, `status`, `template_id`,
  `create_time`, `update_time`
)
SELECT
  '补卡申请', 'attendance_fix', 1, 'attendance_fix', 'icon-kechengziyuanguanli', '', 0,
  1, 1, 0, 1, 0, 1, '/home/attendance_fix/add', '/home/attendance_fix/view', 0, 1, 1,
  @attendance_fix_template_id, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (
  SELECT 1 FROM `oa_flow_cate` WHERE `name` = 'attendance_fix'
);

SET @attendance_fix_cate_id = (
  SELECT `id` FROM `oa_flow_cate`
  WHERE `name` = 'attendance_fix'
  ORDER BY `id` ASC LIMIT 1
);

UPDATE `oa_flow_cate`
SET `check_table` = 'attendance_fix',
    `add_url` = '/home/attendance_fix/add',
    `view_url` = '/home/attendance_fix/view',
    `form` = 1,
    `is_list` = 1,
    `status` = 1,
    `template_id` = @attendance_fix_template_id,
    `update_time` = UNIX_TIMESTAMP()
WHERE `id` = @attendance_fix_cate_id;

INSERT INTO `oa_flow` (
  `title`, `cate_id`, `check_type`, `department_ids`, `copy_uids`, `flow_list`,
  `status`, `remark`, `admin_id`, `create_time`, `update_time`, `delete_time`
)
SELECT
  '补卡审批', @attendance_fix_cate_id, 1, '', '', '', 1, '', 1, UNIX_TIMESTAMP(), 0, 0
WHERE @attendance_fix_cate_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `oa_flow`
    WHERE `cate_id` = @attendance_fix_cate_id AND `delete_time` = 0
  );
