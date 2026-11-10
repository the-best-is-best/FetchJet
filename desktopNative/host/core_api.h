#pragma once

extern "C" {
typedef void (*fj_task_fn)(void *arg);
typedef void (*fj_post_fn)(fj_task_fn fn, void *arg);
typedef void (*fj_notify_fn)(unsigned int changed_channels);
typedef char *(*fj_host_fn)(const char *name, const char *args_json);
typedef void (*fj_host_free_fn)(char *text);

typedef int (*fj_start_fn)(const char *config_json, fj_post_fn post, fj_notify_fn notify, fj_host_fn host,
                           fj_host_free_fn host_free);
typedef char *(*fj_prepare_fn)(const char *config_json);
typedef char *(*fj_snapshot_fn)(int channel);
typedef void (*fj_action_fn)(const char *action_json);
typedef char *(*fj_call_fn)(const char *name, const char *args_json);
typedef void (*fj_free_fn)(char *text);
typedef void (*fj_shutdown_fn)(void);
}

struct CoreApi {
    fj_prepare_fn prepare = nullptr;
    fj_start_fn start = nullptr;
    fj_snapshot_fn snapshot = nullptr;
    fj_action_fn action = nullptr;
    fj_call_fn call = nullptr;
    fj_free_fn release = nullptr;
    fj_shutdown_fn shutdown = nullptr;
};
