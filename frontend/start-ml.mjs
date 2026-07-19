import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const frontendDirectory = dirname(fileURLToPath(import.meta.url));
const mlDirectory = resolve(frontendDirectory, '..', 'ml-service');
const python = resolve(mlDirectory, '.venv', 'Scripts', 'python.exe');
const child = spawn(python, ['-m', 'uvicorn', 'app.main:app', '--host', '127.0.0.1', '--port', '8000'], {
  cwd: mlDirectory,
  stdio: 'inherit',
  windowsHide: true,
});

child.on('exit', (code) => process.exit(code ?? 1));
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal));
}
