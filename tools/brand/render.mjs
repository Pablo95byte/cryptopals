// Disegna i PNG del segno di Instink dagli SVG di design/brand/ (D66), con il Chromium
// già installato. Uso: python3 tools/brand/icons.py && node tools/brand/render.mjs
import { readFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const root = resolve(dirname(new URL(import.meta.url).pathname), '../..');
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const jobs = JSON.parse(readFileSync(`${root}/design/brand/jobs.json`, 'utf8'));
const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined });
for (const job of jobs) {
  const page = await browser.newPage({ viewport: { width: job.w, height: job.h }, deviceScaleFactor: 1 });
  const svg = pathToFileURL(`${root}/${job.svg}`).href;
  // L'SVG aperto come documento, non come <img>: così carica il font del sito.
  await page.goto(svg);
  await page.waitForLoadState('networkidle');
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(150);
  mkdirSync(dirname(`${root}/${job.out}`), { recursive: true });
  await page.screenshot({ path: `${root}/${job.out}`, omitBackground: job.alpha });
  await page.close();
  console.log(job.out);
}
await browser.close();
