// Quads are {x, y, w, h} as fractions (0..1) of the page box, origin top-left, in the page's own (unrotated) frame.
// "display" quads are the same rectangle as seen at a clockwise page rotation of rot degrees (0, 90, 180, 270).
function koshCanonToDisplay(q, rot) {
  switch (((rot % 360) + 360) % 360) {
    case 90:  return { x: 1 - (q.y + q.h), y: q.x, w: q.h, h: q.w };
    case 180: return { x: 1 - (q.x + q.w), y: 1 - (q.y + q.h), w: q.w, h: q.h };
    case 270: return { x: q.y, y: 1 - (q.x + q.w), w: q.h, h: q.w };
    default:  return { x: q.x, y: q.y, w: q.w, h: q.h };
  }
}
function koshDisplayToCanon(d, rot) {
  switch (((rot % 360) + 360) % 360) {
    case 90:  return { x: d.y, y: 1 - d.x - d.w, w: d.h, h: d.w };
    case 180: return { x: 1 - d.x - d.w, y: 1 - d.y - d.h, w: d.w, h: d.h };
    case 270: return { x: 1 - d.y - d.h, y: d.x, w: d.h, h: d.w };
    default:  return { x: d.x, y: d.y, w: d.w, h: d.h };
  }
}
if (typeof module !== 'undefined') module.exports = { koshCanonToDisplay, koshDisplayToCanon };
