// Aggregate the fetched moonvy layer trees into color / text / typography inventories.
import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const WS = 'D:/code/PdfReader/.moonvy-mcp';
const TREES = join(WS, 'trees');
const files = readdirSync(TREES).filter((f) => f.endsWith('.json')).sort();

const colors = new Map(); // hex -> { count, bg, fg, samples:Set }
const texts = new Map();  // text -> { count, designs:Set, styles:Map }
const typeScale = new Map(); // key -> {fontSize,weight,family,lineHeight,letterSpacing,count}
const radii = new Map();

const norm = (v) => {
  if (!v || typeof v !== 'string') return null;
  const s = v.trim().toLowerCase();
  if (!s) return null;
  if (/^#[0-9a-f]{6}$/.test(s)) return s;
  if (/^#[0-9a-f]{3}$/.test(s)) return '#' + s.slice(1).split('').map((c) => c + c).join('');
  if (/^rgba?\(/.test(s)) return s; // keep functional colors as-is
  return null;
};

const bump = (map, key, init) => {
  if (!map.has(key)) map.set(key, init());
  return map.get(key);
};

for (const f of files) {
  const doc = JSON.parse(readFileSync(join(TREES, f), 'utf8'));
  const design = doc.meta?.name || f;
  const items = doc.payload?.structuredContent?.items || [];
  const walk = (nodes) => {
    for (const n of nodes || []) {
      const st = n.style || {};
      const bg = norm(st.background);
      const fg = norm(st.color);
      for (const [hex, role] of [[bg, 'bg'], [fg, 'fg']]) {
        if (!hex) continue;
        const ce = bump(colors, hex, () => ({ count: 0, samples: new Set() }));
        ce.count++; ce[role] = (ce[role] || 0) + 1;
        if (role === 'fg' && n.name) ce.samples.add(String(n.name).slice(0, 24));
      }
      if (st.borderRadius) bump(radii, String(st.borderRadius), () => ({ count: 0 })).count++;
      const type = String(n.type || '').toLowerCase();
      if (type === 'text' && n.name) {
        const t = String(n.name);
        const te = bump(texts, t, () => ({ count: 0, designs: new Set(), style: {} }));
        te.count++; te.designs.add(design);
        const key = [st.fontSize, st.fontWeight, st.fontFamily, st.lineHeight, st.letterSpacing].join('|');
        const se = bump(typeScale, key, () => ({ count: 0 }));
        se.count++;
        se.fontSize = st.fontSize; se.fontWeight = st.fontWeight; se.fontFamily = st.fontFamily;
        se.lineHeight = st.lineHeight; se.letterSpacing = st.letterSpacing;
        te.style[st.fontSize + '/' + st.fontWeight] = true;
      }
      walk(n.children);
    }
  };
  walk(items);
}

const colorList = [...colors.entries()]
  .map(([hex, e]) => ({ hex, count: e.count, bg: e.bg || 0, fg: e.fg || 0, samples: [...(e.samples || [])].slice(0, 3) }))
  .sort((a, b) => b.count - a.count);

const textList = [...texts.entries()]
  .map(([text, e]) => ({ text, count: e.count, designs: [...e.designs], styles: Object.keys(e.style || {}) }))
  .sort((a, b) => b.count - a.count);

const scaleList = [...typeScale.values()].sort((a, b) => (b.fontSize || 0) - (a.fontSize || 0));

writeFileSync(join(WS, 'colors.json'), JSON.stringify(colorList, null, 2));
writeFileSync(join(WS, 'texts.json'), JSON.stringify(textList, null, 2));
writeFileSync(join(WS, 'typography.json'), JSON.stringify(scaleList, null, 2));
writeFileSync(join(WS, 'radii.json'), JSON.stringify([...radii.entries()].map(([r, e]) => ({ r: Number(r), count: e.count })).sort((a, b) => a.r - b.r), null, 2));

console.log(JSON.stringify({
  designs: files.length,
  uniqueColors: colorList.length,
  uniqueTexts: textList.length,
  typeStyles: scaleList.length,
  radii: radii.size,
  topColors: colorList.slice(0, 12).map((c) => `${c.hex}(${c.count})`),
  topTexts: textList.slice(0, 10).map((t) => `${t.text}(${t.count})`),
}, null, 1));
