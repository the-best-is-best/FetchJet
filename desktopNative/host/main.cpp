#define NOMINMAX

#include <windows.h>
#include <dwmapi.h>
#include <shellapi.h>
#include <shobjidl.h>

#include "generated/fj_bridge.h"
#include "core_api.h"
#include "emoji_atlas.h"

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <optional>
#include <string>
#include <thread>
#include <vector>

namespace {

    CoreApi g_core;
    std::optional<slint::ComponentHandle<AppWindow>> g_ui;
    fjgen::Models g_models;
    bool g_running = false;
    HWND g_window = nullptr;
    std::vector<std::string> g_script;
    size_t g_script_next = 0;
    slint::Timer g_script_timer;

    const UINT WM_TRAY = WM_APP + 7;
    const UINT MENU_SHOW = 1;
    const UINT MENU_CLOSE = 2;
    const UINT MENU_WHEN_DONE = 100;
    HWND g_helper = nullptr;
    NOTIFYICONDATAW g_tray{};
    bool g_tray_on = false;
    UINT g_taskbar_created = 0;
    bool g_bridge = true;
    std::string g_bridge_port;
    std::map<fj::EmojiKey, slint::Image> g_emoji;
    std::set<fj::EmojiKey> g_emoji_failed;
    std::set<fj::EmojiKey> g_emoji_missing;
    std::set<fj::EmojiKey> g_emoji_sent;
    std::wstring g_emoji_dir;
    bool g_emoji_child = false;
    bool g_emoji_queued = false;
    int g_emoji_revision = 0;
    slint::Timer g_emoji_timer;
    bool g_can_focus = true;
    bool g_shown = false;
    DWORD g_last_toggle = 0;
    slint::Timer g_topmost_timer;

    std::string take(char *text) {
        std::string value = text ? text : "";
        if (text)
            g_core.release(text);
        return value;
    }

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

    std::string json_string(const std::string &text) {
        std::string out = "\"";
        for (unsigned char c: text) {
            if (c == '"' || c == '\\') {
                out += '\\';
                out += char(c);
            } else if (c < 0x20) {
                char buffer[8];
                std::snprintf(buffer, sizeof buffer, "\\u%04x", c);
                out += buffer;
            } else {
                out += char(c);
            }
        }
        out += '"';
        return out;
    }

    bool load_core() {
        wchar_t path[MAX_PATH * 4];
        DWORD length = GetModuleFileNameW(nullptr, path, DWORD(std::size(path)));
        std::wstring folder(path, length);
        folder = folder.substr(0, folder.find_last_of(L'\\') + 1);
        HMODULE dll = LoadLibraryW((folder + L"fjcore.dll").c_str());
        if (!dll)
            return false;
        g_core.prepare = reinterpret_cast<fj_prepare_fn>(GetProcAddress(dll, "fj_prepare"));
        g_core.start = reinterpret_cast<fj_start_fn>(GetProcAddress(dll, "fj_start"));
        g_core.snapshot = reinterpret_cast<fj_snapshot_fn>(GetProcAddress(dll, "fj_snapshot"));
        g_core.action = reinterpret_cast<fj_action_fn>(GetProcAddress(dll, "fj_action"));
        g_core.call = reinterpret_cast<fj_call_fn>(GetProcAddress(dll, "fj_call"));
        g_core.release = reinterpret_cast<fj_free_fn>(GetProcAddress(dll, "fj_free"));
        g_core.shutdown = reinterpret_cast<fj_shutdown_fn>(GetProcAddress(dll, "fj_shutdown"));
        return g_core.prepare && g_core.start && g_core.snapshot && g_core.action && g_core.call && g_core.release &&
               g_core.shutdown;
    }

    bool system_uses_dark() {
        DWORD value = 1;
        DWORD size = sizeof value;
        LSTATUS status = RegGetValueW(HKEY_CURRENT_USER,
                                      L"Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                                      L"AppsUseLightTheme", RRF_RT_REG_DWORD, nullptr, &value, &size);
        return status == ERROR_SUCCESS && value == 0;
    }

    void send_action(const std::string &json) {
        if (g_running)
            g_core.action(json.c_str());
    }

    void apply_title_bar(bool dark) {
        HWND window = (*g_ui)->window().win32_hwnd();
        if (!window)
            return;
        BOOL value = dark ? TRUE : FALSE;
        DwmSetWindowAttribute(window, 20, &value, sizeof value);
    }

    std::string plain(const slint::SharedString &text) {
        return std::string(text.data(), text.size());
    }

    void copy_text(wchar_t *target, size_t capacity, const std::wstring &text) {
        size_t length = text.size() < capacity - 1 ? text.size() : capacity - 1;
        std::wmemcpy(target, text.data(), length);
        target[length] = L'\0';
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
        if (g_ui)
            copy_text(g_tray.szTip, std::size(g_tray.szTip), widen(plain((*g_ui)->global<Core>().get_shell().trayTooltip)));
        g_tray_on = Shell_NotifyIconW(NIM_ADD, &g_tray) != FALSE;
    }

    void update_tray_tip() {
        if (!g_tray_on)
            return;
        std::wstring tip = widen(plain((*g_ui)->global<Core>().get_shell().trayTooltip));
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

    void show_window(bool focus) {
        auto ui = *g_ui;
        ui->show();
        g_shown = true;
        g_window = ui->window().win32_hwnd();
        if (!g_window)
            return;
        apply_title_bar(ui->global<Core>().get_shell().dark);
        if (IsIconic(g_window))
            ShowWindow(g_window, SW_RESTORE);
        if (!focus || !g_can_focus)
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

    void quit_app() {
        remove_tray();
        slint::quit_event_loop();
    }

    void hide_window() {
        if (!g_tray_on) {
            quit_app();
            return;
        }
        (*g_ui)->hide();
        g_shown = false;
        g_window = nullptr;
    }

    void toggle_window() {
        DWORD now = GetTickCount();
        if (now - g_last_toggle < GetDoubleClickTime())
            return;
        g_last_toggle = now;
        if (g_shown && g_window && !IsIconic(g_window))
            hide_window();
        else
            show_window(true);
    }

    void show_tray_menu() {
        auto ui = *g_ui;
        ShellDto shell = ui->global<Core>().get_shell();
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
        if (ui->global<Dir>().get_rtl())
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

    void create_helper_window() {
        WNDCLASSW type{};
        type.lpfnWndProc = helper_proc;
        type.hInstance = GetModuleHandleW(nullptr);
        type.lpszClassName = L"FetchJetShellHelper";
        RegisterClassW(&type);
        g_helper = CreateWindowExW(WS_EX_TOOLWINDOW, type.lpszClassName, L"FetchJet", WS_POPUP, 0, 0, 0, 0, nullptr, nullptr,
                                   type.hInstance, nullptr);
        g_taskbar_created = RegisterWindowMessageW(L"TaskbarCreated");
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
        if (g_ui)
            (*g_ui)->global<Emoji>().set_revision(++g_emoji_revision);
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
        wchar_t path[MAX_PATH * 4];
        DWORD length = GetModuleFileNameW(nullptr, path, DWORD(std::size(path)));
        std::wstring command = L"\"" + std::wstring(path, length) + L"\" --emoji-cache \"" + g_emoji_dir +
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
                if (!g_running)
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

    void start_emoji() {
        std::wstring home = widen(take(g_core.call("home", "")));
        if (home.empty())
            return;
        CreateDirectoryW((home + L"\\cache").c_str(), nullptr);
        g_emoji_dir = home + L"\\cache\\emoji";
        CreateDirectoryW(g_emoji_dir.c_str(), nullptr);
        auto ui = *g_ui;
        ui->global<Emoji>().on_icon([](slint::SharedString glyph, int size, int) { return emoji_icon(glyph, size); });
        ui->global<Emoji>().set_ready(true);
        load_emoji();
    }

    void handle_effects(const fj::Json &json) {
        for (const EffectDto &effect: fjgen::parse_effects_queue(json)) {
            switch (effect.kind) {
                case fjgen::EffectKind::ShowWindow:
                    show_window(true);
                    break;
                case fjgen::EffectKind::ScrollToRow:
                    (*g_ui)->invoke_scroll_to_top();
                    break;
                case fjgen::EffectKind::Notify:
                    show_notice(plain(effect.title), plain(effect.text));
                    break;
                case fjgen::EffectKind::Exit:
                    quit_app();
                    break;
                default:
                    break;
            }
        }
    }

    void apply_channel(int channel) {
        std::string text = take(g_core.snapshot(channel));
        fj::Json json;
        std::string error;
        if (!fj::parse_json(text, json, error))
            return;
        auto ui = *g_ui;
        if (channel == fjgen::Channel::Tr) {
            fjgen::apply_tr(ui->global<Tr>(), json);
            ui->global<Dir>().set_rtl(fj::flag(json, "rtl"));
            ui->global<Theme>().set_font(fj::text(json, "font"));
            return;
        }
        if (channel == fjgen::Channel::Effects) {
            handle_effects(json);
            return;
        }
        fjgen::apply_channel(ui->global<Core>(), g_models, channel, json);
        if (channel == fjgen::Channel::Shell) {
            bool dark = ui->global<Core>().get_shell().dark;
            ui->global<Theme>().set_dark(dark);
            apply_title_bar(dark);
            update_tray_tip();
        }
    }

    void notify_from_core(unsigned int mask) {
        if (!g_running)
            return;
        for (int channel = 0; channel < fjgen::Channel::Count; channel++)
            if (mask & (1u << channel))
                apply_channel(channel);
    }

    void post_from_core(fj_task_fn fn, void *arg) {
        slint::invoke_from_event_loop([fn, arg] { fn(arg); });
    }

    std::wstring pick_path(bool folder, const std::wstring &title, const std::wstring &initial,
                           const std::wstring &filter_name, const std::wstring &pattern) {
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
        return result;
    }

    char *host_service(const char *name, const char *args_json) {
        std::string service = name ? name : "";
        fj::Json args;
        std::string error;
        fj::parse_json(args_json ? args_json : "{}", args, error);
        auto arg = [&args](const char *key) {
            const fj::Json *found = args.find(key);
            return widen(found ? found->text : std::string());
        };
        std::string result;
        if (service == "pickFolder")
            result = narrow(pick_path(true, arg("title"), arg("initialPath"), L"", L""));
        else if (service == "pickFile")
            result = narrow(pick_path(false, arg("title"), L"", arg("filterName"), arg("pattern")));
        return _strdup(result.c_str());
    }

    void host_free(char *text) {
        std::free(text);
    }

    void run_script_step() {
        if (g_script_next >= g_script.size()) {
            g_script_timer.stop();
            return;
        }
        send_action(g_script[g_script_next++]);
    }

    std::string start_config(int argc, char **argv) {
        std::string home;
        std::string folder;
        std::string &port = g_bridge_port;
        std::string args = "[";
        bool debug = false;
        bool downloads = true;
        for (int i = 1; i < argc; i++) {
            std::string arg = argv[i];
            if (arg == "--home" && i + 1 < argc) {
                home = argv[++i];
                continue;
            }
            if (arg == "--downloads" && i + 1 < argc) {
                folder = argv[++i];
                continue;
            }
            if (arg == "--bridge-port" && i + 1 < argc) {
                port = std::to_string(std::atoi(argv[++i]));
                continue;
            }
            if (arg == "--debug") {
                debug = true;
                continue;
            }
            if (arg == "--no-bridge") {
                g_bridge = false;
                continue;
            }
            if (arg == "--no-focus") {
                g_can_focus = false;
                continue;
            }
            if (arg == "--no-auto-downloads") {
                downloads = false;
                continue;
            }
            if (arg == "--action" && i + 1 < argc) {
                g_script.push_back(argv[++i]);
                continue;
            }
            if (arg == "--script" && i + 1 < argc) {
                std::ifstream file(widen(argv[++i]));
                std::string line;
                while (std::getline(file, line))
                    if (!line.empty() && line[0] == '{')
                        g_script.push_back(line);
                continue;
            }
            if (args.size() > 1)
                args += ',';
            args += json_string(arg);
        }
        args += "]";
        std::string config = "{\"args\":" + args;
        if (!home.empty())
            config += ",\"home\":" + json_string(home);
        if (!folder.empty())
            config += ",\"downloads\":" + json_string(folder);
        if (!port.empty())
            config += ",\"bridgePort\":" + port;
        config += std::string(",\"debug\":") + (debug ? "true" : "false");
        config += std::string(",\"bridge\":") + (g_bridge ? "true" : "false");
        config += std::string(",\"automaticDownloads\":") + (downloads ? "true" : "false");
        return config;
    }

}

int main(int argc, char **argv) {
    for (int i = 1; i + 3 < argc; i++) {
        if (std::string(argv[i]) == "--emoji-cache" && std::string(argv[i + 2]) == "--emoji-list")
            return fj::write_emoji_atlas(widen(argv[i + 1]), widen(argv[i + 3]));
    }
    if (!std::getenv("SLINT_BACKEND"))
        _putenv_s("SLINT_BACKEND", "winit-software");
    if (!load_core()) {
        MessageBoxW(nullptr, L"fjcore.dll could not be loaded.", L"FetchJet", MB_ICONERROR | MB_OK);
        return 2;
    }
    std::string config = start_config(argc, argv);
    bool second = false;
    HANDLE instance = nullptr;
    if (g_bridge) {
        instance = CreateMutexW(nullptr, FALSE, (L"Local\\FetchJet.Native.Instance" + widen(g_bridge_port)).c_str());
        second = instance && GetLastError() == ERROR_ALREADY_EXISTS;
    }
    fj::Json launch;
    std::string error;
    fj::parse_json(take(g_core.prepare((config + (second ? ",\"secondInstance\":true}" : "}")).c_str())), launch, error);
    if (fj::flag(launch, "exit"))
        return 0;
    bool start_hidden = fj::flag(launch, "hidden");

    g_ui = AppWindow::create();
    auto ui = *g_ui;
    fjgen::bind_models(ui->global<Core>(), g_models);
    fjgen::bind_actions(ui->global<Act>(), [](const std::string &json) { send_action(json); });
    ui->window().on_close_requested([] {
        if (!g_tray_on) {
            quit_app();
            return slint::CloseRequestResponse::HideWindow;
        }
        g_shown = false;
        g_window = nullptr;
        return slint::CloseRequestResponse::HideWindow;
    });

    int started = g_core.start((config + "}").c_str(), post_from_core, notify_from_core, host_service, host_free);
    if (started != 0) {
        MessageBoxW(nullptr, L"The download core could not start.", L"FetchJet", MB_ICONERROR | MB_OK);
        return 3;
    }
    g_running = true;
    send_action(std::string("{\"type\":\"SystemTheme\",\"dark\":") + (system_uses_dark() ? "true" : "false") + "}");
    for (int channel = 0; channel < fjgen::Channel::Count; channel++)
        apply_channel(channel);

    create_helper_window();
    add_tray();
    start_emoji();
    if (!g_script.empty())
        g_script_timer.start(slint::TimerMode::Repeated, std::chrono::milliseconds(500), run_script_step);
    if (!start_hidden || !g_tray_on)
        show_window(false);
    slint::run_event_loop(slint::EventLoopMode::RunUntilQuit);

    g_running = false;
    remove_tray();
    g_core.shutdown();
    g_ui.reset();
    if (instance)
        CloseHandle(instance);
    return 0;
}
