#include <windows.h>

#include <cstdio>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

static std::wstring folder() {
    wchar_t path[MAX_PATH * 4];
    DWORD length = GetModuleFileNameW(nullptr, path, DWORD(sizeof path / sizeof path[0]));
    std::wstring text(path, length);
    return text.substr(0, text.find_last_of(L'\\') + 1);
}

static bool print_file(const std::wstring &name) {
    std::ifstream file(folder() + name, std::ios::binary);
    if (!file)
        return false;
    std::ostringstream text;
    text << file.rdbuf();
    std::fwrite(text.str().data(), 1, text.str().size(), stdout);
    std::fputc('\n', stdout);
    return true;
}

int wmain(int argc, wchar_t **argv) {
    std::vector<std::wstring> args(argv + 1, argv + argc);
    std::wstring url = args.empty() ? L"" : args.back();
    bool version = false, flat = false, big_j = false, small_j = false;
    for (const auto &arg: args) {
        if (arg == L"--version") version = true;
        if (arg == L"--flat-playlist") flat = true;
        if (arg == L"-J") big_j = true;
        if (arg == L"-j") small_j = true;
    }
    {
        std::ofstream log(folder() + L"fake-calls.log", std::ios::app);
        for (const auto &arg: args) {
            int size = WideCharToMultiByte(CP_UTF8, 0, arg.c_str(), int(arg.size()), nullptr, 0, nullptr, nullptr);
            std::string narrow(size_t(size), '\0');
            WideCharToMultiByte(CP_UTF8, 0, arg.c_str(), int(arg.size()), narrow.data(), size, nullptr, nullptr);
            log << narrow << ' ';
        }
        log << '\n';
    }
    if (version) {
        std::puts("2026.10.26");
        return 0;
    }
    if (url.find(L"slow") != std::wstring::npos)
        Sleep(2500);
    if (url.find(L"broken") != std::wstring::npos) {
        std::fputs("ERROR: [fake] This video is not available\n", stderr);
        return 1;
    }
    if (big_j || flat) {
        if (url.find(L"list=") != std::wstring::npos && print_file(L"playlist.json"))
            return 0;
        std::fputs("ERROR: [fake] not a playlist\n", stderr);
        return 1;
    }
    if (small_j) {
        for (size_t i = 0; i + 1 < args.size(); i++) {
            if (args[i] != L"-f")
                continue;
            std::wstring id = args[i + 1].substr(0, args[i + 1].find_first_of(L"+/["));
            if (!id.empty() && print_file(L"format-" + id + L".json"))
                return 0;
        }
        return print_file(L"video.json") ? 0 : 1;
    }
    std::fputs("ERROR: [fake] downloads are not simulated\n", stderr);
    return 1;
}
