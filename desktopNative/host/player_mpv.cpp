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
        };

        struct ThumbJob {
            std::atomic<bool> cancel{false};
            std::atomic<int> next{0};
            std::string ffmpeg;
            std::string input;
            std::string folder;
            double duration = 0;
            int count = 0;
            int width = 0;
            int height = 0;
            unsigned tag = 0;
        };

        Trim g_trim;
        std::shared_ptr<slint::VectorModel<slint::Image>> g_thumbs;
        std::shared_ptr<ThumbJob> g_thumb_job;
        std::atomic<unsigned> g_thumb_tag{0};
        int g_thumbs_loaded = 0;

        void drain_events();

        void trim_init();

        void trim_frames();

        void publish_trim();

        void publish_playhead();

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
                                }
                            }
                        } else if (event->reply_userdata == ObsPause && prop->format == MPV_FORMAT_FLAG) {
                            g_paused = *static_cast<int *>(prop->data) != 0;
                            publish_state();
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
                    case MPV_EVENT_END_FILE: {
                        auto *end = static_cast<mpv_event_end_file *>(event->data);
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
            return "ffmpeg";
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

        void thumb_worker(std::shared_ptr<ThumbJob> job) {
            for (;;) {
                int i = job->next.fetch_add(1);
                if (i >= job->count || job->cancel.load())
                    return;
                double at = (double(i) + 0.5) * job->duration / double(job->count);
                std::string out = job->folder + "/thumb-" + std::to_string(job->tag) + "-" + std::to_string(i) + ".rgb";
                std::string size = std::to_string(job->width) + ":" + std::to_string(job->height);
                std::vector<std::string> args = {job->ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-threads", "2",
                                                 "-ss", number_text(at), "-i", job->input, "-an", "-sn", "-dn", "-frames:v", "1",
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
                slint::invoke_from_event_loop([tag, i, w, h, pixels] {
                    if (tag != g_thumb_tag.load() || !g_thumbs || i >= int(g_thumbs->row_count()))
                        return;
                    slint::SharedPixelBuffer<slint::Rgb8Pixel> buffer{uint32_t(w), uint32_t(h)};
                    std::copy(pixels.begin(), pixels.end(), reinterpret_cast<uint8_t *>(buffer.begin()));
                    g_thumbs->set_row_data(size_t(i), slint::Image(buffer));
                    g_thumbs_loaded++;
                });
            }
        }

        void cancel_thumbs() {
            g_thumb_tag++;
            if (g_thumb_job)
                g_thumb_job->cancel = true;
            g_thumb_job.reset();
        }

        void start_thumbs() {
            if (!g_trim.on || !g_trim.ready || g_trim.thumbs_started || g_duration <= 0 || g_trim.strip_w <= 0 || g_trim.strip_h <= 0)
                return;
            if (g_items.empty() || !g_items[g_index].video)
                return;
            g_trim.thumbs_started = true;
            int height = std::clamp(g_trim.strip_h, 16, 240);
            int count = std::clamp(int(std::lround(double(g_trim.strip_w) / (double(height) * 16.0 / 9.0))), 4, 40);
            int width = std::max(8, (g_trim.strip_w + count - 1) / count);
            cancel_thumbs();
            std::vector<slint::Image> empty(static_cast<size_t>(count));
            g_thumbs = std::make_shared<slint::VectorModel<slint::Image>>(empty);
            g_thumbs_loaded = 0;
            ui().global<PlayerUi>().set_trim_thumbs(g_thumbs);
            std::string home = home_folder();
            if (home.empty())
                return;
            make_folder(home + "cache");
            make_folder(home + "cache/player-thumbs");
            auto job = std::make_shared<ThumbJob>();
            job->ffmpeg = find_ffmpeg();
            job->input = g_items[g_index].path;
            job->folder = home + "cache/player-thumbs";
            job->duration = g_duration;
            job->count = count;
            job->width = width;
            job->height = height;
            job->tag = g_thumb_tag.load();
            g_thumb_job = job;
            trim_log("trim thumbs " + std::to_string(count) + " of " + std::to_string(width) + "x" + std::to_string(height) + " with " + job->ffmpeg);
            unsigned workers = std::clamp(std::thread::hardware_concurrency() / 4, 1u, 3u);
            for (unsigned i = 0; i < workers; i++)
                std::thread(thumb_worker, job).detach();
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
            if (g_trim.on)
                ui().global<PlayerUi>().set_trim_playhead(g_duration > 0 ? float(std::clamp(g_position / g_duration, 0.0, 1.0)) : 0.0f);
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
            g_trim.fps_guess = true;
            g_trim.fps = 30;
            trim_frames();
            g_trim.s = trim_index(double(g_trim.start_ms) / 1000.0);
            g_trim.e = g_trim.end_ms > 0 ? trim_index(double(g_trim.end_ms) / 1000.0) : g_trim.frames;
            trim_refresh_fps();
            if (g_trim.end_ms <= 0)
                g_trim.e = g_trim.frames;
            g_trim.s = std::clamp(g_trim.s, 0L, g_trim.frames - 1);
            g_trim.e = std::clamp(g_trim.e, g_trim.s + 1, g_trim.frames);
            trim_seek(trim_shown(0));
            publish_trim();
            start_thumbs();
        }

        void trim_move(int handle, long index) {
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
        }

        void trim_drag(int handle, float fraction) {
            if (!g_trim.ready)
                return;
            trim_refresh_fps();
            trim_move(handle, trim_index(std::clamp(double(fraction), 0.0, 1.0) * g_duration));
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
            trim_seek(trim_time(g_trim.s));
            g_trim.range = true;
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
            g_trim = Trim();
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
        p.on_hold_start([](bool forward) { begin_hold(forward); });
        p.on_hold_end([] { end_hold(); });
        p.on_start_over([] { load_item(0, true); });
        p.on_trim_drag([](int handle, float fraction) { trim_drag(handle, fraction); });
        p.on_trim_seek([](float fraction) {
            if (!g_trim.ready)
                return;
            if (g_trim.range)
                trim_stop_range(false);
            trim_seek(std::clamp(double(fraction), 0.0, 1.0) * g_duration);
        });
        p.on_trim_select([](int handle) {
            if (!g_trim.ready)
                return;
            if (g_trim.range)
                trim_stop_range(false);
            g_trim.active = handle == 1 ? 1 : 0;
            trim_seek(trim_shown(g_trim.active));
            publish_trim();
        });
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
        cancel_thumbs();
        g_thumbs = std::make_shared<slint::VectorModel<slint::Image>>();
        g_thumbs_loaded = 0;
        p.set_trim_thumbs(g_thumbs);
        p.set_trim_playhead(0);
        p.set_trim_ready(false);
        publish_trim();
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
        load_item(g_index, !g_trim.on);
    }

    void close() {
        if (!g_open && !g_mpv)
            return;
        g_open = false;
        keep_awake(false);
        cancel_thumbs();
        g_trim = Trim();
        g_thumbs.reset();
        stop_engine();
        auto &p = ui().global<PlayerUi>();
        p.set_trim(false);
        p.set_trim_ready(false);
        p.set_trim_thumbs(std::make_shared<slint::VectorModel<slint::Image>>());
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
        } else if (name == "trimplay") {
            trim_play_range();
        } else if (name == "trimapply") {
            trim_apply();
        } else if (name == "trimcancel") {
            close();
        }
        const char *path = std::getenv("FJ_PLAYER_LOG");
        if (!path)
            return;
        if (FILE *file = std::fopen(path, "a")) {
            std::fprintf(file, "%s %g -> open=%d index=%d/%zu pos=%.2f dur=%.2f paused=%d speed=%g hold=%d finished=%d failed=%d\n",
                         name.c_str(), value, g_open ? 1 : 0, g_index, g_items.size(), g_position, g_duration,
                         g_paused ? 1 : 0, double(g_speed), g_hold, g_finished ? 1 : 0, g_failed ? 1 : 0);
            if (g_trim.on)
                std::fprintf(file, "  trim ready=%d fps=%.3f%s frames=%ld s=%ld e=%ld start=%s end=%s active=%d range=%d thumbs=%d/%zu\n",
                             g_trim.ready ? 1 : 0, g_trim.fps, g_trim.fps_guess ? "?" : "", g_trim.frames, g_trim.s, g_trim.e,
                             precise_label(trim_time(g_trim.s)).c_str(), precise_label(trim_time(g_trim.e)).c_str(), g_trim.active,
                             g_trim.range ? 1 : 0, g_thumbs_loaded, g_thumbs ? size_t(g_thumbs->row_count()) : size_t(0));
            std::fclose(file);
        }
    }

}
