#ifdef _WIN32
#include <windows.h>
#else
#include <unistd.h>
#endif

#include <cstdio>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#ifdef _WIN32
static std::string narrow(const std::wstring &text) {
    int size = WideCharToMultiByte(CP_UTF8, 0, text.c_str(), int(text.size()), nullptr, 0, nullptr, nullptr);
    std::string out(size_t(size), '\0');
    WideCharToMultiByte(CP_UTF8, 0, text.c_str(), int(text.size()), out.data(), size, nullptr, nullptr);
    return out;
}

static std::wstring wide(const std::string &text) {
    int size = MultiByteToWideChar(CP_UTF8, 0, text.c_str(), int(text.size()), nullptr, 0);
    std::wstring out(size_t(size), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, text.c_str(), int(text.size()), out.data(), size);
    return out;
}

static std::wstring folder() {
    wchar_t path[MAX_PATH * 4];
    DWORD length = GetModuleFileNameW(nullptr, path, DWORD(sizeof path / sizeof path[0]));
    std::wstring text(path, length);
    return text.substr(0, text.find_last_of(L'\\') + 1);
}

static std::wstring in_folder(const std::string &name) { return folder() + wide(name); }

static void pause_ms(int ms) { Sleep(DWORD(ms)); }
#else
static std::string in_folder(const std::string &name) {
    char path[4096];
    ssize_t length = readlink("/proc/self/exe", path, sizeof path - 1);
    std::string text(path, length > 0 ? size_t(length) : 0);
    return text.substr(0, text.find_last_of('/') + 1) + name;
}

static void pause_ms(int ms) { usleep(useconds_t(ms) * 1000); }
#endif

static bool print_file(const std::string &name) {
    std::ifstream file(in_folder(name), std::ios::binary);
    if (!file)
        return false;
    std::ostringstream text;
    text << file.rdbuf();
    std::fwrite(text.str().data(), 1, text.str().size(), stdout);
    std::fputc('\n', stdout);
    return true;
}

static int run(const std::vector<std::string> &args) {
    std::string url = args.empty() ? "" : args.back();
    bool version = false, flat = false, big_j = false, small_j = false;
    for (const auto &arg: args) {
        if (arg == "--version") version = true;
        if (arg == "--flat-playlist") flat = true;
        if (arg == "-J") big_j = true;
        if (arg == "-j") small_j = true;
    }
    {
        std::ofstream log(in_folder("fake-calls.log"), std::ios::app);
        for (const auto &arg: args)
            log << arg << ' ';
        log << '\n';
    }
    if (version) {
        std::puts("2026.10.26");
        return 0;
    }
    if (url.find("slow") != std::string::npos)
        pause_ms(2500);
    if (url.find("broken") != std::string::npos) {
        std::fputs("ERROR: [fake] This video is not available\n", stderr);
        return 1;
    }
    if (big_j || flat) {
        if (url.find("list=") != std::string::npos && print_file("playlist.json"))
            return 0;
        std::fputs("ERROR: [fake] not a playlist\n", stderr);
        return 1;
    }
    if (small_j) {
        for (size_t i = 0; i + 1 < args.size(); i++) {
            if (args[i] != "-f")
                continue;
            std::string id = args[i + 1].substr(0, args[i + 1].find_first_of("+/["));
            if (!id.empty() && print_file("format-" + id + ".json"))
                return 0;
        }
        return print_file("video.json") ? 0 : 1;
    }
    std::fputs("ERROR: [fake] downloads are not simulated\n", stderr);
    return 1;
}

#ifdef _WIN32
int wmain(int argc, wchar_t **argv) {
    std::vector<std::string> args;
    for (int i = 1; i < argc; i++)
        args.push_back(narrow(argv[i]));
    return run(args);
}
#else
int main(int argc, char **argv) {
    return run(std::vector<std::string>(argv + 1, argv + argc));
}
#endif
