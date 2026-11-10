#include "fjtorrent.h"

#include <libtorrent/add_torrent_params.hpp>
#include <libtorrent/address.hpp>
#include <libtorrent/alert.hpp>
#include <libtorrent/alert_types.hpp>
#include <libtorrent/announce_entry.hpp>
#include <libtorrent/download_priority.hpp>
#include <libtorrent/error_code.hpp>
#include <libtorrent/info_hash.hpp>
#include <libtorrent/ip_filter.hpp>
#include <libtorrent/load_torrent.hpp>
#include <libtorrent/magnet_uri.hpp>
#include <libtorrent/peer_info.hpp>
#include <libtorrent/read_resume_data.hpp>
#include <libtorrent/session.hpp>
#include <libtorrent/session_params.hpp>
#include <libtorrent/session_stats.hpp>
#include <libtorrent/settings_pack.hpp>
#include <libtorrent/torrent_flags.hpp>
#include <libtorrent/torrent_handle.hpp>
#include <libtorrent/torrent_info.hpp>
#include <libtorrent/torrent_status.hpp>
#include <libtorrent/version.hpp>
#include <libtorrent/write_resume_data.hpp>

#if __has_include(<libtorrent/pread_disk_io.hpp>)
#include <libtorrent/pread_disk_io.hpp>
#define FJT_HAVE_PREAD_DISK_IO 1
#else
#define FJT_HAVE_PREAD_DISK_IO 0
#endif

#include <algorithm>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <map>
#include <memory>
#include <mutex>
#include <set>
#include <string>
#include <thread>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#endif

namespace fjt_impl {

using lock_t = std::lock_guard<std::recursive_mutex>;
using clock_type = std::chrono::steady_clock;

constexpr int download_only_upload_limit = 32 * 1024;
constexpr char local_only_variable[] = "FETCHJET_TORRENT_LOCAL_ONLY";

struct torrent_entry {
    lt::torrent_handle handle;
    bool disable_upload = false;
    int upload_limit = 0;
    std::chrono::steady_clock::time_point nudge_until{};
    std::chrono::steady_clock::time_point last_nudge{};
    std::chrono::steady_clock::time_point last_refill{};
};

struct magnet_torrent {
    lt::torrent_handle handle;
    int refs = 0;
};

struct resume_result {
    bool ok = false;
    std::vector<char> data;
};

struct core {
    std::recursive_mutex mutex;
    std::shared_ptr<lt::session> session;
    std::map<std::string, torrent_entry> tasks;
    std::map<std::string, magnet_torrent> magnets;
    std::set<lt::torrent_handle> resume_pending;
    std::map<lt::torrent_handle, resume_result> resume_results;
    std::set<lt::torrent_handle> flushed;
    std::set<lt::torrent_handle> flush_asked;
    bool local_only = false;
    int magnet_jobs = 0;
    int dht_nodes_metric = -1;
    std::int64_t dht_nodes = -1;
    clock_type::time_point last_drain = clock_type::now();
};

struct file_entry {
    int index = 0;
    std::int64_t size = 0;
    std::string path;
};

std::string hex_of(lt::sha1_hash const& h) {
    static char const digits[] = "0123456789abcdef";
    std::string out;
    out.reserve(40);
    for (auto b : h) {
        auto const v = static_cast<unsigned char>(b);
        out.push_back(digits[v >> 4]);
        out.push_back(digits[v & 0xf]);
    }
    return out;
}

std::string key_of(lt::info_hash_t const& ih) {
    return hex_of(ih.get_best());
}

std::int32_t put_text(std::string const& text, char* out, std::int32_t cap) {
    if (out != nullptr && cap > 0) {
        std::size_t const n = std::min<std::size_t>(text.size(), static_cast<std::size_t>(cap - 1));
        if (n > 0) std::memcpy(out, text.data(), n);
        out[n] = '\0';
    }
    return static_cast<std::int32_t>(std::min<std::size_t>(text.size(), static_cast<std::size_t>(INT32_MAX)));
}

void clear_text(char* out, std::int32_t cap) {
    if (out != nullptr && cap > 0) out[0] = '\0';
}

lt::span<char const> span_of(std::uint8_t const* data, std::int64_t size) {
    return {reinterpret_cast<char const*>(data), static_cast<std::ptrdiff_t>(size)};
}

std::string lower(std::string text) {
    for (char& ch : text) {
        if (ch >= 'A' && ch <= 'Z') ch = static_cast<char>(ch - 'A' + 'a');
    }
    return text;
}

std::string trimmed(std::string const& text) {
    std::size_t first = 0;
    std::size_t last = text.size();
    while (first < last && (text[first] == ' ' || text[first] == '\t' || text[first] == '\r')) ++first;
    while (last > first && (text[last - 1] == ' ' || text[last - 1] == '\t' || text[last - 1] == '\r')) --last;
    return text.substr(first, last - first);
}

std::vector<std::string> lines_of(char const* text) {
    std::vector<std::string> lines;
    if (text == nullptr) return lines;
    std::string const all(text);
    std::size_t at = 0;
    while (at < all.size()) {
        std::size_t end = all.find('\n', at);
        if (end == std::string::npos) end = all.size();
        std::string line = all.substr(at, end - at);
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (!line.empty()) lines.push_back(std::move(line));
        at = end + 1;
    }
    return lines;
}

bool parse_int(std::string const& text, int& out) {
    if (text.empty()) return false;
    char* end = nullptr;
    long long const value = std::strtoll(text.c_str(), &end, 10);
    if (end == text.c_str() || *end != '\0') return false;
    if (value < INT32_MIN || value > INT32_MAX) return false;
    out = static_cast<int>(value);
    return true;
}

bool local_only_requested() {
#ifdef _WIN32
    char value[8] = {};
    DWORD const length = GetEnvironmentVariableA(local_only_variable, value, static_cast<DWORD>(sizeof(value)));
    return length == 1 && value[0] == '1';
#else
    char const* value = std::getenv(local_only_variable);
    return value != nullptr && std::strcmp(value, "1") == 0;
#endif
}

std::string host_of(std::string const& url) {
    std::size_t start = url.find("://");
    start = start == std::string::npos ? 0 : start + 3;
    std::size_t end = url.find_first_of("/?#", start);
    if (end == std::string::npos) end = url.size();
    return url.substr(start, end - start);
}

bool is_local_host(std::string const& name) {
    std::string const host = lower(name);
    return host == "127.0.0.1" || host == "localhost";
}

bool is_local_url(std::string const& url) {
    std::string host = host_of(trimmed(url));
    std::size_t const at = host.rfind('@');
    if (at != std::string::npos) host = host.substr(at + 1);
    if (!host.empty() && host[0] == '[') return false;
    std::size_t const colon = host.find(':');
    if (colon != std::string::npos) host = host.substr(0, colon);
    return is_local_host(host);
}

std::string local_listen(std::string const& configured) {
    std::string first = configured.substr(0, configured.find(','));
    std::size_t const colon = first.rfind(':');
    std::string port;
    if (colon != std::string::npos) {
        for (std::size_t i = colon + 1; i < first.size() && first[i] >= '0' && first[i] <= '9'; ++i) port.push_back(first[i]);
    }
    return "127.0.0.1:" + (port.empty() ? std::string("0") : port);
}

void force_local(lt::settings_pack& pack, bool creating) {
    pack.set_bool(lt::settings_pack::enable_dht, false);
    pack.set_bool(lt::settings_pack::enable_lsd, false);
    pack.set_bool(lt::settings_pack::enable_upnp, false);
    pack.set_bool(lt::settings_pack::enable_natpmp, false);
    pack.set_str(lt::settings_pack::dht_bootstrap_nodes, "");
    if (pack.has_val(lt::settings_pack::listen_interfaces)) {
        pack.set_str(lt::settings_pack::listen_interfaces, local_listen(pack.get_str(lt::settings_pack::listen_interfaces)));
    } else if (creating) {
        pack.set_str(lt::settings_pack::listen_interfaces, "127.0.0.1:0");
    }
    if (pack.has_val(lt::settings_pack::outgoing_interfaces)) pack.set_str(lt::settings_pack::outgoing_interfaces, "");
    if (pack.has_val(lt::settings_pack::proxy_hostname) && !is_local_host(trimmed(pack.get_str(lt::settings_pack::proxy_hostname)))) {
        pack.set_int(lt::settings_pack::proxy_type, lt::settings_pack::none);
    }
}

int first_port(std::string const& interfaces) {
    std::string const first = interfaces.substr(0, interfaces.find(','));
    std::size_t const colon = first.rfind(':');
    if (colon == std::string::npos) return -1;
    int port = 0;
    bool any = false;
    for (std::size_t i = colon + 1; i < first.size() && first[i] >= '0' && first[i] <= '9'; ++i) {
        port = port * 10 + (first[i] - '0');
        any = true;
        if (port > 65535) return -1;
    }
    return any ? port : -1;
}

std::string with_port(std::string const& interfaces, int port) {
    std::string out;
    std::size_t at = 0;
    while (at <= interfaces.size()) {
        std::size_t end = interfaces.find(',', at);
        if (end == std::string::npos) end = interfaces.size();
        std::string item = interfaces.substr(at, end - at);
        std::size_t const colon = item.rfind(':');
        if (colon != std::string::npos && (item.find(']') == std::string::npos || colon > item.find(']'))) {
            std::size_t digits = colon + 1;
            while (digits < item.size() && item[digits] >= '0' && item[digits] <= '9') ++digits;
            item = item.substr(0, colon + 1) + std::to_string(port) + item.substr(digits);
        }
        if (!out.empty()) out += ",";
        out += item;
        at = end + 1;
    }
    return out;
}

#ifdef _WIN32
bool port_free(int port, bool loopback) {
    int const kinds[2] = {SOCK_STREAM, SOCK_DGRAM};
    for (int kind : kinds) {
        SOCKET s = ::socket(AF_INET, kind, kind == SOCK_STREAM ? IPPROTO_TCP : IPPROTO_UDP);
        if (s == INVALID_SOCKET) return true;
        sockaddr_in address{};
        address.sin_family = AF_INET;
        address.sin_addr.s_addr = htonl(loopback ? INADDR_LOOPBACK : INADDR_ANY);
        address.sin_port = htons(static_cast<u_short>(port));
        BOOL exclusive = TRUE;
        ::setsockopt(s, SOL_SOCKET, SO_EXCLUSIVEADDRUSE, reinterpret_cast<char const*>(&exclusive), sizeof(exclusive));
        int const bound = ::bind(s, reinterpret_cast<sockaddr*>(&address), sizeof(address));
        ::closesocket(s);
        if (bound != 0) return false;
    }
    return true;
}

void pick_listen_port(lt::settings_pack& pack, bool loopback) {
    if (!pack.has_val(lt::settings_pack::listen_interfaces)) return;
    std::string const interfaces = pack.get_str(lt::settings_pack::listen_interfaces);
    int const port = first_port(interfaces);
    if (port <= 0) return;
    WSADATA wsa;
    if (::WSAStartup(MAKEWORD(2, 2), &wsa) != 0) return;
    if (!port_free(port, loopback)) {
        std::uint32_t seed = static_cast<std::uint32_t>(::GetTickCount64()) ^ (static_cast<std::uint32_t>(::GetCurrentProcessId()) << 16);
        for (int i = 0; i < 64; ++i) {
            seed = seed * 1664525u + 1013904223u;
            int const candidate = 20000 + static_cast<int>((seed >> 8) % 40000u);
            if (port_free(candidate, loopback)) {
                pack.set_str(lt::settings_pack::listen_interfaces, with_port(interfaces, candidate));
                break;
            }
        }
    }
    ::WSACleanup();
}
#else
void pick_listen_port(lt::settings_pack&, bool) {}
#endif

void keep_local(lt::add_torrent_params& atp) {
    std::vector<std::string> trackers;
    std::vector<int> tiers;
    for (std::size_t i = 0; i < atp.trackers.size(); ++i) {
        if (!is_local_url(atp.trackers[i])) continue;
        int tier = 0;
        if (i < atp.tracker_tiers.size()) tier = atp.tracker_tiers[i];
        else if (!atp.tracker_tiers.empty()) tier = atp.tracker_tiers.back();
        trackers.push_back(atp.trackers[i]);
        tiers.push_back(tier);
    }
    atp.trackers.clear();
    atp.tracker_tiers.clear();
    for (std::size_t i = 0; i < trackers.size(); ++i) {
        atp.trackers.push_back(trackers[i]);
        atp.tracker_tiers.push_back(tiers[i]);
    }
    std::vector<std::string> seeds;
    for (auto const& seed : atp.url_seeds) {
        if (is_local_url(seed)) seeds.push_back(seed);
    }
    atp.url_seeds.clear();
    for (std::string const& seed : seeds) atp.url_seeds.push_back(seed);
    std::vector<lt::tcp::endpoint> peers;
    for (auto const& peer : atp.peers) {
        if (peer.address().is_loopback()) peers.push_back(peer);
    }
    atp.peers.clear();
    for (auto const& peer : peers) atp.peers.push_back(peer);
    atp.dht_nodes.clear();
}

int read_settings(lt::settings_pack& pack, char const* text) {
    int skipped = 0;
    for (std::string const& line : lines_of(text)) {
        std::size_t const cut = line.find('=');
        if (cut == std::string::npos || cut == 0) {
            ++skipped;
            continue;
        }
        std::string const name = line.substr(0, cut);
        std::string const value = line.substr(cut + 1);
        int const id = lt::setting_by_name(name);
        if (id < 0 || id == lt::settings_pack::alert_mask) {
            ++skipped;
            continue;
        }
        int const kind = id & lt::settings_pack::type_mask;
        if (kind == lt::settings_pack::string_type_base) {
            pack.set_str(id, value);
        } else if (kind == lt::settings_pack::int_type_base) {
            int number = 0;
            if (parse_int(value, number)) pack.set_int(id, number);
            else ++skipped;
        } else if (kind == lt::settings_pack::bool_type_base) {
            pack.set_bool(id, !(value.empty() || value == "0" || value == "false"));
        } else {
            ++skipped;
        }
    }
    return skipped;
}

int clamp_limit(std::int64_t value) {
    if (value <= 0) return 0;
    return static_cast<int>(std::min<std::int64_t>(value, INT32_MAX));
}

void apply_limits(torrent_entry& entry, std::int64_t download, std::int64_t upload) {
    int const up = entry.disable_upload ? download_only_upload_limit : clamp_limit(upload);
    entry.handle.set_download_limit(clamp_limit(download));
    entry.handle.set_upload_limit(up);
    entry.upload_limit = up;
}

std::vector<lt::download_priority_t> priorities_of(std::uint8_t const* values, std::int32_t count) {
    std::vector<lt::download_priority_t> list;
    if (values == nullptr || count <= 0) return list;
    list.reserve(static_cast<std::size_t>(count));
    for (std::int32_t i = 0; i < count; ++i) list.push_back(lt::download_priority_t(values[i]));
    return list;
}

lt::torrent_handle find_existing(lt::session& session, lt::info_hash_t const& ih) {
    lt::torrent_handle h;
    if (ih.has_v1()) h = session.find_torrent(ih.v1);
    if (!h.is_valid() && ih.has_v2()) h = session.find_torrent(ih.v2);
    return h;
}

std::int64_t state_code(lt::torrent_status::state_t state) {
    switch (state) {
        case lt::torrent_status::checking_files:
            return FJT_STATE_CHECKING_FILES;
        case lt::torrent_status::checking_resume_data:
            return FJT_STATE_CHECKING_RESUME_DATA;
        case lt::torrent_status::downloading_metadata:
            return FJT_STATE_DOWNLOADING_METADATA;
        case lt::torrent_status::downloading:
            return FJT_STATE_DOWNLOADING;
        case lt::torrent_status::finished:
            return FJT_STATE_FINISHED;
        case lt::torrent_status::seeding:
            return FJT_STATE_SEEDING;
        default:
            return FJT_STATE_CONNECTING;
    }
}

char const* state_name(std::int64_t code) {
    switch (code) {
        case FJT_STATE_CHECKING_FILES:
            return "checking_files";
        case FJT_STATE_CHECKING_RESUME_DATA:
            return "checking_resume_data";
        case FJT_STATE_DOWNLOADING_METADATA:
            return "downloading_metadata";
        case FJT_STATE_DOWNLOADING:
            return "downloading";
        case FJT_STATE_FINISHED:
            return "finished";
        case FJT_STATE_SEEDING:
            return "seeding";
        default:
            return "connecting";
    }
}

void reconnect_soon(lt::torrent_handle const& h) {
    if (h.flags() & lt::torrent_flags::upload_mode) return;
    h.set_flags(lt::torrent_flags::upload_mode);
    h.unset_flags(lt::torrent_flags::upload_mode);
}

void nudge_for_a_while(torrent_entry& entry) {
    entry.nudge_until = std::chrono::steady_clock::now() + std::chrono::seconds(30);
}

void wake(torrent_entry& entry) {
    if (entry.handle.flags() & lt::torrent_flags::paused) {
        reconnect_soon(entry.handle);
        nudge_for_a_while(entry);
    }
    entry.handle.resume();
}

void prioritize(torrent_entry* entry, lt::torrent_handle const& h, std::vector<lt::download_priority_t> const& priorities) {
    bool const was_finished = h.status(lt::status_flags_t{}).is_finished;
    h.prioritize_files(priorities);
    if (was_finished && !(h.flags() & lt::torrent_flags::paused)) {
        reconnect_soon(h);
        if (entry != nullptr) nudge_for_a_while(*entry);
    }
}

void nudge_if_stuck(torrent_entry& entry, lt::torrent_status const& st) {
    auto const now = std::chrono::steady_clock::now();
    if (now >= entry.nudge_until || now - entry.last_nudge < std::chrono::seconds(2)) return;
    if ((st.flags & lt::torrent_flags::paused) || st.is_finished) return;
    if (st.num_connections > 0 || st.list_peers == 0) return;
    entry.last_nudge = now;
    reconnect_soon(entry.handle);
}

void refill_if_no_candidates(torrent_entry& entry, lt::torrent_status const& st) {
    if ((st.flags & lt::torrent_flags::paused) || st.is_finished || st.state != lt::torrent_status::downloading) return;
    if (st.num_connections > 0 || st.connect_candidates > 0 || st.list_peers == 0) return;
    auto const now = std::chrono::steady_clock::now();
    if (now - entry.last_refill < std::chrono::seconds(5)) return;
    entry.last_refill = now;
    lt::add_torrent_params const saved = entry.handle.get_resume_data();
    if (saved.peers.empty()) return;
    entry.handle.clear_peers();
    for (auto const& peer : saved.peers) entry.handle.connect_peer(peer, lt::peer_info::resume_data);
}

void drain(core& c) {
    c.last_drain = clock_type::now();
    if (!c.session) return;
    std::vector<lt::alert*> alerts;
    c.session->pop_alerts(&alerts);
    for (lt::alert* a : alerts) {
        if (auto const* stats = lt::alert_cast<lt::session_stats_alert>(a)) {
            auto const values = stats->counters();
            if (c.dht_nodes_metric >= 0 && c.dht_nodes_metric < static_cast<int>(values.size())) {
                c.dht_nodes = values[c.dht_nodes_metric];
            }
        } else if (auto const* saved = lt::alert_cast<lt::save_resume_data_alert>(a)) {
            if (c.resume_pending.count(saved->handle) != 0) {
                resume_result result;
                result.ok = true;
                result.data = lt::write_resume_data_buf(saved->params);
                c.resume_results[saved->handle] = std::move(result);
            }
        } else if (auto const* written = lt::alert_cast<lt::cache_flushed_alert>(a)) {
            c.flushed.insert(written->handle);
        } else if (auto const* failed = lt::alert_cast<lt::save_resume_data_failed_alert>(a)) {
            if (c.resume_pending.count(failed->handle) != 0) {
                c.resume_results[failed->handle] = resume_result{};
            }
        }
    }
}

void drain_if_due(core& c) {
    if (clock_type::now() - c.last_drain >= std::chrono::seconds(1)) drain(c);
}

torrent_entry* find_task(core& c, char const* task_id) {
    if (task_id == nullptr) return nullptr;
    auto const it = c.tasks.find(task_id);
    if (it == c.tasks.end() || !it->second.handle.is_valid()) return nullptr;
    return &it->second;
}

bool is_magnet_torrent(core const& c, lt::torrent_handle const& h) {
    for (auto const& item : c.magnets) {
        if (item.second.handle == h) return true;
    }
    return false;
}

void drop_magnet_torrent(core& c, lt::torrent_handle const& h) {
    for (auto it = c.magnets.begin(); it != c.magnets.end();) {
        if (it->second.handle == h) it = c.magnets.erase(it);
        else ++it;
    }
    if (c.session && h.is_valid()) c.session->remove_torrent(h, lt::session_handle::delete_files);
}

std::string tracker_note(lt::announce_entry const& tracker) {
    lt::announce_infohash const* best = nullptr;
    for (auto const& endpoint : tracker.endpoints) {
        lt::announce_infohash const& state = endpoint.info_hashes[lt::protocol_version::V1];
        if (state.fails == 0) {
            best = &state;
            break;
        }
        if (best == nullptr) best = &state;
    }
    if (best == nullptr) return "no endpoint";
    if (!best->message.empty()) return best->message;
    if (best->fails > 0) return "fails=" + std::to_string(static_cast<int>(best->fails));
    if (best->updating) return "updating";
    return best->start_sent ? "ok" : "idle";
}

}

struct fjt_session {
    std::shared_ptr<fjt_impl::core> state;
};

struct fjt_meta {
    std::string name;
    std::string info_hash;
    std::int64_t total_size = 0;
    int num_files = 0;
    std::vector<fjt_impl::file_entry> entries;
    std::vector<char> data;
};

struct fjt_magnet {
    std::shared_ptr<fjt_impl::core> state;
    lt::torrent_handle handle;
    lt::info_hash_t hashes;
    std::string key;
    bool owned = false;
};

struct fjt_buf {
    std::vector<char> data;
};

namespace fjt_impl {

void fill_meta(fjt_meta& meta, lt::torrent_info const& ti) {
    lt::info_hash_t const ih = ti.info_hashes();
    meta.name = ti.name();
    meta.info_hash = hex_of(ih.has_v1() ? ih.v1 : ih.get_best());
    meta.total_size = ti.total_size();
    lt::file_storage const& fs = ti.layout();
    meta.num_files = fs.num_files();
    meta.entries.clear();
    meta.entries.reserve(static_cast<std::size_t>(fs.num_files()));
    for (int i = 0; i < fs.num_files(); ++i) {
        lt::file_index_t const idx(i);
        if (fs.pad_file_at(idx)) continue;
        file_entry entry;
        entry.index = i;
        entry.size = fs.file_size(idx);
        entry.path = fs.file_path(idx);
        meta.entries.push_back(std::move(entry));
    }
}

file_entry const* entry_at(fjt_meta const* meta, std::int32_t entry) {
    if (meta == nullptr || entry < 0 || static_cast<std::size_t>(entry) >= meta->entries.size()) return nullptr;
    return &meta->entries[static_cast<std::size_t>(entry)];
}

void finish_session(std::shared_ptr<lt::session> session) {
    try {
        lt::session_proxy proxy = session->abort();
        session.reset();
    } catch (...) {
    }
}

}

using namespace fjt_impl;

extern "C" {

FJT_API int32_t FJT_CALL fjt_abi_version(void) {
    return FJT_ABI_VERSION;
}

FJT_API int32_t FJT_CALL fjt_version_text(char* out, int32_t cap) {
    try {
        return put_text(lt::version_str, out, cap);
    } catch (...) {
        clear_text(out, cap);
        return 0;
    }
}

FJT_API fjt_session* FJT_CALL fjt_session_create(const char* settings, char* err, int32_t err_cap) {
    clear_text(err, err_cap);
    try {
        auto state = std::make_shared<core>();
        state->local_only = local_only_requested();
        lt::settings_pack pack;
        read_settings(pack, settings);
        pack.set_int(lt::settings_pack::alert_mask, lt::alert_category::error | lt::alert_category::storage);
        pack.set_int(lt::settings_pack::max_retry_port_bind, 10);
        if (state->local_only) force_local(pack, true);
        pick_listen_port(pack, state->local_only);
        lt::session_params params(std::move(pack));
#if FJT_HAVE_PREAD_DISK_IO
        params.disk_io_constructor = lt::pread_disk_io_constructor;
#endif
        if (state->local_only) {
            lt::ip_filter addresses;
            addresses.add_rule(lt::make_address("0.0.0.0"), lt::make_address("126.255.255.255"), lt::ip_filter::blocked);
            addresses.add_rule(lt::make_address("128.0.0.0"), lt::make_address("255.255.255.255"), lt::ip_filter::blocked);
            addresses.add_rule(lt::make_address("::"), lt::make_address("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"), lt::ip_filter::blocked);
            params.ip_filter = addresses;
        }
        state->session = std::make_shared<lt::session>(std::move(params));
        lt::port_filter ports;
        ports.add_rule(0, 79, lt::port_filter::blocked);
        ports.add_rule(81, 442, lt::port_filter::blocked);
        ports.add_rule(444, 1023, lt::port_filter::blocked);
        state->session->set_port_filter(ports);
        state->dht_nodes_metric = lt::find_metric_idx("dht.dht_nodes");
        auto* session = new fjt_session;
        session->state = std::move(state);
        return session;
    } catch (std::exception const& e) {
        put_text(e.what(), err, err_cap);
    } catch (...) {
        put_text("The torrent session could not start", err, err_cap);
    }
    return nullptr;
}

FJT_API int32_t FJT_CALL fjt_session_apply_settings(fjt_session* session, const char* settings) {
    if (session == nullptr) return FJT_E_ARG;
    try {
        std::shared_ptr<core> const c = session->state;
        lock_t lock(c->mutex);
        if (!c->session) return FJT_E_SESSION;
        lt::settings_pack pack;
        int const skipped = read_settings(pack, settings);
        if (c->local_only) force_local(pack, false);
        c->session->apply_settings(std::move(pack));
        return skipped;
    } catch (...) {
        return FJT_E_FAILED;
    }
}

FJT_API int32_t FJT_CALL fjt_session_info(fjt_session* session, int64_t* out, int32_t count) {
    if (session == nullptr || out == nullptr || count <= 0) return FJT_E_ARG;
    try {
        std::shared_ptr<core> const c = session->state;
        lock_t lock(c->mutex);
        if (!c->session) return FJT_E_SESSION;
        std::int64_t values[FJT_SI_COUNT] = {};
        c->session->post_session_stats();
        values[FJT_SI_LISTEN_PORT] = c->session->listen_port();
        values[FJT_SI_DHT_RUNNING] = c->session->is_dht_running() ? 1 : 0;
        drain(*c);
        values[FJT_SI_DHT_NODES] = c->dht_nodes;
        values[FJT_SI_TORRENTS] = static_cast<std::int64_t>(c->session->get_torrents().size());
        std::set<lt::torrent_handle> seen;
        std::int64_t active = 0;
        for (auto const& item : c->tasks) {
            lt::torrent_handle const& h = item.second.handle;
            if (!h.is_valid() || !seen.insert(h).second) continue;
            if (!(h.flags() & lt::torrent_flags::paused)) ++active;
        }
        values[FJT_SI_ACTIVE_TORRENTS] = active;
        values[FJT_SI_MAGNET_JOBS] = c->magnet_jobs;
        values[FJT_SI_LOCAL_ONLY] = c->local_only ? 1 : 0;
        std::int32_t const written = std::min<std::int32_t>(count, FJT_SI_COUNT);
        for (std::int32_t i = 0; i < written; ++i) out[i] = values[i];
        return written;
    } catch (...) {
        return FJT_E_FAILED;
    }
}

FJT_API void FJT_CALL fjt_session_destroy(fjt_session* session, int32_t timeout_ms) {
    if (session == nullptr) return;
    std::shared_ptr<core> const c = session->state;
    delete session;
    std::shared_ptr<lt::session> running;
    try {
        lock_t lock(c->mutex);
        c->tasks.clear();
        c->magnets.clear();
        c->resume_pending.clear();
        c->resume_results.clear();
        c->flushed.clear();
        c->flush_asked.clear();
        running = std::move(c->session);
        c->session.reset();
    } catch (...) {
    }
    if (!running) return;
    try {
        running->pause();
    } catch (...) {
    }
    struct waiter {
        std::mutex mutex;
        std::condition_variable changed;
        bool done = false;
    };
    auto const wait = std::make_shared<waiter>();
    try {
        std::thread worker([wait](std::shared_ptr<lt::session> s) {
            finish_session(std::move(s));
            {
                std::lock_guard<std::mutex> lock(wait->mutex);
                wait->done = true;
            }
            wait->changed.notify_all();
        }, std::move(running));
        worker.detach();
    } catch (...) {
        if (running) finish_session(std::move(running));
        return;
    }
    std::unique_lock<std::mutex> lock(wait->mutex);
    wait->changed.wait_for(lock, std::chrono::milliseconds(std::max<std::int32_t>(timeout_ms, 0)), [&wait] { return wait->done; });
}

FJT_API fjt_meta* FJT_CALL fjt_meta_parse(const uint8_t* torrent, int64_t size, char* err, int32_t err_cap) {
    clear_text(err, err_cap);
    if (torrent == nullptr || size <= 0) {
        put_text("Not a torrent file", err, err_cap);
        return nullptr;
    }
    try {
        lt::error_code ec;
        lt::add_torrent_params atp = lt::load_torrent_buffer(span_of(torrent, size), ec, lt::load_torrent_limits{});
        if (ec || !atp.ti) {
            put_text(ec ? ec.message() : std::string("Not a torrent file"), err, err_cap);
            return nullptr;
        }
        auto meta = std::make_unique<fjt_meta>();
        fill_meta(*meta, *atp.ti);
        auto const* bytes = reinterpret_cast<char const*>(torrent);
        meta->data.assign(bytes, bytes + size);
        return meta.release();
    } catch (std::exception const& e) {
        put_text(e.what(), err, err_cap);
    } catch (...) {
        put_text("Not a torrent file", err, err_cap);
    }
    return nullptr;
}

FJT_API const char* FJT_CALL fjt_meta_name(const fjt_meta* meta) {
    return meta == nullptr ? nullptr : meta->name.c_str();
}

FJT_API const char* FJT_CALL fjt_meta_info_hash(const fjt_meta* meta) {
    return meta == nullptr ? nullptr : meta->info_hash.c_str();
}

FJT_API int64_t FJT_CALL fjt_meta_total_size(const fjt_meta* meta) {
    return meta == nullptr ? -1 : meta->total_size;
}

FJT_API int32_t FJT_CALL fjt_meta_num_files(const fjt_meta* meta) {
    return meta == nullptr ? -1 : meta->num_files;
}

FJT_API int32_t FJT_CALL fjt_meta_entry_count(const fjt_meta* meta) {
    return meta == nullptr ? -1 : static_cast<std::int32_t>(meta->entries.size());
}

FJT_API int32_t FJT_CALL fjt_meta_entry_index(const fjt_meta* meta, int32_t entry) {
    file_entry const* e = entry_at(meta, entry);
    return e == nullptr ? -1 : e->index;
}

FJT_API int64_t FJT_CALL fjt_meta_entry_size(const fjt_meta* meta, int32_t entry) {
    file_entry const* e = entry_at(meta, entry);
    return e == nullptr ? -1 : e->size;
}

FJT_API const char* FJT_CALL fjt_meta_entry_path(const fjt_meta* meta, int32_t entry) {
    file_entry const* e = entry_at(meta, entry);
    return e == nullptr ? nullptr : e->path.c_str();
}

FJT_API const uint8_t* FJT_CALL fjt_meta_torrent_data(const fjt_meta* meta, int64_t* size) {
    if (size != nullptr) *size = meta == nullptr ? 0 : static_cast<std::int64_t>(meta->data.size());
    if (meta == nullptr || meta->data.empty()) return nullptr;
    return reinterpret_cast<std::uint8_t const*>(meta->data.data());
}

FJT_API void FJT_CALL fjt_meta_free(fjt_meta* meta) {
    delete meta;
}

FJT_API fjt_magnet* FJT_CALL fjt_magnet_start(fjt_session* session, const char* magnet_uri, const char* scratch_dir, char* err, int32_t err_cap) {
    clear_text(err, err_cap);
    if (session == nullptr || magnet_uri == nullptr) {
        put_text("A magnet link is required", err, err_cap);
        return nullptr;
    }
    try {
        lt::error_code ec;
        lt::add_torrent_params atp = lt::parse_magnet_uri(magnet_uri, ec);
        if (ec) {
            put_text(ec.message(), err, err_cap);
            return nullptr;
        }
        std::shared_ptr<core> const c = session->state;
        lock_t lock(c->mutex);
        if (!c->session) {
            put_text("The torrent session is not running", err, err_cap);
            return nullptr;
        }
        if (c->local_only) keep_local(atp);
        auto job = std::make_unique<fjt_magnet>();
        job->state = c;
        job->hashes = atp.info_hashes;
        job->key = key_of(atp.info_hashes);
        auto const known = c->magnets.find(job->key);
        if (known != c->magnets.end() && known->second.handle.is_valid()) {
            ++known->second.refs;
            job->handle = known->second.handle;
            job->owned = true;
        } else {
            lt::torrent_handle h = find_existing(*c->session, atp.info_hashes);
            if (!h.is_valid()) {
                atp.save_path = scratch_dir == nullptr ? "" : scratch_dir;
                atp.flags &= ~(lt::torrent_flags::auto_managed | lt::torrent_flags::paused);
                atp.flags |= lt::torrent_flags::upload_mode | lt::torrent_flags::stop_when_ready;
                h = c->session->add_torrent(std::move(atp), ec);
                if (ec || !h.is_valid()) {
                    put_text(ec ? ec.message() : std::string("libtorrent did not accept the magnet link"), err, err_cap);
                    return nullptr;
                }
                h.resume();
                magnet_torrent temp;
                temp.handle = h;
                temp.refs = 1;
                c->magnets[job->key] = temp;
                job->owned = true;
            }
            job->handle = h;
        }
        ++c->magnet_jobs;
        return job.release();
    } catch (std::exception const& e) {
        put_text(e.what(), err, err_cap);
    } catch (...) {
        put_text("The magnet link could not be started", err, err_cap);
    }
    return nullptr;
}

FJT_API int32_t FJT_CALL fjt_magnet_poll(fjt_magnet* job, char* err, int32_t err_cap) {
    clear_text(err, err_cap);
    if (job == nullptr) return FJT_E_ARG;
    try {
        core& c = *job->state;
        lock_t lock(c.mutex);
        if (!c.session) {
            put_text("The torrent session was closed", err, err_cap);
            return FJT_E_SESSION;
        }
        drain_if_due(c);
        if (!job->handle.is_valid()) job->handle = find_existing(*c.session, job->hashes);
        if (!job->handle.is_valid()) {
            put_text("The torrent was removed", err, err_cap);
            return FJT_E_FAILED;
        }
        return job->handle.torrent_file() ? FJT_OK : FJT_PENDING;
    } catch (std::exception const& e) {
        put_text(e.what(), err, err_cap);
    } catch (...) {
        put_text("The magnet link failed", err, err_cap);
    }
    return FJT_E_FAILED;
}

FJT_API fjt_meta* FJT_CALL fjt_magnet_take(fjt_magnet* job) {
    if (job == nullptr) return nullptr;
    try {
        core& c = *job->state;
        lock_t lock(c.mutex);
        if (!c.session || !job->handle.is_valid()) return nullptr;
        std::shared_ptr<lt::torrent_info const> const ti = job->handle.torrent_file();
        if (!ti) return nullptr;
        lt::add_torrent_params atp;
        atp.ti = std::const_pointer_cast<lt::torrent_info>(ti);
        for (auto const& tracker : job->handle.trackers()) {
            atp.trackers.push_back(tracker.url);
            atp.tracker_tiers.push_back(tracker.tier);
        }
        for (auto const& seed : job->handle.url_seeds()) atp.url_seeds.push_back(seed);
        auto meta = std::make_unique<fjt_meta>();
        fill_meta(*meta, *ti);
        meta->data = lt::write_torrent_file_buf(atp, lt::write_flags::allow_missing_piece_layer);
        return meta.release();
    } catch (...) {
        return nullptr;
    }
}

FJT_API void FJT_CALL fjt_magnet_cancel(fjt_magnet* job) {
    if (job == nullptr) return;
    try {
        core& c = *job->state;
        lock_t lock(c.mutex);
        if (c.magnet_jobs > 0) --c.magnet_jobs;
        if (c.session && job->owned) {
            auto const it = c.magnets.find(job->key);
            if (it != c.magnets.end() && --it->second.refs <= 0) {
                lt::torrent_handle const h = it->second.handle;
                c.magnets.erase(it);
                if (h.is_valid()) c.session->remove_torrent(h, lt::session_handle::delete_files);
            }
        }
    } catch (...) {
    }
    delete job;
}

FJT_API int32_t FJT_CALL fjt_torrent_add(
    fjt_session* session,
    const char* task_id,
    const uint8_t* torrent,
    int64_t torrent_size,
    const uint8_t* resume,
    int64_t resume_size,
    const char* save_path,
    const uint8_t* file_priorities,
    int32_t priority_count,
    int32_t add_flags,
    int64_t download_limit,
    int64_t upload_limit,
    const char* extra_trackers,
    char* err,
    int32_t err_cap) {
    clear_text(err, err_cap);
    if (session == nullptr || task_id == nullptr || task_id[0] == '\0') {
        put_text("A task id is required", err, err_cap);
        return FJT_E_ARG;
    }
    try {
        std::shared_ptr<core> const c = session->state;
        lock_t lock(c->mutex);
        if (!c->session) {
            put_text("The torrent session is not running", err, err_cap);
            return FJT_E_SESSION;
        }
        std::vector<lt::download_priority_t> const priorities = priorities_of(file_priorities, priority_count);
        bool const disable_upload = (add_flags & FJT_ADD_DISABLE_UPLOAD) != 0;
        auto const known = c->tasks.find(task_id);
        if (known != c->tasks.end()) {
            if (known->second.handle.is_valid()) {
                torrent_entry& entry = known->second;
                entry.disable_upload = disable_upload;
                if (!priorities.empty()) prioritize(&entry, entry.handle, priorities);
                apply_limits(entry, download_limit, upload_limit);
                wake(entry);
                return FJT_OK;
            }
            c->tasks.erase(known);
        }
        if (torrent == nullptr || torrent_size <= 0) {
            put_text("Not a torrent file", err, err_cap);
            return FJT_E_PARSE;
        }
        lt::error_code ec;
        lt::add_torrent_params atp = lt::load_torrent_buffer(span_of(torrent, torrent_size), ec, lt::load_torrent_limits{});
        if (ec || !atp.ti) {
            put_text(ec ? ec.message() : std::string("Not a torrent file"), err, err_cap);
            return FJT_E_PARSE;
        }
        lt::info_hash_t const ih = atp.ti->info_hashes();
        lt::torrent_handle h = find_existing(*c->session, ih);
        if (h.is_valid() && is_magnet_torrent(*c, h)) {
            drop_magnet_torrent(*c, h);
            h = lt::torrent_handle();
        }
        if (h.is_valid()) {
            if (!priorities.empty()) prioritize(nullptr, h, priorities);
        } else {
            if (resume != nullptr && resume_size > 0) {
                lt::error_code resume_ec;
                lt::add_torrent_params saved = lt::read_resume_data(span_of(resume, resume_size), resume_ec);
                if (!resume_ec && saved.info_hashes == ih) {
                    saved.ti = atp.ti;
                    if (saved.trackers.empty()) {
                        saved.trackers = atp.trackers;
                        saved.tracker_tiers = atp.tracker_tiers;
                    }
                    if (saved.url_seeds.empty()) saved.url_seeds = atp.url_seeds;
                    if (saved.merkle_trees.empty()) {
                        saved.merkle_trees = atp.merkle_trees;
                        saved.merkle_tree_mask = atp.merkle_tree_mask;
                        saved.verified_leaf_hashes = atp.verified_leaf_hashes;
                    }
                    atp = std::move(saved);
                }
            }
            if (c->local_only) keep_local(atp);
            atp.save_path = save_path == nullptr ? "" : save_path;
            atp.flags &= ~(lt::torrent_flags::paused | lt::torrent_flags::auto_managed);
            if ((add_flags & FJT_ADD_SEQUENTIAL) != 0) atp.flags |= lt::torrent_flags::sequential_download;
            if (!priorities.empty()) {
                atp.file_priorities.clear();
                for (lt::download_priority_t const p : priorities) atp.file_priorities.push_back(p);
            }
            h = c->session->add_torrent(std::move(atp), ec);
            if (ec || !h.is_valid()) {
                put_text(ec ? ec.message() : std::string("libtorrent did not accept the torrent"), err, err_cap);
                return FJT_E_FAILED;
            }
        }
        std::vector<std::string> const extra = lines_of(extra_trackers);
        if (!extra.empty()) {
            std::set<std::string> present;
            for (auto const& tracker : h.trackers()) present.insert(lower(tracker.url));
            for (std::string const& line : extra) {
                std::string const url = trimmed(line);
                if (url.empty() || (c->local_only && !is_local_url(url))) continue;
                if (!present.insert(lower(url)).second) continue;
                h.add_tracker(lt::announce_entry(url));
            }
        }
        h.force_reannounce();
        h.force_dht_announce();
        torrent_entry entry;
        entry.handle = h;
        entry.disable_upload = disable_upload;
        apply_limits(entry, download_limit, upload_limit);
        h.resume();
        c->tasks[task_id] = entry;
        return FJT_OK;
    } catch (std::exception const& e) {
        put_text(e.what(), err, err_cap);
    } catch (...) {
        put_text("libtorrent did not accept the torrent", err, err_cap);
    }
    return FJT_E_FAILED;
}

FJT_API int32_t FJT_CALL fjt_torrent_pause(fjt_session* session, const char* task_id) {
    if (session == nullptr || task_id == nullptr) return FJT_E_ARG;
    try {
        core& c = *session->state;
        lock_t lock(c.mutex);
        if (!c.session) return FJT_E_SESSION;
        torrent_entry* entry = find_task(c, task_id);
        if (entry == nullptr) return FJT_E_NOT_FOUND;
        entry->handle.pause();
        return FJT_OK;
    } catch (...) {
        return FJT_E_FAILED;
    }
}

FJT_API int32_t FJT_CALL fjt_torrent_resume(fjt_session* session, const char* task_id) {
    if (session == nullptr || task_id == nullptr) return FJT_E_ARG;
    try {
        core& c = *session->state;
        lock_t lock(c.mutex);
        if (!c.session) return FJT_E_SESSION;
        torrent_entry* entry = find_task(c, task_id);
        if (entry == nullptr) return FJT_E_NOT_FOUND;
        wake(*entry);
        return FJT_OK;
    } catch (...) {
        return FJT_E_FAILED;
    }
}

FJT_API int32_t FJT_CALL fjt_torrent_remove(fjt_session* session, const char* task_id, int32_t delete_files) {
    if (session == nullptr || task_id == nullptr) return FJT_E_ARG;
    try {
        core& c = *session->state;
        lock_t lock(c.mutex);
        if (!c.session) return FJT_E_SESSION;
        auto const it = c.tasks.find(task_id);
        if (it == c.tasks.end()) return FJT_E_NOT_FOUND;
        lt::torrent_handle const h = it->second.handle;
        c.tasks.erase(it);
        bool shared = false;
        for (auto const& item : c.tasks) {
            if (item.second.handle == h) shared = true;
        }
        if (!shared) {
            c.resume_pending.erase(h);
            c.resume_results.erase(h);
            c.flushed.erase(h);
            c.flush_asked.erase(h);
            if (h.is_valid()) {
                c.session->remove_torrent(h, delete_files != 0 ? lt::session_handle::delete_files : lt::remove_flags_t{});
            }
        }
        return FJT_OK;
    } catch (...) {
        return FJT_E_FAILED;
    }
}

FJT_API int32_t FJT_CALL fjt_torrent_set_file_priorities(fjt_session* session, const char* task_id, const uint8_t* file_priorities, int32_t priority_count) {
    if (session == nullptr || task_id == nullptr || file_priorities == nullptr || priority_count <= 0) return FJT_E_ARG;
    try {
        core& c = *session->state;
        lock_t lock(c.mutex);
        if (!c.session) return FJT_E_SESSION;
        torrent_entry* entry = find_task(c, task_id);
        if (entry == nullptr) return FJT_E_NOT_FOUND;
        prioritize(entry, entry->handle, priorities_of(file_priorities, priority_count));
        return FJT_OK;
    } catch (...) {
        return FJT_E_FAILED;
    }
}

FJT_API int32_t FJT_CALL fjt_torrent_set_limits(fjt_session* session, const char* task_id, int64_t download_limit, int64_t upload_limit) {
    if (session == nullptr || task_id == nullptr) return FJT_E_ARG;
    try {
        core& c = *session->state;
        lock_t lock(c.mutex);
        if (!c.session) return FJT_E_SESSION;
        torrent_entry* entry = find_task(c, task_id);
        if (entry == nullptr) return FJT_E_NOT_FOUND;
        apply_limits(*entry, download_limit, upload_limit);
        return FJT_OK;
    } catch (...) {
        return FJT_E_FAILED;
    }
}

FJT_API int32_t FJT_CALL fjt_torrent_set_sequential(fjt_session* session, const char* task_id, int32_t on) {
    if (session == nullptr || task_id == nullptr) return FJT_E_ARG;
    try {
        core& c = *session->state;
        lock_t lock(c.mutex);
        if (!c.session) return FJT_E_SESSION;
        torrent_entry* entry = find_task(c, task_id);
        if (entry == nullptr) return FJT_E_NOT_FOUND;
        if (on != 0) entry->handle.set_flags(lt::torrent_flags::sequential_download);
        else entry->handle.unset_flags(lt::torrent_flags::sequential_download);
        return FJT_OK;
    } catch (...) {
        return FJT_E_FAILED;
    }
}

FJT_API int32_t FJT_CALL fjt_torrent_status(
    fjt_session* session,
    const char* task_id,
    int64_t* status,
    int32_t status_count,
    int64_t* file_progress,
    int32_t file_capacity,
    char* err,
    int32_t err_cap) {
    clear_text(err, err_cap);
    if (session == nullptr || task_id == nullptr || status == nullptr || status_count < FJT_ST_COUNT) return FJT_E_ARG;
    try {
        core& c = *session->state;
        lock_t lock(c.mutex);
        if (!c.session) return FJT_E_SESSION;
        torrent_entry* entry = find_task(c, task_id);
        if (entry == nullptr) return FJT_E_NOT_FOUND;
        drain_if_due(c);
        lt::torrent_handle const& h = entry->handle;
        lt::torrent_status const st = h.status(lt::torrent_handle::query_accurate_download_counters);
        nudge_if_stuck(*entry, st);
        refill_if_no_candidates(*entry, st);
        bool on_disk = false;
        if (st.is_finished) {
            if (c.flushed.count(h) == 0) {
                if (c.flush_asked.insert(h).second) h.flush_cache();
                drain(c);
            }
            on_disk = c.flushed.count(h) != 0;
        } else {
            c.flushed.erase(h);
            c.flush_asked.erase(h);
        }
        std::int64_t flags = 0;
        if (st.flags & lt::torrent_flags::paused) flags |= FJT_F_PAUSED;
        if (st.is_finished && on_disk) flags |= FJT_F_FINISHED;
        if (st.is_seeding && on_disk) flags |= FJT_F_SEEDING;
        if (st.has_metadata) flags |= FJT_F_HAS_METADATA;
        if (st.errc) flags |= FJT_F_ERROR;
        if (st.flags & lt::torrent_flags::need_save_resume) flags |= FJT_F_NEED_SAVE_RESUME;
        std::int32_t files = 0;
        if (st.has_metadata) {
            std::vector<std::int64_t> const progress = h.file_progress();
            files = static_cast<std::int32_t>(progress.size());
            if (file_progress != nullptr) {
                std::int32_t const copied = std::min<std::int32_t>(files, std::max<std::int32_t>(file_capacity, 0));
                for (std::int32_t i = 0; i < copied; ++i) file_progress[i] = progress[static_cast<std::size_t>(i)];
            }
        }
        for (std::int32_t i = 0; i < FJT_ST_COUNT; ++i) status[i] = 0;
        status[FJT_ST_STATE] = state_code(st.state);
        status[FJT_ST_FLAGS] = flags;
        status[FJT_ST_TOTAL_WANTED] = st.total_wanted;
        status[FJT_ST_TOTAL_WANTED_DONE] = st.total_wanted_done;
        status[FJT_ST_DOWNLOAD_RATE] = st.download_payload_rate;
        status[FJT_ST_UPLOAD_RATE] = st.upload_payload_rate;
        status[FJT_ST_PEERS] = st.num_peers;
        status[FJT_ST_SEEDS] = st.num_seeds;
        status[FJT_ST_NUM_FILES] = files;
        status[FJT_ST_CONNECTIONS] = st.num_connections;
        status[FJT_ST_CONNECT_CANDIDATES] = st.connect_candidates;
        status[FJT_ST_LIST_PEERS] = st.list_peers;
        status[FJT_ST_LIST_SEEDS] = st.list_seeds;
        status[FJT_ST_UPLOAD_LIMIT] = entry->upload_limit;
        status[FJT_ST_TOTAL_UPLOAD] = st.total_payload_upload;
        if (st.errc) put_text(st.errc.message(), err, err_cap);
        return files;
    } catch (std::exception const& e) {
        put_text(e.what(), err, err_cap);
    } catch (...) {
    }
    return FJT_E_FAILED;
}

FJT_API int32_t FJT_CALL fjt_torrent_debug_text(fjt_session* session, const char* task_id, char* out, int32_t cap) {
    clear_text(out, cap);
    if (session == nullptr || task_id == nullptr) return FJT_E_ARG;
    try {
        core& c = *session->state;
        lock_t lock(c.mutex);
        if (!c.session) return FJT_E_SESSION;
        torrent_entry* entry = find_task(c, task_id);
        if (entry == nullptr) return FJT_E_NOT_FOUND;
        lt::torrent_handle const& h = entry->handle;
        lt::torrent_status const st = h.status(lt::status_flags_t{});
        std::string line = "state=";
        line += state_name(state_code(st.state));
        line += " peers=" + std::to_string(st.num_peers);
        line += " seeds=" + std::to_string(st.num_seeds);
        auto const seeds = h.url_seeds();
        line += " webSeeds=" + std::to_string(seeds.size());
        if (!seeds.empty()) {
            line += "[";
            bool first_seed = true;
            for (auto const& seed : seeds) {
                if (!first_seed) line += ",";
                first_seed = false;
                line += std::string(seed).substr(0, 60);
            }
            line += "]";
        }
        std::vector<lt::peer_info> peers;
        h.get_peer_info(peers);
        int web_connections = 0;
        std::int64_t web_rate = 0;
        for (lt::peer_info const& peer : peers) {
            if (peer.connection_type == lt::peer_info::web_seed || peer.connection_type == lt::peer_info::http_seed) {
                ++web_connections;
                web_rate += peer.down_speed;
            }
        }
        line += " webConns=" + std::to_string(web_connections);
        line += " webDown=" + std::to_string(web_rate / 1024) + "KB/s";
        line += " conns=" + std::to_string(st.num_connections);
        line += " candidates=" + std::to_string(st.connect_candidates);
        line += " listPeers=" + std::to_string(st.list_peers);
        line += " listSeeds=" + std::to_string(st.list_seeds);
        line += " down=" + std::to_string(st.download_payload_rate / 1024) + "KB/s";
        line += " up=" + std::to_string(st.upload_payload_rate / 1024) + "KB/s";
        c.session->post_session_stats();
        int const port = c.session->listen_port();
        bool const dht = c.session->is_dht_running();
        drain(c);
        line += std::string(" dht=") + (dht ? "true" : "false");
        line += " dhtNodes=" + std::to_string(c.dht_nodes);
        line += " listenPort=" + std::to_string(port);
        line += " upLimit=" + std::to_string(entry->upload_limit);
        if (c.local_only) line += " localOnly=true";
        line += " trackers: ";
        bool first_tracker = true;
        for (lt::announce_entry const& tracker : h.trackers()) {
            if (!first_tracker) line += " | ";
            first_tracker = false;
            line += host_of(tracker.url) + "=" + tracker_note(tracker);
        }
        return put_text(line, out, cap);
    } catch (...) {
        return FJT_E_FAILED;
    }
}

FJT_API fjt_buf* FJT_CALL fjt_torrent_save_resume(fjt_session* session, const char* task_id, int32_t timeout_ms) {
    if (session == nullptr || task_id == nullptr) return nullptr;
    try {
        std::shared_ptr<core> const c = session->state;
        std::shared_ptr<lt::session> running;
        lt::torrent_handle h;
        {
            lock_t lock(c->mutex);
            if (!c->session) return nullptr;
            torrent_entry* entry = find_task(*c, task_id);
            if (entry == nullptr) return nullptr;
            h = entry->handle;
            running = c->session;
            drain(*c);
            c->resume_results.erase(h);
            c->resume_pending.insert(h);
            h.save_resume_data(lt::torrent_handle::flush_disk_cache);
        }
        auto const deadline = clock_type::now() + std::chrono::milliseconds(std::max<std::int32_t>(timeout_ms, 0));
        for (;;) {
            {
                lock_t lock(c->mutex);
                if (!c->session) return nullptr;
                drain(*c);
                auto const done = c->resume_results.find(h);
                if (done != c->resume_results.end()) {
                    resume_result result = std::move(done->second);
                    c->resume_results.erase(done);
                    c->resume_pending.erase(h);
                    if (!result.ok || result.data.empty()) return nullptr;
                    auto buf = std::make_unique<fjt_buf>();
                    buf->data = std::move(result.data);
                    return buf.release();
                }
                if (clock_type::now() >= deadline) {
                    c->resume_pending.erase(h);
                    return nullptr;
                }
            }
            auto const left = std::chrono::duration_cast<std::chrono::milliseconds>(deadline - clock_type::now());
            auto const step = std::min(std::max(left, std::chrono::milliseconds(1)), std::chrono::milliseconds(100));
            running->wait_for_alert(step);
        }
    } catch (...) {
        return nullptr;
    }
}

FJT_API const uint8_t* FJT_CALL fjt_buf_data(const fjt_buf* buf) {
    if (buf == nullptr || buf->data.empty()) return nullptr;
    return reinterpret_cast<std::uint8_t const*>(buf->data.data());
}

FJT_API int64_t FJT_CALL fjt_buf_size(const fjt_buf* buf) {
    return buf == nullptr ? 0 : static_cast<std::int64_t>(buf->data.size());
}

FJT_API void FJT_CALL fjt_buf_free(fjt_buf* buf) {
    delete buf;
}

}
