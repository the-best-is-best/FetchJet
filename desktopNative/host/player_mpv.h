#pragma once

#include <string>

namespace fjhost::player {

    void wire();
    void open(const std::string &queue_json);
    void close();
    void prepared(const std::string &id, const std::string &path);
    void preparing(const std::string &id, int percent);
    void shutdown();
    void debug(const std::string &name, double value);

}
