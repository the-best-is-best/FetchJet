import argparse
import os
import sys

from fontTools import subset
from fontTools.merge import Merger, Options as MergeOptions
from fontTools.ttLib import TTFont
from fontTools.ttLib.scaleUpem import scale_upem

FAMILY = "FetchJet Sans"
WEIGHTS = [("Regular", 400), ("Medium", 500), ("SemiBold", 600), ("Bold", 700)]

TEXT_RANGES = [
    (0x0020, 0x024F), (0x0250, 0x02FF), (0x0300, 0x036F), (0x0370, 0x03FF), (0x0400, 0x052F),
    (0x1E00, 0x1EFF), (0x2000, 0x206F), (0x20A0, 0x20CF), (0x2100, 0x214F), (0xFEFF, 0xFEFF),
    (0xFFFD, 0xFFFD),
]
ARABIC_RANGES = [
    (0x0600, 0x06FF), (0x0750, 0x077F), (0x0870, 0x08FF), (0xFB50, 0xFDFF), (0xFE70, 0xFEFE),
    (0x200C, 0x200F), (0x0660, 0x0669), (0x06F0, 0x06F9),
]
SYMBOLS = [
    0x2190, 0x2191, 0x2192, 0x2193, 0x2194, 0x2195, 0x2196, 0x2197, 0x2198, 0x2199, 0x21BA, 0x21BB,
    0x21C4, 0x21C5, 0x2212, 0x221E, 0x2248, 0x2260, 0x2264, 0x2265,
    0x25A0, 0x25A1, 0x25B2, 0x25B4, 0x25B6, 0x25B8, 0x25BA, 0x25BC, 0x25BE, 0x25C0, 0x25C2, 0x25C4,
    0x25C6, 0x25C9, 0x25CB, 0x25CE, 0x25CF, 0x25D0, 0x2600, 0x2601, 0x2605, 0x2606, 0x263C, 0x263E,
    0x2699, 0x26A0, 0x26A1, 0x26AA, 0x26AB, 0x2702, 0x2713, 0x2714, 0x2715, 0x2716, 0x2717, 0x2718,
    0x2B06, 0x2B07, 0x2B05, 0x27A1, 0x2630, 0x22EE, 0x22EF,
]
MONO_SYMBOLS = [0x2315]


def codepoints(ranges):
    out = set()
    for start, end in ranges:
        out.update(range(start, end + 1))
    return out


def cut(path, unicodes, layout):
    font = TTFont(path)
    options = subset.Options()
    options.layout_features = ["*"] if layout else []
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.notdef_outline = True
    options.glyph_names = False
    options.hinting = False
    if not layout:
        options.drop_tables += ["GSUB", "GPOS", "GDEF", "kern", "BASE", "JSTF", "MATH"]
    worker = subset.Subsetter(options)
    worker.populate(unicodes=sorted(unicodes))
    worker.subset(font)
    return font


def rename(font, style, weight):
    full = FAMILY if style == "Regular" else FAMILY + " " + style
    post = "FetchJetSans-" + style
    names = font["name"]
    names.names = [n for n in names.names if n.nameID not in (1, 2, 3, 4, 6, 16, 17, 21, 22)]
    for platform, encoding, language in ((3, 1, 0x409), (1, 0, 0)):
        names.setName(FAMILY, 1, platform, encoding, language)
        names.setName(style, 2, platform, encoding, language)
        names.setName("FetchJet " + post + " 1.0", 3, platform, encoding, language)
        names.setName(full, 4, platform, encoding, language)
        names.setName(post, 6, platform, encoding, language)
        names.setName(FAMILY, 16, platform, encoding, language)
        names.setName(style, 17, platform, encoding, language)
    os2 = font["OS/2"]
    os2.usWeightClass = weight
    bold = weight >= 700
    os2.fsSelection = (os2.fsSelection & ~0x61) | (0x20 if bold else 0) | (0x40 if style == "Regular" else 0)
    font["head"].macStyle = 0x1 if bold else 0


def keep_metrics(font, source):
    for table, fields in (("hhea", ("ascent", "descent", "lineGap")),
                          ("OS/2", ("sTypoAscender", "sTypoDescender", "sTypoLineGap", "usWinAscent", "usWinDescent", "sxHeight", "sCapHeight"))):
        for field in fields:
            setattr(font[table], field, getattr(source[table], field))


def build(noto, arabic, dejavu, dejavu_mono, out):
    os.makedirs(out, exist_ok=True)
    work = os.path.join(out, "_work")
    os.makedirs(work, exist_ok=True)
    symbols = cut(dejavu, set(SYMBOLS), False)
    mono = cut(dejavu_mono, set(MONO_SYMBOLS), False)
    symbols_path = os.path.join(work, "symbols.ttf")
    mono_path = os.path.join(work, "mono.ttf")
    joined_path = os.path.join(work, "symbols-joined.ttf")
    symbols.save(symbols_path)
    mono.save(mono_path)
    joined = Merger(options=MergeOptions(drop_tables=["vmtx", "vhea"])).merge([symbols_path, mono_path])
    scale_upem(joined, 1000)
    joined.save(joined_path)
    for style, weight in WEIGHTS:
        latin = cut(os.path.join(noto, "NotoSans-" + style + ".ttf"), codepoints(TEXT_RANGES), True)
        arab = cut(os.path.join(arabic, "NotoSansArabic-" + style + ".ttf"), codepoints(ARABIC_RANGES) - codepoints(TEXT_RANGES), True)
        latin_path = os.path.join(work, "latin-" + style + ".ttf")
        arab_path = os.path.join(work, "arabic-" + style + ".ttf")
        latin.save(latin_path)
        arab.save(arab_path)
        merged = Merger(options=MergeOptions(drop_tables=["vmtx", "vhea"])).merge([latin_path, arab_path, joined_path])
        keep_metrics(merged, TTFont(latin_path))
        rename(merged, style, weight)
        target = os.path.join(out, "FetchJetSans-" + style + ".ttf")
        merged.save(target)
        print(target, os.path.getsize(target))
    for name in os.listdir(work):
        os.remove(os.path.join(work, name))
    os.rmdir(work)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--noto", required=True)
    parser.add_argument("--arabic", required=True)
    parser.add_argument("--dejavu", required=True)
    parser.add_argument("--dejavu-mono", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    build(args.noto, args.arabic, args.dejavu, args.dejavu_mono, args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
