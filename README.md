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
- Node.js 18+
- MySQL 8.0
- Redis
- MinIO
- RocketMQ (可选)

### 2. 配置文件
复制配置模板并填入真实值：
```bash
cp haochat-server/docs/config-templates/application-test.properties.example \
   haochat-server/haochat-chat-server/src/main/resources/application-test.properties
```
编辑配置文件，将 `YOUR_*` 占位符替换为真实值。

### 3. 启动后端
```bash
cd haochat-server/haochat-chat-server
mvn spring-boot:run
```

### 4. 启动前端
```bash
cd haochat-web
npm install
npm run dev
```

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
