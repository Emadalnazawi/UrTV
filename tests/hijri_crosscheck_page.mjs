// The Hijri date the page shows (Intl, islamic-umalqura, in the city's zone, the way template.html asks) for every day of 1447-1450 AH against
// the official month starts in tests/hijri_crosscheck.tsv, at 00:05, noon and 23:55 of the day in Riyadh; and the page's own hijriMonthNow() on the
// first and last day of Ramadan 1447 and 1448 and on the days around them.
//   node tests/hijri_crosscheck_page.mjs        (CHROMIUM=/path/to/chromium if Playwright has no browser of its own)
import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const months = readFileSync(path.join(ROOT, 'tests/hijri_crosscheck.tsv'), 'utf8').split(/\r?\n/).filter(l => l && !l.startsWith('#')).map(l => l.split('\t'));
const browser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
let failed = 0;
const check = (name, ok, detail = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : '  -> ' + detail)); if (!ok) failed++; };

const ctx = await browser.newContext({ viewport: { width: 1280, height: 720 } });
await ctx.route(/^https?:/, r => r.abort());
const p = await ctx.newPage();
const errors = [];
p.on('pageerror', e => errors.push(e.message));
await p.clock.setFixedTime(new Date('2026-10-23T09:00:00Z'));
await p.goto('file://' + path.join(ROOT, 'dist/tv.html') + '#tz=Asia%2FRiyadh&gap=0');
await p.waitForFunction(() => typeof hijriMonthNow === 'function' && typeof CONFIG === 'object', null, { timeout: 8000 });

const days = [];
for (const [y, m, start, len] of months) {
  const t0 = Date.parse(start + 'T00:00:00Z');
  for (let k = 0; k < +len; k++) days.push([new Date(t0 + k * 86400000).toISOString().slice(0, 10), +y, +m, k + 1]);
}
const bad = await p.evaluate(days => {
  const f = new Intl.DateTimeFormat('en-u-ca-islamic-umalqura', { timeZone: CONFIG.timezone, year: 'numeric', month: 'numeric', day: 'numeric' }), out = [];
  for (const [g, y, m, d] of days) for (const hm of ['T00:05:00', 'T12:00:00', 'T23:55:00']) {
    const o = Object.fromEntries(f.formatToParts(new Date(g + hm + '+03:00')).map(x => [x.type, +x.value.replace(/[^0-9]/g, '')]));
    if (o.year !== y || o.month !== m || o.day !== d) out.push(`${g} ${hm.slice(1, 6)}: official ${y}-${m}-${d}, page ${o.year}-${o.month}-${o.day}`);
  }
  return out;
}, days);
check(`page: all ${days.length} days of 1447-1450 AH (${months.length} months) have the official Hijri date, at three times of the day`, !bad.length && days.length > 1400, bad.slice(0, 5).join('; '));

// the Ramadan test the prayer code uses (hijriMonthNow(true) === 9): the days either side of both Ramadans
const ram = [];
for (const y of ['1447', '1448']) {
  const nine = months.find(r => r[0] === y && r[1] === '9'), ten = months.find(r => r[0] === y && r[1] === '10');
  const first = nine[2], last = new Date(Date.parse(ten[2] + 'T00:00:00Z') - 86400000).toISOString().slice(0, 10);
  ram.push([first, true], [new Date(Date.parse(first + 'T00:00:00Z') - 86400000).toISOString().slice(0, 10), false], [last, true], [ten[2], false]);
}
const got = [];
for (const [d, want] of ram) {
  await p.clock.setFixedTime(new Date(d + 'T09:00:00Z'));
  const month = await p.evaluate(() => hijriMonthNow(true));
  if ((month === 9) !== want) got.push(`${d}: month ${month}, Ramadan expected ${want}`);
}
check('page: hijriMonthNow() says Ramadan on exactly the first..last day of Ramadan 1447 and 1448 (2026-02-18..03-19, 2027-02-08..03-08)', !got.length, got.join('; '));
check('page: no script errors', !errors.length, errors.join('; '));
await browser.close();
process.exit(failed ? 1 : 0);
