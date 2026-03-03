# OpenClaw WeCom 本地中间层

本地中间层客户端，用于将企业微信机器人的请求通过 WebSocket 隧道转发到本地运行的 OpenClaw。

## 架构说明

```
企业微信服务器 → 公网服务器 → [WebSocket 隧道] → 本地中间层 → 本地 OpenClaw
```

由于本地 OpenClaw 无法直接从公网访问，本地中间层主动与公网服务器建立 WebSocket 连接，服务器收到企业微信消息后通过该连接转发到本地。

## 快速开始

### 1. 安装依赖

```bash
npm install
```

### 2. 配置

复制配置示例文件：

```bash
cp config.example.json config.json
```

编辑 `config.json`：

```json
{
  "server": {
    "url": "wss://your-server.com/ws/relay",
    "reconnectInterval": 5000,
    "maxReconnectAttempts": 0
  },
  "auth": {
    "clientId": "openclaw-local",
    "authToken": "your-auth-token-here"
  },
  "openclaw": {
    "baseUrl": "http://localhost:3000",
    "webhookPath": "/webhooks/wecom",
    "timeout": 60000
  },
  "heartbeat": {
    "interval": 30000,
    "timeout": 10000
  }
}
```

### 3. 运行

开发模式：
```bash
npm run dev
```

生产模式：
```bash
npm run build
npm start
```

## 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `server.url` | 公网服务器 WebSocket 地址 | `ws://localhost:8080/ws/relay` |
| `server.reconnectInterval` | 重连间隔（毫秒） | `5000` |
| `server.maxReconnectAttempts` | 最大重连次数，0 表示无限 | `0` |
| `auth.clientId` | 客户端标识 | `openclaw-local` |
| `auth.authToken` | 认证令牌 | - |
| `openclaw.baseUrl` | 本地 OpenClaw 地址 | `http://localhost:3000` |
| `openclaw.webhookPath` | OpenClaw webhook 路径 | `/webhooks/wecom` |
| `openclaw.timeout` | 请求超时时间（毫秒） | `60000` |
| `heartbeat.interval` | 心跳间隔（毫秒） | `30000` |
| `heartbeat.timeout` | 心跳响应超时（毫秒） | `10000` |

## 环境变量

也可以通过环境变量配置：

| 环境变量 | 对应配置 |
|----------|----------|
| `RELAY_SERVER_URL` | `server.url` |
| `RELAY_CLIENT_ID` | `auth.clientId` |
| `RELAY_AUTH_TOKEN` | `auth.authToken` |
| `OPENCLAW_BASE_URL` | `openclaw.baseUrl` |
| `OPENCLAW_WEBHOOK_PATH` | `openclaw.webhookPath` |
| `CONFIG_PATH` | 配置文件路径 |

## 消息流程

1. 本地中间层启动，连接公网服务器 WebSocket
2. 发送 `register` 消息进行认证
3. 认证成功后开始心跳保活
4. 服务器收到企业微信请求，通过 WebSocket 发送 `webhook` 消息
5. 本地中间层将请求转发到本地 OpenClaw
6. 收到 OpenClaw 响应后，发送 `response` 消息返回服务器

## 与服务器端配合

确保公网服务器已配置相应的客户端认证：

```yaml
# 服务器端 application.yml
wecom:
  relay:
    clients:
      openclaw-local: "your-auth-token-here"
```
