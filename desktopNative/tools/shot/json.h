#pragma once

#include <cstdlib>
#include <string>
#include <utility>
#include <vector>

namespace shot {

    struct Json {
        enum Kind {
            Null, Bool, Number, String, Array, Object
        };
        Kind kind = Null;
        bool boolean = false;
        double number = 0;
        std::string text;
        std::vector <Json> items;
        std::vector <std::pair<std::string, Json>> fields;
    };

    class JsonParser {
    public:
        explicit JsonParser(const std::string &source) : src(source) {}

        bool parse(Json &out, std::string &error) {
            if (src.size() >= 3 && (unsigned char) src[0] == 0xEF && (unsigned char) src[1] == 0xBB
                && (unsigned char) src[2] == 0xBF)
                pos = 3;
            if (!value(out)) {
                error = message + " at offset " + std::to_string(pos);
                return false;
            }
            space();
            if (pos != src.size()) {
                error = "unexpected text after the JSON value at offset " + std::to_string(pos);
                return false;
            }
            return true;
        }

    private:
        const std::string &src;
        size_t pos = 0;
        std::string message;

        void space() {
            while (pos < src.size()
                   && (src[pos] == ' ' || src[pos] == '\t' || src[pos] == '\r' || src[pos] == '\n'))
                pos++;
        }

        bool fail(const char *what) {
            message = what;
            return false;
        }

        static void appendUtf8(std::string &out, unsigned cp) {
            if (cp < 0x80) {
                out += char(cp);
            } else if (cp < 0x800) {
                out += char(0xC0 | (cp >> 6));
                out += char(0x80 | (cp & 0x3F));
            } else if (cp < 0x10000) {
                out += char(0xE0 | (cp >> 12));
                out += char(0x80 | ((cp >> 6) & 0x3F));
                out += char(0x80 | (cp & 0x3F));
            } else {
                out += char(0xF0 | (cp >> 18));
                out += char(0x80 | ((cp >> 12) & 0x3F));
                out += char(0x80 | ((cp >> 6) & 0x3F));
                out += char(0x80 | (cp & 0x3F));
            }
        }

        bool hex4(unsigned &out) {
            if (pos + 4 > src.size())
                return false;
            out = 0;
            for (int i = 0; i < 4; i++) {
                char c = src[pos++];
                out <<= 4;
                if (c >= '0' && c <= '9')
                    out |= unsigned(c - '0');
                else if (c >= 'a' && c <= 'f')
                    out |= unsigned(c - 'a' + 10);
                else if (c >= 'A' && c <= 'F')
                    out |= unsigned(c - 'A' + 10);
                else
                    return false;
            }
            return true;
        }

        bool string(std::string &out) {
            if (pos >= src.size() || src[pos] != '"')
                return fail("expected a string");
            pos++;
            while (pos < src.size()) {
                char c = src[pos++];
                if (c == '"')
                    return true;
                if (c != '\\') {
                    out += c;
                    continue;
                }
                if (pos >= src.size())
                    break;
                char e = src[pos++];
                switch (e) {
                    case 'n':
                        out += '\n';
                        break;
                    case 't':
                        out += '\t';
                        break;
                    case 'r':
                        out += '\r';
                        break;
                    case 'b':
                        out += '\b';
                        break;
                    case 'f':
                        out += '\f';
                        break;
                    case 'u': {
                        unsigned cp = 0;
                        if (!hex4(cp))
                            return fail("bad \\u escape");
                        if (cp >= 0xD800 && cp < 0xDC00 && pos + 1 < src.size() && src[pos] == '\\'
                            && src[pos + 1] == 'u') {
                            pos += 2;
                            unsigned low = 0;
                            if (!hex4(low))
                                return fail("bad \\u escape");
                            cp = 0x10000 + ((cp - 0xD800) << 10) + (low - 0xDC00);
                        }
                        appendUtf8(out, cp);
                        break;
                    }
                    default:
                        out += e;
                        break;
                }
            }
            return fail("unterminated string");
        }

        bool value(Json &out) {
            space();
            if (pos >= src.size())
                return fail("unexpected end of JSON");
            char c = src[pos];
            if (c == '{') {
                pos++;
                out.kind = Json::Object;
                space();
                if (pos < src.size() && src[pos] == '}') {
                    pos++;
                    return true;
                }
                while (true) {
                    space();
                    std::string key;
                    if (!string(key))
                        return false;
                    space();
                    if (pos >= src.size() || src[pos] != ':')
                        return fail("expected ':'");
                    pos++;
                    Json child;
                    if (!value(child))
                        return false;
                    out.fields.emplace_back(std::move(key), std::move(child));
                    space();
                    if (pos < src.size() && src[pos] == ',') {
                        pos++;
                        continue;
                    }
                    if (pos < src.size() && src[pos] == '}') {
                        pos++;
                        return true;
                    }
                    return fail("expected ',' or '}'");
                }
            }
            if (c == '[') {
                pos++;
                out.kind = Json::Array;
                space();
                if (pos < src.size() && src[pos] == ']') {
                    pos++;
                    return true;
                }
                while (true) {
                    Json child;
                    if (!value(child))
                        return false;
                    out.items.push_back(std::move(child));
                    space();
                    if (pos < src.size() && src[pos] == ',') {
                        pos++;
                        continue;
                    }
                    if (pos < src.size() && src[pos] == ']') {
                        pos++;
                        return true;
                    }
                    return fail("expected ',' or ']'");
                }
            }
            if (c == '"') {
                out.kind = Json::String;
                return string(out.text);
            }
            if (src.compare(pos, 4, "true") == 0) {
                pos += 4;
                out.kind = Json::Bool;
                out.boolean = true;
                return true;
            }
            if (src.compare(pos, 5, "false") == 0) {
                pos += 5;
                out.kind = Json::Bool;
                out.boolean = false;
                return true;
            }
            if (src.compare(pos, 4, "null") == 0) {
                pos += 4;
                out.kind = Json::Null;
                return true;
            }
            const char *start = src.c_str() + pos;
            char *end = nullptr;
            double d = std::strtod(start, &end);
            if (end == start)
                return fail("unexpected character");
            pos += size_t(end - start);
            out.kind = Json::Number;
            out.number = d;
            return true;
        }
    };

}
