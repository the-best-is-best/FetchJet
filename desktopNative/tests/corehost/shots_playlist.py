import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, install_fake_ytdlp
from shots import MEDIA, OUT, shot

LIST = "https://www.youtube.com/playlist?list=PLfake"


def open_list(core):
    core.action("ShowAddDialog", show=False)
    core.pump(0.3)
    core.action("SetUrl", text=LIST)
    core.action("ShowAddDialog", show=True)
    core.wait_for("playlist", lambda: (core.snapshot("addMedia") or {}).get("state", 0) == MEDIA["PLAYLIST"], 60)
    core.wait_for("playlist sizes", lambda: all(r.get("size") for r in core.rows("addPlaylist")), 60)
    core.pump(0.5)


def run(language="en"):
    core = Core("shots-playlist", before_start=install_fake_ytdlp)
    failed = 0
    try:
        core.action("SystemTheme", dark=True)
        if language != "en":
            core.action("SetLanguage", choice=language)
        core.pump(0.5)
        open_list(core)
        core.action("TogglePlaylistEntry", index=1, selected=False)
        core.action("TogglePlaylistEntry", index=2, selected=False)
        core.action("ScheduleQuick", minutes=60)
        core.pump(0.3)
        core.action("StartDownload")
        core.wait_for("two tasks", lambda: len(core.tasks()) == 2, 20)
        open_list(core)
        core.action("SetPlaylistQuality", quality=2)
        core.pump(0.5)
        suffix = "" if language == "en" else "-" + language
        for name, scroll in (("playlist-new", "-560"), ("playlist-new-list", "-760")):
            if not shot(core, name + suffix, width=1100, height=820, extra=["--scroll", "550,400,0," + scroll]):
                failed += 1
        core.wait_for("subtitle languages", lambda: len(core.rows("addSubtitles")) >= 3, 30)
        core.action("ToggleSubtitle", code="en", selected=True)
        core.pump(0.5)
        if not shot(core, "playlist-subtitles" + suffix, width=1100, height=820, extra=["--scroll", "262,520,0,-3000"]):
            failed += 1
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        core.action("SetUrl", text="https://www.youtube.com/playlist?list=PLgone")
        core.action("ShowAddDialog", show=True)
        core.wait_for("gone list", lambda: (core.snapshot("addMedia") or {}).get("state", 0) == MEDIA["PLAYLIST"], 60)
        core.wait_for("reads", lambda: not any(r.get("sizeBusy") for r in core.rows("addPlaylist")), 60)
        if not shot(core, "playlist-unavailable" + suffix, width=1100, height=820, extra=["--scroll", "550,400,0,-760"]):
            failed += 1
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        core.action("SetUrl", text="https://www.youtube.com/watch?v=fakelist001" + chr(10) + "https://www.youtube.com/watch?v=fakelist002")
        core.action("ShowAddDialog", show=True)
        core.wait_for("link rows", lambda: len(core.rows("addSubtitles")) >= 3, 60)
        core.pump(0.5)
        if not shot(core, "links-subtitles" + suffix, width=1100, height=820, extra=["--scroll", "550,400,0,-700"]):
            failed += 1
    except CheckFailed as failure:
        failed += 1
        print("  FAIL  " + str(failure))
    core.shutdown()
    print("shots in " + OUT)
    sys.stdout.flush()
    os._exit(1 if failed else 0)


if __name__ == "__main__":
    run(sys.argv[1] if len(sys.argv) > 1 else "en")
