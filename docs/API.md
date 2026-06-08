# 考试平台后端 API 文档

本文档依据当前代码（Spring Boot + Spring Security + JWT）整理，与实现保持一致。

## 1. 基础信息

| 项 | 说明 |
| --- | --- |
| 基础路径 | `http://{host}:8080/api/v1` |
| 上下文路径 | `server.servlet.context-path=/api/v1`（见 `application.properties`） |
| 内容类型 | `application/json`（请求体为 JSON 时使用） |
| 时间格式 | JSON 中日期时间默认为 `yyyy-MM-dd HH:mm:ss`，时区 `Asia/Shanghai` |

### 1.1 统一响应结构

所有接口返回 `Result<T>`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | Integer | 业务/HTTP 语义码；成功一般为 `200` |
| `message` | String | 提示信息 |
| `data` | T | 业务数据，可为 `null` |
| `timestamp` | Long | Unix 秒级时间戳 |

成功示例：`code=200`，`message` 一般为「操作成功」。  
业务失败：`AuthController` 等会直接返回 `Result.error(400/403, "...")`。  
未捕获的 `RuntimeException` 由全局异常处理转为 `code=500`，`message` 为异常信息。

### 1.2 认证方式

- 请求头：`Authorization: Bearer <JWT>`
- JWT 有效期：配置项 `jwt.expiration`（秒），默认 86400（24 小时）
- 登录成功后响应中的 `role` 形如 `ROLE_ADMIN`、`ROLE_USER`（与库表 `role_code` 对应）
- 调用 `/auth/logout` 后 Token 会写入 Redis 黑名单，后续同一 Token 视为失效

### 1.3 权限与放行规则

| 场景 | 说明 |
| --- | --- |
| 匿名可访问 | `POST /auth/login`、`POST /auth/register` |
| 匿名可访问 | `GET /exam/analysis/leaderboard`（查询参数 `paperId`） |
| 匿名可访问 | `GET /ai/**`（配置已放行；若工程内未暴露该前缀控制器则无实际路由） |
| 其余接口 | 需携带有效 JWT |
| 管理员接口 | `@PreAuthorize("hasRole('ADMIN')")`，要求用户角色为 `ROLE_ADMIN` |

未登录访问需认证的接口时，由 Spring Security 处理（通常为 403 等，具体与过滤器链有关）。

---

## 2. 认证模块 `/auth`

### 2.1 登录

- **POST** `/auth/login`
- **鉴权**：无需登录
- **请求体** `LoginReq`

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `username` | string | 必填 |
| `password` | string | 必填 |

- **响应** `data`：`LoginResp`

| 字段 | 说明 |
| --- | --- |
| `token` | JWT |
| `userId` | 用户 ID 字符串（避免长整型精度问题） |
| `role` | 如 `ROLE_USER` |
| `realName` | 真实姓名 |

失败示例：`code=400` 用户名或密码错误；`code=403` 账号禁用。

### 2.2 注册

- **POST** `/auth/register`
- **鉴权**：无需登录
- **请求体** `RegisterReq`

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `username` | string | 必填 |
| `password` | string | 必填 |
| `realName` | string | 必填 |
| `roleCode` | string | 可选，默认 `USER` |

- **响应**：`data` 为空（成功）

用户名重复时 `code=400`。

### 2.3 退出登录

- **POST** `/auth/logout`
- **鉴权**：建议携带 Token（代码会从 `Authorization` 解析并加入黑名单）
- **响应**：`data` 为空

### 2.4 当前用户信息（调试/验证鉴权）

- **GET** `/auth/info`
- **鉴权**：需要 JWT（实现上假定 Header 存在且以 `Bearer ` 开头）
- **响应** `data`：Map

| 字段 | 说明 |
| --- | --- |
| `userId` | 字符串 |
| `username` | 用户名 |
| `role` | 如 `ROLE_USER` |

---

## 3. 考生端考试流程 `/exam`

以下接口（除排行榜外）均需登录，当前用户 ID 来自 JWT（`SecurityContext` 中的 principal 为用户 ID 字符串）。

### 3.1 待考试卷列表

- **GET** `/exam/paper/pending`
- **响应** `data`：`List<Map>`，每项大致包含：

| 字段 | 说明 |
| --- | --- |
| `paperId` | 字符串 |
| `title` | 试卷标题 |
| `durationMins` | 考试时长（分钟） |
| `totalScore` | 满分 |
| `examStartTime` / `examEndTime` | 开放时间段 |
| `allowQuit` / `allowSwitchScreen` | 布尔，是否允许退出/切屏 |
| `isStarted` | 当前时间是否已到开考时间 |

过滤逻辑：已发布（`paperStatus=1`）、当前用户尚无记录、且当前时间早于 `examEndTime`。

### 3.2 已考/已完成记录列表

- **GET** `/exam/paper/completed`
- **响应** `data`：`List<Map>`，包含已交卷、阅卷中、强制收卷及缺考/过期等条目，字段示例：

| 字段 | 说明 |
| --- | --- |
| `recordId` | 字符串，缺考条目可能为 `null` |
| `paperId` | 字符串 |
| `title` | 试卷标题 |
| `totalScore` | 得分 |
| `paperScore` | 试卷满分 |
| `submitTime` | 提交时间或展示用时间 |
| `isFavorited` | 是否收藏 |
| `isMissed` | 是否缺考/过期 |
| `tag` | 如「已交卷」「阅卷中」「强制收卷」「缺考/过期」 |

### 3.3 进入考场 — 拉取试卷详情（无标准答案）

- **GET** `/exam/paper/detail/{paperId}`
- **前置条件**：试卷已发布；当前时间在 `examStartTime`～`examEndTime` 内；Redis 中存在试卷缓存；用户该场考试记录未处于已交卷/阅卷完成状态。
- **响应** `data`：`Map`，在发布试卷时写入缓存的结构基础上追加：

| 字段 | 说明 |
| --- | --- |
| `paperId` | 字符串 |
| `title` | 标题 |
| `durationMins` | 时长 |
| `totalScore` | 满分 |
| `questions` | 题目列表（含 `questionId`、`questionType`、`content`、`options`、`score`，不含标准答案） |
| `allowSwitchScreen` / `allowQuit` | 布尔 |
| `examEndTimeTs` | 考试结束时间毫秒时间戳，可为 `null` |
| `serverTime` | 服务端 Unix 秒 |

不满足条件时抛出运行时异常，接口返回 `code=500` 及对应 `message`。

### 3.4 答题心跳（保存作答进度）

- **POST** `/exam/session/heartbeat`
- **请求体** `HeartbeatReq`

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `paperId` | Long | 必填 |
| `questionId` | Long | 必填 |
| `userAnswer` | string | 可选；空或空白表示清除该题答案 |

仅在存在考试中（`examStatus=0`）的记录时写入 Redis；成功 `data` 为空。

### 3.5 交卷

- **POST** `/exam/submit`
- **请求体** `SubmitReq`

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `paperId` | Long | 必填 |
| `forceSubmit` | Boolean | 可选，默认 `false`（超时/防作弊强制交卷等场景可为 `true`） |

- **响应** `data`：`recordId`（字符串）、`msg`（提示文案）。阅卷异步通过 MQ 处理。

### 3.6 收藏/取消收藏成绩记录

- **PUT** `/exam/record/{recordId}/favorite`
- **查询参数**：`favorite`（Boolean，必填）
- **响应**：成功 `data` 为空（仅本人记录生效）

### 3.7 删除考试记录

- **DELETE** `/exam/record/{recordId}`
- **响应**：成功 `data` 为空（仅本人记录生效）

### 3.8 个人成绩报告

- **GET** `/exam/analysis/report/{recordId}`
- **响应** `data`：`ReportResp`

| 字段 | 说明 |
| --- | --- |
| `totalScore` | 得分 |
| `beatPercentage` | 战胜百分比等文案 |
| `wrongQuestions` | 错题列表（`questionId`、`content`、`userAnswer`、`standardAnswer`、`analysis`） |

### 3.9 排行榜（公开）

- **GET** `/exam/analysis/leaderboard?paperId={paperId}`
- **鉴权**：无需登录（安全配置放行）
- **响应** `data`：`List<LeaderboardResp>`（前 20 名）

| 字段 | 说明 |
| --- | --- |
| `rank` | 名次 |
| `userId` | 用户 ID 字符串 |
| `realName` | 姓名 |
| `score` | 分数 |

### 3.10 AI 助教（错题讲解）

- **GET** `/exam/analysis/ai-tutor?questionId={questionId}&wrongUserAnswer={wrongUserAnswer}`
- **响应** `data`：字符串，为 AI 生成的讲解文案；题目不存在时 `code=400`。

---

## 4. 管理端 — 试卷 `/admin/paper`

**角色**：`ROLE_ADMIN`。

### 4.1 手动组卷

- **POST** `/admin/paper/create`
- **请求体** `PaperCreateReq`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `title` | string | 必填 |
| `durationMins` | int | 必填 |
| `passScore` | int | 必填 |
| `examStartTime` | datetime | 必填 |
| `examEndTime` | datetime | 必填 |
| `allowSwitchScreen` | boolean | 可选，默认 true |
| `allowQuit` | boolean | 可选，默认 true |
| `questionList` | array | 题目明细 |

`questionList` 每项 `PaperQuestionItem`：`questionId`、`itemScore`、`sortNum`。

- **响应** `data`：新建试卷 ID（Long）。

试卷初始状态为草稿（`paperStatus=0`），`totalScore` 由小题分值汇总。

### 4.2 规则随机抽题组卷

- **POST** `/admin/paper/random-create`
- **请求体** `PaperRandomCreateReq`

在手动组卷字段基础上增加：`subjectId`（必填）、`ruleParams`（`Map`，抽题规则，具体结构由抽题策略实现决定）。

- **响应** `data`：新建试卷 ID（Long）。

### 4.3 发布试卷

- **PUT** `/admin/paper/publish/{paperId}`
- **响应**：成功 `data` 为空；会将题目快照写入 Redis 供考生拉取。

### 4.4 结束/下线试卷

- **PUT** `/admin/paper/end/{paperId}`
- **响应**：成功 `data` 为空；删除 Redis 试卷缓存，状态置为已下线（`paperStatus=2`）。

### 4.5 试卷详情（含答案，管理视角）

- **GET** `/admin/paper/detail/{paperId}`
- **响应** `data`：`Map`

| 键 | 说明 |
| --- | --- |
| `paper` | `ExamPaper` 实体 JSON |
| `questions` | 列表，含 `questionId`、`content`、`optionsJson`、`standardAnswer`、`itemScore`、`questionType` |

### 4.6 试卷分页

- **GET** `/admin/paper/page?pageNo=1&pageSize=10`
- **响应** `data`：MyBatis-Plus `Page<ExamPaper>`（含 `records`、`total`、`size`、`current`、`pages` 等）。

---

## 5. 管理端 — 题库 `/admin/question`

**角色**：`ROLE_ADMIN`。

### 5.1 新增题目

- **POST** `/admin/question/add`
- **请求体** `QuestionReq`（`id` 为空表示新增）

| 字段 | 说明 |
| --- | --- |
| `subjectId` | 必填 |
| `questionType` | 必填；1 单选，2 多选（与实体注释一致） |
| `content` | 题干 |
| `optionsJson` | 选项 JSON（任意 Object，存库用 Jackson 处理） |
| `standardAnswer` | 标准答案 |
| `difficulty` | 可选，默认 2（1易 2中 3难） |
| `analysis` | 可选解析 |

- **响应** `data`：题目 ID。

### 5.2 更新题目

- **PUT** `/admin/question/update`
- **请求体**：同上，`id` 为待更新题目 ID。
- **响应** `data`：题目 ID。

### 5.3 删除题目

- **DELETE** `/admin/question/{id}`
- **响应**：逻辑删除，成功 `data` 为空。

### 5.4 题目分页

- **GET** `/admin/question/page?pageNo=1&pageSize=10&subjectId=&keyword=`
- **响应** `data`：`Page<ExamQuestion>`（含选项 JSON、标准答案等字段，注意泄露风险仅限管理端）。

### 5.5 AI 生成试题预览

- **POST** `/admin/question/ai-generate`
- **请求体** `AiGenerateReq`

| 字段 | 约束 |
| --- | --- |
| `questionTypeDesc` | 必填，题型/指令描述 |
| `documentText` | 必填，上下文知识点 |

- **响应** `data`：服务端返回的 JSON 字符串（多题预览），前端确认后再走 `/add` 入库。

---

## 6. 管理端 — 用户 `/admin/user`

**角色**：`ROLE_ADMIN`。

### 6.1 用户分页

- **GET** `/admin/user/page?pageNo=1&pageSize=10&keyword=`
- **响应** `data`：`Page<SysUser>`，密码字段已置空。

### 6.2 更新用户状态

- **PUT** `/admin/user/status`
- **请求体** `UserStatusUpdateReq`：`id`（用户主键）、`status`（1 正常，0 禁用）。
- **响应**：成功 `data` 为空。

---

## 7. 管理端 — 数据分析 `/admin/analysis`

**角色**：`ROLE_ADMIN`。

### 7.1 单场考试统计

- **GET** `/admin/analysis/statistics/{paperId}`
- **响应** `data`：`DashboardResp`

| 字段 | 说明 |
| --- | --- |
| `paperStatus` | 0 草稿 / 1 考试中 / 2 已结束 |
| `totalParticipants` | 参考人数 |
| `averageScore` | 平均分 |
| `highestScore` | 最高分 |
| `passRate` | 及格率文案 |
| `scoreDistribution` | 分数段分布 Map |

### 7.2 控制台全局汇总

- **GET** `/admin/analysis/dashboard-summary`
- **响应** `data`：`DashboardSummaryResp`：`totalQuestions`、`totalPapers`、`totalExams`、`activeUsers`。

---

## 8.  domain 常量备忘

| 概念 | 取值说明 |
| --- | --- |
| `ExamPaper.paperStatus` | 0 草稿，1 已发布，2 已下线 |
| `ExamRecord.examStatus` | 0 考试中，1 已交卷（完成阅卷语义以业务为准），2 阅卷中等 |
| `SysUser.status` | 1 正常，0 禁用 |
| `SysUser.roleCode` | 如 `ADMIN`、`USER` |

---

## 9. 修订说明

文档随代码迭代；若修改了路径、安全配置或 DTO，请同步更新本文档。
