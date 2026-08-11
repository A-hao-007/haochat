# HaoChat Agent 智能体后端架构与面试指南

## 1. 项目一句话介绍

HaoChat Agent 是一个基于 Spring Boot、LangChain4j、Redis、MySQL、RocketMQ、Qdrant 和 Neo4j 的聊天智能体。它不仅生成文本，还能在权限约束下调用业务工具、执行需要人工确认的写操作、创建异步任务，并通过持久化 Run/Step 记录完整执行链路。

面试时不要只说“接入了大模型”。推荐表达：

> 我把大模型当作不可信的规划组件，真正的权限、幂等、超时、重试、熔断和状态流转都由后端确定性代码控制。每次请求都有持久化 Run，每次检索、模型推理和工具调用都有 Step，可以查询、评测和故障恢复。

## 2. 总体架构

```text
客户端消息
   │
   ▼
ChatController / MQ Consumer
   │  requestId = message:{messageId}
   ▼
AgentService
   ├── AgentConversationGuard（Redis 单会话 single-flight）
   ├── AgentRunTraceService（MySQL Run/Step 状态机）
   ├── AiLongTermMemoryService
   │     ├── Query Rewrite
   │     ├── Embedding Service
   │     ├── Qdrant 向量召回
   │     └── Neo4j 关系扩展
   ├── LangChain4j Streaming Agent
   │     └── McpToolProvider
   │           ├── 权限上下文注入
   │           ├── 高风险操作确认
   │           ├── 超时 / 重试 / 熔断
   │           └── 写操作幂等
   └── WebSocket 流式推送 + 消息落库
```

## 3. Agent Run 状态机

一条用户消息对应一个 `agent_run`。

```text
RUNNING
  ├── SUCCEEDED
  ├── FAILED
  ├── CANCELLED
  └── WAITING_CONFIRMATION
         ├── SUCCEEDED
         ├── FAILED
         └── CANCELLED
```

核心字段：

- `run_id`：公开链路追踪 ID。
- `request_id`：请求幂等键，聊天消息使用 `message:{messageId}`。
- `status/current_step`：当前状态与正在执行的步骤。
- `provider/model`：实际模型供应商和模型。
- `first_token_latency_ms`：首字延迟。
- `latency_ms`：端到端耗时。
- `tool_call_count`：工具调用次数。
- `error_code/error_message`：结构化失败原因。

`request_id` 有唯一索引。同一个 MQ 消息被重复消费时，第二次创建 Run 会触发唯一键冲突，后端读取已有 Run 并停止重复执行。

## 4. Step 级链路追踪

`agent_run_step` 记录以下步骤：

- `RETRIEVAL`：查询改写、向量检索、图谱检索。
- `MODEL`：流式模型推理。
- `TOOL`：MCP 工具调用。
- `CONFIRMATION`：用户确认或取消高风险工具。

每个 Step 都包含状态、输入摘要、输出摘要、尝试次数、耗时和错误码。

RAG Step 不保存召回的私人记忆正文，只记录：

```text
rewritten=true,
vectorHits=4,
graphHits=2,
totalHits=6,
vectorDegraded=false,
graphDegraded=false
```

这样既能观测，又不会把用户私人记忆复制到日志表。

## 5. 短期记忆与并发控制

短期记忆存储在 Redis，逻辑 key 为：

```text
ai:context:{roomId}:{uid}
```

运行期 memoryId 增加 `runId`：

```text
{roomId}:{uid}:{runId}
```

工具层可从中获得 Run 关联信息；RedisChatMemoryStore 只取前两段，因此不同 Run 仍共享同一会话记忆。

同一用户在同一房间同时触发两个 Agent 时，可能产生“后写覆盖前写”。系统使用 Redis single-flight 锁串行化同一会话的 Agent 请求：

- 锁值是随机 token。
- 锁 TTL 为 3 分钟，防止服务崩溃形成死锁。
- 释放锁使用 Lua compare-and-delete。
- 旧请求即使在 TTL 后完成，也不能删除新请求持有的锁。

## 6. RAG 与知识图谱

处理流程：

```text
原始问题
  → Query Rewrite
  → Embedding
  → Qdrant 用户私有记忆召回
  → Qdrant 当前房间记忆召回
  → Neo4j 实体关系扩展
  → 分数过滤与去重
  → 上下文长度限制
  → 注入模型
```

隔离规则：

- 用户记忆必须满足 `uid + scope=USER`。
- 房间记忆必须满足 `roomId + scope=ROOM`。
- Neo4j 查询同样限制为当前用户或当前房间。
- Qdrant 或 Neo4j 失败时降级，不阻断普通聊天。
- 检索内容被明确标记为非指令，降低 Prompt Injection 风险。

为什么同时使用 Qdrant 和 Neo4j：

- Qdrant 适合语义相似召回。
- Neo4j 适合“某人、某项目、某事件之间有什么关系”的多跳扩展。
- 两者互补，而不是为了堆技术栈。

## 7. 工具调用可靠性

工具元数据由 `McpTool` 提供：

- 是否只读。
- 是否需要人工确认。
- 单次超时时间。
- 最大尝试次数。

执行策略：

| 类型 | 自动重试 | 人工确认 | 幂等 |
| --- | --- | --- | --- |
| 查询天气、搜索记录 | 最多 2 次 | 否 | 天然只读 |
| 创建提醒 | 不重试 | 否 | Run + ToolCall 去重 |
| 代发消息、发布公告 | 不重试 | 是 | Pending 原子清除 + ToolCall 去重 |
| 未知远程 MCP 工具 | 不重试 | 依远端契约 | 默认保守 |

熔断策略：

- 连续 3 次执行失败后打开熔断器。
- 冷却 30 秒。
- 熔断期间快速失败，避免耗尽线程池。
- 成功执行后失败计数归零。

为什么写操作不能像查询一样重试：超时不等于执行失败。远端可能已经完成写入，只是响应丢失，盲目重试会产生重复副作用。

## 8. 高风险操作确认

```text
模型选择写工具
  → McpToolProvider 判断 needsConfirmation
  → 参数写入 Redis PendingConfirmation（TTL 5 分钟）
  → Run 进入 WAITING_CONFIRMATION
  → 用户确认
  → 后端重新注入 callerUid/roomId
  → 业务服务再次进行权限校验
  → 执行工具
  → Run 进入 SUCCEEDED/FAILED
```

用户取消、确认超时、发送其它文本放弃操作时，Run 会进入 `CANCELLED`，不会永久停留在等待状态。

关键安全点：

- `_callerUid` 和 `_roomId` 由后端注入，不交给模型生成。
- 模型的“用户已经确认”不具备任何权限意义。
- 真正执行前仍调用业务 Service 的权限校验。
- Pending 被清除后重复点击确认不会重复执行。

## 9. 异步任务、租约和 Exactly-once 业务效果

定时提醒写入 `agent_task`，Scheduler 到期后向 RocketMQ 投递事件。

任务状态：

```text
PENDING / WAITING
   → RUNNING（持有 lease_token）
   → DONE
   → WAITING（失败后指数退避）
   → FAILED（达到最大重试次数）
```

可靠性机制：

- MySQL 条件 UPDATE 原子抢占。
- 租约 2 分钟，消费者崩溃后可重新抢占。
- 每次抢占生成新的 `lease_token`。
- 完成操作必须匹配当前 token，旧消费者不能提交。
- 最多 3 次，退避时间为 30 秒、60 秒、120 秒。
- `last_error` 保存最终错误摘要。

提醒消息发送和任务完成在同一个 MySQL 事务中：

1. `ChatService.sendMsg` 写入消息。
2. `markDone(taskId, leaseToken)` 条件更新任务。
3. token 不匹配时抛异常，整个事务回滚。

因此不会出现“旧消费者发送了重复提醒，却无法把任务标为完成”的情况。

## 10. 可观测性接口

已提供用户隔离的接口：

```text
GET /capi/chat/ai/runs?limit=20
GET /capi/chat/ai/runs/{runId}
GET /capi/chat/ai/runs/metrics?days=7
```

指标包含：

- 总请求数、成功、失败、取消、等待确认。
- 成功率。
- 平均/P95 总延迟。
- 平均/P95 首字延迟。
- 总工具调用次数。

接口按登录 uid 查询，不能读取其他用户的输入、回复和执行步骤。

## 11. 自动化测试

确定性测试不调用真实模型：

- 只读工具第一次失败、第二次成功。
- 写工具失败后不自动重试。
- 连续三次失败后熔断，第四次快速拒绝。

真实模型 E2E 评测单独放在 `AgentEvalTest`，默认 `@Ignore`，避免 CI 消耗模型额度或依赖生产数据。执行时使用真实测试用户和房间，等待 Run 到达终态，再校验工具选择与最终回复。

推荐继续积累的指标：工具选择正确率、参数正确率、越权拦截率、引用准确率、任务完成率和 P95 延迟。

## 12. 故障场景回答

### 模型超时怎么办？

模型失败会把 MODEL Step 和 Run 标为 FAILED；如果没有输出 token，走备用模型网关；已经产生部分内容时保留部分回答，避免用户完全无响应。

### 工具线程卡死怎么办？

工具在独立的有界线程池执行，每个工具有超时。超时后取消 Future 并记录 `TOOL_TIMEOUT`。连续失败触发熔断。

### Redis 挂了怎么办？

短期记忆、确认状态和并发锁依赖 Redis。记忆读取失败会退化为空记忆；会话锁或确认状态不可用时不应冒险执行写操作，由 MQ 重试或返回失败。

### MySQL Trace 写失败会不会导致聊天不可用？

Trace 写入被隔离，记录失败只告警，不阻断模型回答。Run 创建失败时仍生成内存 runId 保持工具关联。但请求幂等的强保证依赖 MySQL 可用。

### MQ 重复投递怎么办？

事件层有 Redis eventId 去重，任务层还有 MySQL 条件抢占和 lease token。即使事件去重失效，也只有一个消费者能持有有效租约。

### 为什么不用分布式事务？

任务状态和聊天消息在同一个 MySQL 中，使用本地事务即可保证原子性；MQ 采用至少一次投递，通过幂等和状态机消化重复，比引入重型分布式事务更合适。

## 13. 可现场演示的场景

1. 普通知识问题：Run 依次产生 RETRIEVAL、MODEL Step，展示首字延迟。
2. 查询天气：产生 TOOL Step，展示参数摘要、尝试次数和耗时。
3. 发布群公告：Run 停在 WAITING_CONFIRMATION，确认后进入 SUCCEEDED。
4. 重复消费同一个 messageId：唯一 requestId 使第二次请求不再执行。
5. 工具连续失败：第三次后打开熔断，第四次立即返回 CIRCUIT_OPEN。
6. 构造过期任务租约：新 token 可以重抢，旧 token 更新行数为 0。
7. 查看 metrics：展示成功率、P95 延迟和工具调用量。

## 14. 三分钟项目介绍模板

> HaoChat 的 Agent 最初只有 LangChain4j function calling 和 Redis 记忆，能够工作，但无法回答线上执行到哪一步、工具失败如何恢复、重复消息是否会产生副作用。我把它升级成了一个有确定性后端控制面的智能体系统。
>
> 每条消息先以 messageId 建立唯一 Agent Run，再记录 RAG、模型和工具 Step。RAG 会做查询改写，同时从 Qdrant 和 Neo4j 检索，但严格按 uid 和 roomId 隔离。工具执行层区分读写语义：只读操作可以有限重试，写操作禁止盲重试，并统一做超时、熔断、幂等和人工确认。
>
> 异步提醒使用 RocketMQ 和 MySQL 状态机。我增加了任务租约与 fencing token，消费者崩溃后任务能恢复，旧消费者不能提交；消息写入和任务完成使用同一个本地事务，防止重复提醒。最后提供 Run 明细和成功率、P95 延迟等指标，并用不依赖真实模型的单测验证重试与熔断。

## 15. 后续可演进方向

- 使用 OpenTelemetry 把 HTTP、模型、Qdrant、Neo4j 和工具 Step 串成分布式 Trace。
- 引入模型输出 JSON Schema 校验和参数修复回路。
- 为远程 MCP 增加签名、风险级别和幂等能力协商。
- 建立版本化 Golden Dataset 和自动回归看板。
- 对检索结果增加 reranker 与引用证据展示。
- 对长任务增加 checkpoint，使规划可以跨服务重启恢复。

这些是演进项，不应在面试中描述成已经完成的能力。
