# 通用中继本地客户端 - Agent 开发指南

## 项目概述

通用本地中继客户端。通过 WebSocket 隧道连接到远程服务器，并将 Webhook 请求转发到本地目标服务。

**技术栈：**
- Node.js >= 18.0.0
- TypeScript 5.3+
- WebSocket (ws 库)
- Axios (HTTP 客户端)

**架构：**
```
远程服务器 → WebSocket 隧道 → 本地客户端 → 本地目标服务
```

## 构建和运行命令

### 开发
```bash
# 安装依赖
npm install

# 开发模式运行（带自动重载）
npm run dev

# 监视模式运行
npm run watch

# 构建 TypeScript
npm run build
```

### 生产
```bash
# 生产构建
npm run build

# 启动生产服务器
npm start
```

### 测试
```bash
# 运行测试（如已配置）
npm test

# 运行测试并生成覆盖率报告（如已配置）
npm run test:coverage
```

## 项目结构

```
local/
├── src/
│   ├── index.ts                    # 主入口
│   ├── config.ts                   # 配置加载器
│   ├── relay-client.ts            # WebSocket 客户端实现
│   ├── destination-client.ts      # 目标服务 HTTP 客户端
│   └── types.ts                   # TypeScript 类型定义
├── dist/                          # 编译后的 JavaScript 输出
├── node_modules/                  # Node.js 依赖
├── config.json                    # 运行时配置（从示例创建）
├── config.example.json            # 配置模板
├── package.json                   # Node.js 包清单
├── tsconfig.json                  # TypeScript 配置
└── README.md                      # 项目文档
```

## 配置

配置从项目根目录的 `config.json` 加载。从示例复制：

```bash
cp config.example.json config.json
```

### 配置结构

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

### 配置属性

| 属性 | 说明 | 默认值 |
|------|------|--------|
| `server.url` | WebSocket 服务器 URL | 必需 |
| `server.reconnectInterval` | 重连延迟（毫秒） | 5000 |
| `server.maxReconnectAttempts` | 最大重连次数（0 = 无限） | 0 |
| `auth.clientId` | 客户端标识 | 必需 |
| `auth.authToken` | 认证令牌 | 必需 |
| `destination.baseUrl` | 本地目标服务基础 URL | 必需 |
| `destination.timeout` | HTTP 请求超时（毫秒） | 60000 |
| `destination.allowedPages` | 允许转发的页面路径白名单 | `[]`（空数组表示允许所有） |
| `heartbeat.interval` | 心跳间隔（毫秒） | 30000 |
| `heartbeat.timeout` | 心跳响应超时（毫秒） | 10000 |

### 白名单配置说明

`allowedPages` 用于控制哪些路径可以被转发到本地目标服务：

- **空数组 `[]`**：允许所有路径（默认行为）
- **精确匹配**：如 `/webhooks/wecom` 只匹配该路径
- **前缀匹配**：如 `/api` 匹配 `/api`、`/api/users` 等
- **通配符匹配**：如 `/api/*` 匹配 `/api/` 开头的所有路径

不在白名单中的路径会返回 `403 Forbidden` 错误。

### 环境变量

环境变量会覆盖配置文件中的值：

| 环境变量 | 映射到 |
|----------|--------|
| `RELAY_SERVER_URL` | `server.url` |
| `RELAY_CLIENT_ID` | `auth.clientId` |
| `RELAY_AUTH_TOKEN` | `auth.authToken` |
| `DESTINATION_BASE_URL` | `destination.baseUrl` |
| `CONFIG_PATH` | 配置文件路径 |

## 代码风格和规范

### TypeScript 标准
- **语言：** TypeScript 5.3+
- **风格：** 遵循 TypeScript 最佳实践
- **严格模式：** `tsconfig.json` 中启用
- **模块系统：** ESM

### 代码组织
- **入口：** `src/index.ts` - 应用引导
- **配置：** `src/config.ts` - 配置加载和验证
- **WebSocket 客户端：** `src/relay-client.ts` - 中继连接处理
- **HTTP 客户端：** `src/destination-client.ts` - 目标服务通信
- **类型：** `src/types.ts` - 共享 TypeScript 接口

### 命名规范
- 类：`PascalCase`（如 `RelayClient`）
- 接口：`PascalCase`（如 `ServerMessage`）
- 函数/方法：`camelCase`（如 `connect`、`handleMessage`）
- 常量：`UPPER_SNAKE_CASE`（如 `HOP_BY_HOP_HEADERS`）
- 私有成员：`camelCase`（如 `ws`、`config`）

### 错误处理
- 异步操作使用 try-catch
- 带上下文记录错误
- 连接失败时优雅降级
- 关闭时正确清理

### 日志记录
- 信息性消息使用 `console.log`
- 错误使用 `console.error`
- 警告使用 `console.warn`
- 日志前缀使用组件名（如 `[RelayClient]`）

## WebSocket 协议

### 连接流程
1. 客户端连接到服务器 WebSocket URL
2. 客户端发送带 `clientId` 和 `authToken` 的 `register` 消息
3. 服务器验证并响应 `registered` 消息
4. 客户端启动心跳机制
5. 请求到达时服务器发送 `webhook` 消息
6. 客户端处理 webhook 并发送 `response` 消息

### 消息类型

**客户端 → 服务器：**
- `register`：客户端认证
- `ping`：心跳保活
- `response`：Webhook 响应

**服务器 → 客户端：**
- `registered`：注册成功
- `webhook`：要处理的 Webhook 负载
- `pong`：心跳响应
- `error`：错误通知

### 消息格式

```typescript
// 注册
{
  type: 'register',
  clientId: string,
  authToken: string
}

// Webhook
{
  type: 'webhook',
  messageId: string,
  payload: WebhookPayload
}

// 响应
{
  type: 'response',
  messageId: string,
  payload: ResponsePayload,
  error?: string
}
```

## 开发工作流

### 添加新功能
1. 在 `src/types.ts` 中更新 TypeScript 接口
2. 在适当的服务类中实现逻辑
3. 如需更新配置模式
4. 使用 `npm run dev` 测试
5. 使用 `npm run build` 构建并验证

### 调试
- 使用 `npm run dev` 进行带自动重载的开发
- 在控制台启用详细日志
- 检查 WebSocket 连接状态
- 验证目标服务可访问

### 常见问题

**WebSocket 连接失败：**
- 验证服务器 URL 正确
- 检查网络连接
- 确认认证令牌与服务器配置匹配
- 检查防火墙/代理设置

**目标服务连接错误：**
- 验证目标服务正在运行
- 检查 `baseUrl` 配置
- 验证 webhook 路径正确
- 检查超时设置

**构建错误：**
- 确保 Node.js >= 18.0.0
- 运行 `npm install` 更新依赖
- 检查 TypeScript 版本兼容性

## 组件详情

### RelayClient (src/relay-client.ts)

**职责：**
- WebSocket 连接管理
- 客户端注册和认证
- 心跳机制
- 与服务器之间的消息路由
- 重连逻辑

**关键方法：**
- `start()`：初始化并连接
- `connect()`：建立 WebSocket 连接
- `register()`：发送注册消息
- `handleMessage()`：处理传入消息
- `handleWebhook()`：转发 webhook 到目标服务
- `stop()`：干净关闭

### DestinationClient (src/destination-client.ts)

**职责：**
- 与本地目标服务的 HTTP 通信
- 请求/响应处理
- 请求头过滤
- 内容类型检测
- 二进制/文本响应处理

**关键方法：**
- `forwardWebhook()`：转发 webhook 负载到目标服务
- `healthCheck()`：检查目标服务可用性
- `isTextContentType()`：检测文本内容类型

### Configuration (src/config.ts)

**职责：**
- 从文件加载配置
- 应用环境变量覆盖
- 提供类型安全的配置访问

## 部署

### 生产检查清单
- 使用 `npm run build` 编译 TypeScript
- 设置正确的配置文件
- 配置 systemd 或进程管理器（PM2 等）
- 按需设置环境变量
- 启用日志聚合
- 设置监控和告警

### 进程管理

**使用 PM2：**
```bash
npm install -g pm2
npm run build
pm2 start dist/index.js --name webhook-relay
pm2 save
pm2 startup
```

**使用 systemd：**
```bash
sudo cp webhook-relay.service /etc/systemd/system/
sudo systemctl enable webhook-relay
sudo systemctl start webhook-relay
```

### 健康监控

监控以下指标：
- WebSocket 连接状态
- 重连尝试次数
- Webhook 处理延迟
- 目标服务响应时间
- 错误率

## 安全注意事项

- 生产环境使用安全 WebSocket（wss://）
- 永远不要提交带真实令牌的 `config.json`
- 定期轮换认证令牌
- 敏感数据使用环境变量
- 验证所有传入数据
- 如需实现速率限制
- 保持依赖更新

## 测试指南

### 测试结构
- 单个组件的单元测试
- WebSocket 通信的集成测试
- 完整消息流的端到端测试

### 运行测试
```bash
# 运行所有测试
npm test

# 带覆盖率运行
npm run test:coverage

# 监视模式
npm run test:watch
```

## 维护

### 版本更新
- 在 `package.json` 中更新依赖
- 更新后彻底测试
- 用破坏性变更更新本文档
- 查看变更日志以获取安全更新

### 代码审查检查清单
- [ ] TypeScript 无错误编译
- [ ] 适当的错误处理
- [ ] 适当级别的日志记录
- [ ] 配置模式已更新
- [ ] 测试通过
- [ ] 文档已更新

## 故障排除

### 连接问题
1. 检查配置中的 WebSocket URL
2. 验证服务器可访问
3. 确认认证令牌
4. 检查网络/防火墙设置
5. 查看服务器日志

### 性能问题
1. 监控消息队列长度
2. 检查目标服务响应时间
3. 审查超时设置
4. 检查内存使用
5. 分析 CPU 使用

### 调试模式
通过修改 `console.log` 语句或使用带调试级别的日志库来启用详细日志。

## 资源

- [WebSocket 协议 RFC](https://tools.ietf.org/html/rfc6455)
- [Node.js 文档](https://nodejs.org/docs/)
- [TypeScript 手册](https://www.typescriptlang.org/docs/)
- [Axios 文档](https://axios-http.com/docs/intro)
- [ws 库](https://github.com/websockets/ws)
