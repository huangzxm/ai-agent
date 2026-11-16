# 小蜜 · AI 客服平台

基于 **Spring AI + RAG + ReAct Agent** 的智能客服系统。统一入口下按意图分流到「知识库问答 / 任务执行 / 人工客服」三条路径，具备工具网关鉴权、关键服务熔断、统一降级交接与全链路监控能力。

> 当前仓库中的「恋爱大师」「YuManus」为早期教学 Demo，源码保留但前端入口已替换为客服系统。

---

## 目录

- [功能概览](#功能概览)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [目录结构](#目录结构)
- [快速开始](#快速开始)
- [接口说明](#接口说明)
- [演示账号与 Mock 数据](#演示账号与-mock-数据)
- [关键设计](#关键设计)
- [配置项](#配置项)
- [演示脚本](#演示脚本)

---

## 功能概览

| 能力 | 说明 |
|---|---|
| 双入口对话 | `/cs/rag` 知识库问答、`/cs/agent` 任务执行，也可由后端自动路由 |
| 会话管理 | 会话生命周期、文件持久化历史、上下文动态裁剪 |
| FAQ 缓存 | 高频寒暄直接命中；未命中答案暂存 5 分钟，命中 5 次晋升为 24 小时长效缓存 |
| 混合意图路由 | 关键词/情绪规则 → LLM 结构化分类 → 阈值兜底，输出 CHAT / AGENT / HUMAN 与 skillId |
| RAG 问答 | 指代消解重写、原始与重写双路召回、相似度阈值 + 业务元数据过滤、低置信拒答 |
| Agent 执行 | FSM 状态机约束 + ReAct 循环 + CoT 动态提示，支持多轮确认与 AI 主动降级 |
| 工具网关 | 本地工具与 MCP 工具统一注册，执行前强制鉴权、订单归属校验、审计留痕 |
| Observation 瘦身 | 工具返回的冗长 JSON 在交给模型前按规则裁剪，避免关键事实被日志噪音淹没 |
| 熔断降级 | 向量库、MCP 连续失败触发熔断，直接转人工，不让模型盲答 |
| 人工交接 | 统一 EscalationContext、模拟坐席分配、上下文快照、排队与接单状态跟踪 |
| 事件监控 | 转人工事件同步通知人工客服；指标接口输出路由分布、耗时、缓存命中、工具成功率 |

---

## 系统架构

```
                         ┌──────────────────────────────┐
  浏览器 (Vue 3)  ──────▶ │  CsChatController (SSE/同步)  │
                         └──────────────┬───────────────┘
                                        ▼
                         ┌──────────────────────────────┐
                         │      CsChatOrchestrator       │
                         │ 会话 → 缓存 → 路由 → 执行 → 降级 │
                         └──┬────────┬────────┬─────────┘
                            │        │        │
              ┌─────────────┘        │        └──────────────┐
              ▼                      ▼                       ▼
      ┌───────────────┐      ┌───────────────┐       ┌───────────────┐
      │  CHAT (RAG)   │      │ AGENT (ReAct) │       │ HUMAN (交接)  │
      │ Query 重写     │      │ FSM + CoT     │       │ 坐席分配       │
      │ 双路召回       │      │ 技能分发       │       │ 上下文打包     │
      │ 阈值 + 元数据  │      │ 工具网关       │       │ 事件通知       │
      └───────┬───────┘      └───────┬───────┘       └───────────────┘
              │                      │
              ▼                      ▼
      ┌───────────────┐      ┌─────────────────────────────┐
      │ 向量知识库     │      │ 本地工具 / MCP 工具          │
      │ 6 篇客服文档   │      │ 订单 · 物流 · 退款 · 工单 · ERP│
      └───────────────┘      └─────────────────────────────┘
```

---

## 技术栈

- **后端**：Java 21、Spring Boot 3.4.4
- **AI**：Spring AI 1.0.0-M6、Spring AI Alibaba 1.0.0-M6.1、DashScope（qwen-plus / embedding）
- **检索**：SimpleVectorStore（内存）+ Markdown 知识库 + 元数据过滤
- **协议**：MCP over SSE（image-search-mcp 模块）
- **交互**：SseEmitter + CompletableFuture 流式输出
- **工具库**：Hutool、Kryo、Jsoup、iText、Knife4j
- **前端**：Vue 3 + Vite 6 + Vue Router + Axios

---

## 目录结构

```
ai-agent/
├── src/main/java/com/yupi/yuaiagent/
│   ├── cs/                         # AI 客服系统（核心）
│   │   ├── api/                    # CsChatController、CsAdminController
│   │   ├── core/                   # 编排器、会话管理、FAQ 缓存、事件回调
│   │   ├── router/                 # 规则分类、LLM 分类、漏斗路由
│   │   ├── rag/                    # 知识库加载、向量库、RAG 服务
│   │   ├── agent/                  # FSM、ReAct Agent、技能分发、死循环检测
│   │   ├── tool/                   # 业务工具、工具注册中心、网关、Observation 清洗
│   │   ├── security/               # 认证、权限校验、审计
│   │   ├── human/                  # 转人工、坐席调度、上下文打包、事件通知
│   │   ├── support/                # 熔断、降级、指标、AOP 兜底切面
│   │   └── model/                  # 会话、路由、工具、交接等模型
│   ├── rag/                        # 通用 RAG 组件（查询重写等，供客服复用）
│   ├── chatmemory/                 # 文件持久化对话记忆
│   ├── agent/、app/、controller/    # 早期 Demo（保留）
│   └── tools/                      # 通用工具（网页搜索、PDF 等，保留）
├── src/main/resources/
│   ├── application.yml             # 主配置
│   ├── application-local.yml       # 本地密钥配置（已 gitignore，不提交）
│   └── document/
│       ├── cs/                     # 客服知识库：退换货/物流/发票/账号/订单/政策
│       └── *.md                    # 恋爱 Demo 知识库（保留）
├── ai-agent-front/                 # 前端
│   └── src/
│       ├── views/Home.vue          # 双入口首页
│       ├── views/CsChat.vue        # 客服对话页（RAG/Agent 共用）
│       ├── api/index.js            # SSE 封装
│       └── router/index.js         # /cs/rag、/cs/agent
└── image-search-mcp/               # 独立 MCP 服务（端口 8127）
```

---

## 快速开始

### 环境要求

- JDK 21、Maven 3.9+
- Node.js 18+、npm
- DashScope API Key（阿里云百炼）

### 1. 配置密钥

`application.yml` 默认读取环境变量：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY}
```

也可以在 `src/main/resources/application-local.yml` 中写入（该文件已在 `.gitignore`，不会提交）：

```yaml
spring:
  ai:
    dashscope:
      api-key: sk-xxxxxxxx
```

### 2. 按顺序启动

```bash
# ① MCP 服务（必须先启动，主应用启动时会建立 SSE 连接）
cd image-search-mcp
mvn spring-boot:run

# ② 后端（端口 8123，context-path /api）
mvn spring-boot:run

# ③ 前端（端口 5173）
cd ai-agent-front
npm install
npm run dev
```

访问 `http://localhost:5173`，首页选择「知识库问答」或「智能体助手」进入对话。

> 接口文档：`http://localhost:8123/api/swagger-ui.html`

---

## 接口说明

### 对话

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/cs/chat/sse` | SSE 流式对话 |
| POST | `/api/cs/chat` | 同步对话（调试用） |
| GET | `/api/cs/chat/history?chatId=` | 查询会话历史 |
| POST | `/api/cs/chat/close?chatId=` | 结束会话 |

**SSE 参数**：`message`、`chatId`、`userId`、`token`、`channel`、`mode`

`mode` 取值：`AUTO`（自动路由，默认）/ `RAG`（强制知识库）/ `AGENT`（强制任务执行）。

**SSE 事件**

| 事件名 | 内容 |
|---|---|
| `route` | 路由结果：route、confidence、category、reason |
| `stage` | 处理阶段提示：检索中、执行任务中 |
| `message` | 增量文本（分片） |
| `transfer` | 转人工信息：transferId、agent、reason |
| `done` | 最终响应体（完整答案、置信度、耗时、trace） |
| `fail` | 处理异常提示 |

### 运营后台

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/cs/admin/metrics` | 路由分布、耗时、缓存命中、工具成功率、token 估算 |
| GET | `/api/cs/admin/transfers` | 转人工记录列表 |
| POST | `/api/cs/admin/transfers/{id}/accept` | 模拟坐席接单 |
| POST | `/api/cs/admin/transfers/{id}/finish` | 结束人工会话 |
| GET | `/api/cs/admin/seats` | 坐席负载 |
| GET | `/api/cs/admin/audit?limit=50` | 工具调用审计 |
| GET | `/api/cs/admin/audit/denied?limit=50` | 被拦截的越权/失败记录 |
| GET | `/api/cs/admin/tools` | 已注册工具及来源 |
| POST | `/api/cs/admin/tools/refresh` | 刷新 MCP 工具（热插拔） |
| GET | `/api/cs/admin/circuit-breaker` | 熔断器状态 |
| GET | `/api/cs/admin/skills` | 已注册 Agent 技能 |

> 早期接口 `/api/ai/love_app/*`、`/api/ai/manus/chat` 仍保留可用。

---

## 演示账号与 Mock 数据

### 账号（模拟 token）

| userId | token | 角色 | 可操作订单 |
|---|---|---|---|
| `u1` | `demo-token-u1` | CUSTOMER | 1001、1002 |
| `u2` | `demo-token-u2` | CUSTOMER | 2001 |
| `admin` | `demo-token-admin` | CUSTOMER + AGENT_ADMIN | 全部 |

空 token 或未知用户视为匿名：可咨询知识库，不能查订单或退款。

### 订单

| 订单号 | 用户 | 商品 | 金额 | 状态 |
|---|---|---|---|---|
| 1001 | u1 | 机械键盘 | 199.0 | 待收货（有 3 条物流轨迹） |
| 1002 | u1 | 降噪耳机 | 899.0 | 已完成 |
| 2001 | u2 | 保温杯 | 59.0 | 待发货 |

### ERP 采购单

演示单号 `PR-202303`，返回包含 21 条状态流转、50 条物料明细、10 条审批记录的冗长 JSON；其中「供应商反馈缺料停产」是关键事实，最后一条「系统预排产」是干扰项。

### 其他

- 模拟坐席：`seat-01` ~ `seat-03`
- 预置 FAQ：你好 / 您好 / 在吗 / 谢谢 / 再见
- 知识库：`document/cs/` 下 6 篇文档（退换货、物流、发票、账号、订单、政策）

---

## 关键设计

### 意图路由（漏斗）

1. 规则层：转人工关键词、情绪打分、业务动作词（退款/订单/发票/采购单）
2. LLM 层：结构化输出 route、confidence、category、skillId、emotionScore
3. 阈值层：置信度 < 0.6 转人工；情绪需**连续 2 次**才转人工，单次先安抚并继续处理

### Agent：FSM + ReAct

```
START → AUTHENTICATE → COLLECT_PARAMS → VERIFY_ORDER → CONFIRM → EXECUTE → FINISHED
```

- 每个状态声明工具白名单，越权调用由代码拦截并回注提示
- 每轮动态注入当前状态、可用工具、缺失参数（CoT）
- 模糊确认（"嗯""好的"）不执行退款，先追问一次明确确认
- 重复工具调用不直接转人工，而是停止执行并询问是否需要人工
- AI 主动转人工需连续申请 3 次，前两次被拦截并要求继续澄清

### RAG

- 查询重写：结合本轮之前的历史做指代消解，失败回退原问句
- 双路召回：重写 Query 与原始 Query 各检索一次，按文档去重取最高分
- 过滤：相似度阈值 0.5 + `category` 元数据；分类无命中时退化为全库
- 拒答：检索为空给兜底引导（不降级）；有结果但低于阈值转人工

### 工具网关

所有工具调用统一经过 `ToolGateway`：

```
熔断检查 → 身份认证 → 权限与订单归属校验 → 执行 → Observation 瘦身 → 指标与审计
```

MCP 工具注册为远程来源，异常或超时直接触发熔断并转人工，不在模型侧重试。

### 统一降级

- Chat 与 Agent 共用 `EscalationContext`（原始诉求、最近对话、业务类型、原因、已尝试步骤、情绪、业务实体、TraceId）
- 转人工时发布 `AgentEscalationEvent`，监听器同步通知人工客服
- AOP 兜底拦截 Agent 未捕获异常，避免漏报

### 转人工判定

| 途径 | 行为 |
|---|---|
| 用户明确要求 | 立即转人工 |
| 情绪激动 | 连续 2 次才转，单次先安抚 |
| 同一问题重复 3 次 | 先询问是否需要转人工，用户同意才转 |
| AI 主动申请 | 需连续 3 次申请，前两次要求继续澄清 |
| 重复调用工具 | 不直接转，停止执行并询问 |
| RAG 低置信 / 服务熔断 / 系统异常 | 直接转人工 |

---

## 配置项

`application.yml` 中的 `cs.*`：

```yaml
cs:
  cache:
    ttl-seconds: 300            # 答案暂存时间
    promoted-ttl-seconds: 86400 # 热点晋升后的有效期
    promote-threshold: 5        # 命中多少次晋升
    max-size: 500
  route:
    confidence-threshold: 0.6   # 低于该置信度转人工
    emotion-threshold: 0.7      # 情绪计分阈值
    anger-strikes: 2            # 连续情绪次数才转人工
  rag:
    similarity-threshold: 0.5
    top-k: 3
    history-size: 8             # 参与上下文的历史条数
  agent:
    max-steps: 8
    repeat-limit: 2             # 重复调用检测
    escalate-attempts: 3        # AI 主动转人工申请次数
  human:
    agent-count: 3
    queue-limit: 100
    repeat-question-strikes: 3  # 重复提问多少次后询问转人工
```

---

## 演示脚本

| 场景 | 操作 | 预期 |
|---|---|---|
| 缓存命中 | 输入「你好」 | 直接返回，`cacheHit=true`，耗时极低 |
| 知识库问答 | 「退货政策是什么？」 | route=CHAT，返回政策要点与置信度 |
| 上下文追问 | 接上句问「那运费谁出？」 | 重写为完整问题后检索，回答运费规则 |
| 订单查询 | 智能体入口输入「我的订单 1001 到哪了？」 | route=AGENT，调用 queryOrder/queryLogistics |
| 多轮退款 | 「我要退款，订单 1001」→「嗯」→「确认退款」 | 第一次追问确认，第二次执行退款 |
| 越权拦截 | 用 u1 退订单 2001 | 工具网关拒绝，`/audit/denied` 可查 |
| ERP 瘦身 | 「采购单 PR-202303 现在走到哪一步了？」 | 日志显示裁剪量，回答命中「缺料停产」 |
| 重复提问 | 同一问题连发 3 次 | 第 3 次询问是否转人工；回复「转人工」才转接 |
| 情绪升级 | 连续两条明显抱怨 | 第一条不转，第二条转人工 |
| 服务熔断 | 停掉 MCP 后查询订单 | 立即转人工，`/circuit-breaker` 显示 OPEN |

---

## 技术亮点与设计取舍

**1. 混合路由，而不是全交给 LLM**
规则层零成本拦截高频意图（转人工、退款、采购单），LLM 只处理长尾语义。收益是首响快、行为可预测；代价是规则需要随业务维护。

**2. 用代码约束 Agent，而不是靠 Prompt 自觉**
`AgentFsm` 为每个状态声明工具白名单，越权调用在代码层拦截并回注观察；`applyRefund` 只在用户明确确认后可见。模型可以"想错"，但不能"做错"。

**3. Observation 瘦身，不改下游接口**
ToB 系统接口往往返回全量字段。网关在返回模型前裁剪大字段、保留最近状态与全部异常条目，避免"缺料停产"被系统日志淹没。不改 ERP 接口、不增加摘要模型。

**4. 双路召回兜住重写跑偏**
指代消解重写提升多轮检索准确率，但重写可能过度解读。原始 Query 与重写 Query 并行检索、按文档去重取最高分，形成双保险。

**5. 缓存冷热分离**
所有未命中答案先短 TTL 暂存，命中 5 次自动晋升长效，既避免冷启动穿透，也不会让低频问题长期占用内存；含订单号的问题不缓存，防止跨用户串答案。

**6. 降级有阈值、有确认**
情绪需连续 2 次、重复提问 3 次先询问、AI 主动转人工需申请 3 次。避免单次误判或一次工具失败就把用户推给人工，同时保留显式转人工的即时通道。

**7. Chat 与 Agent 共用一套交接**
两条路径都产出 `EscalationContext`（诉求、历史、原因、已尝试步骤、实体、TraceId），坐席可从断点接手；转人工与通知通过事件解耦。

**8. 全链路可观测**
路由分布、各路径耗时、缓存命中、工具成功率、Observation 裁剪量、熔断状态、越权审计均可通过运营接口查询。

---

## 部署与运维

### 构建

```bash
# 后端
mvn clean package -DskipTests
java -jar target/yu-ai-agent-0.0.1-SNAPSHOT.jar

# 前端
cd ai-agent-front
npm run build      # 产物在 dist/
```

### 生产部署建议

- API Key 通过环境变量 `AI_DASHSCOPE_API_KEY` 注入，不要把密钥写进配置文件
- 前端静态资源交给 Nginx，`/api` 反向代理到后端；**SSE 需要关闭缓冲**：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8123/api/;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;
    proxy_read_timeout 180s;
}
```

- MCP 服务与主应用分别部署时，修改 `spring.ai.mcp.client.sse.connections.servers1.url`
- 健康检查可接入 `/api/v3/api-docs` 或自行扩展 `HealthController`

---

## 常见问题

| 现象 | 原因与处理 |
|---|---|
| 启动报 `Failed to wait for the message endpoint` | MCP 服务未启动或地址不通，先启动 `image-search-mcp`（8127） |
| `InvalidConfigDataPropertyException: spring.profiles.active` | `application-local.yml` 中不要写 `spring.profiles.active`，该属性只能出现在主配置 |
| 调用返回 429 / rate limit | DashScope 每分钟请求数限制，等待或提升配额；系统会按低置信降级 |
| RAG 一直低置信转人工 | 检查 `document/cs/*.md` 是否加载、Embedding 模型是否可用、`category` 过滤是否过严 |
| 前端收不到流式内容 | 检查反向代理是否开启 `proxy_buffering off`、跨域配置、8123 端口是否可达 |
| 端口被占用 | `lsof -iTCP:8123 -sTCP:LISTEN`，结束残留 Java 进程后重启 |
| 工具调用全部失败 | 查看 `/api/cs/admin/audit` 与 `/api/cs/admin/circuit-breaker`，确认是否熔断或鉴权不通过 |

---

## 局限与后续规划

当前实现面向本地演示，以下部分为简化版，生产落地时可逐项替换：

- **向量库**：内存 `SimpleVectorStore`，重启重建；可换 PgVector / 云知识库，并引入 BM25 混合召回与 Reranker
- **会话存储**：进程内 Map + 本地文件；多实例部署需换 Redis
- **记忆体系**：目前为最近 N 条窗口裁剪；可扩展 L2 用户画像与 L3 向量长期记忆
- **业务系统**：订单、CRM、坐席均为内存 Mock；替换 `MockOrderRepository`、`AuthService`、`HumanAgentNotifier` 即可对接真实系统
- **鉴权**：模拟 token；生产应接入统一 SSO / OAuth2
- **Agent 决策**：当前依赖原生 tool calling；如需严格的结构化动作（ask_user / call_tool / escalate），可改为实体输出模式
- **扩展点**：新增业务 Agent 只需实现 `AgentSkill`；MCP 工具上下线后调用 `/tools/refresh` 热插拔

---

## 注意事项

- `application-local.yml` 保存真实 API Key，已被 `.gitignore` 忽略，请勿提交。
- 订单、CRM、坐席均为**内存 Mock**，重启后数据重置；接入真实系统时替换 `MockOrderRepository`、`AuthService`、`HumanAgentNotifier` 即可。
- 向量库为内存 `SimpleVectorStore`，应用启动时重新加载知识库文档。
- MCP 服务需先于主应用启动；运行中重启 MCP 后可调用 `/api/cs/admin/tools/refresh` 热加载。
