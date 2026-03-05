export interface ServerMessage {
  type: string;
  messageId?: string;
  payload?: WebhookPayload | unknown;
  error?: string;
}

export interface ClientMessage {
  type: string;
  clientId?: string;
  authToken?: string;
  messageId?: string;
  payload?: unknown;
  error?: string;
}

export interface WebhookPayload {
  method: string;
  path: string;
  query?: Record<string, string>;
  headers?: Record<string, string>;
  body?: string;
}

export interface ResponsePayload {
  status: number;
  body: string;
  headers?: Record<string, string>;
}
