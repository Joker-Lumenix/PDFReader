// Generate Android resources from the extracted Moonvy design inventory.
// outputs: app/src/main/res/values/{colors,dimens,type,strings}.xml + locale folders
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = 'D:/code/PdfReader';
const RES = join(ROOT, 'app/src/main/res');
const TMP = 'C:/Users/m1838/AppData/Local/Temp/kilo';

const colors = JSON.parse(readFileSync(join(ROOT, '.moonvy-mcp/colors.json'), 'utf8'));
const typo = JSON.parse(readFileSync(join(ROOT, '.moonvy-mcp/typography.json'), 'utf8'));
const radii = JSON.parse(readFileSync(join(ROOT, '.moonvy-mcp/radii.json'), 'utf8'));
const existing = JSON.parse(readFileSync(join(TMP, 'existing-i18n.json'), 'utf8'));

const tsv = (p) => readFileSync(p, 'utf8').split(/\r?\n/).filter((l) => l.trim() && !l.startsWith('#hex')).map((l) => l.split('\t'));
const semantic = new Map(tsv(join(TMP, 'semantic-colors.tsv')).map(([hex, name]) => [hex.toLowerCase(), name]));
const newRows = tsv(join(TMP, 'new-strings.tsv')).map((r) => ({
  key: r[0], zh: r[1], en: r[2], zhTW: r[3], ja: r[4], ko: r[5], es: r[6], fr: r[7], de: r[8], ru: r[9], pt: r[10],
}));

const snake = (k) => k.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase();
const clean = (v) => String(v).replace(/\$\{'\$'}/g, '$').replace(/%(?!\d+\$)/g, '%');
const esc = (v) => clean(v)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  .replace(/'/g, "\\'").replace(/"/g, '\\"');
// Android wants positional format args
const fmt = (v) => clean(v).replace(/%d/g, '%1$d').replace(/%s/g, '%1$s');

// ---------------------------------------------------------------- colors.xml
const seen = new Set();
const colorLines = [];
for (const c of colors) {
  const hex = c.hex.toLowerCase();
  if (seen.has(hex)) continue;
  seen.add(hex);
  const name = semantic.get(hex) || ('dsg_' + hex.replace(/[^a-z0-9]/g, ''));
  // AAPT2 only accepts #RGB / #ARGB / #RRGGBB / #AARRGGBB
  let value = c.hex;
  const rgba = value.match(/^rgba?\(([^)]+)\)$/);
  if (rgba) {
    const parts = rgba[1].split(',').map((x) => parseFloat(x.trim()));
    const [r, g, b, a] = parts;
    const hex2 = (n) => Math.max(0, Math.min(255, Math.round(n))).toString(16).padStart(2, '0');
    value = a === undefined ? `#${hex2(r)}${hex2(g)}${hex2(b)}` : `#${hex2(a * 255)}${hex2(r)}${hex2(g)}${hex2(b)}`;
  } else if (/^#[0-9a-f]{3}$/.test(value)) {
    value = '#' + value.slice(1).split('').map((c) => c + c).join('');
  }
  colorLines.push(`    <color name="${name}">${value}</color>`);
}
const colorXml = `<?xml version="1.0" encoding="utf-8"?>
<!--
  Design tokens extracted from the Moonvy project
  1e7e53d3-a442-4a29-8efd-ccbf689af91a / f96b1cb3-41ae-44d3-9376-7dcbe5f51f35
  116 design files, ${colors.length} unique colours.

  Naming: semantic names first (brand_*, surface_*, text_*, status_*, accent_*),
  every remaining value is kept as dsg_<hex> so no colour from the design is lost.
-->
<resources>
${colorLines.join('\n')}
</resources>
`;
mkdirSync(join(RES, 'values'), { recursive: true });
writeFileSync(join(RES, 'values/colors.xml'), colorXml, 'utf8');

// ---------------------------------------------------------------- dimens.xml
const radiusValues = [...new Set(radii.map((r) => Math.round(r.r * 100) / 100).filter((r) => r > 0))].sort((a, b) => a - b);
const dimenLines = [
  '    <!-- Spacing scale (M3 4dp grid, from the design system) -->',
  '    <dimen name="space_xxs">2dp</dimen>',
  '    <dimen name="space_xs">4dp</dimen>',
  '    <dimen name="space_s">8dp</dimen>',
  '    <dimen name="space_m">12dp</dimen>',
  '    <dimen name="space_ml">16dp</dimen>',
  '    <dimen name="space_l">20dp</dimen>',
  '    <dimen name="space_xl">24dp</dimen>',
  '    <dimen name="space_xxl">32dp</dimen>',
  '    <!-- Screen layout -->',
  '    <dimen name="screen_margin">16dp</dimen>',
  '    <dimen name="screen_margin_tablet">24dp</dimen>',
  '    <dimen name="gutter_sm">8dp</dimen>',
  '    <dimen name="gutter">16dp</dimen>',
  '    <dimen name="top_bar_height">64dp</dimen>',
  '    <dimen name="nav_bar_height">80dp</dimen>',
  '    <!-- Corner radii -->',
  '    <dimen name="radius_xs">2dp</dimen>',
  '    <dimen name="radius_s">4dp</dimen>',
  '    <dimen name="radius_m">8dp</dimen>',
  '    <dimen name="radius_l">12dp</dimen>',
  '    <dimen name="radius_xl">16dp</dimen>',
  '    <dimen name="radius_xxl">20dp</dimen>',
  '    <dimen name="radius_full">999dp</dimen>',
  '',
  '    <!-- Exact corner radii observed in the design -->',
  ...radiusValues.map((r) => `    <dimen name="dsg_radius_${String(r).replace('.', '_')}">${r}dp</dimen>`),
];
writeFileSync(join(RES, 'values/dimens.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!-- Spacing / radius tokens extracted from the Moonvy design. -->
<resources>
${dimenLines.join('\n')}
</resources>
`, 'utf8');

// ---------------------------------------------------------------- type.xml
const fam = (f) => (f === 'Roboto' || !f ? 'sans-serif' : f === 'Poppins' ? 'sans-serif-medium' : f);
const usedStyles = new Set();
const typeLines = [];
const typeLinesV28 = [];
for (const t of typo) {
  const size = Math.round((t.fontSize || 12) * 100) / 100;
  const weight = Math.round((t.fontWeight || 400));
  let name = `design_text_${String(size).replace('.', '_')}_${weight}`;
  if (usedStyles.has(name)) name = `design_text_${String(size).replace('.', '_')}_${weight}_lh${String(Math.round((t.lineHeight || 0) * 100) / 100).replace('.', '_')}`;
  let n = 2; while (usedStyles.has(name)) { name = `design_text_${String(size).replace('.', '_')}_${weight}_${n++}`; }
  usedStyles.add(name);
  const base = [
    `android:textSize="${size}sp"`,
    `android:fontFamily="${fam(t.fontFamily)}"`,
  ];
  if (t.letterSpacing) base.push(`android:letterSpacing="${Math.round(t.letterSpacing * 1000) / 1000}"`);
  const v28 = [`android:textFontWeight="${weight}"`];
  if (t.lineHeight) v28.push(`android:lineHeight="${Math.round(t.lineHeight * 100) / 100}sp"`);
  const note = t.fontFamily && t.fontFamily !== 'Roboto' ? ` <!-- design font: ${t.fontFamily} -->` : '';
  typeLines.push(`    <style name="${name}" parent="@android:style/TextAppearance">\n        ${base.join('\n        ')}\n    </style>${note}`);
  typeLinesV28.push(`    <style name="${name}" parent="@android:style/TextAppearance">\n        ${[...base, ...v28].join('\n        ')}\n    </style>${note}`);
}
const typeHeader = `<?xml version="1.0" encoding="utf-8"?>
<!--
  Typography scale extracted from the Moonvy design (${typo.length} combinations).
  Named <size>_<weight>; the design families Freeman / Poppins / Qwigley have no
  system counterpart, the system sans-serif is used instead (see the comment).
-->
<resources>
`;
writeFileSync(join(RES, 'values/type.xml'), typeHeader + typeLines.join('\n') + '\n</resources>\n', 'utf8');
mkdirSync(join(RES, 'values-v28'), { recursive: true });
writeFileSync(join(RES, 'values-v28/type.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!-- Same type scale plus font weight and line height (API 28+). -->
<resources>
${typeLinesV28.join('\n')}
</resources>
`, 'utf8');

// ---------------------------------------------------------------- strings
const base = new Map(existing.defaults.map(([k, v]) => [snake(k), v]));
const ov = (lang) => new Map((existing.over[lang] || []).map(([k, v]) => [snake(k), v]));
const merge = (...maps) => { const m = new Map(); for (const x of maps) for (const [k, v] of x) m.set(k, v); return m; };

const tables = {
  zh: merge(base),
  zhTW: merge(base, ov('zhTW')),
  en: merge(base, ov('en')),
};
for (const lang of ['ja', 'ko', 'es', 'fr', 'de', 'ru', 'pt']) {
  tables[lang] = merge(base, ov('en'), ov(lang));
}
for (const row of newRows) {
  const k = snake(row.key); tables.zh.set(k, row.zh);
  tables.zhTW.set(k, row.zhTW);
  for (const lang of ['en', 'ja', 'ko', 'es', 'fr', 'de', 'ru', 'pt']) tables[lang].set(k, row[lang]);
}

const locales = [
  ['values', 'zh'], ['values-zh-rTW', 'zhTW'], ['values-en', 'en'], ['values-ja', 'ja'], ['values-ko', 'ko'],
  ['values-es', 'es'], ['values-fr', 'fr'], ['values-de', 'de'], ['values-ru', 'ru'], ['values-pt', 'pt'],
];
for (const [dir, lang] of locales) {
  const table = tables[lang];
  const items = [...table.entries()].sort(([a], [b]) => a.localeCompare(b));
  const body = items.map(([k, v]) => `    <string name="${k}">${esc(fmt(v))}</string>`).join('\n');
  mkdirSync(join(RES, dir), { recursive: true });
  writeFileSync(join(RES, dir, 'strings.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!--
  UI copy: ${items.length} strings.
  Source: the app string table (i18n/Strings.kt) merged with the copy found in the
  Moonvy design project 1e7e53d3-a442-4a29-8efd-ccbf689af91a.
-->
<resources>
${body}
</resources>
`, 'utf8');
}

console.log(JSON.stringify({
  colors: colorLines.length,
  dimens: dimenLines.filter((l) => l.includes('<dimen')).length,
  typeStyles: typeLines.length,
  stringsPerLocale: tables.zh.size,
  locales: locales.map(([d]) => d),
}, null, 1));
