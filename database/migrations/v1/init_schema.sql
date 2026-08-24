-- auto-generated definition
create table project
(
    project_id   bigint                             not null comment '项目id'
        primary key,
    project_name varchar(100)                       not null comment '项目名称',
    project_code varchar(100)                       not null comment '项目唯一编码（项目编号）',
    project_type bigint                            not null comment '类型id',
    project_status tinyint									not null			comment '项目状态：0-未开始/1-进行中/2-待验收/3-验收中/4-已结束',
    is_show				tinyint		default 1				not null	comment '是否公开：0-不公开/1-公开',
    project_start_time datetime default CURRENT_TIMESTAMP not null comment '项目开始日期',
    project_end_time datetime default CURRENT_TIMESTAMP not null comment '项目结束日期',
    created_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete       tinyint  default 0                 not null comment '逻辑删除'
)

    comment '项目表';


-- auto-generated definition
create table project_type
(
    project_type_id   bigint                             not null comment '项目类型id'
        primary key,
    project_type_name varchar(100)                       not null comment '项目类型名称',
    project_code varchar(100)                       not null comment '项目类型唯一编码（项目编号）',
    project_type_status	tinyint 	default 1				not null		comment '项目类型状态：0-禁用/1-启用',
    created_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete       tinyint  default 0                 not null comment '逻辑删除'
)

    comment '项目类型表';



-- auto-generated definition
create table project_member
(
    project_member_id   bigint                             not null comment '项目成员id'
        primary key,
    project_id bigint                       not null comment '项目id',
    user_id bigint                       not null comment '用户id',
    project_role	bigint										not null	comment '项目内角色id',
    created_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete       tinyint  default 0                 not null comment '逻辑删除'
)

    comment '项目成员表';


-- auto-generated definition
create table contract
(
    contract_id   bigint                             not null comment '合同id'
        primary key,
    contract_name varchar(100)                       not null comment '合同名称',
    contract_code varchar(100)                       not null comment '合同唯一编码（项目编号）',
    contract_type	tinyint				default 0						not null comment '合同类型：0-基本收费/1-基本+效益',
    contract_amount decimal(20, 2)                                  null    comment '合同金额（元）',
    contract_file	bigint								not null				comment '合同主文件id',
    contract_other_files	json												null	comment '合同其他附件(id json 数组)',
    base_amount    decimal(20, 2)                                   null    comment '基本收益金额（元）',
    benefit_rules		varchar(255)										null			comment '效益收益规则（自由文本，仅说明）',
    benefit_amount	decimal(20, 2)									null			comment '当前预计效益金额（最新值，分阶段计入产值）',
    benefit_status	tinyint		default 0							not null	comment '效益状态：0-预计中/1-已最终确认',
    signing_date		datetime	default CURRENT_TIMESTAMP not null comment '项目签订日期',
    pre_start_date datetime	default CURRENT_TIMESTAMP	not null comment '项目预计开始日期',
    pre_end_date datetime	default CURRENT_TIMESTAMP	not null comment '项目预计结束日期',
    created_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete       tinyint  default 0                 not null comment '逻辑删除'
)
    comment '合同表';


-- v0.4 新增：合同效益预测修正历史
create table contract_benefit_revision
(
    revision_id     bigint                             not null comment '修正id'
        primary key,
    contract_id     bigint                             not null comment '合同id',
    old_amount      decimal(20, 2)                     null     comment '修正前金额（首次为空）',
    new_amount      decimal(20, 2)                     not null comment '修正后金额',
    delta_amount    decimal(20, 2)                     null     comment '差额 = new - old，首次为空',
    revision_reason varchar(512)                       null     comment '修正原因',
    is_final        tinyint  default 0                 not null comment '是否最终确认（1=结算，与 contract.benefit_status 联动）',
    operator_id     bigint                             null     comment '操作人id',
    created_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete       tinyint  default 0                 not null comment '逻辑删除',
    key idx_cbr_contract (contract_id),
    key idx_cbr_created (created_time)
)
    comment '合同效益预测修正历史表（v0.4）';




-- auto-generated definition
create table project_stage
(
    project_stage_id   bigint                             not null comment '项目阶段id'
        primary key,
    project_id bigint                      not null comment '项目id',
    stage_name	  varchar(255)						not null		comment '阶段名称',
    stage_status	tinyint			default 0		not null	comment '阶段状态：0-未开始/1-进行中/2-待验收/3-已验收/4-已驳回/5-待分配/6-已完成',
    stage_output	decimal(10, 2) default 0.00 not null	comment '基本部分累计计入比例（%，0-100）',
    benefit_inclusion_ratio decimal(10, 4) default 0.0000 not null comment '效益部分累计计入比例（%，0-100）',
    created_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete       tinyint  default 0                 not null comment '逻辑删除'
)
    comment '项目阶段表';


-- auto-generated definition
create table project_stage_template
(
    stage_id   bigint                             not null comment '阶段id'
        primary key,
    stage_name	  varchar(255)						not null		comment '阶段名称',
    stage_output	decimal(10, 2) default 0.00 not null	comment '阶段默认产值比例',
    stage_status 	tinyint		default 1				not null		comment '阶段状态：0-禁用/1-启用',
    created_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete       tinyint  default 0                 not null comment '逻辑删除'
)comment '项目阶段模版表';

-- auto-generated definition
create table project_files
(
    project_file_id     bigint                             not null comment '项目文件id'
        primary key,
    project_id          varchar(64)                        null     comment '项目id（历史遗留 varchar）',
    project_stage_id    bigint                             null     comment '项目阶段id（可空）',
    file_id             bigint                             not null comment '文件id',
    file_name           varchar(255)                       null     comment '用户填写的文件名称',
    upload_user_id      bigint                             null     comment '上传人id',
    file_category       varchar(64)                        null     comment '文件分类：图纸/合同/成果文件/计算底稿/其他',
    description         varchar(512)                       null     comment '文件说明',
    approval_status     tinyint  default 0                 not null comment '审批状态：0-待提交/1-审批中/2-通过/3-驳回',
    current_record_id   bigint                             null     comment '当前待审记录id（快照）',
    created_time        datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time        datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete           tinyint  default 0                 not null comment '逻辑删除',
    key idx_pf_project (project_id),
    key idx_pf_upload_user (upload_user_id),
    key idx_pf_status (approval_status)
)
    comment '项目文件表';


-- auto-generated definition
create table inspection_form
(
    inspection_form_id          bigint 	not null											comment '验工单id'
        primary key,
    inspection_form_code			varchar(255)					not null comment '验工单编号',
    project_id   varchar(64)                     not   null comment '项目id',
    project_stage_id	bigint			not null					comment '项目阶段id',
    inspection_form_description		varchar(1024)			null comment '验工说明',
    apply_user_id					bigint			not null			comment '申请人id',
    inspection_form_status			tinyint 										comment '验工单状态：0-待审核/1-审核中/2-已驳回/3-已通过/4-草稿',
    file_ids			json						not null				comment '附件id（json数组）',
    created_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete       tinyint  default 0                 not null comment '逻辑删除'
)
    comment '验工单表';


-- auto-generated definition
create table approval_records
(
    approval_record_id    bigint                             not null comment '审批记录id'
        primary key,
    approval_record_type  tinyint                            not null comment '审批类型：0-项目文件上传/1-验工审批/2-产值分配审批/3-工时填写',
    inspection_form_id    bigint                             not null comment '对应业务id',
    approver              bigint                             not null comment '审批人id',
    apply_user_id         bigint                             null     comment '审批流程发起人id',
    approval_description  varchar(1024)                      null     comment '审批说明',
    inspection_form_status tinyint                           null     comment '审批状态：0-待审核/1-已通过/2-已拒绝',
    approval_level        tinyint        default 1           not null comment '审批层级（1/2/3...）',
    next_approver_id      bigint                             null     comment '下一级审批人id',
    parent_record_id      bigint                             null     comment '上一步审批记录id（形成审批链）',
    biz_type_ext          varchar(32)                        null     comment '业务子类型：file/inspection/bid/acceptance/output',
    created_time          datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time          datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete             tinyint  default 0                 not null comment '逻辑删除',
    pending_business_key  varchar(128) generated always as (
        case
            when inspection_form_status = 0 and is_delete = 0
                then concat(coalesce(biz_type_ext, concat('code:', approval_record_type)), ':', inspection_form_id)
            else null
        end
    ) stored comment '待审批业务唯一键',
    unique key uk_ar_pending_business (pending_business_key),
    key idx_ar_biz (biz_type_ext),
    key idx_ar_apply_user (apply_user_id),
    key idx_ar_approver_status (approver, inspection_form_status),
    key idx_ar_apply_status_time (apply_user_id, inspection_form_status, updated_time),
    key idx_ar_next (next_approver_id),
    key idx_ar_parent (parent_record_id)
)
    comment '审批记录表';


-- ==================== 用户权限体系 ====================

-- auto-generated definition
create table sys_user
(
    user_id                bigint                             not null comment '用户id'
        primary key,
    username               varchar(64)                        not null comment '登录用户名',
    password               varchar(255)                       not null comment '加密密码（BCrypt）',
    employee_no            varchar(32)                        null     comment '员工编号（对应花名册"编号"）',
    real_name              varchar(64)                        null     comment '真实姓名',
    gender                 tinyint                            null     comment '性别：0-男/1-女/2-其他',
    ethnicity              varchar(32)                        null     comment '民族',
    birth_date             date                               null     comment '出生日期',
    id_card                varchar(32)                        null     comment '身份证号',
    email                  varchar(128)                       null     comment '邮箱',
    phone                  varchar(32)                        null     comment '手机号',
    avatar                 varchar(512)                       null     comment '头像URL',
    education              varchar(32)                        null     comment '学历',
    school                 varchar(128)                       null     comment '毕业院校',
    major                  varchar(128)                       null     comment '专业',
    position               varchar(128)                       null     comment '职务',
    professional_title     varchar(128)                       null     comment '职称',
    certificates           varchar(512)                       null     comment '证书',
    entry_date             date                               null     comment '入职时间',
    contract_end_date      date                               null     comment '合同期限（到期日期）',
    social_insurance_date  date                               null     comment '入社保时间',
    employment_status      tinyint  default 1                 not null comment '在职状态：0-离职/1-在职',
    resign_date            date                               null     comment '离职时间',
    domicile               varchar(255)                       null     comment '户籍所在地',
    address                varchar(255)                       null     comment '居住地',
    remark                 varchar(512)                       null     comment '备注',
    status                 tinyint  default 1                 not null comment '账号状态：0-禁用/1-启用（能否登录）',
    last_login_ip          varchar(64)                        null     comment '最后登录ip',
    last_login_time        datetime                           null     comment '最后登录时间',
    created_time           datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time           datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete              tinyint  default 0                 not null comment '逻辑删除',
    constraint uk_sys_user_username unique (username),
    key idx_sys_user_employee_no (employee_no),
    key idx_sys_user_employment_status (employment_status)
)
    comment '系统用户表';


-- auto-generated definition
create table sys_role
(
    role_id       bigint                             not null comment '角色id'
        primary key,
    role_name     varchar(64)                        not null comment '角色名称',
    role_code     varchar(64)                        not null comment '角色唯一编码',
    role_desc     varchar(255)                       null     comment '角色描述',
    status        tinyint  default 1                 not null comment '状态：0-禁用/1-启用',
    created_time  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time  datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete     tinyint  default 0                 not null comment '逻辑删除',
    constraint uk_sys_role_code unique (role_code)
)
    comment '系统角色表';


-- auto-generated definition
create table sys_permission
(
    permission_id    bigint                             not null comment '权限id'
        primary key,
    permission_name  varchar(64)                        not null comment '权限名称',
    permission_code  varchar(128)                       not null comment '权限唯一编码',
    permission_type  tinyint                            not null comment '类型：1-菜单/2-按钮/3-接口',
    parent_id        bigint   default 0                 not null comment '父级id',
    path             varchar(255)                       null     comment '前端路由或接口路径',
    created_time     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete        tinyint  default 0                 not null comment '逻辑删除',
    constraint uk_sys_permission_code unique (permission_code)
)
    comment '系统权限表';


-- auto-generated definition
create table sys_user_role
(
    id            bigint                             not null comment '主键id'
        primary key,
    user_id       bigint                             not null comment '用户id',
    role_id       bigint                             not null comment '角色id',
    created_time  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time  datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete     tinyint  default 0                 not null comment '逻辑删除',
    key idx_sys_user_role_user (user_id),
    key idx_sys_user_role_role (role_id)
)
    comment '用户角色关联表';


-- auto-generated definition
create table sys_role_permission
(
    id             bigint                             not null comment '主键id'
        primary key,
    role_id        bigint                             not null comment '角色id',
    permission_id  bigint                             not null comment '权限id',
    created_time   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete      tinyint  default 0                 not null comment '逻辑删除',
    key idx_sys_role_permission_role (role_id),
    key idx_sys_role_permission_permission (permission_id)
)
    comment '角色权限关联表';


-- ==================== 产值分配模块 ====================

-- auto-generated definition
create table output_value
(
    output_value_id    bigint                             not null comment '产值分配单id'
        primary key,
    project_id         bigint                             not null comment '项目id',
    project_stage_id   bigint                             not null comment '项目阶段id',
    total_amount       decimal(20, 2)                     not null comment '产值总额（元）',
    status             tinyint  default 0                 not null comment '状态：0-待确认/1-待审核/2-已审批/3-已发放',
    submit_user_id     bigint                             null     comment '提交人id',
    confirm_user_id    bigint                             null     comment '确认人id',
    approve_user_id    bigint                             null     comment '审批人id',
    pay_user_id        bigint                             null     comment '发放人id',
    current_handler_id bigint                             null     comment '当前办理人id',
    submit_time        datetime                           null     comment '提交时间',
    approved_time      datetime                           null     comment '审批时间',
    paid_time          datetime                           null     comment '发放时间',
    quarter            varchar(16)                        null     comment '所属季度，格式 YYYY-Qn',
    company_reserve    decimal(20, 2) default 0.00        not null comment '公司账（v0.4：60% 主体 + 降档差额 + 离职兜底）',
    leader_extra       decimal(20, 2) default 0.00        not null comment '（v0.4 起始终为 0；保留字段防迁移破坏）',
    other_amount       decimal(20, 2) default 0.00        not null comment '离职兜底独立记账（实际钱进 company_reserve）',
    subsidy_amount     decimal(20, 2) default 0.00        not null comment '公司补贴（只记录，不计入产值）',
    stage_cumulative_amount    decimal(20, 2) null comment '当前阶段累计应得（含基本+效益）',
    previous_cumulative_amount decimal(20, 2) null comment '上一次产值分配单的累计（用于计算本期产值）',
    base_amount_part           decimal(20, 2) null comment '本期基本部分',
    benefit_amount_part        decimal(20, 2) null comment '本期效益部分',
    benefit_snapshot           decimal(20, 2) null comment '快照：本单创建时合同的预计效益值',
    current_stage_amount       decimal(20, 2) null comment '当前阶段纯产值，不含历史补差',
    adjustment_amount          decimal(20, 2) null comment '历史阶段补差合计，可正可负',
    base_amount_snapshot       decimal(20, 2) null comment '创建时合同基本金额快照',
    benefit_amount_snapshot    decimal(20, 2) null comment '创建时合同效益金额快照',
    calculation_version        varchar(64) null comment '产值计算版本',
    stage_completion_ratio     decimal(5, 2) default 100.00 not null comment '创建时阶段累计完成比例',
    stage_incremental_ratio    decimal(5, 2) default 100.00 not null comment '本次增量完成比例',
    coefficient                decimal(5, 2) default 1.00 not null comment '分配时使用的阶段系数',
    allocation_version         varchar(32) null comment '人员分配计算版本',
    allocation_rule_version_id bigint null comment '人员分配规则版本id',
    employee_pool_amount       decimal(20, 2) null comment '名义员工池金额',
    company_base_amount        decimal(20, 2) null comment '公司基础留存金额',
    work_transfer_amount       decimal(20, 2) null comment '工作类型超限转公司金额',
    project_pool_amount        decimal(20, 2) null comment '项目人员可分配金额',
    created_time       datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete          tinyint  default 0                 not null comment '逻辑删除',
    key idx_output_value_project (project_id),
    key idx_output_value_stage (project_stage_id),
    key idx_output_value_status (status),
    key idx_output_value_quarter (quarter),
    key idx_output_value_current_handler (current_handler_id)
)
    comment '产值分配单表';


-- auto-generated definition
create table output_value_adjustment_detail
(
    adjustment_detail_id        bigint                             not null comment '补差明细id'
        primary key,
    output_value_id             bigint                             not null comment '本次产值分配单id',
    source_output_value_id      bigint                             not null comment '补差来源历史产值分配单id',
    source_project_stage_id     bigint                             not null comment '补差来源历史阶段id',
    source_stage_name           varchar(128)                       null     comment '补差来源历史阶段名称快照',
    source_base_ratio           decimal(10, 4) default 0.0000      not null comment '来源阶段基本比例快照',
    source_benefit_ratio        decimal(10, 4) default 0.0000      not null comment '来源阶段效益比例快照',
    old_base_amount_snapshot    decimal(20, 2)                     null     comment '历史单创建时基本金额快照',
    old_benefit_amount_snapshot decimal(20, 2)                     null     comment '历史单创建时效益金额快照',
    old_stage_amount            decimal(20, 2) default 0.00        not null comment '历史阶段原计算纯阶段金额',
    new_base_amount_snapshot    decimal(20, 2) default 0.00        not null comment '本单创建时基本金额快照',
    new_benefit_amount_snapshot decimal(20, 2) default 0.00        not null comment '本单创建时效益金额快照',
    new_stage_amount            decimal(20, 2) default 0.00        not null comment '按本单金额重算后的历史阶段金额',
    already_adjusted_amount     decimal(20, 2) default 0.00        not null comment '之前已对该历史阶段补差/扣回金额',
    adjustment_amount           decimal(20, 2) default 0.00        not null comment '本次补差/扣回金额',
    created_time                datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time                datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete                   tinyint  default 0                 not null comment '逻辑删除',
    key idx_ov_adjust_output (output_value_id),
    key idx_ov_adjust_source_output (source_output_value_id),
    key idx_ov_adjust_source_stage (source_project_stage_id)
)
    comment '产值分配历史阶段补差明细表';


-- auto-generated definition
create table output_value_distribution
(
    distribution_id   bigint                             not null comment '分配明细id'
        primary key,
    output_value_id   bigint                             not null comment '产值分配单id',
    user_id           bigint                             not null comment '分配用户id',
    work_type         tinyint                            not null comment '工作类型：0-管理工作/1-基础工作/2-智励工作',
    ratio             decimal(10, 2) default 0.00        not null comment '分配比例（%，旧字段，保留以兼容历史数据）',
    alloc_ratio       decimal(10, 4) default 0.0000      not null comment '分配比例（%），新口径',
    completion_ratio  decimal(10, 4) default 0.0000      not null comment '完成比例（%）',
    work_pool_id      bigint                             null comment '工作类型资金池id',
    role_alloc_ratio  decimal(10, 4)                     null comment '工作类型资金池内分配比例（%）',
    planned_amount    decimal(20, 2)                     null comment '人员计划分配金额',
    company_delta     decimal(20, 2)                     null comment '兑现不足或离职转公司金额',
    dist_type         tinyint        default 0           not null comment '类型：0-员工正常/1-员工降档/2-（v0.4 废弃）/3-公司留存（不生成行）/4-离职兜底',
    is_active         tinyint        default 1           not null comment '下单时成员是否在职（0-离职/1-在职）',
    amount            decimal(20, 2) default 0.00        not null comment '分配金额（元）',
    created_time      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete         tinyint  default 0                 not null comment '逻辑删除',
    key idx_dist_output_value (output_value_id),
    key idx_dist_user (user_id),
    key idx_dist_type (dist_type)
)
    comment '产值分配明细表';

create table output_allocation_rule_version
(
    rule_version_id      bigint auto_increment primary key,
    project_type_id      bigint                             not null comment '项目类型id',
    version_no           int                                not null comment '项目类型内版本号',
    employee_pool_rate   decimal(10, 4) default 40.0000     not null comment '名义员工池比例（%）',
    company_base_rate    decimal(10, 4) default 60.0000     not null comment '公司基础留存比例（%）',
    status               tinyint        default 1           not null comment '0-历史版本/1-当前生效',
    created_by           bigint                             null comment '创建人',
    effective_time       datetime       default CURRENT_TIMESTAMP not null,
    created_time         datetime       default CURRENT_TIMESTAMP not null,
    updated_time         datetime       default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    is_delete            tinyint        default 0           not null,
    unique key uk_output_rule_version (project_type_id, version_no),
    key idx_output_rule_active (project_type_id, status, is_delete)
) comment '产值分配规则版本';

create table output_allocation_rule_item
(
    rule_item_id         bigint auto_increment primary key,
    rule_version_id      bigint                             not null,
    stage_name           varchar(255)                       not null,
    stage_order          int                                not null,
    work_type            tinyint                            not null,
    work_weight          decimal(10, 4)                     not null,
    project_cap_rate     decimal(10, 4)                     null,
    created_time         datetime default CURRENT_TIMESTAMP not null,
    updated_time         datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    is_delete            tinyint  default 0                 not null,
    unique key uk_output_rule_item (rule_version_id, stage_name, work_type),
    key idx_output_rule_item_version (rule_version_id, stage_order, work_type)
) comment '产值分配阶段工作权重';

create table output_value_work_pool
(
    work_pool_id         bigint auto_increment primary key,
    output_value_id      bigint                             not null,
    rule_version_id      bigint                             not null,
    rule_version_no      int                                not null,
    work_type            tinyint                            not null,
    stage_work_ratio     decimal(10, 4)                     not null,
    gross_rate           decimal(10, 4)                     not null,
    gross_amount         decimal(20, 2)                     not null,
    project_rate         decimal(10, 4)                     not null,
    project_amount       decimal(20, 2)                     not null,
    company_rate         decimal(10, 4)                     not null,
    company_amount       decimal(20, 2)                     not null,
    created_time         datetime default CURRENT_TIMESTAMP not null,
    updated_time         datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    is_delete            tinyint  default 0                 not null,
    unique key uk_output_work_pool (output_value_id, work_type),
    key idx_output_work_pool_output (output_value_id)
) comment '产值分配工作类型资金池快照';


-- ==================== 工时模块 ====================

-- auto-generated definition
create table timesheet
(
    timesheet_id      bigint                             not null comment '工时记录id'
        primary key,
    user_id           bigint                             not null comment '填报用户id',
    project_id        bigint                             not null comment '项目id',
    project_stage_id  bigint                             null     comment '项目阶段id（可选）',
    work_type         tinyint                            not null comment '工作类型：0-管理工作/1-基础工作/2-智励工作',
    work_date         date                               not null comment '工作日期',
    hours             decimal(5, 2)                      not null comment '工作时长（小时）',
    planned_hours     decimal(5, 2) default 0.00         not null comment '额定工时（小时）',
    description       varchar(1024)                      null     comment '工作内容描述',
    status            tinyint  default 1                 not null comment '状态：0-草稿/1-已提交',
    approval_status   tinyint  default 0                 not null comment '审批状态：0-未提交/1-审批中/2-通过/3-驳回',
    approver_id       bigint                             null     comment '当前审批人id',
    created_time      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete         tinyint  default 0                 not null comment '逻辑删除',
    key idx_timesheet_user_date (user_id, work_date),
    key idx_timesheet_project (project_id),
    key idx_timesheet_approval (approval_status)
)
    comment '工时记录表';


-- ==================== 回款管理模块 ====================

-- auto-generated definition
create table collection_record
(
    collection_record_id  bigint                             not null comment '回款记录id'
        primary key,
    project_id            bigint                             not null comment '项目id',
    project_stage_id      bigint                             null     comment '项目阶段id（可选）',
    amount                decimal(20, 2)                     not null comment '回款金额（元）',
    collect_date          date                               not null comment '实际回款日期',
    voucher_file_id       bigint                             null     comment '凭证文件id',
    remark                varchar(1024)                      null     comment '备注',
    record_user_id        bigint                             null     comment '录入人id',
    created_time          datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time          datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete             tinyint  default 0                 not null comment '逻辑删除',
    key idx_collection_project (project_id),
    key idx_collection_stage (project_stage_id),
    key idx_collection_date (collect_date)
)
    comment '回款记录表';


-- ==================== 文件模块 ====================

-- auto-generated definition
create table files
(
    file_id           bigint                             not null comment '文件id'
        primary key,
    upload_user_id    bigint                             null     comment '上传用户id',
    file_type         varchar(32)                        null     comment '文件类型：pdf/word/image/text...',
    file_name         varchar(255)                       null     comment '存储文件名',
    original_name     varchar(255)                       null     comment '原始文件名（上传时的名称）',
    display_name      varchar(255)                       null     comment '显示名称（可编辑）',
    file_extension    varchar(32)                        null     comment '文件扩展名',
    storage_type      varchar(32)  default 'local'       null     comment '存储类型：OSS/MinIO/S3/Local/FTP等',
    file_url          varchar(1024)                      null     comment '可访问文件路径',
    file_path         varchar(1024)                      null     comment '文件存储路径（不含域名）',
    thumbnail_url     varchar(1024)                      null     comment '缩略图URL',
    file_size         bigint                             null     comment '文件大小（字节）',
    file_md5          varchar(64)                        null     comment '文件MD5（用于去重）',
    file_hash         varchar(128)                       null     comment '文件哈希（SHA-256）',
    mime_type         varchar(128)                       null     comment 'MIME 类型',
    access_count      int      default 0                 null     comment '访问次数',
    download_count    int      default 0                 null     comment '下载次数',
    preview_count     int      default 0                 null     comment '预览次数',
    share_count       int      default 0                 null     comment '分享次数',
    status            tinyint  default 1                 null     comment '状态：0-上传中/1-成功/2-失败',
    permission_level  tinyint  default 1                 null     comment '权限级别：0-公开/1-私有/2-受保护/3-机密',
    upload_ip         varchar(64)                        null     comment '上传IP地址（支持IPv6）',
    created_time      datetime default CURRENT_TIMESTAMP null     comment '创建时间',
    updated_time      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_deleted        tinyint  default 0                 not null comment '是否删除（0-未删除，1-已删除）',
    key idx_files_md5 (file_md5),
    key idx_files_user (upload_user_id)
)
    comment '文件表';


-- ==================== 公告模块 ====================

-- auto-generated definition
create table announcement
(
    announcement_id   bigint                             not null comment '公告id'
        primary key,
    title             varchar(200)                       not null comment '公告标题',
    content           text                               not null comment '公告内容（支持纯文本或简单 HTML）',
    priority          tinyint  default 0                 not null comment '优先级：0-普通/1-重要/2-紧急',
    status            tinyint  default 0                 not null comment '状态：0-草稿/1-已发布/2-已下线',
    publish_time      datetime                           null     comment '发布时间',
    expire_time       datetime                           null     comment '过期时间（可选，到期自动视为下线）',
    publish_user_id   bigint                             null     comment '发布人id',
    created_time      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete         tinyint  default 0                 not null comment '逻辑删除',
    key idx_announcement_status (status),
    key idx_announcement_publish_time (publish_time),
    key idx_announcement_priority (priority)
)
    comment '公告表';


-- ==================== 投标管理 ====================

-- auto-generated definition
create table bid
(
    bid_id             bigint                             not null comment '投标id'
        primary key,
    bid_name           varchar(200)                       not null comment '投标项目名称',
    bid_code           varchar(100)                       null     comment '投标编号',
    owner_user_id      bigint                             not null comment '负责人id',
    tender_amount      decimal(20, 2)                     null     comment '标的金额',
    bid_status         tinyint  default 0                 not null comment '业务状态：0-筹备/1-已投递/2-中标/3-未中标/4-终止',
    bid_date           date                               null     comment '投标日期',
    result_date        date                               null     comment '结果日期',
    client_name        varchar(200)                       null     comment '业主 / 甲方',
    description        text                               null     comment '说明',
    approval_status    tinyint  default 0                 not null comment '审批状态：0-草稿/1-审核中/2-通过/3-驳回',
    current_record_id  bigint                             null     comment '当前待审记录id（快照）',
    created_time       datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete          tinyint  default 0                 not null comment '逻辑删除',
    key idx_bid_status (bid_status),
    key idx_bid_owner (owner_user_id),
    key idx_bid_approval (approval_status)
)
    comment '投标表';


-- auto-generated definition
create table bid_file
(
    bid_file_id    bigint                             not null comment '投标附件id'
        primary key,
    bid_id         bigint                             not null comment '投标id',
    file_id        bigint                             not null comment '文件id',
    file_category  varchar(64)                        null     comment '分类：招标文件/投标文件/中标通知/其他',
    created_time   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete      tinyint  default 0                 not null comment '逻辑删除',
    key idx_bf_bid (bid_id)
)
    comment '投标附件表';


-- ==================== 验收模块（成果 / 过程 / 阶段性） ====================

-- auto-generated definition
create table project_acceptance
(
    acceptance_id      bigint                             not null comment '验收单id'
        primary key,
    project_id         bigint                             not null comment '项目id',
    project_stage_id   bigint                             null     comment '项目阶段id（成果/过程验收可空）',
    acceptance_type    tinyint                            not null comment '类型：0-过程/1-成果/2-阶段性验收',
    title              varchar(200)                       not null comment '验收标题',
    content            text                               null     comment '验收内容说明',
    file_ids           varchar(1024)                      null     comment '附件id列表（json）',
    apply_user_id      bigint                             not null comment '申请人id',
    status             tinyint  default 0                 not null comment '0-待审批/1-审批中/2-通过/3-驳回',
    current_record_id  bigint                             null     comment '当前待审记录id（快照）',
    created_time       datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete          tinyint  default 0                 not null comment '逻辑删除',
    key idx_acc_project (project_id),
    key idx_acc_type (acceptance_type),
    key idx_acc_status (status),
    key idx_acc_apply (apply_user_id)
)
    comment '成果/过程/阶段性验收表';


-- ==================== 绩效还原模块 ====================

-- auto-generated definition
create table performance_restore
(
    restore_id     bigint                             not null comment '还原id'
        primary key,
    quarter        varchar(16)                        not null comment '季度，如 2026-Q1',
    user_id        bigint                             not null comment '用户id',
    project_id     bigint                             null     comment '项目id（可空，整体还原时为空）',
    restore_amount decimal(20, 2)                     not null comment '还原金额',
    status         tinyint  default 0                 not null comment '0-待还原/1-已还原',
    restored_time  datetime                           null     comment '实际还原时间',
    operator_id    bigint                             null     comment '操作人（财务）',
    remark         varchar(512)                       null     comment '备注',
    created_time   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete      tinyint  default 0                 not null comment '逻辑删除',
    key idx_pr_quarter (quarter),
    key idx_pr_user (user_id),
    key idx_pr_status (status)
)
    comment '绩效还原表';
