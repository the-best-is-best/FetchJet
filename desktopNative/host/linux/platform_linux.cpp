#include "../app_host.h"
#include "tray_linux.h"

#include <dlfcn.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <spawn.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#include <cerrno>
#include <chrono>
#include <vector>

#include <climits>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <thread>

extern char **environ;

namespace fjhost::os {

    namespace {

        std::string executable_folder() {
            char path[PATH_MAX + 1];
            ssize_t length = readlink("/proc/self/exe", path, PATH_MAX);
            if (length <= 0)
                return "./";
            std::string full(path, size_t(length));
            return full.substr(0, full.find_last_of('/') + 1);
        }

        template<typename T>
        T symbol(void *library, const char *name) {
            return reinterpret_cast<T>(dlsym(library, name));
        }

        bool on_path(const std::string &command) {
            const char *path = std::getenv("PATH");
            if (!path)
                return false;
            std::string all(path);
            size_t start = 0;
            while (start <= all.size()) {
                size_t end = all.find(':', start);
                if (end == std::string::npos)
                    end = all.size();
                std::string folder = all.substr(start, end - start);
                if (!folder.empty()) {
                    std::string candidate = folder + "/" + command;
                    struct stat info {};
                    if (stat(candidate.c_str(), &info) == 0 && S_ISREG(info.st_mode) && access(candidate.c_str(), X_OK) == 0)
                        return true;
                }
                start = end + 1;
            }
            return false;
        }

        std::vector<char *> argv_of(std::vector<std::string> &command) {
            std::vector<char *> argv;
            for (auto &part: command)
                argv.push_back(part.data());
            argv.push_back(nullptr);
            return argv;
        }

        bool run_capture(std::vector<std::string> command, int timeout_ms, std::string &output, int &exit_code) {
            int fds[2];
            if (pipe2(fds, O_CLOEXEC) != 0)
                return false;
            posix_spawn_file_actions_t actions;
            posix_spawn_file_actions_init(&actions);
            posix_spawn_file_actions_addopen(&actions, 0, "/dev/null", O_RDONLY, 0);
            posix_spawn_file_actions_adddup2(&actions, fds[1], 1);
            posix_spawn_file_actions_addopen(&actions, 2, "/dev/null", O_WRONLY, 0);
            auto argv = argv_of(command);
            pid_t pid = 0;
            int started = posix_spawnp(&pid, argv[0], &actions, nullptr, argv.data(), environ);
            posix_spawn_file_actions_destroy(&actions);
            close(fds[1]);
            if (started != 0) {
                close(fds[0]);
                return false;
            }
            auto end = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout_ms);
            char buffer[4096];
            bool timed_out = false;
            while (true) {
                long left = timeout_ms < 0 ? -1 : long(std::chrono::duration_cast<std::chrono::milliseconds>(end - std::chrono::steady_clock::now()).count());
                if (timeout_ms >= 0 && left <= 0) {
                    timed_out = true;
                    break;
                }
                pollfd wait{fds[0], POLLIN, 0};
                int ready = poll(&wait, 1, int(left < 0 ? -1 : left));
                if (ready < 0 && errno == EINTR)
                    continue;
                if (ready <= 0)
                    continue;
                ssize_t got = read(fds[0], buffer, sizeof buffer);
                if (got <= 0)
                    break;
                output.append(buffer, size_t(got));
            }
            close(fds[0]);
            if (timed_out)
                kill(pid, SIGKILL);
            int status = 0;
            while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {
            }
            exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : -1;
            return !timed_out;
        }

        void run_detached(std::vector<std::string> command) {
            posix_spawn_file_actions_t actions;
            posix_spawn_file_actions_init(&actions);
            posix_spawn_file_actions_addopen(&actions, 0, "/dev/null", O_RDONLY, 0);
            posix_spawn_file_actions_addopen(&actions, 1, "/dev/null", O_WRONLY, 0);
            posix_spawn_file_actions_addopen(&actions, 2, "/dev/null", O_WRONLY, 0);
            auto argv = argv_of(command);
            pid_t pid = 0;
            if (posix_spawnp(&pid, argv[0], &actions, nullptr, argv.data(), environ) == 0) {
                std::thread([pid] {
                    int status = 0;
                    waitpid(pid, &status, 0);
                }).detach();
            }
            posix_spawn_file_actions_destroy(&actions);
        }

        std::string trim_line(std::string text) {
            while (!text.empty() && (text.back() == '\n' || text.back() == '\r'))
                text.pop_back();
            return text;
        }

        bool spawn_reader(std::vector<std::string> command, pid_t &pid, int &read_fd) {
            int fds[2];
            if (pipe2(fds, O_CLOEXEC) != 0)
                return false;
            posix_spawn_file_actions_t actions;
            posix_spawn_file_actions_init(&actions);
            posix_spawn_file_actions_addopen(&actions, 0, "/dev/null", O_RDONLY, 0);
            posix_spawn_file_actions_adddup2(&actions, fds[1], 1);
            posix_spawn_file_actions_addopen(&actions, 2, "/dev/null", O_WRONLY, 0);
            auto argv = argv_of(command);
            int started = posix_spawnp(&pid, argv[0], &actions, nullptr, argv.data(), environ);
            posix_spawn_file_actions_destroy(&actions);
            close(fds[1]);
            if (started != 0) {
                close(fds[0]);
                return false;
            }
            read_fd = fds[0];
            return true;
        }

        bool read_line(int fd, std::string &pending, std::string &line, int timeout_ms) {
            auto end = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout_ms);
            while (true) {
                size_t cut = pending.find('\n');
                if (cut != std::string::npos) {
                    line = pending.substr(0, cut);
                    pending.erase(0, cut + 1);
                    return true;
                }
                long left = timeout_ms < 0 ? -1 : long(std::chrono::duration_cast<std::chrono::milliseconds>(end - std::chrono::steady_clock::now()).count());
                if (timeout_ms >= 0 && left <= 0)
                    return false;
                pollfd wait{fd, POLLIN, 0};
                int ready = poll(&wait, 1, int(left < 0 ? -1 : left));
                if (ready < 0 && errno == EINTR)
                    continue;
                if (ready <= 0)
                    continue;
                char buffer[4096];
                ssize_t got = read(fd, buffer, sizeof buffer);
                if (got <= 0)
                    return false;
                pending.append(buffer, size_t(got));
            }
        }

        void stop_reader(pid_t pid, int fd) {
            close(fd);
            kill(pid, SIGTERM);
            int status = 0;
            while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {
            }
        }

        std::string variant_text(const std::string &text) {
            std::string out = "'";
            for (char c: text) {
                if (c == '\\' || c == '\'')
                    out.push_back('\\');
                out.push_back(c);
            }
            return out + "'";
        }

        std::string uri_to_path(const std::string &uri) {
            const std::string scheme = "file://";
            if (uri.compare(0, scheme.size(), scheme) != 0)
                return std::string();
            std::string path;
            for (size_t i = scheme.size(); i < uri.size(); i++) {
                if (uri[i] == '%' && i + 2 < uri.size()) {
                    path.push_back(char(std::strtol(uri.substr(i + 1, 2).c_str(), nullptr, 16)));
                    i += 2;
                } else {
                    path.push_back(uri[i]);
                }
            }
            return path;
        }

        bool portal_pick(bool folder, const std::string &title, const std::string &initial, const std::string &filter_name,
                         const std::string &patterns, std::string &result) {
            if (!on_path("gdbus") || std::getenv("DBUS_SESSION_BUS_ADDRESS") == nullptr)
                return false;
            std::string text;
            int code = -1;
            if (!run_capture({"gdbus", "introspect", "--session", "--dest", "org.freedesktop.portal.Desktop", "--object-path",
                              "/org/freedesktop/portal/desktop"}, 3000, text, code) || code != 0 ||
                text.find("org.freedesktop.portal.FileChooser") == std::string::npos)
                return false;
            pid_t monitor = 0;
            int fd = -1;
            if (!spawn_reader({"gdbus", "monitor", "--session", "--dest", "org.freedesktop.portal.Desktop"}, monitor, fd))
                return false;
            std::string pending;
            std::string line;
            if (!read_line(fd, pending, line, 3000)) {
                stop_reader(monitor, fd);
                return false;
            }
            static int counter = 0;
            std::string token = "fetchjet" + std::to_string(getpid()) + "_" + std::to_string(++counter);
            std::string options = "{'handle_token': <'" + token + "'>, 'modal': <true>";
            if (folder)
                options += ", 'directory': <true>";
            if (!initial.empty())
                options += ", 'current_folder': <b" + variant_text(initial) + ">";
            if (!folder && !patterns.empty()) {
                std::string list;
                std::istringstream words(patterns);
                std::string word;
                while (words >> word)
                    list += std::string(list.empty() ? "" : ", ") + "(uint32 0, " + variant_text(word) + ")";
                options += ", 'filters': <[(" + variant_text(filter_name.empty() ? patterns : filter_name) + ", [" + list + "])]>";
            }
            options += "}";
            std::string answer;
            if (!run_capture({"gdbus", "call", "--session", "--dest", "org.freedesktop.portal.Desktop", "--object-path",
                              "/org/freedesktop/portal/desktop", "--method", "org.freedesktop.portal.FileChooser.OpenFile",
                              "''", variant_text(title), options}, 10000, answer, code) || code != 0) {
                stop_reader(monitor, fd);
                return false;
            }
            size_t start = answer.find("'/");
            size_t stop = start == std::string::npos ? std::string::npos : answer.find('\'', start + 1);
            if (stop == std::string::npos) {
                stop_reader(monitor, fd);
                return false;
            }
            std::string handle = answer.substr(start + 1, stop - start - 1);
            result.clear();
            while (read_line(fd, pending, line, -1)) {
                if (line.compare(0, handle.size(), handle) != 0 || line.find("Response") == std::string::npos)
                    continue;
                size_t number = line.find("(uint32 ");
                int response = number == std::string::npos ? 2 : std::atoi(line.c_str() + number + 8);
                size_t uris = line.find("'uris': <['");
                if (response == 0 && uris != std::string::npos) {
                    size_t first = uris + 11;
                    size_t last = line.find('\'', first);
                    if (last != std::string::npos)
                        result = uri_to_path(line.substr(first, last - first));
                }
                break;
            }
            stop_reader(monitor, fd);
            return true;
        }

        std::string runtime_folder() {
            const char *runtime = std::getenv("XDG_RUNTIME_DIR");
            if (runtime && *runtime)
                return runtime;
            return "/tmp";
        }

    }

    int child_main(int, char **, bool &handled) {
        handled = false;
        return 0;
    }

    void prepare_process() {
        if (!std::getenv("SLINT_BACKEND"))
            setenv("SLINT_BACKEND", "winit-software", 0);
    }

    bool load_core(CoreApi &core) {
        void *library = dlopen((executable_folder() + "libfjcore.so").c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!library) {
            std::fprintf(stderr, "FetchJet: %s\n", dlerror());
            return false;
        }
        core.prepare = symbol<fj_prepare_fn>(library, "fj_prepare");
        core.start = symbol<fj_start_fn>(library, "fj_start");
        core.snapshot = symbol<fj_snapshot_fn>(library, "fj_snapshot");
        core.action = symbol<fj_action_fn>(library, "fj_action");
        core.call = symbol<fj_call_fn>(library, "fj_call");
        core.release = symbol<fj_free_fn>(library, "fj_free");
        core.shutdown = symbol<fj_shutdown_fn>(library, "fj_shutdown");
        return core.prepare && core.start && core.snapshot && core.action && core.call && core.release && core.shutdown;
    }

    void fatal(const std::string &text) {
        std::fprintf(stderr, "FetchJet: %s\n", text.c_str());
    }

    bool system_uses_dark() {
        const char *forced = std::getenv("FETCHJET_THEME");
        if (forced && *forced)
            return std::strcmp(forced, "dark") == 0;
        std::string output;
        int code = -1;
        if (on_path("gdbus") &&
            run_capture({"gdbus", "call", "--session", "--timeout", "2", "--dest", "org.freedesktop.portal.Desktop",
                         "--object-path", "/org/freedesktop/portal/desktop", "--method", "org.freedesktop.portal.Settings.Read",
                         "org.freedesktop.appearance", "color-scheme"}, 3000, output, code) && code == 0) {
            if (output.find("uint32 1") != std::string::npos)
                return true;
            if (output.find("uint32 2") != std::string::npos)
                return false;
        }
        output.clear();
        if (on_path("gsettings") &&
            run_capture({"gsettings", "get", "org.gnome.desktop.interface", "color-scheme"}, 2000, output, code) && code == 0 &&
            output.find("dark") != std::string::npos)
            return true;
        const char *theme = std::getenv("GTK_THEME");
        return theme && std::strstr(theme, ":dark") != nullptr;
    }

    std::string read_text_file(const std::string &utf8_path) {
        std::ifstream file(utf8_path, std::ios::binary);
        std::ostringstream text;
        text << file.rdbuf();
        return text.str();
    }

    void *claim_instance(const std::string &bridge_port, bool &second) {
        std::string path = runtime_folder() + "/fetchjet-native" + bridge_port + ".lock";
        int fd = open(path.c_str(), O_RDWR | O_CREAT | O_CLOEXEC, 0600);
        if (fd < 0)
            return nullptr;
        second = flock(fd, LOCK_EX | LOCK_NB) != 0;
        return reinterpret_cast<void *>(intptr_t(fd) + 1);
    }

    void release_instance(void *instance) {
        if (instance)
            close(int(reinterpret_cast<intptr_t>(instance) - 1));
    }

    void apply_title_bar(bool) {}

    void bring_to_front(bool) {
        if (has_ui() && ui().window().is_minimized())
            ui().window().set_minimized(false);
    }

    void window_hidden() {}

    bool window_minimized() {
        return has_ui() && ui().window().is_minimized();
    }

    bool toggle_too_soon() {
        static std::chrono::steady_clock::time_point last{};
        auto now = std::chrono::steady_clock::now();
        if (now - last < std::chrono::milliseconds(400))
            return true;
        last = now;
        return false;
    }

    void start_shell() {
        tray::start(executable_folder());
    }

    bool tray_on() {
        return tray::on();
    }

    void update_tray_tip(const std::string &tip) {
        auto &window = ui();
        ShellDto shell = window.global<Core>().get_shell();
        tray::Menu menu;
        menu.tooltip = tip;
        menu.show = plain(shell.trayShowLabel);
        menu.close = plain(shell.trayCloseLabel);
        menu.when_done = plain(shell.whenDoneLabel);
        menu.when_done_on = shell.whenDoneSupported && shell.whenDoneOptions;
        if (shell.whenDoneOptions) {
            for (size_t i = 0; i < shell.whenDoneOptions->row_count(); i++) {
                auto option = shell.whenDoneOptions->row_data(i);
                menu.options.push_back(option ? plain(*option) : std::string());
            }
        }
        menu.index = shell.whenDoneIndex;
        menu.rtl = window.global<Dir>().get_rtl();
        tray::update(menu);
    }

    void remove_tray() {
        tray::stop();
    }

    void show_notice(const std::string &title, const std::string &text) {
        if (on_path("notify-send")) {
            run_detached({"notify-send", "--app-name=FetchJet", "--icon=fetchjet", title, text});
            return;
        }
        std::fprintf(stderr, "FetchJet notice: %s: %s\n", title.c_str(), text.c_str());
    }

    void start_emoji(const std::string &) {}

    slint::Image emoji_fallback(const slint::SharedString &, int) {
        return slint::Image();
    }

    std::string exe_folder() {
        return executable_folder();
    }

    std::string pick_path(bool folder, const std::string &title, const std::string &initial,
                          const std::string &filter_name, const std::string &pattern) {
        std::string patterns = pattern;
        for (auto &c: patterns)
            if (c == ';')
                c = ' ';
        std::string chosen;
        if (std::getenv("FETCHJET_NO_PORTAL") == nullptr && portal_pick(folder, title, initial, filter_name, patterns, chosen))
            return chosen;
        std::vector<std::string> command;
        if (on_path("zenity")) {
            command = {"zenity", "--file-selection", "--title=" + title};
            if (folder)
                command.push_back("--directory");
            if (!initial.empty())
                command.push_back("--filename=" + initial + (folder ? "/" : ""));
            if (!folder && !patterns.empty())
                command.push_back("--file-filter=" + (filter_name.empty() ? patterns : filter_name) + " | " + patterns);
        } else if (on_path("kdialog")) {
            if (folder)
                command = {"kdialog", "--title", title, "--getexistingdirectory", initial.empty() ? "." : initial};
            else
                command = {"kdialog", "--title", title, "--getopenfilename", initial.empty() ? "." : initial,
                           patterns.empty() ? std::string() : patterns + "|" + filter_name};
        } else {
            std::fprintf(stderr, "FetchJet: no file dialog: install zenity or kdialog\n");
            return std::string();
        }
        std::string output;
        int code = -1;
        if (!run_capture(command, -1, output, code) || code != 0)
            return std::string();
        return trim_line(output);
    }

    char *copy_for_core(const std::string &text) {
        return strdup(text.c_str());
    }

}
