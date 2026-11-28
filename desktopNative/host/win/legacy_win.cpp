#include <string>

namespace fjhost::os {

    std::string store_updates() {
        return "FJSTORE ";
    }

    bool auth_available() {
        return false;
    }

    bool auth_verify(const std::string &) {
        return false;
    }

}
