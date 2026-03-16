# 通用 Webhook 本地中继

本地中继客户端，用于将 Webhook 请求通过 WebSocket 隧道转发到本地运行的目标服务。

## 架构说明

```
公网服务器 → [WebSocket 隧道] → 本地中继 → 本地目标服务
```

由于本地服务无法直接从公网访问，本地中继主动与公网服务器建立 WebSocket 连接，服务器收到 Webhook 请求后通过该连接转发到本地。

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
    "url": "wss://your-server.com/relay/ws/relay",
    "reconnectInterval": 5000,
    "maxReconnectAttempts": 0
  },
  "auth": {
    "clientId": "relay-local",
    "authToken": "your-auth-token-here"
  },
  "destination": {
    "baseUrl": "http://localhost:3000",
    "timeout": 60000,
    "allowedPages": [
      "/webhooks/wecom",
      "/api/*"
    ]
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
| `server.url` | 公网服务器 WebSocket 地址 | `ws://localhost:8080/relay/ws/relay` |
| `server.reconnectInterval` | 重连间隔（毫秒） | `5000` |
| `server.maxReconnectAttempts` | 最大重连次数，0 表示无限 | `0` |
| `auth.clientId` | 客户端标识 | `relay-local` |
| `auth.authToken` | 认证令牌 | - |
| `destination.baseUrl` | 本地目标服务地址 | `http://localhost:3000` |
| `destination.timeout` | 请求超时时间（毫秒） | `60000` |
| `destination.allowedPages` | 允许转发的页面路径白名单 | `[]`（空数组表示允许所有） |
| `heartbeat.interval` | 心跳间隔（毫秒） | `30000` |
| `heartbeat.timeout` | 心跳响应超时（毫秒） | `10000` |

### 白名单配置说明

`allowedPages` 用于控制哪些路径可以被转发到本地目标服务：

- **空数组 `[]`**：允许所有路径（默认行为）
- **精确匹配**：如 `/webhooks/wecom` 只匹配该路径
- **前缀匹配**：如 `/api` 匹配 `/api`、`/api/users` 等
- **通配符匹配**：如 `/api/*` 匹配 `/api/` 开头的所有路径

不在白名单中的路径会返回 `403 Forbidden` 错误。

### 环境变量

也可以通过环境变量配置：

| 环境变量 | 对应配置 |
|----------|----------|
| `RELAY_SERVER_URL` | `server.url` |
| `RELAY_CLIENT_ID` | `auth.clientId` |
| `RELAY_AUTH_TOKEN` | `auth.authToken` |
| `DESTINATION_BASE_URL` | `destination.baseUrl` |
| `CONFIG_PATH` | 配置文件路径 |

## 消息流程

1. 本地中继启动，连接公网服务器 WebSocket
2. 发送 `register` 消息进行认证
3. 认证成功后开始心跳保活
4. 服务器收到 Webhook 请求，通过 WebSocket 发送 `webhook` 消息
5. 本地中继检查请求路径是否在白名单中
6. 白名单校验通过后，将请求转发到本地目标服务
7. 收到目标服务响应后，发送 `response` 消息返回服务器

## 与服务器端配合

确保公网服务器已配置相应的客户端认证：

```yaml
# 服务器端 application.yml
relay:
  clients:
    relay-local: "your-auth-token-here"
```
