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


-- ==================== 用户权限体系 ====================

-- auto-generated definition
create table sys_user
(
    user_id          bigint                             not null comment '用户id'
        primary key,
    username         varchar(64)                        not null comment '登录用户名',
    password         varchar(255)                       not null comment '加密密码（BCrypt）',
    real_name        varchar(64)                        null     comment '真实姓名',
    email            varchar(128)                       null     comment '邮箱',
    phone            varchar(32)                        null     comment '手机号',
    status           tinyint  default 1                 not null comment '状态：0-禁用/1-启用',
    last_login_ip    varchar(64)                        null     comment '最后登录ip',
    last_login_time  datetime                           null     comment '最后登录时间',
    created_time     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete        tinyint  default 0                 not null comment '逻辑删除',
    constraint uk_sys_user_username unique (username)
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
    submit_time        datetime                           null     comment '提交时间',
    approved_time      datetime                           null     comment '审批时间',
    paid_time          datetime                           null     comment '发放时间',
    created_time       datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete          tinyint  default 0                 not null comment '逻辑删除',
    key idx_output_value_project (project_id),
    key idx_output_value_stage (project_stage_id),
    key idx_output_value_status (status)
)
    comment '产值分配单表';


-- auto-generated definition
create table output_value_distribution
(
    distribution_id   bigint                             not null comment '分配明细id'
        primary key,
    output_value_id   bigint                             not null comment '产值分配单id',
    user_id           bigint                             not null comment '分配用户id',
    work_type         tinyint                            not null comment '工作类型：0-管理工作/1-基础工作/2-智励工作',
    ratio             decimal(10, 2) default 0.00        not null comment '分配比例（%）',
    amount            decimal(20, 2) default 0.00        not null comment '分配金额（元）',
    created_time      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete         tinyint  default 0                 not null comment '逻辑删除',
    key idx_dist_output_value (output_value_id),
    key idx_dist_user (user_id)
)
    comment '产值分配明细表';


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
    description       varchar(1024)                      null     comment '工作内容描述',
    status            tinyint  default 1                 not null comment '状态：0-草稿/1-已提交',
    created_time      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_time      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete         tinyint  default 0                 not null comment '逻辑删除',
    key idx_timesheet_user_date (user_id, work_date),
    key idx_timesheet_project (project_id)
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