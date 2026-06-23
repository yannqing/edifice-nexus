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
-- 三、执行后状态
-- ============================================================================
-- 执行后两个库的业务数据全部清空，系统回到「初始状态」：
--   - 所有用户、项目、合同、产值、审批记录全部清除
--   - 角色/权限/菜单/字典/模板/审批流配置保留
--   - 下次 OA 创建员工后，会通过同步任务重新填充 edifice_db.sys_user 等表
--
-- 注意：清空后 edifice_db.sys_user 也空了，需要先在 OA 里创建管理员账号
--       （通常 oa_admin id=1 是 super admin，需要手工 INSERT 或通过 OA 后台创建），
--       否则 edifice 无法登录。
-- ============================================================================
