#define NOMINMAX

#include <windows.h>
#include <dwmapi.h>
#include <shellapi.h>
#include <shobjidl.h>

#include "../app_host.h"
#include "emoji_atlas.h"

#include <cstdlib>
#include <cstring>
#include <fstream>
#include <map>
#include <set>
#include <sstream>
#include <string>
#include <thread>

namespace fjhost::os {

    namespace {

        const UINT WM_TRAY = WM_APP + 7;
        const UINT MENU_SHOW = 1;
        const UINT MENU_CLOSE = 2;
        const UINT MENU_WHEN_DONE = 100;
        HWND g_helper = nullptr;
        HWND g_window = nullptr;
        NOTIFYICONDATAW g_tray{};
        bool g_tray_on = false;
        UINT g_taskbar_created = 0;
        DWORD g_last_toggle = 0;
        slint::Timer g_topmost_timer;

        std::map<fj::EmojiKey, slint::Image> g_emoji;
        std::set<fj::EmojiKey> g_emoji_failed;
        std::set<fj::EmojiKey> g_emoji_missing;
        std::set<fj::EmojiKey> g_emoji_sent;
        std::wstring g_emoji_dir;
        bool g_emoji_child = false;
        bool g_emoji_queued = false;
        int g_emoji_revision = 0;
        slint::Timer g_emoji_timer;

        std::wstring widen(const std::string &text) {
            if (text.empty())
                return std::wstring();
            int length = MultiByteToWideChar(CP_UTF8, 0, text.data(), int(text.size()), nullptr, 0);
            std::wstring wide(size_t(length), L'\0');
            MultiByteToWideChar(CP_UTF8, 0, text.data(), int(text.size()), wide.data(), length);
            return wide;
        }

        std::string narrow(const std::wstring &text) {
            if (text.empty())
                return std::string();
            int length = WideCharToMultiByte(CP_UTF8, 0, text.data(), int(text.size()), nullptr, 0, nullptr, nullptr);
            std::string out(size_t(length), '\0');
            WideCharToMultiByte(CP_UTF8, 0, text.data(), int(text.size()), out.data(), length, nullptr, nullptr);
            return out;
        }

        std::wstring module_path() {
            wchar_t path[MAX_PATH * 4];
            DWORD length = GetModuleFileNameW(nullptr, path, DWORD(std::size(path)));
            return std::wstring(path, length);
        }

        void copy_text(wchar_t *target, size_t capacity, const std::wstring &text) {
            size_t length = text.size() < capacity - 1 ? text.size() : capacity - 1;
            std::wmemcpy(target, text.data(), length);
            target[length] = L'\0';
        }

        HWND window_handle() {
            return has_ui() ? ui().window().win32_hwnd() : nullptr;
        }

        void add_tray() {
            if (!g_helper)
                return;
            int size = GetSystemMetrics(SM_CXSMICON);
            g_tray = {};
            g_tray.cbSize = sizeof g_tray;
            g_tray.hWnd = g_helper;
            g_tray.uID = 1;
            g_tray.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
            g_tray.uCallbackMessage = WM_TRAY;
            g_tray.hIcon = static_cast<HICON>(LoadImageW(GetModuleHandleW(nullptr), MAKEINTRESOURCEW(1), IMAGE_ICON, size, size, 0));
            if (has_ui())
                copy_text(g_tray.szTip, std::size(g_tray.szTip), widen(plain(ui().global<Core>().get_shell().trayTooltip)));
            g_tray_on = Shell_NotifyIconW(NIM_ADD, &g_tray) != FALSE;
        }

        void show_tray_menu() {
            auto &window = ui();
            ShellDto shell = window.global<Core>().get_shell();
            HMENU menu = CreatePopupMenu();
            AppendMenuW(menu, MF_STRING, MENU_SHOW, widen(plain(shell.trayShowLabel)).c_str());
            if (shell.whenDoneSupported && shell.whenDoneOptions) {
                HMENU choices = CreatePopupMenu();
                int count = int(shell.whenDoneOptions->row_count());
                for (int i = 0; i < count; i++) {
                    auto label = shell.whenDoneOptions->row_data(size_t(i));
                    UINT flags = MF_STRING | (i == shell.whenDoneIndex ? MF_CHECKED : MF_UNCHECKED);
                    AppendMenuW(choices, flags, MENU_WHEN_DONE + UINT(i), widen(label ? plain(*label) : std::string()).c_str());
                }
                AppendMenuW(menu, MF_POPUP, reinterpret_cast<UINT_PTR>(choices), widen(plain(shell.whenDoneLabel)).c_str());
            }
            AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
            AppendMenuW(menu, MF_STRING, MENU_CLOSE, widen(plain(shell.trayCloseLabel)).c_str());
            POINT at{};
            GetCursorPos(&at);
            SetForegroundWindow(g_helper);
            UINT flags = TPM_RIGHTBUTTON | TPM_RETURNCMD | TPM_NONOTIFY;
            if (window.global<Dir>().get_rtl())
                flags |= TPM_LAYOUTRTL;
            UINT picked = UINT(TrackPopupMenu(menu, flags, at.x, at.y, 0, g_helper, nullptr));
            PostMessageW(g_helper, WM_NULL, 0, 0);
            DestroyMenu(menu);
            if (picked == MENU_SHOW)
                show_window(true);
            else if (picked == MENU_CLOSE)
                quit_app();
            else if (picked >= MENU_WHEN_DONE)
                send_action("{\"type\":\"SetWhenDone\",\"index\":" + std::to_string(picked - MENU_WHEN_DONE) + "}");
        }

        LRESULT CALLBACK helper_proc(HWND window, UINT message, WPARAM wparam, LPARAM lparam) {
            if (message == WM_TRAY) {
                if (lparam == WM_LBUTTONUP)
                    toggle_window();
                else if (lparam == WM_RBUTTONUP || lparam == WM_CONTEXTMENU)
                    show_tray_menu();
                return 0;
            }
            if (g_taskbar_created != 0 && message == g_taskbar_created) {
                g_tray_on = false;
                add_tray();
                return 0;
            }
            if (message == WM_SETTINGCHANGE && lparam &&
                std::wcscmp(reinterpret_cast<const wchar_t *>(lparam), L"ImmersiveColorSet") == 0) {
                send_action(std::string("{\"type\":\"SystemTheme\",\"dark\":") + (system_uses_dark() ? "true" : "false") + "}");
                return 0;
            }
            return DefWindowProcW(window, message, wparam, lparam);
        }

        void queue_emoji();

        void load_emoji() {
            std::map<fj::EmojiKey, slint::Image> images;
            std::set<fj::EmojiKey> failed;
            if (fj::read_emoji_atlas(g_emoji_dir, images, failed)) {
                g_emoji = std::move(images);
                g_emoji_failed = std::move(failed);
            }
            for (const fj::EmojiKey &key: g_emoji_sent)
                if (!g_emoji.count(key))
                    g_emoji_failed.insert(key);
            g_emoji_sent.clear();
            for (auto it = g_emoji_missing.begin(); it != g_emoji_missing.end();) {
                if (g_emoji.count(*it) || g_emoji_failed.count(*it))
                    it = g_emoji_missing.erase(it);
                else
                    ++it;
            }
            if (has_ui())
                ui().global<Emoji>().set_revision(++g_emoji_revision);
        }

        void start_emoji_child() {
            if (g_emoji_child || g_emoji_missing.empty() || g_emoji_dir.empty())
                return;
            std::wstring list = g_emoji_dir + L"\\wanted.txt";
            {
                std::ofstream out(list, std::ios::binary | std::ios::trunc);
                for (const auto &entry: g_emoji)
                    out << entry.first.second << "\t" << entry.first.first << "\n";
                for (const fj::EmojiKey &key: g_emoji_failed)
                    out << key.second << "\t" << key.first << "\n";
                for (const fj::EmojiKey &key: g_emoji_missing)
                    out << key.second << "\t" << key.first << "\n";
            }
            std::wstring command = L"\"" + module_path() + L"\" --emoji-cache \"" + g_emoji_dir +
                                   L"\" --emoji-list \"" + list + L"\"";
            STARTUPINFOW startup{};
            startup.cb = sizeof startup;
            PROCESS_INFORMATION process{};
            if (!CreateProcessW(nullptr, command.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW | BELOW_NORMAL_PRIORITY_CLASS,
                                nullptr, nullptr, &startup, &process))
                return;
            CloseHandle(process.hThread);
            g_emoji_child = true;
            g_emoji_sent = g_emoji_missing;
            HANDLE child = process.hProcess;
            std::thread([child] {
                WaitForSingleObject(child, 120000);
                CloseHandle(child);
                slint::invoke_from_event_loop([] {
                    g_emoji_child = false;
                    if (!has_ui())
                        return;
                    load_emoji();
                    if (!g_emoji_missing.empty())
                        queue_emoji();
                });
            }).detach();
        }

        void queue_emoji() {
            if (g_emoji_queued)
                return;
            g_emoji_queued = true;
            slint::invoke_from_event_loop([] {
                g_emoji_timer.start(slint::TimerMode::SingleShot, std::chrono::milliseconds(300), [] {
                    g_emoji_queued = false;
                    start_emoji_child();
                });
            });
        }

        slint::Image emoji_icon(const slint::SharedString &glyph, int size) {
            fj::EmojiKey key(std::string(glyph.data(), glyph.size()), size);
            auto found = g_emoji.find(key);
            if (found != g_emoji.end())
                return found->second;
            if (size > 0 && size <= 512 && !key.first.empty() && !g_emoji_failed.count(key) && g_emoji_missing.insert(key).second)
                queue_emoji();
            return slint::Image();
        }

    }

    int child_main(int argc, char **argv, bool &handled) {
        for (int i = 1; i + 3 < argc; i++) {
            if (std::string(argv[i]) == "--emoji-cache" && std::string(argv[i + 2]) == "--emoji-list") {
                handled = true;
                return fj::write_emoji_atlas(widen(argv[i + 1]), widen(argv[i + 3]));
            }
        }
        return 0;
    }

    void prepare_process() {
        if (!std::getenv("SLINT_BACKEND"))
            _putenv_s("SLINT_BACKEND", "winit-software");
    }

    bool load_core(CoreApi &core) {
        std::wstring folder = module_path();
        folder = folder.substr(0, folder.find_last_of(L'\\') + 1);
        HMODULE dll = LoadLibraryW((folder + L"fjcore.dll").c_str());
        if (!dll)
            return false;
        core.prepare = reinterpret_cast<fj_prepare_fn>(GetProcAddress(dll, "fj_prepare"));
        core.start = reinterpret_cast<fj_start_fn>(GetProcAddress(dll, "fj_start"));
        core.snapshot = reinterpret_cast<fj_snapshot_fn>(GetProcAddress(dll, "fj_snapshot"));
        core.action = reinterpret_cast<fj_action_fn>(GetProcAddress(dll, "fj_action"));
        core.call = reinterpret_cast<fj_call_fn>(GetProcAddress(dll, "fj_call"));
        core.release = reinterpret_cast<fj_free_fn>(GetProcAddress(dll, "fj_free"));
        core.shutdown = reinterpret_cast<fj_shutdown_fn>(GetProcAddress(dll, "fj_shutdown"));
        return core.prepare && core.start && core.snapshot && core.action && core.call && core.release && core.shutdown;
    }

    void fatal(const std::string &text) {
        MessageBoxW(nullptr, widen(text).c_str(), L"FetchJet", MB_ICONERROR | MB_OK);
    }

    bool system_uses_dark() {
        DWORD value = 1;
        DWORD size = sizeof value;
        LSTATUS status = RegGetValueW(HKEY_CURRENT_USER,
                                      L"Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                                      L"AppsUseLightTheme", RRF_RT_REG_DWORD, nullptr, &value, &size);
        return status == ERROR_SUCCESS && value == 0;
    }

    std::string read_text_file(const std::string &utf8_path) {
        std::ifstream file(widen(utf8_path), std::ios::binary);
        std::ostringstream text;
        text << file.rdbuf();
        return text.str();
    }

    void *claim_instance(const std::string &bridge_port, bool &second) {
        HANDLE instance = CreateMutexW(nullptr, FALSE, (L"Local\\FetchJet.Native.Instance" + widen(bridge_port)).c_str());
        second = instance && GetLastError() == ERROR_ALREADY_EXISTS;
        return instance;
    }

    void release_instance(void *instance) {
        if (instance)
            CloseHandle(static_cast<HANDLE>(instance));
    }

    void apply_title_bar(bool dark) {
        HWND window = window_handle();
        if (!window)
            return;
        BOOL value = dark ? TRUE : FALSE;
        DwmSetWindowAttribute(window, 20, &value, sizeof value);
    }

    void bring_to_front(bool focus) {
        g_window = window_handle();
        if (!g_window)
            return;
        if (IsIconic(g_window))
            ShowWindow(g_window, SW_RESTORE);
        if (!focus)
            return;
        SetForegroundWindow(g_window);
        if (GetForegroundWindow() == g_window)
            return;
        INPUT keys[2] = {};
        keys[0].type = INPUT_KEYBOARD;
        keys[0].ki.wVk = VK_MENU;
        keys[1].type = INPUT_KEYBOARD;
        keys[1].ki.wVk = VK_MENU;
        keys[1].ki.dwFlags = KEYEVENTF_KEYUP;
        SendInput(2, keys, sizeof(INPUT));
        SetWindowPos(g_window, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);
        SetForegroundWindow(g_window);
        g_topmost_timer.start(slint::TimerMode::SingleShot, std::chrono::milliseconds(150), [] {
            if (g_window)
                SetWindowPos(g_window, HWND_NOTOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);
        });
    }

    void window_hidden() {
        g_window = nullptr;
    }

    bool window_minimized() {
        return !g_window || IsIconic(g_window);
    }

    bool toggle_too_soon() {
        DWORD now = GetTickCount();
        if (now - g_last_toggle < GetDoubleClickTime())
            return true;
        g_last_toggle = now;
        return false;
    }

    void start_shell() {
        WNDCLASSW type{};
        type.lpfnWndProc = helper_proc;
        type.hInstance = GetModuleHandleW(nullptr);
        type.lpszClassName = L"FetchJetShellHelper";
        RegisterClassW(&type);
        g_helper = CreateWindowExW(WS_EX_TOOLWINDOW, type.lpszClassName, L"FetchJet", WS_POPUP, 0, 0, 0, 0, nullptr, nullptr,
                                   type.hInstance, nullptr);
        g_taskbar_created = RegisterWindowMessageW(L"TaskbarCreated");
        add_tray();
    }

    bool tray_on() {
        return g_tray_on;
    }

    void update_tray_tip(const std::string &text) {
        if (!g_tray_on)
            return;
        std::wstring tip = widen(text);
        if (tip == g_tray.szTip)
            return;
        copy_text(g_tray.szTip, std::size(g_tray.szTip), tip);
        g_tray.uFlags = NIF_TIP;
        Shell_NotifyIconW(NIM_MODIFY, &g_tray);
    }

    void remove_tray() {
        if (g_tray_on)
            Shell_NotifyIconW(NIM_DELETE, &g_tray);
        g_tray_on = false;
    }

    void show_notice(const std::string &title, const std::string &text) {
        if (!g_tray_on)
            return;
        NOTIFYICONDATAW data = g_tray;
        data.uFlags = NIF_INFO;
        copy_text(data.szInfoTitle, std::size(data.szInfoTitle), widen(title));
        copy_text(data.szInfo, std::size(data.szInfo), widen(text));
        data.dwInfoFlags = NIIF_INFO | NIIF_RESPECT_QUIET_TIME;
        Shell_NotifyIconW(NIM_MODIFY, &data);
    }

    void start_emoji(const std::string &home_utf8) {
        std::wstring home = widen(home_utf8);
        if (home.empty())
            return;
        CreateDirectoryW((home + L"\\cache").c_str(), nullptr);
        g_emoji_dir = home + L"\\cache\\emoji";
        CreateDirectoryW(g_emoji_dir.c_str(), nullptr);
        load_emoji();
    }

    slint::Image emoji_fallback(const slint::SharedString &glyph, int size) {
        if (g_emoji_dir.empty())
            return slint::Image();
        return emoji_icon(glyph, size);
    }

    std::string exe_folder() {
        std::wstring path = module_path();
        return narrow(path.substr(0, path.find_last_of(L"\\/") + 1));
    }

    std::string pick_path(bool folder, const std::string &title_utf8, const std::string &initial_utf8,
                          const std::string &filter_utf8, const std::string &pattern_utf8) {
        std::wstring title = widen(title_utf8);
        std::wstring initial = widen(initial_utf8);
        std::wstring filter_name = widen(filter_utf8);
        std::wstring pattern = widen(pattern_utf8);
        std::wstring result;
        HRESULT init = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE);
        IFileOpenDialog *dialog = nullptr;
        if (SUCCEEDED(CoCreateInstance(CLSID_FileOpenDialog, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&dialog)))) {
            DWORD options = 0;
            dialog->GetOptions(&options);
            options |= FOS_FORCEFILESYSTEM | FOS_PATHMUSTEXIST;
            if (folder)
                options |= FOS_PICKFOLDERS;
            else
                options |= FOS_FILEMUSTEXIST;
            dialog->SetOptions(options);
            if (!title.empty())
                dialog->SetTitle(title.c_str());
            if (!folder && !pattern.empty()) {
                COMDLG_FILTERSPEC spec[1] = {{filter_name.c_str(), pattern.c_str()}};
                dialog->SetFileTypes(1, spec);
            }
            if (!initial.empty()) {
                IShellItem *start = nullptr;
                if (SUCCEEDED(SHCreateItemFromParsingName(initial.c_str(), nullptr, IID_PPV_ARGS(&start)))) {
                    dialog->SetFolder(start);
                    start->Release();
                }
            }
            if (SUCCEEDED(dialog->Show(g_window))) {
                IShellItem *item = nullptr;
                if (SUCCEEDED(dialog->GetResult(&item))) {
                    PWSTR path = nullptr;
                    if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &path)) && path) {
                        result = path;
                        CoTaskMemFree(path);
                    }
                    item->Release();
                }
            }
            dialog->Release();
        }
        if (SUCCEEDED(init))
            CoUninitialize();
        return narrow(result);
    }

    char *copy_for_core(const std::string &text) {
        return _strdup(text.c_str());
    }

}
