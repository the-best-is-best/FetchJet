import http.server
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import DEFAULT_WORK, ENUMS, install_fake_ytdlp
from test_compress import FFMPEG_NAME, FFMPEG_SOURCE, install_ffmpeg

PAIR = {name: index for index, name in enumerate(ENUMS["PairingCode"])}
SEND = {name: index for index, name in enumerate(ENUMS["DeviceSendCode"])}
STATE = {name: index for index, name in enumerate(ENUMS["MediaStateCode"])}
SENDER_HOST = "127.0.0.2"
RECEIVER_HOST = "127.0.0.3"
MEDIA_PORT = 18297
MEDIA = "http://127.0.0.1:%d" % MEDIA_PORT
PAGE = "https://www.youtube.com/watch?v=fakevideo01"
SHARED = os.path.join(DEFAULT_WORK, "send-subtitles-shared")
FIXTURE = os.path.join(DEFAULT_WORK, "send-subtitles-fixture")
SCENARIOS = ["S1", "S2", "S3", "S4"]
SLOW_KB = 120

VTT = {
    "en": "WEBVTT\n\n00:00:00.000 --> 00:00:01.500\nHello\n\n00:00:01.500 --> 00:00:03.000\nWorld\n",
    "de": "WEBVTT\n\n00:00:00.000 --> 00:00:01.500\nHallo\n\n00:00:01.500 --> 00:00:03.000\nWelt\n",
    "fr": "WEBVTT\n\n00:00:00.000 --> 00:00:01.500\nBonjour\n\n00:00:01.500 --> 00:00:03.000\nMonde\n",
}


class MediaHandler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    state = {"throttle": 0, "served": 0, "ranges": 0}
    lock = threading.Lock()

    def log_message(self, *args):
        pass

    def do_HEAD(self):
        self.answer(False)

    def do_GET(self):
        self.answer(True)

    def reply(self, body, status=200, kind="text/plain"):
        data = body.encode("utf-8") if isinstance(body, str) else body
        self.send_response(status)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def answer(self, with_body):
        route = self.path.split("?")[0]
        query = self.path.split("?", 1)[1] if "?" in self.path else ""
        if route == "/ctl":
            found = re.search(r"throttle=(\d+)", query)
            if found:
                self.state["throttle"] = int(found.group(1))
            if "reset" in query:
                self.state["served"] = 0
                self.state["ranges"] = 0
            self.reply(json.dumps(self.state), kind="application/json")
            return
        found = re.match(r"/sub/(\w+)\.vtt$", route)
        if found and found.group(1) in VTT:
            self.reply(VTT[found.group(1)], kind="text/vtt")
            return
        if route != "/video.mp4":
            self.reply("", 404)
            return
        path = os.path.join(FIXTURE, "video.mp4")
        size = os.path.getsize(path)
        first, last, status = 0, size - 1, 200
        match = re.match(r"bytes=(\d*)-(\d*)$", self.headers.get("Range", "").strip())
        if match and (match.group(1) or match.group(2)):
            if match.group(1):
                first = int(match.group(1))
                if match.group(2):
                    last = min(int(match.group(2)), size - 1)
            else:
                first = max(0, size - int(match.group(2)))
            status = 206
        self.send_response(status)
        self.send_header("Content-Type", "video/mp4")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(last - first + 1))
        if status == 206:
            self.send_header("Content-Range", "bytes %d-%d/%d" % (first, last, size))
        self.end_headers()
        if not with_body:
            return
        with open(path, "rb") as handle:
            handle.seek(first)
            left = last - first + 1
            while left > 0:
                block = handle.read(min(16384, left))
                if not block:
                    break
                try:
                    self.wfile.write(block)
                except OSError:
                    return
                left -= len(block)
                with self.lock:
                    self.state["served"] += len(block)
                kb = self.state["throttle"]
                if kb > 0:
                    time.sleep(len(block) / (kb * 1024.0))


class MediaServer(http.server.ThreadingHTTPServer):
    daemon_threads = True

    def handle_error(self, request, client_address):
        pass


def ctl(query=""):
    with urllib.request.urlopen(MEDIA + "/ctl?" + query, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def make_fixture():
    os.makedirs(FIXTURE, exist_ok=True)
    target = os.path.join(FIXTURE, "video.mp4")
    if os.path.isfile(target) and os.path.getsize(target) > 4 * 1024 * 1024:
        return target
    subprocess.run([FFMPEG_SOURCE, "-y", "-v", "error", "-f", "lavfi", "-i", "testsrc2=s=640x360:r=25:d=40", "-f", "lavfi", "-i",
                    "sine=frequency=440:duration=40", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "16", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-shortest", "-movflags", "+faststart", target],
                   check=True, stdin=subprocess.DEVNULL, timeout=300)
    return target


def write_fakes(core):
    folder = os.path.join(core.home, "tools", "yt-dlp-dir")
    with open(os.path.join(folder, "video.json"), "r", encoding="utf-8") as handle:
        video = json.load(handle)
    size = os.path.getsize(os.path.join(FIXTURE, "video.mp4"))
    url = MEDIA + "/video.mp4"
    for item in video["formats"]:
        item["url"] = url + "?f=" + item["format_id"]
        item["filesize"] = size
    video["subtitles"] = {
        "en": [{"ext": "vtt", "url": MEDIA + "/sub/en.vtt", "name": "English"}],
        "de": [{"ext": "vtt", "url": MEDIA + "/sub/de.vtt", "name": "German"}],
    }
    video["automatic_captions"] = {"fr": [{"ext": "vtt", "url": MEDIA + "/sub/fr.vtt", "name": "French"}]}
    with open(os.path.join(folder, "video.json"), "w", encoding="utf-8") as handle:
        json.dump(video, handle)
    for item in video["formats"]:
        single = dict(video)
        for key in ("formats", "subtitles", "automatic_captions"):
            single.pop(key, None)
        single.update(item)
        single["subtitles"] = video["subtitles"]
        single["automatic_captions"] = video["automatic_captions"]
        with open(os.path.join(folder, "format-%s.json" % item["format_id"]), "w", encoding="utf-8") as handle:
            json.dump(single, handle)


def prepare(core):
    install_fake_ytdlp(core)
    install_ffmpeg(core)
    write_fakes(core)


def put(name, value):
    with open(os.path.join(SHARED, name + ".tmp"), "w", encoding="utf-8") as handle:
        json.dump(value, handle)
    os.replace(os.path.join(SHARED, name + ".tmp"), os.path.join(SHARED, name))


def get(name, timeout=60.0):
    path = os.path.join(SHARED, name)
    end = time.time() + timeout
    while time.time() < end:
        if os.path.isfile(path):
            try:
                with open(path, encoding="utf-8") as handle:
                    return json.load(handle)
            except ValueError:
                pass
        time.sleep(0.1)
    return None


def pairing(core):
    return core.snapshot("devicePairing") or {}


def media(core):
    return core.snapshot("addMedia") or {}


def page(core):
    return core.snapshot("subtitlesPage") or {}


def details(core):
    return core.snapshot("details") or {}


def row(core, key):
    found = [r for r in page(core).get("rows", []) if r.get("key") == key]
    return found[0] if found else {}


def open_page(core):
    core.action("DetailsSubtitles")
    core.wait_for("the subtitles page to open", lambda: page(core).get("open"), 10)
    core.wait_for("the page to read the video",
                  lambda: not page(core).get("reading", False) and (page(core).get("rows") or page(core).get("message")), 60)
    core.pump(0.3)


def subtitle_streams(core, path):
    ffmpeg = os.path.join(core.home, "tools", FFMPEG_NAME)
    result = subprocess.run([ffmpeg, "-hide_banner", "-i", path], capture_output=True, stdin=subprocess.DEVNULL, timeout=60)
    text = result.stderr.decode("utf-8", "replace")
    return len(re.findall(r"Stream #0:\d+.*: Subtitle", text))


def video_path(core, task):
    return task["destinationPath"].rstrip("/\\") + "/" + task["fileName"]


def beside(task):
    return os.listdir(task["destinationPath"])


def wait_streams(core, path, count, timeout=60):
    try:
        core.wait_for("%d subtitle streams in the file" % count, lambda: subtitle_streams(core, path) == count, timeout)
        return True
    except Exception:
        return False


def start_media_download(core, language, embed, srt):
    core.action("SetUrl", text=PAGE)
    core.action("ShowAddDialog", show=True)
    core.wait_for("qualities", lambda: media(core).get("state", 0) == STATE["QUALITIES"], 60)
    rows = core.rows("addQualities")
    low = [r for r in rows if "360" in r.get("label", "")]
    core.check("a 360p row exists", len(low) == 1, rows)
    core.action("SelectAllQualities", selected=False)
    core.action("ToggleQuality", index=low[0]["index"], selected=True)
    core.pump(0.2)
    core.action("ToggleSubtitle", code=language, selected=True)
    core.pump(0.2)
    if embed:
        core.action("SetEmbedSubtitles", embed=True)
    elif srt:
        core.action("SetSubtitleFormat", format="srt")
    core.pump(0.3)
    core.check("the chosen way is set", media(core).get("embedSubtitles", False) == embed, media(core))
    core.action("StartDownload")


def send_to_receiver(core, task, transfer):
    core.action("RowClick", id=task["id"], control=False, shift=False)
    core.pump(0.3)
    core.action("DetailsShare")
    core.pump(0.5)
    core.action("DeviceToggleManual")
    core.action("DeviceManualIp", text=RECEIVER_HOST)
    core.pump(0.2)
    core.action("DeviceManualConnect")
    confirm = core.wait_for("a pairing code", lambda: pairing(core) if pairing(core).get("state") == PAIR["CONFIRM"] else None, 60)
    other = get("receiver-code")
    core.check("both sides show the same six-digit code", confirm.get("code") == other and len(str(other or "")) == 6, (confirm.get("code"), other))
    core.action("PairingConfirm")
    found = core.wait_for("the receiver in the list", lambda: next((r for r in core.rows("deviceRows") if r.get("paired")), None), 60)
    core.action("DeviceSelect", id=found["id"])
    sheet = core.snapshot("deviceSheet") or {}
    if transfer:
        core.check("the sheet offers handing the download over", bool(sheet.get("canTransfer")), sheet)
    core.action("DeviceSetMode", transfer=transfer)
    core.pump(0.2)
    core.action("DeviceSend")
    seen = []

    def finished():
        state = core.snapshot("deviceSheet") or {}
        seen.append(state.get("state", 0))
        if state.get("state") in (SEND["DONE"], SEND["FAILED"]) or not state.get("open", False):
            return {"state": state.get("state", 0), "open": state.get("open", False)}
        return None

    done = core.wait_for("the send to finish", finished, 120)
    core.check("the send finished without a failure", done.get("state", 0) != SEND["FAILED"] and SEND["FAILED"] not in seen,
               (done, sorted(set(seen))))


def pause_midway(core, task_id):
    core.wait_for("some bytes", lambda: (core.task(task_id=task_id) or {}).get("downloadedBytes", 0) > 1024 * 1024, 90)
    core.action("Pause", id=task_id)
    paused = core.wait_task(["PAUSED", "COMPLETED"], timeout=30, task_id=task_id)
    core.check("the download was paused midway", paused["status"] == "PAUSED" and 0 < paused["downloadedBytes"] < paused["totalBytes"], paused)
    return paused


def sender(scenario):
    from corehost import Core, CheckFailed
    core = Core("sendsubs-a", before_start=prepare)
    try:
        core.pump(1.0)
        core.check("sender: the receiver started", get("receiver-ready") is True)
        finished = scenario in ("S1", "S2")
        embed = scenario in ("S1", "S3", "S4")
        ctl("reset=1&throttle=0" if finished else "reset=1&throttle=%d" % SLOW_KB)
        start_media_download(core, "en", embed, not embed)
        task = core.wait_task(["DOWNLOADING", "COMPLETED", "ERROR"], timeout=90, url_part="127.0.0.1:%d" % MEDIA_PORT)
        core.check("sender: the video download started", task["status"] != "ERROR", task)
        if finished:
            task = core.wait_task(["COMPLETED", "ERROR"], timeout=120, task_id=task["id"])
            core.check("sender: the video download completed", task["status"] == "COMPLETED", task)
            path = video_path(core, task)
            if embed:
                core.check("sender: the English subtitle is inside the file", wait_streams(core, path, 1), subtitle_streams(core, path))
            else:
                core.wait_for("the subtitle step to end", lambda: os.path.isfile(os.path.join(task["destinationPath"], os.path.splitext(task["fileName"])[0] + ".en.srt")) and
                              not any(n.startswith(".fjsubs_") for n in os.listdir(task["destinationPath"])), 40)
                core.check("sender: the English file lies next to the video", True, beside(task))
            put("sender-task", task)
            send_to_receiver(core, task, True)
        else:
            task = pause_midway(core, task["id"])
            core.check("sender: the chosen language is on the unfinished task", task.get("subtitles", "") == "en" or task.get("subtitleLanguages", "") != "", task)
            ctl("throttle=0")
            put("served-before", ctl()["served"])
            put("sender-task", task)
            send_to_receiver(core, task, scenario == "S3")
        put("sender-done", True)
        result = get("receiver-result", 240) or {}
        core.check("receiver confirmed every check", result.get("ok"), result)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(json.dumps(core.snapshot("deviceSheet")))
        print(core.log_text()[-3000:])
    put("sender-done", True)
    core.finish()


def receiver(scenario):
    from corehost import Core, CheckFailed
    core = Core("sendsubs-b", before_start=prepare)
    try:
        core.action("SettingToggle", id="visibleToDevices", on=True)
        core.pump(1.0)
        put("receiver-ready", True)
        confirm = core.wait_for("a pairing code", lambda: pairing(core) if pairing(core).get("state") == PAIR["CONFIRM"] else None, 90)
        put("receiver-code", confirm.get("code"))
        core.action("PairingConfirm")
        core.wait_for("an offer", lambda: pairing(core) if pairing(core).get("state") == PAIR["OFFER"] else None, 90)
        core.action("PairingConfirm")
        task = core.wait_for("the received task", lambda: (core.tasks() or [None])[0], 90)
        print("    received: %s %s %s" % (task["engine"], task["status"], task["fileName"]))
        finished = scenario in ("S1", "S2")
        if finished:
            check_finished(core, scenario)
        else:
            check_unfinished(core, scenario, task)
        put("receiver-result", {"ok": not core.failures, "failures": core.failures})
        get("sender-done", 120)
    except CheckFailed as failure:
        core.check(str(failure), False)
        put("receiver-result", {"ok": False, "why": str(failure)})
        print(core.log_text()[-3000:])
    core.finish()


def check_finished(core, scenario):
    embed = scenario == "S1"
    sent = get("sender-task") or {}
    task = core.wait_task("COMPLETED", timeout=120, file_name=sent.get("fileName"))
    core.pump(0.5)
    core.check("receiver: one row, a finished plain file", len(core.tasks()) == 1 and task["engine"] == "HTTP_DIRECT", core.tasks())
    path = video_path(core, task)
    base = os.path.splitext(task["fileName"])[0]
    core.check("receiver: the file is there under the sender's name", os.path.isfile(path) and task["fileName"] == sent.get("fileName"), (path, sent.get("fileName")))
    if embed:
        core.check("receiver: the English subtitle came inside the file", subtitle_streams(core, path) == 1, subtitle_streams(core, path))
        core.check("receiver: no side file", not [n for n in beside(task) if n.endswith(".srt") or n.endswith(".vtt")], beside(task))
    else:
        core.check("receiver: the .en.srt arrived next to the video", os.path.isfile(os.path.join(task["destinationPath"], base + ".en.srt")), beside(task))
        core.check("receiver: no subtitle stream inside", subtitle_streams(core, path) == 0)
    core.action("RowClick", id=task["id"], control=False, shift=False)
    core.pump(0.6)
    core.check("receiver: a finished video offers adding subtitles", details(core).get("canSubtitles") is True, details(core))
    open_page(core)
    keys = sorted(r.get("key") for r in page(core).get("rows", []))
    print("    rows: " + ", ".join("%s%s" % (r.get("name"), " (there)" if r.get("present") else "") for r in page(core).get("rows", [])))
    core.check("receiver: the page lists the languages of the video again", keys == ["de", "en", "fr.auto"], keys)
    core.check("receiver: English is marked as there and locked", row(core, "en").get("present") and row(core, "en").get("checked") and not row(core, "en").get("enabled"), row(core, "en"))
    core.check("receiver: German can be picked", row(core, "de").get("enabled") and not row(core, "de").get("present"), row(core, "de"))
    core.action("SubtitlesToggle", key="de", selected=True)
    core.pump(0.3)
    core.check("receiver: Add is on after picking German", page(core).get("canAdd") is True, page(core))
    core.action("SubtitlesAdd")
    core.wait_for("adding to end", lambda: not page(core).get("running", False) and page(core).get("added", "") != "", 90)
    core.check("receiver: German was added without a refusal or failure", "German" in page(core).get("added", "") and page(core).get("failed", "") == "" and page(core).get("refused", "") == "", page(core))
    if embed:
        core.check("receiver: German went into the file (two subtitle streams)", subtitle_streams(core, path) == 2, subtitle_streams(core, path))
    else:
        core.check("receiver: name.de.srt lies next to the video", os.path.isfile(os.path.join(task["destinationPath"], base + ".de.srt")), beside(task))
    core.check("receiver: German is marked as there", row(core, "de").get("present") is True, row(core, "de"))
    core.check("receiver: still one row", len(core.tasks()) == 1, core.tasks())
    core.action("SubtitlesDismiss")
    core.wait_for("the page to close", lambda: not page(core).get("open", False), 10)


def check_unfinished(core, scenario, first):
    transfer = scenario == "S3"
    sent = get("sender-task") or {}
    core.check("receiver: the unfinished video arrives as a media download", first["engine"] == "MEDIA_STREAM", first)
    core.check("receiver: the chosen language travelled with it", "en" in (first.get("subtitles", "") + "," + first.get("subtitleLanguages", "")), first)
    if transfer:
        core.check("receiver: it keeps the bytes of the sender", first["downloadedBytes"] > 0, first)
    task = core.wait_task(["COMPLETED", "ERROR"], timeout=240, task_id=first["id"])
    core.check("receiver: the download completed", task["status"] == "COMPLETED", task)
    served = ctl()["served"]
    before = get("served-before") or 0
    size = os.path.getsize(os.path.join(FIXTURE, "video.mp4"))
    print("    server served %d bytes in all, the video is %d" % (served, size))
    if transfer:
        core.check("receiver: it continued from the sent bytes (the server did not serve the video twice)", served < size * 1.5, (served, size))
    else:
        core.check("receiver: link only downloads from zero (the server served the whole video again)", served - before >= size * 0.95, (served - before, size))
    path = video_path(core, task)
    core.check("receiver: the English subtitle was embedded after the download", wait_streams(core, path, 1, 90), subtitle_streams(core, path))
    core.pump(0.5)
    core.check("receiver: the playable file is a real mp4", os.path.getsize(path) > 1024 * 1024, os.path.getsize(path))
    core.action("RowClick", id=task["id"], control=False, shift=False)
    core.pump(0.6)
    core.check("receiver: a finished video offers adding subtitles", details(core).get("canSubtitles") is True, details(core))
    open_page(core)
    keys = sorted(r.get("key") for r in page(core).get("rows", []))
    core.check("receiver: the page lists the languages of the video", keys == ["de", "en", "fr.auto"], keys)
    core.check("receiver: English is marked as there and locked", row(core, "en").get("present") and not row(core, "en").get("enabled"), page(core).get("rows"))
    core.action("SubtitlesToggle", key="de", selected=True)
    core.pump(0.3)
    core.action("SubtitlesAdd")
    core.wait_for("adding to end", lambda: not page(core).get("running", False) and page(core).get("added", "") != "", 90)
    core.check("receiver: German went into the file (two subtitle streams)", subtitle_streams(core, path) == 2, subtitle_streams(core, path))
    core.check("receiver: still one row", len(core.tasks()) == 1, core.tasks())
    core.action("SubtitlesDismiss")
    core.wait_for("the page to close", lambda: not page(core).get("open", False), 10)


def coordinator():
    if not os.path.isfile(FFMPEG_SOURCE):
        raise SystemExit("ffmpeg not found: " + FFMPEG_SOURCE)
    make_fixture()
    server = MediaServer(("127.0.0.1", MEDIA_PORT), MediaHandler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    wanted = sys.argv[1:] or SCENARIOS
    here = os.path.abspath(__file__)
    summary = []
    for scenario in wanted:
        shutil.rmtree(SHARED, ignore_errors=True)
        os.makedirs(SHARED, exist_ok=True)
        print("=== " + scenario, flush=True)
        env = dict(os.environ, FETCHJET_SYNC_UDP_PORT="28491", FETCHJET_SYNC_TCP_PORT="28492", FETCHJET_SYNC_NO_BROADCAST="1",
                   FETCHJET_NO_USAGE_PING="1", FJ_TEST_RELEASE=os.environ.get("FJ_TEST_RELEASE", "1"))
        env_b = dict(env, FETCHJET_SYNC_BIND_HOST=RECEIVER_HOST)
        env_a = dict(env, FETCHJET_SYNC_BIND_HOST=SENDER_HOST)
        b = subprocess.Popen([sys.executable, here, "receiver", scenario], env=env_b, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
        time.sleep(1.0)
        a = subprocess.Popen([sys.executable, here, "sender", scenario], env=env_a, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
        try:
            out_a, _ = a.communicate(timeout=420)
            out_b, _ = b.communicate(timeout=180)
        except subprocess.TimeoutExpired:
            a.kill()
            b.kill()
            out_a, out_b = "timeout", "timeout"
        print("--- sender\n" + out_a)
        print("--- receiver\n" + out_b)
        summary.append((scenario, a.returncode == 0 and b.returncode == 0))
    print("")
    for scenario, ok in summary:
        print("%s  %s" % ("PASS" if ok else "FAIL", scenario))
    sys.exit(0 if all(ok for _, ok in summary) else 1)


if __name__ == "__main__":
    if sys.platform == "darwin":
        print("skip  test_send_subtitles needs several loopback addresses (127.0.0.2 / .3): not on macOS")
        sys.exit(0)
    role = sys.argv[1] if len(sys.argv) > 1 else ""
    if role == "receiver":
        receiver(sys.argv[2])
    elif role == "sender":
        sender(sys.argv[2])
    else:
        sys.argv = [sys.argv[0]] + [a for a in sys.argv[1:] if a in SCENARIOS]
        coordinator()
