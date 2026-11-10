import ctypes
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import DEFAULT_WORK, dll_path

checks = 0
failures = []


def check(name, condition, detail=""):
    global checks
    checks += 1
    print(("  ok    " if condition else "  FAIL  ") + name + ("" if condition else " : " + str(detail)[:300]))
    if not condition:
        failures.append(name)


def run():
    work = os.path.join(DEFAULT_WORK, "launch").replace("\\", "/")
    shutil.rmtree(work, ignore_errors=True)
    os.makedirs(work + "/home", exist_ok=True)
    torrent = work + "/sample file.torrent"
    with open(torrent, "wb") as handle:
        handle.write(b"d4:infod4:name1:xee")
    dll = ctypes.CDLL(dll_path(os.environ.get("FJ_TEST_RELEASE") == "1"))
    dll.fj_prepare.argtypes = [ctypes.c_char_p]
    dll.fj_prepare.restype = ctypes.c_void_p
    dll.fj_free.argtypes = [ctypes.c_void_p]

    def prepare(arguments):
        config = {"home": work + "/home", "kind": "development", "bridge": False, "automaticDownloads": False, "args": arguments}
        pointer = dll.fj_prepare(json.dumps(config).encode("utf-8"))
        text = ctypes.string_at(pointer).decode("utf-8") if pointer else "{}"
        if pointer:
            dll.fj_free(pointer)
        return json.loads(text)

    print("arguments")
    result = prepare([])
    check("no argument: a normal start", not result.get("exit", False) and not result.get("hidden", False) and result.get("url", "") == "", result)
    result = prepare(["https://example.com/file.zip"])
    check("a link is kept for the Add dialog", result.get("url") == "https://example.com/file.zip", result)
    result = prepare(["fetchjet://add?url=https%3A%2F%2Fexample.com%2Fa%20b.zip%3Fx%3D1"])
    check("fetchjet:// with an encoded link", result.get("url") == "https://example.com/a b.zip?x=1", result)
    result = prepare(["fetchjet://https://example.com/direct.bin"])
    check("fetchjet:// with the link after the scheme", result.get("url") == "https://example.com/direct.bin", result)
    result = prepare(["kortex://open?URL=http://example.com/old"])
    check("the old kortex:// scheme", result.get("url") == "http://example.com/old", result)
    result = prepare(['"magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"'])
    check("a quoted magnet link", result.get("url", "").startswith("magnet:?xt=urn:btih:0123"), result)
    result = prepare([torrent])
    check("an existing .torrent file becomes its full path", os.path.normcase(result.get("url", "")) == os.path.normcase(os.path.abspath(torrent)), result)
    result = prepare([work + "/missing.torrent"])
    check("a missing .torrent file is ignored", result.get("url", "") == "", result)
    result = prepare(["notes.txt"])
    check("anything else is ignored", result.get("url", "") == "", result)
    result = prepare(["--autostart=true"])
    check("started at login: hidden in the tray by default, the flag is not a link", result.get("hidden") and result.get("loginStart") and
          result.get("url", "") == "", result)
    result = prepare(["--autostart=true", "https://example.com/x.bin"])
    check("login flag plus a link", result.get("loginStart") and result.get("url") == "https://example.com/x.bin", result)
    print("%d checks, %d failed" % (checks, len(failures)))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(run())
