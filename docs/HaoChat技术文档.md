# HaoChat 项目技术文档

> 本文档基于对 `/opt/haochat` 源码的逆向分析编写，目标读者是想学习并重构这个**完全由 AI 生成**的全栈即时通讯项目的开发者。文档生成日期：2026-06-29。

---

## 目录

1. [项目概览](#1-项目概览)
2. [目录结构解析](#2-目录结构解析)
3. [数据库设计](#3-数据库设计)
4. [核心功能实现原理](#4-核心功能实现原理)
5. [API 接口清单](#5-api-接口清单)
6. [状态管理设计](#6-状态管理设计)
7. [部署架构](#7-部署架构)
8. [AI 生成代码的特征与陷阱](#8-ai-生成代码的特征与陷阱)
9. [转化建议：如何把它变成"你自己的"项目](#9-转化建议如何把它变成你自己的项目)

---

## 1. 项目概览

### 1.1 项目定位

HaoChat 是一个仿微信风格的网页即时通讯应用，支持单聊、群聊、好友系统、AI 助手对话、文件/语音/图片消息、消息实时推送。整个工程（前端+后端+部署配置）由 AI 一次性生成，后续又经过多轮人工 bug 修复和功能补充（详见第 8 章）。

### 1.2 核心功能清单

| 模块 | 功能点 |
|---|---|
| 账号体系 | 用户名密码注册/登录、邮箱验证码登记（注册即绑定）、邮箱登录、忘记密码、改昵称（背包道具限次）、改头像 |
| 好友系统 | 搜索用户、发送/同意好友申请、好友备注、删除好友、好友申请未读数 |
| 会话/消息 | 单聊、群聊、消息发送/撤回/编辑/点赞举报、已读未读统计、消息引用回复、置顶/免打扰会话 |
| 群聊 | 建群、拉人/踢人、设管理员、转让群主、群公告（带已读统计）、群内昵称 |
| 实时能力 | WebSocket 推送新消息/在线状态/好友申请/群成员变动、心跳保活 |
| AI 助手 | 内置规则回复 + DeepSeek/ChatGPT 接入、Function Calling、MCP（Model Context Protocol）工具调用、主动提醒（定时轮询） |
| 周边 | 头像/语音/图片走 MinIO 对象存储、IP 属地解析（离线库 ip2region）、敏感词过滤、黑名单 |

### 1.3 技术架构全景（文字版）

```
                         ┌─────────────────────────┐
                         │   浏览器 (Vue 3 SPA)      │
                         └────────────┬─────────────┘
                                      │ HTTPS
                         ┌────────────▼─────────────┐
                         │   Caddy (反向代理+TLS)     │
                         │  /            → 静态文件   │
                         │  /capi/*      → backend:8080
                         │  /websocket*  → backend:8090
                         │  /haochat/*   → minio:9000 (预签名URL)
                         └──┬──────────┬─────────┬───┘
                            │          │         │
                ┌───────────▼──┐  ┌────▼────┐  ┌─▼─────────┐
                │ Spring Boot  │  │ Netty   │  │  MinIO    │
                │ (REST API)   │  │ WebSocket│ │ 对象存储   │
                │ :8080        │  │ Server  │  │ :9000/9001│
                │              │  │ :8090   │  └───────────┘
                └──┬───┬───┬───┘  └────┬────┘
                   │   │   │           │ (共享在线用户表/事件)
        ┌──────────▼┐ ┌▼──────┐  ┌────▼──────┐
        │  MySQL 8  │ │Redis 7│  │ RocketMQ  │
        │ (业务数据) │ │(缓存/  │  │(异步消息   │
        │           │ │验证码/ │  │ 分发/事件) │
        │           │ │分布式锁)│  └───────────┘
        └───────────┘ └───────┘
```

后端是**单体应用**（不是微服务），REST API 和 WebSocket Server 跑在同一个 JVM 进程里，只是分别监听 8080 / 8090 两个端口。RocketMQ 用来把"发消息"这种重操作里的非核心副作用（写会话表、发系统通知、AI 触发等）异步化，避免主链路阻塞。

### 1.4 技术栈版本明细

| 层 | 技术 | 版本 |
|---|---|---|
| 前端框架 | Vue | 3.3.4 |
| 前端构建 | Vite | 4.x（通过 `config/vite.config.*.ts` 区分 dev/prod） |
| 前端语言 | TypeScript | — |
| 状态管理 | Pinia | 2.1.6（+ pinia-plugin-persistedstate 3.2.0 做本地持久化） |
| UI 库 | Element Plus | 2.3.14 |
| 请求库 | Alova | 2.13.1（+ @alova/scene-vue 1.2.0） |
| 路由 | vue-router | 4.2.5 |
| 其它前端库 | dayjs 1.11.10、lodash 4.17.23、mitt 3.0.1（事件总线）、qrcode.vue、xgplayer 2.32.5（视频播放） |
| 后端框架 | Spring Boot | 2.6.7 |
| 语言/运行时 | Java | 17 |
| ORM | MyBatis-Plus | — |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7-alpine |
| 消息队列 | RocketMQ | 5.1.4 |
| 对象存储 | MinIO | latest（S3 兼容） |
| 反向代理 | Caddy | 2-alpine |
| 容器编排 | Docker Compose | — |
| IP 属地库 | ip2region | 离线 xdb，纯内存查询 |
| AI 接入 | DeepSeek（默认）/ ChatGPT（可选）+ 自研轻量 MCP Server | — |

🟡 **注意**：仓库里还留有一份 `haochat-server/docs/TECHNICAL_DOCS.md`，描述的是更早期的部署形态（Nginx + 裸机部署到 `/root/HaoChat`），与当前 `/opt/haochat` 下基于 Docker Compose + Caddy 的部署方式已经不一致，属于**过期文档**，本文档以当前源码为准。

---

## 2. 目录结构解析

### 2.1 前端目录树（`haochat-web/src/`）

```
src/
├── views/            页面级视图：Login（登录/注册）、Home（应用主壳，内含 Chat/Contacts 等子路由）
├── components/        跨页面复用组件：VirtualList(自研虚拟列表)、Avatar、AddFriendModal、
│                      CreateGroupModal、RenderMessage(消息内容渲染)、AiAssistantBall 等
├── stores/            Pinia 状态管理，按领域拆分（见第 6 章）
│   ├── chat.ts        消息/会话核心状态（最复杂的一个 store）
│   ├── user.ts        当前登录用户信息、token
│   ├── global.ts      全局 UI 状态：当前选中会话、各类弹窗开关
│   ├── group.ts       群详情、群成员、群公告
│   ├── contacts.ts     联系人列表、好友申请列表
│   ├── cached.ts       用户信息/徽章缓存（按 uid 查，避免重复请求）
│   ├── ws.ts            WebSocket 扫码登录相关状态
│   ├── emoji.ts          自定义表情
│   └── preview.ts / downloadQuenu.ts   图片预览 / 下载队列
├── services/           API 请求层（基于 Alova 封装）
│   ├── urls.ts          所有接口路径常量
│   ├── apis.ts           按方法封装好的请求函数（参数类型 + 调用 urls.ts）
│   ├── types.ts          请求/响应的 TS 类型定义
│   └── request.ts        Alova 实例配置（鉴权头注入、错误统一处理）
├── router/             路由表 + 路由守卫（未登录拦截等）
├── hooks/               组合式函数（如 useUserInfo、useTheme、useCached）
├── utils/                工具函数（websocket.ts 连接管理、computedTime.ts 时间格式化等）
├── directives/            自定义指令（如 v-login-show）
├── enums/                  枚举（RoomTypeEnum、MsgEnum、RoleEnum 等）
├── constant/                常量
├── assets/                   静态资源（图片、iconfont）
└── styles/                    全局样式（CSS 变量体系，支撑亮/暗主题切换）
```

🟢 这套划分（views/components/stores/services 分层）是 Vue 生态里非常标准的工程结构，AI 在"套模板"这件事上做得不差。

### 2.2 后端目录树（`haochat-chat-server/src/main/java/com/ahao/haochat/common/`）

后端是**按业务域（domain）纵切，每个域内部再按经典分层（controller/service/dao/domain）横切**，而不是传统 Spring 项目常见的"先按层切，再按域分包"。

```
common/
├── chat/                 聊天域：消息、单聊/群聊房间、会话列表
│   ├── controller/        ChatController / RoomController
│   ├── service/             ChatService / RoomAppService / RoomService（含 impl/、adapter/ 子包）
│   ├── dao/                  MessageDao / RoomDao / RoomFriendDao / RoomGroupDao / GroupMemberDao / ContactDao
│   ├── mapper/                 MyBatis Mapper 接口
│   └── domain/                  entity（DB实体）/ vo/request / vo/response / dto / enums
├── user/                  用户域：账号、好友、背包道具、改名卡
│   ├── controller/         AuthController / UserController / FriendController
│   ├── service/              UserServiceImpl / EmailAuthService / UserBackpackServiceImpl / IpService
│   ├── scheduler/             RenameCardScheduler（本次新增，月度发改名卡）
│   └── domain/                User / UserFriend / UserApply / UserBackpack / ItemConfig 等实体
├── chatai/                AI 助手域：内置规则回复 + DeepSeek/ChatGPT + Agent + MCP
│   ├── handler/             ChatAIHandlerFactory + 各 Handler 实现（策略模式）
│   ├── agent/                AgentScheduler（主动提醒轮询）
│   ├── mcp/                    自研 MCP Server + 工具实现（CreateReminderTool 等）
│   └── properties/             DeepSeekProperties 等配置 Bean
├── websocket/             Netty WebSocket 服务端：连接管理、心跳、消息广播
├── sensitive/              敏感词过滤（DFA/AC自动机）
└── common/                 横切基础设施：统一异常处理、JWT工具、限流注解、Redis工具、IP属地解析等
```

### 2.3 关键配置文件

| 文件 | 作用 |
|---|---|
| `haochat-web/config/vite.config.base.ts` | 公共构建配置：Vue 插件、`@/` 路径别名、Element Plus/Iconify 按需自动导入 |
| `haochat-web/config/vite.config.dev.ts` | 开发态：端口 9988，`/capi` 代理到 `VITE_API_PREFIX` |
| `haochat-web/config/vite.config.prod.ts` | 生产构建：gzip 压缩、图片压缩、`vue`/`xgplayer` 手动分包 |
| `haochat-web/.env.production` | 生产环境前端变量留空，走 Caddy 同域相对路径转发，避免跨域 |
| `haochat-chat-server/.../application-prod.properties` | 生产配置：数据源/Redis/RocketMQ/MinIO/JWT/AI Key 全部走环境变量，带兜底默认值 |
| `deploy/docker/web.Dockerfile` | 多阶段构建：Node 22 编译产物 → 装进 Caddy 镜像里 serve |
| `deploy/docker/backend.Dockerfile`（或同名文件）| Maven+JDK17 编译 → Eclipse Temurin 17 JRE 运行期镜像 |
| `docker-compose.yml` | 全栈编排入口，9 个服务（见第 7 章） |
| `deploy/caddy/Caddyfile` | 反代路由规则：`/capi/*`→8080，`/websocket*`→8090，`/haochat/*`→MinIO |
| `.env.example` | 部署所需的全部环境变量清单（域名、DB/Redis/MinIO 密钥、JWT_SECRET、AI Key 等） |

---

## 3. 数据库设计

> ⚠️ **注意**：仓库里的 `haochat-server/docs/haochat.sql` 是项目初始建表脚本，**不是当前线上真实表结构**。本次会话期间我们通过 `deploy/sql/` 下的若干 `ALTER TABLE` 迁移脚本，给 `user` 表加了 `username`/`password`/`email` 字段，给 `contact` 表加了 `pinned`/`muted` 字段——这些都**不在** `haochat.sql` 里。这是 AI 生成项目一个很典型的"文档/脚本与实际线上状态脱节"问题，重构时第一件事就应该是把当前线上真实 schema `mysqldump --no-data` 出来，作为唯一真相来源。

### 3.1 ER 关系（文字版）

```
user ──┬─< user_friend >──┐ (自关联，单向好友关系，互相各存一条)
       │                   │
       ├─< user_apply >────┘ (好友申请记录)
       │
       ├─< user_backpack >─── item_config  (背包道具：改名卡/徽章)
       │
       ├─< room_friend >─── room  (单聊房间，uid1/uid2 排序后拼成 room_key 防重复建房)
       │
       ├─< group_member >─── room_group ─── room  (群聊：room_group 是群资料，room 是房间壳)
       │
       ├─< contact >─── room  (会话列表：每个用户在每个房间一条，记录已读到哪、是否置顶/免打扰)
       │
       └─< message >─── room  (消息属于房间，from_uid 指向发送者；reply_msg_id 自关联实现引用回复)
                │
                └─< message_mark >  (点赞/举报标记)
```

🟡 **设计取舍说明**：单聊和群聊共用同一张 `room`/`message`/`contact` 表，单聊额外用 `room_friend` 记录两个 uid 的映射，群聊额外用 `room_group`+`group_member` 记录群资料和成员。这是一种"房间统一抽象"的设计，比"单聊消息表+群聊消息表分开建"更省心，但也意味着**几乎所有消息查询都要先经过 room_id 这一层间接**，单聊场景下多了一次 `room_friend` 查找成本（不过通常会走缓存）。

### 3.2 核心表结构

#### user（用户表）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint unsigned PK | |
| name | varchar(20) | 昵称，唯一索引 |
| username | varchar(50) | 🟡 不在原始SQL里，本次会话通过迁移脚本补充，唯一索引 |
| password | varchar(200) | BCrypt 加密，同上为迁移脚本补充 |
| email | varchar(100) | 绑定邮箱，同上为迁移脚本补充 |
| avatar | varchar(255) | |
| sex | int | 1男 2女 |
| open_id | char(32) | 微信 openid，唯一索引（历史遗留，现在用户名密码注册的用户这里填随机值） |
| active_status | int，默认2 | 1在线 2离线 |
| last_opt_time | datetime(3) | 最后上下线时间 |
| ip_info | json | 注册/登录 IP 详情（调用淘宝IP库得到，含 isp 字段，**未对外暴露**，见8.6） |
| last_login_ip | varchar(45) | |
| item_id | bigint | 当前佩戴的徽章（关联 item_config.id） |
| status | int，默认0 | 0正常 1拉黑 |
| create_time / update_time | datetime(3) | |

索引：`uniq_open_id`、`uniq_name`、`idx_active_status_last_opt_time`（在线状态查询用）

#### user_friend（好友关系表）
单向关系，A加B为好友时插入 `(uid=A, friend_uid=B)`，互相都要插一条才算"互为好友"。`delete_status` 做逻辑删除。索引：`(uid, friend_uid)`。

#### user_apply（好友申请表）
| 字段 | 说明 |
|---|---|
| uid | 申请人 |
| target_id | 被申请人 |
| type | 1=加好友（预留扩展） |
| status | 1待审批 2同意 |
| read_status | 1未读 2已读 |
| msg | 申请留言 |

#### room / room_friend / room_group / group_member
| 表 | 关键字段 | 说明 |
|---|---|---|
| room | type(1群聊/2单聊)、active_time、last_msg_id、ext_json | 房间壳；`ext_json` 是个"万能字段"，群公告（notice/noticeUid/noticeTime/noticeReadUidList）就塞在 `room_group.ext_json` 里，没有走表结构变更 |
| room_friend | uid1/uid2（**强制按数值排序**，更小的存uid1）、room_key（`uid1_uid2` 拼接）、status | `room_key` 唯一索引保证两个用户之间只会有一个单聊房间；删好友后 status 置为禁用而不是删行 |
| room_group | room_id、name、avatar、ext_json | 群资料 |
| group_member | group_id、uid、role(1群主/2管理员/3普通成员)、nickname | 群内昵称单独存，不影响用户全局昵称 |

#### message / message_mark
| 表 | 关键字段 | 说明 |
|---|---|---|
| message | room_id、from_uid、sender_ip、content、reply_msg_id、type(1正常/2撤回)、extra(json) | `sender_ip` 落库原始IP，属地是**查询时**用 ip2region 现算的，不是存死的，所以历史消息也能受益于属地算法的修复 |
| message_mark | msg_id、uid、type(1点赞/2举报)、status(0正常/1取消) | |

#### contact（会话列表表）
每个用户对每个房间一行，`uniq_uid_room_id` 唯一索引。`read_time`/`active_time`/`last_msg_id` 决定未读数和会话排序。🟡 `pinned`/`muted` 字段同样是本次会话通过迁移脚本补充的，原始 SQL 里没有。

#### user_backpack / item_config（背包道具系统）
这是"改名卡"机制的核心，**没有一个专门的 `user_renamelog` 表**（用户原始任务描述里猜测的表名不存在）——改名次数 = 背包里状态为"待使用"(`status=0`) 的、`item_id` 指向"改名卡"配置的记录数。`idempotent` 字段是幂等号，配合 `uniq_idempotent` 唯一索引防止同一业务场景重复发放（比如本次新增的月度发卡任务，就是用 `itemId_3_uid_年月` 当幂等号）。

#### 其它表
`black`（IP/UID黑名单）、`role`/`user_role`（角色权限）、`user_emoji`（自定义表情）、`sensitive_word`（敏感词库）、`wx_msg`（微信对接消息）、`secure_invoke_record`（本地消息表，记录需要重试的异步操作——这是个"防丢失"兜底设计，🟢 属于比较讲究的工程实践，不少 AI 生成代码不会主动加这个）。

---

## 4. 核心功能实现原理

### 4.1 用户认证流程

```
注册:
  浏览器 ──POST /capi/user/public/register/email/code──▶ 后端
         发验证码到邮箱，Redis 存 "email:register:{email}" → 6位码，TTL 10分钟
  浏览器 ──POST /capi/user/public/register {username,name,password,email,emailCode}──▶ 后端
         校验验证码 → 校验用户名/昵称/邮箱唯一性 → BCrypt加密密码 → 写入 user 表（@Transactional）
         → 成功后删除 Redis 验证码

登录:
  浏览器 ──POST /capi/user/public/login {username,password}──▶ 后端
         查用户 → BCrypt.matches 校验密码 → 生成 JWT（仅含 uid + createTime，🔴 无 exp 过期时间）
         → Redis 记录 token（LoginService 管理） → 返回 token

后续请求:
  浏览器 每次请求带 Authorization: Bearer <token>
         → TokenInterceptor 拦截：路径含"/public/"则放行，否则解析 JWT + 查 Redis 校验有效性
         → 校验通过则把 uid 存入 RequestHolder（线程本地变量），Controller 里随时取当前登录用户

WebSocket 认证:
  浏览器建立 WS 连接 → 发送 type=AUTHORIZE 消息，携带 token（或连接时带 query 参数）
         → 服务端校验 token → 建立 uid ↔ Channel 的映射 → 后续才能收到推送
```

### 4.2 好友系统流程

```
A 搜索用户 ──GET /capi/user/public/search?keyword=B──▶ 模糊匹配 username/name

A 发起申请 ──POST /capi/user/friend/apply {targetUid:B, msg}──▶ 写入 user_apply(status=待审批)
                                                              → WS 推送 type=APPLY 给 B（未读数+1）

B 同意申请 ──PUT /capi/user/friend/apply {applyId}──▶ user_apply.status=同意
                                                    → 双向写入 user_friend (A→B 和 B→A 各一条)
                                                    → 创建/复用 room_friend（room_key=排序后的uid拼接，
                                                      天然防止重复建房）
                                                    → 双方会话列表(contact)各插入一条，指向新房间
```

🟡 这里"双向好友关系存两行"是常见做法，但意味着**任何好友相关的写操作都要记得同时维护两行**，AI 生成代码里这类"双向数据一致性"的地方最容易在后续加新功能时漏掉一边——这正是后面 8.4 节会提到的真实历史 bug（建群时联系人列表没刷新）的同类问题。

### 4.3 消息系统流程

```
发送消息:
  浏览器 ──POST /capi/chat/msg {roomId, content, contentType, quoteId}──▶ ChatController
  (限流: 5秒3次/30秒5次/60秒10次，按uid)
         → ChatServiceImpl.sendMsg() [@Transactional]
              1. 敏感词过滤、黑名单校验
              2. 落库 message 表（status=正常）
              3. 更新 room.active_time / room.last_msg_id
              4. 发布 MessageSendEvent（@TransactionalEventListener，事务提交后才真正执行）
         → 事件监听器异步：
              a) 通过 RocketMQ 投递到 chat_send_msg 主题
              b) WebSocket 服务消费/直接调用，把消息推送给房间内所有在线用户（type=MESSAGE）
              c) 更新发送者+接收者的 contact 表（写扩散：每人一条会话记录都要刷新 last_msg_id）
         → HTTP 响应直接返回这条消息体（发送者本地立即可见，不用等 WS 回环）

历史消息拉取:
  浏览器 ──GET /capi/chat/public/msg/page?roomId=&cursor=&pageSize=──▶ 游标分页（按 id 倒序）
         → 返回消息列表，每条消息的 sender_ip 现场用 ip2region 解析成属地文案

撤回/编辑/点赞:
  PUT /capi/chat/msg/recall|edit, PUT /capi/chat/msg/mark
         → 校验权限（2分钟内本人或管理员可撤回）→ 改 message.status/content → WS 广播变更
```

🔴 **关于"系统出小差"故障的根因**（本次会话实际修复过的真实生产事故）：`MessageSendEvent` 的监听器是 `@TransactionalEventListener(phase=BEFORE_COMMIT)`，里面调用 `RocketMQTemplate.send()` 是**同步阻塞**且**会抛异常**的调用。当服务器磁盘使用率过高触发 RocketMQ Broker 的磁盘保护、拒绝写入时，这个异常会一路往上抛，直接导致**发消息的整个 HTTP 请求失败**，用户看到的就是"系统出小差了"。这是典型的"为了保证强一致用了事务内同步发MQ，却没给外部依赖调用加超时和异常隔离"的设计缺陷。

### 4.4 实时消息推送（WebSocket）

```
连接管理:
  Netty WebSocket Server 监听 8090
  服务端维护 ONLINE_UID_MAP<uid, List<Channel>>  (支持一个用户多端同时在线)

心跳机制:
  IdleStateHandler(readerIdleTime=90s) —— 90秒没收到客户端任何消息（含心跳）就判定超时
  前端需要定期发 type=HEARTBEAT 维持连接（典型间隔 ~10秒，留足够余量）
  超时触发：服务端关闭连接 → 发布 UserOfflineEvent → 广播 type=ONLINE_OFFLINE_NOTIFY 通知所有人

消息分发:
  type=MESSAGE       新消息（房间内在线成员）
  type=RECALL        消息撤回
  type=MARK          点赞/举报变更
  type=APPLY         好友申请
  type=MEMBER_CHANGE 群成员变动
  type=BLACK         被拉黑通知
  type=INVALIDATE_TOKEN  token失效，强制重新登录

多端同步:
  同一个 uid 在 ONLINE_UID_MAP 里可能对应多个 Channel（比如同时开了两个浏览器标签）
  推送时遍历该 uid 下所有 Channel 逐一发送，做到多端状态一致
```

🔴 **关于"聊天框不刷新"bug 的根因**：上面这条服务端推送链路本身没问题，问题出在**前端接收后的状态更新**——`chat.ts` 的 `pushMsg()` 拿到 WS 推送的消息后，是对 `messageMap`（一个 `reactive(Map<roomId, Map<msgId, msg>>)`）里**内层 Map** 直接 `.set()`，没有触发外层 Map 的响应式信号在某些嵌套 computed 链路下可靠传播，导致当前打开的聊天框组件收不到更新通知（但侧边栏因为是更直接地读取 `messageMap`，反而能正常刷新）。修复方式是在内层 `.set()` 之后，对外层 `messageMap` 也重新 `.set()` 一次，强制触发顶层响应式信号。详见第 6.3 节。

### 4.5 改名卡系统（月限逻辑）

```
设计模型: 改名次数 = 背包(user_backpack)里 item_id=改名卡 且 status=待使用 的记录数
          不是 user 表上的一个计数字段！

注册时:   UserRegisterListener 监听 UserRegisterEvent → 发一张改名卡
          幂等号 = "{itemId}_1_{uid}"（IdempotentEnum.UID 类型），保证每个用户注册时只发一次

改名时:   UserServiceImpl.modifyName()
          → 查背包里第一张未使用的改名卡（getFirstValidItem）
          → 没有则报错"改名次数不够了，每月1日会自动补发一张改名卡，请耐心等待~"
          → 有则改 user.name，并把这张卡 status 置为已使用

🔴 历史 bug（本次已修复）: 全项目原本没有任何机制会再发新卡——注册送的那一张用掉之后，
   原提示语"等后续活动送改名卡哦"其实从来没有对应的"活动"逻辑（搜遍全项目也没有任何
   按活动/运营动作触发的改名卡发放代码），用户终身只有一次改名机会。
   修复方案：新增 RenameCardScheduler，@Scheduled(cron="0 0 3 1 * ?") 每月1号凌晨3点，
   给所有 status=正常 的用户发一张卡，幂等号 = "{itemId}_3_{uid}_{年月}"（新增的 YEAR_MONTH
   幂等类型），保证同一个月不会重复发，但每个月都能发新的一张。同时把误导性的"等活动送卡"
   提示语改成了准确描述实际机制的"每月1日自动补发"文案——项目里本来就没有"活动领取"这条
   业务逻辑，不存在需要删除的代码，只是这句 UI 文案本身就是错的，一并订正。
```

---

## 5. API 接口清单

> 鉴权约定：路径中第三段包含 `public` 的接口免登录（如 `/capi/user/public/xxx`），其余接口需在请求头携带 `Authorization: Bearer <JWT>`，由全局 `TokenInterceptor` 校验。

### 5.1 认证模块 `/capi/user/public`

| Method | Path | 请求 | 响应 | 限流 |
|---|---|---|---|---|
| POST | `/register/email/code` | `{email}` | - | 60s/3次/IP |
| POST | `/register` | `{username,name,password,email,emailCode}` | `{uid,username,name}` | 60s/3次/IP |
| POST | `/login` | `{username,password}` | `{token,uid,username,name,avatar}` | 60s/5次/IP |
| POST | `/email/login` | `{email,password}` | 同上 | 60s/5次/IP |
| POST | `/password/forgot/code` | `{email}` | - | 60s/3次/IP |
| POST | `/password/forgot/reset` | `{email,code,newPassword}` | - | 60s/5次/IP |
| GET | `/search?keyword=` | - | `[{uid,name,avatar}]` | - |

### 5.2 用户模块 `/capi/user`（需登录，除注明 public 外）

| Method | Path | 请求 | 响应 |
|---|---|---|---|
| GET | `/userInfo` | - | `UserInfoResp{id,name,avatar,sex,modifyNameChance,email}` |
| PUT | `/name` | `{name}` | - |
| PUT | `/avatar?avatar=` | - | - |
| PUT | `/password` | `{oldPassword,newPassword}` | - |
| PUT | `/statusMsg` | `{statusMsg}` | - |
| GET | `/badges` | - | `[BadgeResp]` |
| PUT | `/badge` | `{badgeId}` | - |
| POST | `/email/code` | `{email}` | - |
| PUT | `/email` | `{email,code}` | - |
| PUT | `/black` | `{targetUid}` | 管理员权限 |
| POST(public) | `/public/summary/userInfo/batch` | `{uidList}` | `[SummeryInfoDTO]`（含 locPlace 城市属地，见8.6） |

### 5.3 好友模块 `/capi/user/friend`（需登录）

| Method | Path | 请求 | 响应 |
|---|---|---|---|
| POST | `/apply` | `{targetUid,msg}` | - |
| PUT | `/apply` | `{applyId}` | - |
| GET | `/apply/page` | `{pageNo,pageSize}` | 申请列表 |
| GET | `/apply/unread` | - | `{unReadCount}` |
| DELETE | (根路径) | `{targetUid}` | - |
| GET | `/page` | 游标分页 | 好友列表 |
| PUT | `/remark?friendUid=&remark=` | - | - |

### 5.4 消息/会话模块 `/capi/chat`

| Method | Path | 请求 | 鉴权 | 限流 |
|---|---|---|---|---|
| GET | `/public/msg/page` | `roomId,cursor,pageSize` | public | - |
| POST | `/msg` | `{roomId,content,contentType,quoteId}` | 需登录 | 5s/3、30s/5、60s/10（按uid） |
| PUT | `/msg/mark` | `{msgId,markType,actType}` | 需登录 | 10s/5 |
| PUT | `/msg/recall` | `{msgId,roomId}` | 需登录 | 20s/3 |
| PUT | `/msg/edit` | `{msgId,roomId,content}` | 需登录 | 20s/3 |
| GET | `/msg/read` | `{msgIds}` | 需登录 | - |
| GET | `/public/contact/page` | 游标分页 | public | - |
| PUT | `/contact/pin?roomId=&pinned=` | - | 需登录 | - |
| PUT | `/contact/mute?roomId=&muted=` | - | 需登录 | - |
| DELETE | `/contact?roomId=` | - | 需登录 | - |

### 5.5 群组模块 `/capi/room`

| Method | Path | 请求 |
|---|---|---|
| POST | `/group` | `{name,members}` |
| GET | `/public/group/member/page` | `roomId,cursor,pageSize` |
| POST | `/group/member` | `{roomId,memberUids}` |
| DELETE | `/group/member` | `{roomId,memberUid}` |
| DELETE | `/group/member/exit` | `{roomId}` |
| PUT | `/group/admin` / DELETE `/group/admin` | `{roomId,memberUid}` |
| PUT | `/group/lord` | `{roomId,targetUid}` 转让群主 |
| PUT | `/group/notice` | `{roomId,notice}` |
| PUT | `/group/notice/read` | `{roomId}` |
| PUT | `/group/nickname` | `{roomId,nickname}` |
| GET | `/group/my-list` | - 我的群列表 |

### 5.6 其它

| 模块 | Path前缀 | 说明 |
|---|---|---|
| 文件上传 | `/capi/file/upload`、`/capi/oss/upload/url` | MultipartFile 或预签名URL两种方式 |
| AI助手/MCP | `/capi/mcp` | JSON-RPC 2.0 协议，method: initialize/tools/list/tools/call/ping |
| 微信对接 | `wx/portal/public` | 公众号回调验证 + 消息接收（可选功能，默认未启用） |

### 5.7 WebSocket 消息协议

连接：`wss://域名/websocket`（Caddy 转发到后端 8090 端口）。信封格式统一为 `{type, data}`。

**客户端→服务端**

| type | 名称 | data |
|---|---|---|
| 1 | LOGIN | 请求扫码登录二维码 |
| 2 | HEARTBEAT | 心跳，无 data |
| 3 | AUTHORIZE | `{token}` |

**服务端→客户端**

| type | 名称 | 说明 |
|---|---|---|
| 1 | LOGIN_URL | 二维码地址 |
| 2 | LOGIN_SCAN_SUCCESS | 已扫码待确认 |
| 3 | LOGIN_SUCCESS | `{uid,avatar,token,name,power}` |
| 4 | MESSAGE | 完整消息体（新消息推送） |
| 5 | ONLINE_OFFLINE_NOTIFY | `{changeList:[{uid,activeStatus,...}], onlineNum}` |
| 6 | INVALIDATE_TOKEN | 强制下线重新登录 |
| 7 | BLACK | 被拉黑通知 |
| 8 | MARK | 点赞/举报变更 |
| 9 | RECALL | 消息撤回 |
| 10 | APPLY | 好友申请（含未读数） |
| 11 | MEMBER_CHANGE | 群成员变动 |

---

## 6. 状态管理设计

前端用 Pinia，按"领域"拆 store，全部用 Composition API 风格（`defineStore(name, () => {...})`）而不是 Options 风格。

| Store | 文件 | 管理的状态 |
|---|---|---|
| chat | `stores/chat.ts` | 消息、会话列表、各房间加载状态——**全项目最复杂的 store** |
| user | `stores/user.ts` | 当前登录用户信息、token、是否已登录 |
| global | `stores/global.ts` | 当前选中会话(`currentSession`)、各类弹窗开关、未读计数 |
| group | `stores/group.ts` | 当前群详情、群成员列表、群公告 |
| contacts | `stores/contacts.ts` | 联系人列表、好友申请列表 |
| cached | `stores/cached.ts` | 按 uid/badgeId 缓存的用户信息/徽章信息，避免到处重复发请求 |
| ws | `stores/ws.ts` | 扫码登录相关的临时状态 |
| emoji / preview / downloadQuenu | — | 表情、图片预览、下载队列，相对独立 |

### 6.1 messageMap 的嵌套结构

```ts
// chat.ts
const messageMap = reactive<Map<number, Map<number, MessageType>>>(...)
//                          外层: roomId        内层: msgId → 消息体
const messageOptions = reactive<Map<number, {isLast, isLoading, cursor}>>(...)
//                          每个房间独立维护自己的分页/加载状态
const replyMapping = reactive<Map<number, Map<number, number[]>>>(...)
//                          房间 → (被回复的msgId → 引用它的msgId列表)，用于"被引用"角标
```

每个会话室的消息互相隔离存放在各自的内层 Map 里，理论上能避免"切换会话时消息串台"的问题——但**理论上能避免，不代表实现时真的处理对了**，见下面的响应式陷阱。

### 6.2 currentRoomId 的"假 undefined"陷阱 🔴

```ts
// global.ts
const currentSession = reactive<{ roomId: number; type: RoomTypeEnum }>({
  roomId: 1,        // ⚠️ 硬编码默认值，不是 undefined / null / 0
  type: RoomTypeEnum.Group,
})
// chat.ts
const currentRoomId = computed(() => globalStore.currentSession?.roomId)
```

这是本次会话里实际踩到的一个坑：修"新用户一直转圈"的 bug时，第一直觉是判断 `currentRoomId === undefined` 来识别"还没选中任何会话"，结果完全没生效——因为 `currentSession` 一开始就被硬编码成了 `{roomId: 1}`，**根本不存在 undefined 的状态**，哪怕是全新注册、零会话的用户，`currentRoomId.value` 读出来也是 `1`（一个该用户压根没有权限访问、甚至可能不存在的房间号）。正确的"是否有真实会话"判断应该是查 `sessionList.length > 0`，而不是看 `currentRoomId` 是否有值。

🟡 **这是 AI 生成代码里极其典型的一类 bug**：写状态初始值时随手填了个"看起来无害"的占位数字（这里甚至凑巧是某个真实存在的房间ID，更具迷惑性），而不是用 `undefined`/`null` 显式表达"尚未设置"，导致后续所有"是否已选中"的判断逻辑全部建立在错误前提上。

### 6.3 Map 响应式更新的传播链路 🟡

```ts
const currentMessageMap = computed({
  get: () => messageMap.get(currentRoomId.value as number),  // computed #1
  ...
})
const chatMessageList = computed(() => [...(currentMessageMap.value?.values() || [])])  // computed #2，依赖 computed #1

// pushMsg() 收到 WS 推送时：
const current = messageMap.get(msg.message.roomId)
current?.set(msg.message.id, msg)          // 只在内层 Map 上原地 set
messageMap.set(msg.message.roomId, current) // 本次修复新增：强制对外层 Map 也 set 一次
```

Vue 3 的 `reactive(Map)` 理论上会让 `.get()` 取出的嵌套对象也具备响应式（深层代理），但在"computed 依赖另一个 computed，而最内层只对三级嵌套 Map 做原地 mutate"这种链路下，依赖收集在实践中并不总是可靠触发——具体表现就是**侧边栏会话预览能实时刷新，但当前打开的聊天框不会**（两者读取 messageMap 的层级深度不同）。这是本次会话修复的 bug #1，修复方式很朴素：操作完内层 Map 后，对外层 Map 也显式 `.set()` 一次，相当于人为补一个"我确实变了"的信号，成本很低且不会有副作用。

🟡 **给重构的启示**：如果你打算用 TypeScript+Pinia 重写这部分，建议放弃"嵌套 Map 套 Map"的结构，换成更扁平的设计（比如 `messagesByRoom: Record<roomId, MessageType[]>` + 单独的 `Map<msgId, MessageType>` 全局索引，或者直接上 [Vue Query](https://tanstack.com/query) / 类似的服务端状态管理库），减少手写响应式追踪链路的心智负担。

---

## 7. 部署架构

### 7.1 Docker Compose 服务拓扑

```
mysql (8.0)         健康检查通过后 backend 才启动
redis (7-alpine)    需密码认证，开 appendonly 持久化
minio               S3兼容对象存储，minio-init 一次性创建桶+设公开读
rocketmq-namesrv    服务发现
rocketmq-broker     消息存储，限内存350M
backend             Spring Boot，依赖以上四者健康，限内存500M
web                 多阶段构建的 Caddy 镜像（前端静态资源已打进镜像）
caddy               可选独立反代（profile: standalone-caddy，默认不启用，因为 web 镜像自带 Caddy）
```

依赖关系：`backend` 等 `mysql`/`redis`/`minio`/`rocketmq-namesrv` 全部 healthy 才启动；`web` 对外提供唯一入口。

### 7.2 网络与端口

| 服务 | 容器内端口 | 对外暴露 |
|---|---|---|
| web (Caddy) | 80/443 | 是，唯一公网入口 |
| backend | 8080 (REST) / 8090 (WebSocket) | 否，仅容器网络内，由 web 反代 |
| mysql | 3306 | 否 |
| redis | 6379 | 否 |
| minio | 9000 (API) / 9001 (Console) | 视 Caddyfile 配置而定，通常给单独的 MinIO 子域名暴露 |
| rocketmq-namesrv | 9876 | 否 |

### 7.3 持久化卷

`mysql-data`、`redis-data`、`minio-data`、`rocketmq-store`、`rocketmq-logs`、`caddy-data`、`caddy-config` —— 全部走具名 volume，不是 bind mount，重新 `docker compose up` 不会丢数据。

🟡 **运维提示**（本次会话踩过的坑）：`rocketmq-logs` 这个卷只保存 broker 自己的运行日志，**RocketMQ 客户端自身**在 backend 容器内还会写一份不受这套 logback 滚动配置约束的日志（`/root/logs/rocketmqlogs/rocketmq_client.log`），如果不重视会一直增长，曾经把磁盘写满导致整个应用故障（详见 8.4 节）。

### 7.4 环境变量清单（`.env.example`）

| 变量 | 用途 |
|---|---|
| `SITE_DOMAIN` / `SITE_ORIGIN` | 主域名 / CORS基准URL |
| `MINIO_ENDPOINT` / `MINIO_DOMAIN` / `MINIO_CONSOLE_DOMAIN` | MinIO 对外地址 |
| `MYSQL_ROOT_PASSWORD` / `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` | 数据库凭据 |
| `REDIS_PASSWORD` | Redis 认证 |
| `JWT_SECRET` | 🔴 Token 签名密钥，务必修改默认值 |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET` | 对象存储凭据 |
| `DEEPSEEK_ENABLED` / `DEEPSEEK_KEY` | AI 助手开关与密钥 |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USER` / `MAIL_PASSWORD` / `MAIL_FROM` | 注册/找回密码邮件发送（QQ邮箱SMTP） |
| `WX_MP_*` | 微信公众号对接（默认禁用占位值） |
| `JAVA_TOOL_OPTIONS` | JVM 堆大小等调优参数，适配小内存云主机 |

---

## 8. AI 生成代码的特征与陷阱

> 总体评价：这份代码**比一般印象中"AI随手糊的代码"质量要高**——分层清晰、批量查询意识到位、SQL 注入/XSS 防护基本到位、验证码生命周期管理正确。真正的问题集中在**边界状态的初始值设计**（导致 6 个真实 bug）和**少数安全配置疏漏**，而不是大面积的低级错误。

### 8.1 🔴 JWT Token 永不过期

`JwtUtils.java` 生成的 Token 只塞了 `uid` 和 `createTime` 两个 claim，**没有设置 `exp` 过期声明**。意味着一个 Token 一旦泄露，永久有效（除非后端主动在 Redis 里把它标记失效）。这是最值得优先修的安全问题——加一个 7天/30天的 `exp`，配合现有的 Redis token 记录机制做"续期"或"强制下线"，改动量不大。

### 8.2 🟡 大量 `catch (e: any)` 与少量 `e.printStackTrace()`

前端 46 处 `: any`/`as any`，大部分集中在 `catch (e: any)` 这种防御性写法和第三方库回调签名（语音录制 `onEnd((audioFile: any) => ...)`），属于"图省事但不算错"。后端有 3 处用 `e.printStackTrace()` 替代正经日志（`IpServiceImpl.java`、`MsgHandler.java`），属于非核心路径（IP详情查询、微信消息处理），不影响主链路，但排查问题时日志会找不到。

### 8.3 🟢 没有 N+1 查询，批量加载意识到位

群成员列表、用户信息批量查询都走的是先收集 ID 列表再一次性 `IN` 查询，WebSocket 广播也是遍历内存里的在线连接表而不是每个成员单独查一次 DB——这点出乎意料地规范，说明生成代码时给了足够具体的"批量查询"指引或者底层框架（MyBatis-Plus + 自带的批量 DAO 方法）天然引导出了这个习惯。

### 8.4 🔴 本次会话实际修复的 6 个 bug（边界状态/默认值类问题占主流）

| # | bug | 根因 | 类别 |
|---|---|---|---|
| 1 | 聊天框收到新消息不刷新，侧边栏却正常 | `pushMsg` 只对嵌套 Map 内层原地 `.set()`，多层 computed 链路下响应式信号传播不可靠 | Vue响应式陷阱 |
| 2 | 新用户一直显示"消息加载中" | `currentSession` 默认值硬编码成 `{roomId:1}` 而非 `undefined`，用"是否有值"判断"是否选中会话"前提就错了 | 硬编码默认值 |
| 3 | IP属地显示运营商名字 | ip2region 离线库对手机数据/数据中心IP段精度不足，"城市"槎位本身就填的是运营商名字，不是代码取错字段 | 第三方数据质量盲区 |
| 4 | 注册页验证码行被裁切且无法滚动 | 外层容器 `overflow:hidden` + 内层新增字段撑高表单后无滚动兜底 | 布局未考虑内容动态增长 |
| 5 | 改名卡终身只有一次 | 注册送一张卡后，"等活动补卡"的提示语没有对应实现，零定时任务/零补发逻辑（且全项目搜索确认不存在任何"活动领取"业务代码，纯粹是文案写错） | 功能描述与实现不匹配（典型的"AI把UI文案写出来了，但忘了配套实现"），修复时已将文案订正为"每月1日自动补发" |
| 6 | 浏览器标签图标是占位图 | `favicon.ico` 是模板自带占位文件，从未替换；`logo.jpeg` 实际是被遗留的开源模板（MallChat）品牌图，对不上当前产品名 | 模板残留未清理 |

🟡 这 6 个问题里，#1/#2 是**典型的"状态初始值/响应式假设"类 bug**，#3 是**第三方数据源精度认知偏差**，#5 是**口头承诺（UI文案）和实际代码不同步**——这三类恰好是 AI 生成代码最容易出问题、又最不容易在"看起来能跑"的阶段被发现的地方，因为它们都不会报错，只是在特定输入/特定时间点表现不对。

### 8.5 🟢 验证码生命周期管理正确

注册/绑定邮箱/找回密码三条验证码链路，都是"校验通过后显式 `Redis.del()`，同时设置 TTL 兜底"的双重防护，没有"验证一次但码还留着可以反复用"的漏洞。值得在重构时原样保留这个模式。

### 8.6 🟡 `user.ip_info`（含运营商信息）字段定义了但从未对外暴露

`User` 实体上有一个 `ipInfo` 字段，调用淘宝的 IP 查询接口拿到详细信息（含 `isp` 运营商字段），整个字段链路读写都很完整，但翻遍前端代码**没有任何接口把这个字段吐给前端**——是一段"做了一半就放着"的功能，不算 bug，但属于死代码/未完成特性，重构时要么补完（在用户资料页展示注册IP溯源信息，常见于风控场景），要么直接删掉这条链路减少维护负担。

### 8.7 🟢 SQL 注入 / XSS 防护到位

全部 DB 访问走 MyBatis-Plus 的参数化 Wrapper API，没找到任何字符串拼接 SQL；前端没有一处 `v-html` 渲染用户输入内容，Vue 默认的 `{{ }}` 插值天然转义，组合起来基本杜绝了存储型 XSS。

---

## 9. 转化建议：如何把它变成"你自己的"项目

### 9.1 建议保留（工具类/基础设施，重写性价比低）

- **ip2region 离线属地解析**：纯内存查询、无外部依赖，直接复用，只需要按本文档 8.4 的方式做好"过滤运营商关键词"的防御。
- **MyBatis-Plus + 批量查询习惯**：这套数据访问层写得规范，值得作为后续新功能的参照模板。
- **背包道具系统（user_backpack + item_config）**：这是一个相当通用的"虚拟物品发放/消耗"抽象，改名卡、徽章都复用了它，未来加新的"权益类"功能（比如"邀请卡""加急卡"）可以直接套用这套模型，不需要每次都新建一张计数字段表。
- **本地消息表 `secure_invoke_record`**（如果实际在用）：这是个成熟的"异步操作可靠重试"模式，值得学习。
- **Caddy + Docker Compose 部署骨架**：服务拓扑、健康检查依赖关系、卷的划分都比较合理，可以直接沿用，重点改环境变量和密钥。

### 9.2 建议必须重写

- **JWT 鉴权**：加上过期时间 + 续期/强制下线机制，这是上线前的硬性要求。
- **前端消息状态管理（`messageMap` 嵌套 Map 结构）**：如本文 6.3 节分析，这套手写响应式追踪的心智负担和踩坑概率都偏高，建议换成更扁平的数据结构，或引入专门的服务端状态库（如 Vue Query / SWR 思路）做缓存和失效管理，而不是手动维护多层 `reactive(Map)`。
- **WebSocket 单体耦合**：当前 WebSocket Server 和 REST API 跑在同一个 JVM 进程里，意味着不能独立扩容/独立重启。如果你的重构目标包含"支持更大并发"，这一块需要拆成独立服务，并把"在线用户表"迁到 Redis（而不是进程内 Map），否则多实例部署时用户会被随机分配到某一台，互相推送不到消息。
- **所有状态初始值**：全面审查前端 store 里的"占位默认值"（参考 6.2 节的 `roomId:1` 教训），统一改成显式的 `undefined`/`null`，并在所有"是否已选中/是否已加载"的判断处使用这些显式值，而不是隐式约定某个数字代表"空"。

### 9.3 建议的重构顺序

1. **先打地基**：把当前线上真实 DB schema dump 出来作为唯一基准（解决 3.0 节提到的脱节问题），同时给 JWT 加过期时间——这两步成本低、收益高，且不影响后续重构的方向选择。
2. **重写前端状态管理**：在还没有引入更多业务复杂度之前，把 `chat.ts` 的嵌套 Map 结构换掉，是性价比最高的重构窗口期。
3. **拆分 WebSocket 服务**（如果有扩容需求）：在状态管理理清楚之后再做，否则两边同时改容易互相干扰排查。
4. **补全/收敛半成品功能**：处理 8.6 提到的 `ip_info`/运营商字段这类"做了一半"的功能，决定补完还是删除。
5. **MinIO 对象存储**：当前已经在用（头像/语音/图片上传），重构时建议顺手补上"上传前校验文件类型/大小"和"定期清理无主对象"（比如撤回的消息、被删除会话里上传过的图片，目前应该没有清理机制——值得专门排查一次）。

### 9.4 需要补充的测试覆盖

| 优先级 | 测试点 |
|---|---|
| 高 | 好友双向关系一致性（A删B好友后，B视角下A是否也正确变成"非好友"） |
| 高 | 消息发送在 RocketMQ 不可用/超时场景下的降级行为（不应该让整个发消息请求失败，参考 4.3 节根因分析） |
| 高 | 新用户全链路（零会话/零好友状态下，所有页面是否都有正确的空状态，而不是隐式依赖某个"假装有值"的默认值） |
| 中 | 群聊权限矩阵（普通成员/管理员/群主分别能做什么，覆盖"删人""转让群主""解散群"等边界操作） |
| 中 | WebSocket 重连后的状态一致性（断网重连后，是否会补拉断线期间错过的消息） |
| 低 | IP属地解析对已知运营商IP段/数据中心IP段的回归测试（防止 8.4 #3 的问题复发） |
