#pragma once

#include <cstdio>
#include <string>

namespace fj {

    inline std::string emoji_file_name(const char *data, size_t length, int size) {
        static const int sizes[] = {20, 32, 48, 64};
        std::string name;
        const unsigned char *bytes = reinterpret_cast<const unsigned char *>(data);
        for (size_t i = 0; i < length;) {
            unsigned int code = bytes[i];
            int extra = code >= 0xF0 ? 3 : code >= 0xE0 ? 2 : code >= 0xC0 ? 1 : 0;
            code &= extra == 3 ? 0x07 : extra == 2 ? 0x0F : extra == 1 ? 0x1F : 0x7F;
            if (extra > 0 && i + size_t(extra) >= length)
                return std::string();
            for (int k = 1; k <= extra; k++)
                code = (code << 6) | (bytes[i + size_t(k)] & 0x3F);
            i += size_t(extra) + 1;
            if (code == 0xFE0F)
                continue;
            if (!name.empty())
                return std::string();
            char part[16];
            std::snprintf(part, sizeof part, "%x", code);
            name = part;
        }
        if (name.empty())
            return name;
        int pick = sizes[3];
        for (int candidate: sizes)
            if (candidate >= size) {
                pick = candidate;
                break;
            }
        return name + "-" + std::to_string(pick) + ".png";
    }

}
