#include "app_host.h"
#include "emoji_files.h"

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <map>
#include <optional>
#include <set>
#include <sstream>
#include <string>
#include <vector>

namespace fjhost {

    namespace {

        CoreApi g_core;
        std::optional<slint::ComponentHandle<AppWindow>> g_ui;
        fjgen::Models g_models;
        bool g_running = false;
        std::vector<std::string> g_script;
        size_t g_script_next = 0;
        slint::Timer g_script_timer;
        bool g_bridge = true;
        std::string g_bridge_port;
        bool g_shown = false;

        std::map<std::string, slint::Image> g_emoji_files;
        std::set<std::string> g_emoji_absent;

        std::filesystem::path utf8_path(const std::string &text) {
            return std::filesystem::path(std::u8string(reinterpret_cast<const char8_t *>(text.data()), text.size()));
        }

        void register_fonts() {
            std::error_code error;
            std::filesystem::path folder = utf8_path(os::exe_folder() + "fonts");
            for (const auto &entry: std::filesystem::directory_iterator(folder, error)) {
                std::u8string name = entry.path().u8string();
                std::string path(reinterpret_cast<const char *>(name.data()), name.size());
                if (path.size() < 4 || (path.compare(path.size() - 4, 4, ".ttf") != 0 && path.compare(path.size() - 4, 4, ".otf") != 0))
                    continue;
                auto failed = ui().window().window_handle().register_font_from_path(slint::SharedString(path));
                if (failed)
                    std::fprintf(stderr, "font %s not registered: %s\n", path.c_str(), std::string(failed->data(), failed->size()).c_str());
            }
        }

        slint::Image emoji_icon(const slint::SharedString &glyph, int size) {
            std::string key = fj::emoji_file_name(glyph.data(), glyph.size(), size);
            if (!key.empty()) {
                auto found = g_emoji_files.find(key);
                if (found != g_emoji_files.end())
                    return found->second;
                if (!g_emoji_absent.count(key)) {
                    std::string path = os::exe_folder() + "emoji/" + key;
                    std::error_code error;
                    if (std::filesystem::exists(utf8_path(path), error)) {
                        slint::Image image = slint::Image::load_from_path(slint::SharedString(path));
                        if (image.size().width > 0) {
                            g_emoji_files.emplace(key, image);
                            return image;
                        }
                    }
                    g_emoji_absent.insert(key);
                }
            }
            return os::emoji_fallback(glyph, size);
        }

        std::string take(char *text) {
            std::string value = text ? text : "";
            if (text)
                g_core.release(text);
            return value;
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

        void handle_effects(const fj::Json &json) {
            for (const EffectDto &effect: fjgen::parse_effects_queue(json)) {
                switch (effect.kind) {
                    case fjgen::EffectKind::ShowWindow:
                        show_window(true);
                        break;
                    case fjgen::EffectKind::ScrollToRow:
                        ui().invoke_scroll_to_top();
                        break;
                    case fjgen::EffectKind::Notify:
                        os::show_notice(plain(effect.title), plain(effect.text));
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
            auto &window = ui();
            if (channel == fjgen::Channel::Tr) {
                fjgen::apply_tr(window.global<Tr>(), json);
                window.global<Dir>().set_rtl(fj::flag(json, "rtl"));
                window.global<Theme>().set_font(fj::text(json, "font"));
                return;
            }
            if (channel == fjgen::Channel::Effects) {
                handle_effects(json);
                return;
            }
            fjgen::apply_channel(window.global<Core>(), g_models, channel, json);
            if (channel == fjgen::Channel::Shell) {
                bool dark = window.global<Core>().get_shell().dark;
                window.global<Theme>().set_dark(dark);
                os::apply_title_bar(dark);
                os::update_tray_tip(plain(window.global<Core>().get_shell().trayTooltip));
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

        char *host_service(const char *name, const char *args_json) {
            std::string service = name ? name : "";
            fj::Json args;
            std::string error;
            fj::parse_json(args_json ? args_json : "{}", args, error);
            auto arg = [&args](const char *key) {
                const fj::Json *found = args.find(key);
                return found ? found->text : std::string();
            };
            std::string result;
            if (service == "pickFolder")
                result = os::pick_path(true, arg("title"), arg("initialPath"), "", "");
            else if (service == "pickFile")
                result = os::pick_path(false, arg("title"), "", arg("filterName"), arg("pattern"));
            return os::copy_for_core(result);
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

        std::string start_config(int argc, char **argv, bool &can_focus) {
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
                    can_focus = false;
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
                    std::istringstream file(os::read_text_file(argv[++i]));
                    std::string line;
                    while (std::getline(file, line)) {
                        if (!line.empty() && line.back() == '\r')
                            line.pop_back();
                        if (!line.empty() && line[0] == '{')
                            g_script.push_back(line);
                    }
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

        bool g_can_focus = true;

    }

    AppWindow &ui() {
        return **g_ui;
    }

    bool has_ui() {
        return g_ui.has_value();
    }

    std::string plain(const slint::SharedString &text) {
        return std::string(text.data(), text.size());
    }

    void send_action(const std::string &json) {
        if (g_running)
            g_core.action(json.c_str());
    }

    void show_window(bool focus) {
        ui().show();
        g_shown = true;
        os::apply_title_bar(ui().global<Core>().get_shell().dark);
        os::bring_to_front(focus && g_can_focus);
    }

    void quit_app() {
        os::remove_tray();
        slint::quit_event_loop();
    }

    void open_external(const std::string &url) {
        if (!g_core.call || url.empty())
            return;
        std::string args = "{\"url\":" + json_string(url) + "}";
        take(g_core.call("openUrl", args.c_str()));
    }

    void hide_window() {
        if (!os::tray_on()) {
            quit_app();
            return;
        }
        ui().hide();
        g_shown = false;
        os::window_hidden();
    }

    void toggle_window() {
        if (os::toggle_too_soon())
            return;
        if (g_shown && !os::window_minimized())
            hide_window();
        else
            show_window(true);
    }

    int run(int argc, char **argv) {
        bool handled = false;
        int child = os::child_main(argc, argv, handled);
        if (handled)
            return child;
        os::prepare_process();
        if (!os::load_core(g_core)) {
            os::fatal("The FetchJet core library could not be loaded.");
            return 2;
        }
        std::string config = start_config(argc, argv, g_can_focus);
        bool second = false;
        void *instance = g_bridge ? os::claim_instance(g_bridge_port, second) : nullptr;
        fj::Json launch;
        std::string error;
        fj::parse_json(take(g_core.prepare((config + (second ? ",\"secondInstance\":true}" : "}")).c_str())), launch, error);
        if (fj::flag(launch, "exit")) {
            os::release_instance(instance);
            return 0;
        }
        bool start_hidden = fj::flag(launch, "hidden");

        g_ui = AppWindow::create();
        auto &window = ui();
        register_fonts();
        window.global<Emoji>().on_icon([](slint::SharedString glyph, int size, int) { return emoji_icon(glyph, size); });
        window.global<Emoji>().set_ready(true);
        fjgen::bind_models(window.global<Core>(), g_models);
        fjgen::bind_actions(window.global<Act>(), [](const std::string &json) { send_action(json); });
        window.window().on_close_requested([] {
            if (!os::tray_on()) {
                quit_app();
                return slint::CloseRequestResponse::HideWindow;
            }
            g_shown = false;
            os::window_hidden();
            return slint::CloseRequestResponse::HideWindow;
        });

        int started = g_core.start((config + "}").c_str(), post_from_core, notify_from_core, host_service, host_free);
        if (started != 0) {
            os::fatal("The download core could not start.");
            os::release_instance(instance);
            return 3;
        }
        g_running = true;
        send_action(std::string("{\"type\":\"SystemTheme\",\"dark\":") + (os::system_uses_dark() ? "true" : "false") + "}");
        for (int channel = 0; channel < fjgen::Channel::Count; channel++)
            apply_channel(channel);

        os::start_shell();
        os::start_emoji(take(g_core.call("home", "")));
        if (!g_script.empty())
            g_script_timer.start(slint::TimerMode::Repeated, std::chrono::milliseconds(500), run_script_step);
        if (!start_hidden || !os::tray_on())
            show_window(false);
        slint::run_event_loop(slint::EventLoopMode::RunUntilQuit);

        g_running = false;
        os::remove_tray();
        g_core.shutdown();
        g_ui.reset();
        os::release_instance(instance);
        return 0;
    }

}

int main(int argc, char **argv) {
    return fjhost::run(argc, argv);
}
