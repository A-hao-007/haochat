# HaoChat 聊天系统 - 技术文档

> 版本: 2.0 | 更新日期: 2026-06-20 | 部署地址: http://47.96.43.107

---

## 一、系统架构

```
浏览器 (Vue 3 SPA)
    │
    ├── HTTP/HTTPS → Nginx (80/443)
    │       ├── /            → 静态文件 (/usr/share/nginx/html/)
    │       ├── /capi/       → Spring Boot (127.0.0.1:8080)
    │       └── /websocket   → Netty (127.0.0.1:8090)
    │
    ├── Spring Boot 2.6.7
    │       ├── MyBatis-Plus → MySQL 8.0
    │       ├── Redisson     → Redis 6.x
    │       └── RocketMQ     → 消息推送
    │
    └── Netty WebSocket Server (8090)
            └── 实时消息/在线状态/输入提示
```

### 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + Pinia + SCSS |
| 后端 | Spring Boot 2.6.7 + MyBatis-Plus + Netty + Redis + RocketMQ |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 6.x |
| 消息队列 | RocketMQ 4.9.3 |
| 对象存储 | MinIO (可选) |

---

## 二、功能清单（47项全部实现）

### 2.1 账号系统 ✅
| 功能 | API | 说明 |
|------|-----|------|
| 注册 | `POST /capi/user/public/register` | 用户名3-20位(字母数字下划线)，密码6-50位，BCrypt加密 |
| 登录 | `POST /capi/user/public/login` | 返回 JWT Token(5天有效期，Redis管理) |
| 修改密码 | `PUT /capi/user/password` | 需旧密码验证 |
| 修改昵称 | `PUT /capi/user/name` | 剩余次数限制 |
| 更换头像 | `PUT /capi/user/avatar?avatar=URL` | 点击设置面板头像更换 |
| 个性签名 | `PUT /capi/user/statusMsg` | DB字段 `user.status_msg` |
| 在线状态 | WebSocket 自动同步 | active_status: 1在线 2离线 |

### 2.2 好友/联系人 ✅
| 功能 | API | 说明 |
|------|-----|------|
| 搜索用户 | `GET /capi/user/public/search?keyword=xx` | 模糊匹配username和name |
| 发送好友请求 | `POST /capi/user/friend/apply` | targetUid + msg |
| 接受请求 | `PUT /capi/user/friend/apply` | applyId |
| 删除好友 | `DELETE /capi/user/friend` | targetUid |
| 好友列表 | `GET /capi/user/friend/page` | 游标分页 |
| **好友备注** | `PUT /capi/user/friend/remark` | DB字段 `user_friend.remark` |

### 2.3 聊天消息 ✅
| 功能 | API | 说明 |
|------|-----|------|
| 发送消息 | `POST /capi/chat/msg` | 限流: 3/5s, 5/30s, 10/60s |
| 获取消息 | `GET /capi/chat/public/msg/page` | 游标分页 |
| 撤回消息 | `PUT /capi/chat/msg/recall` | 2分钟内或管理员 |
| 标记消息 | `PUT /capi/chat/msg/mark` | 点赞/倒赞 |
| 已读/未读 | `GET /capi/chat/msg/read` | 消息级已读统计 |
| 消息搜索 | 前端本地搜索 | 顶部搜索栏 |
| 消息引用 | MsgInput 支持 | 回复引用原消息 |
| 输入提示 | WebSocket | "xxx正在输入..." |
| **消息提示音** | Web Audio API | 收到消息播放短促提示音 |

### 2.4 会话管理 ✅
| 功能 | API | 说明 |
|------|-----|------|
| 会话列表 | `GET /capi/chat/public/contact/page` | 按活跃时间排序 |
| **会话置顶** | `PUT /capi/chat/contact/pin` | `contact.pinned` 字段 |
| **会话免打扰** | `PUT /capi/chat/contact/mute` | `contact.muted` 字段 |
| **删除会话** | `DELETE /capi/chat/contact` | 仅删除当前用户的 |

### 2.5 群聊功能 ✅
| 功能 | API | 说明 |
|------|-----|------|
| 创建群组 | `POST /capi/room/group` | uidList |
| 群成员列表 | `GET /capi/room/public/group/member/page` | 在线优先排序 |
| 邀请成员 | `POST /capi/room/group/member` | roomId + uidList |
| 移除成员 | `DELETE /capi/room/group/member` | roomId + uid |
| 添加管理 | `PUT /capi/room/group/admin` | 群主可操作 |
| 退出群聊 | `DELETE /capi/room/group/member/exit` | |
| 群公告 | 前端展示组件 | GroupAnnouncement.vue |
| 群成员搜索 | 前端过滤 | |

### 2.6 AI 助手 ✅
| 功能 | 说明 |
|------|------|
| 内置智能回复 | 问候/时间/笑话/帮助，无需API Key |
| OpenAI 升级 | 配置 `haochat.chatgpt.key` 后自动切换GPT |
| 触发方式 | 在任意聊天窗口发送文字消息即可 |

### 2.7 界面体验 ✅
| 功能 | 说明 |
|------|------|
| 暗黑/明亮主题 | CSS变量体系，一键切换，持久化存储 |
| 骨架屏 | 会话列表加载占位 |
| 玻璃态效果 | 主界面 backdrop-filter: blur |
| 消息动画 | fadeInUp 淡入 |
| 响应式 | 桌面/平板/手机适配 |
| 右键菜单 | 会话列表右键置顶/免打扰/删除 |

### 2.8 通知提醒 ✅
| 功能 | 说明 |
|------|------|
| 消息提示音 | Web Audio API 生成 |
| 桌面通知 | Notification API (已有) |
| 标题闪烁 | shakeTitle.ts (已有) |
| 未读角标 | el-badge 组件 |

### 2.9 安全防护 ✅
| 防护 | 说明 |
|------|------|
| 密码加密 | BCrypt |
| JWT认证 | Bearer Token，5天过期，Redis管理 |
| 接口限流 | Nginx 10r/s，登录5r/min，注册3/min |
| 敏感词过滤 | AC自动机 + DFA 双引擎 |
| 黑名单 | UID + IP 双重拦截 |
| SQL注入 | MyBatis-Plus 参数化查询 |
| 输入校验 | JSR-303 @Valid |
| 安全响应头 | X-Content-Type, X-Frame, X-XSS, Referrer-Policy |
| 请求体限制 | API 10MB，登录/注册 1KB |

---

## 三、数据库表结构（新增字段）

```sql
-- user 表新增
ALTER TABLE user ADD COLUMN username VARCHAR(50) UNIQUE NOT NULL;
ALTER TABLE user ADD COLUMN password VARCHAR(200);
ALTER TABLE user ADD COLUMN status_msg VARCHAR(100);

-- contact 表新增
ALTER TABLE contact ADD COLUMN pinned TINYINT DEFAULT 0;
ALTER TABLE contact ADD COLUMN muted TINYINT DEFAULT 0;

-- user_friend 表新增
ALTER TABLE user_friend ADD COLUMN remark VARCHAR(50);
```

---

## 四、部署指南

### 依赖服务启动
```bash
systemctl start mysqld
systemctl start redis
redis-cli CONFIG SET requirepass 123456

# RocketMQ
export ROCKETMQ_HOME=/opt/rocketmq
cd /opt/rocketmq && nohup java -cp "lib/*" -Drocketmq.home.dir=/opt/rocketmq org.apache.rocketmq.namesrv.NamesrvStartup &

# 后端
cd /root/HaoChat
nohup java -jar haochat-chat-server/target/haochat-chat-server-1.0-SNAPSHOT.jar > /tmp/haochat.log 2>&1 &

# 前端（构建）
cd /root/HaoChatWeb
npx vite build --config ./config/vite.config.prod.ts
cp -r dist/* /usr/share/nginx/html/
nginx -s reload
```

### 配置文件位置
| 文件 | 说明 |
|------|------|
| `/root/HaoChat/.../application.yml` | Spring Boot 主配置 |
| `/root/HaoChat/.../application-test.properties` | 数据源/Redis/RocketMQ 配置 |
| `/etc/nginx/nginx.conf` | Nginx 配置 |
| `/root/HaoChatWeb/.env.production` | 前端环境变量 |

---

## 五、目录结构

```
/root/
├── HaoChat/                     # 后端 Java 项目
│   ├── pom.xml                  # Maven 父 POM
│   ├── haochat-chat-server/     # 主服务模块
│   │   └── src/main/java/com/ahao/haochat/
│   │       ├── common/
│   │       │   ├── chat/        # 聊天域（消息/房间/会话）
│   │       │   │   ├── controller/  # REST 控制器
│   │       │   │   ├── service/     # 业务服务
│   │       │   │   ├── dao/         # 数据访问
│   │       │   │   └── domain/      # 实体/DTO/枚举
│   │       │   ├── chatai/      # AI 助手
│   │       │   │   └── handler/     # BuiltinChatAIHandler
│   │       │   ├── user/        # 用户域
│   │       │   ├── websocket/   # Netty WebSocket
│   │       │   └── common/      # 基础设施
│   │       └── resources/
│   │           └── mapper/      # MyBatis XML
│   └── haochat-tools/           # 工具模块
│
├── HaoChatWeb/                  # 前端 Vue 3 项目
│   ├── src/
│   │   ├── views/
│   │   │   ├── Login/           # 登录/注册页
│   │   │   └── Home/
│   │   │       ├── Chat/        # 聊天界面
│   │   │       │   └── components/
│   │   │       │       ├── SideBar/      # 会话列表
│   │   │       │       ├── ChatList/     # 消息列表
│   │   │       │       ├── ChatBox/      # 输入区域
│   │   │       │       └── UserList/     # 群成员
│   │   │       ├── Contacts/    # 联系人页
│   │   │       └── components/
│   │   │           └── ToolBar/ # 侧边工具栏
│   │   ├── components/          # 共享组件
│   │   ├── stores/              # Pinia 状态管理
│   │   ├── services/            # API 层
│   │   ├── hooks/               # Composables
│   │   └── utils/               # 工具函数
│   └── config/                  # Vite 配置
│
├── /etc/nginx/nginx.conf        # Nginx 配置
└── /usr/share/nginx/html/       # 前端静态文件
```

---

## 六、关键 API 速查

### 认证
```
POST /capi/user/public/register  { username, name, password }
POST /capi/user/public/login     { username, password } → { token, uid, name, avatar }
```

### 联系人
```
GET  /capi/user/public/search?keyword=xxx      → [{ uid, name, avatar }]
POST /capi/user/friend/apply                    { targetUid, msg }
PUT  /capi/user/friend/remark?friendUid=&remark=
```

### 会话
```
PUT    /capi/chat/contact/pin?roomId=&pinned=true|false
PUT    /capi/chat/contact/mute?roomId=&muted=true|false
DELETE /capi/chat/contact?roomId=
```

### 消息
```
POST /capi/chat/msg          { roomId, msgType, body: { content, reply } }
GET  /capi/chat/public/msg/page?roomId=&cursor=&pageSize=
```

---

## 七、维护指南

### 添加新功能步骤
1. **数据库**: ALTER TABLE 添加字段
2. **实体**: 添加 @TableField 字段
3. **DAO**: 如需自定义查询，添加方法
4. **Service**: 接口 + 实现
5. **Controller**: 添加端点（public路径无需认证）
6. **前端**: urls.ts → apis.ts → 组件

### 日志位置
- 后端: `/tmp/haochat.log`
- Nginx: `/var/log/nginx/access.log`, `error.log`
- RocketMQ: `/root/logs/rocketmqlogs/`

### 常见问题
1. **二维码不显示** → 检查后端端口 8080/8090 是否监听
2. **消息发不出** → 检查 RocketMQ 9876 端口
3. **刷新掉登录** → 检查 localStorage 中 TOKEN 是否存在
4. **AI 不回** → 内置 AI 始终可用；GPT 需配置真实 Key

---

## 八、待扩展方向
- [ ] HTTPS SSL 证书
- [ ] 文件上传（MinIO已部署）
- [ ] 语音/视频通话 (WebRTC)
- [ ] 消息已读回执优化
- [ ] 朋友圈/动态
- [ ] 第三方登录 (GitHub/Google)
