// Fotografa gli screenshot dell'App Store (D70, D73) da page.html, in tutte le lingue.
// Uso, dalla radice: python3 tools/shots/build.py && node tools/shots/render.mjs [lingua] [numero]
//
// iPhone 6,9 pollici: 440×956 punti ×3 = 1320×2868. iPad 13 pollici: 1032×1376 ×2 = 2064×2752.
// Esce un PNG senza canale alfa per lingua, dispositivo e numero, in lancio/screenshots/.
import { readFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const here = dirname(new URL(import.meta.url).pathname);
const root = resolve(here, '../..');
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const data = JSON.parse(readFileSync(`${here}/shots.json`, 'utf8'));
const icon = 'data:image/png;base64,' + readFileSync(`${root}/design/brand/store/app-store-1024.png`).toString('base64');

const [onlyLang, onlyShot] = process.argv.slice(2);
const jobs = [
  ...[1, 2, 3, 4, 5, 6].map(n => ({ device: 'iphone', n, w: 440, h: 956, dpr: 3 })),
  // Sull'iPad bastano tre (D70): il foglio, la condivisione, lo smistamento. La ricerca
  // con quattro risultati lascerebbe mezzo schermo vuoto.
  ...[1, 3, 4].map(n => ({ device: 'ipad', n, w: 1032, h: 1376, dpr: 2 })),
];

const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined });
for (const lang of Object.keys(data)) {
  if (onlyLang && lang !== onlyLang) continue;
  for (const job of jobs) {
    if (onlyShot && String(job.n) !== onlyShot) continue;
    const page = await browser.newPage({ viewport: { width: job.w, height: job.h }, deviceScaleFactor: job.dpr });
    await page.goto(pathToFileURL(`${here}/page.html`).href);
    await page.evaluate(([d, l, n, dev, i]) => draw(d, l, n, dev, i), [data, lang, job.n, job.device, icon]);
    await page.evaluate(() => document.fonts.ready);
    await page.evaluate(() => fitHands());
    await page.waitForTimeout(250);
    const out = `${root}/lancio/screenshots/${lang}/${job.device}-${job.n}.png`;
    mkdirSync(dirname(out), { recursive: true });
    await page.screenshot({ path: out, clip: { x: 0, y: 0, width: job.w, height: job.h }, omitBackground: false });
    await page.close();
    console.log(out.replace(root + '/', ''));
  }
}
await browser.close();
