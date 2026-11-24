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
#include <memory>
#include <string>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

namespace fjhost::player {

    namespace {

        struct Item {
            std::string id;
            std::string title;
            std::string path;
            bool video = false;
        };

        struct Api {
            mpv_handle *(*create)() = nullptr;
            int (*initialize)(mpv_handle *) = nullptr;
            int (*set_option_string)(mpv_handle *, const char *, const char *) = nullptr;
            int (*set_property_string)(mpv_handle *, const char *, const char *) = nullptr;
            int (*command)(mpv_handle *, const char **) = nullptr;
            int (*command_async)(mpv_handle *, uint64_t, const char **) = nullptr;
            int (*observe_property)(mpv_handle *, uint64_t, const char *, mpv_format) = nullptr;
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
            ObsPosition = 1, ObsDuration, ObsPause, ObsEof
        };

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
        long g_label_second = -1;
        std::atomic<unsigned> g_generation{0};
        std::atomic<bool> g_event_posted{false};
        std::atomic<bool> g_frame_posted{false};
        slint::Timer g_rewind_timer;
        slint::SharedPixelBuffer<slint::Rgb8Pixel> g_buffers[2];
        int g_buffer_next = 0;
        std::shared_ptr<slint::VectorModel<PlayerRow>> g_model;

        void drain_events();

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
            std::vector<std::string> names = {tools + "libmpv-2.dll", folder + "libmpv-2.dll", "libmpv-2.dll"};
            for (const auto &name: names) {
                int length = MultiByteToWideChar(CP_UTF8, 0, name.c_str(), -1, nullptr, 0);
                std::wstring wide(length > 0 ? length - 1 : 0, L'\0');
                if (length > 1)
                    MultiByteToWideChar(CP_UTF8, 0, name.c_str(), -1, wide.data(), length);
                g_lib = LoadLibraryW(wide.c_str());
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

        void set(const char *name, const char *value) {
            command_async({"set", name, value});
        }

        std::string number_text(double value) {
            char text[32];
            std::snprintf(text, sizeof text, "%.3f", value);
            return text;
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

        void load_item(int index, bool autoplay) {
            if (g_items.empty())
                return;
            g_rewind_timer.stop();
            g_hold = 0;
            g_index = std::clamp(index, 0, int(g_items.size()) - 1);
            g_finished = false;
            g_failed = false;
            g_seek_pending = false;
            g_position = 0;
            g_duration = 0;
            g_label_second = -1;
            g_paused = !autoplay;
            set("speed", number_text(g_speed).c_str());
            set("pause", autoplay ? "no" : "yes");
            command_async({"loadfile", g_items[g_index].path.c_str(), "replace"});
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
                load_item(g_index + 1, false);
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
                            }
                        } else if (event->reply_userdata == ObsDuration && prop->format == MPV_FORMAT_DOUBLE) {
                            g_duration = *static_cast<double *>(prop->data);
                            publish_time(true);
                        } else if (event->reply_userdata == ObsPause && prop->format == MPV_FORMAT_FLAG) {
                            g_paused = *static_cast<int *>(prop->data) != 0;
                            publish_state();
                        } else if (event->reply_userdata == ObsEof && prop->format == MPV_FORMAT_FLAG) {
                            if (*static_cast<int *>(prop->data) != 0 && !g_finished)
                                end_of_item();
                        }
                        break;
                    }
                    case MPV_EVENT_PLAYBACK_RESTART:
                        g_seek_pending = false;
                        break;
                    case MPV_EVENT_END_FILE: {
                        auto *end = static_cast<mpv_event_end_file *>(event->data);
                        if (end && end->reason == MPV_END_FILE_REASON_ERROR)
                            item_failed();
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
                    {"terminal", "no"}};
            for (const auto &option: options)
                g_api.set_option_string(g_mpv, option[0], option[1]);
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
            unload_library();
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

        void apply_queue(const std::string &queue_json) {
            fj::Json json;
            std::string error;
            if (!fj::parse_json(queue_json, json, error))
                return;
            g_items.clear();
            if (const fj::Json *items = json.find("items")) {
                for (const auto &entry: items->items) {
                    Item item;
                    if (auto *v = entry.find("id")) item.id = v->text;
                    if (auto *v = entry.find("title")) item.title = v->text;
                    if (auto *v = entry.find("path")) item.path = v->text;
                    if (auto *v = entry.find("video")) item.video = v->boolean;
                    g_items.push_back(item);
                }
            }
            g_index = 0;
            if (auto *v = json.find("index")) g_index = int(v->number);
            g_key.clear();
            if (auto *v = json.find("groupKey")) g_key = v->text;
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
        p.on_hold_start([](bool forward) { begin_hold(forward); });
        p.on_hold_end([] { end_hold(); });
        p.on_start_over([] { load_item(0, true); });
        p.on_surface_size([](int w, int h) {
            g_width = std::clamp(w, 16, 2560);
            g_height = std::clamp(h, 16, 1440);
            if (g_open && g_render) {
                g_force_render = true;
                post_frame();
            }
        });
    }

    void open(const std::string &queue_json) {
        wire();
        apply_queue(queue_json);
        if (g_items.empty())
            return;
        g_index = std::clamp(g_index, 0, int(g_items.size()) - 1);
        g_speed = 1.0f;
        auto &p = ui().global<PlayerUi>();
        clear_frame();
        publish_items();
        publish_item();
        p.set_open(true);
        if (!start_engine()) {
            p.set_open(false);
            core_call("player.system", "{\"path\":" + quote(g_items[g_index].path) + "}");
            g_items.clear();
            g_model.reset();
            return;
        }
        g_open = true;
        load_item(g_index, true);
    }

    void close() {
        if (!g_open && !g_mpv)
            return;
        g_open = false;
        keep_awake(false);
        stop_engine();
        auto &p = ui().global<PlayerUi>();
        p.set_open(false);
        p.set_playing(false);
        clear_frame();
        g_items.clear();
        g_model.reset();
        g_buffers[0] = slint::SharedPixelBuffer<slint::Rgb8Pixel>();
        g_buffers[1] = slint::SharedPixelBuffer<slint::Rgb8Pixel>();
    }

    void shutdown() {
        if (g_open || g_mpv)
            close();
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
        }
        const char *path = std::getenv("FJ_PLAYER_LOG");
        if (!path)
            return;
        if (FILE *file = std::fopen(path, "a")) {
            std::fprintf(file, "%s %g -> open=%d index=%d/%zu pos=%.2f dur=%.2f paused=%d speed=%g hold=%d finished=%d failed=%d\n",
                         name.c_str(), value, g_open ? 1 : 0, g_index, g_items.size(), g_position, g_duration,
                         g_paused ? 1 : 0, double(g_speed), g_hold, g_finished ? 1 : 0, g_failed ? 1 : 0);
            std::fclose(file);
        }
    }

}
