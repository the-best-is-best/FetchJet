import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed
from shots import shot, OUT
from test_compress import install_ffmpeg, make_tone, serve_folder, page, details, select, estimate_ready, compress


def run(only=None):
    core = Core("shots-compress", before_start=install_ffmpeg)
    server = None
    failed = 0

    def take(name, width=1100, height=820, **kwargs):
        nonlocal failed
        if only and not any(part in name for part in only):
            return
        core.pump(0.5)
        if not shot(core, name, width=width, height=height, **kwargs):
            failed += 1

    try:
        core.action("SystemTheme", dark=True)
        core.pump(0.6)
        folder = make_tone(core)
        server = serve_folder(folder)
        core.debug("addDirect", {"url": "http://127.0.0.1:%d/tone.wav" % server.server_address[1], "fileName": "tone.wav"})
        sound = core.wait_task("COMPLETED", timeout=60, file_name="tone.wav")
        core.pump(0.6)
        select(core, sound["id"])
        core.action("DetailsCompress")
        core.wait_for("the page to open", lambda: page(core).get("open"), 10)
        core.wait_for("the estimate", lambda: estimate_ready(core), 60)
        core.action("AudioCompressSetLevel", level=3)
        core.pump(0.4)
        take("compress-simple-dark")
        core.action("AudioCompressSetMode", simple=False)
        core.action("AudioCompressSetBitrate", value=32)
        core.action("AudioCompressSetSampleRate", value=22050)
        core.action("AudioCompressSetChannels", value=1)
        core.pump(0.4)
        take("compress-advanced-dark")
        compress(core, 1)
        compress(core, 2)
        core.wait_for("two copies", lambda: len(page(core).get("copies", [])) == 2, 20)
        take("compress-copies-dark")
        take("compress-copies-small", width=800, height=560)
        core.action("ToggleTheme")
        core.pump(0.6)
        take("compress-copies-light")
        core.action("SettingPick", id="language", index=2)
        core.pump(1.0)
        take("compress-copies-light-ar")
        core.action("SettingPick", id="language", index=1)
        core.action("ToggleTheme")
        core.pump(1.0)
        core.action("AudioCompressDismiss")
        core.wait_for("the page to close", lambda: not page(core).get("open", False), 10)
        core.wait_for("the button to count two", lambda: details(core).get("compressLabel", "").endswith("(2)"), 10)
        take("details-button-dark")
        core.action("ToggleTheme")
        core.pump(0.6)
        take("details-button-light")
        core.action("ToggleTheme")
    except CheckFailed as failure:
        print("FAILED: " + str(failure))
        print(core.log_text()[-2000:])
        failed += 1
    if server:
        server.shutdown()
    core.shutdown()
    print("shots in " + OUT + (", %d failed" % failed if failed else ""))
    sys.stdout.flush()
    os._exit(1 if failed else 0)


if __name__ == "__main__":
    run(sys.argv[1:])
