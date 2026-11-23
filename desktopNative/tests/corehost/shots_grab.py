import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER
from shots import OUT, shot

START = SERVER + "/site/start.html"


def reset_url(core, url):
    core.action("SetUrl", text="")
    core.pump(0.3)
    core.action("SetUrl", text=url)
    core.pump(0.3)


def run(language="en", dark=True):
    core = Core("shots-grab")
    failed = 0
    tag = ("dark" if dark else "light") + ("" if language == "en" else "-" + language)
    try:
        core.action("SystemTheme", dark=dark)
        if language != "en":
            core.action("SetLanguage", choice=language)
        core.pump(0.5)
        core.action("ShowAddDialog", show=True)
        reset_url(core, START)
        core.pump(0.4)
        if not shot(core, "grab-card-" + tag, width=1100, height=900):
            failed += 1

        reset_url(core, START + "?delay=15000")
        core.action("GrabPage")
        core.pump(0.8)
        if not shot(core, "grab-running-" + tag, width=1100, height=900):
            failed += 1
        core.action("GrabCancel")
        core.pump(0.3)

        reset_url(core, SERVER + "/site/empty.html")
        core.action("GrabPage")
        core.wait_for("the empty page to be read", lambda: core.snapshot("add").get("grabNote", "") != "", 30)
        core.pump(0.3)
        if not shot(core, "grab-nothing-" + tag, width=1100, height=900):
            failed += 1

        reset_url(core, START)
        core.action("SetGrabDepth", depth=3)
        core.action("GrabPage")
        core.wait_for("the page to be read", lambda: len(core.rows("addLinks")) > 0, 30)
        core.pump(0.5)
        core.action("ToggleGrabGroup", folder="Images", selected=False)
        core.pump(0.5)
        if not shot(core, "grab-results-" + tag, width=1100, height=900):
            failed += 1
        if not shot(core, "grab-list-" + tag, width=1100, height=900, extra=["--scroll", "550,400,0,-640"]):
            failed += 1
        if not shot(core, "grab-results-narrow-" + tag, width=560, height=900):
            failed += 1
        reset_url(core, SERVER + "/site/big.html")
        core.action("GrabPage")
        core.wait_for("3000 files", lambda: len(core.rows("addLinks")) >= 3000, 60)
        core.pump(0.8)
        if not shot(core, "grab-results-3000-" + tag, width=1100, height=900):
            failed += 1
    except CheckFailed as failure:
        failed += 1
        print("  FAIL  " + str(failure))
    core.shutdown()
    print("shots in " + OUT)
    sys.stdout.flush()
    os._exit(1 if failed else 0)


if __name__ == "__main__":
    run(sys.argv[1] if len(sys.argv) > 1 else "en", (sys.argv[2] if len(sys.argv) > 2 else "dark") == "dark")
