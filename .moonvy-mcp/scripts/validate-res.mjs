import fs from 'node:fs';
import path from 'node:path';

const RES = 'D:/code/PdfReader/app/src/main/res';
const problems = [];
let files = 0;
const walk = (d) => fs.readdirSync(d, { withFileTypes: true }).forEach((e) => {
  const p = path.join(d, e.name);
  if (e.isDirectory()) return walk(p);
  if (!p.endsWith('.xml')) return;
  if (!/values(-[a-zA-Z0-9\-]+)?[\\/](colors|dimens|type|strings)\.xml$/.test(p)) return;
  files++;
  const t = fs.readFileSync(p, 'utf8');
  const names = [...t.matchAll(/<(?:string|color|dimen|style) name="([^"]+)"/g)].map((x) => x[1]);
  for (const n of names) if (!/^[a-z][a-z0-9_]*$/.test(n)) problems.push(`BAD NAME ${path.basename(p)} ${n}`);
  const dup = names.filter((n, i) => names.indexOf(n) !== i);
  if (dup.length) problems.push(`DUP ${path.basename(p)} ${[...new Set(dup)].join(',')}`);
  const perc = [...t.matchAll(/>([^<]*%[^<]*)</g)].map((x) => x[1]).filter((s) => !/%\d+\$/.test(s));
  if (perc.length) problems.push(`RAW % ${path.basename(p)} ${JSON.stringify(perc.slice(0, 3))}`);
  if (/<style[^>]*parent="(?!@android:)[A-Z]/.test(t)) problems.push(`BAD PARENT ${path.basename(p)}`);
});
walk(RES);
console.log('checked files:', files, 'problems:', problems.length);
problems.slice(0, 25).forEach((p) => console.log(' -', p));
