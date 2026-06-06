# 架构说明 · Edifice Nexus

> 这份文档讲「系统是怎么搭起来的、各块怎么协作、核心业务怎么流转」。
> 想了解项目是干嘛的、怎么跑起来，看根目录的 [README.md](../README.md)。
> 想了解业务规则细节（产值怎么算），看 [`docs/design/trd/`](design/trd/)。

图都是 Mermaid，GitHub / 大多数 Markdown 预览器能直接渲染。

---

## 1. 系统全景

一句话：**一个 Next.js 前端 + 一个 Spring Boot 后端 + MySQL/Redis**，外加一个**共用用户表的独立 OA 系统**。

```mermaid
graph TB
    subgraph Client["浏览器"]
        UI["edifice-vision<br/>Next.js 16 · React 19"]
    end

    subgraph Backend["edifice-core · Spring Boot 3"]
        API["REST API 层<br/>19 个 Controller"]
        SVC["业务服务层<br/>Service / ServiceImpl"]
        SEC["安全层<br/>Spring Security + JWT 双 Token"]
        SYNC["OA 同步任务<br/>@Scheduled + Outbox"]
    end

    subgraph Data["数据与中间件"]
        DB[("MySQL 8<br/>edifice_db")]
        REDIS[("Redis<br/>会话 / 缓存")]
    end

    subgraph External["外部系统"]
        OA["OA 系统<br/>RuoYi-Vue-Flowable-Plus<br/>（独立部署）"]
    end

    UI -->|"HTTP · BaseResponse<T>"| API
    API --> SEC
    API --> SVC
    SVC --> DB
    SVC --> REDIS
    SEC --> REDIS
    SYNC -->|"用户/组织同步"| OA
    SYNC --> DB
    UI -.->|"SSO 单点登录"| OA

    style UI fill:#e1f0ff,stroke:#3b82f6
    style API fill:#fff4e1,stroke:#f59e0b
    style OA fill:#f0e1ff,stroke:#a855f7
    style DB fill:#e1ffe8,stroke:#22c55e
    style REDIS fill:#ffe1e1,stroke:#ef4444
```

**关键点**

- 前后端**彻底分离**，前端只通过 HTTP 调后端，统一响应体是 `BaseResponse<T>`。
- OA 是**另一个独立进程/技术栈**（Vue + Flowable），不和本系统同进程；两边靠**共用用户表 + 后台同步任务**打通，用户在本系统登录后可 SSO 进 OA。
- Redis 同时承担**会话/Token** 和**业务缓存**两个角色。

---

## 2. 后端分层（edifice-core）

经典的 Controller → Service → Mapper 三层，外加横切的安全、AOP、统一异常。

```mermaid
graph LR
    subgraph Web["接入层"]
        C["Controller<br/>参数用 DTO 接<br/>返回用 VO 包"]
        F["JwtAuthenticationTokenFilter<br/>每请求校验 Token"]
    end

    subgraph Biz["业务层"]
        S["Service / ServiceImpl<br/>核心业务逻辑 + 事务"]
    end

    subgraph Persist["持久层"]
        M["Mapper（MyBatis-Plus）"]
        X["Mapper XML<br/>复杂查询手写 SQL"]
    end

    subgraph Cross["横切关注点"]
        AOP["aop · 日志/权限切面"]
        EX["exception · 全局异常处理"]
        CFG["config · CORS/Jackson/Redis/Security"]
    end

    F --> C
    C --> S
    S --> M
    M --> X
    AOP -.-> C
    EX -.-> C
    CFG -.-> F

    style C fill:#fff4e1,stroke:#f59e0b
    style S fill:#e1f0ff,stroke:#3b82f6
    style M fill:#e1ffe8,stroke:#22c55e
```

**目录对照**（`apps/edifice-core/src/main/java/com/qsy/edifice/`）

| 包 | 职责 |
| --- | --- |
| `controller/` | REST 接口，入参封装为 `domain/dto`，出参封装为 `domain/vo` |
| `service/` + `service/impl/` | 业务逻辑与事务边界 |
| `mapper/` + `resources/mapper/*.xml` | 数据访问，简单 CRUD 用 MyBatis-Plus，复杂查询写 XML |
| `domain/entity` `dto` `vo` `excel` | 数据库实体 / 入参 / 出参 / Excel 模型 |
| `security/` | Security 配置、JWT 过滤器、登录登出 handler、`OaAwarePasswordEncoder` |
| `config/` | CORS、Jackson、Redis、MyBatis-Plus、WebClient、Web MVC 等配置 |
| `aop/` `exception/` | 切面（日志/权限）、全局异常 |
| `common/` `utils/` `enums/` | 统一响应码（`Code`）、`ResultUtils`、`RedisCache`、各类枚举 |

> 约定（来自 v0.1 TRD）：**接口入参一律封装为 DTO，出参一律封装为 VO**，不要直接把 Entity 透传给前端。

---

## 3. 前端分层（edifice-vision）

```mermaid
graph TB
    subgraph App["app/ · App Router"]
        MW["middleware.ts<br/>无 token → 跳 /login"]
        PAGES["(dashboard)/*<br/>19 个业务页面"]
        LOGIN["login/"]
    end

    subgraph Reuse["复用层"]
        COMP["components/<br/>按模块拆 + ui/ 基础组件"]
        SVC["services/*.ts<br/>每模块一个，调后端"]
        REQ["lib/request.ts<br/>统一请求封装 + 拦截"]
        STORE["store/auth-context<br/>登录态"]
        PERM["lib/permissions.ts<br/>前端权限判断"]
    end

    MW --> PAGES
    PAGES --> COMP
    PAGES --> SVC
    SVC --> REQ
    PAGES --> STORE
    PAGES --> PERM
    REQ -->|"NEXT_PUBLIC_API_BASE_URL"| Backend["后端 API"]

    style MW fill:#ffe1e1,stroke:#ef4444
    style REQ fill:#fff4e1,stroke:#f59e0b
    style SVC fill:#e1f0ff,stroke:#3b82f6
```

**关键约定**

- 页面**只管编排**，真正的接口调用沉在 `services/*.ts`（一个业务模块一个文件，和后端 Controller 对齐）。
- 所有请求走 `lib/request.ts` 统一出口：拼 baseURL、带 Token、统一处理 `BaseResponse` 和错误码。
- 登录态在 cookie（给 `middleware.ts` 用）+ `store/auth-context`（给页面用）里各存一份。

---

## 4. 鉴权流程（双 Token）

后端用 **access token + refresh token** 两个令牌。access 短命，过期了不用重新登录，用 refresh 悄悄换新的。

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant FE as 前端 (request.ts)
    participant FT as JwtAuthenticationTokenFilter
    participant AU as AuthController
    participant R as Redis

    U->>FE: 登录（账号密码）
    FE->>AU: POST /auth/login
    AU->>R: 存 refresh token
    AU-->>FE: 返回 access + refresh token
    FE->>FE: 存 cookie / localStorage

    Note over FE,FT: 之后每个请求都带 access token
    FE->>FT: 携带 Authorization 请求业务接口
    FT->>FT: 校验 access token

    alt access 有效
        FT-->>FE: 正常返回数据
    else access 过期 (code=10003)
        FT-->>FE: 提示过期
        FE->>AU: POST /auth/refresh（带 refresh token）
        AU->>R: 校验 refresh token
        alt refresh 有效
            AU-->>FE: 下发新 access token
            FE->>FT: 用新 token 重试原请求
        else refresh 也过期 (code=10004)
            AU-->>FE: 需重新登录 → 跳 /login
        end
    end
```

状态码定义在 `common/Code.java`：`10003` access 过期、`10004` refresh 过期、`40300` 权限不足、`42900` 限流。

---

## 5. 核心业务流：从立项到产值分配

这是整个系统的主线。一个项目从创建到把产值分到人头，大致这么走：

```mermaid
sequenceDiagram
    autonumber
    participant PM as 项目管理
    participant CT as 合同
    participant ST as 阶段
    participant IN as 验工单
    participant OV as 产值核算
    participant CO as 回款

    PM->>PM: 1. 创建项目（校验参数）
    PM->>CT: 2. 关联合同（基本收费 / 基本+效益）
    PM->>ST: 3. 拆分项目阶段（按模板）
    PM->>PM: 4. 分配角色与项目成员

    Note over ST,IN: 项目推进，阶段逐个完成
    ST->>IN: 5. 阶段完成 → 提交验工单
    IN->>IN: 6. 验工单审批（通过/驳回）

    IN->>OV: 7. 审批通过 → 触发产值核算
    OV->>OV: 8. 按已完成阶段占比算应收产值
    OV->>OV: 9. 拆分：员工池 40% + 公司池
    OV->>OV: 10. 确认 → 审批 → 发放

    Note over CT,CO: 与产值并行的回款线
    CT->>CO: 11. 按合同生成应收
    CO->>CO: 12. 登记实际回款，季度绩效还原
```

**算账的核心规则**（详见 [v0.4 文档](design/trd/)）

- **基本收费**（`contract_type=0`）：`应收 = base_amount × Σ 已完成阶段产值占比`
- **基本+效益**（`contract_type=1`）：基本部分固定 + 效益部分（按审减额/节约额 × 约定比例）**分阶段累计计入**
- **产值拆分**：员工池占阶段产值 40%（每人约 8.06%，含奖金；降档则 4%，差额归公司），其余进公司池
- ⚠️ 这块逻辑客户口径调整过多次，**改之前一定先读 v0.4**，代码里的实现是规则的"快照"，规则文档才是源头

> 一个项目可能**多个阶段同时进行**（v0.1 TRD 明确强调），算产值时要按"已完成阶段"逐个累加，不能假设阶段是严格串行的。

---

## 6. OA 用户同步

本系统是用户的"权威源"，OA 复用同一批用户。同步用**发件箱（Outbox）模式**保证可靠：用户变更先落一张 outbox 表，再由定时任务推给 OA，失败可重试。

```mermaid
graph LR
    subgraph Core["edifice-core"]
        U["用户增删改"]
        OUT[("oa_user_sync_outbox<br/>待同步记录")]
        JOB["@Scheduled 定时任务<br/>OaUserSyncServiceImpl"]
    end

    OA["OA 系统<br/>office_db"]

    U -->|"写业务 + 写 outbox<br/>（同事务）"| OUT
    JOB -->|"批量捞未同步记录"| OUT
    JOB -->|"调 OA 同步接口"| OA
    JOB -->|"成功标记已同步 / 失败计数重试"| OUT

    style OUT fill:#fff4e1,stroke:#f59e0b
    style JOB fill:#e1f0ff,stroke:#3b82f6
    style OA fill:#f0e1ff,stroke:#a855f7
```

**为什么用 Outbox 而不是直接调**：用户写库和同步 OA 如果分两步直接做，OA 挂了就会数据不一致。Outbox 把"要同步"这件事和业务写在同一个事务里落库，哪怕 OA 暂时不可用，定时任务后面也能补上——最终一致。

相关配置在 `application.yaml` 的 `oa.sync.*`（批大小、重试次数、重试间隔、全量同步周期都可调），代码在 `service/impl/OaUserSyncServiceImpl.java`。

---

## 7. 数据库迁移

```
database/migrations/
├── v1/init_schema.sql          初始建表
└── v2/                         增量迁移（按需顺序执行）
    ├── edifice_menu_permissions.sql        菜单权限
    ├── oa_application.sql                   OA 申请
    ├── oa_application_phase2.sql            OA 申请二期
    ├── oa_org_sync.sql                      组织同步
    ├── oa_user_sync_outbox.sql             用户同步发件箱
    └── output_value_workflow_assignees.sql 产值流程审批人
```

- 没有引入自动迁移框架（Flyway/Liquibase），**靠手工按顺序执行 SQL**。
- 改库表时：在对应版本目录下**新增**一个 SQL 文件，不要改历史文件，方便别人增量同步。

---

## 附：一眼看懂的对照表

| 你想找… | 去哪 |
| --- | --- |
| 某个接口怎么实现的 | `controller/XxxController.java` → `service/impl/XxxServiceImpl.java` |
| 复杂 SQL | `resources/mapper/XxxMapper.xml` |
| 前端某页调了哪些接口 | `app/(dashboard)/xxx/page.tsx` → `services/xxx.ts` |
| 统一返回格式 / 错误码 | `domain/common/BaseResponse.java` / `common/Code.java` |
| 登录鉴权逻辑 | `security/filter/JwtAuthenticationTokenFilter.java` + `config/SecurityConfig.java` |
| 业务规则（产值/回款） | `docs/design/trd/[v0.4]产值核算与回款逻辑闭环.md` |
| OA 集成方案 | `docs/design/trd/[v0.3]OA集成方案-RuoYi-Flowable.md` |
