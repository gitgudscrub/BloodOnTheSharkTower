import { existsSync } from 'node:fs';
import { resolve } from 'node:path';

const configPath = resolve(process.env.SHARKTOWER_ENV_FILE || '/etc/sharktower-live/sharktower-live.env');
if (!existsSync(configPath)) {
  console.error('Sharktower Live configuration file not found at: ' + configPath);
  console.error('Copy .env.example to that path, fill in the required secrets, or set SHARKTOWER_ENV_FILE to another absolute path.');
  process.exit(1);
}
try {
  process.loadEnvFile(configPath);
} catch (err) {
  console.error('Unable to read Sharktower Live configuration: ' + err.message);
  process.exit(1);
}
await import('./server.js');
