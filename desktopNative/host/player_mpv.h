#pragma once

#include <string>

namespace fjhost::player {

    void wire();
    void open(const std::string &queue_json);
    void close();
    void shutdown();
    void debug(const std::string &name, double value);

}
