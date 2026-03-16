import axios, { AxiosInstance } from 'axios';
import { Config } from './config';
import { WebhookPayload, ResponsePayload } from './types';

export class DestinationClient {
  private client: AxiosInstance;
  private config: Config;

  constructor(config: Config) {
    this.config = config;
    this.client = axios.create({
      baseURL: config.destination.baseUrl,
      timeout: config.destination.timeout,
      headers: {},
    });
  }

  private isPathAllowed(path: string): boolean {
    const allowedPages = this.config.destination.allowedPages;
    
    // 如果白名单为空，允许所有路径
    if (!allowedPages || allowedPages.length === 0) {
      return true;
    }

    // 移除查询参数，只检查路径
    const cleanPath = path.split('?')[0];
    
    // 检查路径是否在白名单中
    return allowedPages.some(allowedPage => {
      // 支持精确匹配和通配符匹配
      if (allowedPage.endsWith('*')) {
        const prefix = allowedPage.slice(0, -1);
        return cleanPath.startsWith(prefix);
      }
      return cleanPath === allowedPage || cleanPath.startsWith(allowedPage + '/');
    });
  }

  async forwardWebhook(payload: WebhookPayload): Promise<ResponsePayload> {
    const path = payload.path || '/';
    
    // 检查路径是否在白名单中
    if (!this.isPathAllowed(path)) {
      console.warn(`[DestinationClient] Path not allowed: ${path}`);
      return {
        status: 403,
        body: JSON.stringify({ error: 'Forbidden: No permission to access this page' }),
        headers: {
          'content-type': 'application/json',
        },
      };
    }

    const queryString = payload.query
      ? '?' + new URLSearchParams(payload.query).toString()
      : '';
    const forwardedHeaders = payload.headers ?? {};
    const filteredHeaders: Record<string, string> = {};
    for (const [key, value] of Object.entries(forwardedHeaders)) {
      const lower = key.toLowerCase();
      if (lower === 'host' || lower === 'content-length' || lower === 'connection' || lower === 'transfer-encoding') {
        continue;
      }
      filteredHeaders[key] = value;
    }

    try {
      const method = (payload.method || 'GET').toUpperCase();
      let data: string | Buffer | undefined;
      if (method !== 'GET' && method !== 'HEAD') {
        if (payload.bodyBase64 && (payload.isBase64 ?? true)) {
          data = Buffer.from(payload.bodyBase64, 'base64');
        } else {
          data = payload.body ?? '';
        }
      }

      const response = await this.client.request({
        method,
        url: path + queryString,
        headers: filteredHeaders,
        data,
        responseType: 'arraybuffer',
        validateStatus: () => true,
      });

      const responseHeaders: Record<string, string> = {};
      if (response.headers) {
        for (const [key, value] of Object.entries(response.headers)) {
          if (value === undefined || value === null) continue;
          if (Array.isArray(value)) {
            responseHeaders[key] = value.join(', ');
          } else {
            responseHeaders[key] = String(value);
          }
        }
      }

      const buffer = Buffer.isBuffer(response.data)
        ? response.data
        : Buffer.from(response.data as ArrayBuffer);
      const contentType = responseHeaders['content-type'] || responseHeaders['Content-Type'];
      const isText = this.isTextContentType(contentType);
      const responseBody = buffer.length === 0
        ? ''
        : isText
          ? buffer.toString('utf8')
          : '';

      return {
        status: response.status,
        body: responseBody,
        headers: responseHeaders,
        bodyBase64: buffer.length > 0 && !isText ? buffer.toString('base64') : undefined,
        isBase64: buffer.length > 0 && !isText ? true : undefined,
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      console.error('[DestinationClient] Error forwarding webhook:', errorMessage);

      return {
        status: 502,
        body: JSON.stringify({ error: `Failed to connect to destination: ${errorMessage}` }),
      };
    }
  }

  private isTextContentType(contentType?: string): boolean {
    if (!contentType) return false;
    const ct = contentType.toLowerCase();
    if (ct.startsWith('text/')) return true;
    if (ct.includes('application/json') || ct.includes('+json')) return true;
    if (ct.includes('application/xml') || ct.includes('text/xml') || ct.includes('+xml')) return true;
    return ct.includes('application/x-www-form-urlencoded');
  }

  async healthCheck(): Promise<boolean> {
    try {
      const response = await this.client.get('/health', { timeout: 5000 });
      return response.status === 200;
    } catch {
      return false;
    }
  }
}
