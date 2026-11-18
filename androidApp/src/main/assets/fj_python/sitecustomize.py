import ctypes
import os
import subprocess

_TARGETS = {"ffmpeg": "libffmpeg.so", "ffprobe": "libffprobe.so", "qjs": "libqjs.so"}
_lib = None
_original = subprocess.Popen._execute_child


def _spawn_lib():
    global _lib
    if _lib is None:
        path = None
        try:
            with open("/proc/self/maps") as maps:
                for line in maps:
                    if line.rstrip().endswith("/libfjexec.so"):
                        path = line.split()[-1]
                        break
        except OSError:
            pass
        lib = ctypes.CDLL(path or "libfjexec.so")
        lib.fjexec_spawn.restype = ctypes.c_int
        lib.fjexec_spawn.argtypes = [
            ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p,
            ctypes.POINTER(ctypes.c_char_p), ctypes.POINTER(ctypes.c_char_p),
            ctypes.c_char_p, ctypes.c_int, ctypes.c_int, ctypes.c_int,
        ]
        _lib = lib
    return _lib


def _c_array(items):
    arr = (ctypes.c_char_p * (len(items) + 1))()
    for i, item in enumerate(items):
        arr[i] = item
    arr[len(items)] = None
    return arr


def _execute_child(self, args, executable, preexec_fn, close_fds, pass_fds, cwd, env,
                   startupinfo, creationflags, shell, p2cread, p2cwrite, c2pread, c2pwrite,
                   errread, errwrite, *rest):
    argv = list(args) if isinstance(args, (list, tuple)) else [args]
    program = os.fsdecode(executable if executable else argv[0])
    target = None if shell else _TARGETS.get(os.path.basename(program))
    if target is None:
        return _original(self, args, executable, preexec_fn, close_fds, pass_fds, cwd, env,
                         startupinfo, creationflags, shell, p2cread, p2cwrite, c2pread, c2pwrite,
                         errread, errwrite, *rest)
    environment = env if env is not None else os.environ
    envp = [os.fsencode(k) + b"=" + os.fsencode(v) for k, v in environment.items()]
    pid = _spawn_lib().fjexec_spawn(
        target.encode(), b"main", None,
        _c_array([os.fsencode(a) for a in argv]), _c_array(envp),
        os.fsencode(cwd) if cwd else None,
        p2cread if p2cread != -1 else 0,
        c2pwrite if c2pwrite != -1 else 1,
        errwrite if errwrite != -1 else 2,
    )
    if pid <= 0:
        raise OSError("could not start " + program)
    self.pid = pid
    self._child_created = True
    for fd in {p2cread, c2pwrite, errwrite}:
        if fd != -1:
            os.close(fd)
subprocess.Popen._execute_child = _execute_child
