import argparse
import json
import re
import sys
from collections import Counter, OrderedDict
from pathlib import Path

HERE = Path(__file__).resolve()
REPO = HERE.parents[4]
I18N = REPO / "sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/core/i18n"
DEFAULT_SOURCE = I18N / "data_provider/AppLocalization.kt"
DEFAULT_OUT = HERE.parents[1] / "strings-members.json"
USAGE_ROOTS = ["sharedUI/src", "sharedLogic/src", "desktopApp/src", "androidApp/src"]

SLINT_RESERVED = {
    "animate", "callback", "component", "else", "enum", "export", "false", "for", "from",
    "function", "global", "if", "import", "in", "in-out", "inherits", "init", "interface",
    "out", "parent", "private", "property", "public", "pure", "return", "root", "self",
    "states", "struct", "transitions", "true", "changed", "when", "implements", "uses",
}
SLINT_TYPES = {
    "angle", "bool", "brush", "color", "duration", "easing", "float", "image", "int",
    "length", "percent", "physical-length", "relative-font-size", "string",
}
SLINT_FIXED = {"language-code", "rtl", "revision"}


def strip_comments(text):
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == '"':
            if text.startswith('"""', i):
                j = text.find('"""', i + 3)
                j = n if j < 0 else j + 3
                out.append(text[i:j])
                i = j
                continue
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            out.append(text[i:j + 1])
            i = j + 1
            continue
        if text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if text.startswith("/*", i):
            j = text.find("*/", i + 2)
            out.append(" ")
            i = n if j < 0 else j + 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def interface_body(text, name):
    m = re.search(r"\binterface\s+" + re.escape(name) + r"\b[^{]*\{", text)
    if not m:
        raise SystemExit("interface %s not found" % name)
    depth = 1
    i = m.end()
    while i < len(text) and depth:
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
        i += 1
    return text[m.end():i - 1]


def split_top(text, sep=","):
    parts = []
    depth = 0
    cur = []
    for c in text:
        if c in "(<[":
            depth += 1
        elif c in ")>]":
            depth -= 1
        if c == sep and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(c)
    tail = "".join(cur).strip()
    if tail:
        parts.append(tail)
    return [p.strip() for p in parts if p.strip()]


def parse_params(text):
    params = []
    for part in split_top(text):
        part = re.sub(r"^(vararg|noinline|crossinline)\s+", "", part)
        name, _, rest = part.partition(":")
        ptype = rest.split("=", 1)[0].strip()
        params.append(OrderedDict([("name", name.strip()), ("type", re.sub(r"\s+", "", ptype))]))
    return params


MEMBER = re.compile(
    r"\bval\s+(?P<val>\w+)\s*:\s*(?P<vtype>[\w.?<>]+)"
    r"|\bfun\s+(?P<fun>\w+)\s*\((?P<params>[^)]*)\)\s*:\s*(?P<ftype>[\w.?<>]+)"
)


def parse_members(source):
    body = interface_body(strip_comments(Path(source).read_text(encoding="utf-8-sig")), "AppLocalization")
    members = []
    for m in MEMBER.finditer(body):
        if m.group("val"):
            if m.group("vtype") != "String":
                raise SystemExit("val %s is %s, not String" % (m.group("val"), m.group("vtype")))
            members.append(OrderedDict([("name", m.group("val")), ("kind", "val"), ("params", [])]))
        else:
            if m.group("ftype") != "String":
                raise SystemExit("fun %s returns %s, not String" % (m.group("fun"), m.group("ftype")))
            members.append(OrderedDict([
                ("name", m.group("fun")),
                ("kind", "fun"),
                ("params", parse_params(m.group("params"))),
            ]))
    names = Counter(x["name"] for x in members)
    dup = [n for n, c in names.items() if c > 1]
    if dup:
        raise SystemExit("duplicate or overloaded members: %s" % ", ".join(dup))
    return members


def kebab(name):
    s = re.sub(r"([a-z0-9])([A-Z])", r"\1-\2", name)
    s = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1-\2", s)
    s = re.sub(r"([A-Za-z])([0-9])", r"\1-\2", s)
    return s.lower()


def slint_name(name):
    k = kebab(name)
    if k in SLINT_RESERVED or k in SLINT_TYPES or k in SLINT_FIXED:
        return k + "-text"
    return k


def slint_param_name(name):
    k = kebab(name)
    if k in SLINT_RESERVED or k in SLINT_TYPES:
        return k + "-arg"
    return k


def shape(member):
    if member["kind"] == "val":
        return "val"
    return "fun(" + ", ".join(p["type"] for p in member["params"]) + ")"


def print_stats(members):
    vals = [m for m in members if m["kind"] == "val"]
    funs = [m for m in members if m["kind"] == "fun"]
    print("members: %d (val %d, fun %d)" % (len(members), len(vals), len(funs)))
    shapes = Counter(shape(m) for m in members)
    first = {}
    for m in members:
        first.setdefault(shape(m), []).append(m["name"])
    for s, c in sorted(shapes.items(), key=lambda kv: (-kv[1], kv[0])):
        print("%4d  %-36s %s" % (c, s, ", ".join(first[s][:3])))
    arity = Counter(len(m["params"]) for m in funs)
    print("fun arity: " + ", ".join("%d params: %d" % (k, v) for k, v in sorted(arity.items())))
    types = Counter(p["type"] for m in funs for p in m["params"])
    print("param types: " + ", ".join("%s: %d" % (k, v) for k, v in types.most_common()))
    print("funs with a nullable param: %d" % sum(1 for m in funs if any(p["type"].endswith("?") for p in m["params"])))
    print("funs with a Boolean param: %d" % sum(1 for m in funs if any(p["type"] == "Boolean" for p in m["params"])))


def print_names(members):
    seen = {}
    clashes = []
    renamed = []
    for m in members:
        s = slint_name(m["name"])
        if s != kebab(m["name"]):
            renamed.append((m["name"], s))
        key = s.replace("_", "-")
        if key in seen:
            clashes.append((seen[key], m["name"], s))
        seen[key] = m["name"]
        print("%s\t%s\t%s" % (m["kind"], m["name"], s))
    print("renamed (reserved word): %s" % (renamed or "none"), file=sys.stderr)
    print("collisions: %s" % (clashes or "none"), file=sys.stderr)


def provider_files():
    return sorted((I18N / "data_provider").glob("*Provider.kt"))


def check_providers(members):
    ok = True
    for f in provider_files():
        text = strip_comments(f.read_text(encoding="utf-8-sig"))
        code = re.search(r'override\s+val\s+localeCode\s*=\s*"([^"]+)"', text)
        have_val = set(re.findall(r"override\s+val\s+(\w+)", text))
        have_fun = set(re.findall(r"override\s+fun\s+(\w+)", text))
        missing = [m["name"] for m in members
                   if m["name"] not in (have_val if m["kind"] == "val" else have_fun)]
        extra = sorted((have_val | have_fun) - {m["name"] for m in members} - {"localeCode"})
        print("%-22s %-3s val %d fun %d missing %d extra %d" % (
            f.name, code.group(1) if code else "?", len(have_val) - 1, len(have_fun), len(missing), len(extra)))
        if missing or extra:
            ok = False
            print("   missing: %s" % missing)
            print("   extra: %s" % extra)
    return ok


LITERAL = re.compile(r'override\s+val\s+(\w+)\s*(?::\s*String\s*)?=\s*((?:"(?:[^"\\\n]|\\.)*"\s*\+?\s*)+)')


def text_sizes(members):
    val_names = {m["name"] for m in members if m["kind"] == "val"}
    for f in provider_files():
        text = strip_comments(f.read_text(encoding="utf-8-sig"))
        total8 = 0
        total16 = 0
        count = 0
        longest = 0
        for name, lit in LITERAL.findall(text):
            if name not in val_names:
                continue
            pieces = re.findall(r'"((?:[^"\\\n]|\\.)*)"', lit)
            value = "".join(pieces)
            value = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), value)
            value = value.replace("\\n", "\n").replace('\\"', '"').replace("\\$", "$").replace("\\\\", "\\")
            b8 = len(value.encode("utf-8"))
            total8 += b8
            total16 += len(value.encode("utf-16-le"))
            longest = max(longest, b8)
            count += 1
        print("%-22s vals %d  utf8 %d B  utf16 %d B  avg %.1f B  longest %d B  file %d B" % (
            f.name, count, total8, total16, total8 / max(count, 1), longest, f.stat().st_size))


def usage(members):
    names = {m["name"]: m["kind"] for m in members}
    per_root = OrderedDict()
    used_anywhere = set()
    for root in USAGE_ROOTS:
        used = Counter()
        base = REPO / root
        if not base.exists():
            continue
        for f in base.rglob("*.kt"):
            if "data_provider" in f.parts:
                continue
            text = f.read_text(encoding="utf-8-sig", errors="replace")
            if "Strings" not in text and "AppLocalization" not in text:
                continue
            aliases = set(re.findall(r"\b(?:val|var)\s+(\w+)\s*(?::\s*AppLocalization\s*)?=\s*(?:com\.tbib\.kmp_localization\.)?LocalizationManager\s*\.\s*Strings\b(?!\s*\.)", text))
            aliases |= set(re.findall(r"\b(\w+)\s*:\s*AppLocalization\b", text))
            pattern = r"(?:\bStrings|" + "|".join(r"\b" + re.escape(a) for a in sorted(aliases)) + r")\s*\.\s*(\w+)" if aliases else r"\bStrings\s*\.\s*(\w+)"
            for name in re.findall(pattern, text):
                if name in names:
                    used[name] += 1
        per_root[root] = used
        used_anywhere |= set(used)
    for root, used in per_root.items():
        print("%-18s distinct %d  references %d" % (root, len(used), sum(used.values())))
    unused = [n for n in names if n not in used_anywhere]
    print("referenced nowhere outside the providers: %d" % len(unused))
    for n in unused:
        print("   " + n)
    return per_root


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default=str(DEFAULT_SOURCE))
    ap.add_argument("--out", default=str(DEFAULT_OUT))
    ap.add_argument("--stats", action="store_true")
    ap.add_argument("--names", action="store_true")
    ap.add_argument("--check-providers", action="store_true")
    ap.add_argument("--sizes", action="store_true")
    ap.add_argument("--usage", action="store_true")
    ap.add_argument("--no-write", action="store_true")
    args = ap.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    members = parse_members(args.source)
    if not args.no_write:
        Path(args.out).write_text(json.dumps(members, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print("wrote %s (%d members)" % (args.out, len(members)), file=sys.stderr)
    if args.stats:
        print_stats(members)
    if args.names:
        print_names(members)
    if args.sizes:
        text_sizes(members)
    if args.usage:
        usage(members)
    if args.check_providers and not check_providers(members):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
