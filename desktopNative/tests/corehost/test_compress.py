import http.server
import os
import re
import shutil
import subprocess
import sys
import threading

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ON_LINUX, SERVER

FFMPEG_NAME = "ffmpeg" if ON_LINUX else "ffmpeg.exe"
FFMPEG_SOURCE = os.environ.get("FJ_TEST_FFMPEG", os.path.join(os.path.expanduser("~"), ".fetch_jet", "tools", FFMPEG_NAME))
UNITS = {"B": 1, "KB": 1024, "MB": 1024 ** 2, "GB": 1024 ** 3, "TB": 1024 ** 4}


class SoundHandler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    folder = ""

    def log_message(self, *args):
        pass

    def do_HEAD(self):
        self.answer(False)

    def do_GET(self):
        self.answer(True)

    def answer(self, with_body):
        name = os.path.basename(self.path.split("?")[0])
        path = os.path.join(self.folder, name)
        if not name or not os.path.isfile(path):
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        size = os.path.getsize(path)
        first, last, status = 0, size - 1, 200
        found = re.match(r"bytes=(\d*)-(\d*)$", self.headers.get("Range", "").strip())
        if found and (found.group(1) or found.group(2)):
            if found.group(1):
                first = int(found.group(1))
                if found.group(2):
                    last = min(int(found.group(2)), size - 1)
            else:
                first = max(0, size - int(found.group(2)))
            if first > last:
                self.send_response(416)
                self.send_header("Content-Range", "bytes */%d" % size)
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            status = 206
        self.send_response(status)
        self.send_header("Content-Type", "audio/wav")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(last - first + 1))
        if status == 206:
            self.send_header("Content-Range", "bytes %d-%d/%d" % (first, last, size))
        self.end_headers()
        if not with_body:
            return
        left = last - first + 1
        try:
            with open(path, "rb") as handle:
                handle.seek(first)
                while left > 0:
                    block = handle.read(min(left, 256 * 1024))
                    if not block:
                        break
                    self.wfile.write(block)
                    left -= len(block)
        except OSError:
            pass


class SoundServer(http.server.ThreadingHTTPServer):
    def handle_error(self, request, client_address):
        pass


def serve_folder(folder):
    SoundHandler.folder = folder
    server = SoundServer(("127.0.0.1", 0), SoundHandler)
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def install_ffmpeg(core):
    if not os.path.isfile(FFMPEG_SOURCE):
        raise SystemExit("ffmpeg not found: " + FFMPEG_SOURCE + " (set FJ_TEST_FFMPEG)")
    tools = os.path.join(core.home, "tools")
    os.makedirs(tools, exist_ok=True)
    shutil.copy2(FFMPEG_SOURCE, os.path.join(tools, FFMPEG_NAME))


def make_tone(core):
    folder = os.path.join(core.work, "sound")
    os.makedirs(folder, exist_ok=True)
    target = os.path.join(folder, "tone.wav")
    subprocess.run([os.path.join(core.home, "tools", FFMPEG_NAME), "-y", "-v", "error", "-f", "lavfi", "-i",
                    "sine=frequency=440:duration=60", "-ac", "2", "-ar", "44100", target],
                   check=True, stdin=subprocess.DEVNULL, timeout=120)
    return folder


def size_of(text):
    found = re.search(r"([0-9]+(?:\.[0-9]+)?)\s*(TB|GB|MB|KB|B)", text or "")
    return int(float(found.group(1)) * UNITS[found.group(2)]) if found else 0


def page(core):
    return core.snapshot("audioCompress") or {}


def details(core):
    return core.snapshot("details") or {}


def picked(choices):
    return [c.get("value", 0) for c in (choices or []) if c.get("selected")]


def select(core, task_id):
    core.action("RowClick", id=task_id, control=False, shift=False)
    core.pump(0.5)


def estimate_ready(core):
    return page(core).get("newSize", "").startswith("≈")


def compress(core, copies_wanted):
    core.action("AudioCompressStart")
    seen_running = False
    try:
        core.wait_for("the compress to start", lambda: page(core).get("running") or len(page(core).get("copies", [])) >= copies_wanted, 10, 0.02)
        seen_running = True
    except CheckFailed:
        pass
    core.wait_for("the compress to end", lambda: not page(core).get("running", False), 180)
    core.pump(0.5)
    return seen_running


def run():
    core = Core("compress", before_start=install_ffmpeg)
    server = None
    try:
        core.pump(0.6)
        strings = core.strings()
        title = strings["audioCompressTitle"]
        folder = make_tone(core)
        source_size = os.path.getsize(os.path.join(folder, "tone.wav"))
        server = serve_folder(folder)
        sound_url = "http://127.0.0.1:%d/tone.wav" % server.server_address[1]

        print("a finished sound and a finished file")
        core.check("the test sound is about 10 MB", 9 * 1024 * 1024 < source_size < 12 * 1024 * 1024, source_size)
        core.debug("addDirect", {"url": sound_url, "fileName": "tone.wav"})
        sound = core.wait_task("COMPLETED", timeout=60, file_name="tone.wav")
        core.debug("addDirect", {"url": SERVER + "/file/1MB.bin?key=compress-a", "fileName": "plain.bin"})
        plain = core.wait_task("COMPLETED", timeout=40, file_name="plain.bin")
        core.pump(0.6)
        original = os.path.join(sound["destinationPath"], sound["fileName"])
        core.check("the sound was downloaded whole", os.path.isfile(original) and os.path.getsize(original) == source_size,
                   (original, sound))

        print("the details button")
        select(core, plain["id"])
        d = details(core)
        core.check("a file that is not a sound cannot be compressed", not d.get("canCompress", False), d.get("canCompress"))
        select(core, sound["id"])
        d = details(core)
        core.check("a finished sound can be compressed", d.get("canCompress") is True, d)
        core.check("the button shows the plain title", d.get("compressLabel") == title, d.get("compressLabel"))
        core.check("the page is closed at start", not page(core).get("open", False), page(core))

        print("open the page")
        core.action("DetailsCompress")
        core.wait_for("the page to open", lambda: page(core).get("open"), 10)
        p = page(core)
        core.check("the page shows the file and its size", p.get("fileName") == "tone.wav" and p.get("originalSize", "") != "" and
                   abs(size_of(p.get("originalSize")) - source_size) < source_size * 0.02, p)
        core.check("no copies yet", len(p.get("copies", [])) == 0, p.get("copies"))
        core.wait_for("the estimate", lambda: estimate_ready(core), 60)
        p = page(core)
        core.check("the new size is an estimate, start is possible", p.get("newSize", "").startswith("≈") and size_of(p.get("newSize")) > 0 and
                   p.get("canStart") and not p.get("running", False) and not p.get("failed", False), p)
        core.check("simple mode with six levels", p.get("simple") is True and [c.get("value") for c in p.get("levels", [])] == [1, 2, 3, 4, 5, 6],
                   (p.get("simple"), p.get("levels")))

        print("simple mode")
        core.action("AudioCompressSetLevel", level=1)
        core.pump(0.4)
        first = page(core)
        core.check("level 1 is picked", picked(first.get("levels")) == [1], first.get("levels"))
        core.action("AudioCompressSetLevel", level=6)
        core.pump(0.4)
        sixth = page(core)
        core.check("level 6 is picked", picked(sixth.get("levels")) == [6], sixth.get("levels"))
        core.check("the new size follows the level", first.get("newSize") != sixth.get("newSize") and
                   size_of(sixth.get("newSize")) < size_of(first.get("newSize")), (first.get("newSize"), sixth.get("newSize")))

        print("advanced mode")
        core.action("AudioCompressSetMode", simple=False)
        core.pump(0.4)
        before = page(core)
        core.check("advanced mode", before.get("simple", False) is False and len(before.get("bitratesTop", [])) == 5 and
                   len(before.get("bitratesBottom", [])) == 5, before)
        core.action("AudioCompressSetBitrate", value=256)
        core.pump(0.4)
        high = page(core)
        core.action("AudioCompressSetBitrate", value=32)
        core.pump(0.4)
        low = page(core)
        bitrates = low.get("bitratesTop", []) + low.get("bitratesBottom", [])
        core.check("32k is picked", picked(bitrates) == [32] and
                   [c.get("label") for c in bitrates if c.get("selected")] == ["32k"], bitrates)
        core.check("the new size follows the bit rate", high.get("newSize") != low.get("newSize") and
                   size_of(low.get("newSize")) < size_of(high.get("newSize")), (high.get("newSize"), low.get("newSize")))
        core.action("AudioCompressSetSampleRate", value=22050)
        core.action("AudioCompressSetChannels", value=1)
        core.pump(0.4)
        p = page(core)
        core.check("22050 Hz is picked", picked(p.get("ratesTop", []) + p.get("ratesBottom", [])) == [22050],
                   p.get("ratesTop", []) + p.get("ratesBottom", []))
        core.check("mono is picked", picked(p.get("channels")) == [1] and
                   [c.get("label") for c in p.get("channels", []) if c.get("selected")] == [strings["audioCompressMono"]], p.get("channels"))
        estimate = size_of(p.get("newSize"))
        core.check("the estimate for one minute at 32k is near 240 KB", 200 * 1024 < estimate < 300 * 1024, p.get("newSize"))

        print("compress")
        seen_running = compress(core, 1)
        p = page(core)
        core.check("it ran and ended", seen_running and not p.get("running", False), p)
        core.check("it did not fail", not p.get("failed", False), p)
        copies = p.get("copies", [])
        core.check("one copy is listed", len(copies) == 1, copies)
        first_copy = copies[0] if copies else {}
        first_path = first_copy.get("path", "")
        core.check("the copy is named after the bit rate", first_copy.get("name", "").endswith("(32k).m4a") and
                   first_copy.get("name") == "tone (32k).m4a", first_copy)
        core.check("the copy is on disk next to the original", os.path.isfile(first_path) and
                   os.path.normcase(os.path.dirname(os.path.normpath(first_path))) == os.path.normcase(os.path.normpath(sound["destinationPath"])),
                   first_path)
        real = os.path.getsize(first_path) if os.path.isfile(first_path) else 0
        core.check("the real size is within 40 % of the estimate", estimate > 0 and abs(real - estimate) <= estimate * 0.4, (real, estimate))
        core.check("the copy is much smaller than the original", 0 < real < source_size / 10, (real, source_size))
        core.check("the listed size is the real one", abs(size_of(first_copy.get("size")) - real) <= max(1024, real * 0.02), first_copy)
        core.check("the original is kept", os.path.isfile(original) and os.path.getsize(original) == source_size)
        core.check("no unfinished file is left", not [n for n in os.listdir(sound["destinationPath"]) if ".part." in n],
                   os.listdir(sound["destinationPath"]))
        core.pump(0.5)
        d = details(core)
        core.check("the button counts the copy", d.get("compressLabel", "").endswith("(1)") and d.get("compressLabel", "").startswith(title),
                   d.get("compressLabel"))

        print("a second copy")
        compress(core, 2)
        p = page(core)
        copies = p.get("copies", [])
        names = [c.get("name") for c in copies]
        core.check("two copies, the second under a free name", len(copies) == 2 and not p.get("failed", False) and
                   "tone (32k).m4a" in names and "tone (32k) (1).m4a" in names, names)
        second_path = next((c.get("path", "") for c in copies if c.get("name") == "tone (32k) (1).m4a"), "")
        core.check("both copies are on disk", os.path.isfile(first_path) and os.path.isfile(second_path), (first_path, second_path))
        core.pump(0.5)
        core.check("the button counts two", details(core).get("compressLabel", "").endswith("(2)"), details(core).get("compressLabel"))

        print("delete a copy")
        core.action("AudioCompressDelete", path=first_path)
        core.pump(0.6)
        p = page(core)
        core.check("the file is gone", not os.path.exists(first_path), first_path)
        core.check("one copy is left: the second", [c.get("name") for c in p.get("copies", [])] == ["tone (32k) (1).m4a"] and
                   os.path.isfile(second_path), p.get("copies"))
        core.check("the original is still there", os.path.isfile(original))
        core.check("the button counts one again", details(core).get("compressLabel", "").endswith("(1)"), details(core).get("compressLabel"))

        print("close")
        core.action("AudioCompressDismiss")
        core.pump(0.4)
        p = page(core)
        core.check("the page closes", not p.get("open", False) and len(p.get("copies", [])) == 0, p)
        core.check("the task is still finished", (core.task(task_id=sound["id"]) or {}).get("status") == "COMPLETED")
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    if server:
        server.shutdown()
    core.finish()


if __name__ == "__main__":
    run()
