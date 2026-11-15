// Minimal MCP stdio client for the local `moonvy-ai` server.
// usage: node moonvy-mcp.mjs <toolName|__list__|__init__> <argsJsonFile> [timeoutMs]
import { spawn } from 'node:child_process';
import { readFileSync } from 'node:fs';

const [tool, argsFile, timeoutRaw = '120000'] = process.argv.slice(2);
const raw = argsFile && argsFile !== '-' ? readFileSync(argsFile, 'utf8').replace(/^\uFEFF/, '') : '{}';
const args = raw.trim() ? JSON.parse(raw) : {};
const timeoutMs = Number(timeoutRaw) || 120000;

const child = spawn('cmd', ['/c', 'moonvy-ai'], { stdio: ['pipe', 'pipe', 'pipe'] });

let buffer = '';
const pending = new Map();
let nextId = 1;

child.stdout.setEncoding('utf8');
child.stdout.on('data', (chunk) => {
  buffer += chunk;
  let idx;
  while ((idx = buffer.indexOf('\n')) >= 0) {
    const line = buffer.slice(0, idx).trim();
    buffer = buffer.slice(idx + 1);
    if (!line) continue;
    let msg;
    try { msg = JSON.parse(line); } catch { continue; }
    if (msg.id !== undefined && pending.has(msg.id)) {
      pending.get(msg.id)(msg);
      pending.delete(msg.id);
    }
  }
});
child.stderr.setEncoding('utf8');
child.stderr.on('data', (d) => process.stderr.write('[moonvy-ai] ' + d));

function request(method, params) {
  const id = nextId++;
  return new Promise((resolve) => {
    pending.set(id, resolve);
    child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n');
  });
}

const timer = setTimeout(() => {
  process.stderr.write('[client] timeout\n');
  child.kill();
  process.exit(3);
}, timeoutMs);

await request('initialize', {
  protocolVersion: '2025-11-25',
  capabilities: {},
  clientInfo: { name: 'kilo-moonvy-bridge', version: '1.0.0' },
});
child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized', params: {} }) + '\n');

if (tool === '__init__') {
  console.log(JSON.stringify('ok'));
} else if (tool === '__list__') {
  const list = await request('tools/list', {});
  console.log(JSON.stringify(list.result, null, 2));
} else {
  const res = await request('tools/call', { name: tool, arguments: args });
  console.log(JSON.stringify(res.result ?? res, null, 2));
}

clearTimeout(timer);
child.kill();
process.exit(0);
