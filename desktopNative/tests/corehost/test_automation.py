import ctypes
import json
import os
import sys
import threading
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import test_bridge
from corehost import Core, CheckFailed, SERVER, install_fake_ytdlp
from test_bridge import request, ORIGIN, EDGE, ALERT

PORT = int(os.environ.get("FJ_AUTOMATION_PORT", "28469"))
test_bridge.PORT = PORT


def slow(key, size="30MB"):
    return SERVER + "/file/%s.bin?key=%s&throttle=200" % (size, key)


def quote(text):
    return urllib.parse.quote(text, safe="")


def cli(core, arguments, port=None):
    core.dll.fj_prepare.argtypes = [ctypes.c_char_p]
    core.dll.fj_prepare.restype = ctypes.c_void_p
    holder = {}

    def work():
        config = {"home": core.home, "downloads": core.downloads, "kind": "development", "bridge": True,
                  "bridgePort": port or PORT, "automaticDownloads": False, "args": arguments}
        pointer = core.dll.fj_prepare(json.dumps(config).encode("utf-8"))
        holder["text"] = ctypes.string_at(pointer).decode("utf-8") if pointer else "{}"
        if pointer:
            core.dll.fj_free(pointer)

    worker = threading.Thread(target=work, daemon=True)
    worker.start()
    waited = 0.0
    while worker.is_alive() and waited < 25:
        core.pump(0.05)
        waited += 0.05
    result = json.loads(holder.get("text", "{}"))
    try:
        result["json"] = json.loads(result.get("output", ""))
    except ValueError:
        result["json"] = None
    return result


def run():
    core = Core("automation", before_start=install_fake_ytdlp, config={"bridge": True, "bridgePort": PORT})
    try:
        core.pump(1.5)
        ext = {"X-FetchJet": "1", "Origin": ORIGIN, "User-Agent": EDGE, "X-FetchJet-Ext": "3.5.0"}
        seen = {}

        def allow():
            alerts = core.rows("alerts")
            if alerts and not seen.get("done"):
                seen["done"] = True
                core.action("AlertDismiss", kind=ALERT["BRIDGE_PAIRING"], fromScrim=False)

        pair_headers = dict(ext)
        pair_headers["X-FetchJet-Pairing"] = "1"
        answer = request(core, "POST", "/pair", pair_headers, {}, timeout=30, while_waiting=allow)
        token = (answer.json() or {}).get("token", "")
        core.check("paired, to get a token", len(token) >= 32, (answer.status, answer.body[:60]))
        local = {"X-FetchJet": "1", "X-FetchJet-Token": token}
        paired_ext = dict(ext)
        paired_ext["X-FetchJet-Token"] = token

        print("off by default")
        answer = request(core, "GET", "/api/status", {"X-FetchJet": "1"})
        core.check("no token: 401", answer.status == 401, (answer.status, answer.body))
        answer = request(core, "GET", "/api/status", local)
        core.check("token but off: 403 automation is off", answer.status == 403 and "off" in answer.body, (answer.status, answer.body))
        answer = request(core, "GET", "/api/status", paired_ext)
        core.check("the extension (an Origin header) is refused, even paired", answer.status == 403, (answer.status, answer.body))
        answer = request(core, "GET", "/api/status", {"Origin": "https://evil.example.com", "X-FetchJet": "1", "X-FetchJet-Token": token})
        core.check("a web page is refused", answer.status == 403, answer.status)
        answer = request(core, "GET", "/api/status", {"Host": "evil.example.com", "X-FetchJet": "1", "X-FetchJet-Token": token})
        core.check("a foreign Host is refused", answer.status == 403, answer.status)
        answer = request(core, "GET", "/api/status", {"X-FetchJet-Token": token})
        core.check("without the X-FetchJet header: 403", answer.status == 403, answer.status)

        print("turn it on")
        answer = request(core, "GET", "/api/automation", local)
        core.check("state is off", (answer.json() or {}).get("automation") is False, answer.body)
        answer = request(core, "GET", "/api/automation?on=1", paired_ext)
        core.check("the extension cannot turn it on", answer.status == 403, answer.status)
        answer = request(core, "GET", "/api/automation?on=1", local)
        core.check("a token holder cannot turn it on", answer.status == 403, (answer.status, answer.body))
        result = cli(core, ["automation", "on"])
        core.check("fetchjet automation on (the app itself)", result.get("code", 0) == 0 and (result.get("json") or {}).get("automation") is True, result)
        answer = request(core, "GET", "/api/automation", local)
        core.check("on", answer.status == 200 and (answer.json() or {}).get("automation") is True, answer.body)
        saved = json.load(open(os.path.join(core.home, "bridge.json"), encoding="utf-8"))
        core.check("saved in bridge.json", saved.get("automation") is True, list(saved.keys()))

        print("status")
        answer = request(core, "GET", "/api/status", local)
        data = answer.json() or {}
        core.check("empty list", answer.status == 200 and data.get("count") == 0 and data.get("downloads") == [] and data.get("app") == "FetchJet", answer.body)
        answer = request(core, "GET", "/api/status?id=nope", local)
        core.check("unknown id: 404", answer.status == 404, (answer.status, answer.body))

        print("add, pause, start")
        answer = request(core, "POST", "/add", paired_ext, {"url": slow("auto-a"), "direct": True, "fileName": "a.bin"})
        core.check("add", answer.status == 200, answer.body)
        core.wait_task(["DOWNLOADING"], timeout=40, url_part="auto-a")
        core.pump(2)
        answer = request(core, "GET", "/api/status", local)
        data = answer.json() or {}
        row = next((d for d in data.get("downloads", []) if "auto-a" in d.get("url", "")), {})
        core.check("the list shows it running with a speed", row.get("status") == "downloading" and row.get("speedBytesPerSec", 0) > 0 and
                   row.get("name") == "a.bin" and data.get("speedBytesPerSec", 0) > 0 and row.get("totalBytes", 0) > 0, row)
        ident = row.get("id", "")
        answer = request(core, "GET", "/api/status?id=" + quote(ident), local)
        core.check("one download by id", answer.status == 200 and (answer.json() or {}).get("id") == ident, answer.body[:120])
        answer = request(core, "POST", "/api/pause?id=" + quote(ident), local)
        core.check("pause", answer.status == 200 and (answer.json() or {}).get("ok") is True, (answer.status, answer.body))
        core.wait_task(["PAUSED"], timeout=20, url_part="auto-a")
        answer = request(core, "GET", "/api/status?id=" + quote(ident), local)
        core.check("status says paused", (answer.json() or {}).get("status") == "paused", answer.body[:120])
        answer = request(core, "POST", "/api/start?id=" + quote(ident), local)
        core.check("start", answer.status == 200, (answer.status, answer.body))
        core.wait_task(["DOWNLOADING", "CONNECTING"], timeout=30, url_part="auto-a")

        print("command line")
        result = cli(core, ["status"])
        listed = (result.get("json") or {}).get("downloads", [])
        core.check("fetchjet status: exit code 0 and the list as JSON", result.get("exit") and result.get("code", 0) == 0 and
                   any(d.get("id") == ident for d in listed), result)
        result = cli(core, ["status", ident])
        core.check("fetchjet status <id>: one download", result.get("code", 0) == 0 and (result.get("json") or {}).get("id") == ident, result)
        result = cli(core, ["pause", ident])
        core.check("fetchjet pause <id>", result.get("code", 0) == 0 and (result.get("json") or {}).get("ok") is True, result)
        core.wait_task(["PAUSED"], timeout=20, url_part="auto-a")
        result = cli(core, ["start", ident])
        core.check("fetchjet start <id>", result.get("code", 0) == 0, result)
        core.wait_task(["DOWNLOADING", "CONNECTING"], timeout=30, url_part="auto-a")
        result = cli(core, ["pause", "nope"])
        core.check("fetchjet pause with an unknown id: exit code 1", result.get("code") == 1 and (result.get("json") or {}).get("missing") == ["nope"], result)
        result = cli(core, ["status"], port=PORT + 1)
        core.check("the app is not running: exit code 1 and a clear message", result.get("code") == 1 and "not running" in result.get("output", ""), result)

        print("errors")
        answer = request(core, "POST", "/api/pause?id=nope", local)
        core.check("pause an unknown id: 404 with the id listed", answer.status == 404 and (answer.json() or {}).get("missing") == ["nope"], (answer.status, answer.body))
        answer = request(core, "POST", "/api/pause", local)
        core.check("no id: 400", answer.status == 400, (answer.status, answer.body))
        answer = request(core, "POST", "/api/explode?id=x", local)
        core.check("an unknown action: 404", answer.status == 404, (answer.status, answer.body))
        answer = request(core, "DELETE", "/api/status", local)
        core.check("only GET and POST", answer.status == 405, answer.status)

        print("remove takes it off the list, delete takes the files too")
        answer = request(core, "POST", "/api/remove?id=" + quote(ident), local)
        core.check("remove", answer.status == 200 and (answer.json() or {}).get("done") == 1, (answer.status, answer.body))
        core.wait_for("the row to go", lambda: not core.tasks(), 15)
        answer = request(core, "GET", "/api/status", local)
        core.check("the list is empty again", (answer.json() or {}).get("count") == 0, answer.body[:120])
        request(core, "POST", "/add", paired_ext, {"url": slow("auto-b"), "direct": True, "fileName": "b.bin"})
        core.wait_task(["DOWNLOADING"], timeout=40, url_part="auto-b")
        core.pump(2)
        answer = request(core, "GET", "/api/status", local)
        first = ((answer.json() or {}).get("downloads") or [{}])[0]
        ident_b = first.get("id", "")
        dest_b = first.get("destination", "")
        answer = request(core, "POST", "/api/delete?ids=" + quote(ident_b), local)
        core.check("delete", answer.status == 200 and (answer.json() or {}).get("done") == 1, (answer.status, answer.body))
        core.wait_for("the row to go", lambda: not core.tasks(), 15)
        core.pump(1)
        left = [n for n in os.listdir(dest_b) if n.startswith("b.bin")] if dest_b and os.path.isdir(dest_b) else []
        core.check("delete took the partial files too", left == [], left)

        print("pause and start only where they make sense")
        request(core, "POST", "/add", paired_ext, {"url": SERVER + "/file/1MB.bin?key=auto-c", "direct": True, "fileName": "c.bin"})
        done = core.wait_task(["COMPLETED"], timeout=40, url_part="auto-c")
        answer = request(core, "POST", "/api/pause?id=" + quote(done["id"]), local)
        core.check("pause on a finished download: 409, nothing changes", answer.status == 409 and (answer.json() or {}).get("failed") == [done["id"]], (answer.status, answer.body))
        answer = request(core, "POST", "/api/start?id=" + quote(done["id"]), local)
        core.check("start on a finished download: 409", answer.status == 409, (answer.status, answer.body))
        core.pump(1)
        core.check("it is still completed", core.task(url_part="auto-c")["status"] == "COMPLETED", core.task(url_part="auto-c"))
        file_c = os.path.join(done.get("destinationPath") or core.downloads, "c.bin")
        if not os.path.isfile(file_c):
            file_c = os.path.join(core.downloads, "c.bin")
        answer = request(core, "POST", "/api/remove?id=" + quote(done["id"]), local)
        core.wait_for("the finished row to go", lambda: not core.tasks(), 15)
        core.check("remove on a finished download keeps its file", answer.status == 200 and os.path.isfile(file_c), (answer.status, file_c))

        print("turn it off")
        answer = request(core, "GET", "/api/automation?on=0", local)
        core.check("off", (answer.json() or {}).get("automation") is False, answer.body)
        answer = request(core, "GET", "/api/status", local)
        core.check("403 again", answer.status == 403, answer.status)
        saved = json.load(open(os.path.join(core.home, "bridge.json"), encoding="utf-8"))
        core.check("saved off", saved.get("automation") is False, saved.get("automation"))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
