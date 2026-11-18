import ctypes
import os
import socket
import sys
import time

TORRENT_DLL = os.environ.get("FETCHJET_TORRENT_DLL", "E:/tools/probes/fjtorrent/build/fjtorrent.dll")
LISTEN_PORT = 0
INFO_COUNT = 8

failed = 0


def check(name, ok, detail=None):
    global failed
    if not ok:
        failed += 1
    print(("  ok    " if ok else "  FAIL  ") + name + ("" if ok or detail is None else " : " + repr(detail)))


def free_port():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind(("0.0.0.0", 0))
    port = s.getsockname()[1]
    s.close()
    return port


def hold(port):
    held = []
    for kind in (socket.SOCK_STREAM, socket.SOCK_DGRAM):
        s = socket.socket(socket.AF_INET, kind)
        if os.name == "nt":
            s.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        s.bind(("0.0.0.0", port))
        if kind == socket.SOCK_STREAM:
            s.listen(1)
        held.append(s)
    return held


def listen_port(lib, port):
    settings = "\n".join([
        "enable_dht=false", "enable_lsd=false", "enable_upnp=false", "enable_natpmp=false",
        "listen_interfaces=0.0.0.0:%d,[::]:%d" % (port, port),
    ]).encode()
    err = ctypes.create_string_buffer(512)
    session = lib.fjt_session_create(settings, err, len(err))
    if not session:
        return None, err.value.decode(errors="replace")
    try:
        values = (ctypes.c_int64 * INFO_COUNT)()
        seen = 0
        for _ in range(30):
            if lib.fjt_session_info(session, values, INFO_COUNT) > 0 and values[LISTEN_PORT] > 0:
                seen = values[LISTEN_PORT]
                break
            time.sleep(0.2)
        return seen, None
    finally:
        lib.fjt_session_destroy(session, 3000)


def run():
    lib = ctypes.CDLL(TORRENT_DLL)
    lib.fjt_session_create.restype = ctypes.c_void_p
    lib.fjt_session_create.argtypes = [ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int32]
    lib.fjt_session_info.restype = ctypes.c_int32
    lib.fjt_session_info.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_int64), ctypes.c_int32]
    lib.fjt_session_destroy.restype = None
    lib.fjt_session_destroy.argtypes = [ctypes.c_void_p, ctypes.c_int32]

    port = free_port()
    got, error = listen_port(lib, port)
    check("a free port is used as set", got == port, (port, got, error))

    port = free_port()
    held = hold(port)
    try:
        got, error = listen_port(lib, port)
        check("a port another program holds: the session still gets a listen socket", bool(got), (port, got, error))
        check("and it is another port, not the held one", got != port, (port, got))
    finally:
        for s in held:
            s.close()

    print("all passed" if not failed else "%d failed" % failed)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(run())
