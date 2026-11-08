const http = require('http'), fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const ROOT = '/home/claude/Kosh/app/src/main/assets/pdfviewer';
const MIME = { html:'text/html', js:'text/javascript', mjs:'text/javascript', css:'text/css', svg:'image/svg+xml', png:'image/png', wasm:'application/wasm' };
const server = http.createServer((req, res) => {
  const u = decodeURIComponent(req.url.split('?')[0]);
  let file;
  if (u.startsWith('/app/')) file = path.join(ROOT, u.slice(5));
  else if (u.startsWith('/doc/')) file = '/tmp/vt/' + u.slice(5);
  if (!file || !fs.existsSync(file)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(file).slice(1)] || 'application/octet-stream', 'Access-Control-Allow-Origin': '*' });
  fs.createReadStream(file).pipe(res);
});
const ok = (c, m) => { console.log((c ? 'PASS ' : 'FAIL ') + m); if (!c) process.exitCode = 1; };
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  await new Promise(r => server.listen(0, r));
  const port = server.address().port, base = `http://127.0.0.1:${port}`;
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome', args: ['--no-sandbox'] });
  async function open(doc) {
    const page = await browser.newPage({ viewport: { width: 400, height: 700 } });
    page.on('pageerror', e => console.log('PAGEERROR', e.message));
    page.on('console', m => { if (m.type() === 'error') console.log('CONSOLE', m.text()); });
    await page.addInitScript(() => { window.__calls = []; window.JWI = new Proxy({}, { get: (_t, n) => (...a) => { window.__calls.push([n, ...a]); } }); });
    await page.goto(base + '/app/viewer.html');
    await page.evaluate(() => setupHelper());
    await page.evaluate(u => openUrl({ url: u }), base + '/doc/' + doc);
    return page;
  }
  const calls = (page, name) => page.evaluate(n => window.__calls.filter(c => c[0] === n), name);
  const waitFor = async (page, name, ms = 8000) => { const t = Date.now(); while (Date.now() - t < ms) { const c = await calls(page, name); if (c.length) return c; await sleep(100); } return []; };

  // ---- plain document ----
  let page = await open('test.pdf');
  const ls = await waitFor(page, 'onLoadSuccess');
  ok(ls.length && ls[0][1] === 3, 'onLoadSuccess with 3 pages');
  const outline = await waitFor(page, 'onOutlineLoaded');
  const ol = outline.length ? JSON.parse(outline[0][1]) : [];
  ok(ol.length === 3 && ol[1].title === 'Chapter 2' && ol[1].page === 1, 'outline parsed with page indexes');
  const props = await waitFor(page, 'onLoadProperties');
  ok(props.length > 0 && JSON.parse(props[0][1]).size > 1000, 'properties include file size');
  await page.evaluate(() => setScaleValue('page-width'));
  const fit = await page.evaluate(() => getActualScaleFor('page-width'));
  const cur = await page.evaluate(() => PDFViewerApplication.pdfViewer.currentScale);
  ok(Math.abs(fit - cur) < 0.03, `fit-width scale matches (${fit.toFixed(3)} vs ${cur.toFixed(3)})`);
  await page.evaluate(() => scalePageTo(2));
  ok(Math.abs(await page.evaluate(() => PDFViewerApplication.pdfViewer.currentScale) - 2) < 0.01, 'scalePageTo(2)');
  await page.evaluate(() => setScaleValue('page-width'));
  await page.evaluate(() => goToPage(2)); await sleep(500);
  ok(await page.evaluate(() => PDFViewerApplication.page) === 2, 'goToPage(2)');
  ok((await calls(page, 'onPageChange')).some(c => c[1] === 2), 'onPageChange(2) reported');

  // find
  await page.evaluate(() => startFind('lorem', false, false, false)); await sleep(1500);
  const fc = await calls(page, 'onFindMatchChange');
  ok(fc.some(c => c[2] >= 30), 'find reports >= 30 matches (' + (fc.at(-1) || []).slice(1) + ')');
  await page.evaluate(() => findNext()); await sleep(300);
  await page.evaluate(() => stopFind());

  // selection -> quads -> highlight overlay
  await page.evaluate(() => goToPage(1)); await sleep(800);
  const sel = await page.evaluate(() => {
    const span = [...document.querySelectorAll('.page[data-page-number="1"] .textLayer span')].find(s => s.textContent.includes('Lorem ipsum'));
    const r = document.createRange(); r.selectNodeContents(span);
    const s = getSelection(); s.removeAllRanges(); s.addRange(r);
    return getSelectionInfo();
  });
  const info = sel ? JSON.parse(sel) : null;
  ok(info && info.page === 1 && info.quads.length >= 1 && info.text.includes('Lorem'), 'selection info has page, text and quads');
  ok(info && info.quads[0].x > 0 && info.quads[0].x < 0.3 && info.quads[0].w > 0.2, 'quad is plausible (x=' + (info && info.quads[0].x.toFixed(3)) + ', w=' + (info && info.quads[0].w.toFixed(3)) + ')');
  await page.evaluate(q => applyStoredHighlights(JSON.stringify([{ id: 7, page: 1, color: 'rgba(255,255,0,.5)', quads: q }])), info.quads);
  const hl0 = await page.evaluate(() => { const e = document.querySelector('.kosh-hl'); const r = e.getBoundingClientRect(); const t = [...document.querySelectorAll('.page[data-page-number="1"] .textLayer span')].find(s => s.textContent.includes('Lorem ipsum')).getBoundingClientRect(); return { dx: Math.abs(r.left - t.left), dy: Math.abs((r.top + r.height / 2) - (t.top + t.height / 2)), n: document.querySelectorAll('.kosh-hl').length }; });
  ok(hl0.n >= 1 && hl0.dx < 4 && hl0.dy < 6, `overlay aligns with the text (dx=${hl0.dx.toFixed(1)}, dy=${hl0.dy.toFixed(1)})`);
  await page.evaluate(() => setRotation(90)); await sleep(800);
  const hl1 = await page.evaluate(() => { const e = document.querySelector('.kosh-hl'); const r = e.getBoundingClientRect(); const t = [...document.querySelectorAll('.page[data-page-number="1"] .textLayer span')].find(s => s.textContent.includes('Lorem ipsum')).getBoundingClientRect(); return { dx: Math.abs(r.left + r.width / 2 - (t.left + t.width / 2)), dy: Math.abs(r.top + r.height / 2 - (t.top + t.height / 2)) }; });
  ok(hl1.dx < 6 && hl1.dy < 6, `overlay still aligned after 90° rotation (dx=${hl1.dx.toFixed(1)}, dy=${hl1.dy.toFixed(1)})`);
  await page.evaluate(() => setRotation(0)); await sleep(500);

  // tap hit-testing
  await page.evaluate(() => { window.__calls.length = 0; });
  await page.evaluate(() => { getSelection().removeAllRanges(); document.querySelector('.kosh-hl').scrollIntoView({block:'center'}); }); await sleep(600);
  const pt = await page.evaluate(() => { const r = document.querySelector('.kosh-hl').getBoundingClientRect(); return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; });
  await page.mouse.click(pt.x, pt.y); await sleep(500);
  ok((await calls(page, 'onHighlightTapped')).length === 1, 'tapping a highlight reports onHighlightTapped');
  await page.evaluate(() => { window.__calls.length = 0; });
  await page.mouse.click(5, 690); await sleep(500);
  ok((await calls(page, 'onSingleClick')).length === 1, 'tap on empty area reports onSingleClick');
  await page.evaluate(() => { window.__calls.length = 0; });
  await page.mouse.click(5, 690); await sleep(80); await page.mouse.click(6, 691); await sleep(500);
  ok((await calls(page, 'onDoubleClick')).length === 1 && (await calls(page, 'onSingleClick')).length === 0, 'two quick taps report one onDoubleClick');

  // theme + snap + scroll mode
  await page.evaluate(() => { setReadingTheme('dark'); setSnap('y'); });
  ok(await page.evaluate(() => document.getElementById('kosh-theme').textContent.includes('invert')), 'dark theme filter applied');
  await page.evaluate(() => setScrollMode(1)); await sleep(500);
  ok(await page.evaluate(() => PDFViewerApplication.pdfViewer.scrollMode) === 1, 'horizontal scroll mode');
  await page.evaluate(() => setScrollMode(0));

  // auto scroll
  await page.evaluate(() => { setScaleValue('page-width'); scrollToOffset(0); startAutoScroll(200); }); await sleep(1200);
  const st = await page.evaluate(() => document.getElementById('viewerContainer').scrollTop);
  await page.evaluate(() => stopAutoScroll());
  ok(st > 100, 'auto scroll moved the document (scrollTop=' + st + ')');
  await page.close();

  // ---- password protected ----
  page = await open('enc.pdf');
  const pw = await waitFor(page, 'onPasswordDialogChange');
  ok(pw.length && pw[0][1] === true, 'encrypted PDF asks for a password');
  await page.evaluate(() => { window.__calls.length = 0; submitPassword('wrong'); }); await sleep(1000);
  ok((await calls(page, 'onPasswordDialogChange')).some(c => c[1] === true), 'wrong password asks again');
  await page.evaluate(() => submitPassword('secret'));
  const ls2 = await waitFor(page, 'onLoadSuccess');
  ok(ls2.length && ls2[0][1] === 3, 'correct password opens the document');
  await page.close();
  await browser.close(); server.close();
})().catch(e => { console.error('ERR', e); process.exit(2); });
