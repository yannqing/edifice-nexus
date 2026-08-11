-- 用车申请功能（可重复执行）

CREATE TABLE IF NOT EXISTS `oa_car_apply` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `admin_id` int(11) NOT NULL DEFAULT 0 COMMENT '申请人ID',
  `did` int(11) NOT NULL DEFAULT 0 COMMENT '所属部门ID',
  `start_address` varchar(255) NOT NULL DEFAULT '' COMMENT '始发地',
  `destination` varchar(255) NOT NULL DEFAULT '' COMMENT '目的地',
  `use_start_time` bigint(11) NOT NULL DEFAULT 0 COMMENT '用车开始时间',
  `use_end_time` bigint(11) NOT NULL DEFAULT 0 COMMENT '用车结束时间',
  `contact_name` varchar(100) NOT NULL DEFAULT '' COMMENT '联系人',
  `contact_phone` varchar(30) NOT NULL DEFAULT '' COMMENT '联系电话',
  `budget_item` varchar(255) NOT NULL DEFAULT '' COMMENT '预算事项',
  `passenger_count` int(11) NOT NULL DEFAULT 1 COMMENT '乘车人数',
  `business_type` varchar(100) NOT NULL DEFAULT '' COMMENT '业务类别',
  `description` text COMMENT '用车说明',
  `file_ids` varchar(500) NOT NULL DEFAULT '' COMMENT '附件ID',
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
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_check_status` (`check_status`),
  KEY `idx_use_time` (`use_start_time`, `use_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用车申请表';

INSERT INTO `oa_basic_adm` (`types`, `title`, `status`, `create_time`, `update_time`)
SELECT '3', '客户拜访', 1, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_basic_adm` WHERE `types` = '3' AND `title` = '客户拜访');

INSERT INTO `oa_basic_adm` (`types`, `title`, `status`, `create_time`, `update_time`)
SELECT '3', '商务接待', 1, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_basic_adm` WHERE `types` = '3' AND `title` = '商务接待');

INSERT INTO `oa_basic_adm` (`types`, `title`, `status`, `create_time`, `update_time`)
SELECT '3', '项目现场', 1, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_basic_adm` WHERE `types` = '3' AND `title` = '项目现场');

INSERT INTO `oa_basic_adm` (`types`, `title`, `status`, `create_time`, `update_time`)
SELECT '3', '会议/活动', 1, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_basic_adm` WHERE `types` = '3' AND `title` = '会议/活动');

INSERT INTO `oa_basic_adm` (`types`, `title`, `status`, `create_time`, `update_time`)
SELECT '3', '其他公务', 1, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_basic_adm` WHERE `types` = '3' AND `title` = '其他公务');

INSERT INTO `oa_template` (
  `title`, `name`, `types`, `check_types`, `remark`, `msg_link`,
  `msg_title_0`, `msg_content_0`, `msg_title_1`, `msg_content_1`,
  `msg_title_2`, `msg_content_2`, `msg_title_3`, `msg_content_3`,
  `email_link`, `status`, `admin_id`, `create_time`, `update_time`, `delete_time`
)
SELECT
  '用车审批', 'car_apply', 2, 0, '', '/adm/carapply/view/id/{action_id}',
  '{from_user}提交了一个『用车申请』，请及时审批', '您有一个新的『用车申请』需要处理。',
  '您提交的『用车申请』已被审批通过。', '您在{create_time}提交的『用车申请』已于{date}被审批通过。',
  '您提交的『用车申请』已被驳回拒绝。', '您在{create_time}提交的『用车申请』已于{date}被驳回拒绝。',
  '{from_user}提交的『用车申请』已被审批通过并抄送给你',
  '{from_user}在{create_time}提交的『用车申请』已被审批通过并抄送给你，请及时查看详情。',
  '', 1, 1, UNIX_TIMESTAMP(), 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM `oa_template` WHERE `name` = 'car_apply' AND `delete_time` = 0
);

SET @car_apply_template_id = (
  SELECT `id` FROM `oa_template`
  WHERE `name` = 'car_apply' AND `delete_time` = 0
  ORDER BY `id` ASC LIMIT 1
);

INSERT INTO `oa_flow_cate` (
  `title`, `name`, `module_id`, `check_table`, `icon`, `department_ids`, `sort`,
  `is_copy`, `is_file`, `is_export`, `is_back`, `is_reversed`, `form`,
  `add_url`, `view_url`, `form_id`, `is_list`, `status`, `template_id`,
  `create_time`, `update_time`
)
SELECT
  '用车申请', 'car_apply', 2, 'car_apply', 'icon-qiche', '', 0,
  1, 1, 0, 1, 0, 1, '/adm/carapply/add', '/adm/carapply/view', 0, 1, 1,
  @car_apply_template_id, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_flow_cate` WHERE `name` = 'car_apply');

SET @car_apply_cate_id = (
  SELECT `id` FROM `oa_flow_cate`
  WHERE `name` = 'car_apply'
  ORDER BY `id` ASC LIMIT 1
);

UPDATE `oa_flow_cate`
SET `module_id` = 2,
    `check_table` = 'car_apply',
    `add_url` = '/adm/carapply/add',
    `view_url` = '/adm/carapply/view',
    `form` = 1,
    `is_list` = 1,
    `status` = 1,
    `template_id` = @car_apply_template_id,
    `update_time` = UNIX_TIMESTAMP()
WHERE `id` = @car_apply_cate_id;

INSERT INTO `oa_flow` (
  `title`, `cate_id`, `check_type`, `department_ids`, `copy_uids`, `flow_list`,
  `status`, `remark`, `admin_id`, `create_time`, `update_time`, `delete_time`
)
SELECT
  '用车审批', @car_apply_cate_id, 1, '', '', '', 1, '', 1, UNIX_TIMESTAMP(), 0, 0
WHERE @car_apply_cate_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `oa_flow`
    WHERE `cate_id` = @car_apply_cate_id AND `delete_time` = 0
  );

INSERT INTO `oa_admin_rule` (`id`, `pid`, `src`, `title`, `name`, `module`, `icon`, `menu`, `sort`, `status`, `create_time`, `update_time`)
SELECT 700001000, 4, 'adm/carapply/datalist', '用车申请', '用车申请', 'adm', 'icon-qiche', 1, 2, 1, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_admin_rule` WHERE `id` = 700001000 OR `src` = 'adm/carapply/datalist');

SET @car_apply_rule_id = (SELECT `id` FROM `oa_admin_rule` WHERE `src` = 'adm/carapply/datalist' LIMIT 1);

INSERT INTO `oa_admin_rule` (`id`, `pid`, `src`, `title`, `name`, `module`, `icon`, `menu`, `sort`, `status`, `create_time`, `update_time`)
SELECT 700001001, @car_apply_rule_id, 'adm/carapply/add', '新建/编辑', '用车申请', 'adm', '', 2, 1, 1, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_admin_rule` WHERE `id` = 700001001 OR `src` = 'adm/carapply/add');

INSERT INTO `oa_admin_rule` (`id`, `pid`, `src`, `title`, `name`, `module`, `icon`, `menu`, `sort`, `status`, `create_time`, `update_time`)
SELECT 700001002, @car_apply_rule_id, 'adm/carapply/view', '查看', '用车申请', 'adm', '', 2, 1, 1, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_admin_rule` WHERE `id` = 700001002 OR `src` = 'adm/carapply/view');

INSERT INTO `oa_admin_rule` (`id`, `pid`, `src`, `title`, `name`, `module`, `icon`, `menu`, `sort`, `status`, `create_time`, `update_time`)
SELECT 700001003, @car_apply_rule_id, 'adm/carapply/del', '删除', '用车申请', 'adm', '', 2, 1, 1, UNIX_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM `oa_admin_rule` WHERE `id` = 700001003 OR `src` = 'adm/carapply/del');

SET @car_apply_add_rule_id = (SELECT `id` FROM `oa_admin_rule` WHERE `src` = 'adm/carapply/add' LIMIT 1);
SET @car_apply_view_rule_id = (SELECT `id` FROM `oa_admin_rule` WHERE `src` = 'adm/carapply/view' LIMIT 1);
SET @car_apply_del_rule_id = (SELECT `id` FROM `oa_admin_rule` WHERE `src` = 'adm/carapply/del' LIMIT 1);

UPDATE `oa_admin_group`
SET `rules` = CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM `rules`), ''), @car_apply_rule_id)
WHERE `id` = 1 AND @car_apply_rule_id IS NOT NULL AND COALESCE(FIND_IN_SET(@car_apply_rule_id, `rules`), 0) = 0;

UPDATE `oa_admin_group`
SET `rules` = CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM `rules`), ''), @car_apply_add_rule_id)
WHERE `id` = 1 AND @car_apply_add_rule_id IS NOT NULL AND COALESCE(FIND_IN_SET(@car_apply_add_rule_id, `rules`), 0) = 0;

UPDATE `oa_admin_group`
SET `rules` = CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM `rules`), ''), @car_apply_view_rule_id)
WHERE `id` = 1 AND @car_apply_view_rule_id IS NOT NULL AND COALESCE(FIND_IN_SET(@car_apply_view_rule_id, `rules`), 0) = 0;

UPDATE `oa_admin_group`
SET `rules` = CONCAT_WS(',', NULLIF(TRIM(BOTH ',' FROM `rules`), ''), @car_apply_del_rule_id)
WHERE `id` = 1 AND @car_apply_del_rule_id IS NOT NULL AND COALESCE(FIND_IN_SET(@car_apply_del_rule_id, `rules`), 0) = 0;
