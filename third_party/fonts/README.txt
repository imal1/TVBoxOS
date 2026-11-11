app/src/main/res/font 下的内嵌字体（总计约 2.9 MB）：

- inter_{regular,medium,semibold}.ttf
    Inter 4.1（https://github.com/rsms/inter），SIL OFL 1.1，见 OFL-Inter.txt
    子集：Basic Latin、Latin-1、Latin Extended-A、常用标点与符号
- noto_sans_sc_{regular,medium,semibold}.otf
    Noto Sans SC（思源黑体的 Google Fonts 发行版，https://github.com/google/fonts/tree/main/ofl/notosanssc），
    SIL OFL 1.1，见 OFL-NotoSansSC.txt
    由可变字体 NotoSansSC[wght].ttf 实例化为 400/500/600 三个字重后子集化：
    GB2312 一级汉字（3755）+ 项目 UI 用到的全部汉字 + 中文标点、全角字符、Latin-1；
    再由 TrueType 轮廓转换为子程序化的 CFF（字形不变，体积约减少 22%）。
    子集外的生僻字会回退到系统字体。

运行时由 compose/theme/TvFonts.kt 组装：拉丁字符优先使用 Inter，其余回退到 Noto Sans SC，再回退到系统字体。

重新生成（需 Python + fonttools、cffsubr、brotli）：
    python tools/build_fonts.py <源字体目录> <仓库根目录> <输出目录>
    python tools/ttf2otf.py <输出目录>/noto_sans_sc_<weight>.ttf <输出目录>/noto_sans_sc_<weight>.otf
<源字体目录> 需包含 NotoSansSC-VF.ttf 与 inter/Inter-{Regular,Medium,SemiBold}.ttf。
