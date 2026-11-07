/* Wires pdf.js events and touch gestures to the native side. Kotlin calls setupHelper() once the page has loaded. */
var KOSH_SETUP_DONE = false;
function setupHelper() {
  if (KOSH_SETUP_DONE) return;
  if (!window.pdfjsViewerReady) { setTimeout(setupHelper, 50); return; }
  KOSH_SETUP_DONE = true;
  var app = koshApp(), bus = app.eventBus, v = app.pdfViewer, c = koshContainer(), fc = app.findController;

  bus.on('kosh-documentloaded', function () {
    KOSH.loadSuccessSent = false;
    loadOutline(); loadAttachments(); sendDocumentProperties(); loadDocumentHighlights();
  });
  bus.on('pagesinit', function () { v.currentScaleValue = 'auto'; });
  bus.on('pagerendered', function () {
    if (!KOSH.loadSuccessSent) { KOSH.loadSuccessSent = true; JWI.onLoadSuccess(app.pagesCount); }
    requestAnimationFrame(koshRedrawHighlights);
  });
  bus.on('rotationchanging', function () { requestAnimationFrame(koshRedrawHighlights); });
  bus.on('pagechanging', function (e) { JWI.onPageChange(e.pageNumber); });
  var scaleTimer = 0;
  bus.on('scalechanging', function (e) {
    koshOnScaleForLock();
    clearTimeout(scaleTimer);
    scaleTimer = setTimeout(function () { JWI.onScaleChange(v.currentScale, String(v.currentScaleValue)); }, 120);
  });
  bus.on('updatefindcontrolstate', function (e) {
    var m = e.matchesCount || { current: 0, total: 0 };
    if (e.state === 3) JWI.onFindMatchStart(); else JWI.onFindMatchComplete(e.state !== 1);
    JWI.onFindMatchChange(m.current || 0, m.total || 0);
  });
  bus.on('updatefindmatchescount', function (e) {
    var m = e.matchesCount || { current: 0, total: 0 };
    JWI.onFindMatchChange(m.current || 0, m.total || 0);
  });

  // Centre the active search match vertically instead of pinning it to the top edge.
  var origScroll = fc.scrollMatchIntoView.bind(fc);
  fc.scrollMatchIntoView = function (o) {
    var el = o && o.element, before = fc._scrollMatches;
    origScroll(o);
    if (el && before && fc._scrollMatches === false) {
      requestAnimationFrame(function () {
        var r = el.getBoundingClientRect(), cr = c.getBoundingClientRect();
        c.scrollTop += (r.top + r.height / 2) - (cr.top + cr.height / 2);
        if (v.scrollMode === 1) c.scrollLeft += (r.left + r.width / 2) - (cr.left + cr.width / 2);
      });
    }
  };

  // Keep fit-to-width/page zooms correct when the window resizes (rotation, split screen).
  window.addEventListener('resize', function () {
    var val = v.currentScaleValue;
    if (!val || val === 'auto' || val === 'page-fit' || val === 'page-width') v.currentScaleValue = val || 'auto';
    v.update();
  });

  // Scroll reporting + horizontal lock. Calls into the app are synchronous, so they are sent a few times a second only.
  var scrollTimer = 0;
  c.addEventListener('scroll', function () {
    if (KOSH.lockEnabled && Date.now() >= KOSH.lockSuspendUntil && c.scrollLeft !== KOSH.lockLeft) c.scrollLeft = KOSH.lockLeft;
    if (scrollTimer) return;
    scrollTimer = setTimeout(function () {
      scrollTimer = 0;
      var h = v.scrollMode === 1;
      var offset = h ? c.scrollLeft : c.scrollTop;
      var total = h ? c.scrollWidth : c.scrollHeight;
      var room = total - (h ? c.clientWidth : c.clientHeight);
      JWI.onScroll(Math.round(offset), Math.round(total), room > 0 ? Math.min(1, Math.max(0, offset / room)) : 0, h);
    }, 120);
  }, { passive: true });

  // Text selection
  var selTimer = null;
  document.addEventListener('selectionchange', function () {
    clearTimeout(selTimer);
    selTimer = setTimeout(function () { JWI.onTextSelected(getSelectionInfo()); }, 120);
  });

  koshSetupDoodle(c);
  bus.on('pagerendered', function () { requestAnimationFrame(koshRedrawDoodles); });
  bus.on('rotationchanging', function () { requestAnimationFrame(koshRedrawDoodles); });

  // Taps: single / double / link / highlight
  var clickTimer = null, lastT = 0, lastX = 0, lastY = 0, ctxSuppress = false;
  document.addEventListener('contextmenu', function (e) { ctxSuppress = true; setTimeout(function () { ctxSuppress = false; }, 600); });
  c.addEventListener('click', function (e) {
    if (DOODLE.active) { e.stopPropagation(); return; }
    var link = e.target.closest && e.target.closest('a[href]');
    if (link) {
      var raw = link.getAttribute('href') || '';
      if (raw.charAt(0) === '#') return;           // internal destination: pdf.js handles it
      e.preventDefault(); e.stopPropagation();
    }
    if (ctxSuppress) return;
    var sel = window.getSelection();
    if (sel && !sel.isCollapsed) return;           // tapping while text is selected only clears the selection
    var now = Date.now(), x = e.clientX, y = e.clientY;
    if (clickTimer && now - lastT <= 300 && Math.hypot(x - lastX, y - lastY) < 48) {
      clearTimeout(clickTimer); clickTimer = null; lastT = 0;
      JWI.onDoubleClick(x, y); return;
    }
    lastT = now; lastX = x; lastY = y;
    clickTimer = setTimeout(function () {
      clickTimer = null;
      if (link) { JWI.onLinkClick(link.href); return; }
      var h = koshHitHighlight(x, y);
      if (h) { JWI.onHighlightTapped(JSON.stringify(h)); return; }
      JWI.onSingleClick(x, y, c.clientWidth, c.clientHeight);
    }, 300);
  }, true);

  // Long press + pinch zoom. All touch listeners are passive so normal scrolling is never held up by JavaScript.
  var lpTimer = null, pinch = null, pinchRaf = 0, pinchLast = null;
  function dist(t) { return Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY); }
  function applyPinch() {
    pinchRaf = 0;
    if (!pinch || !pinchLast) return;
    var target = Math.min(KOSH.maxScale, Math.max(KOSH.minScale, pinch.s * pinchLast.d / pinch.d));
    c.scrollLeft += pinch.cx - pinchLast.x; c.scrollTop += pinch.cy - pinchLast.y;   // two fingers also pan
    pinch.cx = pinchLast.x; pinch.cy = pinchLast.y;
    scalePageToAt(target, pinchLast.x, pinchLast.y, 300);
  }
  c.addEventListener('touchstart', function (e) {
    clearTimeout(lpTimer);
    if (e.touches.length === 1 && !DOODLE.active) {
      lpTimer = setTimeout(function () { JWI.onLongClick(); }, 500);
    } else if (e.touches.length === 2) {
      pinch = { d: dist(e.touches), s: v.currentScale, overflow: c.style.overflow,
        cx: (e.touches[0].clientX + e.touches[1].clientX) / 2, cy: (e.touches[0].clientY + e.touches[1].clientY) / 2 };
      c.style.overflow = 'hidden';   // no scrolling while two fingers pinch
    }
  }, { passive: true });
  c.addEventListener('touchmove', function (e) {
    if (pinch && e.touches.length === 2) {
      pinchLast = { d: dist(e.touches), x: (e.touches[0].clientX + e.touches[1].clientX) / 2, y: (e.touches[0].clientY + e.touches[1].clientY) / 2 };
      if (!pinchRaf) pinchRaf = requestAnimationFrame(applyPinch);
    } else { clearTimeout(lpTimer); }
  }, { passive: true });
  function end(e) {
    clearTimeout(lpTimer);
    if (pinch && e.touches.length < 2) {
      if (pinchRaf) { cancelAnimationFrame(pinchRaf); applyPinch(); }
      c.style.overflow = pinch.overflow || '';
      pinch = null; pinchLast = null;
    }
  }
  c.addEventListener('touchend', end, { passive: true });
  c.addEventListener('touchcancel', end, { passive: true });
}
