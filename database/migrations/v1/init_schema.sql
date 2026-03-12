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
    contract_amount int																null	comment '合同金额',
    contract_file	bigint								not null				comment '合同主文件id',
    contract_other_files	json												null	comment '合同其他附件(id json 数组)',
      	base_amount			int																	null comment '基本收益金额',
    benefit_rules		varchar(255)										null			comment '效益收益规则',
    signing_date		datetime	default CURRENT_TIMESTAMP not null comment '项目签订日期',
    pre_start_date datetime	default CURRENT_TIMESTAMP	not null comment '项目预计开始日期',
    pre_end_date datetime	default CURRENT_TIMESTAMP	not null comment '项目预计结束日期',
    created_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete       tinyint  default 0                 not null comment '逻辑删除'
)
    comment '合同表';




-- auto-generated definition
create table project_stage
(
    project_stage_id   bigint                             not null comment '项目阶段id'
        primary key,
    project_id bigint                      not null comment '项目id',
    stage_name	  varchar(255)						not null		comment '阶段名称',
    stage_status	tinyint			default 0		not null	comment '阶段状态：0-未开始/1-进行中/2-待验收/3-已验收/4-已驳回/5-待分配/6-已完成',
    stage_output	decimal(10, 2) default 0.00 not null	comment '阶段产值比例',
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
    project_file_id          bigint 	not null											comment '项目文件id'
        primary key,
    project_id   varchar(64)                        null comment '项目id',
    project_stage_id	bigint			not null					comment '项目阶段id',
    file_id			bigint						not null				comment '文件id',
    created_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete       tinyint  default 0                 not null comment '逻辑删除'
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
    approval_record_id          bigint 	not null											comment '审批记录id'
        primary key,
    approval_record_type		tinyint 							not null	comment '审批类型：0-项目文件上传/1-验工审批/2-产值分配审批/3-工时填写',
        inspection_form_id			bigint					not null comment '对应业务id',
    approver					bigint			not null			comment '审批人id',
    approval_description				varchar(1024)			null			comment '审批说明',
    inspection_form_status			tinyint 										comment '审批状态：0-待审核/1-已通过/2-已拒绝',
    created_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete       tinyint  default 0                 not null comment '逻辑删除'
)
    comment '审批记录表';