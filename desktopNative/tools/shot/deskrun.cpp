#ifndef NOMINMAX
#    define NOMINMAX
#endif

#include <windows.h>
#include <psapi.h>

#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

namespace {

    struct Found {
        DWORD pid;
        std::vector <HWND> windows;
    };

    BOOL CALLBACK
    collect(HWND
    h,
    LPARAM p
    ) {
    auto *found = reinterpret_cast<Found *>(p);
    DWORD pid = 0;
    GetWindowThreadProcessId(h, &pid
    );
    if (pid == found->pid)
    found->windows.
    push_back(h);
    return
    TRUE;
}

bool savePng(HWND
h,
const std::wstring &path
);

void report(HANDLE process, DWORD pid, HDESK desk, const std::wstring &capture) {
    PROCESS_MEMORY_COUNTERS_EX pmc{};
    if (GetProcessMemoryInfo(process, reinterpret_cast<PROCESS_MEMORY_COUNTERS *>(&pmc),
                             sizeof pmc))
        printf("deskrun: private %zu KB, working set %zu KB, peak working set %zu KB\n",
               size_t(pmc.PrivateUsage / 1024), size_t(pmc.WorkingSetSize / 1024),
               size_t(pmc.PeakWorkingSetSize / 1024));
    Found found{pid, {}};
    EnumDesktopWindows(desk, collect, reinterpret_cast<LPARAM>(&found));
    int index = 0;
    for (HWND h: found.windows) {
        wchar_t cls[256] = {}, title[256] = {};
        GetClassNameW(h, cls, 255);
        GetWindowTextW(h, title, 255);
        RECT r{};
        GetWindowRect(h, &r);
        printf("deskrun: window class '%ls' title '%ls' visible %d rect %ld,%ld %ldx%ld\n", cls,
               title,
               int(IsWindowVisible(h)), r.left, r.top, r.right - r.left, r.bottom - r.top);
        if (!capture.empty() && IsWindowVisible(h) && r.right - r.left > 1 &&
            r.bottom - r.top > 1) {
            std::wstring path = capture;
            if (index > 0) {
                auto dot = path.find_last_of(L'.');
                path.insert(dot == std::wstring::npos ? path.size() : dot,
                            L"-" + std::to_wstring(index));
            }
            printf("deskrun: capture %ls %s\n", path.c_str(), savePng(h, path) ? "ok" : "failed");
            index++;
        }
    }
}

bool savePng(HWND
h,
const std::wstring &path
)
{
RECT r{};
GetClientRect(h, &r
);
int w = r.right - r.left, hgt = r.bottom - r.top;
if (w <= 0 || hgt <= 0)
return false;
HDC screen = GetDC(nullptr);
HDC mem = CreateCompatibleDC(screen);
BITMAPINFO bi{};
bi.bmiHeader.
biSize = sizeof bi.bmiHeader;
bi.bmiHeader.
biWidth = w;
bi.bmiHeader.
biHeight = -hgt;
bi.bmiHeader.
biPlanes = 1;
bi.bmiHeader.
biBitCount = 32;
bi.bmiHeader.
biCompression = BI_RGB;
void *bits = nullptr;
HBITMAP bmp = CreateDIBSection(screen, &bi, DIB_RGB_COLORS, &bits, nullptr, 0);
HGDIOBJ old = SelectObject(mem, bmp);
BOOL ok = PrintWindow(h, mem, 0x00000001 | 0x00000002);

GdiFlush();

std::wstring bmpPath = path;
bool written = false;
if (
ok &&bits
) {
BITMAPFILEHEADER fh{};
fh.
bfType = 0x4D42;
fh.
bfOffBits = sizeof fh + sizeof bi.bmiHeader;
fh.
bfSize = fh.bfOffBits + DWORD(w) * DWORD(hgt) * 4;
FILE *f = nullptr;
_wfopen_s(&f, bmpPath
.

c_str(),

L"wb");
if (f) {
fwrite(&fh,
sizeof fh, 1, f);
fwrite(&bi
.bmiHeader, sizeof bi.bmiHeader, 1, f);
fwrite(bits,
4,
size_t(w)
*
size_t(hgt), f
);
fclose(f);
written = true;
}
}
SelectObject(mem, old
);
DeleteObject(bmp);
DeleteDC(mem);
ReleaseDC(nullptr, screen
);
return
written;
}

}

int wmain(int argc, wchar_t **argv) {
    DWORD timeout = 15000;
    DWORD settle = 0;
    std::wstring capture;
    bool info = false;
    int i = 1;
    for (; i < argc; i++) {
        std::wstring a = argv[i];
        if (a == L"--timeout" && i + 1 < argc) {
            timeout = DWORD(_wtoi(argv[++i]));
        } else if (a == L"--after" && i + 1 < argc) {
            settle = DWORD(_wtoi(argv[++i]));
        } else if (a == L"--capture" && i + 1 < argc) {
            capture = argv[++i];
        } else if (a == L"--info") {
            info = true;
        } else if (a == L"--") {
            i++;
            break;
        } else {
            break;
        }
    }
    if (i >= argc) {
        fprintf(stderr,
                "deskrun [--timeout MS] [--after MS [--info] [--capture file.bmp]] -- program args...\n"
                "Runs the program on a new hidden desktop: its windows are never shown and cannot take focus.\n"
                "--after MS: after MS, report memory and windows (and save a BMP of each window), then stop the program.\n");
        return 2;
    }
    std::wstring command;
    for (; i < argc; i++) {
        std::wstring a = argv[i];
        bool quote = a.empty() || a.find_first_of(L" \t\"") != std::wstring::npos;
        if (!command.empty())
            command += L' ';
        if (!quote) {
            command += a;
            continue;
        }
        command += L'"';
        for (size_t k = 0; k < a.size(); k++) {
            size_t slashes = 0;
            while (k < a.size() && a[k] == L'\\') {
                slashes++;
                k++;
            }
            if (k == a.size()) {
                command.append(slashes * 2, L'\\');
                break;
            }
            if (a[k] == L'"')
                command.append(slashes * 2 + 1, L'\\');
            else
                command.append(slashes, L'\\');
            command += a[k];
        }
        command += L'"';
    }

    std::wstring name = L"fjshot-" + std::to_wstring(GetCurrentProcessId());
    HDESK desk = CreateDesktopW(name.c_str(), nullptr, nullptr, 0, GENERIC_ALL, nullptr);
    if (!desk) {
        fprintf(stderr, "deskrun: CreateDesktop failed %lu\n", GetLastError());
        return 3;
    }
    SetThreadDesktop(desk);
    std::wstring full = L"WinSta0\\" + name;
    STARTUPINFOW si{};
    si.cb = sizeof si;
    si.lpDesktop = full.data();
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    si.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
    si.hStdError = GetStdHandle(STD_ERROR_HANDLE);
    PROCESS_INFORMATION pi{};
    if (!CreateProcessW(nullptr, command.data(), nullptr, nullptr, TRUE, 0, nullptr, nullptr, &si,
                        &pi)) {
        fprintf(stderr, "deskrun: CreateProcess failed %lu\n", GetLastError());
        CloseDesktop(desk);
        return 3;
    }
    DWORD code = 0;
    if (settle > 0) {
        DWORD w = WaitForSingleObject(pi.hProcess, settle);
        if (w == WAIT_TIMEOUT) {
            if (info || !capture.empty())
                report(pi.hProcess, pi.dwProcessId, desk, capture);
            fflush(stdout);
            TerminateProcess(pi.hProcess, 0);
            WaitForSingleObject(pi.hProcess, 3000);
        }
        GetExitCodeProcess(pi.hProcess, &code);
    } else {
        DWORD w = WaitForSingleObject(pi.hProcess, timeout);
        if (w == WAIT_TIMEOUT) {
            fprintf(stderr, "deskrun: timeout after %lu ms, stopping the program\n", timeout);
            TerminateProcess(pi.hProcess, 124);
            WaitForSingleObject(pi.hProcess, 3000);
        }
        GetExitCodeProcess(pi.hProcess, &code);
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    CloseDesktop(desk);
    return int(code);
}
