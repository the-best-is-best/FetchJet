import os
import sys
import time

TORRENT_DLL = os.environ.get("FETCHJET_TORRENT_DLL", "E:/tools/probes/fjtorrent/build/fjtorrent.dll")
os.environ["FETCHJET_TORRENT_DLL"] = TORRENT_DLL
os.environ["FETCHJET_TORRENT_LOCAL_ONLY"] = "1"

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS
from test_torrent import Seeder, same_files, wait_files, TSTATE, SEED_EXE

TRACKER = {name: index for index, name in enumerate(ENUMS.get("TrackerStateCode", []))}
PIECE = 65536
FILES = [("a.bin", 1500000), ("sub/b.bin", 3000000), ("c.txt", 70000)]
PIECES = sum((size + PIECE - 1) // PIECE for _, size in FILES)


def plain(text):
    return (text or "").replace("\u2066", "").replace("\u2069", "")


def page(core):
    return core.snapshot("torrentDetails") or {}


def ready_page(core, timeout=20):
    return core.wait_for("the torrent details", lambda: (lambda p: p if p.get("shown") and not p.get("loading") else None)(page(core)), timeout)


def tracker_with(core, port):
    for row in page(core).get("trackers", []):
        if (":" + str(port)) in plain(row.get("name")):
            return row
    return None


def run():
    if not os.path.isfile(TORRENT_DLL) or not os.path.isfile(SEED_EXE):
        raise SystemExit("build fjtorrent.dll and fjtseed.exe first (desktopNative/torrent/build.cmd): " + TORRENT_DLL)
    if not TRACKER:
        raise SystemExit("run desktopNative/tools/gen/gen.py first: protocol.json has no TrackerStateCode")
    core = Core("torrentdetails")
    seeders = []
    try:
        core.pump(0.5)
        strings = core.strings()
        seed_dir = core.work + "/seed"
        one = Seeder("dir=" + seed_dir, "out=" + seed_dir + "/pack.torrent", "name=pack",
                     "files=" + ",".join("%s:%d" % item for item in FILES), "piece=%d" % PIECE)
        seeders.append(one)
        two = Seeder("dir=" + seed_dir, "out=" + seed_dir + "/other.torrent", "name=other", "files=o.bin:200000", "piece=%d" % PIECE)
        seeders.append(two)
        ready_one = one.ready()
        ready_two = two.ready()
        core.check("two local seeders are ready on 127.0.0.1", ready_one is not None and ready_two is not None, (one.lines[-3:], two.lines[-3:]))
        if not ready_one or not ready_two:
            raise CheckFailed("no seeder")

        print("closed")
        closed = page(core)
        core.check("no torrent section while no task is open", not closed.get("shown", False), closed)

        print("download")
        core.action("SetUrl", text=os.path.normpath(ready_one["torrent"]))
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)
        wait_files(core, 30)
        info = core.snapshot("addTorrent") or {}
        core.check("the file list is read from the .torrent", info.get("state") == TSTATE["FILES"], info)
        core.action("StartDownload")
        core.pump(0.5)
        task = core.wait_for("the torrent task", lambda: core.task(url_part="pack.torrent"), 20)

        print("details while it runs or right after")
        core.action("RowClick", id=task["id"])
        first = core.wait_for("the torrent section", lambda: (lambda p: p if p.get("shown") else None)(page(core)), 10)
        core.check("opening a torrent task shows the torrent section for it", first.get("id") == task["id"], first)
        shown = ready_page(core)
        core.check("the engine gives the details on this platform", shown.get("supported", False), shown)
        core.check("ratio, uploaded amount and piece size are filled", plain(shown.get("ratio")) != "" and
                   plain(shown.get("uploaded")) != "" and plain(shown.get("pieceSize")) == "64.0 KB",
                   (shown.get("ratio"), shown.get("uploaded"), shown.get("pieceSize")))
        core.check("the pieces line counts the torrent's %d pieces" % PIECES,
                   plain(shown.get("pieces")).endswith("/ %d" % PIECES), shown.get("pieces"))

        task = core.wait_task("COMPLETED", timeout=90, url_part="pack.torrent")
        bad = same_files(seed_dir + "/pack", core.downloads, [name for name, _ in FILES])
        core.check("the files match the seeder's bytes", not bad, bad)
        done = core.wait_for("all pieces done in the details", lambda: (lambda p: p if p.get("piecesFraction", 0) >= 1.0 else None)(page(core)), 20)
        core.check("a finished torrent shows every piece done", plain(done.get("pieces")) == "%d / %d" % (PIECES, PIECES), done.get("pieces"))

        print("trackers")
        own = core.wait_for("the torrent's own tracker to work",
                            lambda: (lambda r: r if r and r.get("state", 0) == TRACKER["WORKING"] else None)(tracker_with(core, ready_one["tracker"])), 30)
        core.check("the torrent's tracker is listed as working with its translated label",
                   own.get("stateLabel") == strings.get("torrentDetailsStateWorking") and own.get("message", "") == "", own)
        rows = page(core).get("trackers", [])
        core.check("the trackers title carries the count", plain(page(core).get("trackersTitle")).endswith(str(len(rows))) and len(rows) >= 1,
                   (page(core).get("trackersTitle"), len(rows)))
        failed = [row for row in rows if row.get("state", 0) == TRACKER["ERROR"]]
        core.check("a failing tracker carries the error label", all(row.get("stateLabel") == strings.get("torrentDetailsStateError") for row in failed),
                   failed)

        print("magnet link")
        magnet = page(core).get("magnet", "")
        core.check("the magnet link of the torrent is ready for the copy button",
                   magnet.startswith("magnet:?") and ready_one["hash"].lower() in magnet.lower(), magnet)

        print("add tracker")
        before = page(core)
        core.action("TorrentAddTracker", url="not a tracker")
        wrong = core.wait_for("the tracker error", lambda: (lambda p: p if p.get("trackerError") else None)(page(core)), 10)
        core.check("an invalid tracker address shows the translated error", wrong.get("trackerError") == strings.get("torrentDetailsTrackerInvalid"),
                   wrong.get("trackerError"))
        core.check("the invalid address is not added and the field keeps its text",
                   len(wrong.get("trackers", [])) == len(before.get("trackers", [])) and wrong.get("trackerRev", 0) == before.get("trackerRev", 0),
                   (wrong.get("trackers"), wrong.get("trackerRev"), before.get("trackerRev")))
        extra = "http://127.0.0.1:%s/announce" % ready_two["tracker"]
        core.action("TorrentAddTracker", url="  " + extra + " ")
        core.wait_for("the added tracker in the list", lambda: tracker_with(core, ready_two["tracker"]), 20)
        added = core.wait_for("the field to be cleared", lambda: (lambda p: p if p.get("trackerRev", 0) > before.get("trackerRev", 0) else None)(page(core)), 10)
        core.check("a valid tracker is added, the error goes and the field is cleared",
                   added.get("trackerError", "") == "" and len(added.get("trackers", [])) == len(before.get("trackers", [])) + 1,
                   (added.get("trackerError"), added.get("trackers")))
        try:
            core.wait_for("the announce to the added tracker",
                          lambda: any(line.startswith("ANNOUNCE") and ready_one["hash"] in line for line in two.lines), 30)
            core.check("the app announced to the added tracker", True)
        except CheckFailed:
            core.check("the app announced to the added tracker", False, two.lines[-5:])

        print("first and last pieces")
        core.check("first and last pieces first starts off", not page(core).get("firstLast", False), page(core).get("firstLast"))
        core.action("TorrentSetFirstLast", on=True)
        core.wait_for("first and last pieces first on", lambda: page(core).get("firstLast", False), 10)
        core.check("the toggle turns on", True)
        core.action("TorrentSetFirstLast", on=False)
        core.wait_for("first and last pieces first off", lambda: not page(core).get("firstLast", False), 10)
        core.check("the toggle turns off", True)

        print("check again")
        seen_partial = [False]

        def rechecked():
            current = page(core)
            if current.get("piecesFraction", 0) < 1.0:
                seen_partial[0] = True
                return None
            return current if time.time() - started > 3.0 else None

        started = time.time()
        core.action("TorrentRecheck")
        after = core.wait_for("the recheck to finish", rechecked, 60)
        print("    recheck %s in %.1f s" % ("was seen" if seen_partial[0] else "was not seen in the 2 s refresh", time.time() - started))
        core.check("after Check again every piece is done again", plain(after.get("pieces")) == "%d / %d" % (PIECES, PIECES), after.get("pieces"))
        task = core.task(task_id=task["id"])
        core.check("the task is still complete after the recheck", task and task["status"] == "COMPLETED", task)
        bad = same_files(seed_dir + "/pack", core.downloads, [name for name, _ in FILES])
        core.check("the files are untouched by the recheck", not bad, bad)

        print("other pages")
        core.action("OpenSettings")
        core.wait_for("the section to hide in Settings", lambda: not page(core).get("shown", False), 10)
        core.check("opening Settings hides the torrent section", True)
        core.action("SetFilter", filter=0)
        back = core.wait_for("the section to come back", lambda: (lambda p: p if p.get("shown") and not p.get("loading") else None)(page(core)), 20)
        core.check("back on the list the open torrent shows its details again", back.get("id") == task["id"] and len(back.get("trackers", [])) >= 2,
                   back.get("trackers"))
        core.action("CloseDetails")
        core.wait_for("the section to close", lambda: not page(core).get("shown", False), 10)
        core.check("closing the details clears the torrent section", page(core).get("trackers", []) == [], page(core))

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
