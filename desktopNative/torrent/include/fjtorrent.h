#ifndef FJTORRENT_H
#define FJTORRENT_H

#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32) && defined(FJT_BUILD)
#define FJT_API __declspec(dllexport)
#elif defined(_WIN32)
#define FJT_API __declspec(dllimport)
#elif defined(FJT_BUILD)
#define FJT_API __attribute__((visibility("default")))
#else
#define FJT_API
#endif

#if defined(_WIN32)
#define FJT_CALL __cdecl
#else
#define FJT_CALL
#endif

#ifdef __cplusplus
extern "C" {
#endif

#define FJT_ABI_VERSION 1

#define FJT_OK 0
#define FJT_PENDING 1
#define FJT_E_ARG (-1)
#define FJT_E_NOT_FOUND (-2)
#define FJT_E_PARSE (-3)
#define FJT_E_SESSION (-4)
#define FJT_E_TIMEOUT (-5)
#define FJT_E_FAILED (-6)

#define FJT_PRIO_SKIP 0
#define FJT_PRIO_LOW 1
#define FJT_PRIO_DEFAULT 4
#define FJT_PRIO_TOP 7

#define FJT_ADD_DISABLE_UPLOAD 1
#define FJT_ADD_SEQUENTIAL 2

#define FJT_STATE_CONNECTING 0
#define FJT_STATE_CHECKING_FILES 1
#define FJT_STATE_CHECKING_RESUME_DATA 2
#define FJT_STATE_DOWNLOADING_METADATA 3
#define FJT_STATE_DOWNLOADING 4
#define FJT_STATE_FINISHED 5
#define FJT_STATE_SEEDING 6

#define FJT_F_PAUSED 1
#define FJT_F_FINISHED 2
#define FJT_F_SEEDING 4
#define FJT_F_HAS_METADATA 8
#define FJT_F_ERROR 16
#define FJT_F_NEED_SAVE_RESUME 32

#define FJT_ST_STATE 0
#define FJT_ST_FLAGS 1
#define FJT_ST_TOTAL_WANTED 2
#define FJT_ST_TOTAL_WANTED_DONE 3
#define FJT_ST_DOWNLOAD_RATE 4
#define FJT_ST_UPLOAD_RATE 5
#define FJT_ST_PEERS 6
#define FJT_ST_SEEDS 7
#define FJT_ST_NUM_FILES 8
#define FJT_ST_CONNECTIONS 9
#define FJT_ST_CONNECT_CANDIDATES 10
#define FJT_ST_LIST_PEERS 11
#define FJT_ST_LIST_SEEDS 12
#define FJT_ST_UPLOAD_LIMIT 13
#define FJT_ST_TOTAL_UPLOAD 14
#define FJT_ST_RESERVED 15
#define FJT_ST_COUNT 16

#define FJT_SI_LISTEN_PORT 0
#define FJT_SI_DHT_RUNNING 1
#define FJT_SI_DHT_NODES 2
#define FJT_SI_TORRENTS 3
#define FJT_SI_ACTIVE_TORRENTS 4
#define FJT_SI_MAGNET_JOBS 5
#define FJT_SI_LOCAL_ONLY 6
#define FJT_SI_RESERVED_7 7
#define FJT_SI_COUNT 8

typedef struct fjt_session fjt_session;
typedef struct fjt_meta fjt_meta;
typedef struct fjt_magnet fjt_magnet;
typedef struct fjt_buf fjt_buf;

FJT_API int32_t FJT_CALL fjt_abi_version(void);
FJT_API int32_t FJT_CALL fjt_version_text(char* out, int32_t cap);

FJT_API fjt_session* FJT_CALL fjt_session_create(const char* settings, char* err, int32_t err_cap);
FJT_API int32_t FJT_CALL fjt_session_apply_settings(fjt_session* session, const char* settings);
FJT_API int32_t FJT_CALL fjt_session_info(fjt_session* session, int64_t* out, int32_t count);
FJT_API void FJT_CALL fjt_session_destroy(fjt_session* session, int32_t timeout_ms);

FJT_API fjt_meta* FJT_CALL fjt_meta_parse(const uint8_t* torrent, int64_t size, char* err, int32_t err_cap);
FJT_API const char* FJT_CALL fjt_meta_name(const fjt_meta* meta);
FJT_API const char* FJT_CALL fjt_meta_info_hash(const fjt_meta* meta);
FJT_API int64_t FJT_CALL fjt_meta_total_size(const fjt_meta* meta);
FJT_API int32_t FJT_CALL fjt_meta_num_files(const fjt_meta* meta);
FJT_API int32_t FJT_CALL fjt_meta_entry_count(const fjt_meta* meta);
FJT_API int32_t FJT_CALL fjt_meta_entry_index(const fjt_meta* meta, int32_t entry);
FJT_API int64_t FJT_CALL fjt_meta_entry_size(const fjt_meta* meta, int32_t entry);
FJT_API const char* FJT_CALL fjt_meta_entry_path(const fjt_meta* meta, int32_t entry);
FJT_API const uint8_t* FJT_CALL fjt_meta_torrent_data(const fjt_meta* meta, int64_t* size);
FJT_API void FJT_CALL fjt_meta_free(fjt_meta* meta);

FJT_API fjt_magnet* FJT_CALL fjt_magnet_start(fjt_session* session, const char* magnet_uri, const char* scratch_dir, char* err, int32_t err_cap);
FJT_API int32_t FJT_CALL fjt_magnet_poll(fjt_magnet* job, char* err, int32_t err_cap);
FJT_API fjt_meta* FJT_CALL fjt_magnet_take(fjt_magnet* job);
FJT_API void FJT_CALL fjt_magnet_cancel(fjt_magnet* job);

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
    int32_t err_cap);
FJT_API int32_t FJT_CALL fjt_torrent_pause(fjt_session* session, const char* task_id);
FJT_API int32_t FJT_CALL fjt_torrent_resume(fjt_session* session, const char* task_id);
FJT_API int32_t FJT_CALL fjt_torrent_remove(fjt_session* session, const char* task_id, int32_t delete_files);
FJT_API int32_t FJT_CALL fjt_torrent_set_file_priorities(fjt_session* session, const char* task_id, const uint8_t* file_priorities, int32_t priority_count);
FJT_API int32_t FJT_CALL fjt_torrent_set_limits(fjt_session* session, const char* task_id, int64_t download_limit, int64_t upload_limit);
FJT_API int32_t FJT_CALL fjt_torrent_set_sequential(fjt_session* session, const char* task_id, int32_t on);
FJT_API int32_t FJT_CALL fjt_torrent_status(
    fjt_session* session,
    const char* task_id,
    int64_t* status,
    int32_t status_count,
    int64_t* file_progress,
    int32_t file_capacity,
    char* err,
    int32_t err_cap);
FJT_API int32_t FJT_CALL fjt_torrent_debug_text(fjt_session* session, const char* task_id, char* out, int32_t cap);
FJT_API fjt_buf* FJT_CALL fjt_torrent_save_resume(fjt_session* session, const char* task_id, int32_t timeout_ms);

FJT_API const uint8_t* FJT_CALL fjt_buf_data(const fjt_buf* buf);
FJT_API int64_t FJT_CALL fjt_buf_size(const fjt_buf* buf);
FJT_API void FJT_CALL fjt_buf_free(fjt_buf* buf);

#ifdef __cplusplus
}
#endif

#endif
