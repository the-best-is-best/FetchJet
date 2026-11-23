import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER
from shots import OUT, shot

START = SERVER + "/site/start.html"


def run(dark=True):
    core = Core("shots-kindfilter")
    failed = 0
    name = "dark" if dark else "light"
    try:
        core.action("SystemTheme", dark=dark)
        core.pump(0.8)
        core.action("ShowAddDialog", show=True)
        core.action("SetUrl", text="")
        core.pump(0.3)
        core.action("SetUrl", text=START)
        core.pump(0.3)
        core.action("SetGrabDepth", depth=3)
        core.wait_for("the page card", lambda: core.snapshot("add").get("grabShown", False), 20)
        core.action("GrabPage")
        core.wait_for("rows", lambda: len(core.rows("addLinks")) > 0 and not core.snapshot("add").get("grabRunning", False), 30)
        core.pump(0.8)
        core.action("ToggleGrabGroup", folder="Images", selected=False)
        core.pump(0.5)
        failed += 0 if shot(core, "kind-" + name + "-narrow", width=520, height=900) else 1
        failed += 0 if shot(core, "kind-" + name + "-list-narrow", width=520, height=900, extra=["--scroll", "260,500,0,-600"]) else 1
    except CheckFailed as failure:
        failed += 1
        print("  FAIL  " + str(failure))
    core.shutdown()
    print("shots in " + OUT)
    sys.stdout.flush()
    os._exit(1 if failed else 0)


if __name__ == "__main__":
    run(not any(a in ("light", "--light") for a in sys.argv[1:]))
