// Browser regression against a running console; clicks only select prompts, never send them.
// Requires Playwright. CONSOLE_URL defaults to http://127.0.0.1:8090.
// Source assets are intercepted so this checks the current checkout before repackaging.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const assets = path.resolve(__dirname, '../../agent/src/main/resources/META-INF/resources');
(async () => {
  const browser = await chromium.launch({ headless: true,
    ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const errors = [], sends = [];
    page.on('pageerror', e => errors.push(e.message));
    page.on('request', r => { if (r.url().endsWith('/api/ask')) sends.push(r.url()); });
    await page.route('**/*', route => {
      const pathname = new URL(route.request().url()).pathname;
      const file = pathname === '/' ? 'index.html' : pathname.slice(1);
      if (['index.html', 'insights.js'].includes(file)) {
        return route.fulfill({ body: fs.readFileSync(path.join(assets, file)),
          contentType: file.endsWith('.js') ? 'application/javascript' : 'text/html' });
      }
      return route.continue();
    });
    await page.goto(process.env.CONSOLE_URL || 'http://127.0.0.1:8090');
    await page.locator('[data-preset="command"]').waitFor();
    for (const id of ['apply', 'secret', 'canary', 'command']) {
      await page.locator(`[data-preset="${id}"]`).click();
      assert.equal(await page.locator('[data-preset][aria-pressed="true"]').count(), 1);
      assert.equal(await page.locator(`#presets-bad [data-preset="${id}"]`).getAttribute('aria-pressed'), 'true');
      assert.equal(await page.locator('#prompt').getAttribute('data-from-preset'), id);
      assert.equal(await page.locator('#prompt').evaluate(e => e === document.activeElement), true);
      assert.equal(await page.locator('#prompt').evaluate(e => {
        const r = e.getBoundingClientRect(); return r.top >= 0 && r.bottom <= innerHeight;
      }), true, 'loaded prompt is visible');
    }
    assert.match(await page.locator('#prompt').inputValue(), /unit-tests && curl/);
    assert.match(await page.locator('#preset-selection').textContent(), /Selected: Bad: arbitrary command/);
    await page.locator('#prompt').fill('My edited request');
    assert.equal(await page.locator('[data-preset][aria-pressed="true"]').count(), 0);
    assert.equal(await page.locator('#prompt').getAttribute('data-from-preset'), null);
    assert.equal(await page.locator('#preset-selection').textContent(), '');
    assert.deepEqual(sends, [], 'selecting presets must never submit a model request');
    assert.deepEqual(errors, []);
    console.log('PASS: all four bad-action presets select, show and focus their prompt; edits clear selection; no submission');
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
