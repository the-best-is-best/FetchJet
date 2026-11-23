import argparse
import io
import os
import re
import sys

from fontTools import subset
from fontTools.merge import Merger, Options as MergeOptions
from fontTools.ttLib import TTFont
from fontTools.ttLib.scaleUpem import scale_upem
from fontTools.varLib import instancer

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
DEVANAGARI_RANGES = [
    (0x0900, 0x097F), (0xA8E0, 0xA8FF), (0x1CD0, 0x1CFF), (0x25CC, 0x25CC),
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

CJK_PUNCTUATION = [
    (0x3000, 0x303F), (0xFF00, 0xFFEF), (0x2E80, 0x2EFF), (0x3200, 0x32FF), (0x3300, 0x33FF),
]
CJK = {
    "SC": {"source": "NotoSansSC-VF.ttf", "provider": "ChineseProvider.kt", "extra": []},
    "JP": {"source": "NotoSansJP-VF.ttf", "provider": "JapaneseProvider.kt", "extra": [(0x3040, 0x30FF), (0x31F0, 0x31FF)]},
    "KR": {"source": "NotoSansKR-VF.ttf", "provider": "KoreanProvider.kt", "extra": [(0x1100, 0x11FF), (0x3130, 0x318F)]},
}


def codepoints(ranges):
    out = set()
    for start, end in ranges:
        out.update(range(start, end + 1))
    return out


def base_codepoints():
    return codepoints(TEXT_RANGES) | codepoints(ARABIC_RANGES) | codepoints(DEVANAGARI_RANGES) | set(SYMBOLS) | set(MONO_SYMBOLS)


def provider_codepoints(path):
    text = io.open(path, encoding="utf-8").read()
    strings = re.findall(r'"((?:[^"\\]|\\.)*)"', text)
    return {ord(c) for s in strings for c in s if ord(c) > 0x7F}


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


def instance(variable, weight, path, width=None):
    axes = {"wght": weight}
    if width is not None:
        axes["wdth"] = width
    font = instancer.instantiateVariableFont(TTFont(variable), axes)
    font.save(path)
    return path


def rename(font, family, post_family, style, weight):
    full = family if style == "Regular" else family + " " + style
    post = post_family + "-" + style
    names = font["name"]
    names.names = [n for n in names.names if n.nameID not in (1, 2, 3, 4, 6, 16, 17, 21, 22)]
    for platform, encoding, language in ((3, 1, 0x409), (1, 0, 0)):
        names.setName(family, 1, platform, encoding, language)
        names.setName(style, 2, platform, encoding, language)
        names.setName("FetchJet " + post + " 1.1", 3, platform, encoding, language)
        names.setName(full, 4, platform, encoding, language)
        names.setName(post, 6, platform, encoding, language)
        names.setName(family, 16, platform, encoding, language)
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


def merge(paths):
    return Merger(options=MergeOptions(drop_tables=["vmtx", "vhea"])).merge(paths)


def build(noto, arabic, devanagari, cjk_dir, providers, dejavu, dejavu_mono, out):
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
    joined = merge([symbols_path, mono_path])
    scale_upem(joined, 1000)
    joined.save(joined_path)
    base = base_codepoints()
    for style, weight in WEIGHTS:
        latin = cut(os.path.join(noto, "NotoSans-" + style + ".ttf"), codepoints(TEXT_RANGES), True)
        arab = cut(os.path.join(arabic, "NotoSansArabic-" + style + ".ttf"), codepoints(ARABIC_RANGES) - codepoints(TEXT_RANGES), True)
        deva_static = instance(devanagari, weight, os.path.join(work, "deva-static.ttf"), 100)
        deva = cut(deva_static, codepoints(DEVANAGARI_RANGES) - codepoints(TEXT_RANGES) - codepoints(ARABIC_RANGES), True)
        latin_path = os.path.join(work, "latin-" + style + ".ttf")
        arab_path = os.path.join(work, "arabic-" + style + ".ttf")
        deva_path = os.path.join(work, "devanagari-" + style + ".ttf")
        latin.save(latin_path)
        arab.save(arab_path)
        deva.save(deva_path)
        parts = [latin_path, arab_path, deva_path, joined_path]
        merged = merge(parts)
        keep_metrics(merged, TTFont(latin_path))
        rename(merged, FAMILY, "FetchJetSans", style, weight)
        target = os.path.join(out, "FetchJetSans-" + style + ".ttf")
        merged.save(target)
        print(target, os.path.getsize(target))
        for tag, spec in CJK.items():
            wanted = (provider_codepoints(os.path.join(providers, spec["provider"])) | codepoints(CJK_PUNCTUATION)
                      | codepoints(spec["extra"])) - base
            cjk_static = instance(os.path.join(cjk_dir, spec["source"]), weight, os.path.join(work, "cjk-static.ttf"))
            cjk_font = cut(cjk_static, wanted, True)
            cjk_path = os.path.join(work, "cjk-" + tag + "-" + style + ".ttf")
            cjk_font.save(cjk_path)
            merged = merge(parts[:3] + [cjk_path, joined_path])
            keep_metrics(merged, TTFont(latin_path))
            rename(merged, FAMILY + " " + tag, "FetchJetSans" + tag, style, weight)
            target = os.path.join(out, "FetchJetSans" + tag + "-" + style + ".ttf")
            merged.save(target)
            print(target, os.path.getsize(target))
    for name in os.listdir(work):
        os.remove(os.path.join(work, name))
    os.rmdir(work)


def check(fonts, providers):
    files = {"": "FetchJetSans", "SC": "FetchJetSansSC", "JP": "FetchJetSansJP", "KR": "FetchJetSansKR"}
    by_tag = {tag: spec["provider"] for tag, spec in CJK.items()}
    missing_total = 0
    for name in sorted(os.listdir(providers)):
        if not name.endswith("Provider.kt"):
            continue
        tag = next((t for t, p in by_tag.items() if p == name), "")
        wanted = provider_codepoints(os.path.join(providers, name))
        for style, _ in WEIGHTS:
            font = TTFont(os.path.join(fonts, files[tag] + "-" + style + ".ttf"))
            have = set(font.getBestCmap().keys())
            missing = sorted(c for c in wanted if c not in have and not (0xFE00 <= c <= 0xFE0F) and not 0x1F000 <= c)
            if missing:
                missing_total += len(missing)
                print("%s %s %s: %d missing: %s" % (name, files[tag], style, len(missing), "".join(chr(c) for c in missing[:40])))
    print("missing characters: %d" % missing_total)
    return 1 if missing_total else 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="only check that every provider text is in the fonts of --out")
    parser.add_argument("--noto")
    parser.add_argument("--arabic")
    parser.add_argument("--devanagari")
    parser.add_argument("--cjk")
    parser.add_argument("--providers", required=True)
    parser.add_argument("--dejavu")
    parser.add_argument("--dejavu-mono")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    if args.check:
        return check(args.out, args.providers)
    build(args.noto, args.arabic, args.devanagari, args.cjk, args.providers, args.dejavu, args.dejavu_mono, args.out)
    return check(args.out, args.providers)


if __name__ == "__main__":
    sys.exit(main())
