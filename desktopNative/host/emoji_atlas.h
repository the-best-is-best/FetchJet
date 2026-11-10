#pragma once

#include "../tools/shot/emoji_d2d.h"

#include <cstdio>
#include <cstring>
#include <fstream>
#include <map>
#include <set>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

namespace fj {

    using EmojiKey = std::pair<std::string, int>;

    inline std::wstring emoji_widen(const std::string &text) {
        if (text.empty())
            return std::wstring();
        int length = MultiByteToWideChar(CP_UTF8, 0, text.data(), int(text.size()), nullptr, 0);
        std::wstring wide(size_t(length), L'\0');
        MultiByteToWideChar(CP_UTF8, 0, text.data(), int(text.size()), wide.data(), length);
        return wide;
    }

    inline std::string emoji_font_stamp() {
        wchar_t folder[MAX_PATH];
        UINT length = GetWindowsDirectoryW(folder, MAX_PATH);
        std::wstring path = std::wstring(folder, length) + L"\\Fonts\\seguiemj.ttf";
        WIN32_FILE_ATTRIBUTE_DATA data{};
        if (!GetFileAttributesExW(path.c_str(), GetFileExInfoStandard, &data))
            return "none";
        char text[96];
        std::snprintf(text, sizeof text, "%lu-%lu-%lu-%lu", data.nFileSizeHigh, data.nFileSizeLow,
                      data.ftLastWriteTime.dwHighDateTime, data.ftLastWriteTime.dwLowDateTime);
        return text;
    }

    inline std::vector<EmojiKey> read_emoji_list(const std::wstring &file) {
        std::vector<EmojiKey> wanted;
        std::ifstream input(file, std::ios::binary);
        std::string line;
        while (std::getline(input, line)) {
            if (!line.empty() && line.back() == '\r')
                line.pop_back();
            size_t tab = line.find('\t');
            if (tab == std::string::npos || tab + 1 >= line.size())
                continue;
            int size = std::atoi(line.substr(0, tab).c_str());
            if (size > 0 && size <= 512)
                wanted.emplace_back(line.substr(tab + 1), size);
        }
        return wanted;
    }

    inline int write_emoji_atlas(const std::wstring &folder, const std::wstring &list_file) {
        std::vector<EmojiKey> wanted = read_emoji_list(list_file);
        CreateDirectoryW(folder.c_str(), nullptr);
        std::wstring pixels_new = folder + L"\\atlas.bin.new";
        std::wstring index_new = folder + L"\\atlas.txt.new";
        {
            std::ofstream pixels(pixels_new, std::ios::binary | std::ios::trunc);
            std::ofstream index(index_new, std::ios::binary | std::ios::trunc);
            if (!pixels || !index)
                return 4;
            index << "fjemoji 1 " << emoji_font_stamp() << "\n";
            EmojiRasterizer drawer;
            unsigned long long offset = 0;
            for (const EmojiKey &item: wanted) {
                auto buffer = drawer.renderIcon(emoji_widen(item.first), uint32_t(item.second));
                unsigned width = buffer.width();
                unsigned height = buffer.height();
                if (width == 0 || height == 0) {
                    index << item.second << "\t0\t0\t0\t" << item.first << "\n";
                    continue;
                }
                size_t bytes = size_t(width) * height * 4;
                pixels.write(reinterpret_cast<const char *>(buffer.begin()), std::streamsize(bytes));
                index << item.second << "\t" << width << "\t" << height << "\t" << offset << "\t" << item.first << "\n";
                offset += bytes;
            }
        }
        MoveFileExW(pixels_new.c_str(), (folder + L"\\atlas.bin").c_str(), MOVEFILE_REPLACE_EXISTING);
        MoveFileExW(index_new.c_str(), (folder + L"\\atlas.txt").c_str(), MOVEFILE_REPLACE_EXISTING);
        return 0;
    }

    inline bool read_emoji_atlas(const std::wstring &folder, std::map<EmojiKey, slint::Image> &images,
                                 std::set<EmojiKey> &failed) {
        std::ifstream index(folder + L"\\atlas.txt", std::ios::binary);
        std::ifstream pixels(folder + L"\\atlas.bin", std::ios::binary);
        if (!index || !pixels)
            return false;
        std::string head;
        if (!std::getline(index, head) || head != "fjemoji 1 " + emoji_font_stamp())
            return false;
        std::string all((std::istreambuf_iterator<char>(pixels)), std::istreambuf_iterator<char>());
        std::string line;
        while (std::getline(index, line)) {
            std::istringstream fields(line);
            std::string size_text, width_text, height_text, offset_text, glyph;
            if (!std::getline(fields, size_text, '\t') || !std::getline(fields, width_text, '\t') ||
                !std::getline(fields, height_text, '\t') || !std::getline(fields, offset_text, '\t') ||
                !std::getline(fields, glyph))
                continue;
            int size = std::atoi(size_text.c_str());
            unsigned width = unsigned(std::atoi(width_text.c_str()));
            unsigned height = unsigned(std::atoi(height_text.c_str()));
            unsigned long long offset = std::strtoull(offset_text.c_str(), nullptr, 10);
            EmojiKey key(glyph, size);
            size_t bytes = size_t(width) * height * 4;
            if (width == 0 || height == 0 || offset + bytes > all.size()) {
                failed.insert(key);
                continue;
            }
            slint::SharedPixelBuffer<slint::Rgba8Pixel> buffer(width, height);
            std::memcpy(buffer.begin(), all.data() + offset, bytes);
            images[key] = slint::Image(buffer);
        }
        return true;
    }

}
