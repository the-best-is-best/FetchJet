#include "p01.h"
#include "p02.h"
#include "p03.h"
#include "p04.h"
#include "p05.h"
#include "p06.h"
#include "p07.h"
#include "p08.h"
#include "p09.h"
#include "p10.h"
#include "p11.h"

#include <atomic>
#include <chrono>
#include <filesystem>
#include <memory>
#include <string>
#include <thread>
#include <vector>

#include <windows.h>

static slint::Image solid_image(uint32_t w, uint32_t h, slint::Rgba8Pixel c)
{
    slint::SharedPixelBuffer<slint::Rgba8Pixel> buf(w, h);
    for (auto *p = buf.begin(); p != buf.end(); ++p)
        *p = c;
    return slint::Image(buf);
}

static void check_text_and_keys()
{
    auto ui = p01::Demo::create();
    ui->set_url("https://example.com");
    slint::SharedString url = ui->get_url();
    ui->on_paste_url([weak = slint::ComponentWeakHandle(ui)]() -> bool {
        if (auto strong = weak.lock())
            (*strong)->set_url("pasted");
        return true;
    });
    ui->on_submitted([](slint::SharedString text) { (void)text; });
    ui->global<p01::FieldTheme>().set_primary(slint::Color::from_rgb_uint8(0, 229, 255));

    auto kb = p05::Demo::create();
    auto &shortcuts = kb->global<p05::Shortcuts>();
    shortcuts.on_select_all([] {});
    shortcuts.on_key_fallback([](slint::SharedString text, bool control, bool shift, bool alt, bool meta,
                                 bool repeat) -> bool {
        (void)control;
        (void)shift;
        (void)alt;
        (void)meta;
        (void)repeat;
        return text == slint::SharedString(u8"\uF700");
    });
    if (auto keys = slint::Keys::from_parts({ "Control", "Shift", "N" })) {
        shortcuts.set_custom_add(*keys);
        slint::SharedString label = keys->to_string();
        (void)label;
    }
    slint::SharedString paste_label = shortcuts.get_paste_label();
    kb->window().dispatch_key_press_event(slint::SharedString("a"));
    kb->window().dispatch_key_release_event(slint::SharedString("a"));
    bool text_focused = kb->get_text_focused();
    (void)text_focused;
    (void)paste_label;
    (void)url;
}

static void check_popups_and_dialogs()
{
    auto ui = p02::Demo::create();
    auto items = std::make_shared<slint::VectorModel<p02::MenuRow>>();
    items->push_back(p02::MenuRow { 1, "Newest first", true, true, false });
    items->push_back(p02::MenuRow { 0, "", false, false, true });
    ui->set_sort_items(items);
    ui->on_sort_picked([items](int id) {
        for (size_t i = 0; i < items->row_count(); ++i) {
            auto row = *items->row_data(i);
            row.checked = row.id == id;
            items->set_row_data(i, row);
        }
    });
    ui->on_menu_action([](slint::SharedString action, int row) {
        (void)action;
        (void)row;
    });
    ui->set_context_row(4);
    ui->invoke_open_row_menu(120.f, 80.f);
    ui->invoke_close_row_menu();
    ui->set_show_speed(false);

    auto main_window = p03::MainWindow::create();
    main_window->on_confirm_accepted([](bool delete_files) { (void)delete_files; });
    main_window->set_confirm_open(true);

    auto pairing = p03::PairingDialog::create();
    pairing->set_origin("chrome-extension://abc");
    pairing->on_allow([] {});
    pairing->on_block([] {});
    pairing->window().on_close_requested([] { return slint::CloseRequestResponse::HideWindow; });
    pairing->window().set_position(slint::PhysicalPosition({ 200, 200 }));
    pairing->show();
    pairing->hide();
}

static void check_list()
{
    auto ui = p04::Demo::create();
    auto rows = std::make_shared<slint::VectorModel<p04::TaskRow>>();
    for (int i = 0; i < 1500; ++i) {
        p04::TaskRow r;
        r.id = i;
        r.name = slint::SharedString("file ") + slint::SharedString::from_number(i);
        r.status = "Downloading";
        r.progress = 0.5f;
        r.header = i % 100 == 0 ? slint::SharedString("Group") : slint::SharedString();
        rows->push_back(r);
    }
    ui->set_rows(rows);
    auto &api = ui->global<p04::ListApi>();
    api.set_row_height(64.f);
    api.on_row_clicked([rows](int index, int id, bool control, bool shift) {
        (void)id;
        (void)shift;
        if (auto row = rows->row_data(size_t(index))) {
            row->selected = control ? !row->selected : true;
            rows->set_row_data(size_t(index), *row);
        }
    });
    api.on_row_double_clicked([](int, int) {});
    api.on_row_context([](int, int, float, float) {});
    api.on_key([](slint::SharedString, bool, bool) { return false; });
    api.on_row_y([](int index) -> float { return float(index) * 64.f + float(index / 100 + 1) * 36.f; });
    ui->invoke_scroll_to_row(700);
    ui->invoke_scroll_to_top();
    float y = ui->get_list_content_y();
    (void)y;

    auto filtered = std::make_shared<slint::FilterModel<p04::TaskRow>>(
            rows, [](const p04::TaskRow &r) { return r.progress < 1.f; });
    auto sorted = std::make_shared<slint::SortModel<p04::TaskRow>>(
            filtered, [](const p04::TaskRow &a, const p04::TaskRow &b) { return a.id > b.id; });
    auto mapped = std::make_shared<slint::MapModel<p04::TaskRow, p04::TaskRow>>(sorted, [](const p04::TaskRow &r) {
        auto copy = r;
        copy.status = "mapped";
        return copy;
    });
    auto reversed = std::make_shared<slint::ReverseModel<p04::TaskRow>>(mapped);
    ui->set_rows(reversed);
    filtered->reset();
    int source_row = filtered->unfiltered_row(0);
    (void)source_row;
}

static void check_dnd()
{
    auto ui = p06::Demo::create();
    auto &api = ui->global<p06::DropApi>();
    api.on_accepts([](slint::DataTransfer data) { return data.has_file_paths() || data.has_plain_text(); });
    api.on_dropped([](slint::DataTransfer data, float x, float y) {
        (void)x;
        (void)y;
        if (auto paths = data.file_paths()) {
            for (const std::filesystem::path &p : *paths) {
                std::wstring native = p.native();
                (void)native;
            }
        }
        if (auto text = data.plain_text()) {
            std::string_view view(*text);
            (void)view;
        }
        if (auto image = data.image()) {
            (void)image->size();
        }
    });
    api.on_row_payload([](int id) {
        slint::DataTransfer t(slint::SharedString("fetchjet-task"));
        t.set_file_paths({ std::filesystem::path(L"C:/tmp/a.bin") });
        t.set_user_data(std::any(id));
        return t;
    });
    api.on_row_drag_finished([](int, bool) {});
    api.on_copy_text([](slint::SharedString text) {
        std::string_view utf8(text);
        int wide_len = MultiByteToWideChar(CP_UTF8, 0, utf8.data(), int(utf8.size()), nullptr, 0);
        if (!OpenClipboard(nullptr))
            return;
        EmptyClipboard();
        if (HGLOBAL mem = GlobalAlloc(GMEM_MOVEABLE, size_t(wide_len + 1) * sizeof(wchar_t))) {
            auto *dst = static_cast<wchar_t *>(GlobalLock(mem));
            MultiByteToWideChar(CP_UTF8, 0, utf8.data(), int(utf8.size()), dst, wide_len);
            dst[wide_len] = 0;
            GlobalUnlock(mem);
            SetClipboardData(CF_UNICODETEXT, mem);
        }
        CloseClipboard();
    });
    api.on_read_clipboard([]() -> slint::SharedString {
        slint::SharedString result;
        if (!OpenClipboard(nullptr))
            return result;
        if (HANDLE h = GetClipboardData(CF_UNICODETEXT)) {
            if (auto *src = static_cast<const wchar_t *>(GlobalLock(h))) {
                int len = WideCharToMultiByte(CP_UTF8, 0, src, -1, nullptr, 0, nullptr, nullptr);
                std::string utf8(size_t(len > 0 ? len - 1 : 0), '\0');
                WideCharToMultiByte(CP_UTF8, 0, src, -1, utf8.data(), len, nullptr, nullptr);
                result = slint::SharedString(utf8);
                GlobalUnlock(h);
            }
        }
        CloseClipboard();
        return result;
    });
    bool over = ui->get_drag_over();
    slint::language::DragAction action = ui->get_action();
    (void)over;
    (void)action;
}

static void check_windows()
{
    auto ui = p07::MainWindow::create();
    auto tray = p07::AppTray::create();
    auto frameless = p07::FramelessWindow::create();

    ui->set_window_title("FetchJet - 2 downloads");
    ui->set_window_icon(slint::Image::load_from_path("E:/tbib/FetchJet/desktopNative/ui/logo.png"));
    ui->set_on_top(true);

    slint::Window &w = ui->window();
    w.set_size(slint::LogicalSize({ 1100.f, 720.f }));
    w.set_size(slint::PhysicalSize({ 1650u, 1080u }));
    w.set_position(slint::PhysicalPosition({ 120, 80 }));
    w.set_position(slint::LogicalPosition({ 80.f, 60.f }));
    slint::PhysicalSize size = w.size();
    slint::PhysicalPosition pos = w.position();
    float scale = w.scale_factor();
    bool maximized = w.is_maximized();
    bool minimized = w.is_minimized();
    bool fullscreen = w.is_fullscreen();
    bool visible = w.is_visible();
    w.set_maximized(true);
    w.set_minimized(false);
    w.set_fullscreen(false);
    ui->set_is_maximized(false);
    bool maximized_prop = ui->get_is_maximized();
    float logical_width = ui->get_current_width();
    HWND hwnd = w.win32_hwnd();
    if (hwnd) {
        WINDOWPLACEMENT placement { sizeof(WINDOWPLACEMENT) };
        GetWindowPlacement(hwnd, &placement);
        SetWindowPos(hwnd, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);
    }
    (void)size;
    (void)pos;
    (void)scale;
    (void)maximized;
    (void)minimized;
    (void)fullscreen;
    (void)visible;
    (void)maximized_prop;
    (void)logical_width;

    std::atomic<bool> quitting { false };
    w.on_close_requested([weak = slint::ComponentWeakHandle(ui), &quitting]() {
        if (quitting)
            return slint::CloseRequestResponse::HideWindow;
        if (auto strong = weak.lock())
            (*strong)->window().hide();
        return slint::CloseRequestResponse::KeepWindowShown;
    });

    tray->set_tray_icon(solid_image(32, 32, slint::Rgba8Pixel { 0, 229, 255, 255 }));
    tray->set_tip("FetchJet - 2 downloading");
    tray->set_paused(false);
    auto when_done = std::make_shared<slint::VectorModel<p07::TrayEntry>>();
    when_done->push_back(p07::TrayEntry { 0, "Do nothing", true, true });
    when_done->push_back(p07::TrayEntry { 1, "Sleep", true, false });
    tray->set_when_done(when_done);
    tray->on_show_app([weak = slint::ComponentWeakHandle(ui)] {
        if (auto strong = weak.lock()) {
            slint::Window &win = (*strong)->window();
            win.show();
            win.set_minimized(false);
            if (HWND h = win.win32_hwnd())
                SetForegroundWindow(h);
        }
    });
    tray->on_quit_app([&quitting] {
        quitting = true;
        slint::quit_event_loop();
    });
    tray->on_when_done_picked([when_done](int id) {
        for (size_t i = 0; i < when_done->row_count(); ++i) {
            auto e = *when_done->row_data(i);
            e.checked = e.id == id;
            when_done->set_row_data(i, e);
        }
    });

    frameless->set_caption("Details");
    frameless->on_close_clicked([] {});

    if (false) {
        ui->show();
        tray->show();
        frameless->show();
        slint::run_event_loop(slint::EventLoopMode::RunUntilQuit);
        tray->hide();
        ui->hide();
    }
}

static void check_theme_and_translations()
{
    auto ui = p08::Demo::create();
    auto &theme = ui->global<p08::Theme>();
    theme.set_mode(p08::ThemeMode::Dark);
    bool dark = theme.get_dark();
    slint::Color bg = theme.get_background();
    slint::Brush gradient = theme.get_accent_gradient();
    slint::language::ColorScheme scheme = ui->get_scheme();
    slint::SharedString style = ui->get_style();
    theme.invoke_apply_to_widgets();
    (void)dark;
    (void)bg;
    (void)gradient;
    (void)scheme;
    (void)style;

    auto tr = p10::Demo::create();
    auto &strings = tr->global<p10::Strings>();
    strings.set_add_download(slint::SharedString(u8"إضافة تنزيل"));
    strings.set_rtl(true);
    strings.on_step([](slint::SharedString text) -> slint::SharedString { return text; });
    strings.on_format1([](slint::SharedString pattern, slint::SharedString a) -> slint::SharedString {
        std::string s(pattern.data(), pattern.size());
        auto at = s.find("{0}");
        if (at != std::string::npos)
            s.replace(at, 3, std::string(a.data(), a.size()));
        return slint::SharedString(s);
    });
    bool selected = slint::select_bundled_translation("ar");
    slint::update_all_translations();
    (void)selected;
}

static void check_models_and_threads()
{
    auto ui = p09::Demo::create();
    auto &core = ui->global<p09::Core>();

    auto tasks = std::make_shared<slint::VectorModel<p09::TaskVm>>();
    p09::TaskVm task;
    task.id = 1;
    task.name = "video.mp4";
    task.state = p09::TaskState::Downloading;
    task.progress = 0.25f;
    task.speed = "6.4 MB/s";
    task.thumbnail = solid_image(160, 90, slint::Rgba8Pixel { 30, 41, 59, 255 });
    task.pieces = std::make_shared<slint::VectorModel<p09::Piece>>(
            std::vector<p09::Piece> { { 0.f, 0.25f, true }, { 0.25f, 0.4f, false } });
    task.files = std::make_shared<slint::VectorModel<p09::TorrentFile>>();
    task.qualities = std::make_shared<slint::VectorModel<p09::Quality>>();
    task.tags = std::make_shared<slint::VectorModel<slint::SharedString>>(
            std::vector<slint::SharedString> { "mp4", "1080p" });
    tasks->push_back(task);
    core.set_tasks(tasks);
    core.set_filters(std::make_shared<slint::VectorModel<slint::SharedString>>(
            std::vector<slint::SharedString> { "All", "Downloading" }));
    core.set_logo(slint::Image::load_from_path("E:/tbib/FetchJet/desktopNative/ui/logo.png"));

    core.on_toggle([tasks](int id) {
        for (size_t i = 0; i < tasks->row_count(); ++i) {
            auto t = *tasks->row_data(i);
            if (t.id == id) {
                t.state = t.state == p09::TaskState::Paused ? p09::TaskState::Downloading : p09::TaskState::Paused;
                tasks->set_row_data(i, t);
            }
        }
    });
    core.on_remove([](std::shared_ptr<slint::Model<int>> ids, bool delete_files) {
        (void)delete_files;
        for (size_t i = 0; i < ids->row_count(); ++i) {
            int id = *ids->row_data(i);
            (void)id;
        }
    });
    core.on_pick_folder([](slint::SharedString current) -> slint::SharedString { return current; });
    core.on_set_file_selected([](int, int, bool) {});
    core.on_format_bytes([](float bytes) -> slint::SharedString {
        return slint::SharedString::from_number(double(bytes) / 1048576.0) + " MB";
    });
    core.on_tr([](slint::SharedString key) -> slint::SharedString { return key; });
    core.on_count_in([](int) { return 3; });
    slint::SharedString pct = core.invoke_percent(0.5f);
    int selected = core.get_selected_filter();
    (void)pct;
    (void)selected;
    ui->on_ready([] {});

    slint::ComponentWeakHandle<p09::Demo> weak(ui);
    std::thread worker([weak, tasks] {
        slint::invoke_from_event_loop([weak, tasks] {
            if (auto strong = weak.lock()) {
                if (auto t = tasks->row_data(0)) {
                    t->progress = 0.5f;
                    tasks->set_row_data(0, *t);
                }
            }
        });
        int count = slint::blocking_invoke_from_event_loop([tasks] { return int(tasks->row_count()); });
        (void)count;
        slint::blocking_invoke_from_event_loop([] {});
    });
    worker.detach();

    slint::Timer ticker(std::chrono::milliseconds(500), [tasks] { (void)tasks->row_count(); });
    slint::Timer once;
    once.start(slint::TimerMode::SingleShot, std::chrono::milliseconds(50), [] {});
    slint::Timer::single_shot(std::chrono::milliseconds(10), [] { slint::quit_event_loop(); });
    ticker.stop();
    bool running = ticker.running();
    (void)running;

    auto styled = slint::StyledText::from_markdown("**bold**");
    auto plain = slint::StyledText::from_plain_text("plain");
    (void)styled;
    (void)plain;

    auto png = slint::Image::load_from_data(std::span<const uint8_t>(), "png");
    auto rgba = png.to_rgba8();
    (void)rgba;
}

static void check_layout()
{
    auto ui = p11::Demo::create();
    ui->global<p11::Ui>().set_rtl(true);
    ui->global<p11::Ui>().set_compact(true);
    ui->set_progress(0.8f);
    auto snapshot = ui->window().take_snapshot();
    (void)snapshot;
}

int main()
{
    check_text_and_keys();
    check_popups_and_dialogs();
    check_list();
    check_dnd();
    check_windows();
    check_theme_and_translations();
    check_models_and_threads();
    check_layout();
    return 0;
}
