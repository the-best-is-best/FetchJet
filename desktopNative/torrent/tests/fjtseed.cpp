#ifdef _WIN32
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
using socket_length = int;
#else
#include <arpa/inet.h>
#include <csignal>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <unistd.h>
#include <chrono>
#include <cstdint>
using SOCKET = int;
using socket_length = socklen_t;
constexpr SOCKET INVALID_SOCKET = -1;
inline int closesocket(SOCKET s) { return close(s); }
inline std::uint64_t GetTickCount64() {
    return static_cast<std::uint64_t>(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count());
}
inline unsigned GetCurrentProcessId() { return static_cast<unsigned>(getpid()); }
#endif

#include <libtorrent/add_torrent_params.hpp>
#include <libtorrent/alert_types.hpp>
#include <libtorrent/create_torrent.hpp>
#include <libtorrent/ip_filter.hpp>
#include <libtorrent/load_torrent.hpp>
#include <libtorrent/session.hpp>
#include <libtorrent/session_params.hpp>
#include <libtorrent/settings_pack.hpp>
#include <libtorrent/torrent_flags.hpp>
#include <libtorrent/torrent_handle.hpp>
#include <libtorrent/torrent_info.hpp>
#include <libtorrent/torrent_status.hpp>

#include <atomic>
#include <cctype>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <map>
#include <memory>
#include <mutex>
#include <set>
#include <string>
#include <thread>
#include <vector>

namespace {

std::mutex print_mutex;

void say(std::string const& line) {
    std::lock_guard<std::mutex> lock(print_mutex);
    std::fwrite(line.data(), 1, line.size(), stdout);
    std::fputc('\n', stdout);
    std::fflush(stdout);
}

struct options {
    std::string dir = ".";
    std::string out;
    std::string name = "fjtseed";
    std::string files = "a.bin:1500000,sub/b.bin:24000000,c.txt:70000";
    int piece = 262144;
    int rate = 0;
    bool swarm = false;
    bool web = false;
    bool peer = true;
    bool v1_only = false;
    int life = 900;
};

struct file_spec {
    std::string path;
    std::int64_t size = 0;
};

std::vector<std::string> split(std::string const& text, char cut) {
    std::vector<std::string> parts;
    std::size_t at = 0;
    while (at <= text.size()) {
        std::size_t end = text.find(cut, at);
        if (end == std::string::npos) end = text.size();
        if (end > at) parts.push_back(text.substr(at, end - at));
        at = end + 1;
    }
    return parts;
}

std::vector<file_spec> parse_files(std::string const& text) {
    std::vector<file_spec> list;
    for (std::string const& item : split(text, ',')) {
        std::size_t const colon = item.rfind(':');
        if (colon == std::string::npos) continue;
        file_spec f;
        f.path = item.substr(0, colon);
        f.size = std::stoll(item.substr(colon + 1));
        list.push_back(f);
    }
    return list;
}

#ifdef _WIN32
std::string native_path(std::string path) {
    for (char& c : path) {
        if (c == '/') c = '\\';
    }
    return path;
}

void make_dirs_for(std::string const& file) {
    std::string const path = native_path(file);
    for (std::size_t i = 3; i < path.size(); ++i) {
        if (path[i] == '\\') CreateDirectoryA(path.substr(0, i).c_str(), nullptr);
    }
}
#else
std::string native_path(std::string path) {
    return path;
}

void make_dirs_for(std::string const& file) {
    for (std::size_t i = 1; i < file.size(); ++i) {
        if (file[i] == '/') mkdir(file.substr(0, i).c_str(), 0755);
    }
}
#endif

bool write_file(std::string const& path, std::int64_t size, int salt) {
    make_dirs_for(path);
    std::ofstream out(native_path(path), std::ios::binary | std::ios::trunc);
    if (!out) return false;
    std::vector<char> buffer(1 << 20);
    std::int64_t done = 0;
    while (done < size) {
        std::size_t const n = static_cast<std::size_t>(std::min<std::int64_t>(size - done, static_cast<std::int64_t>(buffer.size())));
        for (std::size_t i = 0; i < n; ++i) {
            std::uint32_t x = static_cast<std::uint32_t>(done + static_cast<std::int64_t>(i)) * 2654435761u ^ static_cast<std::uint32_t>(salt + 1) * 40503u;
            x ^= x >> 13;
            buffer[i] = static_cast<char>(x & 0xff);
        }
        out.write(buffer.data(), static_cast<std::streamsize>(n));
        done += static_cast<std::int64_t>(n);
    }
    return static_cast<bool>(out);
}

std::string hex_of(lt::sha1_hash const& h) {
    static char const digits[] = "0123456789abcdef";
    std::string text;
    for (auto b : h) {
        auto const v = static_cast<unsigned char>(b);
        text.push_back(digits[v >> 4]);
        text.push_back(digits[v & 0xf]);
    }
    return text;
}

std::string hex_bytes(std::string const& bytes) {
    static char const digits[] = "0123456789abcdef";
    std::string text;
    for (unsigned char v : bytes) {
        text.push_back(digits[v >> 4]);
        text.push_back(digits[v & 0xf]);
    }
    return text;
}

int hex_digit(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

std::string url_decode(std::string const& text) {
    std::string out;
    for (std::size_t i = 0; i < text.size(); ++i) {
        if (text[i] == '%' && i + 2 < text.size() && hex_digit(text[i + 1]) >= 0 && hex_digit(text[i + 2]) >= 0) {
            out.push_back(static_cast<char>(hex_digit(text[i + 1]) * 16 + hex_digit(text[i + 2])));
            i += 2;
        } else if (text[i] == '+') {
            out.push_back(' ');
        } else {
            out.push_back(text[i]);
        }
    }
    return out;
}

std::map<std::string, std::string> query_of(std::string const& target) {
    std::map<std::string, std::string> values;
    std::size_t const q = target.find('?');
    if (q == std::string::npos) return values;
    for (std::string const& pair : split(target.substr(q + 1), '&')) {
        std::size_t const eq = pair.find('=');
        if (eq == std::string::npos) values[pair] = "";
        else values[pair.substr(0, eq)] = url_decode(pair.substr(eq + 1));
    }
    return values;
}

struct tracker_state {
    std::mutex mutex;
    std::map<std::string, std::set<int>> peers;
    std::string root;
    bool swarm = false;
};

bool send_all(SOCKET s, char const* data, std::size_t size) {
    while (size > 0) {
        int const n = send(s, data, static_cast<int>(std::min<std::size_t>(size, 1 << 20)), 0);
        if (n <= 0) return false;
        data += n;
        size -= static_cast<std::size_t>(n);
    }
    return true;
}

bool send_text(SOCKET s, std::string const& text) {
    return send_all(s, text.data(), text.size());
}

std::string header_value(std::string const& head, char const* name) {
    std::string lower_head = head;
    for (char& c : lower_head) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    std::string const key = std::string("\r\n") + name + ":";
    std::size_t at = lower_head.find(key);
    if (at == std::string::npos) return "";
    at += key.size();
    std::size_t const end = head.find("\r\n", at);
    std::string value = head.substr(at, end == std::string::npos ? std::string::npos : end - at);
    while (!value.empty() && value.front() == ' ') value.erase(value.begin());
    return value;
}

bool answer_announce(SOCKET s, tracker_state& state, std::string const& target, std::string const& peer_ip) {
    auto const q = query_of(target);
    std::string const hash = q.count("info_hash") ? q.at("info_hash") : std::string();
    int const port = q.count("port") ? std::atoi(q.at("port").c_str()) : 0;
    std::string const event = q.count("event") ? q.at("event") : std::string();
    say("ANNOUNCE hash=" + hex_bytes(hash) + " port=" + std::to_string(port) + " event=" + event + " from=" + peer_ip);
    std::string compact;
    {
        std::lock_guard<std::mutex> lock(state.mutex);
        std::string const key = hex_bytes(hash.substr(0, 20));
        auto& list = state.peers[key];
        if (state.swarm && port > 0) {
            if (event == "stopped") list.erase(port);
            else list.insert(port);
        }
        for (int p : list) {
            if (p == port) continue;
            compact.push_back(static_cast<char>(127));
            compact.push_back(0);
            compact.push_back(0);
            compact.push_back(1);
            compact.push_back(static_cast<char>((p >> 8) & 0xff));
            compact.push_back(static_cast<char>(p & 0xff));
        }
    }
    std::string const body = "d8:intervali30e12:min intervali5e5:peers" + std::to_string(compact.size()) + ":" + compact + "e";
    std::string const head = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + std::to_string(body.size()) + "\r\n\r\n";
    return send_text(s, head) && send_text(s, body);
}

bool answer_file(SOCKET s, tracker_state& state, std::string const& target, std::string const& head, bool head_only) {
    std::string rel = url_decode(target.substr(5, target.find('?') == std::string::npos ? std::string::npos : target.find('?') - 5));
    std::string const range = header_value(head, "range");
    say("GET " + target + " range=" + (range.empty() ? std::string("-") : range));
    if (rel.find("..") != std::string::npos) {
        return send_text(s, "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n");
    }
    std::string const path = native_path(state.root + "/" + rel);
    std::ifstream in(path, std::ios::binary);
    if (!in) return send_text(s, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n");
    in.seekg(0, std::ios::end);
    std::int64_t const size = static_cast<std::int64_t>(in.tellg());
    std::int64_t first = 0;
    std::int64_t last = size - 1;
    bool partial = false;
    if (range.rfind("bytes=", 0) == 0) {
        std::string const spec = range.substr(6);
        std::size_t const dash = spec.find('-');
        if (dash != std::string::npos) {
            std::string const a = spec.substr(0, dash);
            std::string const b = spec.substr(dash + 1);
            if (!a.empty()) first = std::stoll(a);
            if (!b.empty()) last = std::min<std::int64_t>(std::stoll(b), size - 1);
            if (a.empty() && !b.empty()) {
                first = std::max<std::int64_t>(0, size - std::stoll(b));
                last = size - 1;
            }
            partial = true;
        }
    }
    if (first > last || first >= size) {
        return send_text(s, "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + std::to_string(size) + "\r\nContent-Length: 0\r\n\r\n");
    }
    std::int64_t const length = last - first + 1;
    std::string response = partial ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n";
    response += "Content-Type: application/octet-stream\r\nAccept-Ranges: bytes\r\nContent-Length: " + std::to_string(length) + "\r\n";
    if (partial) response += "Content-Range: bytes " + std::to_string(first) + "-" + std::to_string(last) + "/" + std::to_string(size) + "\r\n";
    response += "\r\n";
    if (!send_text(s, response)) return false;
    if (head_only) return true;
    in.seekg(first);
    std::vector<char> buffer(256 * 1024);
    std::int64_t left = length;
    while (left > 0) {
        std::size_t const n = static_cast<std::size_t>(std::min<std::int64_t>(left, static_cast<std::int64_t>(buffer.size())));
        in.read(buffer.data(), static_cast<std::streamsize>(n));
        if (in.gcount() <= 0) return false;
        if (!send_all(s, buffer.data(), static_cast<std::size_t>(in.gcount()))) return false;
        left -= in.gcount();
    }
    return true;
}

void serve(SOCKET s, std::shared_ptr<tracker_state> state, std::string peer_ip) {
    std::string pending;
    char buffer[4096];
    for (;;) {
        std::size_t end = pending.find("\r\n\r\n");
        while (end == std::string::npos) {
            int const n = recv(s, buffer, sizeof(buffer), 0);
            if (n <= 0) {
                closesocket(s);
                return;
            }
            pending.append(buffer, static_cast<std::size_t>(n));
            if (pending.size() > 65536) {
                closesocket(s);
                return;
            }
            end = pending.find("\r\n\r\n");
        }
        std::string const head = pending.substr(0, end + 2);
        pending.erase(0, end + 4);
        std::size_t const sp1 = head.find(' ');
        std::size_t const sp2 = head.find(' ', sp1 + 1);
        if (sp1 == std::string::npos || sp2 == std::string::npos) break;
        std::string const method = head.substr(0, sp1);
        std::string const target = head.substr(sp1 + 1, sp2 - sp1 - 1);
        bool ok = false;
        if (target.rfind("/announce", 0) == 0) ok = answer_announce(s, *state, target, peer_ip);
        else if (target.rfind("/web/", 0) == 0) ok = answer_file(s, *state, target, head, method == "HEAD");
        else {
            say("GET " + target + " range=-");
            ok = send_text(s, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n");
        }
        std::string const connection = header_value(head, "connection");
        if (!ok || connection == "close" || connection == "Close") break;
    }
    closesocket(s);
}

void listen_loop(SOCKET listener, std::shared_ptr<tracker_state> state) {
    for (;;) {
        sockaddr_in from{};
        socket_length length = sizeof(from);
        SOCKET s = accept(listener, reinterpret_cast<sockaddr*>(&from), &length);
        if (s == INVALID_SOCKET) return;
        char ip[64] = {};
        inet_ntop(AF_INET, &from.sin_addr, ip, sizeof(ip));
        std::thread(serve, s, state, std::string(ip)).detach();
    }
}

lt::ip_filter loopback_only() {
    lt::ip_filter filter;
    filter.add_rule(lt::make_address("0.0.0.0"), lt::make_address("126.255.255.255"), lt::ip_filter::blocked);
    filter.add_rule(lt::make_address("128.0.0.0"), lt::make_address("255.255.255.255"), lt::ip_filter::blocked);
    filter.add_rule(lt::make_address("::"), lt::make_address("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"), lt::ip_filter::blocked);
    return filter;
}

}

int main(int argc, char** argv) {
    options o;
    for (int i = 1; i < argc; ++i) {
        std::string const arg = argv[i];
        std::size_t const eq = arg.find('=');
        if (eq == std::string::npos) continue;
        std::string const key = arg.substr(0, eq);
        std::string const value = arg.substr(eq + 1);
        if (key == "dir") o.dir = value;
        else if (key == "out") o.out = value;
        else if (key == "name") o.name = value;
        else if (key == "files") o.files = value;
        else if (key == "piece") o.piece = std::atoi(value.c_str());
        else if (key == "rate") o.rate = std::atoi(value.c_str());
        else if (key == "swarm") o.swarm = value == "1";
        else if (key == "web") o.web = value == "1";
        else if (key == "peer") o.peer = value != "0";
        else if (key == "v1") o.v1_only = value == "1";
        else if (key == "life") o.life = std::atoi(value.c_str());
    }
    if (o.out.empty()) o.out = o.dir + "/" + o.name + ".torrent";
    try {
#ifdef _WIN32
        WSADATA wsa;
        if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
            say("ERROR winsock");
            return 2;
        }
#else
        std::signal(SIGPIPE, SIG_IGN);
#endif
        std::vector<file_spec> const specs = parse_files(o.files);
        if (specs.empty()) {
            say("ERROR no files");
            return 2;
        }
        std::vector<lt::create_file_entry> entries;
        int salt = 0;
        for (file_spec const& f : specs) {
            std::string const full = o.dir + "/" + o.name + "/" + f.path;
            if (!write_file(full, f.size, salt++)) {
                say("ERROR cannot write " + full);
                return 2;
            }
            entries.emplace_back(o.name + "/" + f.path, f.size);
        }

        SOCKET listener = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        sockaddr_in address{};
        address.sin_family = AF_INET;
        address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        address.sin_port = 0;
        if (listener == INVALID_SOCKET || bind(listener, reinterpret_cast<sockaddr*>(&address), sizeof(address)) != 0 || listen(listener, 64) != 0) {
            say("ERROR tracker socket");
            return 2;
        }
        socket_length length = sizeof(address);
        getsockname(listener, reinterpret_cast<sockaddr*>(&address), &length);
        int const tracker_port = ntohs(address.sin_port);
        std::string const tracker = "http://127.0.0.1:" + std::to_string(tracker_port) + "/announce";

        lt::create_torrent t(std::move(entries), o.piece, o.v1_only ? lt::create_torrent::v1_only : lt::create_flags_t{});
        if (o.peer) {
            t.add_tracker(tracker, 0);
            t.add_tracker("http://127.0.0.2:1/announce", 1);
        }
        if (o.web) t.add_url_seed("http://127.0.0.1:" + std::to_string(tracker_port) + "/web/");
        t.set_creator("fjtseed");
        lt::error_code ec;
        lt::set_piece_hashes(t, o.dir, ec);
        if (ec) {
            say("ERROR hashes " + ec.message());
            return 2;
        }
        std::vector<char> const bytes = t.generate_buf();
        {
            make_dirs_for(o.out);
            std::ofstream out(native_path(o.out), std::ios::binary | std::ios::trunc);
            out.write(bytes.data(), static_cast<std::streamsize>(bytes.size()));
            if (!out) {
                say("ERROR cannot write " + o.out);
                return 2;
            }
        }
        lt::error_code parse_ec;
        lt::add_torrent_params loaded = lt::load_torrent_buffer(lt::span<char const>(bytes.data(), static_cast<std::ptrdiff_t>(bytes.size())), parse_ec, lt::load_torrent_limits{});
        if (parse_ec || !loaded.ti) {
            say("ERROR parse " + parse_ec.message());
            return 2;
        }
        std::shared_ptr<lt::torrent_info const> const ti = loaded.ti;
        lt::info_hash_t const hashes = ti->info_hashes();
        std::string const v1 = hashes.has_v1() ? hex_of(hashes.v1) : std::string("none");

        auto state = std::make_shared<tracker_state>();
        state->root = o.dir;
        state->swarm = o.swarm;

        lt::settings_pack pack;
        int wanted_port = 0;
        std::uint32_t port_seed = static_cast<std::uint32_t>(GetTickCount64()) ^ (static_cast<std::uint32_t>(GetCurrentProcessId()) << 16);
        for (int attempt = 0; attempt < 200 && wanted_port == 0; ++attempt) {
            port_seed = port_seed * 1664525u + 1013904223u;
            int const port = 20000 + static_cast<int>((port_seed >> 8) % 40000u);
            sockaddr_in any{};
            any.sin_family = AF_INET;
            any.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
            any.sin_port = htons(static_cast<u_short>(port));
            SOCKET tcp = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
            SOCKET udp = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
            if (tcp != INVALID_SOCKET && udp != INVALID_SOCKET
                && bind(tcp, reinterpret_cast<sockaddr*>(&any), sizeof(any)) == 0
                && bind(udp, reinterpret_cast<sockaddr*>(&any), sizeof(any)) == 0) {
                wanted_port = port;
            }
            if (udp != INVALID_SOCKET) closesocket(udp);
            if (tcp != INVALID_SOCKET) closesocket(tcp);
        }
        pack.set_str(lt::settings_pack::listen_interfaces, "127.0.0.1:" + std::to_string(wanted_port));
        pack.set_bool(lt::settings_pack::enable_dht, false);
        pack.set_bool(lt::settings_pack::enable_lsd, false);
        pack.set_bool(lt::settings_pack::enable_upnp, false);
        pack.set_bool(lt::settings_pack::enable_natpmp, false);
        pack.set_str(lt::settings_pack::dht_bootstrap_nodes, "");
        pack.set_bool(lt::settings_pack::allow_multiple_connections_per_ip, true);
        pack.set_int(lt::settings_pack::alert_mask, lt::alert_category::error | lt::alert_category::connect | lt::alert_category::peer | lt::alert_category::status);
        lt::session_params params(std::move(pack));
        params.ip_filter = loopback_only();
        std::unique_ptr<lt::session> session;
        lt::torrent_handle handle;
        int peer_port = 0;
        if (o.peer) {
            session = std::make_unique<lt::session>(std::move(params));
            lt::add_torrent_params atp = loaded;
            atp.trackers.clear();
            atp.tracker_tiers.clear();
            atp.url_seeds.clear();
            atp.save_path = o.dir;
            atp.flags = lt::torrent_flags::seed_mode;
            handle = session->add_torrent(std::move(atp));
            if (o.rate > 0) handle.set_upload_limit(o.rate);
            for (int i = 0; i < 100 && peer_port == 0; ++i) {
                peer_port = session->listen_port();
                if (peer_port == 0) std::this_thread::sleep_for(std::chrono::milliseconds(50));
            }
            if (peer_port == 0) {
                say("ERROR no listen port");
                return 2;
            }
            for (int i = 0; i < 200; ++i) {
                lt::torrent_status const st = handle.status();
                if (st.state == lt::torrent_status::seeding && !(st.flags & lt::torrent_flags::paused)) break;
                std::this_thread::sleep_for(std::chrono::milliseconds(50));
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(500));
            std::lock_guard<std::mutex> lock(state->mutex);
            if (hashes.has_v1()) state->peers[v1].insert(peer_port);
            if (hashes.has_v2()) {
                std::string const truncated(reinterpret_cast<char const*>(hashes.v2.data()), 20);
                state->peers[hex_bytes(truncated)].insert(peer_port);
            }
        }
        std::thread(listen_loop, listener, state).detach();
        say("READY tracker=" + std::to_string(tracker_port) + " peer=" + std::to_string(peer_port) + " hash=" + v1 + " torrent=" + o.out);
        auto const end = std::chrono::steady_clock::now() + std::chrono::seconds(o.life);
        while (std::chrono::steady_clock::now() < end) {
            if (session) {
                session->wait_for_alert(std::chrono::milliseconds(500));
                std::vector<lt::alert*> alerts;
                session->pop_alerts(&alerts);
                for (lt::alert* a : alerts) say("ALERT " + a->message());
            } else {
                std::this_thread::sleep_for(std::chrono::milliseconds(500));
            }
        }
        closesocket(listener);
    } catch (std::exception const& e) {
        say(std::string("ERROR ") + e.what());
        return 2;
    }
    return 0;
}
