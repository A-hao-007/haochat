# HaoChat - 实时聊天应用

基于 Spring Boot + Vue 3 的实时聊天应用，支持文字、图片、文件、语音、视频等多种消息类型。

## 技术栈

### 后端 (haochat-server)
- **框架**: Spring Boot 2.6.7
- **语言**: Java 17
- **数据库**: MySQL 8.0
- **缓存**: Redis
- **消息队列**: RocketMQ
- **对象存储**: MinIO
- **AI**: DeepSeek API
- **实时通信**: Netty WebSocket

### 前端 (haochat-web)
- **框架**: Vue 3.3 + TypeScript
- **构建**: Vite 4
- **UI**: Element Plus
- **HTTP**: Alova
- **状态管理**: Pinia

## 快速开始

### 1. 环境要求
- JDK 17+
- Node.js 22（与前端 Docker 构建保持一致）
- pnpm 9.15.9（见 `haochat-web/package.json` 的 `packageManager`）
- MySQL 8.0
- Redis
- MinIO
- RocketMQ (可选)

### 2. 准备运行环境

完整环境的配置、数据库初始化和部署步骤见[部署文档](docs/deployment/oracle-free-vm.md)。生产发布与回滚见[发布流程](deploy/RELEASE_PROCESS.md)。

当前完整的后端环境变量配置位于 `haochat-server/haochat-chat-server/src/main/resources/application-prod.properties`。本地运行时也必须配置数据库、Redis、MinIO 等服务的地址与凭据，并显式选择对应 profile。根目录 `.env` 由 Docker Compose 读取，直接运行 Java 时需要另行设置进程环境变量。

`haochat-server/docs/config-templates/application-test.properties.example` 是旧版配置模板，不能仅复制它就完成当前后端配置；新 clone 中也没有默认 `application.yml`。请勿覆盖并提交带有真实凭据的配置文件。

### 3. 构建和启动后端

从聚合项目构建，确保内部依赖模块一起参与构建：

```bash
cd haochat-server
./mvnw -pl haochat-chat-server -am package -DskipTests
# 配置好上述服务的环境变量后运行：
java -jar haochat-chat-server/target/haochat-chat-server-1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

Windows PowerShell 使用 `./mvnw.cmd` 替代 `./mvnw`。构建命令会跳过测试，不代表测试已通过。

### 4. 启动前端

在 `haochat-web/.env.development.local` 中设置自己的后端地址，覆盖仓库中的开发环境默认地址：

```dotenv
VITE_API_PREFIX=http://localhost:8080
VITE_WS_URL=ws://localhost:8090
```

另开终端，从仓库根目录运行：

```bash
cd haochat-web
corepack enable
pnpm install --frozen-lockfile
pnpm dev
```

开发服务器端口为 `9988`。如使用 `pnpm dev:https`，还需配置可信的开发证书，并为 WebSocket 配置可用的 `wss://` 地址。

### 5. 前端验证

在 `haochat-web` 目录运行：

```bash
pnpm test         # 通知模块回归测试，无需后端服务
pnpm type-check   # TypeScript / Vue 类型检查
pnpm build-only   # 生产资源构建
```

通知回归测试覆盖不支持 Notification API、授权成功/拒绝、权限请求失败和通知创建失败，确保可选的桌面通知不会中断消息处理。

## 功能特性
- 多类型消息：文字、图片、文件、语音、视频、表情
- AI 智能助手（DeepSeek）
- 群聊 & 私聊
- 消息已读/未读
- 消息撤回、回复、@提及
- 文件上传（MinIO OSS）
- 暗黑模式
- 响应式设计

## License
MIT
