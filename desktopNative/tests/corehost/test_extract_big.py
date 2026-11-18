import hashlib
import os
import random
import subprocess
import sys
import time
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed

PORT = int(os.environ.get("FJ_EXTRACT_PORT", "18262"))
BASE = "http://127.0.0.1:%d" % PORT
MEGABYTES = int(os.environ.get("FJ_EXTRACT_MB", "300"))


def make_zip(path):
    rnd = random.Random(5)
    words = [("w%d" % rnd.randrange(5000)).encode() for _ in range(4000)]
    block = b" ".join(rnd.choice(words) for _ in range(150000))[: 1 << 20]
    digest = hashlib.sha256()
    total = 0
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=1) as z:
        with z.open("data/big.txt", "w", force_zip64=True) as f:
            for i in range(MEGABYTES):
                chunk = block[i % 7:] + block[: i % 7]
                f.write(chunk)
                digest.update(chunk)
                total += len(chunk)
        z.writestr("data/small.txt", b"hello")
    return digest.hexdigest(), total


def task_row(core, name):
    for r in core.rows("tasks"):
        if r.get("kind", 0) == 0 and r.get("name") == name:
            return r
    return {}


def run():
    core = Core("extractbig")
    site = os.path.join(core.work, "site")
    os.makedirs(site, exist_ok=True)
    expected, expected_size = make_zip(os.path.join(site, "big.zip"))
    size = os.path.getsize(os.path.join(site, "big.zip"))
    print("zip: %.0f MB for %d MB of text" % (size / 1048576, MEGABYTES))
    server = subprocess.Popen([sys.executable, "-m", "http.server", str(PORT), "--bind", "127.0.0.1", "--directory", site],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    try:
        core.pump(0.5)
        core.action("ShowAddDialog", show=True)
        core.action("SetUrl", text=BASE + "/big.zip")
        core.pump(0.6)
        core.action("StartDownload")
        done = core.wait_task(["COMPLETED", "ERROR"], timeout=120, file_name="big.zip")
        core.check("the big zip is downloaded", done["status"] == "COMPLETED", done)
        core.pump(1.0)
        row = task_row(core, "big.zip")
        core.check("the row offers Extract", row.get("canExtract", False) is True, row)

        started = time.time()
        core.action("RowExtract", id=done["id"])
        seen = []
        finish = os.path.join(core.downloads, "data", "small.txt")
        end = started + 600
        while time.time() < end:
            core.pump(0.15)
            r = task_row(core, "big.zip")
            if r.get("busy"):
                seen.append((r.get("percent"), r.get("stateLine", ""), r.get("canExtract", False), r.get("progress", 0.0)))
            if os.path.isfile(finish) and not r.get("busy"):
                break
        seconds = time.time() - started
        print("extract took %.1f s = %.0f MB/s of text; %d progress samples; first %s last %s" % (seconds, MEGABYTES / seconds, len(seen), seen[:1], seen[-1:]))
        core.check("the row showed the extraction while it ran", len(seen) >= 3, len(seen))
        core.check("the state line says it is extracting with a percent", all("%" in s[1] for s in seen) and any("xtract" in s[1] or "فك" in s[1] for s in seen), seen[:3])
        core.check("the Extract button is hidden meanwhile", all(s[2] is False for s in seen), seen[:3])
        values = [s[3] for s in seen]
        core.check("progress only goes up and ends high", all(b >= a - 0.001 for a, b in zip(values, values[1:])) and max(values) > 0.8, (values[:3], values[-3:]))
        core.check("after it the button is back", task_row(core, "big.zip").get("canExtract", False) is True, task_row(core, "big.zip"))
        out = os.path.join(core.downloads, "data", "big.txt")
        h = hashlib.sha256()
        with open(out, "rb") as f:
            while True:
                part = f.read(1 << 20)
                if not part:
                    break
                h.update(part)
        core.check("the big file has the exact size", os.path.getsize(out) == expected_size, (os.path.getsize(out), expected_size))
        core.check("the big file has the exact content", h.hexdigest() == expected, h.hexdigest())
        core.check("speed above 15 MB/s", MEGABYTES / seconds > 15, "%.1f MB/s" % (MEGABYTES / seconds))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
    finally:
        server.kill()
    core.finish()


if __name__ == "__main__":
    run()
