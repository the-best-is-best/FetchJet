import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER, install_fake_ytdlp
from shots import MEDIA, shot

LIST = "https://www.youtube.com/playlist?list=PLfake"
START = SERVER + "/site/start.html"


def reset_url(core, url):
    core.action("SetUrl", text="")
    core.pump(0.3)
    core.action("SetUrl", text=url)
    core.pump(0.3)


def open_list(core):
    core.action("ShowAddDialog", show=False)
    core.pump(0.3)
    core.action("SetUrl", text=LIST)
    core.action("ShowAddDialog", show=True)
    core.wait_for("playlist", lambda: (core.snapshot("addMedia") or {}).get("state", 0) == MEDIA["PLAYLIST"], 60)
    core.wait_for("playlist sizes", lambda: all(r.get("size") for r in core.rows("addPlaylist")), 60)
    core.pump(0.5)


def run(language="en", dark=True):
    core = Core("shots-adddialog", before_start=install_fake_ytdlp)
    failed = 0
    tag = ("dark" if dark else "light") + ("" if language == "en" else "-" + language)
    try:
        core.action("SystemTheme", dark=dark)
        if language != "en":
            core.action("SetLanguage", choice=language)
        core.pump(0.5)
        core.action("ShowAddDialog", show=True)
        reset_url(core, START)
        core.wait_for("the page card", lambda: core.snapshot("add").get("grabShown", False), 20)
        core.pump(0.4)
        if not shot(core, "fix-page-" + tag, width=520, height=900):
            failed += 1
        core.action("SetGrabDepth", depth=2)
        core.action("GrabPage")
        core.wait_for("rows", lambda: not core.snapshot("add").get("grabRunning", False) and len(core.rows("addLinks")) > 0, 40)
        core.pump(0.4)
        core.action("ToggleGrabGroup", folder="Images", selected=False)
        core.action("ToggleGrabGroup", folder="Other", selected=False)
        core.pump(0.3)
        core.action("StartDownload")
        core.wait_for("three tasks", lambda: len(core.tasks()) >= 3, 60)
        core.pump(2.5)
        core.action("ShowAddDialog", show=True)
        reset_url(core, START)
        core.wait_for("the page card", lambda: core.snapshot("add").get("grabShown", False), 20)
        core.action("SetGrabDepth", depth=2)
        core.action("GrabPage")
        core.wait_for("rows", lambda: not core.snapshot("add").get("grabRunning", False) and len(core.rows("addLinks")) > 0, 40)
        core.pump(0.5)
        if not shot(core, "fix-links-" + tag, width=520, height=900, extra=["--scroll", "260,400,0,-420"]):
            failed += 1
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)

        open_list(core)
        core.action("ScheduleQuick", minutes=60)
        core.pump(0.3)
        core.action("StartDownload")
        core.wait_for("tasks", lambda: len(core.tasks()) >= 4, 30)
        open_list(core)
        if not shot(core, "fix-playlist-allbefore-" + tag, width=520, height=900, extra=["--scroll", "260,400,0,-560"]):
            failed += 1
        core.action("ShowAddDialog", show=False)
    except CheckFailed as failure:
        failed += 1
        print("  FAIL  " + str(failure))
        print(core.log_text()[-1500:])
    core.shutdown()
    print("done, %d failed" % failed)
    sys.stdout.flush()
    os._exit(1 if failed else 0)


if __name__ == "__main__":
    run(sys.argv[1] if len(sys.argv) > 1 else "en", (sys.argv[2] if len(sys.argv) > 2 else "dark") == "dark")
