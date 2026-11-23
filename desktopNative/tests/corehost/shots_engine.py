import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER
from shots import OUT, shot

CASES = (
    ("page", SERVER + "/site/empty.html", True),
    ("video", SERVER + "/site/video.html", True),
    ("failed", SERVER + "/site/nothing.html", True),
    ("file", SERVER + "/file/1MB.zip?key=shot-file", False),
    ("magnet", "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=sample", False),
)


def run(dark):
    core = Core("shots-engine")
    failed = 0
    name = "dark" if dark else "light"
    try:
        core.action("SystemTheme", dark=dark)
        core.pump(0.8)
        core.action("ShowAddDialog", show=True)
        for label, url, probed in CASES:
            core.action("SetUrl", text="")
            core.pump(0.3)
            core.action("SetUrl", text=url)
            core.pump(0.2)
            if probed:
                core.wait_for("the link check", lambda: not core.snapshot("add").get("checkingLink", False), 15)
            core.pump(0.6)
            failed += 0 if shot(core, "engine-" + label + "-" + name, width=520, height=900) else 1
    except CheckFailed as failure:
        failed += 1
        print("  FAIL  " + str(failure))
    core.shutdown()
    print("shots in " + OUT)
    sys.stdout.flush()
    os._exit(1 if failed else 0)


if __name__ == "__main__":
    run("--light" not in sys.argv)
