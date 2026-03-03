import WebSocket from 'ws';
import { Config, loadConfig } from './config';
import { ServerMessage, ClientMessage, WebhookPayload, ResponsePayload } from './types';
import { OpenClawClient } from './openclaw-client';

export class RelayClient {
  private config: Config;
  private ws: WebSocket | null = null;
  private openclawClient: OpenClawClient;
  private reconnectAttempts = 0;
  private isConnecting = false;
  private isRegistered = false;
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private pongTimeout: NodeJS.Timeout | null = null;
  private pendingResponses: Map<string, { resolve: (value: unknown) => void; reject: (reason: unknown) => void }> = new Map();

  constructor(config?: Config) {
    this.config = config || loadConfig();
    this.openclawClient = new OpenClawClient(this.config);
  }

  async start(): Promise<void> {
    console.log('[RelayClient] Starting local relay client...');
    console.log(`[RelayClient] Server URL: ${this.config.server.url}`);
    console.log(`[RelayClient] Client ID: ${this.config.auth.clientId}`);
    console.log(`[RelayClient] OpenClaw URL: ${this.config.openclaw.baseUrl}`);

    await this.connect();
  }

  private async connect(): Promise<void> {
    if (this.isConnecting || (this.ws && this.ws.readyState === WebSocket.OPEN)) {
      return;
    }

    this.isConnecting = true;

    return new Promise((resolve, reject) => {
      try {
        console.log('[RelayClient] Connecting to server...');
        this.ws = new WebSocket(this.config.server.url);

        this.ws.on('open', () => {
          console.log('[RelayClient] WebSocket connected');
          this.isConnecting = false;
          this.reconnectAttempts = 0;
          this.register();
          resolve();
        });

        this.ws.on('message', (data: WebSocket.RawData) => {
          this.handleMessage(data);
        });

        this.ws.on('close', (code: number, reason: Buffer) => {
          console.log(`[RelayClient] WebSocket closed: ${code} - ${reason.toString()}`);
          this.handleDisconnect();
        });

        this.ws.on('error', (error: Error) => {
          console.error('[RelayClient] WebSocket error:', error.message);
          this.isConnecting = false;
          reject(error);
        });

        this.ws.on('ping', () => {
          this.ws?.pong();
        });

      } catch (error) {
        this.isConnecting = false;
        reject(error);
      }
    });
  }

  private register(): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      console.error('[RelayClient] Cannot register: WebSocket not connected');
      return;
    }

    console.log('[RelayClient] Sending registration...');

    const message: ClientMessage = {
      type: 'register',
      clientId: this.config.auth.clientId,
      authToken: this.config.auth.authToken,
    };

    this.send(message);
  }

  private handleMessage(data: WebSocket.RawData): void {
    try {
      const message: ServerMessage = JSON.parse(data.toString());
      console.log(`[RelayClient] Received message type: ${message.type}`);

      switch (message.type) {
        case 'registered':
          this.handleRegistered();
          break;

        case 'webhook':
          this.handleWebhook(message);
          break;

        case 'pong':
          this.handlePong();
          break;

        case 'error':
          console.error('[RelayClient] Server error:', message.error);
          break;

        default:
          console.warn('[RelayClient] Unknown message type:', message.type);
      }
    } catch (error) {
      console.error('[RelayClient] Failed to parse message:', error);
    }
  }

  private handleRegistered(): void {
    console.log('[RelayClient] Successfully registered with server');
    this.isRegistered = true;
    this.startHeartbeat();
  }

  private async handleWebhook(message: ServerMessage): Promise<void> {
    const messageId = message.messageId;
    if (!messageId) {
      console.error('[RelayClient] Webhook message missing messageId');
      return;
    }

    const payload = message.payload as WebhookPayload;
    console.log(`[RelayClient] Forwarding webhook: ${payload.method} ${payload.path}`);

    try {
      const response: ResponsePayload = await this.openclawClient.forwardWebhook(payload);

      const responseMessage: ClientMessage = {
        type: 'response',
        messageId: messageId,
        payload: response,
      };

      this.send(responseMessage);
      console.log(`[RelayClient] Webhook response sent: ${response.status}`);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      console.error('[RelayClient] Error handling webhook:', errorMessage);

      const responseMessage: ClientMessage = {
        type: 'response',
        messageId: messageId,
        error: errorMessage,
      };

      this.send(responseMessage);
    }
  }

  private handleDisconnect(): void {
    this.isRegistered = false;
    this.stopHeartbeat();
    this.scheduleReconnect();
  }

  private scheduleReconnect(): void {
    const maxAttempts = this.config.server.maxReconnectAttempts;
    
    if (maxAttempts > 0 && this.reconnectAttempts >= maxAttempts) {
      console.error(`[RelayClient] Max reconnect attempts (${maxAttempts}) reached, stopping`);
      return;
    }

    this.reconnectAttempts++;
    const delay = this.config.server.reconnectInterval;

    console.log(`[RelayClient] Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);

    setTimeout(() => {
      this.connect().catch((error) => {
        console.error('[RelayClient] Reconnect failed:', error.message);
      });
    }, delay);
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();

    console.log(`[RelayClient] Starting heartbeat (interval: ${this.config.heartbeat.interval}ms)`);

    this.heartbeatTimer = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN && this.isRegistered) {
        this.sendPing();
      }
    }, this.config.heartbeat.interval);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    if (this.pongTimeout) {
      clearTimeout(this.pongTimeout);
      this.pongTimeout = null;
    }
  }

  private sendPing(): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      return;
    }

    const message: ClientMessage = { type: 'ping' };
    this.send(message);

    this.pongTimeout = setTimeout(() => {
      console.warn('[RelayClient] Pong timeout, closing connection');
      this.ws?.close();
    }, this.config.heartbeat.timeout);
  }

  private handlePong(): void {
    if (this.pongTimeout) {
      clearTimeout(this.pongTimeout);
      this.pongTimeout = null;
    }
  }

  private send(message: ClientMessage): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      console.error('[RelayClient] Cannot send: WebSocket not connected');
      return;
    }

    const json = JSON.stringify(message);
    this.ws.send(json);
  }

  async stop(): Promise<void> {
    console.log('[RelayClient] Stopping...');
    this.stopHeartbeat();
    
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    
    this.isRegistered = false;
    console.log('[RelayClient] Stopped');
  }

  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN && this.isRegistered;
  }
}
