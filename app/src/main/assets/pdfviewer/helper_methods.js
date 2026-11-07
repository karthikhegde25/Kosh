/* Commands called from Kotlin (via evaluateJavascript). Pages are 1-based here, as in pdf.js. */
var KOSH = { minScale: 0.1, maxScale: 10, lockEnabled: false, lockLeft: 0, lockSuspendUntil: 0,
  highlights: [], lastFind: null, attachments: {}, loadSuccessSent: false };

function koshApp() { return window.PDFViewerApplication; }
function koshViewer() { return window.PDFViewerApplication.pdfViewer; }
function koshContainer() { return document.getElementById('viewerContainer'); }

function openUrl(arg) {
  var p = (typeof arg === 'string') ? JSON.parse(arg) : arg;
  koshApp().open(p).catch(function (e) {
    var msg = String((e && e.message) || e);
    if (msg !== 'Failed to fetch') JWI.onLoadFailed(msg, (e && e.name) || '');
  });
}

/* ---- scale ---- */
function setScaleLimits(min, max) { if (min > 0) KOSH.minScale = min; if (max > 0) KOSH.maxScale = max; }

function getActualScaleFor(token) {
  var v = koshViewer(), pv = v.getPageView(v.currentPageNumber - 1);
  if (!pv || !pv.width) return -1;
  var c = koshContainer(), cs = getComputedStyle(c);
  var horizontal = v.scrollMode === 1;
  var hPad = horizontal ? 5 : 40, vPad = horizontal ? 40 : 5;
  var padX = (parseFloat(cs.paddingLeft) || 0) + (parseFloat(cs.paddingRight) || 0);
  var padY = (parseFloat(cs.paddingTop) || 0) + (parseFloat(cs.paddingBottom) || 0);
  var pw = (c.clientWidth - padX - hPad) / pv.width * pv.scale;
  var ph = (c.clientHeight - padY - vPad) / pv.height * pv.scale;
  switch (token) {
    case 'page-width': return pw;
    case 'page-height': return ph;
    case 'page-fit': return Math.min(pw, ph);
    case 'page-actual': return 1;
    case 'auto': return pv.width < pv.height ? Math.min(1.25, pw) : Math.min(1.25, Math.min(pw, ph));
    default: var n = parseFloat(token); return isNaN(n) ? -1 : n;
  }
}
function setScaleValue(token) { koshViewer().currentScaleValue = token; }
function scalePageTo(scale) { koshViewer().currentScale = scale; }
// delay: ms to wait before re-drawing the pages sharply. Until then the pages are only stretched (instant, no re-render).
function scalePageToAt(scale, x, y, delay) {
  var v = koshViewer(), cur = v.currentScale;
  if (!cur || cur <= 0) { v.currentScale = scale; return; }
  var f = scale / cur;
  if (Math.abs(f - 1) < 1e-6) return;
  v.updateScale({ scaleFactor: f, origin: [x, y], drawingDelay: delay === undefined ? 150 : delay });
}
function zoomIn() { koshViewer().increaseScale({ drawingDelay: 150 }); }
function zoomOut() { koshViewer().decreaseScale({ drawingDelay: 150 }); }

/* ---- layout ---- */
function setScrollMode(n) {
  var v = koshViewer();
  if (n === 1 && KOSH.lockEnabled) KOSH.lockEnabled = false;
  v.scrollMode = n;
  if (n === 1) v.spreadMode = 0;
}
function setRotation(deg) { koshViewer().pagesRotation = ((deg % 360) + 360) % 360; }
function setSnap(mode) {
  var c = koshContainer();
  c.classList.remove('snap-x', 'snap-y');
  if (mode === 'y') c.classList.add('snap-y'); else if (mode === 'x') c.classList.add('snap-x');
}
function setHorizontalScrollLock(enabled) {
  var c = koshContainer();
  if (enabled && koshViewer().scrollMode === 1) enabled = false;
  KOSH.lockEnabled = !!enabled;
  KOSH.lockLeft = c.scrollLeft;
}
function koshOnScaleForLock() {
  if (!KOSH.lockEnabled) return;
  var c = koshContainer();
  KOSH.lockSuspendUntil = Date.now() + 300;
  setTimeout(function () {
    var max = Math.max(0, c.scrollWidth - c.clientWidth);
    KOSH.lockLeft = Math.min(KOSH.lockLeft, max);
    c.scrollLeft = KOSH.lockLeft;
  }, 320);
}
function setReadingTheme(name) {
  var filters = { light: 'none', dark: 'invert(0.85) hue-rotate(180deg)', sepia: 'sepia(0.3) brightness(0.95)', black: 'invert(1) hue-rotate(180deg)' };
  var f = filters[name] || 'none';
  var st = document.getElementById('kosh-theme');
  if (!st) { st = document.createElement('style'); st.id = 'kosh-theme'; document.head.appendChild(st); }
  st.textContent = f === 'none' ? '' : '.pdfViewer .page { filter: ' + f + '; }';
}
function setContainerBackgroundColor(css) { koshContainer().style.backgroundColor = css; }
function setContentPadding(top, bottom) {
  var c = koshContainer(); c.style.paddingTop = top + 'px'; c.style.paddingBottom = bottom + 'px';
}

/* ---- scrolling ---- */
function scrollToOffset(px) { koshContainer().scrollTop = px; }
function scrollToRatio(ratio, horizontal) {
  var c = koshContainer();
  if (horizontal) c.scrollLeft = (c.scrollWidth - c.clientWidth) * ratio; else c.scrollTop = (c.scrollHeight - c.clientHeight) * ratio;
}
function goToPage(n) { var a = koshApp(); if (n >= 1 && n <= a.pagesCount) { a.page = n; return true; } return false; }

/* ---- auto scroll ---- */
var KAS = { active: false, paused: false, speed: 50, acc: 0, last: 0 };
function koshSnapOff() {
  var c = koshContainer();
  KAS.snap = c.classList.contains('snap-y') ? 'y' : (c.classList.contains('snap-x') ? 'x' : 'none');
  c.classList.remove('snap-x', 'snap-y');   // scroll snapping would undo the small steps
}
function startAutoScroll(speed) { if (!KAS.active) koshSnapOff(); KAS.speed = speed; KAS.active = true; KAS.paused = false; KAS.acc = 0; KAS.last = 0; requestAnimationFrame(koshAutoLoop); }
function stopAutoScroll() { if (KAS.active && KAS.snap) setSnap(KAS.snap); KAS.active = false; KAS.paused = false; KAS.last = 0; }
function pauseAutoScroll() { KAS.paused = true; KAS.last = 0; }
function resumeAutoScroll() { KAS.paused = false; KAS.last = 0; }
function setAutoScrollSpeed(speed) { KAS.speed = speed; }
function koshAutoLoop(t) {
  if (!KAS.active) return;
  requestAnimationFrame(koshAutoLoop);
  if (KAS.paused) { KAS.last = 0; return; }
  if (!KAS.last) { KAS.last = t; return; }
  KAS.acc += KAS.speed * (t - KAS.last) / 1000; KAS.last = t;
  if (KAS.acc < 1) return;
  var n = Math.floor(KAS.acc); KAS.acc -= n;
  var c = koshContainer(), h = koshViewer().scrollMode === 1;
  var before = h ? c.scrollLeft : c.scrollTop;
  if (h) c.scrollLeft = before + n; else c.scrollTop = before + n;
  var after = h ? c.scrollLeft : c.scrollTop;
  var max = h ? c.scrollWidth - c.clientWidth : c.scrollHeight - c.clientHeight;
  if (after === before && after >= max - 1) { stopAutoScroll(); JWI.onAutoScrollEnd(); }
}

/* ---- find ---- */
function koshDispatchFind(type, previous) {
  var f = KOSH.lastFind; if (!f) return;
  koshApp().eventBus.dispatch('find', { source: window, type: type, query: f.query, caseSensitive: f.matchCase,
    entireWord: f.entireWord, matchDiacritics: f.diacritics, highlightAll: true, findPrevious: previous });
}
function startFind(query, matchCase, entireWord, diacritics) {
  KOSH.lastFind = { query: query, matchCase: !!matchCase, entireWord: !!entireWord, diacritics: !!diacritics };
  koshDispatchFind('', false);
}
function findNext() { koshDispatchFind('again', false); }
function findPrevious() { koshDispatchFind('again', true); }
function stopFind() {
  KOSH.lastFind = null;
  koshApp().eventBus.dispatch('find', { source: window, type: '', query: '', caseSensitive: false, entireWord: false,
    matchDiacritics: false, highlightAll: false, findPrevious: false });
}
function submitPassword(pw) { var u = koshApp().passwordUpdate; if (u) u(pw); }

/* ---- outline, attachments, properties ---- */
async function loadOutline() {
  var doc = koshApp().pdfDocument;
  try {
    var outline = await doc.getOutline();
    if (!outline) { JWI.onOutlineLoaded('[]'); return; }
    var conv = async function (items) {
      var out = [];
      for (var i = 0; i < items.length; i++) {
        var it = items[i], page = 0;
        try {
          var dest = it.dest;
          if (typeof dest === 'string') dest = await doc.getDestination(dest);
          if (Array.isArray(dest)) {
            var ref = dest[0];
            page = (ref && typeof ref === 'object') ? await doc.getPageIndex(ref) : (ref | 0);
          }
        } catch (e) { page = 0; }
        out.push({ title: it.title || '', page: page, id: 'outlineItem-' + Math.random().toString(36).slice(2), children: await conv(it.items || []) });
      }
      return out;
    };
    JWI.onOutlineLoaded(JSON.stringify(await conv(outline)));
  } catch (e) { JWI.onOutlineLoaded('[]'); }
}
async function loadAttachments() {
  try {
    var att = await koshApp().pdfDocument.getAttachments();
    var list = [], i = 0; KOSH.attachments = {};
    for (var k in (att || {})) {
      var id = 'attachmentItem-' + (i++); KOSH.attachments[id] = att[k];
      list.push({ id: id, title: att[k].filename || k });
    }
    JWI.onAttachmentsLoaded(JSON.stringify(list));
  } catch (e) { JWI.onAttachmentsLoaded('[]'); }
}
function koshToBase64(u8) {
  var s = '', CH = 0x8000;
  for (var i = 0; i < u8.length; i += CH) s += String.fromCharCode.apply(null, u8.subarray(i, i + CH));
  return btoa(s);
}
function downloadAttachment(id) {
  var a = KOSH.attachments[id]; if (!a || !a.content) return;
  JWI.handleBase64Data(koshToBase64(a.content), a.filename || 'attachment', '');
}
async function sendDocumentProperties() {
  var doc = koshApp().pdfDocument;
  try {
    var m = await doc.getMetadata(), info = m.info || {};
    var dl = {}; try { dl = await doc.getDownloadInfo(); } catch (e) {}
    var t = function (v) { return (v === undefined || v === null || v === '') ? '-' : String(v); };
    JWI.onLoadProperties(JSON.stringify({
      title: t(info.Title), subject: t(info.Subject), author: t(info.Author), creator: t(info.Creator), producer: t(info.Producer),
      creationDate: t(info.CreationDate), modDate: t(info.ModDate), keywords: t(info.Keywords), language: t(info.Language),
      pdfVersion: t(info.PDFFormatVersion), size: (dl && dl.length) || 0, linearized: !!info.IsLinearized,
      encryptFilterName: info.EncryptFilterName || '', acroForm: !!info.IsAcroFormPresent, collection: !!info.IsCollectionPresent,
      signatures: !!info.IsSignaturesPresent, xfa: !!info.IsXFAPresent, custom: info.Custom || {}
    }));
  } catch (e) { /* properties are optional */ }
}

/* ---- text selection ---- */
function koshEl(n) { return n && (n.nodeType === 1 ? n : n.parentElement); }
function getSelectionInfo() {
  var sel = window.getSelection();
  if (!sel || sel.rangeCount === 0 || sel.isCollapsed) return '';
  var text = sel.toString(); if (!text.trim()) return '';
  var range = sel.getRangeAt(0), startEl = koshEl(range.startContainer);
  var pageEl = startEl && startEl.closest('.page'); if (!pageEl) return '';
  var pageNum = parseInt(pageEl.getAttribute('data-page-number'), 10);
  var pv = koshViewer().getPageView(pageNum - 1); if (!pv || !pv.viewport) return '';
  var rot = pv.viewport.rotation || 0;
  var box = (pageEl.querySelector('.canvasWrapper') || pageEl).getBoundingClientRect();
  if (!box.width || !box.height) return '';
  var rects = range.getClientRects(), quads = [], u = null;
  for (var i = 0; i < rects.length; i++) {
    var r = rects[i]; if (r.width <= 0 || r.height <= 0) continue;
    var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    if (cx < box.left || cx > box.right || cy < box.top || cy > box.bottom) continue;
    var l = Math.max(r.left, box.left), tp = Math.max(r.top, box.top), rt = Math.min(r.right, box.right), bt = Math.min(r.bottom, box.bottom);
    quads.push(koshDisplayToCanon({ x: (l - box.left) / box.width, y: (tp - box.top) / box.height, w: (rt - l) / box.width, h: (bt - tp) / box.height }, rot));
    u = u ? { l: Math.min(u.l, l), t: Math.min(u.t, tp), r: Math.max(u.r, rt), b: Math.max(u.b, bt) } : { l: l, t: tp, r: rt, b: bt };
  }
  if (!quads.length) return '';
  var cr = koshContainer().getBoundingClientRect();
  return JSON.stringify({ page: pageNum, text: text, quads: quads, anchor: { x: u.l - cr.left, y: u.t - cr.top, w: u.r - u.l, h: u.b - u.t } });
}
function clearTextSelection() { var s = window.getSelection(); if (s) s.removeAllRanges(); }

/* ---- app highlights overlay ---- */
function koshRedrawHighlights() {
  if (!KOSH.highlights.length && !document.querySelector('.kosh-hl-layer')) return;
  var pages = document.querySelectorAll('#viewer .page');
  for (var i = 0; i < pages.length; i++) {
    var pageEl = pages[i], old = pageEl.querySelector('.kosh-hl-layer'); if (old) old.remove();
    var n = parseInt(pageEl.getAttribute('data-page-number'), 10);
    var pv = koshViewer().getPageView(n - 1); if (!pv || !pv.viewport) continue;
    var rot = pv.viewport.rotation || 0, layer = null;
    for (var j = 0; j < KOSH.highlights.length; j++) {
      var h = KOSH.highlights[j]; if (h.page !== n) continue;
      if (!layer) { layer = document.createElement('div'); layer.className = 'kosh-hl-layer'; pageEl.appendChild(layer); }
      for (var k = 0; k < h.quads.length; k++) {
        var d = koshCanonToDisplay(h.quads[k], rot), el = document.createElement('div');
        el.className = 'kosh-hl'; el.setAttribute('data-id', String(h.id)); el.style.background = h.color;
        el.style.left = (d.x * 100) + '%'; el.style.top = (d.y * 100) + '%'; el.style.width = (d.w * 100) + '%'; el.style.height = (d.h * 100) + '%';
        layer.appendChild(el);
      }
    }
  }
}
function applyStoredHighlights(json) { KOSH.highlights = JSON.parse(json) || []; koshRedrawHighlights(); }
function koshHitHighlight(x, y) {
  var els = document.querySelectorAll('.kosh-hl'), pad = 6, hit = null;
  for (var i = 0; i < els.length; i++) {
    var r = els[i].getBoundingClientRect();
    if (x >= r.left - pad && x <= r.right + pad && y >= r.top - pad && y <= r.bottom + pad) { hit = els[i].getAttribute('data-id'); }
  }
  if (hit === null) return null;
  var all = document.querySelectorAll('.kosh-hl[data-id="' + hit + '"]'), u = null, cr = koshContainer().getBoundingClientRect();
  for (var j = 0; j < all.length; j++) {
    var b = all[j].getBoundingClientRect();
    u = u ? { l: Math.min(u.l, b.left), t: Math.min(u.t, b.top), r: Math.max(u.r, b.right), b: Math.max(u.b, b.bottom) } : { l: b.left, t: b.top, r: b.right, b: b.bottom };
  }
  return { id: parseInt(hit, 10), x: u.l - cr.left, y: u.t - cr.top, w: u.r - u.l, h: u.b - u.t };
}
function scrollToHighlight(id) {
  var els = document.querySelectorAll('.kosh-hl[data-id="' + id + '"]'); if (!els.length) return;
  els[0].scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' });
  for (var i = 0; i < els.length; i++) { els[i].classList.remove('pulse'); void els[i].offsetWidth; els[i].classList.add('pulse'); }
}
function scrollToPageQuad(page, quad) {
  var pv = koshViewer().getPageView(page - 1); if (!pv || !pv.div) return;
  var d = koshCanonToDisplay(quad, pv.viewport.rotation || 0), c = koshContainer(), div = pv.div;
  c.scrollTo({ top: div.offsetTop + (d.y + d.h / 2) * div.clientHeight - c.clientHeight / 2,
    left: div.offsetLeft + (d.x + d.w / 2) * div.clientWidth - c.clientWidth / 2, behavior: 'smooth' });
}

/* ---- highlights already stored in the PDF (read-only) ---- */
function koshFlattenQuad(q) {
  var out = [];
  for (var i = 0; i < q.length; i++) {
    var p = q[i];
    if (typeof p === 'number') out.push(p); else if (p && typeof p === 'object') { if ('x' in p) { out.push(p.x, p.y); } else { out.push(p[0], p[1]); } }
  }
  return out;
}
async function loadDocumentHighlights() {
  var doc = koshApp().pdfDocument, result = [];
  try {
    for (var p = 1; p <= doc.numPages; p++) {
      var page = await doc.getPage(p), annots = await page.getAnnotations({ intent: 'display' });
      var hl = annots.filter(function (a) { return a.subtype === 'Highlight' && a.quadPoints && koshFlattenQuad(a.quadPoints).length >= 8; });
      if (!hl.length) continue;
      var view = page.view, w = view[2] - view[0], h = view[3] - view[1], tc = await page.getTextContent();
      for (var i = 0; i < hl.length; i++) {
        var a = hl[i], pts = koshFlattenQuad(a.quadPoints), quads = [], boxes = [];
        for (var q = 0; q + 7 < pts.length; q += 8) {
          var xs = [pts[q], pts[q + 2], pts[q + 4], pts[q + 6]], ys = [pts[q + 1], pts[q + 3], pts[q + 5], pts[q + 7]];
          var L = Math.min.apply(null, xs), R = Math.max.apply(null, xs), B = Math.min.apply(null, ys), T = Math.max.apply(null, ys);
          boxes.push([L, B, R, T]);
          quads.push({ x: (L - view[0]) / w, y: (view[3] - T) / h, w: (R - L) / w, h: (T - B) / h });
        }
        var parts = [];
        tc.items.forEach(function (it) {
          if (!it.str || !it.transform) return;
          var cx = it.transform[4] + (it.width || 0) / 2, cy = it.transform[5] + (it.height || Math.abs(it.transform[3])) / 2;
          for (var b = 0; b < boxes.length; b++) { if (cx >= boxes[b][0] && cx <= boxes[b][2] && cy >= boxes[b][1] && cy <= boxes[b][3]) { parts.push(it.str); break; } }
        });
        var col = '#FFFF98';
        if (a.color && a.color.length >= 3) col = '#' + [a.color[0], a.color[1], a.color[2]].map(function (v) { return ('0' + (v | 0).toString(16)).slice(-2); }).join('');
        result.push({ id: -(p * 1000 + i + 1), page: p, text: parts.join(' ').replace(/\s+/g, ' ').trim(), quads: quads, color: col,
          note: (a.contentsObj && a.contentsObj.str) || '', label: (a.titleObj && a.titleObj.str) || '' });
      }
    }
  } catch (e) { /* document highlights are optional */ }
  JWI.onDocumentHighlightsLoaded(JSON.stringify(result));
}
