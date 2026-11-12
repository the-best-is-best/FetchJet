import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed
from shots import shot
from test_compress import FFMPEG_NAME, install_ffmpeg, serve_folder, page, compress, estimate_ready


def make_mp3(core):
    folder = os.path.join(core.work, "sound")
    os.makedirs(folder, exist_ok=True)
    subprocess.run([os.path.join(core.home, "tools", FFMPEG_NAME), "-y", "-v", "error", "-f", "lavfi", "-i",
                    "sine=frequency=440:duration=60", "-ac", "2", "-ar", "44100", "-c:a", "libmp3lame", "-b:a", "192k",
                    os.path.join(folder, "song.mp3")], check=True, stdin=subprocess.DEVNULL, timeout=120)
    return folder


def rows(core):
    return [row for row in core.rows("tasks") if row.get("name") == "song.mp3"]


def run():
    core = Core("compress-format", before_start=install_ffmpeg)
    server = None
    try:
        core.pump(0.6)
        folder = make_mp3(core)
        server = serve_folder(folder)
        core.debug("addDirect", {"url": "http://127.0.0.1:%d/song.mp3" % server.server_address[1], "fileName": "song.mp3"})
        sound = core.wait_task("COMPLETED", timeout=60, file_name="song.mp3")
        core.pump(0.8)
        where = sound["destinationPath"]

        print("the row button")
        found = rows(core)
        core.check("the finished sound row offers compress", bool(found) and found[0].get("canCompress") is True, found)
        shot(core, "row-compress-icon", width=1200, height=520)
        core.action("RowCompress", id=sound["id"])
        core.wait_for("the page to open", lambda: page(core).get("open"), 10)
        core.wait_for("the estimate", lambda: estimate_ready(core), 60)
        p = page(core)
        options = p.get("formatOptions", [])
        core.check("three formats on the desktop", options == ["m4a", "mp3", "opus"], options)
        core.check("the file's own format is chosen", options and options[p.get("formatPick", 0) - 1] == "mp3", p.get("formatPick"))

        print("same format")
        core.action("AudioCompressSetMode", simple=False)
        core.pump(0.3)
        core.action("AudioCompressSetBitrate", value=64)
        core.pump(0.4)
        shot(core, "compress-format-dark", width=1100, height=820)
        compress(core, 1)
        same = os.path.join(where, "song (64k).mp3")
        core.check("an mp3 gives a smaller mp3", os.path.isfile(same) and 0 < os.path.getsize(same) < os.path.getsize(os.path.join(where, "song.mp3")) // 2,
                   [c.get("name") for c in page(core).get("copies", [])])

        print("another format")
        core.action("AudioCompressSetFormat", index=2)
        core.pump(0.4)
        p = page(core)
        core.check("opus is chosen and the estimate stays", p.get("formatOptions", [])[p.get("formatPick", 0) - 1] == "opus" and
                   p.get("newSize", "").startswith("≈"), (p.get("formatPick"), p.get("newSize")))
        compress(core, 2)
        other = os.path.join(where, "song (64k).opus")
        core.check("the opus copy is made", os.path.isfile(other) and os.path.getsize(other) > 0, [c.get("name") for c in page(core).get("copies", [])])
        core.action("AudioCompressSetFormat", index=0)
        core.pump(0.4)
        compress(core, 3)
        core.check("the m4a copy is made", os.path.isfile(os.path.join(where, "song (64k).m4a")), [c.get("name") for c in page(core).get("copies", [])])
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    if server:
        server.shutdown()
    core.finish()


if __name__ == "__main__":
    run()
