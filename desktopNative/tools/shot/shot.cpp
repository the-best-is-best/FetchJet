#include "headless.h"
#include "json.h"
#include "emoji_d2d.h"

#include <slint-interpreter.h>

#include <psapi.h>
#include <shellapi.h>

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <memory>
#include <optional>
#include <string>
#include <thread>
#include <utility>
#include <vector>

namespace {

    using slint::interpreter::ComponentCompiler;
    using slint::interpreter::ComponentDefinition;
    using slint::interpreter::ComponentInstance;
    using slint::interpreter::DiagnosticLevel;
    using slint::interpreter::PropertyDescriptor;
    using slint::interpreter::Struct;
    using slint::interpreter::Value;
    using slint::interpreter::ValueType;

    struct Step {
        enum Kind {
            Hover, Click, Press, Release, Type, Key, Wait, Scroll, Leave
        };
        Kind kind = Wait;
        float x = 0;
        float y = 0;
        float dx = 0;
        float dy = 0;
        int ms = 0;
        std::string text;
    };

    struct Options {
        std::string file;
        std::string component;
        std::string out;
        std::optional<float> width;
        std::optional<float> height;
        float scale = 1.0f;
        std::vector <std::pair<std::string, std::string>> sets;
        std::vector <std::string> jsonFiles;
        std::optional<bool> dark;
        std::optional<bool> colorScheme;
        std::string themeGlobal = "Theme";
        std::string themeProperty = "dark";
        std::string style = "fluent";
        std::vector <std::string> includes;
        std::vector <std::string> exports;
        std::vector <Step> steps;
        std::string emojiCallback;
        std::string emojiFamily = "Segoe UI Emoji";
        bool list = false;
        bool wrap = false;
        std::string wrapBackground = "#ffffff";
        std::string wrapFont = "Segoe UI";
        std::string bind;
        std::string tail;
        bool hasCrop = false;
        float crop[4] = {0, 0, 0, 0};
        int zoom = 1;
        bool quiet = false;
        bool stats = false;
    };

    struct Context {
        std::string baseDir;
        fj::EmojiRasterizer emoji;
        std::string emojiFamily;
    };

    const char *kUsage =
            "shot <file.slint> [Component] --out x.png [options]\n"
            "  --width N --height N     logical size (default: the component's preferred size)\n"
            "  --scale F                scale factor, 1.25 = 125% (default 1)\n"
            "  --dark | --light         set <Theme>.dark (see --theme-global) and the std-widgets colour scheme\n"
            "  --color-scheme dark|light   only the std-widgets Palette colour scheme (default: light)\n"
            "  --theme-global G.prop    the bool that --dark/--light sets (default Theme.dark)\n"
            "  --set name=value         set a property; Global.name=value for an exported global\n"
            "  --json file.json         properties, structs, arrays and globals from a JSON file\n"
            "  --export Name            also export a global the file only imports\n"
            "  --style S                widget style (default fluent)\n"
            "  -I dir                   extra include path\n"
            "  --hover x,y --click x,y --press x,y --release x,y --leave\n"
            "  --scroll x,y,dx,dy --type TEXT --key NAME --wait MS   run in order before the shot\n"
            "  --crop x,y,w,h           keep a logical rectangle\n"
            "  --zoom N                 enlarge the result N times (nearest neighbour)\n"
            "  --wrap [--bg #rrggbb] [--font F] [--bind 'a: 1; b: \"x\";']   put a non-Window component in a Window\n"
            "  --emoji-callback G.name  implement 'pure callback name(string, int) -> image' with DirectWrite colour emoji\n"
            "  --list                   print the public properties, callbacks and globals, then exit\n"
            "  --stats                  print timings and memory of this run\n"
            "  --tail TEXT              Slint source appended to the file before compiling\n"
            "  --quiet                  no summary line\n";

    std::string readFile(const std::string &path, bool &ok) {
        std::string data;
        FILE *f = nullptr;
        _wfopen_s(&f, shot::widen(path).c_str(), L"rb");
        ok = f != nullptr;
        if (!f)
            return data;
        char buf[65536];
        size_t n;
        while ((n = fread(buf, 1, sizeof buf, f)) > 0)
            data.append(buf, n);
        fclose(f);
        return data;
    }

    std::string fullPath(const std::string &path) {
        std::wstring w = shot::widen(path);
        wchar_t buf[4096];
        DWORD n = GetFullPathNameW(w.c_str(), 4096, buf, nullptr);
        if (n == 0 || n >= 4096)
            return path;
        std::string s = shot::narrow(std::wstring(buf, n));
        std::replace(s.begin(), s.end(), '\\', '/');
        return s;
    }

    std::string dirOf(const std::string &path) {
        auto slash = path.find_last_of("/\\");
        return slash == std::string::npos ? std::string(".") : path.substr(0, slash);
    }

    bool isAbsolute(const std::string &p) {
        return p.size() > 1 && (p[1] == ':' || p[0] == '/' || p[0] == '\\');
    }

    std::string normalized(std::string name) {
        std::replace(name.begin(), name.end(), '_', '-');
        return name;
    }

    bool parseFloats(const std::string &text, float *out, int count) {
        size_t pos = 0;
        for (int i = 0; i < count; i++) {
            size_t comma = text.find(',', pos);
            std::string part = text.substr(pos,
                                           comma == std::string::npos ? std::string::npos : comma -
                                                                                            pos);
            char *end = nullptr;
            out[i] = std::strtof(part.c_str(), &end);
            if (end == part.c_str())
                return false;
            if (comma == std::string::npos && i + 1 < count)
                return false;
            pos = comma == std::string::npos ? text.size() : comma + 1;
        }
        return true;
    }

    std::optional <slint::Color> parseColor(const std::string &text) {
        if (text.size() < 4 || text[0] != '#')
            return std::nullopt;
        std::string hex = text.substr(1);
        for (char c: hex)
            if (!isxdigit((unsigned char) c))
                return std::nullopt;
        if (hex.size() == 3 || hex.size() == 4) {
            std::string wide;
            for (char c: hex) {
                wide += c;
                wide += c;
            }
            hex = wide;
        }
        if (hex.size() != 6 && hex.size() != 8)
            return std::nullopt;
        unsigned long v = std::strtoul(hex.c_str(), nullptr, 16);
        if (hex.size() == 6)
            return slint::Color::from_argb_uint8(255, uint8_t(v >> 16), uint8_t(v >> 8),
                                                 uint8_t(v));
        return slint::Color::from_argb_uint8(uint8_t(v), uint8_t(v >> 24), uint8_t(v >> 16),
                                             uint8_t(v >> 8));
    }

    std::optional <slint::Image> emojiImage(Context &ctx, const std::string &spec) {
        std::string text = spec;
        int px = 32;
        bool icon = true;
        auto at = spec.rfind('@');
        if (at != std::string::npos && at > 0) {
            text = spec.substr(0, at);
            std::string tail = spec.substr(at + 1);
            if (!tail.empty() && (tail.back() == 't' || tail.back() == 'T')) {
                icon = false;
                tail.pop_back();
            }
            px = std::max(1, std::atoi(tail.c_str()));
        }
        std::wstring family = shot::widen(ctx.emojiFamily);
        auto buffer = icon ? ctx.emoji.renderIcon(shot::widen(text), uint32_t(px), family.c_str())
                           : ctx.emoji.renderText(shot::widen(text), float(px), family.c_str());
        if (buffer.width() == 0)
            return std::nullopt;
        return slint::Image(buffer);
    }

    std::optional <slint::Image> loadImage(Context &ctx, const std::string &path) {
        std::string full = isAbsolute(path) ? path : ctx.baseDir + "/" + path;
        auto img = slint::Image::load_from_path(slint::SharedString(full));
        if (img.size().width == 0)
            return std::nullopt;
        return img;
    }

    bool startsWith(const std::string &s, const char *prefix) {
        return s.rfind(prefix, 0) == 0;
    }

    std::optional <Value>
    toValue(Context &ctx, const shot::Json &j, ValueType hint, std::string &error) {
        switch (j.kind) {
            case shot::Json::Null:
                return Value();
            case shot::Json::Bool:
                return Value(j.boolean);
            case shot::Json::Number:
                return Value(j.number);
            case shot::Json::String: {
                const std::string &s = j.text;
                if (startsWith(s, "@color:") || hint == ValueType::Brush) {
                    auto c = parseColor(startsWith(s, "@color:") ? s.substr(7) : s);
                    if (!c) {
                        error = "'" + s + "' is not a colour (#rgb, #rrggbb or #rrggbbaa)";
                        return std::nullopt;
                    }
                    return Value(slint::Brush(*c));
                }
                if (startsWith(s, "@emoji:")) {
                    auto img = emojiImage(ctx, s.substr(7));
                    if (!img) {
                        error = "cannot draw emoji '" + s + "'";
                        return std::nullopt;
                    }
                    return Value(*img);
                }
                if (startsWith(s, "@image:") || hint == ValueType::Image) {
                    std::string path = startsWith(s, "@image:") ? s.substr(7) : s;
                    if (path.empty())
                        return Value(slint::Image());
                    auto img = loadImage(ctx, path);
                    if (!img) {
                        error = "cannot load image '" + path + "'";
                        return std::nullopt;
                    }
                    return Value(*img);
                }
                if (hint == ValueType::Number) {
                    char *end = nullptr;
                    double d = std::strtod(s.c_str(), &end);
                    if (end == s.c_str()) {
                        error = "'" + s + "' is not a number";
                        return std::nullopt;
                    }
                    return Value(d);
                }
                if (hint == ValueType::Bool) {
                    if (s == "true" || s == "1")
                        return Value(true);
                    if (s == "false" || s == "0")
                        return Value(false);
                    error = "'" + s + "' is not a bool";
                    return std::nullopt;
                }
                return Value(slint::SharedString(s));
            }
            case shot::Json::Array: {
                slint::SharedVector <Value> values;
                for (const auto &item: j.items) {
                    auto v = toValue(ctx, item, ValueType::Other, error);
                    if (!v)
                        return std::nullopt;
                    values.push_back(*v);
                }
                return Value(values);
            }
            case shot::Json::Object: {
                Struct s;
                for (const auto &[key, child]: j.fields) {
                    auto v = toValue(ctx, child, ValueType::Other, error);
                    if (!v)
                        return std::nullopt;
                    s.set_field(normalized(key), *v);
                }
                return Value(s);
            }
        }
        return Value();
    }

    const char *typeName(ValueType t) {
        switch (t) {
            case ValueType::Void:
                return "void";
            case ValueType::Number:
                return "number";
            case ValueType::String:
                return "string";
            case ValueType::Bool:
                return "bool";
            case ValueType::Model:
                return "array";
            case ValueType::Struct:
                return "struct";
            case ValueType::Brush:
                return "brush";
            case ValueType::Image:
                return "image";
            default:
                return "other";
        }
    }

    std::optional <ValueType> findType(const slint::SharedVector <PropertyDescriptor> &props,
                                       const std::string &name) {
        std::string wanted = normalized(name);
        for (const auto &p: props) {
            std::string n(p.property_name.data(), p.property_name.size());
            if (normalized(n) == wanted)
                return p.property_type;
        }
        return std::nullopt;
    }

    std::string namesOf(const slint::SharedVector <PropertyDescriptor> &props) {
        std::string all;
        for (const auto &p: props) {
            if (!all.empty())
                all += ", ";
            all += std::string(p.property_name.data(), p.property_name.size());
        }
        return all;
    }

    bool hasGlobal(const ComponentDefinition &def, const std::string &name) {
        for (const auto &g: def.globals())
            if (std::string(g.data(), g.size()) == name)
                return true;
        return false;
    }

    struct Target {
        std::string global;
        std::string property;
    };

    Target splitTarget(const std::string &name) {
        auto dot = name.find('.');
        if (dot == std::string::npos)
            return {"", name};
        return {name.substr(0, dot), name.substr(dot + 1)};
    }

    bool assign(Context &ctx, const ComponentDefinition &def, const ComponentInstance &inst,
                const Target &target, const shot::Json &json, std::string &error) {
        if (target.global.empty()) {
            auto props = def.properties();
            auto type = findType(props, target.property);
            if (!type) {
                error = "no public property '" + target.property + "' (available: " +
                        namesOf(props) + ")";
                return false;
            }
            auto value = toValue(ctx, json, *type, error);
            if (!value)
                return false;
            if (!inst.set_property(normalized(target.property), *value)) {
                error = "property '" + target.property + "' (" + typeName(*type)
                        + ") refused the value: wrong type or not settable";
                return false;
            }
            return true;
        }
        auto props = def.global_properties(target.global);
        if (!props) {
            error = "global '" + target.global + "' is not exported from the file (use --export "
                    + target.global + ")";
            return false;
        }
        auto type = findType(*props, target.property);
        if (!type) {
            error = "global '" + target.global + "' has no public property '" + target.property
                    + "' (available: " + namesOf(*props) + ")";
            return false;
        }
        auto value = toValue(ctx, json, *type, error);
        if (!value)
            return false;
        if (!inst.set_global_property(target.global, normalized(target.property), *value)) {
            error = "global property '" + target.global + "." + target.property + "' (" +
                    typeName(*type)
                    + ") refused the value: wrong type or not settable";
            return false;
        }
        return true;
    }

    shot::Json jsonForSet(const std::string &raw, ValueType type) {
        shot::Json j;
        if (type == ValueType::Model || type == ValueType::Struct) {
            std::string error;
            shot::JsonParser parser(raw);
            if (parser.parse(j, error))
                return j;
            j = shot::Json();
        }
        if (type == ValueType::Number) {
            char *end = nullptr;
            double d = std::strtod(raw.c_str(), &end);
            if (end != raw.c_str()) {
                j.kind = shot::Json::Number;
                j.number = d;
                return j;
            }
        }
        if (type == ValueType::Bool &&
            (raw == "true" || raw == "false" || raw == "1" || raw == "0")) {
            j.kind = shot::Json::Bool;
            j.boolean = raw == "true" || raw == "1";
            return j;
        }
        j.kind = shot::Json::String;
        j.text = raw;
        return j;
    }

    bool parseArgs(const std::vector <std::string> &args, Options &o, std::string &error) {
        std::vector <std::string> positional;
        for (size_t i = 0; i < args.size(); i++) {
            std::string a = args[i];
            std::string inlineValue;
            bool hasInline = false;
            if (a.rfind("--", 0) == 0) {
                auto eq = a.find('=');
                if (eq != std::string::npos) {
                    inlineValue = a.substr(eq + 1);
                    a = a.substr(0, eq);
                    hasInline = true;
                }
            }
            auto next = [&](std::string &out) {
                if (hasInline) {
                    out = inlineValue;
                    return true;
                }
                if (i + 1 >= args.size()) {
                    error = a + " needs a value";
                    return false;
                }
                out = args[++i];
                return true;
            };
            auto point = [&](Step::Kind kind) {
                std::string v;
                if (!next(v))
                    return false;
                Step s;
                s.kind = kind;
                float xy[2];
                if (!parseFloats(v, xy, 2)) {
                    error = a + " needs x,y";
                    return false;
                }
                s.x = xy[0];
                s.y = xy[1];
                o.steps.push_back(s);
                return true;
            };
            std::string v;
            if (a == "--out" || a == "-o") {
                if (!next(o.out))
                    return false;
            } else if (a == "--width") {
                if (!next(v))
                    return false;
                o.width = std::strtof(v.c_str(), nullptr);
            } else if (a == "--height") {
                if (!next(v))
                    return false;
                o.height = std::strtof(v.c_str(), nullptr);
            } else if (a == "--scale") {
                if (!next(v))
                    return false;
                o.scale = std::strtof(v.c_str(), nullptr);
                if (o.scale <= 0) {
                    error = "--scale must be positive";
                    return false;
                }
            } else if (a == "--dark") {
                o.dark = true;
            } else if (a == "--light") {
                o.dark = false;
            } else if (a == "--color-scheme") {
                if (!next(v))
                    return false;
                if (v != "dark" && v != "light") {
                    error = "--color-scheme needs dark or light";
                    return false;
                }
                o.colorScheme = v == "dark";
            } else if (a == "--theme-global") {
                if (!next(v))
                    return false;
                auto t = splitTarget(v);
                if (t.global.empty()) {
                    error = "--theme-global needs Global.property";
                    return false;
                }
                o.themeGlobal = t.global;
                o.themeProperty = t.property;
            } else if (a == "--set") {
                if (!next(v))
                    return false;
                auto eq = v.find('=');
                if (eq == std::string::npos) {
                    error = "--set needs name=value";
                    return false;
                }
                o.sets.emplace_back(v.substr(0, eq), v.substr(eq + 1));
            } else if (a == "--json") {
                if (!next(v))
                    return false;
                o.jsonFiles.push_back(v);
            } else if (a == "--export") {
                if (!next(v))
                    return false;
                o.exports.push_back(v);
            } else if (a == "--style") {
                if (!next(o.style))
                    return false;
            } else if (a == "-I" || a == "--include") {
                if (!next(v))
                    return false;
                o.includes.push_back(v);
            } else if (a == "--hover") {
                if (!point(Step::Hover))
                    return false;
            } else if (a == "--click") {
                if (!point(Step::Click))
                    return false;
            } else if (a == "--press") {
                if (!point(Step::Press))
                    return false;
            } else if (a == "--release") {
                if (!point(Step::Release))
                    return false;
            } else if (a == "--leave") {
                Step s;
                s.kind = Step::Leave;
                o.steps.push_back(s);
            } else if (a == "--scroll") {
                if (!next(v))
                    return false;
                float f[4];
                if (!parseFloats(v, f, 4)) {
                    error = "--scroll needs x,y,dx,dy";
                    return false;
                }
                Step s;
                s.kind = Step::Scroll;
                s.x = f[0];
                s.y = f[1];
                s.dx = f[2];
                s.dy = f[3];
                o.steps.push_back(s);
            } else if (a == "--type") {
                Step s;
                s.kind = Step::Type;
                if (!next(s.text))
                    return false;
                o.steps.push_back(s);
            } else if (a == "--key") {
                Step s;
                s.kind = Step::Key;
                if (!next(s.text))
                    return false;
                o.steps.push_back(s);
            } else if (a == "--wait") {
                if (!next(v))
                    return false;
                Step s;
                s.kind = Step::Wait;
                s.ms = std::atoi(v.c_str());
                o.steps.push_back(s);
            } else if (a == "--crop") {
                if (!next(v))
                    return false;
                if (!parseFloats(v, o.crop, 4)) {
                    error = "--crop needs x,y,w,h";
                    return false;
                }
                o.hasCrop = true;
            } else if (a == "--zoom") {
                if (!next(v))
                    return false;
                o.zoom = std::clamp(std::atoi(v.c_str()), 1, 16);
            } else if (a == "--wrap") {
                o.wrap = true;
            } else if (a == "--bg") {
                if (!next(o.wrapBackground))
                    return false;
                o.wrap = true;
            } else if (a == "--font") {
                if (!next(o.wrapFont))
                    return false;
                o.wrap = true;
            } else if (a == "--bind") {
                if (!next(o.bind))
                    return false;
            } else if (a == "--tail") {
                if (!next(o.tail))
                    return false;
            } else if (a == "--emoji-callback") {
                if (!next(o.emojiCallback))
                    return false;
            } else if (a == "--emoji-family") {
                if (!next(o.emojiFamily))
                    return false;
            } else if (a == "--list") {
                o.list = true;
            } else if (a == "--quiet") {
                o.quiet = true;
            } else if (a == "--stats") {
                o.stats = true;
            } else if (a == "--help" || a == "-h") {
                error = "";
                return false;
            } else if (a.rfind("--", 0) == 0) {
                error = "unknown option " + a;
                return false;
            } else {
                positional.push_back(args[i]);
            }
        }
        if (positional.empty()) {
            error = "missing the .slint file";
            return false;
        }
        o.file = positional[0];
        if (positional.size() > 1)
            o.component = positional[1];
        if (positional.size() > 2) {
            error = "unexpected argument " + positional[2];
            return false;
        }
        if (o.out.empty() && !o.list) {
            error = "missing --out";
            return false;
        }
        return true;
    }

    std::string keyText(const std::string &name) {
        static const std::pair<const char *, const char *> keys[] = {
                {"Return",    "\n"},
                {"Enter",     "\n"},
                {"Tab",       "\t"},
                {"Escape",    "\x1b"},
                {"Backspace", "\x08"},
                {"Delete",    "\x7f"},
                {"Space",     " "},
                {"Up",        "\xEF\x9C\x80"},
                {"Down",      "\xEF\x9C\x81"},
                {"Left",      "\xEF\x9C\x82"},
                {"Right",     "\xEF\x9C\x83"},
                {"Home",      "\xEF\x9C\xA9"},
                {"End",       "\xEF\x9C\xAB"},
        };
        for (const auto &[n, t]: keys)
            if (name == n)
                return t;
        return name;
    }

    std::vector <std::string> utf8Chars(const std::string &s) {
        std::vector <std::string> out;
        for (size_t i = 0; i < s.size();) {
            unsigned char c = (unsigned char) s[i];
            size_t n = c < 0x80 ? 1 : (c >> 5) == 6 ? 2 : (c >> 4) == 14 ? 3 : 4;
            out.push_back(s.substr(i, n));
            i += n;
        }
        return out;
    }

    std::string slintString(const std::string &s) {
        std::string out = "\"";
        for (char c: s) {
            if (c == '"' || c == '\\')
                out += '\\';
            out += c;
        }
        return out + "\"";
    }

    std::string masked(const std::string &src) {
        std::string out = src;
        size_t i = 0;
        while (i < src.size()) {
            if (src[i] == '/' && i + 1 < src.size() && src[i + 1] == '/') {
                while (i < src.size() && src[i] != '\n')
                    out[i++] = ' ';
            } else if (src[i] == '/' && i + 1 < src.size() && src[i + 1] == '*') {
                while (i < src.size() &&
                       !(src[i] == '*' && i + 1 < src.size() && src[i + 1] == '/')) {
                    if (src[i] != '\n')
                        out[i] = ' ';
                    i++;
                }
                if (i < src.size()) {
                    out[i] = ' ';
                    out[i + 1] = ' ';
                    i += 2;
                }
            } else if (src[i] == '"') {
                i++;
                while (i < src.size() && src[i] != '"') {
                    if (src[i] == '\\' && i + 1 < src.size()) {
                        out[i] = ' ';
                        i++;
                    }
                    if (src[i] != '\n')
                        out[i] = ' ';
                    i++;
                }
                i++;
            } else {
                i++;
            }
        }
        return out;
    }

    bool identChar(char c) {
        return isalnum((unsigned char) c) || c == '_' || c == '-';
    }

    std::string readIdent(const std::string &text, size_t &pos) {
        size_t start = pos;
        while (pos < text.size() && identChar(text[pos]))
            pos++;
        return text.substr(start, pos - start);
    }

    void skipSpace(const std::string &text, size_t &pos) {
        while (pos < text.size() && isspace((unsigned char) text[pos]))
            pos++;
    }

    bool wordAt(const std::string &text, size_t pos, const char *word) {
        size_t n = strlen(word);
        if (text.compare(pos, n, word) != 0)
            return false;
        if (pos > 0 && identChar(text[pos - 1]))
            return false;
        return pos + n >= text.size() || !identChar(text[pos + n]);
    }

    std::string selectComponent(std::string &source, const std::string &name) {
        std::string mask = masked(source);
        std::string wanted = normalized(name);
        std::string tail = "export { " + name + " }\n";
        int depth = 0;
        for (size_t i = 0; i < mask.size(); i++) {
            char c = mask[i];
            if (c == '{') {
                depth++;
                continue;
            }
            if (c == '}') {
                depth--;
                continue;
            }
            if (depth != 0 || !wordAt(mask, i, "export"))
                continue;
            size_t exportPos = i;
            size_t pos = i + 6;
            skipSpace(mask, pos);
            if (wordAt(mask, pos, "component")) {
                pos += 9;
                skipSpace(mask, pos);
                if (normalized(readIdent(mask, pos)) == wanted)
                    source.replace(exportPos, 6, "      ");
                i = pos - 1;
                continue;
            }
            if (pos >= mask.size() || mask[pos] != '{')
                continue;
            size_t open = pos;
            size_t close = mask.find('}', open);
            if (close == std::string::npos)
                break;
            size_t item = open + 1;
            int kept = 0;
            bool removed = false;
            while (item < close) {
                size_t comma = mask.find(',', item);
                if (comma == std::string::npos || comma > close)
                    comma = close;
                size_t p = item;
                skipSpace(mask, p);
                std::string ident = p < comma ? readIdent(mask, p) : std::string();
                if (!ident.empty() && normalized(ident) == wanted) {
                    size_t end = comma < close ? comma + 1 : comma;
                    for (size_t k = item; k < end; k++)
                        if (source[k] != '\n')
                            source[k] = ' ';
                    removed = true;
                } else if (!ident.empty()) {
                    kept++;
                }
                item = comma + 1;
            }
            size_t after = close + 1;
            skipSpace(mask, after);
            bool hasFrom = wordAt(mask, after, "from");
            size_t stmtEnd = close + 1;
            std::string fromPath;
            if (hasFrom) {
                size_t q1 = source.find('"', after);
                size_t q2 = q1 == std::string::npos ? q1 : source.find('"', q1 + 1);
                if (q2 != std::string::npos) {
                    fromPath = source.substr(q1, q2 - q1 + 1);
                    stmtEnd = q2 + 1;
                    size_t semi = stmtEnd;
                    skipSpace(mask, semi);
                    if (semi < mask.size() && mask[semi] == ';')
                        stmtEnd = semi + 1;
                }
            }
            if (removed && !fromPath.empty())
                tail = "import { " + name + " } from " + fromPath + ";\n" + tail;
            if (removed && kept == 0) {
                for (size_t k = exportPos; k < stmtEnd; k++)
                    if (source[k] != '\n')
                        source[k] = ' ';
            } else if (removed) {
                size_t last = close;
                while (last > open && isspace((unsigned char) source[last - 1]))
                    last--;
                if (last > open && source[last - 1] == ',')
                    source[last - 1] = ' ';
            }
            i = close;
            depth = 0;
        }
        return tail;
    }

    std::optional <ComponentDefinition> compile(const Options &o, const std::string &path,
                                                const std::string &source, bool report) {
        ComponentCompiler compiler;
        compiler.set_style(o.style);
        if (!o.includes.empty()) {
            slint::SharedVector <slint::SharedString> paths;
            for (const auto &p: o.includes)
                paths.push_back(slint::SharedString(fullPath(p)));
            compiler.set_include_paths(paths);
        }
        auto def = compiler.build_from_source(source, path);
        if (report) {
            for (const auto &d: compiler.diagnostics()) {
                const char *level = d.level == DiagnosticLevel::Error ? "error"
                                                                      : d.level ==
                                                                        DiagnosticLevel::Warning
                                                                        ? "warning" : "note";
                fprintf(stderr, "%.*s:%zu:%zu: %s: %.*s\n", int(d.source_file.size()),
                        d.source_file.data(),
                        size_t(d.line), size_t(d.column), level, int(d.message.size()),
                        d.message.data());
            }
        }
        return def;
    }

    void printList(const ComponentDefinition &def) {
        auto name = def.name();
        printf("component %.*s\n", int(name.size()), name.data());
        for (const auto &p: def.properties())
            printf("  property %.*s: %s\n", int(p.property_name.size()), p.property_name.data(),
                   typeName(p.property_type));
        for (const auto &c: def.callbacks())
            printf("  callback %.*s\n", int(c.size()), c.data());
        for (const auto &f: def.functions())
            printf("  function %.*s\n", int(f.size()), f.data());
        for (const auto &g: def.globals()) {
            std::string gname(g.data(), g.size());
            printf("global %s\n", gname.c_str());
            if (auto props = def.global_properties(gname))
                for (const auto &p: *props)
                    printf("  property %.*s: %s\n", int(p.property_name.size()),
                           p.property_name.data(),
                           typeName(p.property_type));
            if (auto cbs = def.global_callbacks(gname))
                for (const auto &c: *cbs)
                    printf("  callback %.*s\n", int(c.size()), c.data());
            if (auto fns = def.global_functions(gname))
                for (const auto &f: *fns)
                    printf("  function %.*s\n", int(f.size()), f.data());
        }
    }

    int run(const std::vector <std::string> &args) {
        Options o;
        std::string error;
        if (!parseArgs(args, o, error)) {
            if (!error.empty())
                fprintf(stderr, "shot: %s\n", error.c_str());
            fputs(kUsage, error.empty() ? stdout : stderr);
            return error.empty() ? 0 : 2;
        }

        auto started = std::chrono::steady_clock::now();
        std::string path = fullPath(o.file);
        bool ok = false;
        std::string source = readFile(path, ok);
        if (!ok) {
            fprintf(stderr, "shot: cannot read %s\n", path.c_str());
            return 2;
        }
        if (source.size() >= 3 && (unsigned char) source[0] == 0xEF &&
            (unsigned char) source[1] == 0xBB
            && (unsigned char) source[2] == 0xBF)
            source.erase(0, 3);

        auto adapters = std::make_shared < std::vector < shot::HeadlessAdapter * >> ();
        slint::platform::set_platform(std::make_unique<shot::HeadlessPlatform>(adapters.get()));

        Context ctx;
        ctx.baseDir = dirOf(path);
        ctx.emojiFamily = o.emojiFamily;

        std::vector <std::string> wantedGlobals = o.exports;
        auto wantGlobal = [&](const std::string &g) {
            if (!g.empty() &&
                std::find(wantedGlobals.begin(), wantedGlobals.end(), g) == wantedGlobals.end())
                wantedGlobals.push_back(g);
        };
        if (o.dark)
            wantGlobal(o.themeGlobal);
        for (const auto &[name, value]: o.sets)
            wantGlobal(splitTarget(name).global);
        if (!o.emojiCallback.empty())
            wantGlobal(splitTarget(o.emojiCallback).global);

        std::optional<bool> scheme = o.colorScheme ? o.colorScheme : o.dark;
        std::string pick;
        if (!o.component.empty() && !o.wrap && o.bind.empty())
            pick = selectComponent(source, o.component);
        auto tailFor = [&](const std::vector <std::string> &extraExports) {
            std::string tail = "\n" + o.tail + "\n";
            if (scheme)
                tail += "import { Palette as ShotPalette__ } from \"std-widgets.slint\";\n"
                        "export global ShotScheme__ { public function apply(dark: bool) { "
                        "ShotPalette__.color-scheme = dark ? ColorScheme.dark : ColorScheme.light; } }\n";
            for (const auto &g: extraExports)
                tail += "export { " + g + " }\n";
            if (o.component.empty())
                return tail;
            if (o.wrap) {
                tail += "export component ShotRoot__ inherits Window {\n    background: " +
                        o.wrapBackground
                        + ";\n    default-font-family: " + slintString(o.wrapFont) + ";\n    "
                        + o.component + " { " + o.bind + " }\n}\n";
            } else if (!o.bind.empty()) {
                tail += "export component ShotRoot__ inherits " + o.component + " { " + o.bind +
                        " }\n";
            } else {
                tail += pick;
            }
            return tail;
        };

        auto def = compile(o, path, source + tailFor({}), true);
        if (!def) {
            fprintf(stderr, "shot: %s did not compile\n", path.c_str());
            return 1;
        }
        std::vector <std::string> missing;
        for (const auto &g: wantedGlobals)
            if (!hasGlobal(*def, g))
                missing.push_back(g);
        std::vector <std::string> added;
        for (const auto &g: missing) {
            std::vector <std::string> attempt = added;
            attempt.push_back(g);
            auto retry = compile(o, path, source + tailFor(attempt), false);
            if (retry) {
                def = retry;
                added = attempt;
            }
        }
        bool themeVisible = hasGlobal(*def, o.themeGlobal);
        if (o.dark && !themeVisible && !o.quiet)
            fprintf(stderr,
                    "shot: note: no global '%s' in %s, --dark/--light only sets the std-widgets colour scheme\n",
                    o.themeGlobal.c_str(), o.file.c_str());

        if (o.list) {
            printList(*def);
            return 0;
        }
        auto compiled = std::chrono::steady_clock::now();

        auto instance = def->create();
        instance->window();
        if (adapters->empty()) {
            fprintf(stderr, "shot: no window adapter was created\n");
            return 1;
        }
        shot::HeadlessAdapter *adapter = adapters->front();

        bool failed = false;
        for (const auto &file: o.jsonFiles) {
            bool readOk = false;
            std::string text = readFile(fullPath(file), readOk);
            if (!readOk) {
                fprintf(stderr, "shot: cannot read %s\n", file.c_str());
                return 2;
            }
            shot::Json root;
            shot::JsonParser parser(text);
            if (!parser.parse(root, error) || root.kind != shot::Json::Object) {
                fprintf(stderr, "shot: %s: %s\n", file.c_str(),
                        error.empty() ? "the top level must be an object" : error.c_str());
                return 2;
            }
            std::string savedBase = ctx.baseDir;
            ctx.baseDir = dirOf(fullPath(file));
            for (const auto &[key, child]: root.fields) {
                if (child.kind == shot::Json::Object && key.find('.') == std::string::npos
                    && hasGlobal(*def, key) && !findType(def->properties(), key)) {
                    for (const auto &[prop, value]: child.fields) {
                        if (!assign(ctx, *def, *instance, Target{key, prop}, value, error)) {
                            fprintf(stderr, "shot: %s: %s\n", file.c_str(), error.c_str());
                            failed = true;
                        }
                    }
                    continue;
                }
                if (!assign(ctx, *def, *instance, splitTarget(key), child, error)) {
                    fprintf(stderr, "shot: %s: %s\n", file.c_str(), error.c_str());
                    failed = true;
                }
            }
            ctx.baseDir = savedBase;
        }
        for (const auto &[name, raw]: o.sets) {
            Target target = splitTarget(name);
            std::optional <ValueType> type;
            if (target.global.empty()) {
                type = findType(def->properties(), target.property);
            } else if (auto props = def->global_properties(target.global)) {
                type = findType(*props, target.property);
            }
            shot::Json json = jsonForSet(raw, type.value_or(ValueType::String));
            if (!assign(ctx, *def, *instance, target, json, error)) {
                fprintf(stderr, "shot: --set %s: %s\n", name.c_str(), error.c_str());
                failed = true;
            }
        }
        if (o.dark && themeVisible) {
            shot::Json json;
            json.kind = shot::Json::Bool;
            json.boolean = *o.dark;
            if (!assign(ctx, *def, *instance, Target{o.themeGlobal, o.themeProperty}, json,
                        error)) {
                fprintf(stderr, "shot: --dark/--light: %s\n", error.c_str());
                failed = true;
            }
        }
        if (scheme) {
            Value arg(*scheme);
            if (!instance->invoke_global("ShotScheme__", "apply", std::span<const Value>(&arg, 1)))
                fprintf(stderr, "shot: warning: could not set the std-widgets colour scheme\n");
        }
        if (failed)
            return 2;

        auto emojiCache = std::make_shared < std::map < std::pair < std::string,
        int > , slint::Image >> ();
        if (!o.emojiCallback.empty()) {
            Target t = splitTarget(o.emojiCallback);
            Context *ctxPtr = &ctx;
            bool set = instance->set_global_callback(
                    t.global, normalized(t.property),
                    [ctxPtr, emojiCache](std::span<const Value> a) -> Value {
                        if (a.size() < 2)
                            return Value(slint::Image());
                        auto text = a[0].to_string();
                        auto size = a[1].to_number();
                        if (!text || !size)
                            return Value(slint::Image());
                        std::string key(text->data(), text->size());
                        int px = std::max(1, int(std::lround(*size)));
                        auto found = emojiCache->find({key, px});
                        if (found != emojiCache->end())
                            return Value(found->second);
                        std::wstring family = shot::widen(ctxPtr->emojiFamily);
                        slint::Image img(ctxPtr->emoji.renderIcon(shot::widen(key), uint32_t(px),
                                                                  family.c_str()));
                        emojiCache->emplace(std::make_pair(key, px), img);
                        return Value(img);
                    });
            if (!set) {
                fprintf(stderr, "shot: --emoji-callback: no callback %s\n",
                        o.emojiCallback.c_str());
                return 2;
            }
        }

        instance->show();
        float width = o.width.value_or(adapter->preferred && adapter->preferred->width > 0
                                       ? adapter->preferred->width : 800.0f);
        float height = o.height.value_or(adapter->preferred && adapter->preferred->height > 0
                                         ? adapter->preferred->height : 600.0f);
        adapter->resize(width, height, o.scale);
        auto &window = adapter->window();
        window.dispatch_window_active_changed_event(true);

        auto created = std::chrono::steady_clock::now();
        auto pixels = adapter->render();
        auto firstFrame = std::chrono::steady_clock::now();
        if (o.stats) {
            adapter->resize(width + 1, height, o.scale);
            auto t0 = std::chrono::steady_clock::now();
            adapter->render();
            auto t1 = std::chrono::steady_clock::now();
            adapter->resize(width, height, o.scale);
            adapter->render();
            auto t2 = std::chrono::steady_clock::now();
            adapter->render();
            auto t3 = std::chrono::steady_clock::now();
            auto us = [](auto a, auto b) {
                return static_cast<long long>(std::chrono::duration_cast<std::chrono::microseconds>(
                        b - a).count());
            };
            PROCESS_MEMORY_COUNTERS_EX pmc{};
            GetProcessMemoryInfo(GetCurrentProcess(),
                                 reinterpret_cast<PROCESS_MEMORY_COUNTERS *>(&pmc), sizeof pmc);
            printf("stats: compile %lld us, create+properties %lld us, first frame (layout+draw) %lld us, "
                   "relayout+draw %lld us and %lld us, redraw only %lld us\n",
                   us(started, compiled), us(compiled, created), us(created, firstFrame),
                   us(t0, t1), us(t1, t2),
                   us(t2, t3));
            printf("stats: private bytes %zu KB, working set %zu KB, peak working set %zu KB\n",
                   size_t(pmc.PrivateUsage / 1024), size_t(pmc.WorkingSetSize / 1024),
                   size_t(pmc.PeakWorkingSetSize / 1024));
        }
        bool acted = false;
        for (const auto &s: o.steps) {
            slint::LogicalPosition pos({s.x, s.y});
            switch (s.kind) {
                case Step::Hover:
                    window.dispatch_pointer_move_event(pos);
                    break;
                case Step::Click:
                    window.dispatch_pointer_move_event(pos);
                    window.dispatch_pointer_press_event(pos, slint::PointerEventButton::Left);
                    window.dispatch_pointer_release_event(pos, slint::PointerEventButton::Left);
                    break;
                case Step::Press:
                    window.dispatch_pointer_move_event(pos);
                    window.dispatch_pointer_press_event(pos, slint::PointerEventButton::Left);
                    break;
                case Step::Release:
                    window.dispatch_pointer_release_event(pos, slint::PointerEventButton::Left);
                    break;
                case Step::Leave:
                    window.dispatch_pointer_exit_event();
                    break;
                case Step::Scroll:
                    window.dispatch_pointer_scroll_event(pos, s.dx, s.dy);
                    break;
                case Step::Type:
                    for (const auto &ch: utf8Chars(s.text)) {
                        window.dispatch_key_press_event(slint::SharedString(ch));
                        window.dispatch_key_release_event(slint::SharedString(ch));
                    }
                    break;
                case Step::Key: {
                    slint::SharedString k(keyText(s.text));
                    window.dispatch_key_press_event(k);
                    window.dispatch_key_release_event(k);
                    break;
                }
                case Step::Wait: {
                    auto until = std::chrono::steady_clock::now() + std::chrono::milliseconds(s.ms);
                    while (std::chrono::steady_clock::now() < until) {
                        std::this_thread::sleep_for(std::chrono::milliseconds(8));
                        slint::platform::update_timers_and_animations();
                    }
                    break;
                }
            }
            slint::platform::update_timers_and_animations();
            acted = true;
        }
        pixels = adapter->render();
        if (acted)
            pixels = adapter->render();

        uint32_t pw = adapter->physical.width;
        uint32_t ph = adapter->physical.height;
        if (o.hasCrop) {
            auto px = [&](float v) { return int(std::lround(v * o.scale)); };
            int x0 = std::clamp(px(o.crop[0]), 0, int(pw));
            int y0 = std::clamp(px(o.crop[1]), 0, int(ph));
            int x1 = std::clamp(px(o.crop[0] + o.crop[2]), x0, int(pw));
            int y1 = std::clamp(px(o.crop[1] + o.crop[3]), y0, int(ph));
            if (x1 > x0 && y1 > y0) {
                std::vector <slint::Rgb8Pixel> cropped(size_t(x1 - x0) * size_t(y1 - y0));
                for (int y = y0; y < y1; y++)
                    for (int x = x0; x < x1; x++)
                        cropped[size_t(y - y0) * size_t(x1 - x0) + size_t(x - x0)] = pixels[
                                size_t(y) * pw + size_t(x)];
                pixels.swap(cropped);
                pw = uint32_t(x1 - x0);
                ph = uint32_t(y1 - y0);
            }
        }
        if (o.zoom > 1) {
            uint32_t z = uint32_t(o.zoom);
            std::vector <slint::Rgb8Pixel> big(size_t(pw) * z * size_t(ph) * z);
            for (uint32_t y = 0; y < ph * z; y++)
                for (uint32_t x = 0; x < pw * z; x++)
                    big[size_t(y) * pw * z + x] = pixels[size_t(y / z) * pw + x / z];
            pixels.swap(big);
            pw *= z;
            ph *= z;
        }

        std::string outPath = fullPath(o.out);
        if (!shot::writePngRgb(shot::widen(outPath), pw, ph, pixels, &error)) {
            fprintf(stderr, "shot: cannot write %s: %s\n", outPath.c_str(), error.c_str());
            return 3;
        }
        instance->hide();
        if (!o.quiet) {
            auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now() - started).count();
            printf("shot: %s %ux%u px (logical %gx%g, scale %g) %lld ms\n", outPath.c_str(), pw, ph,
                   double(width), double(height), double(o.scale), static_cast<long long>(ms));
        }
        return 0;
    }

}

int main() {
    int argc = 0;
    LPWSTR *argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    std::vector <std::string> args;
    for (int i = 1; i < argc; i++)
        args.push_back(shot::narrow(argv[i]));
    LocalFree(argv);
    SetConsoleOutputCP(CP_UTF8);
    int code = run(args);
    fflush(stdout);
    fflush(stderr);
    return code;
}
