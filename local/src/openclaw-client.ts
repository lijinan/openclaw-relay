import axios, { AxiosInstance } from 'axios';
import { Config } from './config';
import { WebhookPayload, ResponsePayload } from './types';

export class OpenClawClient {
  private client: AxiosInstance;
  private config: Config;

  constructor(config: Config) {
    this.config = config;
    this.client = axios.create({
      baseURL: config.openclaw.baseUrl,
      timeout: config.openclaw.timeout,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }

  async forwardWebhook(payload: WebhookPayload): Promise<ResponsePayload> {
    const url = payload.path || this.config.openclaw.webhookPath;
    const queryString = payload.query
      ? '?' + new URLSearchParams(payload.query).toString()
      : '';
    const forwardedHeaders = payload.headers ?? {};

    try {
      let response;

      if (payload.method === 'GET') {
        response = await this.client.get(url + queryString, {
          headers: {
            ...forwardedHeaders,
          },
          validateStatus: () => true,
        });
      } else if (payload.method === 'POST') {
        const body = payload.body || '';
        const headers: Record<string, string> = { ...forwardedHeaders };
        const hasContentType = Object.keys(headers).some(
          (key) => key.toLowerCase() === 'content-type',
        );
        if (!hasContentType) {
          const contentType = body.startsWith('{') || body.startsWith('[')
            ? 'application/json'
            : 'text/xml';
          headers['Content-Type'] = contentType;
        }

        response = await this.client.post(url + queryString, body, {
          headers,
          validateStatus: () => true,
        });
      } else {
        return {
          status: 405,
          body: JSON.stringify({ error: `Method ${payload.method} not supported` }),
        };
      }

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

      const responseBody = typeof response.data === 'string'
        ? response.data
        : JSON.stringify(response.data);

      return {
        status: response.status,
        body: responseBody,
        headers: responseHeaders,
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      console.error('[OpenClawClient] Error forwarding webhook:', errorMessage);
      
      return {
        status: 502,
        body: JSON.stringify({ error: `Failed to connect to OpenClaw: ${errorMessage}` }),
      };
    }
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
