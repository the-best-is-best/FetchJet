import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, REPO
from shots import SHOT, APP
from test_compress import install_ffmpeg, serve_folder, select

OUT = os.environ.get("FJ_SHOT_OUT", "E:/tools/probes/cut-desktop")
MEDIA = os.environ.get("FJ_CUT_MEDIA", "E:/tools/mpv-spike/media")


def shot(core, name, width=1300, height=900, patch=None):
    os.makedirs(OUT, exist_ok=True)
    fixture = os.path.join(OUT, name + ".json")
    data = core.fixture(fixture, {"Emoji": {"ready": True}})
    if patch:
        patch(data)
        with open(fixture, "w", encoding="utf-8") as handle:
            json.dump(data, handle, ensure_ascii=False, indent=1)
    command = [SHOT, APP, "AppWindow", "--json", fixture, "--width", str(width), "--height", str(height),
               "--emoji-callback", "Emoji.icon", "--out", os.path.join(OUT, name + ".png"), "--quiet",
               "--font-dir", os.path.join(REPO, "desktopNative", "res", "fonts"),
               "--emoji-dir", os.path.join(REPO, "desktopNative", "res", "emoji")]
    command.append("--dark" if data["Theme"]["dark"] else "--light")
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
    ok = result.returncode == 0
    print(("  shot  " if ok else "  FAIL  ") + name + ("" if ok else " : " + (result.stdout + result.stderr).strip()[-600:]))
    return ok


def cut_page(core):
    return (core.snapshot("audioCompress") or {}).get("cut", {}) or {}


def running(data):
    cut = data["Core"]["audioCompress"]["cut"]
    cut.update({"open": True, "chooser": False, "running": True, "progress": 0.42, "percent": "42%",
                "elapsed": "0:03", "timeLeft": "0:04", "canStart": False})


def four(core, prefix, take, patch=None):
    take(prefix + "-dark-en", patch)
    core.action("ToggleTheme")
    core.pump(0.6)
    take(prefix + "-light-en", patch)
    core.action("SettingPick", id="language", index=2)
    core.pump(1.0)
    take(prefix + "-light-ar", patch)
    core.action("ToggleTheme")
    core.pump(0.6)
    take(prefix + "-dark-ar", patch)
    core.action("SettingPick", id="language", index=1)
    core.pump(1.0)


def run(phase):
    core = Core("shots-cut", before_start=install_ffmpeg)
    server = None
    failed = 0

    def take(name, patch=None):
        nonlocal failed
        core.pump(0.6)
        if not shot(core, phase + "-" + name, patch=patch):
            failed += 1

    try:
        core.action("SystemTheme", dark=True)
        core.pump(0.6)
        server = serve_folder(MEDIA)
        port = server.server_address[1]
        core.debug("addDirect", {"url": "http://127.0.0.1:%d/one.mp4" % port, "fileName": "one.mp4"})
        video = core.wait_task("COMPLETED", timeout=60, file_name="one.mp4")
        core.pump(0.6)
        select(core, video["id"])
        core.pump(0.6)
        four(core, "details", take)
        if phase == "after":
            core.action("RowMenu", id=video["id"])
            core.pump(0.4)
            core.action("RowMenuPick", key=next(r["key"] for r in core.rows("rowMenu") if "ut" in r["label"] or "قص" in r["label"]))
            core.wait_for("the chooser", lambda: cut_page(core).get("open") and cut_page(core).get("chooser"), 10)
            four(core, "chooser", take)
            core.action("VideoCutChoose", timeline=False)
            core.wait_for("the time view", lambda: cut_page(core).get("open") and not cut_page(core).get("chooser"), 10)
            core.wait_for("the duration", lambda: cut_page(core).get("duration", "") != "", 20)
            core.action("VideoCutPickTime", end=False)
            core.action("ClipPickerType", field=2, text="02")
            core.action("ClipPickerApply")
            core.pump(0.3)
            four(core, "time", take)
            core.action("VideoCutApply", startMs=2000, endMs=5000)
            core.pump(0.3)
            four(core, "progress", take, running)
            core.wait_for("the clip", lambda: not cut_page(core).get("running") and len(cut_page(core).get("clips", [])) == 1, 120)
            four(core, "saved", take)
            core.action("VideoCutDismiss")
            core.wait_for("the dialog to close", lambda: not cut_page(core).get("open"), 10)
            select(core, video["id"])
            core.wait_for("clips in details", lambda: len((core.snapshot("details") or {}).get("clips", [])) == 1, 10)
            four(core, "details-clips", take)
    except CheckFailed as failure:
        print("FAILED: " + str(failure))
        print(core.log_text()[-2000:])
        failed += 1
    if server:
        server.shutdown()
    core.shutdown()
    print("shots in " + OUT + (", %d failed" % failed if failed else ""))
    sys.stdout.flush()
    os._exit(1 if failed else 0)


if __name__ == "__main__":
    run(sys.argv[1] if len(sys.argv) > 1 else "after")
