import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, SERVER, install_fake_ytdlp
from shots import shot, ARABIC_NAME


def run():
    core = Core("shots-rowmenu", before_start=install_fake_ytdlp)
    try:
        core.debug("addDirect", {"url": SERVER + "/file/1MB.bin?key=rm-zip", "fileName": "alpha.zip"})
        done = core.wait_task("COMPLETED", timeout=40, file_name="alpha.zip")
        core.debug("addDirect", {"url": SERVER + "/file/1MB.bin?key=rm-mp3", "fileName": "song.mp3"})
        core.wait_task("COMPLETED", timeout=40, file_name="song.mp3")
        for dark, theme in ((True, "dark"), (False, "light")):
            core.action("SystemTheme", dark=dark)
            core.action("RowMenu", id=done["id"])
            core.pump(0.8)
            rows = [r for r in core.rows("tasks") if r.get("id")]
            index = next(i for i, r in enumerate(rows) if r.get("id") == done["id"])
            y = 140 + index * 74
            shot(core, "rowmenu-" + theme, width=1100, height=640, extra=["--right-click", "640,%d" % y])
        core.action("SetLanguage", code="ar") if False else None
    finally:
        core.finish()


if __name__ == "__main__":
    run()
