import sys, os, re, glob
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools import subset

src, repo, out = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(out, exist_ok=True)

# ---- CJK 字符集：GB2312 一级汉字 + 项目内 UI 用到的全部汉字 + 常用中文标点
cjk = set()
for hi in range(0xB0, 0xD8):
    for lo in range(0xA1, 0xFF):
        try:
            cjk.add(bytes([hi, lo]).decode('gb2312'))
        except UnicodeDecodeError:
            pass
han = re.compile(r'[　-〿㐀-鿿＀-￯ -⁯]')
files = glob.glob(os.path.join(repo, 'app/src/main/res/values*/*.xml')) + \
        glob.glob(os.path.join(repo, 'app/src/main/java/com/github/tvbox/osc/compose/**/*.kt'), recursive=True)
ui = set()
for f in files:
    ui.update(han.findall(open(f, encoding='utf-8', errors='ignore').read()))
cjk |= ui
cjk.update(chr(c) for c in range(0x3000, 0x3040))   # CJK 符号与标点
cjk.update(chr(c) for c in range(0xFF01, 0xFF5F))   # 全角 ASCII
cjk.update(chr(c) for c in range(0x2010, 0x2028))   # 破折号、引号、省略号等
cjk.update(chr(c) for c in range(0x20, 0x7F))       # ASCII（Inter 缺字时兜底）
cjk.update(chr(c) for c in range(0xA0, 0x100))      # Latin-1（含 · 等中文常用符号）
print('CJK subset chars:', len(cjk), '(UI-only extra:', len(ui), ')')

latin = set(chr(c) for c in list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) + list(range(0x2010, 0x2070)) +
            [0x20AC, 0x2122, 0x2190, 0x2191, 0x2192, 0x2193, 0x2212, 0x00D7, 0x2715, 0x2713])

def do_subset(font, chars, path):
    opts = subset.Options()
    opts.layout_features = ['kern', 'liga', 'calt', 'tnum', 'locl', 'ccmp', 'mark', 'mkmk', 'vert', 'halt', 'palt']
    opts.name_IDs = ['*']
    opts.name_languages = ['*']
    opts.notdef_outline = True
    opts.hinting = False
    opts.desubroutinize = False
    s = subset.Subsetter(opts)
    s.populate(text=''.join(sorted(chars)))
    s.subset(font)
    font.save(path)
    print(os.path.basename(path), os.path.getsize(path))

for w, name in [(400, 'regular'), (500, 'medium'), (600, 'semibold')]:
    vf = TTFont(os.path.join(src, 'NotoSansSC-VF.ttf'))
    inst = instancer.instantiateVariableFont(vf, {'wght': w}, updateFontNames=True)
    do_subset(inst, cjk, os.path.join(out, f'noto_sans_sc_{name}.ttf'))

for w, name in [('Regular', 'regular'), ('Medium', 'medium'), ('SemiBold', 'semibold')]:
    do_subset(TTFont(os.path.join(src, 'inter', f'Inter-{w}.ttf')), latin, os.path.join(out, f'inter_{name}.ttf'))

total = sum(os.path.getsize(p) for p in glob.glob(os.path.join(out, '*.ttf')))
print('TOTAL', total)
