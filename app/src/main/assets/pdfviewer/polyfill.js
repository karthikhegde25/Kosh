// Small gaps older Android WebViews (110+) may have; the pdf.js legacy build covers the rest.
if (!URL.parse) {
  URL.parse = function (url, base) { try { return new URL(url, base); } catch (e) { return null; } };
}
if (!Promise.withResolvers) {
  Promise.withResolvers = function () {
    var resolve, reject;
    var promise = new Promise(function (a, b) { resolve = a; reject = b; });
    return { promise: promise, resolve: resolve, reject: reject };
  };
}
if (!AbortSignal.any) {
  AbortSignal.any = function (signals) {
    var c = new AbortController();
    signals.forEach(function (s) {
      if (s.aborted) { c.abort(s.reason); return; }
      s.addEventListener('abort', function () { c.abort(s.reason); }, { once: true });
    });
    return c.signal;
  };
}
// Phones report a pixel ratio of 3 or more. Drawing pages at 2x keeps text sharp but needs less than half the pixels,
// which makes zooming and scrolling much smoother.
try {
  if ((window.devicePixelRatio || 1) > 2) Object.defineProperty(window, 'devicePixelRatio', { get: function () { return 2; }, configurable: true });
} catch (e) {}
