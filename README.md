# Edifice Nexus · 项目产值分配核算管理系统

> 把「项目 → 合同 → 阶段 → 验工 → 产值核算 → 分配 → 回款 → 绩效」这条业务链，做成一个能闭环跑通的内部管理系统，并集成 OA 审批。

面向工程 / 咨询类企业内部使用。一句话说清它干嘛：**项目做到哪个阶段、该确认多少产值、钱该怎么分给员工和公司、客户回款收了多少、季度绩效怎么算——全都在这一个系统里说清楚。**

- GitHub：https://github.com/yannqing/edifice-nexus
- 详细设计文档：[`docs/design/trd/`](docs/design/trd/)（v0.1 ~ v0.5，按时间演进）
- 架构详解：[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)

---

## 这个仓库长什么样（Monorepo）

用 **pnpm workspace + Turborepo** 统一管理，前端是 TS workspace 的一员，后端 Java 工程挂在同一个 monorepo 下独立构建。

```
edifice-nexus/
├── apps/
│   ├── edifice-core/      后端 · Spring Boot 3 + Java 17       （~22.5k 行 Java）
│   └── edifice-vision/    前端 · Next.js 16 + React 19 + TS    （~21.8k 行 TS）
├── packages/
│   ├── shared-types/      前端共享类型（预留，目前较薄）
│   └── shared-utils/      前端共享工具（预留，目前较薄）
├── database/
│   ├── migrations/        建表 + 增量迁移 SQL（v1 init / v2 增量）
│   └── seeds/             种子数据
├── docs/
│   ├── design/trd/        技术开发文档（核心业务规则都在这）
│   └── design/prototype/  HTML 原型页
└── deploy/                部署相关（预留）
```

> 命名约定：`core` = 后端核心服务，`vision` = 前端可视化界面。两个词拼起来就是 "nexus"（枢纽）。

---

## 技术栈

### 后端 `edifice-core`
| 用途 | 选型 |
| --- | --- |
| 语言 / 框架 | Java 17 · Spring Boot 3 |
| 持久层 | MyBatis-Plus + MySQL，Druid 连接池 |
| 鉴权 | Spring Security + JWT（auth0 java-jwt），**双 Token**（access + refresh） |
| 缓存 / 会话 | Redis（Lettuce） |
| AI 能力 | Spring AI 1.0.0-M6（见 `feat/ai` 分支） |
| 工具 | EasyExcel（导入导出）、Knife4j（OpenAPI 文档）、Fastjson、dotenv-java |
| 配置管理 | 全量环境变量化，密钥走 Infisical |

### 前端 `edifice-vision`
| 用途 | 选型 |
| --- | --- |
| 框架 | Next.js 16（App Router）+ React 19 |
| 样式 / 组件 | Tailwind CSS v4 + Radix UI + lucide-react |
| 提示 | sonner（toast） |
| 鉴权 | `middleware.ts` 校验 cookie 里的 access_token，未登录跳 `/login` |
| 数据请求 | `src/lib/request.ts` 统一封装，按模块拆 `src/services/*.ts` |

---

## 业务模块一览

后端 19 个 Controller ↔ 前端 19 个页面，基本一一对应。

**核心业务链**
项目管理 → 合同 / 效益修正 → 项目阶段 → 验工单审批 → 产值核算与分配 → 回款应收 → 季度绩效还原

**支撑模块**
认证 · 用户 / 角色 / 权限 / 部门 / 岗位 · 工时 · 投标 · 公告 · 文件 · 报表统计 · 审批流

**OA 集成**
基于 `RuoYi-Vue-Flowable-Plus` 的独立 OA 系统，**共用用户表**。通过 `OaSyncController` + outbox 表做用户/组织同步，带定时任务和失败重试（见 `OaUserSyncServiceImpl`）。

> 产值怎么算、钱怎么分，规则全在 [`docs/design/trd/[v0.4]产值核算与回款逻辑闭环.md`](docs/design/trd/)。改这块逻辑前务必先读它。

---

## 本地跑起来

### 你需要先装

- **Node.js ≥ 20** 和 **pnpm 10.15.0**（`corepack enable` 即可）
- **JDK 17**（后端用 Maven Wrapper，不用单独装 Maven）
- **MySQL 8** 和 **Redis**（本地或 Docker 起一个）

### 1. 装依赖

```bash
pnpm install
```

### 2. 配后端环境变量

后端读 `apps/edifice-core/.env`（已被 gitignore，不会进库）。建一个，填上你自己的：

```dotenv
# 数据库
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=edifice_db
MYSQL_USERNAME=root
MYSQL_PASSWORD=你的密码

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_DATABASE=1
REDIS_PASSWORD=

# JWT
JWT_SECRET=换成你自己的长随机串
JWT_EXPIRE_TIMES=
```

> 团队成员可以直接用 `pnpm env:pull`（根 `package.json` 里）从 Infisical 拉取，省去手填。

### 3. 建库

先建好数据库，再按顺序执行迁移：

```bash
mysql -u root -p edifice_db < database/migrations/v1/init_schema.sql
# 然后按文件名顺序执行 database/migrations/v2/ 下的增量 SQL
```

### 4. 配前端环境变量

后端默认跑在 `8081`。在 `apps/edifice-vision/` 下建 `.env.local`：

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:8081
```

### 5. 启动

```bash
# 一起启动 Vision、Core 和 OA（Turbo 编排）
pnpm dev

# 或者只起后端
pnpm dev:core

# 或者只起前端
pnpm dev:vision

# 或者只起 OA
pnpm dev:oa
```

跑起来后：

| 服务 | 地址 |
| --- | --- |
| 前端 | http://localhost:3000 |
| OA | http://localhost:8080/home/index/index.html |
| 后端 API | http://localhost:8081 |
| 接口文档（Knife4j） | http://localhost:8081/doc.html |

---

## 常用命令

| 命令 | 作用 |
| --- | --- |
| `pnpm dev` | 同时启动 Vision、Core 和 OA（Turbo 并行编排） |
| `pnpm dev:vision` | 只启动前端 `edifice-vision` |
| `pnpm dev:core` | 只启动后端 `edifice-core` |
| `pnpm dev:oa` | 只启动 `gougu-oa` |
| `pnpm build` | 构建所有 app |
| `pnpm lint` | 全量 lint |
| `pnpm java:dev` | `pnpm dev:core` 的兼容别名 |
| `pnpm env:pull` | 从 Infisical 拉取 `.env`（前后端各一份） |

后端单独构建（在 `apps/edifice-core/` 下）：

```bash
./mvnw clean package      # 打包
./mvnw spring-boot:run    # 直接跑
```

---

## 接口约定

所有接口统一返回 `BaseResponse<T>`：

```jsonc
{
  "code": 200,        // 200 成功 / 500 通用失败 / 业务码见 common/Code.java
  "data": { },        // 业务数据，出错时为 null
  "msg": "success"    // 提示信息
}
```

常见业务状态码（`apps/edifice-core/src/main/java/com/qsy/edifice/common/Code.java`）：

| code | 含义 |
| --- | --- |
| `200` | 成功 |
| `500` | 通用失败 |
| `10003` | Access Token 过期（前端用 refresh token 续期） |
| `10004` | Refresh Token 过期（需重新登录） |
| `40300` | 权限不足 |
| `42900` | 请求过于频繁（限流） |

---

## 分支约定

- `master`：主分支
- `dev`：日常集成分支
- 特性分支命名：`<类型>/<模块>-<人名>-<日期>`，例如 `feat/project-xzb-20260315`

提交信息走 Conventional Commits：`feat:` / `fix:` / `refactor:` / `docs:` / `chore:` …

---

## 给后来人的几句话

- **改产值/回款逻辑前，先读 v0.4 文档**，规则比代码更权威。
- **后端日志包名**配置里写的是 `com.resume.core`（历史模板残留），真实包名是 `com.qsy.edifice`——不影响运行，但别被它误导。
- **`packages/shared-*` 目前是预留壳子**，前后端真正复用的东西还没沉淀进来。
- **OA 是独立系统**，靠共用用户表 + 同步任务打通，不是同一个进程。
