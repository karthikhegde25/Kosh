// Minimal replacement for the pdf.js generic viewer application: wires pdf.js components together and exposes
// the PDFViewerApplication surface the helper scripts and the native side rely on.
import "./pdfjs_boot.js";
import { PDFViewer, EventBus, PDFLinkService, PDFFindController } from "./web/pdf_viewer.mjs";

const container = document.getElementById("viewerContainer");
const viewerEl = document.getElementById("viewer");
const eventBus = new EventBus();
const linkService = new PDFLinkService({ eventBus });
const findController = new PDFFindController({ linkService, eventBus, updateMatchesCountOnProgress: true });
const pdfViewer = new PDFViewer({
  container, viewer: viewerEl, eventBus, linkService, findController,
  textLayerMode: 1,
  annotationMode: 1,
  annotationEditorMode: -1,
  removePageBorders: false,
  // Pages are drawn sharp up to about 8 million pixels each. pdf.js also caps a page at ~3x the screen area by default,
  // which made pages go soft as soon as you zoomed in a little, so that extra cap is turned off (-1).
  maxCanvasPixels: 8 * 1024 * 1024,
  capCanvasAreaFactor: -1,
});
linkService.setViewer(pdfViewer);

// How many rendered pages pdf.js keeps in memory. Many small pages are fine; fewer when each page is a big canvas.
globalThis.koshCacheSize = function (defaultSize, visibleCount, visible) {
  var biggest = 0;
  for (var i = 0; i < visible.length; i++) {
    var c = visible[i].view && visible[i].view.canvas;
    if (c) biggest = Math.max(biggest, c.width * c.height);
  }
  if (!biggest) return defaultSize;
  return Math.max(3, Math.min(defaultSize, Math.floor(48e6 / biggest)));
};

const app = {
  eventBus, pdfViewer, findController, linkService,
  pdfDocument: null, loadingTask: null, passwordUpdate: null, baseUrl: "",
  get pagesCount() { return pdfViewer.pagesCount; },
  get page() { return pdfViewer.currentPageNumber; },
  set page(n) { pdfViewer.currentPageNumber = n; },
  async open(params) {
    if (app.loadingTask) { try { await app.loadingTask.destroy(); } catch (e) {} }
    app.pdfDocument = null;
    const base = new URL("./", import.meta.url).href;
    const task = pdfjsLib.getDocument({
      url: params.url,
      cMapUrl: base + "cmaps/", cMapPacked: true,
      standardFontDataUrl: base + "standard_fonts/",
      wasmUrl: base + "wasm/", iccUrl: base + "iccs/",
      disableRange: true, isEvalSupported: false,
    });
    app.loadingTask = task;
    task.onPassword = function (updatePassword, reason) {
      app.passwordUpdate = updatePassword;
      JWI.onPasswordDialogChange(true);
    };
    task.onProgress = function (p) {
      if (p && p.total) JWI.onProgressChange(Math.min(100, Math.round(100 * p.loaded / p.total)));
    };
    const doc = await task.promise;
    app.pdfDocument = doc;
    app.passwordUpdate = null;
    JWI.onPasswordDialogChange(false);
    pdfViewer.setDocument(doc);
    linkService.setDocument(doc, null);
    eventBus.dispatch("kosh-documentloaded", { source: app });
    return doc;
  },
};
window.PDFViewerApplication = app;
window.pdfjsViewerReady = true;
