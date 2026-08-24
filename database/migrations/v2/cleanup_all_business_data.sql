-- ============================================================================
-- 清空 edifice_db + office_db 的所有业务数据（保留表结构和字典配置）
--
-- ⚠️ 破坏性操作！执行前务必备份两个库：
--    docker exec mysql mysqldump -uroot -p<pwd> --databases edifice_db office_db > backup.sql
--
-- 执行方式（在服务器 docker mysql 容器内）：
--    docker cp database/migrations/v2/cleanup_all_business_data.sql mysql:/tmp/cleanup.sql
--    docker exec -i mysql sh -c "mysql -uroot -p<pwd> < /tmp/cleanup.sql"
--
-- 保留的内容（不清空）：
--   edifice_db:
--     - sys_permission（菜单权限定义）— 24 条
--     - sys_role（角色定义）— 但清空关联，保留 SUPER_ADMIN / STAFF 等系统角色
--     - sys_department（部门）— 清空，由 OA 同步重建
--     - sys_position（岗位）— 清空，由 OA 同步重建
--     - project_stage_template（阶段模板）— 18 条，业务预设
--     - project_type（项目类型）— 5 条，业务预设
--     - business_rule_config（业务规则配置）— 9 条，业务预设
--     - approval_flow_config / approval_flow_node（审批流程配置）— 业务预设
--   office_db:
--     - oa_admin_module（后台菜单）— 9 条
--     - oa_admin_rule（权限规则）— 429 条
--     - oa_config（系统配置）— 5 条
--     - oa_department（部门）— 15 条，由 OA 维护
--     - oa_position（岗位）— 3 条
--     - oa_basic_*（客户来源/等级等字典）— 全部保留
--     - oa_*_cate（各类分类字典）— 全部保留
--     - oa_template（审批模板）— 20 条
--     - oa_flow_cate / oa_flow_module（审批流分类）— 全部保留
--     - oa_mobile_*（移动端配置）— 全部保留
--     - oa_links / oa_industry / oa_disk_group 等配置表 — 全部保留
--
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、edifice_db 清空业务数据
-- ============================================================================

-- 1. 审批流相关（业务数据：审批记录、抄送、催办；保留 flow_config/node 模板）
TRUNCATE TABLE edifice_db.approval_records;
TRUNCATE TABLE edifice_db.approval_cc;
TRUNCATE TABLE edifice_db.approval_urge;
TRUNCATE TABLE edifice_db.user_message_read;

-- 2. 产值分配相关
TRUNCATE TABLE edifice_db.output_value_distribution;
TRUNCATE TABLE edifice_db.output_value_work_pool;
TRUNCATE TABLE edifice_db.output_value_adjustment_detail;
TRUNCATE TABLE edifice_db.output_value;
TRUNCATE TABLE edifice_db.performance_restore;

-- 3. 项目相关
TRUNCATE TABLE edifice_db.project_files;
TRUNCATE TABLE edifice_db.project_acceptance;
TRUNCATE TABLE edifice_db.project_member;
TRUNCATE TABLE edifice_db.project_stage;
TRUNCATE TABLE edifice_db.project;

-- 4. 合同相关
TRUNCATE TABLE edifice_db.contract_change_log;
TRUNCATE TABLE edifice_db.contract_benefit_revision;
TRUNCATE TABLE edifice_db.contract;

-- 5. 验工 / 验收 / 投标
TRUNCATE TABLE edifice_db.inspection_form;
TRUNCATE TABLE edifice_db.bid_file;
TRUNCATE TABLE edifice_db.bid;

-- 6. 回款 / 工时 / 公告 / OA申请
TRUNCATE TABLE edifice_db.collection_record;
TRUNCATE TABLE edifice_db.timesheet;
TRUNCATE TABLE edifice_db.announcement;
TRUNCATE TABLE edifice_db.oa_application;
TRUNCATE TABLE edifice_db.oa_user_sync_outbox;

-- 7. 文件 / 审计日志
TRUNCATE TABLE edifice_db.files;
TRUNCATE TABLE edifice_db.operation_audit_log;

-- 8. 用户/角色/权限关联（物理清空所有关联，含历史膨胀的 is_delete=1 行）
--    注意：sys_permission / sys_role / sys_position / sys_department 的定义保留，
--    但用户-角色 / 角色-权限 / 用户-部门 关联全部清空（含软删除的历史行）
DELETE FROM edifice_db.sys_role_permission;       -- 含 46635 行膨胀数据
DELETE FROM edifice_db.sys_user_role;             -- 含 3727 行膨胀数据
DELETE FROM edifice_db.sys_user_department;
TRUNCATE TABLE edifice_db.sys_user;

-- sys_department / sys_position 清空（由 OA 重新同步）
TRUNCATE TABLE edifice_db.sys_department;
TRUNCATE TABLE edifice_db.sys_position;

-- ============================================================================
-- 二、office_db 清空业务数据（保留字典/配置/权限表）
-- ============================================================================

-- 1. 员工 / 用户（核心：清空后 edifice 也跟着重置）
TRUNCATE TABLE office_db.oa_admin;
TRUNCATE TABLE office_db.oa_admin_profiles;
TRUNCATE TABLE office_db.oa_admin_log;
TRUNCATE TABLE office_db.oa_admin_log_count;
TRUNCATE TABLE office_db.oa_admin_group;
TRUNCATE TABLE office_db.oa_position_group;
TRUNCATE TABLE office_db.oa_department_admin;
TRUNCATE TABLE office_db.oa_department_change;

-- 2. 审批流（业务数据，保留 oa_flow_cate/oa_flow_module/oa_template 配置）
TRUNCATE TABLE office_db.oa_approve;
TRUNCATE TABLE office_db.oa_flow;
TRUNCATE TABLE office_db.oa_flow_record;
TRUNCATE TABLE office_db.oa_flow_step;
TRUNCATE TABLE office_db.oa_step;

-- 3. 合同 / 客户 / 供应商 / 项目（业务数据）
TRUNCATE TABLE office_db.oa_contract;
TRUNCATE TABLE office_db.oa_customer;
TRUNCATE TABLE office_db.oa_customer_chance;
TRUNCATE TABLE office_db.oa_customer_contact;
TRUNCATE TABLE office_db.oa_customer_file;
TRUNCATE TABLE office_db.oa_customer_trace;
TRUNCATE TABLE office_db.oa_supplier;
TRUNCATE TABLE office_db.oa_supplier_contact;
TRUNCATE TABLE office_db.oa_project;
TRUNCATE TABLE office_db.oa_project_document;
TRUNCATE TABLE office_db.oa_project_file;
TRUNCATE TABLE office_db.oa_project_step;
TRUNCATE TABLE office_db.oa_project_step_record;
TRUNCATE TABLE office_db.oa_project_task;
TRUNCATE TABLE office_db.oa_project_user;

-- 4. 财务相关（费用/报销/借款/采购/开票/付款）
TRUNCATE TABLE office_db.oa_expense;
TRUNCATE TABLE office_db.oa_expense_interfix;
TRUNCATE TABLE office_db.oa_purchase;
TRUNCATE TABLE office_db.oa_purchased;
TRUNCATE TABLE office_db.oa_loan;
TRUNCATE TABLE office_db.oa_invoice;
TRUNCATE TABLE office_db.oa_invoice_income;
TRUNCATE TABLE office_db.oa_ticket;
TRUNCATE TABLE office_db.oa_ticket_payment;

-- 5. 人事行政（请假/加班/出差/转正/离职/考勤/关怀/奖惩/合同）
TRUNCATE TABLE office_db.oa_leaves;
TRUNCATE TABLE office_db.oa_overtimes;
TRUNCATE TABLE office_db.oa_outs;
TRUNCATE TABLE office_db.oa_trips;
TRUNCATE TABLE office_db.oa_personal_quit;
TRUNCATE TABLE office_db.oa_attendance;
TRUNCATE TABLE office_db.oa_care;
TRUNCATE TABLE office_db.oa_rewards;
TRUNCATE TABLE office_db.oa_labor_contract;

-- 6. 车辆 / 资产 / 印章 / 会议 / 公文
TRUNCATE TABLE office_db.oa_car;
TRUNCATE TABLE office_db.oa_car_fee;
TRUNCATE TABLE office_db.oa_car_mileage;
TRUNCATE TABLE office_db.oa_car_repair;
TRUNCATE TABLE office_db.oa_property;
TRUNCATE TABLE office_db.oa_property_repair;
TRUNCATE TABLE office_db.oa_seal;
TRUNCATE TABLE office_db.oa_meeting_order;
TRUNCATE TABLE office_db.oa_meeting_records;
TRUNCATE TABLE office_db.oa_meeting_room;
TRUNCATE TABLE office_db.oa_official_docs;

-- 7. 日常办公（工作/计划/日程/笔记/消息/公告/评论/网盘）
TRUNCATE TABLE office_db.oa_work;
TRUNCATE TABLE office_db.oa_work_record;
TRUNCATE TABLE office_db.oa_plan;
TRUNCATE TABLE office_db.oa_schedule;
TRUNCATE TABLE office_db.oa_note;
TRUNCATE TABLE office_db.oa_message;
TRUNCATE TABLE office_db.oa_msg;
TRUNCATE TABLE office_db.oa_news;
TRUNCATE TABLE office_db.oa_article;
TRUNCATE TABLE office_db.oa_comment;
TRUNCATE TABLE office_db.oa_comment_read;
TRUNCATE TABLE office_db.oa_disk;
TRUNCATE TABLE office_db.oa_file;
TRUNCATE TABLE office_db.oa_edit_log;
TRUNCATE TABLE office_db.oa_third_message;

-- 8. 其他业务数据
TRUNCATE TABLE office_db.oa_basic_user;
TRUNCATE TABLE office_db.oa_services;
TRUNCATE TABLE office_db.oa_product;
TRUNCATE TABLE office_db.oa_enterprise;
TRUNCATE TABLE office_db.oa_blacklist;
TRUNCATE TABLE office_db.oa_talent;
TRUNCATE TABLE office_db.oa_basic_customer;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 三、恢复默认管理员账号（admin / 123456）
-- ============================================================================
-- 清库后两个系统都没有账号，这里自动恢复一个 super admin，确保清库后能登录。
-- 密码：123456
--   - OA（oa_admin）：md5(md5('123456' + 'edifice123') + 'edifice123') = 15c25f5a7515747f3333b1b670d69259
--   - Edifice（sys_user）：走 OA 密码代理 {edifice-oa}1:（oaAdminId=1，校验时查 OA 库）

-- ---------- 3.1 OA：恢复 admin + 权限组 + 岗位关联 ----------

-- oa_admin：super admin（id=1）
INSERT INTO office_db.oa_admin (
    id, userid, username, pwd, salt, name, email, mobile,
    sex, nickname, thumb, theme, did, pid, position_id,
    type, is_staff, status, create_time, update_time, entry_time,
    auth_did, is_lock
) VALUES (
    1, '2051970777591087105', 'admin',
    '15c25f5a7515747f3333b1b670d69259', 'edifice123',
    '系统管理员', 'admin@edifice.local', 13800138000,
    1, '系统管理员', '/static/home/images/icon.png', 'white',
    1, 0, 1, 1, 1, 1,
    UNIX_TIMESTAMP(), UNIX_TIMESTAMP(), UNIX_TIMESTAMP(),
    10, 0
);

-- oa_admin_group：4 个权限组（从生产备份固化，含完整 rules/layouts/mobile 配置）
INSERT INTO office_db.oa_admin_group (id, title, status, rules, layouts, mobile_bar, mobile_menu, `desc`, create_time, update_time) VALUES
(1, '超级权限角色', 1, '1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,193,194,195,196,197,198,199,200,201,202,203,204,205,206,207,208,209,210,211,212,213,214,215,216,217,218,219,220,221,222,223,224,225,226,227,228,229,230,231,232,233,234,235,236,237,238,239,240,241,242,243,244,245,246,247,248,249,250,251,252,253,254,255,256,257,258,259,260,261,262,263,264,265,266,267,268,269,270,271,272,273,274,275,276,277,278,279,280,281,282,283,284,285,286,287,288,289,290,291,292,293,294,295,296,297,298,299,300,301,302,303,304,305,306,307,308,309,310,311,312,313,314,315,316,317,318,319,320,321,322,323,324,325,326,327,328,329,330,331,332,333,334,335,336,337,338,339,340,341,342,343,344,345,346,347,348,349,350,351,352,353,354,355,356,357,358,359,360,361,362,363,364,365,366,367,368,369,370,371,372,373,374,375,376,377,378,379,380,381,382,383,384,385,386,387,388,389,390,391,392,393,394,395,396,397,398,399,400,401,402,403,404,405,406,407,408,409,410,411,412,900001000,900001001,900001002,900001003,900001004,900001005,900001006,900001007,900001008,900001009,900001010,900001012,900001013,900001014,900001015,900001016,900001017,900001018,900001019,900001020,900001021,900001022,900001023,900001024', '1,2,3,4,5,6,7,8,9,10,11,12,13', '1,2,3,4,5', '1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,5,17,18,19,20', '超级权限角色，拥有系统的最高权限，主要用于系统初始化数据而设，不可修改，不可删除。', 0, 0),
(2, '管理岗角色', 1, '1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,193,194,195,196,197,198,199,200,201,202,203,204,205,206,207,208,209,210,211,212,213,214,215,216,217,218,219,220,221,222,223,224,225,226,227,228,229,230,231,232,233,234,235,236,237,238,239,240,241,242,243,244,245,246,247,248,249,250,251,252,253,254,255,256,257,258,259,260,261,262,263,264,265,266,267,268,269,270,271,272,273,274,275,276,277,278,279,280,281,282,283,284,285,286,287,288,289,290,291,292,293,294,295,296,297,298,299,300,301,302,303,304,305,306,307,308,309,310,311,312,313,314,315,316,317,318,319,320,321,322,323,324,325,326,327,328,329,330,331,332,333,334,335,336,337,338,339,340,341,342,343,344,345,346,347,348,349,350,351,352,353,354,355,356,357,358,359,360,361,362,363,364,365,366,367,368,369,370,371,372,373,374,375,376,377,378,379,380,381,382,383,384,385,386,387,388,389,390,391,392,393,394,395,396,397,398,399,400,401,402,403,900001000,900001001,900001002,900001013,900001018,900001019', '1,2,3,4,5,6,7,8,9,10,11,12,13', '1,2,3,4,5', '1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,5,17,18,19,20', '管理岗角色权限，可根据公司的具体需求调整。', 0, 0),
(3, '业务岗角色', 1, '4,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,193,197,198,199,200,201,202,203,204,5,205,206,207,208,209,210,211,212,213,214,215,216,217,218,219,220,221,222,223,224,225,6,390,391,392,393,234,235,236,237,238,239,240,241,242,243,244,245,246,247,248,249,250,251,252,253,254,255,256,257,258,259,260,261,262,394,263,264,265,266,267,7,281,282,283,284,285,286,287,288,289,290,291,294,295,296,292,293,297,298,299,300,301,302,303,304,8,335,336,337,338,339,340,341,342,343,344,345,346,347,348,9,359,360,361,362,363,364,365,366,367,368,369,370,371,372,373,10,374,375,376,377,378,379,380,381,382,383,384,385,386,387,388,389,900001000,900001001,900001002,900001013,900001018,900001019', '1,2,3,4,5,6,7,8,9,10,11,12,13', '1,2,3,4,5', '1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,5,17,18,19,20', '业务岗角色权限，可根据公司的具体需求调整。', 0, 0),
(4, '测试权限', 1, '1,11,12,13,14,900001006,900001008,900001010,900001018,900001019,900001000,900001001,900001002,900001013', '1,11', '1', '1,11', '', 0, 0);

-- oa_position_group：岗位 → 权限组关联（admin position_id=1 → 超级权限组 group_id=1）
INSERT INTO office_db.oa_position_group (pid, group_id, create_time, update_time) VALUES
(1, 1, 1635755739, 0),
(2, 2, 1638007427, 0),
(3, 3, 1781510669, 0),
(3, 4, 1781510669, 0);

-- ---------- 3.2 Edifice：恢复 admin + 关联 SUPER_ADMIN 角色 ----------

-- sys_user：admin（password 用 OA 代理格式，校验时查 OA 库的 pwd/salt）
INSERT INTO edifice_db.sys_user (
    user_id, username, password, real_name, gender, email, phone,
    position, oa_admin_id, department_id, oa_department_id,
    position_id, oa_position_id, entry_date,
    employment_status, status, sync_source, synced_at, is_delete
) VALUES (
    2051970777591087105, 'admin', '{edifice-oa}1:',
    '系统管理员', 0, 'admin@edifice.local', '13800138000',
    '系统管理员', 1, 1, 1, 1, 1, '2026-05-06',
    1, 1, 'OA_SYNC', NOW(), 0
);

-- sys_user_role：admin → SUPER_ADMIN（role_id=900000000000000001）
INSERT INTO edifice_db.sys_user_role (
    id, user_id, role_id, source, source_ref, is_delete, project_id
) VALUES (
    900000000000000099, 2051970777591087105, 900000000000000001,
    'MANUAL', 'SUPER_ADMIN', 0, 0
);

-- ============================================================================
-- 四、执行后状态
-- ============================================================================
-- 执行后两个库的业务数据全部清空，系统回到「初始状态」：
--   - 所有用户、项目、合同、产值、审批记录全部清除
--   - 角色/权限/菜单/字典/模板/审批流配置保留
--   - 自动恢复 admin / 123456 超级管理员（OA + Edifice 都能登录）
--   - 下次 OA 创建员工后，会通过同步任务重新填充 edifice_db.sys_user 等表
-- ============================================================================
