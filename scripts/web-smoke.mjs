import { chromium } from 'playwright';

const url = process.argv[2] || 'http://localhost:8082';
const browser = await chromium.launch();
const page = await browser.newPage();
const logs = [];
page.on('console', (msg) => logs.push(`[${msg.type()}] ${msg.text()}`));
page.on('pageerror', (err) => logs.push(`[pageerror] ${err.message}\n${err.stack || ''}`));

try {
  await page.goto(url, { waitUntil: 'networkidle', timeout: 120_000 });
  await page.waitForTimeout(8000);
  const bootError = await page.locator('#boot-error').textContent().catch(() => '');
  const bodyText = await page.locator('body').innerText().catch(() => '');
  const canvas = await page.locator('canvas').count();
  console.log('URL:', url);
  console.log('Canvas count:', canvas);
  console.log('Body text:', JSON.stringify(bodyText.slice(0, 500)));
  console.log('Boot error:', JSON.stringify(bootError.slice(0, 2000)));
  console.log('--- console ---');
  logs.slice(0, 40).forEach((l) => console.log(l));
} finally {
  await browser.close();
}
