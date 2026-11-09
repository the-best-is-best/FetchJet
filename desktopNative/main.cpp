#include "app.h"

#include <cstdio>
#include <cstdlib>
#include <algorithm>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

#include <windows.h>

namespace {

    struct Core {
        void (*setDestination)(const char *) = nullptr;

        void (*setLimit)(long long) = nullptr;

        int (*add)(const char *) = nullptr;

        void (*toggle)(int) = nullptr;

        char *(*snapshot)() = nullptr;

        void (*release)(char *) = nullptr;

        bool load() {
            HMODULE dll = LoadLibraryA("fjcore.dll");
            if (!dll)
                return false;
            setDestination = reinterpret_cast<void (*)(const char *)>(GetProcAddress(dll,
                                                                                     "fj_set_destination"));
            setLimit = reinterpret_cast<void (*)(long long)>(GetProcAddress(dll, "fj_set_limit"));
            add = reinterpret_cast<int (*)(const char *)>(GetProcAddress(dll, "fj_add"));
            toggle = reinterpret_cast<void (*)(int)>(GetProcAddress(dll, "fj_toggle"));
            snapshot = reinterpret_cast<char *(*)()>(GetProcAddress(dll, "fj_snapshot"));
            release = reinterpret_cast<void (*)(char *)>(GetProcAddress(dll, "fj_free"));
            return setDestination && setLimit && add && toggle && snapshot && release;
        }
    };

    struct Row {
        int id;
        std::string name;
        int state;
        long long total;
        long long done;
        long long speed;
        std::string error;
    };

    const char *kFilters[] = {"All downloads", "Downloading", "Waiting", "Paused",
                              "Completed", "Failed", "Video & audio", "Files", "Torrents"};

    std::string bytesText(long long b) {
        char buf[32];
        if (b >= 1LL << 30)
            std::snprintf(buf, sizeof buf, "%.2f GB", b / double(1LL << 30));
        else if (b >= 1LL << 20)
            std::snprintf(buf, sizeof buf, "%.2f MB", b / double(1LL << 20));
        else if (b >= 1024)
            std::snprintf(buf, sizeof buf, "%.1f KB", b / 1024.0);
        else
            std::snprintf(buf, sizeof buf, "%lld B", b);
        return buf;
    }

    std::string etaText(long long s) {
        char buf[32];
        if (s < 60)
            std::snprintf(buf, sizeof buf, "%llds", s);
        else if (s < 3600)
            std::snprintf(buf, sizeof buf, "%lldm %02llds", s / 60, s % 60);
        else
            std::snprintf(buf, sizeof buf, "%lldh %02lldm", s / 3600, (s % 3600) / 60);
        return buf;
    }

    std::string kindTag(const std::string &name) {
        auto dot = name.find_last_of('.');
        std::string ext = dot == std::string::npos ? "" : name.substr(dot + 1);
        for (auto &c: ext)
            c = char(tolower((unsigned char) c));
        for (const char *a: {"mp3", "m4a", "aac", "flac", "wav", "ogg", "opus"})
            if (ext == a)
                return "AUD";
        for (const char *v: {"mp4", "mkv", "webm", "mov", "avi"})
            if (ext == v)
                return "VID";
        bool alnum = !ext.empty();
        for (char c: ext)
            alnum = alnum && isalnum((unsigned char) c);
        if (alnum && ext.size() >= 2 && ext.size() <= 4) {
            for (auto &c: ext)
                c = char(toupper((unsigned char) c));
            return ext;
        }
        return "FILE";
    }

    int groupOf(const Row &r) {
        switch (r.state) {
            case 1:
                return 0;
            case 4:
                return 1;
            case 0:
                return 3;
            default:
                return 2;
        }
    }

    bool matches(const Row &r, const std::string &tag, int filter) {
        switch (filter) {
            case 1:
                return r.state == 1;
            case 2:
                return r.state == 4;
            case 3:
                return r.state == 2;
            case 4:
                return r.state == 0;
            case 5:
                return r.state == 3;
            case 6:
                return tag == "VID" || tag == "AUD";
            case 7:
                return tag != "VID" && tag != "AUD" && tag != "MAG";
            case 8:
                return false;
            default:
                return true;
        }
    }

    std::vector <Row> parse(const char *text) {
        std::vector <Row> rows;
        std::istringstream in(text);
        std::string line;
        while (std::getline(in, line)) {
            std::vector <std::string> f;
            size_t start = 0, tab;
            while ((tab = line.find('\t', start)) != std::string::npos) {
                f.push_back(line.substr(start, tab - start));
                start = tab + 1;
            }
            f.push_back(line.substr(start));
            if (f.size() < 7)
                continue;
            rows.push_back(Row{std::atoi(f[0].c_str()), f[1], std::atoi(f[2].c_str()),
                               std::atoll(f[3].c_str()),
                               std::atoll(f[4].c_str()), std::atoll(f[5].c_str()), f[6]});
        }
        return rows;
    }

}

int main() {
    if (std::getenv("SLINT_BACKEND") == nullptr)
        _putenv_s("SLINT_BACKEND", "winit-software");

    auto core = std::make_shared<Core>();
    if (!core->load()) {
        MessageBoxA(nullptr, "fjcore.dll is missing", "FetchJet", MB_ICONERROR);
        return 1;
    }
    const char *dest = std::getenv("FETCHJET_DOWNLOADS");
    core->setDestination(dest ? dest : "E:/tools/fjhome/Downloads");
    const char *limit = std::getenv("FETCHJET_LIMIT_KBS");
    core->setLimit((limit ? std::atoll(limit) : 20) * 1024);

    auto ui = AppWindow::create();
    auto model = std::make_shared < slint::VectorModel < TaskItem >> ();
    auto navModel = std::make_shared < slint::VectorModel < NavEntry >> ();
    for (const char *f: kFilters)
        navModel->push_back(NavEntry{slint::SharedString(f), 0});
    ui->set_nav(navModel);
    ui->set_tasks(model);

    auto tray = Tray::create();
    const char *limitEnv = std::getenv("FETCHJET_LIMIT_KBS");
    long long limitKbs = limitEnv ? std::atoll(limitEnv) : 20;
    auto rowsCache = std::make_shared < std::vector < Row >> ();

    auto refresh = [ui, core, model, navModel, tray, limitKbs, rowsCache]() {
        char *raw = core->snapshot();
        *rowsCache = parse(raw);
        core->release(raw);
        const auto &rows = *rowsCache;
        int filter = ui->get_filter();
        auto search = ui->get_search();
        std::string query(search.data(), search.size());
        for (auto &c: query)
            c = char(tolower((unsigned char) c));
        int counts[9] = {};
        long long down = 0;
        int downloading = 0, waiting = 0;
        bool pausable = false, resumable = false;
        std::vector <Row> shown;
        std::string tip;
        for (const auto &r: rows) {
            auto tag = kindTag(r.name);
            for (int f = 0; f < 9; f++)
                if (matches(r, tag, f))
                    counts[f]++;
            if (r.state == 1) {
                down += r.speed;
                downloading++;
                pausable = true;
                if (tip.size() < 200) {
                    int pct = r.total > 0 ? int(r.done * 100 / r.total) : 0;
                    tip += "\n" + (r.name.size() > 25 ? r.name.substr(0, 22) + "..." : r.name) +
                           ": " +
                           std::to_string(pct) + "%";
                }
            }
            if (r.state == 4) {
                waiting++;
                pausable = true;
            }
            if (r.state == 2 || r.state == 3)
                resumable = true;
            if (!matches(r, tag, filter))
                continue;
            if (!query.empty()) {
                std::string n = r.name;
                for (auto &c: n)
                    c = char(tolower((unsigned char) c));
                if (n.find(query) == std::string::npos)
                    continue;
            }
            shown.push_back(r);
        }
        bool grouped = filter == 0 || filter >= 6;
        if (grouped)
            std::stable_sort(shown.begin(), shown.end(),
                             [](const Row &a, const Row &b) { return groupOf(a) < groupOf(b); });
        int groupCount[4] = {};
        for (const auto &r: shown)
            groupCount[groupOf(r)]++;
        const char *groupTitle[] = {"Downloading", "Waiting", "Paused & failed", "Completed"};
        const char *groupIcon[] = {"⬇", "⏳", "⏸", "✓"};

        std::vector <TaskItem> items;
        int lastGroup = -1;
        for (const auto &r: shown) {
            float progress = r.total > 0 ? float(double(r.done) / double(r.total)) : 0.0f;
            int pct = int(progress * 100);
            std::string status;
            std::string left = "—";
            switch (r.state) {
                case 0:
                    status = "Completed" +
                             (r.total > 0 ? " · " + bytesText(r.total) : std::string());
                    break;
                case 1:
                    status = std::to_string(pct) + "%";
                    if (r.speed > 0)
                        status += " · " + bytesText(r.speed) + "/s";
                    if (r.speed > 0 && r.total > 0) {
                        left = etaText((r.total - r.done) / r.speed);
                        status += " · " + left + " left";
                    }
                    break;
                case 2:
                    status = "Paused · " + std::to_string(pct) + "%";
                    break;
                case 3:
                    status = "Failed: " + (r.error.empty() ? std::string("error") : r.error);
                    break;
                default:
                    status = "Waiting in queue";
                    break;
            }
            int g = groupOf(r);
            bool head = grouped && g != lastGroup;
            lastGroup = g;
            std::string size =
                    r.total > 0 ? bytesText(r.total) : (r.done > 0 ? bytesText(r.done) : "—");
            std::string speed = r.state == 1 && r.speed > 0 ? bytesText(r.speed) + "/s" : "—";
            items.push_back(TaskItem{r.id,
                                     slint::SharedString(r.name),
                                     slint::SharedString(kindTag(r.name)),
                                     slint::SharedString(status),
                                     r.state,
                                     slint::SharedString(size),
                                     progress,
                                     slint::SharedString(speed),
                                     slint::SharedString(left),
                                     slint::SharedString(head ? groupTitle[g] : ""),
                                     slint::SharedString(head ? groupIcon[g] : ""),
                                     head ? groupCount[g] : 0,
                                     g});
        }
        for (int f = 0; f < 9; f++)
            navModel->set_row_data(f, NavEntry{slint::SharedString(kFilters[f]), counts[f]});
        if (model->row_count() == items.size()) {
            for (size_t i = 0; i < items.size(); i++)
                model->set_row_data(i, items[i]);
        } else {
            model->clear();
            for (auto &it: items)
                model->push_back(it);
        }
        std::string lim = limitKbs > 0 ? std::to_string(limitKbs) + " KB/s" : "";
        ui->set_down_speed(slint::SharedString(bytesText(down) + "/s"));
        ui->set_up_speed(slint::SharedString("0 B/s"));
        ui->set_limited(limitKbs > 0);
        ui->set_limit_text(slint::SharedString(limitKbs > 0 ? lim : std::string("High")));
        ui->set_summary(slint::SharedString(std::to_string(downloading) + " downloading · " +
                                            std::to_string(waiting) + " waiting"));
        ui->set_queue_text(slint::SharedString("Queue: 3 at a time"));
        ui->set_limit_status(
                slint::SharedString("Speed limit: " + (limitKbs > 0 ? lim : std::string("off"))));
        ui->set_version(slint::SharedString("v1.4.0-native"));
        ui->set_any_pausable(pausable);
        ui->set_any_resumable(resumable);
        tray->set_tip(
                slint::SharedString(tip.empty() ? std::string("FetchJet - Running in Background")
                                                : "FetchJet Downloads:" + tip));
    };

    ui->on_filter_changed([refresh](int) { refresh(); });
    ui->on_toggle_task([core, refresh](int id) {
        core->toggle(id);
        refresh();
    });
    ui->on_pause_all([core, rowsCache, refresh]() {
        for (const auto &r: *rowsCache)
            if (r.state == 1)
                core->toggle(r.id);
        refresh();
    });
    ui->on_resume_all([core, rowsCache, refresh]() {
        for (const auto &r: *rowsCache)
            if (r.state == 2 || r.state == 3)
                core->toggle(r.id);
        refresh();
    });
    ui->on_add_download([core, refresh](slint::SharedString url, int) {
        std::string u(url.data(), url.size());
        core->add(u.c_str());
        refresh();
    });

    auto bringToFront = [ui]() {
        ui->window().set_minimized(false);
        ui->window().show();
        if (HWND h = FindWindowW(nullptr, L"FetchJet - High-Velocity Multi-Engine Downloader")) {
            ShowWindow(h, SW_RESTORE);
            SetForegroundWindow(h);
        }
    };
    tray->on_show_app([ui, bringToFront]() {
        if (ui->window().is_visible() && !ui->window().is_minimized())
            ui->window().hide();
        else
            bringToFront();
    });
    tray->on_quit_app([]() { slint::quit_event_loop(); });
    ui->window().on_close_requested([]() { return slint::CloseRequestResponse::HideWindow; });

    if (const char *first = std::getenv("FETCHJET_ADD"))
        core->add(first);

    slint::Timer ticker(std::chrono::milliseconds(500), refresh);
    refresh();
    ui->show();
    slint::run_event_loop(slint::EventLoopMode::RunUntilQuit);
    return 0;
}
