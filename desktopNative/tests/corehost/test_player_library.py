import gzip
import http.server
import json
import os
import shutil
import subprocess
import sys
import threading
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

PORT = int(os.environ.get("FJ_PLAYER_LIBRARY_PORT", "18276"))
BASE = "http://127.0.0.1:%d" % PORT
os.environ["FETCHJET_NO_USAGE_PING"] = "1"
os.environ["FETCHJET_LIBMPV_URL"] = BASE + "/libmpv.gz"
os.environ["FETCHJET_LIBMPV_SHA256"] = "0" * 64

from corehost import Core, CheckFailed, ENUMS, LIBMPV_NAME, PLAYER_EXE, PLAYER_LIBMPV, run_window, sha256_of_file, window_runner
from test_compress import FFMPEG_SOURCE
from test_hidden import add_and_wait

EFFECT = {name: index for index, name in enumerate(ENUMS["EffectKind"])}
CHOICE = {name: index for index, name in enumerate(ENUMS["ChoiceDialogKind"])}
EXE = PLAYER_EXE
OUT = os.environ.get("FJ_SHOT_OUT", "")


class Site:
    def __init__(self, folder):
        self.folder = folder
        self.gate = threading.Event()
        self.gate.set()
        self.fail = False
        self.requests = 0
        site = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                name = self.path.lstrip("/").split("?", 1)[0]
                path = os.path.join(site.folder, name)
                if name == "libmpv.gz":
                    site.requests += 1
                    if site.fail:
                        self.send_error(404)
                        return
                if not os.path.isfile(path):
                    self.send_error(404)
                    return
                data = open(path, "rb").read()
                self.send_response(200)
                self.send_header("Content-Length", str(len(data)))
                self.send_header("Content-Type", "application/octet-stream")
                self.end_headers()
                if name != "libmpv.gz":
                    self.wfile.write(data)
                    return
                half = len(data) // 2
                self.wfile.write(data[:half])
                self.wfile.flush()
                site.gate.wait(120)
                try:
                    self.wfile.write(data[half:])
                except OSError:
                    pass

        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
        self.server.daemon_threads = True
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def stop(self):
        self.gate.set()
        self.server.shutdown()


def make_site(folder):
    os.makedirs(folder, exist_ok=True)
    subprocess.run([FFMPEG_SOURCE, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "color=c=0x204060:s=320x180:d=20:r=25",
                    "-f", "lavfi", "-i", "sine=f=440:d=20", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac",
                    os.path.join(folder, "clip.mp4")], check=True, stdin=subprocess.DEVNULL, timeout=120)
    with open(PLAYER_LIBMPV, "rb") as source, gzip.open(os.path.join(folder, "libmpv.gz"), "wb", compresslevel=1) as out:
        shutil.copyfileobj(source, out)


def library(core):
    return os.path.join(core.home, "tools", LIBMPV_NAME)


def remove_library(core):
    for name in [LIBMPV_NAME, "libmpv.sha256"]:
        path = os.path.join(core.home, "tools", name)
        if os.path.exists(path):
            os.remove(path)


def waiting(core):
    return next((c for c in core.rows("choices") if c.get("kind") == CHOICE["LIBRARY_DOWNLOAD"]), None)


def opened(core):
    return [json.loads(e.get("text") or "{}") for e in core.take_effects() if e.get("kind", 0) == EFFECT["OPEN_PLAYER"]]


def wait_open(core, timeout=60):
    found = []

    def done():
        found.extend(opened(core))
        return bool(found)

    core.wait_for("the player to open", done, timeout)
    return found[0]


def play(core, task_id):
    core.take_effects()
    core.check("play is taken", core.debug("playTask", {"id": task_id}) == "ok")
    core.pump(0.3)


def core_part(core, clip, site):
    strings = core.strings()
    gz_sha = os.environ["FETCHJET_LIBMPV_SHA256"]
    core.check("no player library at the start", not os.path.exists(library(core)))

    print("ask mode: Download the player shows the download, then the player opens")
    site.gate.clear()
    play(core, clip)
    ask = next((c for c in core.rows("choices") if c.get("kind") == CHOICE["PLAYER_LIBRARY"]), None)
    core.check("the first play asks", ask is not None, core.rows("choices"))
    core.action("ChoicePrimary", kind=CHOICE["PLAYER_LIBRARY"])
    core.pump(0.3)
    dialog = core.wait_for("the download dialog", lambda: waiting(core), 10)
    core.check("the ask dialog is replaced by the download dialog",
               not any(c.get("kind") == CHOICE["PLAYER_LIBRARY"] for c in core.rows("choices")), core.rows("choices"))
    core.check("it names the in-app player and the file", dialog.get("title") == strings["playerModeOn"] and dialog.get("name") == "clip", dialog)
    core.check("it says Downloading with a bar", dialog.get("badge") == strings["sysStatusDownloading"] and dialog.get("barShown") is True, dialog)
    core.check("no Retry while it downloads", not dialog.get("primaryLabel"), dialog.get("primaryLabel"))
    core.check("the system app is offered meanwhile", dialog.get("secondaryLabel") == strings["playerAskSystem"], dialog.get("secondaryLabel"))
    half = core.wait_for("a percent half way", lambda: (lambda d: d if d and d.get("percent", "").endswith("%") and d.get("progress", 0) >= 0.3 else None)(waiting(core)), 30)
    core.check("the percent and the bar move together", abs(int(half["percent"][:-1]) / 100.0 - half["progress"]) < 0.011, half)
    core.check("nothing opened while downloading", opened(core) == [])
    first = None
    site.gate.set()
    try:
        first = wait_open(core)
    except CheckFailed as failure:
        core.check(str(failure), False, core.rows("choices"))
    core.check("the player opens by itself when the download ends", first is not None and first["items"][first["index"]]["path"].endswith("clip.mp4"), first)
    core.wait_for("the dialog to close", lambda: waiting(core) is None, 10)
    core.check("the library is installed with its sha", os.path.isfile(library(core)) and
               open(os.path.join(core.home, "tools", "libmpv.sha256")).read().strip() == gz_sha)

    print("a failed download shows the failure with Retry; Retry opens the player")
    remove_library(core)
    site.fail = True
    play(core, clip)
    failed = core.wait_for("the failure", lambda: (lambda d: d if d and d.get("primaryLabel") else None)(waiting(core)), 30)
    core.check("in-app mode does not ask again", not any(c.get("kind") == CHOICE["PLAYER_LIBRARY"] for c in core.rows("choices")))
    core.check("the failure badge", failed.get("badge") == strings["sysDownloadFailed"] and not failed.get("barShown") and failed.get("danger") is True, failed)
    core.check("the failure text", failed.get("question", "").startswith(strings["sysMediaToolsDownloadFailed"]), failed.get("question"))
    core.check("Retry is the main button", failed.get("primaryLabel") == strings["retry"], failed.get("primaryLabel"))
    core.check("nothing opened after the failure", opened(core) == [])
    site.fail = False
    core.action("ChoicePrimary", kind=CHOICE["LIBRARY_DOWNLOAD"])
    again = None
    try:
        again = wait_open(core)
    except CheckFailed as failure:
        core.check(str(failure), False, core.rows("choices"))
    core.check("Retry downloads and opens the player", again is not None and os.path.isfile(library(core)), again)
    core.wait_for("the dialog to close after Retry", lambda: waiting(core) is None, 10)

    print("Cancel while downloading: the library still lands, the player does not open")
    remove_library(core)
    site.gate.clear()
    play(core, clip)
    core.wait_for("the download dialog", lambda: waiting(core), 10)
    core.action("ChoiceDismiss", kind=CHOICE["LIBRARY_DOWNLOAD"])
    core.pump(0.3)
    core.check("cancel closes the dialog", waiting(core) is None)
    site.gate.set()
    core.wait_for("the library to be installed", lambda: os.path.isfile(library(core)), 60)
    core.pump(1.0)
    core.check("a cancelled wait does not open the player", opened(core) == [])
    play(core, clip)
    core.check("with the library ready play opens at once, no dialog", len(opened(core)) == 1 and waiting(core) is None)


def host_part(core, clip, site):
    missing = [p for p in [EXE, *window_runner()] if not os.path.isfile(p)]
    if missing:
        print("SKIP the window part, missing: " + ", ".join(missing))
        return
    print("window: play with no library, the dialog shows, then the video plays")

    def window(name, steps, after):
        script = os.path.join(core.work, name + "-script.txt")
        with open(script, "w", encoding="utf-8") as handle:
            for step in steps:
                handle.write(json.dumps(step) + "\n")
        log = os.path.join(core.work, name + "-player.log")
        if os.path.exists(log):
            os.remove(log)
        env = dict(os.environ, FJ_PLAYER_LOG=log, FJ_PLAYER_AO="null")
        folder = OUT or core.work
        os.makedirs(folder, exist_ok=True)
        total = 500 * len(steps) + 6000
        output = run_window([EXE, "--home", core.home, "--downloads", core.downloads, "--no-bridge", "--no-focus", "--no-auto-downloads", "--script", script],
                            env, total, after or total - 2000, os.path.join(folder, name + ".bmp"))
        print("  " + output.strip().replace("\n", "\n  ")[-200:])
        text = open(log, encoding="utf-8", errors="replace").read() if os.path.exists(log) else ""
        return [l for l in text.splitlines() if l.startswith("state ")]

    remove_library(core)
    site.gate.clear()
    lines = window("player-library-downloading", [{"type": "RowQuickAction", "id": clip}] + [{"player": "state"}] * 10, 4000)
    core.check("the first window run logged steps", len(lines) >= 4, len(lines))
    core.check("the player is not open while the library downloads", lines and not any(" open=1 " in l for l in lines), lines[-1:])
    core.check("the library is not there yet", not os.path.isfile(library(core)))
    remove_library(core)
    threading.Timer(6.0, site.gate.set).start()
    lines = window("player-library-playing", [{"type": "RowQuickAction", "id": clip}] + [{"player": "state"}] * 30, None)
    core.check("the second window run logged its steps", len(lines) >= 25, len(lines))
    early = [l for l in lines[:6] if " open=1 " in l]
    core.check("not open before the gate opens", early == [], early[:1])
    last = lines[-1] if lines else ""
    pos = float(last.split(" pos=", 1)[1].split(" ", 1)[0]) if " pos=" in last else 0.0
    core.check("after the download the clip plays", " open=1 " in last and last.rstrip().endswith("clip.mp4") and " paused=0 " in last and pos > 0.5, last)
    core.check("the library was installed by the window run", os.path.isfile(library(core)))


def run():
    core = Core("player_library")
    folder = os.path.join(core.work, "site")
    make_site(folder)
    os.environ["FETCHJET_LIBMPV_SHA256"] = sha256_of_file(os.path.join(folder, "libmpv.gz"))
    site = Site(folder)
    try:
        core.pump(0.5)
        task = add_and_wait(core, BASE + "/clip.mp4", "clip.mp4")
        core.check("the clip is downloaded", task and task["status"] == "COMPLETED", task and task["status"])
        clip = task["id"]
        core_part(core, clip, site)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
        site.stop()
        core.finish()
        return
    crash = core.crash_text()
    core.check("no entries in native-crash.log", crash == "", crash[:1500])
    core.shutdown()
    try:
        host_part(core, clip, site)
    except CheckFailed as failure:
        core.check(str(failure), False)
    site.stop()
    print("%d checks, %d failed" % (core.checks, len(core.failures)))
    sys.stdout.flush()
    os._exit(1 if core.failures else 0)


if __name__ == "__main__":
    run()
