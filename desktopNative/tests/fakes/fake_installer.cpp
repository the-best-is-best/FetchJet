#define NOMINMAX
#include <windows.h>

#include <string>

int wmain(int argc, wchar_t **argv) {
    FILETIME now;
    GetSystemTimeAsFileTime(&now);
    unsigned long long ticks = (static_cast<unsigned long long>(now.dwHighDateTime) << 32) | now.dwLowDateTime;
    unsigned long long unixMs = (ticks - 116444736000000000ULL) / 10000ULL;
    std::wstring self = argv[0];
    std::wstring folder = self.substr(0, self.find_last_of(L"\\/") + 1);
    std::wstring out = folder + L"installer-ran.txt";
    std::wstring text = std::to_wstring(unixMs) + L"\n" + GetCommandLineW() + L"\n";
    int size = WideCharToMultiByte(CP_UTF8, 0, text.c_str(), static_cast<int>(text.size()), nullptr, 0, nullptr, nullptr);
    std::string bytes(size, '\0');
    WideCharToMultiByte(CP_UTF8, 0, text.c_str(), static_cast<int>(text.size()), bytes.data(), size, nullptr, nullptr);
    HANDLE file = CreateFileW(out.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE)
        return 1;
    DWORD written = 0;
    WriteFile(file, bytes.data(), static_cast<DWORD>(bytes.size()), &written, nullptr);
    CloseHandle(file);
    return 0;
}
