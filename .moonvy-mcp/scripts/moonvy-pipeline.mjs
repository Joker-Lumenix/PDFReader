// Moonvy design pipeline: fetch every design's layer tree (with styles) from the
// local moonvy-ai MCP server, then aggregate colors / text / typography.
// usage: node moonvy-pipeline.mjs <mode>   mode = inspect | fetch
import { spawn } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const WS = 'D:/code/PdfReader/.moonvy-mcp';
const TREES = join(WS, 'trees');
mkdirSync(TREES, { recursive: true });

const catalog = JSON.parse(readFileSync(join(WS, 'catalog.json'), 'utf8').replace(/^\uFEFF/, ''));
const designs = Array.isArray(catalog) ? catalog : (catalog.designs || catalog.items || []);

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
    if (msg.id !== undefined && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
  }
});
child.stderr.setEncoding('utf8');
child.stderr.on('data', (d) => process.stderr.write('[srv] ' + d));
const request = (method, params) => new Promise((resolve) => {
  const id = nextId++;
  pending.set(id, resolve);
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n');
});

await request('initialize', { protocolVersion: '2025-11-25', capabilities: {}, clientInfo: { name: 'pipeline', version: '1.0' } });
child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized', params: {} }) + '\n');

const mode = process.argv[2] || 'fetch';

if (mode === 'inspect') {
  const d = designs[0];
  const res = await request('tools/call', { name: 'moonvy_get_tree', arguments: { url: d.url, withStyle: true, maxDepth: 99 } });
  const payload = res.result ?? res;
  const items = payload.structuredContent?.items || [];
  const flat = [];
  const walk = (nodes, depth) => { for (const n of nodes || []) { flat.push({ depth, ...n }); if (n.children) walk(n.children, depth + 1); } };
  walk(items, 0);
  writeFileSync(join(WS, 'sample-tree.json'), JSON.stringify(payload, null, 2));
  const out = {
    design: d.name,
    itemCount: items.length,
    totalNodes: flat.length,
    topKeys: Object.keys(items[0] || {}),
    styleKeys: Object.keys(items[0]?.style || {}),
    typeCounts: flat.reduce((a, n) => { const t = n.type || 'n/a'; a[t] = (a[t] || 0) + 1; return a; }, {}),
    textSamples: flat.filter((n) => n.type === 'TEXT').slice(0, 12).map((n) => ({ name: n.name, keys: Object.keys(n), style: n.style })),
  };
  writeFileSync(join(WS, 'inspect.json'), JSON.stringify(out, null, 2));
  console.log(JSON.stringify({ design: out.design, items: out.itemCount, nodes: out.totalNodes, topKeys: out.topKeys, styleKeys: out.styleKeys, typeCounts: out.typeCounts }, null, 1));
  child.kill();
  process.exit(0);
}

// ---- fetch phase: one get_tree per design
const jobs = designs.map((d, i) => ({ i, d }));
let ok = 0, fail = 0;
for (const { i, d } of jobs) {
  const file = join(TREES, `${String(i).padStart(3, '0')}.json`);
  try {
    const res = await request('tools/call', { name: 'moonvy_get_tree', arguments: { url: d.url, withStyle: true, maxDepth: 99 } });
    const payload = res.result ?? res;
    writeFileSync(file, JSON.stringify({ meta: { name: d.name, url: d.url }, payload }, null, 2));
    if (payload.isError) { fail++; process.stderr.write(`ERR ${i} ${d.name}\n`); } else ok++;
  } catch (e) {
    fail++; process.stderr.write(`THROW ${i} ${d.name}: ${e.message}\n`);
  }
  if ((i + 1) % 20 === 0) process.stderr.write(`progress ${i + 1}/${designs.length}\n`);
}
writeFileSync(join(WS, 'designs-index.json'), JSON.stringify(designs.map((d, i) => ({ i, name: d.name, url: d.url })), null, 2));
process.stderr.write(`fetch done ok=${ok} fail=${fail} files=${readdirSync(TREES).length}\n`);
child.kill();
process.exit(0);
