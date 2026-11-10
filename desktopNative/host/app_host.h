#pragma once

#include "generated/fj_bridge.h"
#include "core_api.h"

#include <string>

namespace fjhost {

    AppWindow &ui();
    bool has_ui();
    void send_action(const std::string &json);
    void show_window(bool focus);
    void hide_window();
    void toggle_window();
    void quit_app();
    std::string plain(const slint::SharedString &text);

    namespace os {
        void prepare_process();
        bool load_core(CoreApi &core);
        void fatal(const std::string &text);
        bool system_uses_dark();
        std::string read_text_file(const std::string &utf8_path);

        void *claim_instance(const std::string &bridge_port, bool &second);
        void release_instance(void *instance);

        void apply_title_bar(bool dark);
        void bring_to_front(bool focus);
        void window_hidden();
        bool window_minimized();
        bool toggle_too_soon();

        void start_shell();
        bool tray_on();
        void update_tray_tip(const std::string &tip);
        void remove_tray();
        void show_notice(const std::string &title, const std::string &text);

        void start_emoji(const std::string &home);
        slint::Image emoji_fallback(const slint::SharedString &glyph, int size);
        std::string exe_folder();

        std::string pick_path(bool folder, const std::string &title, const std::string &initial,
                              const std::string &filter_name, const std::string &pattern);
        char *copy_for_core(const std::string &text);

        int child_main(int argc, char **argv, bool &handled);
    }

}
