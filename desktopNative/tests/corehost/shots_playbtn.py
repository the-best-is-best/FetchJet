import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, SERVER, install_fake_ytdlp
from shots import shot


def run():
    core = Core("shots-play", before_start=install_fake_ytdlp)
    try:
        core.action("SystemTheme", dark=True)
        for name in ("clip.mp4", "song.mp3", "alpha.zip"):
            core.debug("addDirect", {"url": SERVER + "/file/1MB.bin?key=pb-" + name, "fileName": name})
            core.wait_task("COMPLETED", timeout=40, file_name=name)
        core.pump(0.8)
        shot(core, "play-buttons", width=1100, height=520)
    finally:
        core.finish()


if __name__ == "__main__":
    run()
