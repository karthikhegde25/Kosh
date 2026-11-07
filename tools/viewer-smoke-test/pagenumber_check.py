import io, subprocess, itertools, math
from pypdf import PdfWriter, PdfReader
from pypdf.generic import NameObject, DictionaryObject, ArrayObject, FloatObject, NumberObject, StreamObject

def number_origin(position, W, H, margin, tw, size):
    bottom, align = position
    x = margin if align == -1 else (W - margin - tw if align == 1 else (W - tw) / 2)
    y = H - margin if bottom else margin + size * 0.8
    return x, y

def user_point(rot, w, h, dx, dy):
    if rot == 90:  return w - dy, dx
    if rot == 180: return w - dx, h - dy
    if rot == 270: return dy, h - dx
    return dx, dy

def placement(position, rot, w, h, margin, tw, size):
    swap = rot in (90, 270)
    W, H = (h, w) if swap else (w, h)
    x, y_top = number_origin(position, W, H, margin, tw, size)
    ux, uy = user_point(rot, w, h, x, H - y_top)
    return ux, uy, rot

def make_pdf(path, w, h, rot, tm):
    wr = PdfWriter(); page = wr.add_blank_page(w, h)
    if rot: page[NameObject('/Rotate')] = NumberObject(rot)
    font = DictionaryObject({NameObject('/Type'): NameObject('/Font'), NameObject('/Subtype'): NameObject('/Type1'), NameObject('/BaseFont'): NameObject('/Helvetica')})
    page[NameObject('/Resources')] = DictionaryObject({NameObject('/Font'): DictionaryObject({NameObject('/F1'): wr._add_object(font)})})
    a, b, c, d, e, f = tm
    s = StreamObject(); s.set_data(f"BT /F1 24 Tf {a:.5f} {b:.5f} {c:.5f} {d:.5f} {e:.3f} {f:.3f} Tm (Page 7 of 9) Tj ET".encode())
    page[NameObject('/Contents')] = wr._add_object(s)
    wr.write(open(path, 'wb'))

def render(path):
    out = subprocess.run(['pdftoppm', '-r', '60', '-gray', '-png', path, path + '_img'], capture_output=True)
    from PIL import Image
    import glob
    return Image.open(glob.glob(path + '_img*.png')[0]).convert('L')

from PIL import ImageChops
W0, H0 = 595, 842
size = 24
tw = 0.0
# text width of the string in Helvetica 24pt (use poppler-independent estimate from AFM widths)
widths = {'P':667,'a':556,'g':556,'e':556,' ':278,'7':556,'o':556,'f':278,'9':556}
tw = sum(widths[ch] for ch in 'Page 7 of 9') / 1000 * size
positions = {'BL':(True,-1),'BC':(True,0),'BR':(True,1),'TL':(False,-1),'TC':(False,0),'TR':(False,1)}
bad = 0
for rot in (0, 90, 180, 270):
    for name, pos in positions.items():
        ux, uy, ang = placement(pos, rot, W0, H0, 28, tw, size)
        th = math.radians(ang)
        make_pdf(f'rot_{rot}_{name}.pdf', W0, H0, rot, (math.cos(th), math.sin(th), -math.sin(th), math.cos(th), ux, uy))
        # reference: an unrotated page with the display size and the text drawn upright at the display position
        swap = rot in (90, 270)
        Wd, Hd = (H0, W0) if swap else (W0, H0)
        x, ytop = number_origin(pos, Wd, Hd, 28, tw, size)
        make_pdf(f'ref_{rot}_{name}.pdf', Wd, Hd, 0, (1, 0, 0, 1, x, Hd - ytop))
        a = render(f'rot_{rot}_{name}.pdf'); b = render(f'ref_{rot}_{name}.pdf')
        if a.size != b.size: print('SIZE MISMATCH', rot, name, a.size, b.size); bad += 1; continue
        diff = ImageChops.difference(a, b).getbbox()
        dark = sum(1 for p in a.getdata() if p < 128)
        if diff is not None or dark < 20:
            print('MISMATCH', rot, name, diff, 'dark', dark); bad += 1
print('mismatches:', bad, 'of 24')
for rot in (0, 90, 180, 270):
    print(rot, [tuple(round(v, 2) for v in placement(p, rot, 595.0, 842.0, 28.0, 90.0, 12.0)[:2]) for p in positions.values()])
