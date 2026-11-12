import ctypes
import json
import os
import shutil
import sys
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, DEFAULT_WORK, SERVER, dll_path
from test_bridge import ALERT, EFFECT, ORIGIN, PORT, request

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
    os.makedirs(work + "/downloads", exist_ok=True)
    torrent = work + "/sample file.torrent"
    with open(torrent, "wb") as handle:
        handle.write(b"d4:infod4:name1:xee")
    dll = ctypes.CDLL(dll_path(os.environ.get("FJ_TEST_RELEASE") == "1"))
    dll.fj_prepare.argtypes = [ctypes.c_char_p]
    dll.fj_prepare.restype = ctypes.c_void_p
    dll.fj_free.argtypes = [ctypes.c_void_p]

    def prepare(arguments, extra=None):
        config = {"home": work + "/home", "downloads": work + "/downloads", "kind": "development", "bridge": False,
                  "automaticDownloads": False, "args": arguments}
        if extra:
            config.update(extra)
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

    def same(left, right):
        return os.path.normcase(os.path.normpath(left)) == os.path.normcase(os.path.normpath(right))

    def inside(path, folder):
        return os.path.normcase(os.path.normpath(path)).startswith(os.path.normcase(os.path.normpath(folder)))

    print("flags")
    a = SERVER + "/file/1MB.bin?key=launch-parse-a"
    b = SERVER + "/file/1MB.bin?key=launch-parse-b"
    folder = work + "/save here"
    result = prepare(["/d", a])
    check("/d gives the link and nothing else", result.get("urls") == [a] and result.get("url") == a and not result.get("silent", False) and
          not result.get("add", False) and not result.get("startQueue", False) and not result.get("hidden", False), result)
    result = prepare(["/D", a, "/P", folder, "/F", "my name.bin", "/N"])
    check("capital flags, a folder with a space, a name, silent", result.get("urls") == [a] and same(result.get("path", ""), folder) and
          result.get("file") == "my name.bin" and result.get("silent") and result.get("hidden"), result)
    result = prepare(["--download", a, "--download", b, "--path", folder, "--file", "x.bin", "--add", "--start-queue"])
    check("long forms; the name is dropped with two links", result.get("urls") == [a, b] and same(result.get("path", ""), folder) and
          result.get("file", "") == "" and result.get("add") and result.get("startQueue") and not result.get("silent", False), result)
    result = prepare(["/d"])
    check("/d without a link is nothing", result.get("urls", []) == [] and result.get("url", "") == "", result)
    result = prepare(["/d", "/n"])
    check("/d takes the next argument, even a flag", result.get("urls", []) == [] and not result.get("silent", False), result)
    result = prepare(["/download", a, "/data", "/nn"])
    check("only the exact flags are flags", result.get("urls") == [a] and not result.get("silent", False), result)
    result = prepare(["/p", folder, torrent])
    check("a real path is a file, not a flag", len(result.get("urls", [])) == 1 and same(result["urls"][0], torrent) and
          same(result.get("path", ""), folder), result)
    result = prepare(["/d", a, "/f", "..\\..\\evil.bin"])
    check("a name never carries a folder", result.get("file") == "evil.bin", result)
    result = prepare(["/a", "/n", "/d", a])
    check("/a with /n", result.get("add") and result.get("silent") and result.get("urls") == [a], result)
    result = prepare(["/s"])
    check("/s alone", result.get("startQueue") and result.get("urls", []) == [] and not result.get("exit", False), result)

    print("cold start")
    running = {"bridge": True, "bridgePort": PORT}
    cold = SERVER + "/file/1MB.bin?key=launch-cold"
    cold_folder = work + "/cold folder"
    result = prepare(["/n", "/d", cold, "/p", cold_folder, "/f", "cold name.bin"], running)
    check("nothing runs yet: the command waits for this start, in the tray", not result.get("exit", False) and result.get("hidden") and
          result.get("urls") == [cold], result)
    core = Core("launch", fresh=False, config=running)
    try:
        task = core.wait_task(["COMPLETED", "ERROR"], timeout=60, url_part="launch-cold")
        core.check("/n /p /f: downloaded at once into the folder under the name, no dialog", task["status"] == "COMPLETED" and
                   task["fileName"] == "cold name.bin" and same(task["destinationPath"], cold_folder) and
                   os.path.isfile(cold_folder + "/cold name.bin") and not core.snapshot("add").get("visible", False), task)

        print("hand-over to the open copy")
        core.take_effects()
        silent = SERVER + "/file/1MB.bin?key=launch-silent"
        result = prepare(["/n", "/d", silent, "/f", "silent name.bin"], running)
        task = core.wait_task(["COMPLETED", "ERROR"], timeout=60, url_part="launch-silent")
        core.check("/n: the second copy exits, the first downloads, no dialog, no window", result.get("exit") and
                   task["status"] == "COMPLETED" and task["fileName"] == "silent name.bin" and
                   not core.snapshot("add").get("visible", False) and
                   not any(e.get("kind", 0) == EFFECT["SHOW_WINDOW"] for e in core.take_effects()), (result, task))

        waiting = SERVER + "/file/1MB.bin?key=launch-add"
        add_folder = work + "/added"
        result = prepare(["/a", "/d", waiting, "/p", add_folder], running)
        core.wait_task("PAUSED", timeout=20, url_part="launch-add")
        core.pump(2.0)
        task = core.task(url_part="launch-add")
        core.check("/a: in the list, not started", result.get("exit") and task["status"] == "PAUSED" and task["downloadedBytes"] == 0 and
                   same(task["destinationPath"], add_folder) and not os.path.isfile(add_folder + "/1MB.bin"), task)
        core.check("/a without /n shows the window", any(e.get("kind", 0) == EFFECT["SHOW_WINDOW"] for e in core.take_effects()))
        result = prepare(["/s"], running)
        task = core.wait_task(["COMPLETED", "ERROR"], timeout=60, url_part="launch-add")
        core.check("/s starts what waits", result.get("exit") and task["status"] == "COMPLETED" and
                   os.path.isfile(os.path.join(add_folder, task["fileName"] or "missing")), task)

        print("the Add dialog")
        asked = SERVER + "/file/1MB.bin?key=launch-dialog"
        dialog_folder = work + "/dialog folder"
        os.makedirs(dialog_folder, exist_ok=True)
        result = prepare(["/d", asked, "/p", dialog_folder, "/f", "dialog name.bin"], running)
        core.pump(1.0)
        add = core.snapshot("add")
        core.check("/d /p /f: the dialog opens filled in, nothing starts", result.get("exit") and add.get("visible") and
                   add.get("url") == asked and add.get("fileName") == "dialog name.bin" and
                   inside(add.get("destination", ""), dialog_folder) and core.task(url_part="launch-dialog") is None,
                   (result, add.get("url"), add.get("fileName"), add.get("destination")))
        core.action("ShowAddDialog", show=False)
        first = SERVER + "/file/1MB.bin?key=launch-list-a"
        second = SERVER + "/file/1MB.bin?key=launch-list-b"
        result = prepare(["/d", first, "/d", second], running)
        core.pump(1.0)
        state = core.debug("state")
        core.check("two /d without /n: the dialog's link list", result.get("exit") and core.snapshot("add").get("visible") and
                   state.get("linkList") == 2 and core.task(url_part="launch-list") is None, state)
        core.action("ShowAddDialog", show=False)
        bare = SERVER + "/file/1MB.bin?key=launch-bare"
        result = prepare([bare], running)
        core.pump(1.0)
        add = core.snapshot("add")
        core.check("a bare link still opens the dialog", result.get("exit") and add.get("visible") and add.get("url") == bare,
                   (result, add.get("url")))
        core.action("ShowAddDialog", show=False)

        print("only the app itself")
        answer = request(core, "GET", "/download?cli=1&start=1", {"X-FetchJet": "1"})
        core.check("a command without the token is refused", answer.status == 401, (answer.status, answer.body, answer.error))
        seen = {}

        def allow():
            if not seen.get("done") and any(row.get("kind", 0) == ALERT["BRIDGE_PAIRING"] for row in core.rows("alerts")):
                seen["done"] = True
                core.action("AlertDismiss", kind=ALERT["BRIDGE_PAIRING"], fromScrim=False)

        answer = request(core, "POST", "/pair", {"X-FetchJet": "1", "Origin": ORIGIN, "X-FetchJet-Pairing": "1"}, {}, timeout=30,
                         while_waiting=allow)
        token = (answer.json() or {}).get("token", "")
        browser = SERVER + "/file/1MB.bin?key=launch-browser"
        answer = request(core, "GET", "/download?cli=1&silent=1&url=" + urllib.parse.quote(browser, safe=""),
                         {"X-FetchJet": "1", "Origin": ORIGIN, "X-FetchJet-Token": token})
        core.pump(1.0)
        core.check("a paired browser cannot send a command", len(token) >= 32 and answer.status == 403 and
                   core.task(url_part="launch-browser") is None, (answer.status, answer.body, answer.error))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.checks += checks
    core.failures.extend(failures)
    core.finish()
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(run())
