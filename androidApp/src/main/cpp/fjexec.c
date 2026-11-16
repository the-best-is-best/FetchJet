#define _GNU_SOURCE
#include <jni.h>
#include <dlfcn.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

typedef int (*main_fn)(int, char **);

#define EXPORT __attribute__((visibility("default")))

static int ends_with_so(const char *name) {
    const char *p = strstr(name, ".so");
    return p && (p[3] == '\0' || p[3] == '.');
}

static void preload_dirs(const char *dirs) {
    if (!dirs || !*dirs) return;
    void (*update)(const char *) = (void (*)(const char *)) dlsym(RTLD_DEFAULT, "android_update_LD_LIBRARY_PATH");
    if (update) {
        update(dirs);
    } else {
        dprintf(2, "fjexec: android_update_LD_LIBRARY_PATH not available\n");
    }
    char *copy = strdup(dirs);
    char *paths[64];
    int dirCount = 0;
    for (char *tok = strtok(copy, ":"); tok && dirCount < 64; tok = strtok(NULL, ":")) paths[dirCount++] = tok;
    char *files[1024];
    int fileCount = 0;
    for (int d = 0; d < dirCount; d++) {
        DIR *dir = opendir(paths[d]);
        if (!dir) continue;
        struct dirent *e;
        while ((e = readdir(dir)) != NULL && fileCount < 1024) {
            if (e->d_name[0] == '.' || !ends_with_so(e->d_name)) continue;
            size_t n = strlen(paths[d]) + strlen(e->d_name) + 2;
            char *full = malloc(n);
            snprintf(full, n, "%s/%s", paths[d], e->d_name);
            files[fileCount++] = full;
        }
        closedir(dir);
    }
    int loaded[1024] = {0};
    int progress = 1;
    while (progress) {
        progress = 0;
        for (int i = 0; i < fileCount; i++) {
            if (loaded[i]) continue;
            if (dlopen(files[i], RTLD_NOW | RTLD_GLOBAL)) {
                loaded[i] = 1;
                progress = 1;
            }
        }
    }
    free(copy);
}

EXPORT int fjexec_spawn(const char *target, const char *symbol, const char *preload,
                        char *const argv[], char *const envp[], const char *cwd,
                        int fd0, int fd1, int fd2) {
    pid_t pid = fork();
    if (pid < 0) return -errno;
    if (pid > 0) return pid;
    setpgid(0, 0);
    if (fd0 != 0) dup2(fd0, 0);
    if (fd1 != 1) dup2(fd1, 1);
    if (fd2 != 2) dup2(fd2, 2);
    for (int fd = 3; fd < 1024; fd++) close(fd);
    sigset_t all;
    sigemptyset(&all);
    sigprocmask(SIG_SETMASK, &all, NULL);
    signal(SIGPIPE, SIG_DFL);
    if (cwd && *cwd) chdir(cwd);
    if (envp) {
        clearenv();
        for (int i = 0; envp[i]; i++) putenv(envp[i]);
    }
    preload_dirs(preload);
    void *h = dlopen(target, RTLD_NOW | RTLD_GLOBAL);
    if (!h) {
        dprintf(2, "fjexec: dlopen %s failed: %s\n", target, dlerror());
        _exit(127);
    }
    main_fn fn = (main_fn) dlsym(h, symbol);
    if (!fn) {
        dprintf(2, "fjexec: no symbol %s in %s\n", symbol, target);
        _exit(127);
    }
    int argc = 0;
    while (argv[argc]) argc++;
    int rc = fn(argc, (char **) argv);
    fflush(NULL);
    _exit(rc);
}

EXPORT void fj_exit(int code) {
    fflush(NULL);
    _exit(code);
}

JNIEXPORT jint JNICALL
Java_com_deniscerri_ytdl_core_NativeExec_spawn(JNIEnv *env, jclass cls, jstring jtarget,
                                              jstring jsymbol, jstring jpreload,
                                              jobjectArray jargv, jobjectArray jenvp,
                                              jstring jcwd, jint fd0, jint fd1, jint fd2) {
    const char *target = (*env)->GetStringUTFChars(env, jtarget, NULL);
    const char *symbol = (*env)->GetStringUTFChars(env, jsymbol, NULL);
    const char *preload = jpreload ? (*env)->GetStringUTFChars(env, jpreload, NULL) : NULL;
    const char *cwd = jcwd ? (*env)->GetStringUTFChars(env, jcwd, NULL) : NULL;
    int argc = (*env)->GetArrayLength(env, jargv);
    char **argv = calloc(argc + 1, sizeof(char *));
    for (int i = 0; i < argc; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, jargv, i);
        const char *c = (*env)->GetStringUTFChars(env, s, NULL);
        argv[i] = strdup(c);
        (*env)->ReleaseStringUTFChars(env, s, c);
        (*env)->DeleteLocalRef(env, s);
    }
    char **envp = NULL;
    if (jenvp) {
        int n = (*env)->GetArrayLength(env, jenvp);
        envp = calloc(n + 1, sizeof(char *));
        for (int i = 0; i < n; i++) {
            jstring s = (jstring) (*env)->GetObjectArrayElement(env, jenvp, i);
            const char *c = (*env)->GetStringUTFChars(env, s, NULL);
            envp[i] = strdup(c);
            (*env)->ReleaseStringUTFChars(env, s, c);
            (*env)->DeleteLocalRef(env, s);
        }
    }
    int pid = fjexec_spawn(target, symbol, preload, argv, envp, cwd, fd0, fd1, fd2);
    for (int i = 0; argv[i]; i++) free(argv[i]);
    free(argv);
    if (envp) {
        for (int i = 0; envp[i]; i++) free(envp[i]);
        free(envp);
    }
    (*env)->ReleaseStringUTFChars(env, jtarget, target);
    (*env)->ReleaseStringUTFChars(env, jsymbol, symbol);
    if (preload) (*env)->ReleaseStringUTFChars(env, jpreload, preload);
    if (cwd) (*env)->ReleaseStringUTFChars(env, jcwd, cwd);
    return pid;
}

JNIEXPORT jint JNICALL
Java_com_deniscerri_ytdl_core_NativeExec_waitFor(JNIEnv *env, jclass cls, jint pid) {
    int status = 0;
    pid_t r;
    do {
        r = waitpid(pid, &status, 0);
    } while (r < 0 && errno == EINTR);
    if (r < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_deniscerri_ytdl_core_NativeExec_pollExit(JNIEnv *env, jclass cls, jint pid) {
    int status = 0;
    pid_t r = waitpid(pid, &status, WNOHANG);
    if (r == 0) return -1000;
    if (r < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_com_deniscerri_ytdl_core_NativeExec_killGroup(JNIEnv *env, jclass cls, jint pid) {
    kill(-pid, SIGKILL);
    kill(pid, SIGKILL);
}
