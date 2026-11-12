import os
import sys
import time

TORRENT_DLL = os.environ.get("FETCHJET_TORRENT_DLL", "E:/tools/probes/fjtorrent/build/fjtorrent.dll")
os.environ["FETCHJET_TORRENT_DLL"] = TORRENT_DLL
os.environ["FETCHJET_TORRENT_LOCAL_ONLY"] = "1"

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed
from test_torrent import Seeder, same_files, TSTATE, SEED_EXE
from test_network import set_proxy, PROXY_OPEN_HTTP, PROXY_LOGIN_HTTP, PROXY_LOGIN_SOCKS

WORK = os.environ.get("FJ_TEST_WORK", "E:/tools/probes/winhelpers-work")
PROXY_OPEN_SOCKS = 11082
LOGS = ("proxy-open", "proxy-login")
NONE, HTTP, SOCKS = 0, 1, 2


def log_sizes():
    sizes = {}
    for name in LOGS:
        try:
            sizes[name] = os.path.getsize(WORK + "/servers/" + name + ".log")
        except OSError:
            sizes[name] = 0
    return sizes


def log_since(name, sizes):
    try:
        with open(WORK + "/servers/" + name + ".log", "rb") as handle:
            handle.seek(sizes.get(name, 0))
            return handle.read().decode("utf-8", "replace").splitlines()
    except OSError:
        return []


def with_port(lines, port):
    key = "127.0.0.1:%s" % port
    return [line for line in lines if key + " " in line + " " or key + "/" in line]


def state_of(core):
    return (core.snapshot("addTorrent") or {}).get("state", 0)


def wait_state(core, seconds):
    end = time.time() + seconds
    while time.time() < end:
        core.pump(0.2)
        if state_of(core) in (TSTATE["FILES"], TSTATE["ERROR"]):
            break
    return state_of(core)


def one_round(core, label, kind, port, log_name, user, password, index, works=True):
    print(label)
    set_proxy(core, kind, port, user, password)
    core.pump(0.5)
    files = "a%d.bin:900000,b%d.bin:300000" % (index, index)
    seed_dir = core.work + "/seed"
    name = "px%d" % index
    seeder = Seeder("dir=" + seed_dir, "out=" + seed_dir + "/" + name + ".torrent", "name=" + name,
                    "files=" + files, "piece=65536")
    try:
        ready = seeder.ready()
        core.check(label + ": the local seeder is ready", ready is not None, seeder.lines[-3:])
        if not ready:
            return
        tracker, peer = ready["tracker"], ready["peer"]
        sizes = log_sizes()
        magnet = "magnet:?xt=urn:btih:%s&dn=%s&tr=http%%3A%%2F%%2F127.0.0.1%%3A%s%%2Fannounce" % (ready["hash"], name, tracker)
        core.action("SetUrl", text=magnet)
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)
        started = time.time()
        state = wait_state(core, 80)
        announced = [line for line in seeder.lines if line.startswith("ANNOUNCE") and ready["hash"] in line]
        connected = [line for line in seeder.lines if "CONNECT" in line.upper() or "INCOMING" in line.upper()]
        if not works:
            lines = log_since(log_name, sizes)
            refused = [line for line in lines if " 407 " in line and ("127.0.0.1:%s/" % tracker) in line] if kind == HTTP \
                else [line for line in lines if "SOCKS AUTH-FAIL" in line]
            core.check(label + ": no metadata, the dialog reports the failure after %.0f s" % (time.time() - started),
                       state == TSTATE["ERROR"], core.snapshot("addTorrent"))
            core.check(label + ": the seeder's tracker saw no announce (nothing went around the proxy)", not announced, announced[:3])
            core.check(label + ": the proxy refused the login (" + str(len(refused)) + " lines)", len(refused) > 0, lines[-6:])
            passed = [line for line in with_port(lines, tracker) + with_port(lines, peer) if " OK " in line]
            core.check(label + ": the proxy let nothing through", not passed, passed[:4])
            core.action("ShowAddDialog", show=False)
            core.pump(0.5)
            return
        paths = sorted(r.get("path") for r in core.rows("addTorrentFiles"))
        core.check(label + ": the magnet's metadata arrives", state == TSTATE["FILES"] and len(paths) == 2,
                   (core.snapshot("addTorrent"), paths, seeder.lines[-4:]))
        print("    metadata in %.1f s" % (time.time() - started))
        core.check(label + ": the seeder's tracker saw the announce", len(announced) > 0, seeder.lines[-4:])
        if state == TSTATE["FILES"]:
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
        else:
            core.action("ShowAddDialog", show=False)
            core.pump(0.5)
        for other in LOGS:
            lines = log_since(other, sizes)
            via_tracker = with_port(lines, tracker)
            via_peer = with_port(lines, peer)
            if kind == NONE or other != log_name:
                core.check(label + ": nothing of this torrent in " + other + ".log", not via_tracker and not via_peer,
                           (via_tracker + via_peer)[:4])
                continue
            if kind == HTTP:
                good_tracker = [line for line in via_tracker if "HTTP-PROXY OK GET http://127.0.0.1:%s/announce" % tracker in line]
                good_peer = [line for line in via_peer if "HTTP-PROXY OK CONNECT 127.0.0.1:%s" % peer in line]
            else:
                good_tracker = [line for line in via_tracker if "SOCKS OK" in line and line.endswith("user=" + user)]
                good_peer = [line for line in via_peer if "SOCKS OK" in line and line.endswith("user=" + user)]
            core.check(label + ": the tracker announce went through the proxy (" + str(len(good_tracker)) + " lines)",
                       len(good_tracker) > 0, (via_tracker[:4], lines[-4:]))
            core.check(label + ": the peer connection went through the proxy (" + str(len(good_peer)) + " lines)",
                       len(good_peer) > 0, (via_peer[:4], lines[-4:]))
            if good_tracker:
                print("    " + good_tracker[0])
            if good_peer:
                print("    " + good_peer[0])
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
        one_round(core, "no proxy (control: the proxy logs stay empty)", NONE, 0, "", "", "", 0)
        one_round(core, "SOCKS5, no login", SOCKS, PROXY_OPEN_SOCKS, "proxy-open", "", "", 1)
        one_round(core, "HTTP proxy, no login", HTTP, PROXY_OPEN_HTTP, "proxy-open", "", "", 2)
        one_round(core, "SOCKS5 with login", SOCKS, PROXY_LOGIN_SOCKS, "proxy-login", "fj", "secret", 3)
        one_round(core, "HTTP proxy with login", HTTP, PROXY_LOGIN_HTTP, "proxy-login", "fj", "secret", 4)
        one_round(core, "proxy off again after a proxy (the live session goes direct)", NONE, 0, "", "", "", 5)
        one_round(core, "SOCKS5, wrong password", SOCKS, PROXY_LOGIN_SOCKS, "proxy-login", "fj", "wrong", 6, works=False)
        one_round(core, "HTTP proxy, wrong password", HTTP, PROXY_LOGIN_HTTP, "proxy-login", "fj", "wrong", 7, works=False)
        set_proxy(core, 0, 0)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
