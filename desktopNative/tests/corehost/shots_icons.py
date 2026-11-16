import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, SERVER, install_fake_ytdlp
from shots import shot


def run():
    core = Core("shots-icons", before_start=install_fake_ytdlp)
    try:
        core.action("SystemTheme", dark=True)
        for name in ("clip.mp4", "song.mp3", "alpha.zip", "notes.pdf"):
            core.debug("addDirect", {"url": SERVER + "/file/1MB.bin?key=ic-" + name, "fileName": name})
            core.wait_task("COMPLETED", timeout=40, file_name=name)
        core.debug("addDirect", {"url": SERVER + "/file/60MB.bin?key=ic-run&throttle=300", "fileName": "running.mkv"})
        core.wait_task("DOWNLOADING", timeout=40, file_name="running.mkv")
        core.debug("addDirect", {"url": SERVER + "/file/60MB.bin?key=ic-pause&throttle=300", "fileName": "paused.iso"})
        paused = core.wait_task("DOWNLOADING", timeout=40, file_name="paused.iso")
        core.pump(1.5)
        core.action("Pause", id=paused["id"])
        core.wait_task("PAUSED", timeout=20, task_id=paused["id"])
        core.pump(0.8)
        shot(core, "icons-dark", width=1100, height=640)
        core.action("SystemTheme", dark=False)
        core.pump(0.8)
        shot(core, "icons-light", width=1100, height=640)
    finally:
        core.finish()


if __name__ == "__main__":
    run()
