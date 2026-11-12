import os
import sys
import time

TORRENT_DLL = os.environ.get("FETCHJET_TORRENT_DLL", "E:/tools/probes/fjtorrent/build/fjtorrent.dll")
os.environ["FETCHJET_TORRENT_DLL"] = TORRENT_DLL
os.environ["FETCHJET_TORRENT_LOCAL_ONLY"] = "1"

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed
from test_torrent import Seeder, same_files, wait_files, TSTATE, SEED_EXE
from test_network import set_proxy, PROXY_OPEN_HTTP, PROXY_LOGIN_HTTP, PROXY_LOGIN_SOCKS, MAPPED

WORK = os.environ.get("FJ_TEST_WORK", "E:/tools/probes/winhelpers-work")
PROXY_OPEN_SOCKS = 11082


def proxy_lines(name):
    try:
        with open(WORK + "/servers/" + name + ".log", "r", encoding="utf-8", errors="replace") as handle:
            return handle.read().splitlines()
    except OSError:
        return []


def one_round(core, label, kind, port, log_name, user, password, index, files):
    print(label)
    set_proxy(core, kind, port, user, password)
    seed_dir = core.work + "/seed"
    name = "px%d" % index
    seeder = Seeder("dir=" + seed_dir, "out=" + seed_dir + "/" + name + ".torrent", "name=" + name,
                    "files=" + files, "piece=65536")
    try:
        ready = seeder.ready()
        core.check(label + ": the local seeder is ready", ready is not None, seeder.lines[-3:])
        if not ready:
            return
        before = len(proxy_lines(log_name))
        magnet = "magnet:?xt=urn:btih:%s&dn=%s&tr=http%%3A%%2F%%2F%s%%3A%s%%2Fannounce" % (ready["hash"], name, MAPPED, ready["tracker"])
        core.action("SetUrl", text=magnet)
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)
        started = time.time()
        wait_files(core, 80)
        info = core.snapshot("addTorrent") or {}
        paths = sorted(r.get("path") for r in core.rows("addTorrentFiles"))
        core.check(label + ": the magnet's metadata arrives (tracker name only the proxy resolves)",
                   info.get("state") == TSTATE["FILES"] and len(paths) == len(files.split(",")), (info, paths, seeder.lines[-4:]))
        print("    metadata in %.1f s" % (time.time() - started))
        announced = [line for line in seeder.lines if line.startswith("ANNOUNCE")]
        core.check(label + ": the seeder's tracker saw the announce", any(ready["hash"] in line for line in announced), announced[:3])
        if info.get("state") != TSTATE["FILES"]:
            return
        core.action("StartDownload")
        task = None
        try:
            task = core.wait_task("COMPLETED", timeout=90, url_part=ready["hash"])
        except CheckFailed as failure:
            core.check(label + ": the download completes", False, str(failure))
        if task:
            names = [f.split(":")[0] for f in files.split(",")]
            bad = same_files(seed_dir + "/" + name, core.downloads, names)
            core.check(label + ": the files match the seeder's bytes", not bad, bad)
        new_lines = proxy_lines(log_name)[before:]
        core.check(label + ": the proxy log shows traffic from the app (" + str(len(new_lines)) + " lines)", len(new_lines) > 0, new_lines[:5])
        tracker_via_proxy = [line for line in new_lines if ready["tracker"] in line]
        core.check(label + ": the tracker connection went through the proxy", len(tracker_via_proxy) > 0, new_lines[:6])
        peer_port = ready.get("port") or ready.get("listen")
        if peer_port:
            via = [line for line in new_lines if str(peer_port) in line]
            core.check(label + ": the peer connection went through the proxy", len(via) > 0, new_lines[:6])
    finally:
        seeder.stop()


def run():
    if not os.path.isfile(TORRENT_DLL) or not os.path.isfile(SEED_EXE):
        raise SystemExit("build fjtorrent.dll and fjtseed.exe first (desktopNative/torrent/build.cmd)")
    core = Core("torrentproxy")
    try:
        core.pump(0.5)
        core.action("OpenSettings")
        core.pump(0.6)
        one_round(core, "SOCKS5, no login", 2, PROXY_OPEN_SOCKS, "proxy-open", "", "", 1, "x1.bin:900000,x2.bin:300000")
        one_round(core, "HTTP proxy, no login", 1, PROXY_OPEN_HTTP, "proxy-open", "", "", 2, "y1.bin:900000,y2.bin:300000")
        one_round(core, "SOCKS5 with login", 2, PROXY_LOGIN_SOCKS, "proxy-login", "fj", "secret", 3, "z1.bin:900000,z2.bin:300000")
        one_round(core, "HTTP proxy with login", 1, PROXY_LOGIN_HTTP, "proxy-login", "fj", "secret", 4, "w1.bin:900000,w2.bin:300000")
        set_proxy(core, 0, 0)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
