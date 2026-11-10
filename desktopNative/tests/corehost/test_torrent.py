import os
import subprocess
import sys
import threading
import time

TORRENT_DLL = os.environ.get("FETCHJET_TORRENT_DLL", "E:/tools/probes/fjtorrent/build/fjtorrent.dll")
os.environ["FETCHJET_TORRENT_DLL"] = TORRENT_DLL
os.environ["FETCHJET_TORRENT_LOCAL_ONLY"] = "1"

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, sha256_of_file

ENGINE = {name: index for index, name in enumerate(ENUMS["EngineCode"])}
TSTATE = {name: index for index, name in enumerate(ENUMS["TorrentStateCode"])}
SEED_EXE = os.environ.get("FJ_TORRENT_SEED", os.path.join(os.path.dirname(TORRENT_DLL), "fjtseed.exe" if os.name == "nt" else "fjtseed"))


class Seeder:
    def __init__(self, *args):
        env = dict(os.environ)
        env["FETCHJET_TORRENT_LOCAL_ONLY"] = "1"
        self.lines = []
        self.process = subprocess.Popen([SEED_EXE] + list(args), stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                        stderr=subprocess.STDOUT, env=env, creationflags=0x08000000 if os.name == "nt" else 0)
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self):
        for raw in self.process.stdout:
            self.lines.append(raw.decode("utf-8", "replace").rstrip("\r\n"))

    def ready(self, timeout=60.0):
        end = time.time() + timeout
        while time.time() < end:
            for line in list(self.lines):
                if line.startswith("ERROR"):
                    return None
                if line.startswith("READY "):
                    return dict(part.split("=", 1) for part in line[6:].split(" ") if "=" in part)
            time.sleep(0.1)
        return None

    def stop(self):
        try:
            self.process.kill()
            self.process.wait(5)
        except Exception:
            pass


def find_file(root, relative):
    tail = relative.replace("/", os.sep)
    for folder, _, names in os.walk(root):
        for name in names:
            full = os.path.join(folder, name)
            if full.endswith(os.sep + tail):
                return full
    return None


def same_files(seed_root, download_root, names):
    bad = []
    for name in names:
        got = find_file(download_root, name)
        want = os.path.join(seed_root, name.replace("/", os.sep))
        if not got or sha256_of_file(got) != sha256_of_file(want):
            bad.append(name)
    return bad


def wait_files(core, timeout):
    return core.wait_for("torrent file list", lambda: (core.snapshot("addTorrent") or {}).get("state", 0) in (TSTATE["FILES"], TSTATE["ERROR"]),
                         timeout)


def run():
    if not os.path.isfile(TORRENT_DLL) or not os.path.isfile(SEED_EXE):
        raise SystemExit("build fjtorrent.dll and fjtseed.exe first (desktopNative/torrent/build.cmd): " + TORRENT_DLL)
    core = Core("torrent")
    seeders = []
    try:
        core.pump(0.5)
        strings = core.strings()
        seed_dir = core.work + "/seed"
        one = Seeder("dir=" + seed_dir, "out=" + seed_dir + "/pack.torrent", "name=pack",
                     "files=a.bin:1500000,sub/b.bin:3000000,c.txt:70000", "piece=65536")
        seeders.append(one)
        two = Seeder("dir=" + seed_dir, "out=" + seed_dir + "/mag.torrent", "name=mag",
                     "files=m1.bin:2000000,m2.bin:600000", "piece=65536")
        seeders.append(two)
        ready_one = one.ready()
        ready_two = two.ready()
        core.check("two local seeders are ready on 127.0.0.1", ready_one is not None and ready_two is not None, (one.lines[-3:], two.lines[-3:]))
        if not ready_one or not ready_two:
            raise CheckFailed("no seeder")

        print("engine shown")
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)
        add = core.snapshot("add")
        engines = [e.get("engine", 0) for e in add.get("engines", [])]
        core.check("the Add dialog offers the torrent engine", ENGINE["BITTORRENT"] in engines, engines)
        core.check("Open .torrent follows the host's file picker (none when headless)",
                   add.get("canOpenTorrent", False) == add.get("canImportTxt", False), (add.get("canOpenTorrent"), add.get("canImportTxt")))
        core.action("ShowAddDialog", show=False)
        core.action("OpenSettings")
        core.pump(0.6)
        titles = [s.get("title") for s in (core.snapshot("settingsNav") or {}).get("sections", [])]
        core.check("the BitTorrent settings page is listed", strings["sectionTorrent"] in titles, titles)

        print(".torrent file")
        torrent_path = os.path.normpath(ready_one["torrent"])
        core.action("SetUrl", text=torrent_path)
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("a .torrent path selects the torrent engine", add.get("engine", -1) == ENGINE["BITTORRENT"], add.get("engine"))
        wait_files(core, 30)
        info = core.snapshot("addTorrent") or {}
        rows = core.rows("addTorrentFiles")
        paths = [r.get("path") for r in rows]
        core.check("the file list is read from the .torrent", info.get("state") == TSTATE["FILES"] and info.get("name") == "pack" and
                   sorted(paths) == ["pack/a.bin", "pack/c.txt", "pack/sub/b.bin"], (info, paths))
        c_index = next((r.get("index") for r in rows if r.get("path", "").endswith("c.txt")), -1)
        core.action("ToggleTorrentFile", index=c_index, selected=False)
        core.pump(0.3)
        rows = core.rows("addTorrentFiles")
        core.check("c.txt is left out", any(r.get("index") == c_index and not r.get("selected") for r in rows), rows)
        add = core.snapshot("add")
        core.check("start is enabled", add.get("primaryEnabled", False), add.get("primaryLabel"))
        started = time.time()
        core.action("StartDownload")
        task = core.wait_task("COMPLETED", timeout=90, url_part="pack.torrent")
        seconds = time.time() - started
        core.check("the task uses the torrent engine", task.get("engine") == "BITTORRENT", task.get("engine"))
        bad = same_files(seed_dir + "/pack", core.downloads, ["a.bin", "sub/b.bin"])
        core.check("a.bin and sub/b.bin match the seeder's bytes", not bad, bad)
        c_file = find_file(core.downloads, "pack/c.txt")
        core.check("c.txt was not downloaded in full", not c_file or os.path.getsize(c_file) < 70000 or
                   sha256_of_file(c_file) != sha256_of_file(os.path.join(seed_dir, "pack", "c.txt")), c_file)
        print("    .torrent done in %.1f s" % seconds)

        print("magnet")
        magnet = "magnet:?xt=urn:btih:%s&dn=mag&tr=http%%3A%%2F%%2F127.0.0.1%%3A%s%%2Fannounce" % (ready_two["hash"], ready_two["tracker"])
        core.action("SetUrl", text=magnet)
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("a magnet selects the torrent engine", add.get("engine", -1) == ENGINE["BITTORRENT"], add.get("engine"))
        started = time.time()
        wait_files(core, 70)
        info = core.snapshot("addTorrent") or {}
        paths = sorted(r.get("path") for r in core.rows("addTorrentFiles"))
        core.check("the magnet's metadata comes from the local seeder", info.get("state") == TSTATE["FILES"] and
                   paths == ["mag/m1.bin", "mag/m2.bin"], (info, paths))
        print("    metadata in %.1f s" % (time.time() - started))
        core.action("StartDownload")
        task = core.wait_task("COMPLETED", timeout=90, url_part=ready_two["hash"])
        bad = same_files(seed_dir + "/mag", core.downloads, ["m1.bin", "m2.bin"])
        core.check("the magnet download matches the seeder's bytes", not bad, bad)
        announced = [line for line in two.lines if line.startswith("ANNOUNCE")]
        core.check("the app announced to the magnet's tr= tracker", any(ready_two["hash"] in line for line in announced), announced[:3])

        print("shutdown")
        started = time.time()
        core.shutdown()
        core.check("fj_shutdown with a torrent session returns within 5 s", time.time() - started < 5.0, "%.1f s" % (time.time() - started))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    finally:
        for seeder in seeders:
            seeder.stop()
    core.finish()


if __name__ == "__main__":
    run()
