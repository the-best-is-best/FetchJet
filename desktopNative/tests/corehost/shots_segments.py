import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, SERVER, install_fake_ytdlp
from shots import OUT, shot

START = SERVER + "/site/start.html"
VIDEO = "https://www.youtube.com/watch?v=fakevideo01"
STATE = {name: index for index, name in enumerate(ENUMS["MediaStateCode"])}


def reset_url(core, url):
    core.action("SetUrl", text="")
    core.pump(0.3)
    core.action("SetUrl", text=url)
    core.pump(0.3)


def run():
    core = Core("shots-segments", before_start=install_fake_ytdlp)
    failed = 0
    try:
        core.action("SystemTheme", dark=True)
        core.pump(1.0)
        core.action("ShowAddDialog", show=True)
        reset_url(core, START)
        core.pump(0.4)
        for name, w in (("grab-card-narrow", 520), ("grab-card-wide", 1100), ("grab-card-tiny", 420)):
            failed += 0 if shot(core, "seg-" + name, width=w, height=900) else 1
        core.action("SetGrabDepth", depth=3)
        core.pump(0.3)
        failed += 0 if shot(core, "seg-grab-card-depth3-narrow", width=520, height=900) else 1
        core.action("SetGrabDepth", depth=1)
        core.action("GrabPage")
        core.wait_for("rows", lambda: len(core.rows("addLinks")) > 0, 30)
        core.pump(0.5)
        core.action("StartDownload")
        core.wait_task(["COMPLETED", "ERROR"], timeout=60, file_name="1MB.bin")
        core.pump(0.5)
        core.action("ShowAddDialog", show=True)
        reset_url(core, START)
        core.action("GrabPage")
        core.wait_for("rows", lambda: len(core.rows("addLinks")) > 0, 30)
        core.pump(1.5)
        failed += 0 if shot(core, "seg-locked-row", width=900, height=900) else 1
        failed += 0 if shot(core, "seg-locked-row-narrow", width=520, height=900) else 1
        core.action("ShowAddDialog", show=False)
        core.pump(0.5)
        core.action("ShowAddDialog", show=True)
        reset_url(core, VIDEO)
        core.wait_for("qualities", lambda: (core.snapshot("addMedia") or {}).get("state", 0) == STATE["QUALITIES"], 60)
        core.action("ToggleSubtitle", code="en", selected=True)
        core.pump(0.5)
        core.action("SetEmbedSubtitles", embed=True)
        core.pump(0.4)
        failed += 0 if shot(core, "seg-subtitles-step1", width=900, height=1000, extra=["--scroll", "250,365,0,-300", "--wait", "500"] * 8) else 1
        core.action("SetEmbedSubtitles", embed=False)
        core.action("SetSubtitleFormat", format="vtt")
        core.pump(0.4)
        failed += 0 if shot(core, "seg-subtitles-step2", width=900, height=1000, extra=["--scroll", "250,365,0,-300", "--wait", "500"] * 8) else 1
    except CheckFailed as failure:
        failed += 1
        print("  FAIL  " + str(failure))
    core.shutdown()
    print("shots in " + OUT)
    sys.stdout.flush()
    os._exit(1 if failed else 0)


if __name__ == "__main__":
    run()
