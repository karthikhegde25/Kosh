// No-op stand-in so the page also runs in a desktop browser (handy for debugging). In the app the real JWI is injected.
if (typeof window.JWI === 'undefined') {
  window.JWI = new Proxy({}, { get: function (_t, name) { return function () { console.log('JWI.' + String(name), Array.prototype.slice.call(arguments)); }; } });
}
