/* PDF compression for the Tools tab, using pdf-lib: re-encodes the large pictures inside a PDF and saves it compactly. The result is sent back to the app in chunks (KEDIT bridge). */
var KEDIT_BRIDGE = (typeof KEDIT !== 'undefined') ? KEDIT : null;

function koshB64(u8) {
  var s = '', CH = 0x8000;
  for (var i = 0; i < u8.length; i += CH) s += String.fromCharCode.apply(null, u8.subarray(i, i + CH));
  return btoa(s);
}

async function koshFetchBytes(url) {
  var r = await fetch(url);
  if (!r.ok) throw new Error('Could not read the PDF (' + r.status + ')');
  return new Uint8Array(await r.arrayBuffer());
}

async function koshSend(bytes, original, pages) {
  KEDIT.begin(bytes.length);
  var CH = 300 * 1024;
  for (var o = 0; o < bytes.length; o += CH) {
    KEDIT.chunk(koshB64(bytes.subarray(o, Math.min(bytes.length, o + CH))));
    await new Promise(function (r) { setTimeout(r, 0); });
  }
  KEDIT.end(original, bytes.length, pages);
}

async function koshLoad(url) {
  var bytes = await koshFetchBytes(url);
  var doc;
  try {
    doc = await PDFLib.PDFDocument.load(bytes, { updateMetadata: false });
  } catch (e) {
    if (e && /encrypt/i.test(String(e.message || e))) throw new Error('This PDF is password protected.');
    throw e;
  }
  return { doc: doc, size: bytes.length };
}

/* ---- compress: re-encode large pictures, then save compactly ---- */
var KOSH_LEVELS = { light: { q: 0.8, max: 2400 }, balanced: { q: 0.68, max: 1700 }, strong: { q: 0.5, max: 1100 } };

function koshHasFilter(dict, name) {
  var f = dict.get(PDFLib.PDFName.of('Filter'));
  if (!f) return false;
  if (f instanceof PDFLib.PDFName) return f.decodeText() === name;
  if (f instanceof PDFLib.PDFArray) { var a = f.asArray(); return a.length === 1 && a[0] instanceof PDFLib.PDFName && a[0].decodeText() === name; }
  return false;
}
function koshNum(dict, key) { var v = dict.get(PDFLib.PDFName.of(key)); return v && typeof v.asNumber === 'function' ? v.asNumber() : 0; }
function koshName(dict, key) { var v = dict.get(PDFLib.PDFName.of(key)); return v instanceof PDFLib.PDFName ? v.decodeText() : null; }

async function koshToJpeg(canvasDraw, w, h, level) {
  var scale = Math.min(1, level.max / Math.max(w, h));
  var tw = Math.max(1, Math.round(w * scale)), th = Math.max(1, Math.round(h * scale));
  var canvas = document.createElement('canvas'); canvas.width = tw; canvas.height = th;
  var ctx = canvas.getContext('2d');
  ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, tw, th);
  canvasDraw(ctx, tw, th);
  var blob = await new Promise(function (r) { canvas.toBlob(r, 'image/jpeg', level.q); });
  var bytes = new Uint8Array(await blob.arrayBuffer());
  return { bytes: bytes, w: tw, h: th };
}

async function koshCompress(url, levelName) {
  var level = KOSH_LEVELS[levelName] || KOSH_LEVELS.balanced;
  var loaded = await koshLoad(url), doc = loaded.doc, ctx = doc.context;
  var P = PDFLib, images = [];
  ctx.enumerateIndirectObjects().forEach(function (pair) {
    var ref = pair[0], obj = pair[1];
    if (!(obj instanceof P.PDFRawStream)) return;
    var d = obj.dict;
    if (koshName(d, 'Subtype') !== 'Image') return;
    if (obj.contents.length < 30 * 1024) return;                       // small pictures aren't worth touching
    if (d.get(P.PDFName.of('SMask')) || d.get(P.PDFName.of('Mask'))) return;   // keep transparency exactly as is
    images.push({ ref: ref, obj: obj });
  });
  var done = 0, total = images.length;
  for (var i = 0; i < images.length; i++) {
    var im = images[i], d = im.obj.dict, w = koshNum(d, 'Width'), h = koshNum(d, 'Height'), made = null;
    try {
      if (w > 0 && h > 0) {
        if (koshHasFilter(d, 'DCTDecode')) {
          var bmp = await createImageBitmap(new Blob([im.obj.contents], { type: 'image/jpeg' }));
          made = await koshToJpeg(function (c, tw, th) { c.drawImage(bmp, 0, 0, tw, th); }, w, h, level);
          bmp.close && bmp.close();
        } else if (koshHasFilter(d, 'FlateDecode') && koshNum(d, 'BitsPerComponent') === 8 && !d.get(P.PDFName.of('DecodeParms'))) {
          var cs = koshName(d, 'ColorSpace'), comps = cs === 'DeviceRGB' ? 3 : (cs === 'DeviceGray' ? 1 : 0);
          if (comps) {
            var raw = P.decodePDFRawStream(im.obj).decode();
            if (raw.length === w * h * comps) {
              var rgba = new Uint8ClampedArray(w * h * 4);
              for (var p = 0, q = 0; p < w * h; p++, q += 4) {
                if (comps === 3) { rgba[q] = raw[p * 3]; rgba[q + 1] = raw[p * 3 + 1]; rgba[q + 2] = raw[p * 3 + 2]; }
                else { rgba[q] = rgba[q + 1] = rgba[q + 2] = raw[p]; }
                rgba[q + 3] = 255;
              }
              var src = document.createElement('canvas'); src.width = w; src.height = h;
              src.getContext('2d').putImageData(new ImageData(rgba, w, h), 0, 0);
              made = await koshToJpeg(function (c, tw, th) { c.drawImage(src, 0, 0, tw, th); }, w, h, level);
            }
          }
        }
      }
    } catch (e) { made = null; }
    if (made && made.bytes.length < im.obj.contents.length * 0.9) {
      var nd = P.PDFDict.withContext(ctx);
      nd.set(P.PDFName.of('Type'), P.PDFName.of('XObject'));
      nd.set(P.PDFName.of('Subtype'), P.PDFName.of('Image'));
      nd.set(P.PDFName.of('Width'), P.PDFNumber.of(made.w));
      nd.set(P.PDFName.of('Height'), P.PDFNumber.of(made.h));
      nd.set(P.PDFName.of('ColorSpace'), P.PDFName.of('DeviceRGB'));
      nd.set(P.PDFName.of('BitsPerComponent'), P.PDFNumber.of(8));
      nd.set(P.PDFName.of('Filter'), P.PDFName.of('DCTDecode'));
      ctx.assign(im.ref, P.PDFRawStream.of(nd, made.bytes));
    }
    done++; KEDIT.progress(done, total);
  }
  var bytes = await doc.save({ useObjectStreams: true });
  if (bytes.length >= loaded.size * 0.97) { KEDIT.noGain(loaded.size, bytes.length); return; }
  await koshSend(bytes, loaded.size, doc.getPageCount());
}

async function koshRun(kind, args) {
  try {
    if (kind === 'compress') await koshCompress(args.url, args.level);
    else throw new Error('Unknown operation');
  } catch (e) {
    KEDIT.fail(String((e && e.message) || e));
  }
}
