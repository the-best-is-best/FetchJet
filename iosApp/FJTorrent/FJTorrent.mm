#import "FJTorrent.h"

#include <libtorrent/session.hpp>
#include <libtorrent/session_params.hpp>
#include <libtorrent/settings_pack.hpp>
#include <libtorrent/add_torrent_params.hpp>
#include <libtorrent/torrent_handle.hpp>
#include <libtorrent/torrent_info.hpp>
#include <libtorrent/torrent_status.hpp>
#include <libtorrent/magnet_uri.hpp>
#include <libtorrent/load_torrent.hpp>
#include <libtorrent/write_resume_data.hpp>
#include <libtorrent/announce_entry.hpp>
#include <libtorrent/peer_info.hpp>
#include <libtorrent/download_priority.hpp>
#include <libtorrent/version.hpp>

#include <cctype>
#include <map>
#include <memory>
#include <mutex>
#include <set>
#include <string>
#include <utility>
#include <vector>

static NSString *const FJTorrentErrorDomain = @"FJTorrent";

static NSString *FJString(std::string const &s) {
    return [[NSString alloc] initWithBytes:s.data() length:s.size() encoding:NSUTF8StringEncoding] ?: @"";
}

static void FJSetError(NSError **error, NSString *message) {
    if (error)
        *error = [NSError errorWithDomain:FJTorrentErrorDomain code:1
                                 userInfo:@{NSLocalizedDescriptionKey: message}];
}

static std::string FJHex(lt::sha1_hash const &h) {
    static char const digits[] = "0123456789abcdef";
    std::string out;
    out.reserve(40);
    for (auto b: h) {
        auto v = static_cast<unsigned char>(b);
        out.push_back(digits[v >> 4]);
        out.push_back(digits[v & 0xf]);
    }
    return out;
}

static std::string FJKey(lt::info_hash_t const &ih) {
    return FJHex(ih.get_best());
}

static lt::span<char const> FJSpan(NSData *data) {
    return {static_cast<char const *>(data.bytes), static_cast<std::ptrdiff_t>(data.length)};
}

static std::pair<std::string, std::string> FJTrackerState(lt::announce_entry const &t) {
    bool anyEnabled = false, working = false, updating = false, failed = false;
    std::string okMessage, errorMessage;
    for (auto const &ep: t.endpoints) {
        if (!ep.enabled) continue;
        anyEnabled = true;
        for (auto const &ih: ep.info_hashes) {
            if (ih.updating) updating = true;
            if (ih.fails > 0) {
                failed = true;
                if (errorMessage.empty()) {
                    if (!ih.message.empty()) errorMessage = ih.message;
                    else if (ih.last_error) errorMessage = ih.last_error.message();
                }
            } else if (ih.start_sent) {
                working = true;
                if (okMessage.empty()) okMessage = ih.message;
            }
        }
    }
    if (!t.endpoints.empty() && !anyEnabled) return {"disabled", ""};
    if (working) return {"working", okMessage};
    if (updating) return {"updating", ""};
    if (failed) return {"error", errorMessage};
    return {"waiting", ""};
}

static bool FJTrackerUrlAllowed(std::string const &url) {
    static char const *const schemes[] = {"http://", "https://", "udp://", "ws://", "wss://"};
    for (char c: url) {
        auto v = static_cast<unsigned char>(c);
        if (v <= 0x20 || v == 0x7f) return false;
    }
    for (char const *scheme: schemes) {
        std::string const prefix(scheme);
        if (url.size() <= prefix.size()) continue;
        bool same = true;
        for (std::size_t i = 0; i < prefix.size() && same; ++i) {
            same = std::tolower(static_cast<unsigned char>(url[i])) == prefix[i];
        }
        if (!same) continue;
        char const first = url[prefix.size()];
        return first != '/' && first != ':' && first != '?' && first != '#';
    }
    return false;
}

@interface FJTorrentFile ()
@property(nonatomic, readwrite) NSInteger index;
@property(nonatomic, readwrite, copy) NSString *path;
@property(nonatomic, readwrite) int64_t size;
@end

@implementation FJTorrentFile
@end

@interface FJTorrentInfo ()
@property(nonatomic, readwrite, copy) NSString *name;
@property(nonatomic, readwrite, copy) NSString *infoHash;
@property(nonatomic, readwrite) NSInteger fileCount;
@property(nonatomic, readwrite, copy) NSArray<FJTorrentFile *> *files;
@property(nonatomic, readwrite) int64_t totalSize;
@property(nonatomic, readwrite, copy) NSData *torrentData;
@end

@implementation FJTorrentInfo

+ (instancetype)infoWith:(lt::torrent_info const &)ti hashes:(lt::info_hash_t const &)ih data:(NSData *)data {
    FJTorrentInfo *info = [FJTorrentInfo new];
    info.name = FJString(ti.name());
    info.infoHash = FJString(FJKey(ih));
    lt::file_storage const &fs = ti.layout();
    info.fileCount = fs.num_files();
    NSMutableArray < FJTorrentFile * > *files = [NSMutableArray arrayWithCapacity:fs.num_files()];
    int64_t total = 0;
    for (int i = 0; i < fs.num_files(); ++i) {
        lt::file_index_t idx(i);
        if (fs.pad_file_at(idx)) continue;
        FJTorrentFile *f = [FJTorrentFile new];
        f.index = i;
        f.path = FJString(fs.file_path(idx));
        f.size = fs.file_size(idx);
        total += f.size;
        [files addObject:f];
    }
    info.files = files;
    info.totalSize = total;
    info.torrentData = data;
    return info;
}

@end

@interface FJTorrentStatus ()
@property(nonatomic, readwrite) FJTorrentState state;
@property(nonatomic, readwrite) BOOL isPaused;
@property(nonatomic, readwrite) BOOL isFinished;
@property(nonatomic, readwrite) BOOL isSeeding;
@property(nonatomic, readwrite) BOOL hasMetadata;
@property(nonatomic, readwrite, copy, nullable) NSString *errorMessage;
@property(nonatomic, readwrite) int64_t totalWanted;
@property(nonatomic, readwrite) int64_t totalWantedDone;
@property(nonatomic, readwrite) int64_t downloadRate;
@property(nonatomic, readwrite) int64_t uploadRate;
@property(nonatomic, readwrite) int32_t peers;
@property(nonatomic, readwrite) int32_t seeds;
@property(nonatomic, readwrite, copy) NSArray<NSNumber *> *fileProgress;
@property(nonatomic, readwrite) int64_t totalUploaded;
@property(nonatomic, readwrite) int64_t totalDownloaded;
@end

@implementation FJTorrentStatus
@end

@interface FJTorrentTracker ()
@property(nonatomic, readwrite, copy) NSString *url;
@property(nonatomic, readwrite, copy) NSString *state;
@property(nonatomic, readwrite, copy) NSString *message;
@end

@implementation FJTorrentTracker
@end

@interface FJTorrentPeer ()
@property(nonatomic, readwrite, copy) NSString *address;
@property(nonatomic, readwrite, copy) NSString *client;
@property(nonatomic, readwrite) int64_t downloadRate;
@property(nonatomic, readwrite) int64_t uploadRate;
@property(nonatomic, readwrite) float progress;
@property(nonatomic, readwrite) BOOL isSeed;
@end

@implementation FJTorrentPeer
@end

@interface FJTorrentPieces ()
@property(nonatomic, readwrite) int32_t count;
@property(nonatomic, readwrite) int32_t have;
@property(nonatomic, readwrite) int64_t pieceLength;
@end

@implementation FJTorrentPieces
@end

@implementation FJTorrentSession {
    std::unique_ptr <lt::session> _session;
    std::map <std::string, lt::torrent_handle> _handles;
    std::recursive_mutex _mutex;
}

+ (NSString *)libtorrentVersion {
    return @(lt::version_str);
}

- (instancetype)initWithListenPort:(NSInteger)port
                         userAgent:(NSString *)userAgent
                   peerFingerprint:(NSString *)fingerprint
                             error:(NSError **)error {
    self = [super init];
    if (!self) return nil;
    try {
        lt::settings_pack pack;
        std::string const p = std::to_string(port);
        pack.set_str(lt::settings_pack::listen_interfaces, "0.0.0.0:" + p + ",[::]:" + p);
        pack.set_int(lt::settings_pack::max_retry_port_bind, 10);
        pack.set_str(lt::settings_pack::user_agent, userAgent.UTF8String);
        pack.set_str(lt::settings_pack::peer_fingerprint, fingerprint.UTF8String);
        pack.set_bool(lt::settings_pack::enable_dht, true);
        pack.set_bool(lt::settings_pack::enable_lsd, true);
        pack.set_bool(lt::settings_pack::enable_upnp, true);
        pack.set_bool(lt::settings_pack::enable_natpmp, true);
        pack.set_bool(lt::settings_pack::enable_outgoing_utp, true);
        pack.set_bool(lt::settings_pack::enable_incoming_utp, true);
        pack.set_int(lt::settings_pack::out_enc_policy, lt::settings_pack::pe_enabled);
        pack.set_int(lt::settings_pack::in_enc_policy, lt::settings_pack::pe_enabled);
        pack.set_bool(lt::settings_pack::validate_https_trackers, false);
        pack.set_int(lt::settings_pack::active_limit, 20);
        pack.set_int(lt::settings_pack::active_downloads, 10);
        pack.set_int(lt::settings_pack::active_seeds, 10);
        pack.set_int(lt::settings_pack::alert_mask, lt::alert_category::error);
        _session = std::make_unique<lt::session>(lt::session_params(pack));
    } catch (std::exception const &e) {
        FJSetError(error, FJString(e.what()));
        return nil;
    }
    return self;
}

+ (nullable FJTorrentInfo *)infoFromTorrentData:(NSData *)data error:(NSError **)error {
    try {
        lt::error_code ec;
        lt::add_torrent_params atp = lt::load_torrent_buffer(FJSpan(data), ec, lt::load_torrent_limits{});
        if (ec || !atp.ti) {
            FJSetError(error, ec ? FJString(ec.message()) : @"Not a torrent file");
            return nil;
        }
        return [FJTorrentInfo infoWith:*atp.ti hashes:atp.ti->info_hashes() data:data];
    } catch (std::exception const &e) {
        FJSetError(error, FJString(e.what()));
        return nil;
    }
}

- (lt::torrent_handle)handleFor:(NSString *)key {
    std::lock_guard <std::recursive_mutex> lock(_mutex);
    auto it = _handles.find(key.UTF8String);
    if (it == _handles.end() || !it->second.is_valid()) return {};
    return it->second;
}

- (nullable NSString *)registered:(lt::torrent_handle const &)h hashes:(lt::info_hash_t const &)ih {
    if (!h.is_valid()) return nil;
    std::string const key = FJKey(ih);
    std::lock_guard <std::recursive_mutex> lock(_mutex);
    _handles[key] = h;
    return FJString(key);
}

- (nullable NSString *)existing:(lt::info_hash_t const &)ih {
    if (!_session) return nil;
    lt::torrent_handle h;
    if (ih.has_v1()) h = _session->find_torrent(ih.v1);
    if (!h.is_valid() && ih.has_v2()) h = _session->find_torrent(ih.v2);
    if (!h.is_valid()) return nil;
    return [self registered:h hashes:ih];
}

- (nullable NSString *)findMagnet:(NSString *)uri {
    try {
        lt::error_code ec;
        lt::add_torrent_params atp = lt::parse_magnet_uri(uri.UTF8String, ec);
        if (ec) return nil;
        return [self existing:atp.info_hashes];
    } catch (...) {
        return nil;
    }
}

- (nullable NSString *)findTorrentData:(NSData *)data {
    try {
        lt::error_code ec;
        lt::add_torrent_params atp = lt::load_torrent_buffer(FJSpan(data), ec, lt::load_torrent_limits{});
        if (ec || !atp.ti) return nil;
        return [self existing:atp.ti->info_hashes()];
    } catch (...) {
        return nil;
    }
}

- (nullable NSString *)addMagnet:(NSString *)uri savePath:(NSString *)savePath error:(NSError **)error {
    if (!_session) {
        FJSetError(error, @"The torrent session is not running");
        return nil;
    }
    try {
        lt::error_code ec;
        lt::add_torrent_params atp = lt::parse_magnet_uri(uri.UTF8String, ec);
        if (ec) {
            FJSetError(error, FJString(ec.message()));
            return nil;
        }
        atp.save_path = savePath.UTF8String;
        atp.flags &= ~(lt::torrent_flags::paused | lt::torrent_flags::auto_managed);
        lt::info_hash_t const ih = atp.info_hashes;
        lt::torrent_handle h = _session->add_torrent(std::move(atp), ec);
        if (ec) {
            FJSetError(error, FJString(ec.message()));
            return nil;
        }
        return [self registered:h hashes:ih];
    } catch (std::exception const &e) {
        FJSetError(error, FJString(e.what()));
        return nil;
    }
}

- (nullable NSString *)addTorrentData:(NSData *)data
                             savePath:(NSString *)savePath
                       filePriorities:(NSArray

<NSNumber *> *)
priorities
        error
:(NSError **)error {
    if (!_session) {
        FJSetError(error, @"The torrent session is not running");
        return nil;
    }
    try {
        lt::error_code ec;
        lt::add_torrent_params atp = lt::load_torrent_buffer(FJSpan(data));
        atp.save_path = savePath.UTF8String;
        atp.flags &= ~(lt::torrent_flags::paused | lt::torrent_flags::auto_managed);
        atp.file_priorities.clear();
        for (NSNumber *p in priorities) {
            atp.file_priorities.push_back(lt::download_priority_t(p.unsignedCharValue));
        }
        lt::info_hash_t const ih = atp.ti ? atp.ti->info_hashes() : atp.info_hashes;
        lt::torrent_handle h = _session->add_torrent(std::move(atp), ec);
        if (ec) {
            FJSetError(error, FJString(ec.message()));
            return nil;
        }
        return [self registered:h hashes:ih];
    } catch (std::exception const &e) {
        FJSetError(error, FJString(e.what()));
        return nil;
    }
}

- (nullable FJTorrentStatus *)statusFor:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    if (!h.is_valid()) return nil;
    try {
        lt::torrent_status const st = h.status();
        FJTorrentStatus *s = [FJTorrentStatus new];
        switch (st.state) {
            case lt::torrent_status::checking_files:
                s.state = FJTorrentStateCheckingFiles;
                break;
            case lt::torrent_status::checking_resume_data:
                s.state = FJTorrentStateCheckingResumeData;
                break;
            case lt::torrent_status::downloading_metadata:
                s.state = FJTorrentStateDownloadingMetadata;
                break;
            case lt::torrent_status::downloading:
                s.state = FJTorrentStateDownloading;
                break;
            case lt::torrent_status::finished:
                s.state = FJTorrentStateFinished;
                break;
            case lt::torrent_status::seeding:
                s.state = FJTorrentStateSeeding;
                break;
            default:
                s.state = FJTorrentStateConnecting;
                break;
        }
        s.isPaused = (st.flags & lt::torrent_flags::paused) ? YES : NO;
        s.isFinished = st.is_finished;
        s.isSeeding = st.is_seeding;
        s.hasMetadata = st.has_metadata;
        s.errorMessage = st.errc ? FJString(st.errc.message()) : nil;
        s.totalWanted = st.total_wanted;
        s.totalWantedDone = st.total_wanted_done;
        s.downloadRate = st.download_payload_rate;
        s.uploadRate = st.upload_payload_rate;
        s.peers = st.num_peers;
        s.seeds = st.num_seeds;
        s.totalUploaded = st.all_time_upload;
        s.totalDownloaded = st.all_time_download;
        if (st.has_metadata) {
            std::vector <std::int64_t> const progress = h.file_progress();
            NSMutableArray < NSNumber * > *list = [NSMutableArray arrayWithCapacity:progress.size()];
            for (auto v: progress) [list addObject:@(v)];
            s.fileProgress = list;
        } else {
            s.fileProgress = @[];
        }
        return s;
    } catch (...) {
        return nil;
    }
}

- (nullable FJTorrentInfo *)infoFor:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    if (!h.is_valid()) return nil;
    try {
        std::shared_ptr < lt::torrent_info const> ti = h.torrent_file();
        if (!ti) return nil;
        lt::add_torrent_params atp;
        atp.ti = std::const_pointer_cast<lt::torrent_info>(ti);
        for (auto const &t: h.trackers()) {
            atp.trackers.push_back(t.url);
            atp.tracker_tiers.push_back(t.tier);
        }
        std::vector<char> const buf = lt::write_torrent_file_buf(atp, lt::write_flags::allow_missing_piece_layer);
        NSData *data = [NSData dataWithBytes:buf.data() length:buf.size()];
        return [FJTorrentInfo infoWith:*ti hashes:ti->info_hashes() data:data];
    } catch (...) {
        return nil;
    }
}

- (void)pause:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    try {if (h.is_valid()) h.pause();} catch (...) {}
}

- (void)resume:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    try {if (h.is_valid()) h.resume();} catch (...) {}
}

- (void)remove:(NSString *)key deleteFiles:(BOOL)deleteFiles {
    lt::torrent_handle h = [self handleFor:key];
    {
        std::lock_guard <std::recursive_mutex> lock(_mutex);
        _handles.erase(key.UTF8String);
    }
    if (!h.is_valid() || !_session) return;
    try {
        _session->remove_torrent(h, deleteFiles ? lt::session_handle::delete_files : lt::remove_flags_t{});
    } catch (...) {}
}

- (void)setFilePriorities:(NSArray

<NSNumber *> *)priorities for:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    if (!h.is_valid()) return;
    try {
        std::vector <lt::download_priority_t> list;
        list.reserve(priorities.count);
        for (NSNumber *p in priorities) list.push_back(lt::download_priority_t(p.unsignedCharValue));
        h.prioritize_files(list);
    } catch (...) {}
}

- (void)setDownloadLimit:(int64_t)download uploadLimit:(int64_t)upload for:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    if (!h.is_valid()) return;
    auto clamp = [](int64_t v) {
        return v <= 0 ? 0 : (int) std::min<int64_t>(v, INT32_MAX);
    };
    try {
        h.set_download_limit(clamp(download));
        h.set_upload_limit(clamp(upload));
    } catch (...) {}
}

- (NSArray<FJTorrentTracker *> *)trackersFor:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    if (!h.is_valid()) return @[];
    try {
        NSMutableArray<FJTorrentTracker *> *list = [NSMutableArray array];
        for (lt::announce_entry const &t: h.trackers()) {
            std::pair<std::string, std::string> const state = FJTrackerState(t);
            FJTorrentTracker *tracker = [FJTorrentTracker new];
            tracker.url = FJString(t.url);
            tracker.state = FJString(state.first);
            tracker.message = FJString(state.second);
            [list addObject:tracker];
        }
        return list;
    } catch (...) {
        return @[];
    }
}

- (NSArray<FJTorrentPeer *> *)peersFor:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    if (!h.is_valid()) return @[];
    try {
        std::vector<lt::peer_info> peers;
        h.get_peer_info(peers);
        NSMutableArray<FJTorrentPeer *> *list = [NSMutableArray array];
        for (lt::peer_info const &p: peers) {
            if (list.count >= 200) break;
            if (p.connection_type == lt::peer_info::web_seed || p.connection_type == lt::peer_info::http_seed) continue;
            if ((p.flags & lt::peer_info::connecting) || (p.flags & lt::peer_info::handshake)) continue;
            std::string address;
            try {
                auto const ep = p.remote_endpoint();
                address = ep.address().to_string();
                if (ep.address().is_v6()) address = "[" + address + "]";
                address += ":" + std::to_string(ep.port());
            } catch (...) {}
            FJTorrentPeer *peer = [FJTorrentPeer new];
            peer.address = FJString(address);
            peer.client = FJString(p.client);
            peer.downloadRate = p.payload_down_speed;
            peer.uploadRate = p.payload_up_speed;
            peer.progress = p.progress;
            peer.isSeed = (p.flags & lt::peer_info::seed) ? YES : NO;
            [list addObject:peer];
        }
        return list;
    } catch (...) {
        return @[];
    }
}

- (BOOL)addTracker:(NSString *)url for:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    if (!h.is_valid()) return NO;
    try {
        NSString *trimmed = [url stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
        char const *raw = trimmed.UTF8String;
        std::string const u = raw ? raw : "";
        if (!FJTrackerUrlAllowed(u)) return NO;
        h.add_tracker(lt::announce_entry(u));
        bool found = false;
        for (lt::announce_entry const &t: h.trackers()) {
            if (t.url == u) {
                found = true;
                break;
            }
        }
        if (!found) return NO;
        h.force_reannounce(0, u, lt::torrent_handle::ignore_min_interval);
        return YES;
    } catch (...) {
        return NO;
    }
}

- (BOOL)forceRecheck:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    if (!h.is_valid()) return NO;
    try {
        h.force_recheck();
        return YES;
    } catch (...) {
        return NO;
    }
}

- (nullable NSString *)magnetLinkFor:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    if (!h.is_valid()) return nil;
    try {
        lt::add_torrent_params atp;
        atp.info_hashes = h.info_hashes();
        std::shared_ptr<lt::torrent_info const> ti = h.torrent_file();
        if (ti) atp.name = ti->name();
        for (lt::announce_entry const &t: h.trackers()) atp.trackers.push_back(t.url);
        for (std::string const &seed: h.url_seeds()) atp.url_seeds.push_back(seed);
        std::string const magnet = lt::make_magnet_uri(atp);
        if (magnet.size() <= 8) return nil;
        return FJString(magnet);
    } catch (...) {
        return nil;
    }
}

- (BOOL)setFirstLastPiecesFirst:(BOOL)on for:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    if (!h.is_valid()) return NO;
    try {
        std::shared_ptr<lt::torrent_info const> ti = h.torrent_file();
        if (!ti || ti->num_pieces() <= 0) return NO;
        lt::file_storage const &fs = ti->layout();
        std::vector<lt::download_priority_t> const prios = h.get_file_priorities();
        auto filePriority = [&](int i) -> lt::download_priority_t {
            lt::file_index_t idx(i);
            if (fs.pad_file_at(idx) || fs.file_size(idx) <= 0) return lt::dont_download;
            if (i < static_cast<int>(prios.size())) return prios[static_cast<std::size_t>(i)];
            return lt::default_priority;
        };
        std::set<lt::piece_index_t> edges;
        for (int i = 0; i < fs.num_files(); ++i) {
            if (filePriority(i) == lt::dont_download) continue;
            lt::file_index_t idx(i);
            edges.insert(fs.piece_index_at_file(idx));
            edges.insert(fs.last_piece_index_at_file(idx));
        }
        std::vector<std::pair<lt::piece_index_t, lt::download_priority_t>> list;
        list.reserve(edges.size());
        for (lt::piece_index_t const piece: edges) {
            lt::download_priority_t prio = lt::top_priority;
            if (!on) {
                prio = lt::dont_download;
                int const first = static_cast<int>(fs.file_index_at_piece(piece));
                int const last = static_cast<int>(fs.last_file_index_at_piece(piece));
                for (int f = first; f <= last && f < fs.num_files(); ++f) {
                    lt::download_priority_t const p = filePriority(f);
                    if (p > prio) prio = p;
                }
            }
            list.emplace_back(piece, prio);
        }
        if (!list.empty()) h.prioritize_pieces(list);
        return YES;
    } catch (...) {
        return NO;
    }
}

- (nullable FJTorrentPieces *)piecesFor:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    if (!h.is_valid()) return nil;
    try {
        std::shared_ptr<lt::torrent_info const> ti = h.torrent_file();
        if (!ti) return nil;
        lt::torrent_status const st = h.status(lt::status_flags_t{});
        FJTorrentPieces *pieces = [FJTorrentPieces new];
        pieces.count = ti->num_pieces();
        pieces.have = st.num_pieces;
        pieces.pieceLength = ti->piece_length();
        return pieces;
    } catch (...) {
        return nil;
    }
}

- (int64_t)uploadedBytesFor:(NSString *)key {
    lt::torrent_handle h = [self handleFor:key];
    if (!h.is_valid()) return 0;
    try {
        return h.status(lt::status_flags_t{}).all_time_upload;
    } catch (...) {
        return 0;
    }
}

- (void)shutdown {
    std::unique_ptr <lt::session> session;
    {
        std::lock_guard <std::recursive_mutex> lock(_mutex);
        _handles.clear();
        session = std::move(_session);
    }
    if (!session) return;
    try {session->pause();} catch (...) {}
    lt::session *raw = session.release();
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_UTILITY, 0), ^{
        delete raw;
    });
}

- (void)dealloc {
    [self shutdown];
}

@end
