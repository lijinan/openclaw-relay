import { RelayClient } from './relay-client';
import { loadConfig } from './config';

const config = loadConfig();
const client = new RelayClient(config);

process.on('SIGINT', async () => {
  console.log('\n[Main] Received SIGINT, shutting down...');
  await client.stop();
  process.exit(0);
});

process.on('SIGTERM', async () => {
  console.log('\n[Main] Received SIGTERM, shutting down...');
  await client.stop();
  process.exit(0);
});

process.on('uncaughtException', (error) => {
  console.error('[Main] Uncaught exception:', error);
  process.exit(1);
});

process.on('unhandledRejection', (reason) => {
  console.error('[Main] Unhandled rejection:', reason);
});

client.start().catch((error) => {
  console.error('[Main] Failed to start relay client:', error);
  process.exit(1);
});
