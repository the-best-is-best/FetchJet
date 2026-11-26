#ifdef _WIN32
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#endif
#include "player_mpv.h"

#include "app_host.h"
#include "json.h"
#include "third_party/mpv/client.h"
#include "third_party/mpv/render.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <deque>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#include <fcntl.h>
#include <signal.h>
#include <spawn.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
extern char **environ;
#endif

namespace fjhost::player {

    namespace {

        struct Item {
            std::string id;
            std::string title;
            std::string path;
            bool video = false;
            bool hidden = false;
        };

        struct Api {
            mpv_handle *(*create)() = nullptr;
            int (*initialize)(mpv_handle *) = nullptr;
            int (*set_option_string)(mpv_handle *, const char *, const char *) = nullptr;
            int (*set_property_string)(mpv_handle *, const char *, const char *) = nullptr;
            int (*command)(mpv_handle *, const char **) = nullptr;
            int (*command_async)(mpv_handle *, uint64_t, const char **) = nullptr;
            int (*observe_property)(mpv_handle *, uint64_t, const char *, mpv_format) = nullptr;
            int (*get_property)(mpv_handle *, const char *, mpv_format, void *) = nullptr;
            mpv_event *(*wait_event)(mpv_handle *, double) = nullptr;
            void (*set_wakeup_callback)(mpv_handle *, void (*)(void *), void *) = nullptr;
            void (*terminate_destroy)(mpv_handle *) = nullptr;
            int (*render_create)(mpv_render_context **, mpv_handle *, mpv_render_param *) = nullptr;
            uint64_t (*render_update)(mpv_render_context *) = nullptr;
            int (*render_render)(mpv_render_context *, mpv_render_param *) = nullptr;
            void (*render_set_callback)(mpv_render_context *, void (*)(void *), void *) = nullptr;
            void (*render_free)(mpv_render_context *) = nullptr;
        };

        enum Observed : uint64_t {
            ObsPosition = 1, ObsDuration, ObsPause, ObsEof, ObsTracks
        };

        enum Reply : uint64_t {
            ReplyLoad = 100, ReplySubOpen
        };

        struct SubFile {
            std::string path;
            std::string label;
        };

        struct SubTrack {
            long long id = 0;
            std::string title;
            std::string lang;
            std::string file;
            bool external = false;
            bool selected = false;
        };

        const int kSubOff = 0;
        const int kSubOpen = 1;
        const int kSubFirst = 1000;

        const double kRewindSpeed = 2.0;
        const float kSpeeds[] = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

        Api g_api;
        void *g_lib = nullptr;
        mpv_handle *g_mpv = nullptr;
        mpv_render_context *g_render = nullptr;

        std::vector<Item> g_items;
        int g_index = 0;
        std::string g_key;
        bool g_open = false;
        bool g_wired = false;
        float g_speed = 1.0f;
        int g_hold = 0;
        bool g_resume_after_hold = false;
        double g_rewind_target = 0;
        std::chrono::steady_clock::time_point g_rewind_last;
        bool g_seek_pending = false;
        double g_position = 0;
        double g_duration = 0;
        bool g_paused = true;
        bool g_finished = false;
        bool g_force_render = false;
        bool g_failed = false;
        int g_width = 960;
        int g_height = 540;
        bool g_controls_on = true;
        long g_label_second = -1;
        std::atomic<unsigned> g_generation{0};
        std::atomic<bool> g_event_posted{false};
        std::atomic<bool> g_frame_posted{false};
        slint::Timer g_rewind_timer;
        slint::SharedPixelBuffer<slint::Rgb8Pixel> g_buffers[2];
        int g_buffer_next = 0;
        std::shared_ptr<slint::VectorModel<PlayerRow>> g_model;
        std::string g_preparing;
        bool g_preparing_autoplay = false;
        std::vector<std::string> g_release_after_end;
        bool g_had_hidden = false;
        int g_loaded = 0;
        bool g_prepare_deferred = false;
        std::vector<SubFile> g_sub_files;
        std::vector<SubTrack> g_sub_tracks;
        std::string g_sub_choice;
        bool g_sub_apply = false;
        int g_load_replies = 0;
        long long g_sub_want_entry = -1;
        long long g_sub_entry = -1;
        long long g_sub_loaded_entry = -1;
        std::string g_sub_opened;
        std::string g_sub_enforce;
        int g_sub_expect = 0;
        int g_sub_tick = 0;
        slint::Timer g_notice_timer;
        double g_volume = 100;
        bool g_muted = false;
        bool g_autoplay = true;
        bool g_fullscreen = false;

        struct Trim {
            bool on = false;
            bool ready = false;
            bool range = false;
            bool fps_guess = true;
            std::string task;
            long start_ms = 0;
            long end_ms = 0;
            double fps = 30;
            long frames = 1;
            long s = 0;
            long e = 1;
            int active = 0;
            int strip_w = 0;
            int strip_h = 0;
            bool thumbs_started = false;
            bool audio = false;
            double view_start = 0;
            double view_span = 0;
            int drag_handle = -1;
            double drag_pos = 0;
            bool follow = false;
        };

        struct ThumbTile {
            int level = 0;
            long index = 0;
            double at = 0;
        };

        struct ThumbJob {
            std::atomic<bool> cancel{false};
            std::mutex lock;
            std::deque<ThumbTile> pending;
            int running = 0;
            std::string ffmpeg;
            std::string input;
            std::string folder;
            int width = 0;
            int height = 0;
            unsigned tag = 0;
        };

        Trim g_trim;
        std::shared_ptr<slint::VectorModel<TrimTile>> g_thumbs;
        std::vector<std::pair<int, long>> g_thumb_keys;
        std::map<std::pair<int, long>, slint::Image> g_tile_cache;
        int g_tile_level = 0;
        long g_tile_from = 1;
        long g_tile_to = 0;
        std::shared_ptr<ThumbJob> g_thumb_job;
        std::atomic<unsigned> g_thumb_tag{0};
        std::atomic<int> g_workers{0};
        std::atomic<bool> g_closing{false};

        struct Working {
            Working() { g_workers++; }
            ~Working() { g_workers--; }
        };
        std::atomic<int> g_thumb_made{0};
        int g_thumbs_loaded = 0;
        std::vector<float> g_wave_all;
        std::shared_ptr<slint::VectorModel<float>> g_wave;
        long g_wave_from = 1;
        long g_wave_to = 0;
        int g_wave_group = 0;
        slint::Timer g_edge_timer;

        void drain_events();

        void trim_init();

        void trim_frames();

        void publish_trim();

        void publish_playhead();

        void publish_view();

        void trim_check_range();

        void trim_stop_range(bool show_end);

        void render_frame();

        std::string speed_label(float speed) {
            char text[16];
            if (std::fabs(speed - std::round(speed)) < 0.001f)
                std::snprintf(text, sizeof text, "%dx", int(std::lround(speed)));
            else
                std::snprintf(text, sizeof text, "%gx", double(speed));
            return text;
        }

        std::string time_label(double seconds) {
            long total = seconds > 0 ? long(seconds) : 0;
            long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
            char text[32];
            if (h > 0)
                std::snprintf(text, sizeof text, "%ld:%02ld:%02ld", h, m, s);
            else
                std::snprintf(text, sizeof text, "%ld:%02ld", m, s);
            return text;
        }

        std::string precise_label(double seconds) {
            long long ms = seconds > 0 ? std::llround(seconds * 1000.0) : 0;
            long long total = ms / 1000;
            char text[40];
            std::snprintf(text, sizeof text, "%lld:%02lld:%02lld.%03lld", total / 3600, (total % 3600) / 60, total % 60, ms % 1000);
            return text;
        }

        std::string quote(const std::string &text) {
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
            return out + "\"";
        }

        void remember() {
            if (g_key.empty() || g_index < 0 || g_index >= int(g_items.size()))
                return;
            core_call("player.remember", "{\"key\":" + quote(g_key) + ",\"id\":" + quote(g_items[g_index].id) + "}");
        }

        void forget() {
            if (g_key.empty())
                return;
            core_call("player.clear", "{\"key\":" + quote(g_key) + "}");
        }

        void send_hidden(const char *type, const std::string &id) {
            send_action(std::string("{\"type\":\"") + type + "\",\"id\":" + quote(id) + "}");
        }

        void release_hidden(int index) {
            if (index < 0 || index >= int(g_items.size()) || !g_items[index].hidden)
                return;
            Item &item = g_items[index];
            if (g_preparing == item.id) {
                g_preparing.clear();
                if (!g_prepare_deferred)
                    send_hidden("PlayerRelease", item.id);
                g_prepare_deferred = false;
                return;
            }
            if (item.path.empty())
                return;
            item.path.clear();
            g_release_after_end.push_back(item.id);
        }

        void flush_releases() {
            std::vector<std::string> ids;
            ids.swap(g_release_after_end);
            for (const auto &id: ids)
                send_hidden("PlayerRelease", id);
        }

        void *symbol(const char *name) {
#ifdef _WIN32
            return reinterpret_cast<void *>(GetProcAddress(static_cast<HMODULE>(g_lib), name));
#else
            return dlsym(g_lib, name);
#endif
        }

        void unload_library() {
            if (!g_lib)
                return;
#ifdef _WIN32
            FreeLibrary(static_cast<HMODULE>(g_lib));
#else
            dlclose(g_lib);
#endif
            g_lib = nullptr;
            g_api = Api();
        }

        bool load_library() {
            if (g_lib)
                return true;
            std::string folder = os::exe_folder();
            std::string tools = core_call("home", "");
            if (!tools.empty()) {
                char last = tools.back();
                if (last != '/' && last != '\\')
                    tools += '/';
                tools += "tools/";
            }
#ifdef _WIN32
            std::vector<std::string> names = {tools + "libmpv-2.dll", folder + "libmpv-2.dll"};
            for (const auto &name: names) {
                int length = MultiByteToWideChar(CP_UTF8, 0, name.c_str(), -1, nullptr, 0);
                std::wstring wide(length > 0 ? length - 1 : 0, L'\0');
                if (length > 1)
                    MultiByteToWideChar(CP_UTF8, 0, name.c_str(), -1, wide.data(), length);
                g_lib = LoadLibraryExW(wide.c_str(), nullptr, LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
                if (g_lib)
                    break;
            }
#elif defined(__APPLE__)
            std::vector<std::string> names = {tools + "libmpv.2.dylib", folder + "libmpv.2.dylib", folder + "../Frameworks/libmpv.2.dylib",
                                              "libmpv.2.dylib", "/opt/homebrew/lib/libmpv.2.dylib",
                                              "/usr/local/lib/libmpv.2.dylib", "libmpv.dylib"};
            for (const auto &name: names) {
                g_lib = dlopen(name.c_str(), RTLD_NOW | RTLD_LOCAL);
                if (g_lib)
                    break;
            }
#else
            std::vector<std::string> names = {tools + "libmpv.so.2", folder + "libmpv.so.2", "libmpv.so.2", "libmpv.so.1", "libmpv.so"};
            for (const auto &name: names) {
                g_lib = dlopen(name.c_str(), RTLD_NOW | RTLD_LOCAL);
                if (g_lib)
                    break;
            }
#endif
            if (!g_lib)
                return false;
#define FJ_SYM(field, name) \
            g_api.field = reinterpret_cast<decltype(g_api.field)>(symbol(name)); \
            if (!g_api.field) { \
                unload_library(); \
                return false; \
            }
            FJ_SYM(create, "mpv_create")
            FJ_SYM(initialize, "mpv_initialize")
            FJ_SYM(set_option_string, "mpv_set_option_string")
            FJ_SYM(set_property_string, "mpv_set_property_string")
            FJ_SYM(command, "mpv_command")
            FJ_SYM(command_async, "mpv_command_async")
            FJ_SYM(observe_property, "mpv_observe_property")
            FJ_SYM(get_property, "mpv_get_property")
            FJ_SYM(wait_event, "mpv_wait_event")
            FJ_SYM(set_wakeup_callback, "mpv_set_wakeup_callback")
            FJ_SYM(terminate_destroy, "mpv_terminate_destroy")
            FJ_SYM(render_create, "mpv_render_context_create")
            FJ_SYM(render_update, "mpv_render_context_update")
            FJ_SYM(render_render, "mpv_render_context_render")
            FJ_SYM(render_set_callback, "mpv_render_context_set_update_callback")
            FJ_SYM(render_free, "mpv_render_context_free")
#undef FJ_SYM
            return true;
        }

        void post_events() {
            if (g_event_posted.exchange(true))
                return;
            unsigned generation = g_generation.load();
            slint::invoke_from_event_loop([generation] {
                g_event_posted = false;
                if (generation == g_generation.load())
                    drain_events();
            });
        }

        void post_frame() {
            if (g_frame_posted.exchange(true))
                return;
            unsigned generation = g_generation.load();
            slint::invoke_from_event_loop([generation] {
                g_frame_posted = false;
                if (generation == g_generation.load())
                    render_frame();
            });
        }

        void on_wakeup(void *) { post_events(); }

        void on_render_update(void *) { post_frame(); }


        void command_async(std::initializer_list<const char *> words) {
            if (!g_mpv)
                return;
            std::vector<const char *> args(words);
            args.push_back(nullptr);
            g_api.command_async(g_mpv, 0, args.data());
        }

        void command_tagged(uint64_t tag, std::initializer_list<const char *> words) {
            if (!g_mpv)
                return;
            std::vector<const char *> args(words);
            args.push_back(nullptr);
            g_api.command_async(g_mpv, tag, args.data());
        }

        void set(const char *name, const char *value) {
            command_async({"set", name, value});
        }

        std::string path_key(const std::string &path) {
            std::string out = path;
            for (auto &c: out) {
                if (c == '\\')
                    c = '/';
#ifdef _WIN32
                else if (c >= 'A' && c <= 'Z')
                    c = char(c - 'A' + 'a');
#endif
            }
            return out;
        }

        bool same_path(const std::string &a, const std::string &b) {
            return !a.empty() && path_key(a) == path_key(b);
        }

        std::string file_name(const std::string &path) {
            size_t slash = path.find_last_of("/\\");
            return slash == std::string::npos ? path : path.substr(slash + 1);
        }

        std::string sub_label(const SubTrack &track) {
            auto &tr = ui().global<Tr>();
            if (track.external) {
                for (const auto &file: g_sub_files)
                    if (same_path(file.path, track.file))
                        return file.label;
                if (!track.file.empty())
                    return file_name(track.file);
            }
            if (!track.title.empty())
                return track.title;
            if (!track.lang.empty())
                return track.lang;
            return plain(tr.get_playerSubtitlesTrack()) + " " + std::to_string(track.id);
        }

        int sub_checked() {
            for (int i = 0; i < int(g_sub_tracks.size()); i++)
                if (g_sub_tracks[i].selected)
                    return kSubFirst + i;
            return kSubOff;
        }

        void publish_subs() {
            auto &p = ui().global<PlayerUi>();
            auto &tr = ui().global<Tr>();
            auto items = std::make_shared<slint::VectorModel<RowMenuItemDto>>();
            RowMenuItemDto off{};
            off.key = kSubOff;
            off.label = tr.get_playerSubtitlesOff();
            off.header = tr.get_playerSubtitles();
            items->push_back(off);
            for (int pass = 0; pass < 2; pass++) {
                bool first = true;
                for (int i = 0; i < int(g_sub_tracks.size()); i++) {
                    const SubTrack &track = g_sub_tracks[i];
                    if (track.external != (pass == 1))
                        continue;
                    RowMenuItemDto row{};
                    row.key = kSubFirst + i;
                    row.label = slint::SharedString(sub_label(track));
                    if (first) {
                        row.divider = true;
                        row.header = pass == 0 ? tr.get_playerSubtitlesInVideo() : tr.get_playerSubtitlesFiles();
                        first = false;
                    }
                    items->push_back(row);
                }
            }
            RowMenuItemDto open{};
            open.key = kSubOpen;
            open.label = tr.get_playerSubtitlesOpenFile();
            open.divider = true;
            items->push_back(open);
            p.set_sub_items(items);
            int checked = sub_checked();
            p.set_sub_checked(checked);
            p.set_sub_on(checked != kSubOff);
        }

        void sub_margin() {
            if (!g_mpv)
                return;
            double scale = std::max(1.0f, ui().window().scale_factor());
            double lift = g_controls_on ? 120.0 : 24.0;
            double margin = std::max(22.0, lift * scale * 720.0 / double(std::max(g_height, 16)));
            set("sub-margin-y", std::to_string(int(std::lround(margin))).c_str());
        }

        void show_notice(const std::string &text) {
            ui().global<PlayerUi>().set_notice(slint::SharedString(text));
            g_notice_timer.start(slint::TimerMode::SingleShot, std::chrono::milliseconds(3000), [] {
                ui().global<PlayerUi>().set_notice(slint::SharedString());
            });
        }

        void sub_reset() {
            g_sub_files.clear();
            g_sub_tracks.clear();
            g_sub_choice.clear();
            g_sub_apply = false;
            g_sub_want_entry = -1;
            g_sub_opened.clear();
            g_sub_enforce.clear();
            g_sub_expect = 0;
        }

        void sub_prepare(const Item &item) {
            sub_reset();
            publish_subs();
            sub_margin();
            if (g_trim.on) {
                set("sid", "no");
                return;
            }
            set("sid", "auto");
            std::string reply = core_call("player.subtitles", "{\"id\":" + quote(item.id) + ",\"path\":" + quote(item.path) +
                                                              ",\"hidden\":" + (item.hidden ? "true" : "false") + "}");
            fj::Json json;
            std::string error;
            if (!reply.empty() && fj::parse_json(reply, json, error)) {
                if (const fj::Json *files = json.find("files")) {
                    for (const auto &entry: files->items) {
                        SubFile file;
                        if (auto *v = entry.find("path")) file.path = v->text;
                        if (auto *v = entry.find("label")) file.label = v->text;
                        if (!file.path.empty())
                            g_sub_files.push_back(file);
                    }
                }
                if (auto *v = json.find("choice")) g_sub_choice = v->text;
            }
            g_sub_apply = true;
        }

        void sub_apply() {
            if (!g_sub_apply || g_sub_want_entry < 0 || g_sub_loaded_entry != g_sub_want_entry)
                return;
            g_sub_apply = false;
            const std::string choice = g_sub_choice;
            bool by_file = choice.compare(0, 5, "file:") == 0;
            std::string chosen = by_file ? choice.substr(5) : std::string();
            int adds = 0;
            for (const auto &file: g_sub_files) {
                if (!(by_file && same_path(file.path, chosen))) {
                    command_async({"sub-add", file.path.c_str(), "auto"});
                    adds++;
                }
            }
            if (by_file && !chosen.empty()) {
                command_async({"sub-add", chosen.c_str(), "select"});
                adds++;
            }
            g_sub_enforce = adds > 0 ? choice : std::string();
            g_sub_expect = adds;
            if (choice == "off")
                set("sid", "no");
            else if (choice.compare(0, 6, "track:") == 0)
                set("sid", choice.substr(6).c_str());
        }

        void sub_remember(const std::string &choice) {
            g_sub_choice = choice;
            if (g_items.empty() || g_index < 0 || g_index >= int(g_items.size()))
                return;
            const Item &item = g_items[g_index];
            if (item.hidden)
                return;
            core_call("player.subtitle", "{\"id\":" + quote(item.id) + ",\"choice\":" + quote(choice) + ",\"hidden\":false}");
        }

        void sub_add_file(const std::string &path) {
            if (!g_mpv || path.empty())
                return;
            g_sub_opened = path;
            g_sub_enforce.clear();
            command_tagged(ReplySubOpen, {"sub-add", path.c_str(), "select"});
        }

        std::string node_text(const mpv_node &node) {
            return node.format == MPV_FORMAT_STRING && node.u.string ? std::string(node.u.string) : std::string();
        }

        void enforce_sub_choice() {
            if (g_sub_enforce.empty())
                return;
            int external = 0;
            for (const auto &track: g_sub_tracks)
                if (track.external)
                    external++;
            if (external < g_sub_expect)
                return;
            const std::string choice = g_sub_enforce;
            g_sub_enforce.clear();
            if (choice.compare(0, 5, "file:") == 0) {
                for (const auto &track: g_sub_tracks) {
                    if (track.external && same_path(track.file, choice.substr(5))) {
                        if (!track.selected)
                            set("sid", std::to_string(track.id).c_str());
                        return;
                    }
                }
            } else if (choice == "off") {
                set("sid", "no");
            } else if (choice.compare(0, 6, "track:") == 0) {
                set("sid", choice.substr(6).c_str());
            }
        }

        void read_tracks(const mpv_node *node) {
            g_sub_tracks.clear();
            if (node && node->format == MPV_FORMAT_NODE_ARRAY && node->u.list) {
                for (int i = 0; i < node->u.list->num; i++) {
                    const mpv_node &entry = node->u.list->values[i];
                    if (entry.format != MPV_FORMAT_NODE_MAP || !entry.u.list)
                        continue;
                    SubTrack track;
                    std::string type;
                    for (int k = 0; k < entry.u.list->num; k++) {
                        std::string key = entry.u.list->keys[k] ? entry.u.list->keys[k] : "";
                        const mpv_node &value = entry.u.list->values[k];
                        if (key == "type")
                            type = node_text(value);
                        else if (key == "id" && value.format == MPV_FORMAT_INT64)
                            track.id = value.u.int64;
                        else if (key == "title")
                            track.title = node_text(value);
                        else if (key == "lang")
                            track.lang = node_text(value);
                        else if (key == "external-filename")
                            track.file = node_text(value);
                        else if (key == "external" && value.format == MPV_FORMAT_FLAG)
                            track.external = value.u.flag != 0;
                        else if (key == "selected" && value.format == MPV_FORMAT_FLAG)
                            track.selected = value.u.flag != 0;
                    }
                    if (type == "sub")
                        g_sub_tracks.push_back(track);
                }
            }
            auto rank = [](const SubTrack &track) {
                if (!track.external)
                    return 0;
                for (int i = 0; i < int(g_sub_files.size()); i++)
                    if (same_path(g_sub_files[i].path, track.file))
                        return 1 + i;
                return 1 + int(g_sub_files.size());
            };
            std::stable_sort(g_sub_tracks.begin(), g_sub_tracks.end(), [&rank](const SubTrack &a, const SubTrack &b) {
                int ra = rank(a), rb = rank(b);
                return ra != rb ? ra < rb : a.id < b.id;
            });
            enforce_sub_choice();
            publish_subs();
        }

        void open_sub_dialog() {
            if (g_items.empty())
                return;
            std::string title = plain(ui().global<Tr>().get_playerSubtitlesOpenFile());
            std::string name = plain(ui().global<Tr>().get_playerSubtitles());
            std::string id = g_items[g_index].id;
            unsigned generation = g_generation.load();
            std::thread([title, name, id, generation] {
                std::string path = os::pick_path(false, title, "", name, "*.srt;*.vtt;*.ass;*.ssa");
                if (path.empty())
                    return;
                slint::invoke_from_event_loop([path, id, generation] {
                    if (generation != g_generation.load() || !g_open || g_items.empty() || g_items[g_index].id != id)
                        return;
                    sub_add_file(path);
                });
            }).detach();
        }

        void sub_pick(int key) {
            if (!g_mpv || g_items.empty())
                return;
            g_sub_enforce.clear();
            if (key == kSubOpen) {
                open_sub_dialog();
                return;
            }
            if (key == kSubOff) {
                set("sid", "no");
                sub_remember("off");
                return;
            }
            int index = key - kSubFirst;
            if (index < 0 || index >= int(g_sub_tracks.size()))
                return;
            const SubTrack &track = g_sub_tracks[index];
            std::string id = std::to_string(track.id);
            set("sid", id.c_str());
            sub_remember(track.external && !track.file.empty() ? "file:" + track.file : "track:" + id);
        }

        void show_sub_menu() {
            publish_subs();
            g_sub_tick++;
            ui().global<PlayerUi>().set_sub_menu_tick(g_sub_tick);
        }

        std::string number_text(double value) {
            char text[32];
            std::snprintf(text, sizeof text, "%.3f", value);
            return text;
        }

        void publish_sound() {
            auto &p = ui().global<PlayerUi>();
            p.set_volume(float(g_volume));
            p.set_muted(g_muted);
            p.set_autoplay(g_autoplay);
            p.set_fullscreen(g_fullscreen);
        }

        void set_volume(double value) {
            g_volume = std::clamp(std::round(value), 0.0, 200.0);
            set("volume", number_text(g_volume).c_str());
            if (g_muted && g_volume > 0) {
                g_muted = false;
                set("mute", "no");
            }
            publish_sound();
        }

        void toggle_mute() {
            g_muted = !g_muted;
            set("mute", g_muted ? "yes" : "no");
            publish_sound();
        }

        void set_autoplay(bool on) {
            g_autoplay = on;
            core_call("player.autoplay", std::string("{\"on\":\"") + (on ? "true" : "false") + "\"}");
            publish_sound();
        }

        void set_fullscreen(bool on) {
            if (g_fullscreen == on)
                return;
            g_fullscreen = on;
            ui().window().set_fullscreen(on);
            publish_sound();
        }

        void publish_items() {
            g_model = std::make_shared<slint::VectorModel<PlayerRow>>();
            for (int i = 0; i < int(g_items.size()); i++) {
                PlayerRow row;
                row.title = slint::SharedString(g_items[i].title);
                row.video = g_items[i].video;
                row.active = i == g_index;
                g_model->push_back(row);
            }
            ui().global<PlayerUi>().set_items(g_model);
        }

        void publish_active() {
            if (!g_model)
                return;
            for (int i = 0; i < int(g_items.size()); i++) {
                PlayerRow row = *g_model->row_data(size_t(i));
                bool active = i == g_index;
                if (row.active != active) {
                    row.active = active;
                    g_model->set_row_data(size_t(i), row);
                }
            }
        }

        void publish_item() {
            auto &p = ui().global<PlayerUi>();
            const Item &item = g_items[g_index];
            p.set_title(slint::SharedString(item.title));
            p.set_index(g_index);
            p.set_count(int(g_items.size()));
            p.set_has_video(item.video);
            publish_active();
        }

        void keep_awake(bool on) {
#ifdef _WIN32
            bool video = !g_items.empty() && g_index >= 0 && g_index < int(g_items.size()) && g_items[g_index].video;
            SetThreadExecutionState(on ? (ES_CONTINUOUS | ES_SYSTEM_REQUIRED | (video ? ES_DISPLAY_REQUIRED : 0)) : ES_CONTINUOUS);
#else
            (void) on;
#endif
        }

        void publish_state() {
            auto &p = ui().global<PlayerUi>();
            keep_awake(g_open && !g_paused && !g_finished && !g_failed);
            p.set_playing(!g_paused && !g_finished && !g_failed);
            p.set_finished(g_finished);
            p.set_failed(g_failed);
            p.set_hold(g_hold);
            p.set_speed(g_speed);
            p.set_speed_label(slint::SharedString(speed_label(g_speed)));
        }

        void publish_time(bool force) {
            long second = long(g_position);
            auto &p = ui().global<PlayerUi>();
            p.set_position_s(float(g_position));
            p.set_duration_s(float(g_duration));
            if (force || second != g_label_second) {
                g_label_second = second;
                p.set_position_label(slint::SharedString(time_label(g_position)));
                p.set_duration_label(slint::SharedString(time_label(g_duration)));
            }
        }

        void clear_frame() {
            ui().global<PlayerUi>().set_frame(slint::Image());
        }

        void set_opening(bool on, int percent) {
            auto &p = ui().global<PlayerUi>();
            p.set_opening(on);
            p.set_opening_percent(percent);
        }

        void load_item(int index, bool autoplay) {
            if (g_items.empty())
                return;
            g_rewind_timer.stop();
            g_hold = 0;
            int target = std::clamp(index, 0, int(g_items.size()) - 1);
            if (target != g_index)
                release_hidden(g_index);
            g_index = target;
            g_finished = false;
            g_failed = false;
            g_seek_pending = false;
            g_position = 0;
            g_duration = 0;
            g_label_second = -1;
            g_paused = !autoplay;
            set("speed", number_text(g_speed).c_str());
            set("pause", autoplay ? "no" : "yes");
            const Item &item = g_items[g_index];
            if (item.hidden && item.path.empty()) {
                command_async({"stop"});
                clear_frame();
                if (g_preparing != item.id) {
                    if (!g_preparing.empty() && !g_prepare_deferred)
                        send_hidden("PlayerRelease", g_preparing);
                    g_preparing = item.id;
                    g_prepare_deferred = g_loaded > 0;
                    if (!g_prepare_deferred)
                        send_hidden("PlayerPrepare", item.id);
                }
                g_preparing_autoplay = autoplay;
                set_opening(true, 0);
            } else {
                set_opening(false, 0);
                sub_prepare(item);
                g_loaded++;
                g_load_replies++;
                command_tagged(ReplyLoad, {"loadfile", item.path.c_str(), "replace"});
            }
            remember();
            publish_item();
            publish_state();
            publish_time(true);
        }

        void finish_list() {
            g_finished = true;
            g_paused = true;
            forget();
            publish_state();
        }

        void end_of_item() {
            if (g_index + 1 < int(g_items.size()))
                load_item(g_index + 1, g_autoplay);
            else
                finish_list();
        }

        void item_failed() {
            set("pause", "yes");
            if (g_index + 1 < int(g_items.size())) {
                load_item(g_index + 1, false);
                return;
            }
            g_failed = true;
            g_paused = true;
            publish_state();
        }

        void drain_events() {
            while (g_mpv) {
                mpv_event *event = g_api.wait_event(g_mpv, 0.0);
                if (event->event_id == MPV_EVENT_NONE)
                    break;
                switch (event->event_id) {
                    case MPV_EVENT_PROPERTY_CHANGE: {
                        auto *prop = static_cast<mpv_event_property *>(event->data);
                        if (!prop || prop->format == MPV_FORMAT_NONE)
                            break;
                        if (event->reply_userdata == ObsPosition && prop->format == MPV_FORMAT_DOUBLE) {
                            if (!g_seek_pending) {
                                g_position = *static_cast<double *>(prop->data);
                                publish_time(false);
                                if (g_trim.on) {
                                    publish_playhead();
                                    trim_check_range();
                                }
                            }
                        } else if (event->reply_userdata == ObsDuration && prop->format == MPV_FORMAT_DOUBLE) {
                            g_duration = *static_cast<double *>(prop->data);
                            publish_time(true);
                            if (g_trim.on) {
                                if (!g_trim.ready) {
                                    trim_init();
                                } else {
                                    trim_frames();
                                    g_trim.e = std::min(g_trim.e, g_trim.frames);
                                    g_trim.s = std::min(g_trim.s, g_trim.e - 1);
                                    publish_trim();
                                    g_tile_from = 1;
                                    g_tile_to = 0;
                                    g_wave_group = 0;
                                    publish_view();
                                }
                            }
                        } else if (event->reply_userdata == ObsPause && prop->format == MPV_FORMAT_FLAG) {
                            g_paused = *static_cast<int *>(prop->data) != 0;
                            publish_state();
                        } else if (event->reply_userdata == ObsTracks && prop->format == MPV_FORMAT_NODE) {
                            read_tracks(static_cast<mpv_node *>(prop->data));
                        } else if (event->reply_userdata == ObsEof && prop->format == MPV_FORMAT_FLAG) {
                            if (*static_cast<int *>(prop->data) != 0 && !g_finished) {
                                if (g_trim.on)
                                    trim_stop_range(false);
                                else
                                    end_of_item();
                            }
                        }
                        break;
                    }
                    case MPV_EVENT_PLAYBACK_RESTART:
                        g_seek_pending = false;
                        break;
                    case MPV_EVENT_COMMAND_REPLY: {
                        auto *reply = static_cast<mpv_event_command *>(event->data);
                        if (event->reply_userdata == ReplyLoad) {
                            g_load_replies = std::max(0, g_load_replies - 1);
                            if (g_load_replies == 0 && event->error >= 0 && reply && reply->result.format == MPV_FORMAT_NODE_MAP &&
                                reply->result.u.list) {
                                for (int k = 0; k < reply->result.u.list->num; k++) {
                                    const char *key = reply->result.u.list->keys[k];
                                    const mpv_node &value = reply->result.u.list->values[k];
                                    if (key && std::string(key) == "playlist_entry_id" && value.format == MPV_FORMAT_INT64)
                                        g_sub_want_entry = value.u.int64;
                                }
                                sub_apply();
                            }
                        } else if (event->reply_userdata == ReplySubOpen) {
                            std::string path = g_sub_opened;
                            g_sub_opened.clear();
                            if (event->error >= 0 && !path.empty())
                                sub_remember("file:" + path);
                            else
                                show_notice(plain(ui().global<Tr>().get_playerSubtitlesFailed()));
                        }
                        break;
                    }
                    case MPV_EVENT_START_FILE: {
                        auto *start = static_cast<mpv_event_start_file *>(event->data);
                        if (start)
                            g_sub_entry = start->playlist_entry_id;
                        break;
                    }
                    case MPV_EVENT_FILE_LOADED:
                        g_sub_loaded_entry = g_sub_entry;
                        sub_apply();
                        break;
                    case MPV_EVENT_END_FILE: {
                        auto *end = static_cast<mpv_event_end_file *>(event->data);
                        g_loaded = std::max(0, g_loaded - 1);
                        flush_releases();
                        if (g_loaded == 0) {
                            if (g_prepare_deferred && !g_preparing.empty()) {
                                g_prepare_deferred = false;
                                send_hidden("PlayerPrepare", g_preparing);
                            }
                        }
                        if (end && end->reason == MPV_END_FILE_REASON_ERROR) {
                            if (g_trim.on) {
                                g_failed = true;
                                g_paused = true;
                                publish_state();
                            } else {
                                item_failed();
                            }
                        }
                        break;
                    }
                    default:
                        break;
                }
            }
        }

        void render_frame() {
            if (!g_render)
                return;
            uint64_t flags = g_api.render_update(g_render);
            if (!(flags & MPV_RENDER_UPDATE_FRAME) && !g_force_render)
                return;
            g_force_render = false;
            if (!g_items.empty() && !g_items[g_index].video)
                return;
            int width = std::max(g_width, 16);
            int height = std::max(g_height, 16);
            auto &buffer = g_buffers[g_buffer_next];
            if (buffer.width() != uint32_t(width) || buffer.height() != uint32_t(height))
                buffer = slint::SharedPixelBuffer<slint::Rgb8Pixel>(uint32_t(width), uint32_t(height));
            int size[2] = {width, height};
            size_t stride = size_t(width) * 3;
            const char *format = "rgb24";
            mpv_render_param params[] = {
                    {MPV_RENDER_PARAM_SW_SIZE, size},
                    {MPV_RENDER_PARAM_SW_FORMAT, const_cast<char *>(format)},
                    {MPV_RENDER_PARAM_SW_STRIDE, &stride},
                    {MPV_RENDER_PARAM_SW_POINTER, buffer.begin()},
                    {MPV_RENDER_PARAM_INVALID, nullptr}};
            if (g_api.render_render(g_render, params) < 0)
                return;
            ui().global<PlayerUi>().set_frame(slint::Image(buffer));
            g_buffer_next ^= 1;
        }

        bool start_engine() {
            if (g_mpv)
                return true;
            if (!load_library())
                return false;
            g_mpv = g_api.create();
            if (!g_mpv)
                return false;
            const char *options[][2] = {
                    {"vo", "libmpv"},
                    {"hwdec", "no"},
                    {"sws-fast", "yes"},
                    {"audio-display", "no"},
                    {"idle", "yes"},
                    {"keep-open", "yes"},
                    {"hr-seek", "yes"},
                    {"hr-seek-framedrop", "no"},
                    {"cache", "no"},
                    {"demuxer-max-bytes", "8MiB"},
                    {"demuxer-max-back-bytes", "1MiB"},
                    {"msg-level", "all=error"},
                    {"input-default-bindings", "no"},
                    {"input-vo-keyboard", "no"},
                    {"osc", "no"},
                    {"sub-auto", "no"},
                    {"video-timing-offset", "0"},
                    {"terminal", "no"}};
            for (const auto &option: options)
                g_api.set_option_string(g_mpv, option[0], option[1]);
            g_api.set_option_string(g_mpv, "volume-max", "200");
            g_api.set_option_string(g_mpv, "volume", number_text(g_volume).c_str());
            g_api.set_option_string(g_mpv, "mute", g_muted ? "yes" : "no");
            if (const char *driver = std::getenv("FJ_PLAYER_AO"))
                g_api.set_option_string(g_mpv, "ao", driver);
            if (const char *log = std::getenv("FJ_PLAYER_MPV_LOG")) {
                g_api.set_option_string(g_mpv, "log-file", log);
                g_api.set_option_string(g_mpv, "msg-level", "all=info");
            }
            if (g_api.initialize(g_mpv) < 0) {
                g_api.terminate_destroy(g_mpv);
                g_mpv = nullptr;
                unload_library();
                return false;
            }
            int advanced = 1;
            mpv_render_param params[] = {
                    {MPV_RENDER_PARAM_API_TYPE, const_cast<char *>(MPV_RENDER_API_TYPE_SW)},
                    {MPV_RENDER_PARAM_ADVANCED_CONTROL, &advanced},
                    {MPV_RENDER_PARAM_INVALID, nullptr}};
            if (g_api.render_create(&g_render, g_mpv, params) < 0) {
                g_api.terminate_destroy(g_mpv);
                g_mpv = nullptr;
                g_render = nullptr;
                unload_library();
                return false;
            }
            g_api.render_set_callback(g_render, on_render_update, nullptr);
            g_api.observe_property(g_mpv, ObsPosition, "time-pos", MPV_FORMAT_DOUBLE);
            g_api.observe_property(g_mpv, ObsDuration, "duration", MPV_FORMAT_DOUBLE);
            g_api.observe_property(g_mpv, ObsPause, "pause", MPV_FORMAT_FLAG);
            g_api.observe_property(g_mpv, ObsEof, "eof-reached", MPV_FORMAT_FLAG);
            g_api.observe_property(g_mpv, ObsTracks, "track-list", MPV_FORMAT_NODE);
            g_api.set_wakeup_callback(g_mpv, on_wakeup, nullptr);
            return true;
        }

        void stop_engine() {
            g_generation++;
            g_rewind_timer.stop();
            if (g_render) {
                g_api.render_set_callback(g_render, nullptr, nullptr);
                g_api.render_free(g_render);
                g_render = nullptr;
            }
            if (g_mpv) {
                g_api.set_wakeup_callback(g_mpv, nullptr, nullptr);
                g_api.terminate_destroy(g_mpv);
                g_mpv = nullptr;
            }
            g_event_posted = false;
            g_frame_posted = false;
            g_load_replies = 0;
            g_sub_entry = -1;
            g_sub_loaded_entry = -1;
#ifndef __APPLE__
            unload_library();
#endif
        }

        void begin_hold(bool forward) {
            if (!g_mpv || g_finished || g_failed || g_hold != 0)
                return;
            g_resume_after_hold = !g_paused;
            if (forward) {
                g_hold = 1;
                set("speed", "2.0");
                if (g_paused)
                    set("pause", "no");
            } else {
                g_hold = -1;
                set("pause", "yes");
                g_rewind_target = g_position;
                g_rewind_last = std::chrono::steady_clock::now();
                g_rewind_timer.start(slint::TimerMode::Repeated, std::chrono::milliseconds(40), [] {
                    if (g_hold >= 0 || g_seek_pending)
                        return;
                    auto now = std::chrono::steady_clock::now();
                    double elapsed = std::chrono::duration<double>(now - g_rewind_last).count();
                    g_rewind_last = now;
                    g_rewind_target = std::max(0.0, g_rewind_target - kRewindSpeed * std::min(elapsed, 0.5));
                    g_seek_pending = true;
                    std::string target = number_text(g_rewind_target);
                    command_async({"seek", target.c_str(), "absolute+exact"});
                    g_position = g_rewind_target;
                    publish_time(false);
                });
            }
            publish_state();
        }

        void end_hold() {
            if (g_hold == 0)
                return;
            int was = g_hold;
            g_hold = 0;
            g_rewind_timer.stop();
            if (was > 0) {
                set("speed", number_text(g_speed).c_str());
                if (!g_resume_after_hold)
                    set("pause", "yes");
            } else if (g_resume_after_hold) {
                set("pause", "no");
            }
            publish_state();
        }

        void seek_to(double seconds) {
            if (!g_mpv)
                return;
            double target = std::clamp(seconds, 0.0, g_duration > 0 ? g_duration : seconds);
            g_finished = false;
            g_seek_pending = true;
            std::string text = number_text(target);
            command_async({"seek", text.c_str(), "absolute+exact"});
            g_position = target;
            publish_time(true);
            publish_state();
            if (g_paused && g_duration > 0 && target >= g_duration - 0.05)
                end_of_item();
        }

        void toggle_play() {
            if (!g_mpv)
                return;
            if (g_finished) {
                load_item(0, true);
                return;
            }
            if (g_paused && g_duration > 0 && g_position >= g_duration - 0.3)
                seek_to(0);
            command_async({"cycle", "pause"});
        }

        void previous() {
            if (g_position > 3.0 || g_index == 0)
                seek_to(0);
            else
                load_item(g_index - 1, false);
        }

        void trim_log(const std::string &line) {
            const char *path = std::getenv("FJ_PLAYER_LOG");
            if (!path)
                return;
            if (FILE *file = std::fopen(path, "a")) {
                std::fprintf(file, "%s\n", line.c_str());
                std::fclose(file);
            }
        }

        std::string home_folder() {
            std::string home = core_call("home", "");
            if (home.empty())
                return home;
            char last = home.back();
            if (last != '/' && last != '\\')
                home += '/';
            return home;
        }

#ifdef _WIN32
        std::wstring wide(const std::string &text) {
            int length = MultiByteToWideChar(CP_UTF8, 0, text.c_str(), -1, nullptr, 0);
            std::wstring out(length > 0 ? length - 1 : 0, L'\0');
            if (length > 1)
                MultiByteToWideChar(CP_UTF8, 0, text.c_str(), -1, out.data(), length);
            return out;
        }

        std::wstring command_arg(const std::wstring &arg) {
            if (!arg.empty() && arg.find_first_of(L" \t\"") == std::wstring::npos)
                return arg;
            std::wstring out = L"\"";
            size_t slashes = 0;
            for (wchar_t c: arg) {
                if (c == L'\\') {
                    slashes++;
                    continue;
                }
                if (c == L'"')
                    out.append(slashes * 2 + 1, L'\\');
                else
                    out.append(slashes, L'\\');
                slashes = 0;
                out += c;
            }
            out.append(slashes * 2, L'\\');
            return out + L"\"";
        }
#endif

        bool file_exists(const std::string &path) {
#ifdef _WIN32
            DWORD attributes = GetFileAttributesW(wide(path).c_str());
            return attributes != INVALID_FILE_ATTRIBUTES && !(attributes & FILE_ATTRIBUTE_DIRECTORY);
#else
            struct stat info{};
            return stat(path.c_str(), &info) == 0 && S_ISREG(info.st_mode);
#endif
        }

        void make_folder(const std::string &path) {
#ifdef _WIN32
            CreateDirectoryW(wide(path).c_str(), nullptr);
#else
            mkdir(path.c_str(), 0755);
#endif
        }

        void remove_file(const std::string &path) {
#ifdef _WIN32
            DeleteFileW(wide(path).c_str());
#else
            unlink(path.c_str());
#endif
        }

        bool read_file(const std::string &path, std::vector<uint8_t> &out, size_t size) {
#ifdef _WIN32
            FILE *file = _wfopen(wide(path).c_str(), L"rb");
#else
            FILE *file = std::fopen(path.c_str(), "rb");
#endif
            if (!file)
                return false;
            out.resize(size);
            size_t got = std::fread(out.data(), 1, size, file);
            std::fclose(file);
            return got == size;
        }

        std::string find_ffmpeg() {
            std::string home = home_folder();
#ifdef _WIN32
            std::string name = "ffmpeg.exe";
#else
            std::string name = "ffmpeg";
#endif
            if (!home.empty() && file_exists(home + "tools/" + name))
                return home + "tools/" + name;
            return "";
        }

        bool run_tool(const std::vector<std::string> &args, const std::atomic<bool> &cancel) {
#ifdef _WIN32
            std::wstring command;
            for (const auto &arg: args) {
                if (!command.empty())
                    command += L' ';
                command += command_arg(wide(arg));
            }
            STARTUPINFOW startup{};
            startup.cb = sizeof startup;
            PROCESS_INFORMATION process{};
            if (!CreateProcessW(nullptr, command.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW | BELOW_NORMAL_PRIORITY_CLASS,
                                nullptr, nullptr, &startup, &process))
                return false;
            CloseHandle(process.hThread);
            bool ok = false;
            bool ended = false;
            for (int waited = 0; waited < 60000 && !cancel.load(); waited += 50) {
                if (WaitForSingleObject(process.hProcess, 50) == WAIT_OBJECT_0) {
                    DWORD code = 1;
                    GetExitCodeProcess(process.hProcess, &code);
                    ok = code == 0;
                    ended = true;
                    break;
                }
            }
            if (!ended)
                TerminateProcess(process.hProcess, 1);
            CloseHandle(process.hProcess);
            return ok;
#else
            std::vector<char *> argv;
            for (const auto &arg: args)
                argv.push_back(const_cast<char *>(arg.c_str()));
            argv.push_back(nullptr);
            posix_spawn_file_actions_t actions;
            posix_spawn_file_actions_init(&actions);
            posix_spawn_file_actions_addopen(&actions, 0, "/dev/null", O_RDONLY, 0);
            posix_spawn_file_actions_addopen(&actions, 1, "/dev/null", O_WRONLY, 0);
            posix_spawn_file_actions_addopen(&actions, 2, "/dev/null", O_WRONLY, 0);
            pid_t pid = 0;
            int started = posix_spawnp(&pid, argv[0], &actions, nullptr, argv.data(), environ);
            posix_spawn_file_actions_destroy(&actions);
            if (started != 0)
                return false;
            int status = 0;
            for (int waited = 0; waited < 60000 && !cancel.load(); waited += 50) {
                if (waitpid(pid, &status, WNOHANG) == pid)
                    return WIFEXITED(status) && WEXITSTATUS(status) == 0;
                usleep(50000);
            }
            kill(pid, SIGKILL);
            waitpid(pid, &status, 0);
            return false;
#endif
        }

        bool thumb_next(const std::shared_ptr<ThumbJob> &job, ThumbTile &tile) {
            std::lock_guard<std::mutex> guard(job->lock);
            if (job->cancel.load() || job->pending.empty()) {
                job->running--;
                return false;
            }
            tile = job->pending.front();
            job->pending.pop_front();
            return true;
        }

        void thumb_worker(std::shared_ptr<ThumbJob> job) {
            Working working;
            ThumbTile tile;
            while (thumb_next(job, tile)) {
                std::string out = job->folder + "/thumb-" + std::to_string(job->tag) + "-" + std::to_string(tile.level + 64) + "-" +
                                  std::to_string(tile.index) + ".rgb";
                std::string size = std::to_string(job->width) + ":" + std::to_string(job->height);
                std::vector<std::string> args = {job->ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-threads", "2",
                                                 "-ss", number_text(tile.at), "-i", job->input, "-an", "-sn", "-dn", "-frames:v", "1",
                                                 "-vf", "scale=" + size + ":force_original_aspect_ratio=increase,crop=" + size,
                                                 "-pix_fmt", "rgb24", "-f", "rawvideo", "-y", out};
                bool ran = run_tool(args, job->cancel);
                std::vector<uint8_t> pixels;
                bool ok = ran && read_file(out, pixels, size_t(job->width) * size_t(job->height) * 3);
                remove_file(out);
                if (!ok || job->cancel.load())
                    continue;
                unsigned tag = job->tag;
                int w = job->width, h = job->height;
                slint::invoke_from_event_loop([tag, tile, w, h, pixels] {
                    if (tag != g_thumb_tag.load())
                        return;
                    slint::SharedPixelBuffer<slint::Rgb8Pixel> buffer{uint32_t(w), uint32_t(h)};
                    std::copy(pixels.begin(), pixels.end(), reinterpret_cast<uint8_t *>(buffer.begin()));
                    slint::Image image(buffer);
                    g_tile_cache[{tile.level, tile.index}] = image;
                    g_thumb_made++;
                    if (!g_thumbs)
                        return;
                    for (size_t row = 0; row < g_thumb_keys.size(); row++) {
                        if (g_thumb_keys[row] != std::make_pair(tile.level, tile.index))
                            continue;
                        auto data = g_thumbs->row_data(row);
                        if (!data)
                            break;
                        TrimTile entry = *data;
                        entry.img = image;
                        g_thumbs->set_row_data(row, entry);
                        g_thumbs_loaded++;
                        break;
                    }
                });
            }
        }

        void cancel_thumbs() {
            g_thumb_tag++;
            if (g_thumb_job)
                g_thumb_job->cancel = true;
            g_thumb_job.reset();
            g_edge_timer.stop();
        }

        void reset_tiles() {
            cancel_thumbs();
            g_tile_cache.clear();
            g_thumb_keys.clear();
            g_tile_from = 1;
            g_tile_to = 0;
            g_thumbs_loaded = 0;
            g_thumb_made = 0;
            g_wave_all.clear();
            g_wave_from = 1;
            g_wave_to = 0;
            g_wave_group = 0;
        }

        std::vector<float> parse_peaks(const std::string &text) {
            std::vector<float> out;
            fj::Json json;
            std::string error;
            if (text.empty() || !fj::parse_json(text, json, error))
                return out;
            for (const auto &entry: json.items)
                out.push_back(float(std::clamp(entry.number, 0.0, 1.0)));
            return out;
        }

        double view_min_span() {
            return std::min(5.0, g_duration);
        }

        void view_clamp() {
            if (g_duration <= 0)
                return;
            g_trim.view_span = std::clamp(g_trim.view_span, view_min_span(), g_duration);
            g_trim.view_start = std::clamp(g_trim.view_start, 0.0, std::max(0.0, g_duration - g_trim.view_span));
        }

        void publish_wave() {
            if (!g_trim.audio || g_wave_all.empty() || g_trim.strip_w <= 0 || g_duration <= 0)
                return;
            long total = long(g_wave_all.size());
            double bar_s = g_duration / double(total);
            double px_per_bar = double(g_trim.strip_w) / g_trim.view_span * bar_s;
            double gap_px = std::max(2.0, double(std::max(g_trim.strip_h, 16)) / 13.0);
            int group = std::max(1, int(std::ceil(gap_px / std::max(px_per_bar, 1e-9) - 1e-9)));
            long first = long(std::floor(g_trim.view_start / bar_s));
            long last = long(std::ceil((g_trim.view_start + g_trim.view_span) / bar_s));
            if (group == g_wave_group && first >= g_wave_from && last <= g_wave_to && g_wave)
                return;
            long margin = long(std::ceil(g_trim.view_span * 0.5 / bar_s));
            long from = std::max(0L, (first - margin) / group * group);
            long to = std::min(total, last + margin);
            std::vector<float> slice;
            slice.reserve(size_t((to - from) / group + 1));
            for (long b = from; b < to; b += group) {
                float peak = 0;
                for (long k = b; k < std::min(to, b + group); k++)
                    peak = std::max(peak, g_wave_all[size_t(k)]);
                slice.push_back(peak);
            }
            g_wave_group = group;
            g_wave_from = from;
            g_wave_to = to;
            g_wave = std::make_shared<slint::VectorModel<float>>(slice);
            auto &p = ui().global<PlayerUi>();
            p.set_trim_wave_first(float(double(from) * bar_s / g_duration));
            p.set_trim_wave_step(float(double(group) * bar_s / g_duration));
            p.set_trim_wave(g_wave);
        }

        void start_wave() {
            g_trim.thumbs_started = true;
            int bars = std::max(std::clamp(g_trim.strip_w / 4, 40, 400), int(std::min(4000.0, std::round(g_duration * 20.0))));
            cancel_thumbs();
            g_wave_all.clear();
            g_wave.reset();
            ui().global<PlayerUi>().set_trim_wave(std::make_shared<slint::VectorModel<float>>());
            unsigned tag = g_thumb_tag.load();
            std::string args = "{\"path\":" + quote(g_items[g_index].path) + ",\"bars\":" + std::to_string(bars) +
                               ",\"durationMs\":" + std::to_string(std::llround(g_duration * 1000.0)) + "}";
            trim_log("trim wave " + std::to_string(bars) + " bars");
            std::thread([tag, args] {
                Working working;
                std::vector<float> peaks = parse_peaks(core_call("player.waveform", args));
                if (g_closing.load())
                    return;
                slint::invoke_from_event_loop([tag, peaks] {
                    if (tag != g_thumb_tag.load() || !g_trim.on)
                        return;
                    g_wave_all = peaks;
                    g_wave_group = 0;
                    publish_wave();
                    trim_log("trim wave loaded " + std::to_string(peaks.size()));
                });
            }).detach();
        }

        void thumb_spawn(const std::shared_ptr<ThumbJob> &job) {
            unsigned workers = std::clamp(std::thread::hardware_concurrency() / 4, 1u, 3u);
            std::lock_guard<std::mutex> guard(job->lock);
            while (job->running < int(workers) && job->running < int(job->pending.size())) {
                job->running++;
                std::thread(thumb_worker, job).detach();
            }
        }

        std::shared_ptr<ThumbJob> thumb_job() {
            if (g_thumb_job)
                return g_thumb_job;
            std::string home = home_folder();
            if (home.empty())
                return nullptr;
            make_folder(home + "cache");
            make_folder(home + "cache/player-thumbs");
            std::string ffmpeg = find_ffmpeg();
            if (ffmpeg.empty())
                return nullptr;
            auto job = std::make_shared<ThumbJob>();
            job->ffmpeg = ffmpeg;
            job->input = g_items[g_index].path;
            job->folder = home + "cache/player-thumbs";
            job->height = std::clamp(g_trim.strip_h, 16, 240);
            job->width = std::clamp(int(std::lround(double(job->height) * 1.6)), 16, 384);
            job->tag = g_thumb_tag.load();
            g_thumb_job = job;
            trim_log("trim thumbs " + std::to_string(job->width) + "x" + std::to_string(job->height) + " with " + job->ffmpeg);
            return job;
        }

        bool cached_tile(int level, double at, slint::Image &out) {
            for (int d = 1; d <= 3; d++) {
                for (int k: {level - d, level + d}) {
                    double size = std::ldexp(1.0, k);
                    auto found = g_tile_cache.find({k, long(std::floor(at / size))});
                    if (found != g_tile_cache.end()) {
                        out = found->second;
                        return true;
                    }
                }
            }
            return false;
        }

        void publish_tiles() {
            if (!g_trim.on || !g_trim.ready || g_trim.audio || g_duration <= 0 || g_trim.strip_w <= 0 || g_trim.strip_h <= 0)
                return;
            if (g_items.empty() || !g_items[g_index].video)
                return;
            g_trim.thumbs_started = true;
            double want = double(std::clamp(g_trim.strip_h, 16, 240)) * 64.0 / 52.0 / double(g_trim.strip_w) * g_trim.view_span;
            int level = int(std::lround(std::log2(std::max(want, 1e-3))));
            double size = std::ldexp(1.0, level);
            long count = std::max(1L, long(std::ceil(g_duration / size - 1e-9)));
            long first = long(std::floor(g_trim.view_start / size));
            long last = std::min(count - 1, long(std::floor((g_trim.view_start + g_trim.view_span) / size)));
            if (level == g_tile_level && first >= g_tile_from && last <= g_tile_to && g_thumbs)
                return;
            long margin = long(std::ceil(g_trim.view_span / size));
            long from = std::max(0L, first - margin);
            long to = std::min(count - 1, last + margin);
            if (g_tile_cache.size() > 3000) {
                for (auto it = g_tile_cache.begin(); it != g_tile_cache.end();)
                    it = it->first.first == level ? std::next(it) : g_tile_cache.erase(it);
            }
            std::vector<TrimTile> rows;
            g_thumb_keys.clear();
            g_thumbs_loaded = 0;
            std::vector<ThumbTile> missing;
            for (long i = from; i <= to; i++) {
                double at = double(i) * size;
                double len = std::min(size, g_duration - at);
                TrimTile row;
                row.at = float(at / g_duration);
                row.len = float(len / g_duration);
                auto exact = g_tile_cache.find({level, i});
                if (exact != g_tile_cache.end()) {
                    row.img = exact->second;
                    g_thumbs_loaded++;
                } else {
                    cached_tile(level, at + len / 2, row.img);
                    missing.push_back({level, i, at + len / 2});
                }
                rows.push_back(row);
                g_thumb_keys.push_back({level, i});
            }
            g_tile_level = level;
            g_tile_from = from;
            g_tile_to = to;
            g_thumbs = std::make_shared<slint::VectorModel<TrimTile>>(rows);
            ui().global<PlayerUi>().set_trim_thumbs(g_thumbs);
            double view_end = g_trim.view_start + g_trim.view_span;
            double middle = g_trim.view_start + g_trim.view_span / 2;
            std::stable_sort(missing.begin(), missing.end(), [&](const ThumbTile &a, const ThumbTile &b) {
                bool va = a.at + size / 2 >= g_trim.view_start && a.at - size / 2 <= view_end;
                bool vb = b.at + size / 2 >= g_trim.view_start && b.at - size / 2 <= view_end;
                if (va != vb)
                    return va;
                return std::fabs(a.at - middle) < std::fabs(b.at - middle);
            });
            auto job = thumb_job();
            if (!job)
                return;
            {
                std::lock_guard<std::mutex> guard(job->lock);
                job->pending.assign(missing.begin(), missing.end());
            }
            thumb_spawn(job);
        }

        void publish_view() {
            if (!g_trim.on || !g_trim.ready || g_duration <= 0)
                return;
            view_clamp();
            auto &p = ui().global<PlayerUi>();
            p.set_trim_view_start(float(g_trim.view_start / g_duration));
            p.set_trim_view_span(float(g_trim.view_span / g_duration));
            p.set_trim_zoom_in(g_trim.view_span > view_min_span() + 1e-6);
            p.set_trim_zoom_out(g_trim.view_span < g_duration - 1e-6);
            if (g_trim.audio)
                publish_wave();
            else
                publish_tiles();
        }

        void view_pan(double view_fraction) {
            g_trim.view_start += view_fraction * g_trim.view_span;
            publish_view();
        }

        void view_zoom(double factor, double anchor) {
            if (g_duration <= 0 || factor <= 0)
                return;
            anchor = std::clamp(anchor, 0.0, 1.0);
            double at = g_trim.view_start + anchor * g_trim.view_span;
            g_trim.view_span = std::clamp(g_trim.view_span * factor, view_min_span(), g_duration);
            g_trim.view_start = at - anchor * g_trim.view_span;
            publish_view();
        }

        void view_center(double fraction) {
            g_trim.view_start = std::clamp(fraction, 0.0, 1.0) * g_duration - g_trim.view_span / 2;
            publish_view();
        }

        void view_show(double seconds) {
            double margin = g_trim.view_span * 0.08;
            if (seconds < g_trim.view_start + margin)
                g_trim.view_start = seconds - margin;
            else if (seconds > g_trim.view_start + g_trim.view_span - margin)
                g_trim.view_start = seconds - g_trim.view_span + margin;
            else
                return;
            publish_view();
        }

        void start_thumbs() {
            if (!g_trim.on || !g_trim.ready || g_duration <= 0 || g_trim.strip_w <= 0 || g_trim.strip_h <= 0 || g_items.empty())
                return;
            if (g_trim.audio) {
                if (!g_trim.thumbs_started)
                    start_wave();
                else
                    publish_wave();
                return;
            }
            if (!g_items[g_index].video)
                return;
            g_tile_from = 1;
            g_tile_to = 0;
            publish_tiles();
        }

        double trim_time(long b) {
            if (b >= g_trim.frames)
                return g_duration;
            return double(std::max(0L, b)) / g_trim.fps;
        }

        long trim_index(double seconds) {
            if (seconds >= g_duration - 0.5 / g_trim.fps)
                return g_trim.frames;
            return std::clamp(long(std::llround(seconds * g_trim.fps)), 0L, g_trim.frames);
        }

        double read_double(const char *name) {
            double value = 0;
            if (!g_mpv || g_api.get_property(g_mpv, name, MPV_FORMAT_DOUBLE, &value) < 0)
                return 0;
            return std::isfinite(value) ? value : 0;
        }

        void trim_frames() {
            g_trim.frames = std::max(1L, long(std::ceil(g_duration * g_trim.fps - 1e-6)));
        }

        void trim_refresh_fps() {
            if (!g_trim.fps_guess)
                return;
            double fps = read_double("container-fps");
            if (fps <= 1 || fps > 1000)
                fps = read_double("estimated-vf-fps");
            if (fps <= 1 || fps > 1000)
                return;
            double start = trim_time(g_trim.s), end = trim_time(g_trim.e);
            g_trim.fps = fps;
            g_trim.fps_guess = false;
            trim_frames();
            g_trim.s = std::min(trim_index(start), g_trim.frames - 1);
            g_trim.e = std::clamp(trim_index(end), g_trim.s + 1, g_trim.frames);
        }

        void publish_trim() {
            auto &p = ui().global<PlayerUi>();
            p.set_trim(g_trim.on);
            if (!g_trim.on)
                return;
            double start = trim_time(g_trim.s), end = trim_time(g_trim.e);
            p.set_trim_start(g_duration > 0 ? float(start / g_duration) : 0.0f);
            p.set_trim_end(g_duration > 0 ? float(end / g_duration) : 1.0f);
            p.set_trim_start_label(slint::SharedString(precise_label(start)));
            p.set_trim_end_label(slint::SharedString(precise_label(end)));
            p.set_trim_length_label(slint::SharedString(precise_label(end - start)));
            p.set_trim_active(g_trim.active);
            p.set_trim_ready(g_trim.ready);
        }

        void publish_playhead() {
            if (!g_trim.on)
                return;
            ui().global<PlayerUi>().set_trim_playhead(g_duration > 0 ? float(std::clamp(g_position / g_duration, 0.0, 1.0)) : 0.0f);
            if (!g_trim.ready || g_duration <= 0)
                return;
            bool inside = g_position >= g_trim.view_start && g_position <= g_trim.view_start + g_trim.view_span;
            if (!g_paused && g_trim.follow && !inside) {
                g_trim.view_start = g_position - g_trim.view_span * 0.05;
                publish_view();
                inside = true;
            }
            g_trim.follow = inside;
        }

        void trim_seek(double seconds) {
            if (!g_mpv)
                return;
            double target = std::clamp(seconds, 0.0, std::max(0.0, g_duration));
            g_seek_pending = true;
            std::string text = number_text(target);
            command_async({"seek", text.c_str(), "absolute+exact"});
            g_position = target;
            publish_time(true);
            publish_playhead();
        }

        double trim_shown(int handle) {
            if (handle == 0)
                return trim_time(g_trim.s);
            return trim_time(std::max(g_trim.s, g_trim.e - 1));
        }

        void trim_stop_range(bool show_end) {
            bool was = g_trim.range;
            g_trim.range = false;
            set("pause", "yes");
            g_paused = true;
            if (was && show_end)
                trim_seek(trim_shown(1));
            publish_state();
        }

        void trim_init() {
            if (!g_trim.on || g_trim.ready || g_duration <= 0)
                return;
            g_trim.ready = true;
            g_trim.audio = !g_items.empty() && !g_items[g_index].video;
            g_trim.fps_guess = !g_trim.audio;
            g_trim.fps = g_trim.audio ? 100 : 30;
            ui().global<PlayerUi>().set_trim_audio(g_trim.audio);
            trim_frames();
            g_trim.s = trim_index(double(g_trim.start_ms) / 1000.0);
            g_trim.e = g_trim.end_ms > 0 ? trim_index(double(g_trim.end_ms) / 1000.0) : g_trim.frames;
            trim_refresh_fps();
            if (g_trim.end_ms <= 0)
                g_trim.e = g_trim.frames;
            g_trim.s = std::clamp(g_trim.s, 0L, g_trim.frames - 1);
            g_trim.e = std::clamp(g_trim.e, g_trim.s + 1, g_trim.frames);
            g_trim.view_span = std::min(120.0, g_duration);
            g_trim.view_start = trim_time(g_trim.s) - g_trim.view_span * 0.08;
            view_clamp();
            trim_seek(trim_shown(0));
            publish_trim();
            publish_view();
            start_thumbs();
        }

        void trim_move(int handle, long index, bool show = true) {
            if (!g_trim.ready)
                return;
            if (g_trim.range)
                trim_stop_range(false);
            g_trim.active = handle == 1 ? 1 : 0;
            if (g_trim.active == 0)
                g_trim.s = std::clamp(index, 0L, g_trim.e - 1);
            else
                g_trim.e = std::clamp(index, g_trim.s + 1, g_trim.frames);
            trim_seek(trim_shown(g_trim.active));
            publish_trim();
            if (show)
                view_show(trim_time(g_trim.active == 0 ? g_trim.s : g_trim.e));
        }

        void trim_drag(int handle, float fraction) {
            if (!g_trim.ready)
                return;
            trim_refresh_fps();
            trim_move(handle, trim_index(std::clamp(double(fraction), 0.0, 1.0) * g_duration));
        }

        constexpr double kEdge = 0.04;

        void trim_edge_tick() {
            if (!g_trim.ready || g_trim.drag_handle < 0) {
                g_edge_timer.stop();
                return;
            }
            double pos = g_trim.drag_pos;
            double depth = pos < kEdge ? kEdge - pos : pos > 1 - kEdge ? pos - (1 - kEdge) : 0;
            if (depth <= 0) {
                g_edge_timer.stop();
                return;
            }
            double step = std::clamp(0.004 + depth * 0.25, 0.004, 0.06);
            view_pan(pos < kEdge ? -step : step);
            double at = g_trim.view_start + std::clamp(pos, 0.0, 1.0) * g_trim.view_span;
            trim_move(g_trim.drag_handle, trim_index(at), false);
        }

        void trim_drag_view(int handle, double pos) {
            if (!g_trim.ready || g_duration <= 0)
                return;
            trim_refresh_fps();
            g_trim.drag_handle = handle == 1 ? 1 : 0;
            g_trim.drag_pos = pos;
            double at = g_trim.view_start + std::clamp(pos, 0.0, 1.0) * g_trim.view_span;
            trim_move(g_trim.drag_handle, trim_index(at), false);
            if ((pos < kEdge || pos > 1 - kEdge) && !g_edge_timer.running())
                g_edge_timer.start(slint::TimerMode::Repeated, std::chrono::milliseconds(30), [] { trim_edge_tick(); });
        }

        void trim_drag_end() {
            g_trim.drag_handle = -1;
            g_edge_timer.stop();
        }

        void trim_select(int handle) {
            if (!g_trim.ready)
                return;
            if (g_trim.range)
                trim_stop_range(false);
            g_trim.active = handle == 1 ? 1 : 0;
            trim_seek(trim_shown(g_trim.active));
            publish_trim();
            view_show(trim_time(g_trim.active == 0 ? g_trim.s : g_trim.e));
        }

        void trim_nudge(int handle, long frames) {
            if (!g_trim.ready)
                return;
            trim_refresh_fps();
            trim_move(handle, (handle == 1 ? g_trim.e : g_trim.s) + frames);
        }

        void trim_step(int handle, int ms) {
            if (!g_trim.ready)
                return;
            trim_refresh_fps();
            long delta = long(std::llround(double(ms) / 1000.0 * g_trim.fps));
            if (delta == 0)
                delta = ms < 0 ? -1 : 1;
            trim_move(handle, (handle == 1 ? g_trim.e : g_trim.s) + delta);
        }

        void trim_play_range() {
            if (!g_trim.ready || !g_mpv)
                return;
            if (g_trim.range || !g_paused) {
                trim_stop_range(false);
                return;
            }
            trim_refresh_fps();
            view_show(trim_time(g_trim.s));
            trim_seek(trim_time(g_trim.s));
            g_trim.range = true;
            g_trim.follow = true;
            set("pause", "no");
            g_paused = false;
            publish_state();
        }

        void trim_check_range() {
            if (g_trim.range && g_position >= trim_shown(1) - 0.25 / g_trim.fps)
                trim_stop_range(true);
        }

        void trim_apply() {
            if (!g_trim.on || !g_trim.ready)
                return;
            long long start = std::llround(trim_time(g_trim.s) * 1000.0);
            long long end = std::llround(trim_time(g_trim.e) * 1000.0);
            std::string json = "{\"type\":\"VideoCutApply\",\"startMs\":" + std::to_string(start) + ",\"endMs\":" + std::to_string(end) + "}";
            trim_log("trim apply " + json);
            send_action(json);
            close();
        }

        void apply_queue(const std::string &queue_json) {
            fj::Json json;
            std::string error;
            if (!fj::parse_json(queue_json, json, error))
                return;
            g_items.clear();
            g_preparing.clear();
            g_release_after_end.clear();
            g_had_hidden = false;
            g_loaded = 0;
            g_prepare_deferred = false;
            if (const fj::Json *items = json.find("items")) {
                for (const auto &entry: items->items) {
                    Item item;
                    if (auto *v = entry.find("id")) item.id = v->text;
                    if (auto *v = entry.find("title")) item.title = v->text;
                    if (auto *v = entry.find("path")) item.path = v->text;
                    if (auto *v = entry.find("video")) item.video = v->boolean;
                    if (auto *v = entry.find("hidden")) item.hidden = v->boolean;
                    if (item.hidden)
                        g_had_hidden = true;
                    g_items.push_back(item);
                }
            }
            g_index = 0;
            if (auto *v = json.find("index")) g_index = int(v->number);
            g_key.clear();
            if (auto *v = json.find("groupKey")) g_key = v->text;
            int strip_w = g_trim.strip_w;
            int strip_h = g_trim.strip_h;
            g_trim = Trim();
            g_trim.strip_w = strip_w;
            g_trim.strip_h = strip_h;
            if (auto *v = json.find("trim")) g_trim.on = v->boolean;
            if (auto *v = json.find("cutTaskId")) g_trim.task = v->text;
            if (auto *v = json.find("startMs")) g_trim.start_ms = long(v->number);
            if (auto *v = json.find("endMs")) g_trim.end_ms = long(v->number);
            if (g_trim.on) {
                g_key.clear();
                for (int i = 0; i < int(g_items.size()); i++)
                    if (!g_trim.task.empty() && g_items[i].id == g_trim.task)
                        g_index = i;
            }
        }

    }

    void wire() {
        if (g_wired)
            return;
        g_wired = true;
        auto &p = ui().global<PlayerUi>();
        std::vector<slint::SharedString> labels;
        for (float speed: kSpeeds)
            labels.push_back(slint::SharedString(speed_label(speed)));
        p.set_speed_labels(std::make_shared<slint::VectorModel<slint::SharedString>>(labels));
        p.on_close([] { close(); });
        p.on_toggle_play([] { toggle_play(); });
        p.on_seek([](float seconds) { seek_to(double(seconds)); });
        p.on_skip([](int ms) {
            if (!g_mpv || g_finished)
                return;
            seek_to(g_position + double(ms) / 1000.0);
        });
        p.on_next([] {
            if (g_index + 1 < int(g_items.size()))
                load_item(g_index + 1, false);
        });
        p.on_previous([] { previous(); });
        p.on_pick([](int index) { load_item(index, false); });
        p.on_set_speed([](int index) {
            if (index < 0 || index >= int(sizeof kSpeeds / sizeof kSpeeds[0]))
                return;
            g_speed = kSpeeds[index];
            if (g_hold == 0)
                set("speed", number_text(g_speed).c_str());
            publish_state();
        });
        p.on_set_volume([](float value) { set_volume(double(value)); });
        p.on_toggle_mute([] { toggle_mute(); });
        p.on_set_autoplay([](bool on) { set_autoplay(on); });
        p.on_toggle_fullscreen([] { set_fullscreen(!g_fullscreen); });
        p.on_hold_start([](bool forward) { begin_hold(forward); });
        p.on_hold_end([] { end_hold(); });
        p.on_start_over([] { load_item(0, true); });
        p.on_trim_drag_view([](int handle, float pos) { trim_drag_view(handle, double(pos)); });
        p.on_trim_drag_end([] { trim_drag_end(); });
        p.on_trim_pan([](float fraction) {
            if (g_trim.ready)
                view_pan(double(fraction));
        });
        p.on_trim_zoom([](float factor, float anchor) {
            if (g_trim.ready)
                view_zoom(double(factor), double(anchor));
        });
        p.on_trim_overview([](float fraction) {
            if (g_trim.ready)
                view_center(double(fraction));
        });
        p.on_trim_seek([](float fraction) {
            if (!g_trim.ready)
                return;
            if (g_trim.range)
                trim_stop_range(false);
            trim_seek(std::clamp(double(fraction), 0.0, 1.0) * g_duration);
        });
        p.on_trim_select([](int handle) { trim_select(handle); });
        p.on_trim_nudge([](int handle, int frames) { trim_nudge(handle, frames); });
        p.on_trim_step([](int handle, int ms) { trim_step(handle, ms); });
        p.on_trim_play_range([] { trim_play_range(); });
        p.on_trim_apply([] { trim_apply(); });
        p.on_trim_cancel([] { close(); });
        p.on_trim_strip_size([](int w, int h) {
            g_trim.strip_w = w;
            g_trim.strip_h = h;
            start_thumbs();
        });
        p.on_controls_shown([](bool on) {
            g_controls_on = on;
            sub_margin();
        });
        p.on_sub_menu([] { publish_subs(); });
        p.on_sub_pick([](int key) { sub_pick(key); });
        p.on_surface_size([](int w, int h) {
            g_width = std::clamp(w, 16, 2560);
            g_height = std::clamp(h, 16, 1440);
            sub_margin();
            if (g_open && g_render) {
                g_force_render = true;
                post_frame();
            }
        });
    }

    void open(const std::string &queue_json) {
        wire();
        if (g_had_hidden && (g_open || g_mpv))
            close();
        apply_queue(queue_json);
        if (g_items.empty())
            return;
        g_index = std::clamp(g_index, 0, int(g_items.size()) - 1);
        g_speed = 1.0f;
        g_autoplay = core_call("player.autoplay", "{}") != "false";
        publish_sound();
        auto &p = ui().global<PlayerUi>();
        reset_tiles();
        g_thumbs = std::make_shared<slint::VectorModel<TrimTile>>();
        p.set_trim_thumbs(g_thumbs);
        g_wave.reset();
        p.set_trim_view_start(0);
        p.set_trim_view_span(1);
        p.set_trim_wave(std::make_shared<slint::VectorModel<float>>());
        p.set_trim_audio(false);
        p.set_trim_playhead(0);
        p.set_trim_ready(false);
        publish_trim();
        clear_frame();
        publish_items();
        publish_item();
        p.set_open(true);
        if (!start_engine()) {
            p.set_open(false);
            const Item &item = g_items[g_index];
            core_call("player.system", "{\"path\":" + quote(item.path) + ",\"id\":" + quote(item.id) +
                                       ",\"hidden\":" + (item.hidden ? "true" : "false") + "}");
            g_items.clear();
            g_had_hidden = false;
            g_model.reset();
            return;
        }
        g_open = true;
        load_item(g_index, !g_trim.on);
    }

    void close() {
        if (!g_open && !g_mpv)
            return;
        g_open = false;
        set_fullscreen(false);
        keep_awake(false);
        reset_tiles();
        g_trim = Trim();
        g_thumbs.reset();
        g_wave.reset();
        stop_engine();
        set_opening(false, 0);
        g_preparing.clear();
        g_release_after_end.clear();
        g_loaded = 0;
        g_prepare_deferred = false;
        sub_reset();
        g_notice_timer.stop();
        if (g_had_hidden) {
            g_had_hidden = false;
            send_action("{\"type\":\"PlayerClosed\"}");
        }
        auto &p = ui().global<PlayerUi>();
        publish_subs();
        p.set_notice(slint::SharedString());
        p.set_trim(false);
        p.set_trim_ready(false);
        p.set_trim_thumbs(std::make_shared<slint::VectorModel<TrimTile>>());
        p.set_trim_wave(std::make_shared<slint::VectorModel<float>>());
        p.set_trim_audio(false);
        p.set_open(false);
        p.set_playing(false);
        clear_frame();
        g_items.clear();
        g_model.reset();
        g_buffers[0] = slint::SharedPixelBuffer<slint::Rgb8Pixel>();
        g_buffers[1] = slint::SharedPixelBuffer<slint::Rgb8Pixel>();
    }

    void prepared(const std::string &id, const std::string &path) {
        bool current = g_open && !g_items.empty() && g_items[g_index].id == id && g_preparing == id;
        if (!current) {
            if (!path.empty())
                send_hidden("PlayerRelease", id);
            return;
        }
        g_preparing.clear();
        set_opening(false, 0);
        if (path.empty()) {
            item_failed();
            return;
        }
        g_items[g_index].path = path;
        load_item(g_index, g_preparing_autoplay);
    }

    void preparing(const std::string &id, int percent) {
        if (g_open && g_preparing == id)
            set_opening(true, std::clamp(percent, 0, 100));
    }

    void shutdown() {
        g_closing = true;
        if (g_open || g_mpv)
            close();
        cancel_thumbs();
        for (int waited = 0; g_workers.load() > 0 && waited < 5000; waited += 20)
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }

    void debug(const std::string &name, double value) {
        if (name == "close") {
            close();
        } else if (name == "pause") {
            toggle_play();
        } else if (name == "seek") {
            seek_to(value);
        } else if (name == "skip") {
            if (g_mpv)
                seek_to(g_position + value / 1000.0);
        } else if (name == "next") {
            if (g_index + 1 < int(g_items.size()))
                load_item(g_index + 1, false);
        } else if (name == "previous") {
            previous();
        } else if (name == "pick") {
            load_item(int(value), false);
        } else if (name == "speed") {
            int index = int(value);
            if (index >= 0 && index < int(sizeof kSpeeds / sizeof kSpeeds[0])) {
                g_speed = kSpeeds[index];
                if (g_hold == 0)
                    set("speed", number_text(g_speed).c_str());
                publish_state();
            }
        } else if (name == "holdfwd") {
            begin_hold(true);
        } else if (name == "holdback") {
            begin_hold(false);
        } else if (name == "holdend") {
            end_hold();
        } else if (name == "startover") {
            load_item(0, true);
        } else if (name == "trimtest") {
            if (!g_items.empty()) {
                Item item = g_items[g_index];
                std::string json = "{\"items\":[{\"id\":" + quote(item.id) + ",\"title\":" + quote(item.title) + ",\"path\":" +
                                   quote(item.path) + ",\"video\":" + (item.video ? "true" : "false") +
                                   "}],\"index\":0,\"groupKey\":\"\",\"trim\":true,\"cutTaskId\":" + quote(item.id) +
                                   ",\"startMs\":" + std::to_string(long(value * 1000)) + ",\"endMs\":0}";
                open(json);
            }
        } else if (name == "trimstart") {
            trim_drag(0, float(value));
        } else if (name == "trimend") {
            trim_drag(1, float(value));
        } else if (name == "trimnudge") {
            trim_nudge(g_trim.active, long(value));
        } else if (name == "trimstep") {
            trim_step(g_trim.active, int(value));
        } else if (name == "trimselect") {
            g_trim.active = int(value) == 1 ? 1 : 0;
            publish_trim();
            view_show(trim_time(g_trim.active == 0 ? g_trim.s : g_trim.e));
        } else if (name == "trimpan") {
            if (g_trim.ready)
                view_pan(value);
        } else if (name == "trimzoom") {
            if (g_trim.ready)
                view_zoom(value, 0.5);
        } else if (name == "trimoverview") {
            if (g_trim.ready)
                view_center(value);
        } else if (name == "trimdragview") {
            trim_drag_view(g_trim.active, value);
        } else if (name == "trimdragend") {
            trim_drag_end();
        } else if (name == "trimseek") {
            if (g_trim.ready)
                trim_seek(value);
        } else if (name == "trimplay") {
            trim_play_range();
        } else if (name == "trimapply") {
            trim_apply();
        } else if (name == "trimcancel") {
            close();
        } else if (name == "queuetest") {
            if (!g_items.empty()) {
                Item item = g_items[g_index];
                std::string one = "{\"id\":" + quote(item.id) + ",\"title\":" + quote(item.title) + ",\"path\":" + quote(item.path) +
                                  ",\"video\":" + (item.video ? "true" : "false") + "}";
                open("{\"items\":[" + one + "," + one + "],\"index\":0,\"groupKey\":\"\"}");
            }
        } else if (name == "queuepair") {
            const char *second = std::getenv("FJ_PLAYER_QUEUE_NEXT");
            if (!g_items.empty() && second && *second) {
                Item item = g_items[g_index];
                std::string one = "{\"id\":" + quote(item.id) + ",\"title\":" + quote(item.title) + ",\"path\":" + quote(item.path) +
                                  ",\"video\":" + (item.video ? "true" : "false") + "}";
                open("{\"items\":[" + one + "," + second + "],\"index\":0,\"groupKey\":\"\"}");
            }
        } else if (name == "autoplay") {
            set_autoplay(value != 0);
        } else if (name == "volume") {
            set_volume(value);
        } else if (name == "mute") {
            toggle_mute();
        } else if (name == "fullscreen") {
            set_fullscreen(value != 0);
        } else if (name == "submenu") {
            show_sub_menu();
        } else if (name == "subpick") {
            sub_pick(int(value));
        } else if (name == "subopen") {
            if (const char *file = std::getenv("FJ_PLAYER_SUB_FILE"))
                sub_add_file(file);
        }
        const char *path = std::getenv("FJ_PLAYER_LOG");
        if (!path)
            return;
        if (FILE *file = std::fopen(path, "a")) {
            std::fprintf(file, "%s %g -> open=%d index=%d/%zu pos=%.2f dur=%.2f paused=%d speed=%g hold=%d finished=%d failed=%d preparing=%s hidden=%d path=%s\n",
                         name.c_str(), value, g_open ? 1 : 0, g_index, g_items.size(), g_position, g_duration,
                         g_paused ? 1 : 0, double(g_speed), g_hold, g_finished ? 1 : 0, g_failed ? 1 : 0, g_preparing.c_str(),
                         g_items.empty() ? 0 : (g_items[g_index].hidden ? 1 : 0), g_items.empty() ? "" : g_items[g_index].path.c_str());
            double mpv_volume = -1;
            int mpv_mute = -1;
            if (g_mpv) {
                g_api.get_property(g_mpv, "volume", MPV_FORMAT_DOUBLE, &mpv_volume);
                g_api.get_property(g_mpv, "mute", MPV_FORMAT_FLAG, &mpv_mute);
            }
            auto &ui_player = ui().global<PlayerUi>();
            std::fprintf(file, "  sound autoplay=%d volume=%g muted=%d mpvvolume=%g mpvmute=%d uivolume=%g uiautoplay=%d fullscreen=%d playing=%d\n",
                         g_autoplay ? 1 : 0, g_volume, g_muted ? 1 : 0, mpv_volume, mpv_mute, double(ui_player.get_volume()),
                         ui_player.get_autoplay() ? 1 : 0, g_fullscreen ? 1 : 0, ui_player.get_playing() ? 1 : 0);
            if (name.compare(0, 3, "sub") == 0) {
                std::fprintf(file, "  subs checked=%d on=%d uion=%d uichecked=%d choice=%s files=%zu notice=%s\n", sub_checked(), sub_checked() != kSubOff ? 1 : 0,
                             ui().global<PlayerUi>().get_sub_on() ? 1 : 0, ui().global<PlayerUi>().get_sub_checked(),
                             g_sub_choice.c_str(), g_sub_files.size(), plain(ui().global<PlayerUi>().get_notice()).c_str());
                for (int i = 0; i < int(g_sub_tracks.size()); i++)
                    std::fprintf(file, "  sub key=%d id=%lld external=%d selected=%d label=%s file=%s\n", kSubFirst + i, g_sub_tracks[i].id,
                                 g_sub_tracks[i].external ? 1 : 0, g_sub_tracks[i].selected ? 1 : 0, sub_label(g_sub_tracks[i]).c_str(),
                                 g_sub_tracks[i].file.c_str());
                auto items = ui().global<PlayerUi>().get_sub_items();
                for (size_t i = 0; i < items->row_count(); i++) {
                    auto row = *items->row_data(i);
                    std::fprintf(file, "  menu key=%d header=%s label=%s divider=%d\n", row.key, plain(row.header).c_str(), plain(row.label).c_str(),
                                 row.divider ? 1 : 0);
                }
            }
            if (g_trim.on)
                std::fprintf(file, "  trim ready=%d fps=%.3f%s frames=%ld s=%ld e=%ld start=%s end=%s active=%d range=%d thumbs=%d/%zu audio=%d wave=%zu"
                                   " view=%.3f span=%.3f zoomin=%d zoomout=%d strip=%d tile=%g made=%d slice=%zu group=%d pos=%.3f edge=%d\n",
                             g_trim.ready ? 1 : 0, g_trim.fps, g_trim.fps_guess ? "?" : "", g_trim.frames, g_trim.s, g_trim.e,
                             precise_label(trim_time(g_trim.s)).c_str(), precise_label(trim_time(g_trim.e)).c_str(), g_trim.active,
                             g_trim.range ? 1 : 0, g_thumbs_loaded, g_thumbs ? size_t(g_thumbs->row_count()) : size_t(0), g_trim.audio ? 1 : 0,
                             g_wave_all.size(), g_trim.view_start, g_trim.view_span, g_trim.view_span > view_min_span() + 1e-6 ? 1 : 0,
                             g_trim.view_span < g_duration - 1e-6 ? 1 : 0, g_trim.strip_w, std::ldexp(1.0, g_tile_level), g_thumb_made.load(),
                             g_wave ? size_t(g_wave->row_count()) : size_t(0), g_wave_group, g_position, g_edge_timer.running() ? 1 : 0);
            std::fclose(file);
        }
    }

}
