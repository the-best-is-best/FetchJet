#pragma once

#include "app.h"
#include "json.h"

#include <cstdio>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

namespace fj {

    inline const Json &null_json() {
        static const Json empty;
        return empty;
    }

    inline const Json &child(const Json &j, const char *key) {
        const Json *found = j.find(key);
        return found ? *found : null_json();
    }

    inline slint::SharedString text(const Json &j, const char *key) {
        const Json *found = j.find(key);
        return found && found->kind == Json::String ? slint::SharedString(std::string_view(found->text))
                                                     : slint::SharedString();
    }

    inline int integer(const Json &j, const char *key) {
        const Json *found = j.find(key);
        return found && found->kind == Json::Number ? int(found->number) : 0;
    }

    inline float real(const Json &j, const char *key) {
        const Json *found = j.find(key);
        return found && found->kind == Json::Number ? float(found->number) : 0.0f;
    }

    inline bool flag(const Json &j, const char *key) {
        const Json *found = j.find(key);
        return found && found->kind == Json::Bool && found->boolean;
    }

    inline std::shared_ptr<slint::Model<slint::SharedString>> text_model(const Json &j, const char *key) {
        std::vector<slint::SharedString> values;
        for (const auto &item: child(j, key).items)
            values.emplace_back(std::string_view(item.text));
        return std::make_shared<slint::VectorModel<slint::SharedString>>(std::move(values));
    }

    inline std::shared_ptr<slint::Model<int>> int_model(const Json &j, const char *key) {
        std::vector<int> values;
        for (const auto &item: child(j, key).items)
            values.push_back(int(item.number));
        return std::make_shared<slint::VectorModel<int>>(std::move(values));
    }

    template<class T, class Parse>
    std::shared_ptr<slint::Model<T>> struct_model(const Json &j, const char *key, Parse parse) {
        std::vector<T> values;
        for (const auto &item: child(j, key).items)
            values.push_back(parse(item));
        return std::make_shared<slint::VectorModel<T>>(std::move(values));
    }

    template<class T, class Parse>
    void apply_rows(slint::VectorModel<T> &model, const Json &j, Parse parse) {
        const size_t wanted = size_t(integer(j, "n"));
        while (model.row_count() > wanted)
            model.erase(model.row_count() - 1);
        const Json &at = child(j, "at");
        const Json &rows = child(j, "rows");
        const size_t count = at.items.size() < rows.items.size() ? at.items.size() : rows.items.size();
        for (size_t i = 0; i < count; i++) {
            const size_t index = size_t(at.items[i].number);
            T value = parse(rows.items[i]);
            if (index < model.row_count())
                model.set_row_data(index, value);
            else
                model.push_back(value);
        }
    }

    class ActionWriter {
    public:
        explicit ActionWriter(const char *type) {
            out = "{\"type\":\"";
            out += type;
            out += "\"";
        }

        void add(const char *key, const slint::SharedString &value) {
            name(key);
            out += '"';
            for (unsigned char c: std::string_view(value)) {
                switch (c) {
                    case '"':
                        out += "\\\"";
                        break;
                    case '\\':
                        out += "\\\\";
                        break;
                    case '\n':
                        out += "\\n";
                        break;
                    case '\r':
                        out += "\\r";
                        break;
                    case '\t':
                        out += "\\t";
                        break;
                    default:
                        if (c < 0x20) {
                            char buffer[8];
                            std::snprintf(buffer, sizeof buffer, "\\u%04x", c);
                            out += buffer;
                        } else {
                            out += char(c);
                        }
                }
            }
            out += '"';
        }

        void add(const char *key, int value) {
            name(key);
            out += std::to_string(value);
        }

        void add(const char *key, float value) {
            name(key);
            char buffer[40];
            std::snprintf(buffer, sizeof buffer, "%g", double(value));
            out += buffer;
        }

        void add(const char *key, bool value) {
            name(key);
            out += value ? "true" : "false";
        }

        std::string finish() {
            out += '}';
            return std::move(out);
        }

    private:
        std::string out;

        void name(const char *key) {
            out += ",\"";
            out += key;
            out += "\":";
        }
    };

}
