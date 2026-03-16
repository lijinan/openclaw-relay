import * as fs from 'fs';
import * as path from 'path';

export interface Config {
  server: {
    url: string;
    reconnectInterval: number;
    maxReconnectAttempts: number;
  };
  auth: {
    clientId: string;
    authToken: string;
  };
  destination: {
    baseUrl: string;
    timeout: number;
    allowedPages: string[];
  };
  heartbeat: {
    interval: number;
    timeout: number;
  };
}

export const defaultConfig: Config = {
  server: {
    url: 'ws://localhost:8080/relay/ws/relay',
    reconnectInterval: 5000,
    maxReconnectAttempts: 0,
  },
  auth: {
    clientId: 'relay-local',
    authToken: 'your-auth-token',
  },
  destination: {
    baseUrl: 'http://localhost:3000',
    timeout: 60000,
    allowedPages: [],
  },
  heartbeat: {
    interval: 30000,
    timeout: 10000,
  },
};

function deepMerge<T>(target: T, source: Partial<T>): T {
  const result = { ...target };
  for (const key in source) {
    if (source[key] !== undefined) {
      if (typeof source[key] === 'object' && source[key] !== null && !Array.isArray(source[key])) {
        result[key] = deepMerge(result[key], source[key] as Partial<T[Extract<keyof T, string>]>) as T[Extract<keyof T, string>];
      } else {
        result[key] = source[key] as T[Extract<keyof T, string>];
      }
    }
  }
  return result;
}

export function loadConfig(): Config {
  let config: Config = { ...defaultConfig };

  const configPath = process.env.CONFIG_PATH || path.join(process.cwd(), 'config.json');

  if (fs.existsSync(configPath)) {
    try {
      const fileContent = fs.readFileSync(configPath, 'utf-8');
      const fileConfig = JSON.parse(fileContent) as Partial<Config>;
      config = deepMerge(config, fileConfig);
      console.log(`[Config] Loaded configuration from ${configPath}`);
    } catch (error) {
      console.warn(`[Config] Failed to load config file: ${error}`);
    }
  }

  if (process.env.RELAY_SERVER_URL) {
    config.server.url = process.env.RELAY_SERVER_URL;
  }
  if (process.env.RELAY_CLIENT_ID) {
    config.auth.clientId = process.env.RELAY_CLIENT_ID;
  }
  if (process.env.RELAY_AUTH_TOKEN) {
    config.auth.authToken = process.env.RELAY_AUTH_TOKEN;
  }
  if (process.env.DESTINATION_BASE_URL) {
    config.destination.baseUrl = process.env.DESTINATION_BASE_URL;
  }

  return config;
}
