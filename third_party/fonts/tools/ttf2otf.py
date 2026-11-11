"""把 TrueType(glyf) 字体转换为子程序化的 CFF(OTF)，字形不变，CJK 体积通常明显更小。"""
import sys
from fontTools.ttLib import TTFont, newTable
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.pens.qu2cuPen import Qu2CuPen
from fontTools.fontBuilder import FontBuilder
import cffsubr

src, dst = sys.argv[1], sys.argv[2]
f = TTFont(src, recalcTimestamp=False)
order = f.getGlyphOrder()
gs = f.getGlyphSet()
hmtx = f['hmtx'].metrics
charstrings = {}
for name in order:
    pen = T2CharStringPen(width=hmtx[name][0], glyphSet=gs)
    gs[name].draw(Qu2CuPen(pen, max_err=1.0, all_cubic=True))
    charstrings[name] = pen.getCharString()

name = f['name']
ps = (name.getDebugName(6) or 'Font').replace(' ', '')
fb = FontBuilder(f['head'].unitsPerEm, isTTF=False)
fb.setupGlyphOrder(order)
fb.setupCFF(ps, {'FullName': name.getDebugName(4) or ps, 'FamilyName': name.getDebugName(1) or ps}, charstrings, {})
out = fb.font
out.recalcTimestamp = False
for tag in ['head', 'hhea', 'hmtx', 'maxp', 'OS/2', 'name', 'cmap', 'post', 'GSUB', 'GPOS', 'GDEF', 'BASE', 'vhea', 'vmtx', 'VORG']:
    if tag in f and tag not in ('maxp', 'post'):
        out[tag] = f[tag]
maxp = newTable('maxp'); maxp.tableVersion = 0x00005000; maxp.numGlyphs = len(order); out['maxp'] = maxp
post = f['post']; post.formatType = 3.0; out['post'] = post
out['head'].glyphDataFormat = 0
cffsubr.subroutinize(out)
out.save(dst)
print(dst, __import__('os').path.getsize(dst))
