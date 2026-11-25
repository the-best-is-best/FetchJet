#pragma once

#include <string>

namespace fjhost::pdf {

    void wire();
    void open(const std::string &json);
    void close();
    void shutdown();
    void debug(const std::string &name, double value);

}
