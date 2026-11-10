import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, REPO, SERVER, install_fake_ytdlp

SHOT = os.path.join(REPO, "desktopNative", "tools", "shot", "build", "Release", "shot.exe")
APP = os.path.join(REPO, "desktopNative", "ui", "app.slint")
OUT = os.environ.get("FJ_SHOT_OUT", "E:/tools/probes/ui/shots")
ARABIC_NAME = "\u062a\u0642\u0631\u064a\u0631 \u0627\u0644\u0645\u0628\u064a\u0639\u0627\u062a \u0627\u0644\u0633\u0646\u0648\u064a.pdf"
FILTER = {name: index for index, name in enumerate(ENUMS["SideFilterCode"])}
MEDIA = {name: index for index, name in enumerate(ENUMS["MediaStateCode"])}


def shot(core, name, width=1200, height=760, extra=None):
    os.makedirs(OUT, exist_ok=True)
    fixture = os.path.join(OUT, name + ".json")
    data = core.fixture(fixture, {"Emoji": {"ready": True}})
    command = [SHOT, APP, "AppWindow", "--json", fixture, "--width", str(width), "--height", str(height),
               "--emoji-callback", "Emoji.icon", "--out", os.path.join(OUT, name + ".png"), "--quiet"]
    command.append("--dark" if data["Theme"]["dark"] else "--light")
    if extra:
        command += extra
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
    ok = result.returncode == 0
    print(("  shot  " if ok else "  FAIL  ") + name + ("" if ok else " : " + (result.stdout + result.stderr).strip()[-400:]))
    return ok


def run(only=None):
    core = Core("shots", before_start=install_fake_ytdlp)
    failed = 0

    def take(name, **kwargs):
        nonlocal failed
        if only and not any(part in name for part in only):
            return
        core.pump(0.5)
        if not shot(core, name, **kwargs):
            failed += 1

    try:
        core.action("SystemTheme", dark=True)
        core.pump(0.5)
        take("home-empty-dark")
        core.debug("addDirect", {"url": SERVER + "/file/1MB.bin?key=shots-a", "fileName": "alpha.zip"})
        core.wait_task("COMPLETED", timeout=40, file_name="alpha.zip")
        core.debug("addDirect", {"url": SERVER + "/file/90MB.bin?key=shots-b&throttle=700", "fileName": ARABIC_NAME})
        running = core.wait_task("DOWNLOADING", timeout=40, file_name=ARABIC_NAME)
        core.debug("addDirect", {"url": SERVER + "/file/30MB.bin?key=shots-c&throttle=300", "fileName": "movie.mkv"})
        third = core.wait_task("DOWNLOADING", timeout=40, file_name="movie.mkv")
        core.wait_for("bytes", lambda: (core.task(task_id=third["id"]) or {}).get("downloadedBytes", 0) > 200 * 1024, 30)
        core.action("Pause", id=third["id"])
        core.wait_task("PAUSED", timeout=20, task_id=third["id"])
        core.debug("addDirect", {"url": SERVER + "/status/404?key=shots-d", "fileName": "missing.iso"})
        core.wait_task("ERROR", timeout=40, file_name="missing.iso")
        take("home-dark")
        take("home-dark-medium", width=820, height=640)
        core.action("RowClick", id=running["id"], control=False, shift=False)
        take("details-side-dark", width=1360, height=800)
        take("details-sheet-dark", width=1100, height=750)
        core.action("RowClick", id=third["id"], control=True, shift=False)
        take("selection-dark")
        core.action("ClearChecks")
        core.action("Remove", id=third["id"])
        take("alert-remove-dark")
        core.action("DismissRemove")
        core.action("CloseDetails")
        core.action("SetUrl", text=SERVER + "/file/25MB.bin?name=setup-installer.exe")
        core.action("ShowAddDialog", show=True)
        take("add-direct-dark", width=1100, height=820)
        core.action("ScheduleAtTime")
        core.action("SetConnections", count=8)
        take("add-direct-time-dark", width=1100, height=900)
        core.action("ScheduleQuick", minutes=60)
        take("add-direct-scheduled-dark", width=820, height=820)
        core.action("ShowAddDialog", show=False)
        core.action("ToggleTheme")
        core.action("SetUrl", text=SERVER + "/file/25MB.bin?name=setup-installer.exe")
        core.action("ShowAddDialog", show=True)
        take("add-direct-light", width=1100, height=820)
        core.action("SetLanguage", choice="ar")
        take("add-direct-light-ar", width=1100, height=820)
        core.action("SetLanguage", choice="en")
        core.action("ShowAddDialog", show=False)
        core.action("ToggleTheme")
        core.action("SetUrl", text="https://www.youtube.com/watch?v=fakevideo01")
        core.action("ShowAddDialog", show=True)
        core.wait_for("qualities", lambda: (core.snapshot("addMedia") or {}).get("state", 0) == MEDIA["QUALITIES"], 60)
        take("media-qualities-dark", width=1100, height=820)
        core.action("ToggleSubtitle", code="en", selected=True)
        core.action("SetClipEnabled", on=True)
        take("media-clip-dark", width=1100, height=820, extra=["--scroll", "550,400,0,-420"])
        take("media-clip-dark-2", width=1100, height=820, extra=["--scroll", "550,400,0,-900"])
        core.action("OpenClipPicker", end=True)
        take("media-picker-dark", width=1100, height=820)
        core.action("ClipPickerClose")
        core.action("SetLanguage", choice="ar")
        take("media-clip-dark-ar", width=1100, height=820, extra=["--scroll", "550,400,0,-420"])
        core.action("SetLanguage", choice="en")
        core.action("ShowAddDialog", show=False)
        core.action("SetUrl", text="https://www.youtube.com/playlist?list=PLfake")
        core.action("ShowAddDialog", show=True)
        core.wait_for("playlist", lambda: (core.snapshot("addMedia") or {}).get("state", 0) == MEDIA["PLAYLIST"], 60)
        core.action("SetPlaylistQuality", quality=5)
        take("media-playlist-dark", width=1100, height=820)
        take("media-playlist-dark-2", width=1100, height=820, extra=["--scroll", "550,400,0,-700"])
        core.action("ShowAddDialog", show=False)
        core.action("SetUrl", text="https://www.youtube.com/watch?v=broken01")
        core.action("ShowAddDialog", show=True)
        core.wait_for("error", lambda: (core.snapshot("addMedia") or {}).get("state", 0) == MEDIA["ERROR"], 90)
        take("media-error-dark", width=1100, height=820)
        core.action("ShowAddDialog", show=False)
        core.action("SetUrl", text=SERVER + "/file/1MB.bin?key=shots-a")
        core.action("ShowAddDialog", show=True)
        core.action("StartDownload")
        core.wait_for("a question", lambda: len(core.rows("choices")) > 0, 20)
        take("choice-dialog-dark", width=1100, height=820)
        first = core.rows("choices")[0]
        core.action("ChoiceDismiss", kind=first.get("kind", 0))
        core.action("ShowAddDialog", show=False)
        core.action("ToggleTheme")
        core.action("SetUrl", text="")
        take("home-light")
        core.action("RowClick", id=running["id"], control=False, shift=False)
        take("details-side-light", width=1360, height=800)
        core.action("CloseDetails")
        core.action("SetLanguage", choice="ar")
        take("home-light-ar")
        core.action("ToggleTheme")
        core.action("RowClick", id=running["id"], control=False, shift=False)
        take("details-side-dark-ar", width=1360, height=800)
        core.action("Remove", id=third["id"])
        take("alert-remove-dark-ar", width=1100, height=750)
        core.action("DismissRemove")
        core.action("CloseDetails")
        core.action("SetLanguage", choice="zh")
        take("home-dark-zh", width=1100, height=640)
        core.action("SetLanguage", choice="en")
        core.action("PauseAll")
        core.pump(1.0)
    except CheckFailed as failure:
        failed += 1
        print("  FAIL  " + str(failure))
        print(core.log_text()[-1500:])
    crash = core.crash_text()
    if crash:
        failed += 1
        print(crash[:1500])
    core.shutdown()
    print("shots in " + OUT + (", %d failed" % failed if failed else ""))
    sys.stdout.flush()
    os._exit(1 if failed else 0)


if __name__ == "__main__":
    run(sys.argv[1:])
