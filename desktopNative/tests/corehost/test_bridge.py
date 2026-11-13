import http.client
import json
import os
import sys
import threading
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, ON_LINUX, SERVER, YTDLP_PROGRAM, install_fake_ytdlp

PORT = int(os.environ.get("FJ_BRIDGE_PORT", "28459"))
ALERT = {name: index for index, name in enumerate(ENUMS["AlertKind"])}
EFFECT = {name: index for index, name in enumerate(ENUMS["EffectKind"])}
ORIGIN = "chrome-extension://abcdefghijklmnopabcdefghijklmnop"
EDGE = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0"


class Answer:
    def __init__(self, status=0, body="", headers=None, error=""):
        self.status = status
        self.body = body
        self.headers = headers or {}
        self.error = error

    def json(self):
        try:
            return json.loads(self.body)
        except ValueError:
            return None


def request(core, method, path, headers=None, body=None, timeout=20, while_waiting=None):
    result = {}

    def work():
        try:
            connection = http.client.HTTPConnection("127.0.0.1", PORT, timeout=timeout)
            data = body if isinstance(body, (bytes, type(None))) else json.dumps(body).encode("utf-8")
            sent = dict(headers or {})
            if data is not None and "Content-Type" not in sent:
                sent["Content-Type"] = "application/json"
            connection.request(method, path, body=data, headers=sent)
            response = connection.getresponse()
            text = response.read().decode("utf-8", "replace")
            result["answer"] = Answer(response.status, text, {k.lower(): v for k, v in response.getheaders()})
            connection.close()
        except Exception as failure:
            result["answer"] = Answer(error=repr(failure))

    worker = threading.Thread(target=work, daemon=True)
    worker.start()
    waited = 0.0
    while worker.is_alive() and waited < timeout + 5:
        core.pump(0.05)
        waited += 0.05
        if while_waiting:
            while_waiting()
    return result.get("answer", Answer(error="no answer"))


def run():
    core = Core("bridge", before_start=install_fake_ytdlp, config={"bridge": True, "bridgePort": PORT})
    try:
        core.pump(1.5)
        ext = {"X-FetchJet": "1", "Origin": ORIGIN, "User-Agent": EDGE, "X-FetchJet-Ext": "3.4.0"}

        print("ping, gates")
        answer = request(core, "GET", "/ping")
        core.check("ping answers PONG", answer.status == 200 and answer.body == "PONG", (answer.status, answer.body, answer.error))
        answer = request(core, "GET", "/ping", {"Host": "evil.example.com"})
        core.check("a foreign Host header is refused", answer.status == 403 and answer.body == "forbidden", answer.status)
        answer = request(core, "GET", "/ping", {"Origin": "https://evil.example.com"})
        core.check("a web page origin is refused, without a CORS header", answer.status == 403 and
                   "access-control-allow-origin" not in answer.headers, answer.headers)
        answer = request(core, "OPTIONS", "/add", {"Origin": ORIGIN, "Access-Control-Request-Method": "POST"})
        core.check("preflight for the extension", answer.status == 204 and answer.headers.get("access-control-allow-origin") == ORIGIN and
                   "X-FetchJet-Token" in answer.headers.get("access-control-allow-headers", "") and
                   answer.headers.get("access-control-allow-methods") == "POST, OPTIONS", answer.headers)
        answer = request(core, "GET", "/nothing")
        core.check("unknown paths are 404", answer.status == 404, answer.status)

        print("config")
        answer = request(core, "GET", "/config", {"Origin": ORIGIN, "User-Agent": EDGE, "X-FetchJet-Ext": "3.4.0"})
        config = answer.json() or {}
        core.check("config has the app's facts", config.get("app") == "FetchJet" and config.get("bridge") == 2 and
                   config.get("language") == "en" and config.get("paired") is False and config.get("pairing") is True and
                   config.get("mediaExtractor") is True and "/inspect" in config.get("endpoints", []), config)
        core.check("keys come in the order the extension knows", answer.body.startswith('{"app":"FetchJet","version":"') and
                   list(config.keys()) == ["app", "version", "bridge", "mediaExtractor", "torrent", "language", "pairing", "pairedMerge",
                                           "coursePlaylist", "paired", "endpoints"], answer.body[:80])

        print("no token")
        body = {"url": SERVER + "/file/1MB.bin?key=bridge-a", "direct": True, "fileName": "from-browser.bin"}
        answer = request(core, "POST", "/add", {"Origin": ORIGIN}, body)
        core.check("without the X-FetchJet header: refused", answer.status == 403 and "X-FetchJet" in answer.body, (answer.status, answer.body))
        answer = request(core, "POST", "/add", {"X-FetchJet": "1", "Origin": ORIGIN, "X-FetchJet-Pairing": "1"}, body)
        core.check("without a token: pairing required", answer.status == 401 and answer.body == "pairing required", (answer.status, answer.body))
        core.check("and nothing was queued", len(core.tasks()) == 0 and len(core.rows("alerts")) == 0)
        answer = request(core, "GET", "/add", {"X-FetchJet": "1"})
        core.check("add is POST only", answer.status == 405, answer.status)

        print("pairing")
        seen = {}

        def answer_dialog(allow):
            def step():
                alerts = core.rows("alerts")
                if alerts and not seen.get("done"):
                    seen["alert"] = alerts[0]
                    seen["done"] = True
                    if allow:
                        core.action("AlertDismiss", kind=ALERT["BRIDGE_PAIRING"], fromScrim=False)
                    else:
                        core.action("AlertDismiss", kind=ALERT["BRIDGE_PAIRING"], fromScrim=True)
            return step

        pair_headers = dict(ext)
        pair_headers["X-FetchJet-Pairing"] = "1"
        answer = request(core, "POST", "/pair", pair_headers, {}, timeout=30, while_waiting=answer_dialog(False))
        core.check("the app asks; a click outside means no", answer.status == 403 and answer.body == "refused" and
                   seen.get("alert", {}).get("kind") == ALERT["BRIDGE_PAIRING"] and ORIGIN in seen.get("alert", {}).get("detail", ""),
                   (answer.status, answer.body, seen.get("alert")))
        core.check("the window was asked to come forward", any(e.get("kind", 0) == EFFECT["SHOW_WINDOW"] for e in core.take_effects()))
        core.pump(8.5)
        seen.clear()
        answer = request(core, "POST", "/pair", pair_headers, {}, timeout=30, while_waiting=answer_dialog(True))
        token = (answer.json() or {}).get("token", "")
        core.check("allow gives a token", answer.status == 200 and len(token) >= 32, (answer.status, answer.body[:60], answer.error))
        paired = dict(ext)
        paired["X-FetchJet-Token"] = token
        answer = request(core, "GET", "/config", paired)
        core.check("config now says paired", (answer.json() or {}).get("paired") is True)

        print("add")
        answer = request(core, "POST", "/add", paired, body)
        core.check("add is accepted", answer.status == 200 and answer.json() == {"ok": True}, (answer.status, answer.body))
        task = core.wait_task(["COMPLETED", "ERROR"], timeout=40, url_part="bridge-a")
        core.check("the file is downloaded at once, no dialog", task["status"] == "COMPLETED" and task["fileName"] == "from-browser.bin" and
                   not core.snapshot("add").get("visible", False), task)
        answer = request(core, "POST", "/add", paired, body)
        core.pump(0.5)
        core.check("the same click twice does not add twice", answer.status == 200 and len(core.tasks()) == 1, len(core.tasks()))
        answer = request(core, "POST", "/add", paired, {"url": "gopher://example.com/file.bin"})
        core.check("only http(s) and magnet links", answer.status == 400 and "unsupported" in answer.body, (answer.status, answer.body))
        answer = request(core, "POST", "/add", paired, b"x" * (4 * 1024 * 1024 + 10))
        core.check("a body above 4 MB is not read", answer.status == 400 or answer.error != "", (answer.status, answer.error))

        print("links")
        answer = request(core, "POST", "/links", paired, [
            {"url": SERVER + "/file/1MB.bin?key=bridge-b", "fileName": "b.bin"},
            {"url": SERVER + "/file/2MB.bin?key=bridge-c", "fileName": "c.bin"},
            {"url": SERVER + "/file/1MB.bin?key=bridge-b", "fileName": "again.bin"},
            {"url": "javascript:alert(1)"},
        ])
        core.check("two different links accepted", answer.status == 200 and answer.json() == {"ok": True, "accepted": 2}, answer.body)
        b = core.wait_task(["COMPLETED", "ERROR"], timeout=40, url_part="bridge-b")
        c = core.wait_task(["COMPLETED", "ERROR"], timeout=40, url_part="bridge-c")
        core.check("both were queued and finished", b["status"] == "COMPLETED" and c["status"] == "COMPLETED", (b["status"], c["status"]))

        print("inspect")
        video = "https://www.youtube.com/watch?v=fakevideo01"
        answer = request(core, "GET", "/inspect?url=" + urllib.parse.quote(video, safe=""), paired, timeout=90)
        qualities = answer.json() or []
        core.check("qualities of a video", answer.status == 200 and len(qualities) >= 2 and
                   list(qualities[0].keys()) == ["index", "quality", "format", "size", "directUrl", "title", "formatId", "duration"] and
                   qualities[0].get("title") == "Big Buck Bunny - sample video", answer.body[:200])
        answer = request(core, "GET", "/inspect?url=" + urllib.parse.quote("file:///C:/Windows/win.ini", safe=""), paired)
        core.check("a local path is refused", answer.status == 400, answer.status)
        answer = request(core, "POST", "/add", paired, {"url": video, "formatId": "no-such-format"}, timeout=60)
        core.check("an unknown quality asks the extension to inspect again", answer.status == 409, (answer.status, answer.body))
        answer = request(core, "GET", "/inspect?url=" + urllib.parse.quote(video, safe=""), {"X-FetchJet": "1"})
        core.check("inspect needs the token too", answer.status == 401, answer.status)
        local = SERVER + "/file/1KB.bin?key=bridge-broken"
        answer = request(core, "GET", "/inspect?url=" + urllib.parse.quote(local, safe=""), paired, timeout=90)
        core.check("a page yt-dlp finds nothing on still answers an empty list", answer.status == 200 and answer.json() == [],
                   (answer.status, answer.body[:200]))
        fake = os.path.join(core.home, "tools", "yt-dlp-dir", YTDLP_PROGRAM)
        os.replace(fake, fake + ".off")
        try:
            local = SERVER + "/file/1KB.bin?key=bridge-no-ytdlp"
            answer = request(core, "GET", "/inspect?url=" + urllib.parse.quote(local, safe=""), paired, timeout=90)
            error = (answer.json() or {}).get("error", "") if answer.status == 500 else ""
            core.check("without yt-dlp the inspection says so", "yt-dlp is not installed" in error, (answer.status, answer.body[:200]))
            core.check("the missing yt-dlp is in the normal log", "Inspect: yt-dlp is not installed" in core.log_text(), core.log_text()[-300:])
        finally:
            os.replace(fake + ".off", fake)

        print("download and show")
        link = SERVER + "/file/3MB.bin?key=bridge-d"
        core.take_effects()
        answer = request(core, "GET", "/download?url=" + urllib.parse.quote(link, safe=""), paired)
        core.pump(0.6)
        add = core.snapshot("add")
        core.check("a link handed over opens the Add dialog with it", answer.status == 200 and answer.body == "OK" and add.get("visible") and
                   add.get("url") == link, (answer.status, add.get("url")))
        core.action("ShowAddDialog", show=False)
        answer = request(core, "GET", "/show", paired)
        core.check("show brings the window forward", answer.status == 200 and
                   any(e.get("kind", 0) == EFFECT["SHOW_WINDOW"] for e in core.take_effects()), answer.status)

        print("the app knows the browser")
        core.action("OpenSettings")
        core.action("SetSettingsSection", index=2)
        core.wait_for("the browsers", lambda: len(core.rows("settingsRows")) > 0, 15)
        core.pump(0.5)
        edge = [r for r in core.rows("settingsRows") if r.get("title") == "Microsoft Edge"]
        if edge and edge[0].get("subtitle") == core.strings().get("browserNotInstalled"):
            print("  skip  Edge is shown as connected with its version: Edge is not installed on this machine")
        elif edge:
            core.check("Edge is shown as connected with its version", "3.4.0" in edge[0].get("subtitle", "") and
                       edge[0].get("subtitle", "").startswith("\u2713"), edge[0].get("subtitle"))
        saved = json.load(open(os.path.join(core.home, "bridge.json"), encoding="utf-8"))
        core.check("the token is stored encrypted", str(saved.get("token", "")).startswith("fjenc1:") and token not in json.dumps(saved), list(saved.keys()))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
