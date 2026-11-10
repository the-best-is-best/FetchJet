#pragma once

#include <string>
#include <vector>

namespace fjhost::os::tray {

    struct Menu {
        std::string tooltip;
        std::string show;
        std::string close;
        std::string when_done;
        std::vector<std::string> options;
        int index = 0;
        bool when_done_on = false;
        bool rtl = false;

        bool operator==(const Menu &other) const = default;
    };

    bool start(const std::string &icon_folder);
    bool on();
    void update(const Menu &menu);
    void stop();

}
