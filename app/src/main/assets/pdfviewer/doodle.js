/* Doodle (freehand drawing) layer. Strokes are stored per page as fractions of the page box in the page's own unrotated
   frame (same convention as the highlight quads), so they stay put while scrolling, zooming and rotating.
   Saving asks pdf.js to write them into a NEW copy of the PDF as standard Ink annotations; the opened file is not touched. */
var DOODLE = { active: false, tool: 'pen', color: '#E53935', width: 0.006, strokes: [], history: [], cur: null, erased: null, pointerIds: {}, nextId: 1 };

function koshDoodleNotify() {
  JWI.onDoodleChanged(DOODLE.strokes.length, DOODLE.history.length);
}

function koshPageGeometry(pageEl) {
  var n = parseInt(pageEl.getAttribute('data-page-number'), 10);
  var pv = koshViewer().getPageView(n - 1);
  var box = (pageEl.querySelector('.canvasWrapper') || pageEl).getBoundingClientRect();
  if (!pv || !pv.viewport || !box.width || !box.height) return null;
  return { n: n, rot: pv.viewport.rotation || 0, box: box };
}

function koshDoodleSvg(pageEl, geo) {
  var svg = pageEl.querySelector('.kosh-doodle-layer');
  var H = 1000 * geo.box.height / geo.box.width;
  if (svg && svg.getAttribute('data-rot') === String(geo.rot) && Math.abs(parseFloat(svg.getAttribute('data-h')) - H) < 0.5) return svg;
  if (svg) svg.remove();
  svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', 'kosh-doodle-layer');
  svg.setAttribute('viewBox', '0 0 1000 ' + H);
  svg.setAttribute('preserveAspectRatio', 'none');
  svg.setAttribute('data-rot', String(geo.rot));
  svg.setAttribute('data-h', String(H));
  pageEl.appendChild(svg);
  return svg;
}

// Stroke width is stored as a fraction of the page's own width; in the display it is that many "canonical width" units.
function koshStrokeUnits(s, geo) {
  var H = 1000 * geo.box.height / geo.box.width;
  return s.width * (geo.rot % 180 === 0 ? 1000 : H);
}

function koshStrokePath(s, geo) {
  var H = 1000 * geo.box.height / geo.box.width, d = '';
  for (var i = 0; i < s.pts.length; i++) {
    var p = koshCanonToDisplay({ x: s.pts[i][0], y: s.pts[i][1], w: 0, h: 0 }, geo.rot);
    d += (i === 0 ? 'M' : 'L') + (p.x * 1000).toFixed(2) + ' ' + (p.y * H).toFixed(2);
  }
  if (s.pts.length === 1) d += 'l0.01 0';
  return d;
}

function koshDoodleStyle(path, s, geo) {
  path.setAttribute('fill', 'none');
  path.setAttribute('stroke', s.color);
  path.setAttribute('stroke-width', koshStrokeUnits(s, geo).toFixed(3));
  path.setAttribute('stroke-linecap', 'round');
  path.setAttribute('stroke-linejoin', 'round');
}

function koshRedrawDoodles() {
  if (!DOODLE.strokes.length && !document.querySelector('.kosh-doodle-layer')) return;
  var pages = document.querySelectorAll('#viewer .page');
  for (var i = 0; i < pages.length; i++) {
    var pageEl = pages[i], old = pageEl.querySelector('.kosh-doodle-layer');
    var n = parseInt(pageEl.getAttribute('data-page-number'), 10);
    var mine = DOODLE.strokes.filter(function (s) { return s.page === n; });
    if (!mine.length) { if (old) old.remove(); continue; }
    var geo = koshPageGeometry(pageEl); if (!geo) continue;
    if (old) old.remove();
    var svg = koshDoodleSvg(pageEl, geo);
    mine.forEach(function (s) {
      var p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      p.setAttribute('d', koshStrokePath(s, geo)); koshDoodleStyle(p, s, geo); p.setAttribute('data-id', String(s.id));
      svg.appendChild(p);
    });
  }
}

function setDoodleMode(on) {
  DOODLE.active = !!on;
  koshContainer().classList.toggle('doodling', !!on);
  if (on) { var s = window.getSelection(); if (s) s.removeAllRanges(); }
  if (!on) { DOODLE.cur = null; DOODLE.erased = null; DOODLE.pointerIds = {}; }
}
function setDoodleTool(tool) { DOODLE.tool = tool === 'eraser' ? 'eraser' : 'pen'; }
function setDoodleColor(css) { DOODLE.color = css; }
function setDoodleWidth(frac) { if (frac > 0) DOODLE.width = frac; }
function clearDoodles() { DOODLE.strokes = []; DOODLE.history = []; koshRedrawDoodles(); koshDoodleNotify(); }

function doodleUndo() {
  var h = DOODLE.history.pop(); if (!h) return;
  if (h.type === 'add') DOODLE.strokes = DOODLE.strokes.filter(function (s) { return s.id !== h.stroke.id; });
  else DOODLE.strokes = DOODLE.strokes.concat(h.strokes);
  koshRedrawDoodles(); koshDoodleNotify();
}

// ---- pointer handling ----
function koshPointOnPage(e, geo) {
  var fx = Math.min(1, Math.max(0, (e.clientX - geo.box.left) / geo.box.width));
  var fy = Math.min(1, Math.max(0, (e.clientY - geo.box.top) / geo.box.height));
  var c = koshDisplayToCanon({ x: fx, y: fy, w: 0, h: 0 }, geo.rot);
  return [c.x, c.y];
}

function koshDistToSegment(px, py, ax, ay, bx, by) {
  var dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy, t = l2 ? ((px - ax) * dx + (py - ay) * dy) / l2 : 0;
  t = Math.max(0, Math.min(1, t));
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}

function koshEraseAt(e) {
  var cur = DOODLE.cur; if (!cur) return;
  var geo = cur.geo, radius = 16, hit = [];
  DOODLE.strokes.forEach(function (s) {
    if (s.page !== geo.n) return;
    var half = s.width * (geo.rot % 180 === 0 ? geo.box.width : geo.box.height) / 2 + radius;
    var prev = null;
    for (var i = 0; i < s.pts.length; i++) {
      var d = koshCanonToDisplay({ x: s.pts[i][0], y: s.pts[i][1], w: 0, h: 0 }, geo.rot);
      var x = geo.box.left + d.x * geo.box.width, y = geo.box.top + d.y * geo.box.height;
      var dist = prev ? koshDistToSegment(e.clientX, e.clientY, prev[0], prev[1], x, y) : Math.hypot(e.clientX - x, e.clientY - y);
      if (dist <= half) { hit.push(s); return; }
      prev = [x, y];
    }
  });
  if (!hit.length) return;
  DOODLE.strokes = DOODLE.strokes.filter(function (s) { return hit.indexOf(s) < 0; });
  DOODLE.erased = (DOODLE.erased || []).concat(hit);
  hit.forEach(function (s) { var el = document.querySelector('.kosh-doodle-layer path[data-id="' + s.id + '"]'); if (el) el.remove(); });
  koshDoodleNotify();
}

function koshSetupDoodle(container) {
  container.addEventListener('contextmenu', function (e) { if (DOODLE.active) e.preventDefault(); });
  container.addEventListener('pointerdown', function (e) {
    if (!DOODLE.active) return;
    DOODLE.pointerIds[e.pointerId] = true;
    if (Object.keys(DOODLE.pointerIds).length > 1) {          // a second finger means pan / zoom, not drawing
      if (DOODLE.cur && DOODLE.cur.stroke) { var el = document.querySelector('.kosh-doodle-layer path[data-id="' + DOODLE.cur.stroke.id + '"]'); if (el) el.remove(); }
      DOODLE.cur = null; DOODLE.erased = null; return;
    }
    var target = document.elementFromPoint(e.clientX, e.clientY), pageEl = target && target.closest && target.closest('.page');
    if (!pageEl) return;
    var geo = koshPageGeometry(pageEl); if (!geo) return;
    e.preventDefault();
    try { container.setPointerCapture(e.pointerId); } catch (x) {}
    if (DOODLE.tool === 'eraser') { DOODLE.cur = { geo: geo, erase: true }; DOODLE.erased = []; koshEraseAt(e); return; }
    var stroke = { id: DOODLE.nextId++, page: geo.n, color: DOODLE.color, width: DOODLE.width, pts: [koshPointOnPage(e, geo)] };
    var svg = koshDoodleSvg(pageEl, geo), path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    koshDoodleStyle(path, stroke, geo); path.setAttribute('data-id', String(stroke.id)); path.setAttribute('d', koshStrokePath(stroke, geo));
    svg.appendChild(path);
    DOODLE.cur = { geo: geo, stroke: stroke, path: path };
  }, { passive: false });
  container.addEventListener('pointermove', function (e) {
    var cur = DOODLE.cur; if (!DOODLE.active || !cur) return;
    e.preventDefault();
    if (cur.erase) { koshEraseAt(e); return; }
    var evs = (e.getCoalescedEvents && e.getCoalescedEvents()) || [e];
    if (!evs.length) evs = [e];
    evs.forEach(function (ev) {
      var p = koshPointOnPage(ev, cur.geo), last = cur.stroke.pts[cur.stroke.pts.length - 1];
      if (Math.hypot(p[0] - last[0], p[1] - last[1]) < 0.0005) return;
      cur.stroke.pts.push(p);
    });
    cur.path.setAttribute('d', koshStrokePath(cur.stroke, cur.geo));
  }, { passive: false });
  function up(e) {
    delete DOODLE.pointerIds[e.pointerId];
    var cur = DOODLE.cur; if (!DOODLE.active || !cur) return;
    DOODLE.cur = null;
    if (cur.erase) {
      if (DOODLE.erased && DOODLE.erased.length) { DOODLE.history.push({ type: 'erase', strokes: DOODLE.erased }); }
      DOODLE.erased = null; koshDoodleNotify(); return;
    }
    DOODLE.strokes.push(cur.stroke); DOODLE.history.push({ type: 'add', stroke: cur.stroke });
    koshDoodleNotify();
  }
  container.addEventListener('pointerup', up);
  container.addEventListener('pointercancel', function (e) {
    delete DOODLE.pointerIds[e.pointerId];
    var cur = DOODLE.cur; if (cur && cur.stroke) { var el = document.querySelector('.kosh-doodle-layer path[data-id="' + cur.stroke.id + '"]'); if (el) el.remove(); }
    DOODLE.cur = null;
  });
}

// ---- saving: write the strokes into a new copy of the PDF as Ink annotations ----
function koshHexToRgb(h) {
  var m = /^#?([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(h || '');
  return m ? [parseInt(m[1], 16), parseInt(m[2], 16), parseInt(m[3], 16)] : [0, 0, 0];
}

async function saveDoodled() {
  var doc = koshApp().pdfDocument, storage = doc && doc.annotationStorage, keys = [];
  try {
    if (!doc || !DOODLE.strokes.length) { JWI.onSaveFailed('Nothing to save'); return; }
    DOODLE.strokes.forEach(function (s, i) {
      var pv = koshViewer().getPageView(s.page - 1), view = pv && pv.pdfPage && pv.pdfPage.view;
      if (!view) return;
      var w = view[2] - view[0], h = view[3] - view[1], thickness = Math.max(0.5, s.width * w);
      var pts = [], lines = [], minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
      s.pts.forEach(function (p, j) {
        var x = view[0] + p[0] * w, y = view[3] - p[1] * h;
        pts.push(x, y);
        minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        if (j === 0) lines.push(NaN, NaN, NaN, NaN, x, y); else lines.push(NaN, NaN, NaN, NaN, x, y);
      });
      if (s.pts.length === 1) { pts.push(pts[0], pts[1]); }
      var pad = thickness;
      var key = 'pdfjs_internal_editor_kosh_' + i;
      storage.setValue(key, {
        annotationType: 15, pageIndex: s.page - 1, rotation: 0, opacity: 1, thickness: thickness,
        color: koshHexToRgb(s.color), rect: [minX - pad, minY - pad, maxX + pad, maxY + pad],
        paths: { lines: [lines], points: [pts] }, structTreeParentId: null, isCopy: false
      });
      keys.push(key);
    });
    var bytes = await doc.saveDocument();
    JWI.onSaveBegin(bytes.length);
    var CH = 300 * 1024;
    for (var o = 0; o < bytes.length; o += CH) {
      JWI.onSaveChunk(koshToBase64(bytes.subarray(o, Math.min(bytes.length, o + CH))));
      await new Promise(function (r) { setTimeout(r, 0); });
    }
    JWI.onSaveEnd();
  } catch (e) {
    JWI.onSaveFailed(String((e && e.message) || e));
  } finally {
    keys.forEach(function (k) { try { storage.remove(k); } catch (e) {} });
  }
}
