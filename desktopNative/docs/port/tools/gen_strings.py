import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from list_strings import slint_name, slint_param_name as param_name

SLINT_TYPE = {"String": "string", "String?": "string", "Int": "int", "Int?": "int", "Boolean": "bool"}
SEPARATOR = 0x1F


def cpp_name(slint):
    return slint.replace("-", "_")


def english_defaults(provider):
    text = Path(provider).read_text(encoding="utf-8-sig")
    out = {}
    pattern = re.compile(r'override\s+val\s+(\w+)\s*=\s*((?:"(?:[^"\\\n]|\\.)*"\s*\+?\s*)+)')
    for name, lit in pattern.findall(text):
        value = "".join(re.findall(r'"((?:[^"\\\n]|\\.)*)"', lit))
        out[name] = value
    return out


def slint_literal(kotlin_escaped):
    value = kotlin_escaped.replace("\\$", "$")
    value = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: "\\u{" + m.group(1) + "}", value)
    value = value.replace("\\'", "'")
    return '"' + value + '"'


def slots(member):
    ints = []
    strs = []
    for p in member["params"]:
        (strs if p["type"].startswith("String") else ints).append(p)
    return ints, strs


def gen_slint(members, defaults):
    lines = ["export global Strings {"]
    lines.append('    in property <string> language-code: "en";')
    lines.append("    in property <bool> rtl: false;")
    lines.append("    in property <int> revision: 0;")
    for m in members:
        if m["kind"] != "val":
            continue
        lines.append("    in property <string> %s: %s;" % (slint_name(m["name"]), slint_literal(defaults.get(m["name"], ""))))
    for m in members:
        if m["kind"] != "fun":
            continue
        name = slint_name(m["name"])
        params = ", ".join("%s: %s" % (param_name(p["name"]), SLINT_TYPE[p["type"]]) for p in m["params"])
        args = ", ".join(param_name(p["name"]) for p in m["params"])
        lines.append("    pure callback fn-%s(revision: int, %s) -> string;" % (name, params))
        lines.append("    public pure function %s(%s) -> string { return fn-%s(revision, %s); }" % (name, params, name, args))
    lines.append("}")
    return "\n".join(lines) + "\n"


def gen_kotlin(members, package, max_ints, max_strs):
    out = []
    out.append("@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)")
    out.append("")
    if package:
        out.append("package %s" % package)
        out.append("")
    out.append("import com.michelle.raouf.fetch_jet.core.i18n.data_provider.AppLocalization")
    out.append("import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings")
    out.append("import com.tbib.kmp_localization.LocalizationManager")
    out.append("import kotlinx.cinterop.ByteVar")
    out.append("import kotlinx.cinterop.CPointer")
    out.append("import kotlinx.cinterop.toKString")
    out.append("import kotlin.native.CName")
    out.append("")
    out.append("internal const val STRING_VALUE_COUNT = %d" % sum(1 for m in members if m["kind"] == "val"))
    out.append("")
    out.append("internal fun AppLocalization.packValues(): String {")
    out.append("    val b = StringBuilder(48 * 1024)")
    for m in members:
        if m["kind"] == "val":
            out.append("    b.append(%s).append('\\u%04x')" % (m["name"], SEPARATOR))
    out.append("    return b.toString()")
    out.append("}")
    out.append("")
    int_params = ", ".join("i%d: Int" % i for i in range(max_ints))
    str_params = ", ".join("s%d: String" % i for i in range(max_strs))
    out.append("internal fun AppLocalization.callText(id: Int, %s, %s): String = when (id) {" % (int_params, str_params))
    fid = 0
    for m in members:
        if m["kind"] != "fun":
            continue
        ii = 0
        si = 0
        args = []
        for p in m["params"]:
            t = p["type"]
            if t == "String":
                args.append("s%d" % si)
                si += 1
            elif t == "String?":
                args.append("s%d.ifEmpty { null }" % si)
                si += 1
            elif t == "Int":
                args.append("i%d" % ii)
                ii += 1
            elif t == "Int?":
                args.append("i%d.takeIf { it >= 0 }" % ii)
                ii += 1
            elif t == "Boolean":
                args.append("i%d != 0" % ii)
                ii += 1
            else:
                raise SystemExit("no rule for parameter type %s in %s" % (t, m["name"]))
        out.append("    %d -> %s(%s)" % (fid, m["name"], ", ".join(args)))
        fid += 1
    out.append('    else -> ""')
    out.append("}")
    out.append("")
    out.append('@CName("fj_strings_values")')
    out.append("fun fjStringsValues(): CPointer<ByteVar> = cString(LocalizationManager.Strings.packValues())")
    out.append("")
    ptr_params = ", ".join("s%d: CPointer<ByteVar>?" % i for i in range(max_strs))
    conv = ", ".join("s%d?.toKString().orEmpty()" % i for i in range(max_strs))
    ints = ", ".join("i%d" % i for i in range(max_ints))
    out.append('@CName("fj_strings_call")')
    out.append("fun fjStringsCall(id: Int, %s, %s): CPointer<ByteVar> =" % (int_params, ptr_params))
    out.append("    cString(LocalizationManager.Strings.callText(id, %s, %s))" % (ints, conv))
    return "\n".join(out) + "\n"


def gen_cpp(members, max_ints, max_strs):
    out = []
    out.append("#pragma once")
    out.append("#include <cstring>")
    out.append("#include <string_view>")
    out.append("")
    int_sig = ", ".join(["int"] * max_ints)
    str_sig = ", ".join(["const char *"] * max_strs)
    out.append("struct FjStringsApi")
    out.append("{")
    out.append("    char *(*values)() = nullptr;")
    out.append("    char *(*call)(int, %s, %s) = nullptr;" % (int_sig, str_sig))
    out.append("    void (*release)(char *) = nullptr;")
    out.append("};")
    out.append("")
    out.append("inline slint::SharedString fj_strings_take(const FjStringsApi &api, char *text)")
    out.append("{")
    out.append("    slint::SharedString result(text ? text : \"\");")
    out.append("    if (text)")
    out.append("        api.release(text);")
    out.append("    return result;")
    out.append("}")
    out.append("")
    out.append("inline void fj_strings_apply(const Strings &s, const FjStringsApi &api)")
    out.append("{")
    out.append("    char *blob = api.values();")
    out.append("    const char *p = blob;")
    out.append("    auto next = [&p]() {")
    out.append("        const char *e = std::strchr(p, %d);" % SEPARATOR)
    out.append("        size_t n = e ? size_t(e - p) : std::strlen(p);")
    out.append("        slint::SharedString v(std::string_view(p, n));")
    out.append("        p = e ? e + 1 : p + n;")
    out.append("        return v;")
    out.append("    };")
    for m in members:
        if m["kind"] == "val":
            out.append("    s.set_%s(next());" % cpp_name(slint_name(m["name"])))
    out.append("    api.release(blob);")
    out.append("    s.set_revision(s.get_revision() + 1);")
    out.append("}")
    out.append("")
    out.append("inline void fj_strings_bind(const Strings &s, const FjStringsApi &api)")
    out.append("{")
    fid = 0
    for m in members:
        if m["kind"] != "fun":
            continue
        ints, strs = slots(m)
        decl = ["int"]
        ii = 0
        si = 0
        int_args = ["0"] * max_ints
        str_args = ["nullptr"] * max_strs
        for p in m["params"]:
            t = p["type"]
            if t.startswith("String"):
                decl.append("slint::SharedString s%d" % si)
                str_args[si] = "s%d.data()" % si
                si += 1
            elif t == "Boolean":
                decl.append("bool i%d" % ii)
                int_args[ii] = "i%d ? 1 : 0" % ii
                ii += 1
            else:
                decl.append("int i%d" % ii)
                int_args[ii] = "i%d" % ii
                ii += 1
        out.append("    s.on_fn_%s([api](%s) { return fj_strings_take(api, api.call(%d, %s, %s)); });" % (
            cpp_name(slint_name(m["name"])), ", ".join(decl), fid, ", ".join(int_args), ", ".join(str_args)))
        fid += 1
    out.append("}")
    return "\n".join(out) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--members", required=True)
    ap.add_argument("--english", required=True)
    ap.add_argument("--slint", required=True)
    ap.add_argument("--cpp", required=True)
    ap.add_argument("--kotlin", required=True)
    ap.add_argument("--package", default="")
    args = ap.parse_args()
    members = json.loads(Path(args.members).read_text(encoding="utf-8"))
    names = [slint_name(m["name"]) for m in members]
    if len(set(names)) != len(names):
        raise SystemExit("two members map to the same Slint name")
    funs = [m for m in members if m["kind"] == "fun"]
    max_ints = max(len(slots(m)[0]) for m in funs)
    max_strs = max(len(slots(m)[1]) for m in funs)
    defaults = english_defaults(args.english)
    Path(args.slint).write_text(gen_slint(members, defaults), encoding="utf-8", newline="\n")
    Path(args.cpp).write_text(gen_cpp(members, max_ints, max_strs), encoding="utf-8", newline="\n")
    Path(args.kotlin).write_text(gen_kotlin(members, args.package, max_ints, max_strs), encoding="utf-8", newline="\n")
    print("vals %d funs %d int slots %d string slots %d" % (
        len(members) - len(funs), len(funs), max_ints, max_strs))


if __name__ == "__main__":
    main()
