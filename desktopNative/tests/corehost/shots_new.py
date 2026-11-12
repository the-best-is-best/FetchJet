import os
import subprocess
import sys

TORRENT_DLL = os.environ.get("FETCHJET_TORRENT_DLL", "E:/tools/probes/fjtorrent/build/fjtorrent.dll")
os.environ["FETCHJET_TORRENT_DLL"] = TORRENT_DLL
os.environ["FETCHJET_TORRENT_LOCAL_ONLY"] = "1"

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER
from shots import shot, OUT, SHOT, REPO
from test_torrent import Seeder, wait_files, SEED_EXE
from test_torrent_details import FILES, PIECE, page, ready_page, tracker_with, TRACKER


def run():
    core = Core("shots-new")
    seeder = None
    failed = 0

    def take(name, width=1200, height=800, **kwargs):
        nonlocal failed
        core.pump(0.5)
        if not shot(core, name, width=width, height=height, **kwargs):
            failed += 1

    try:
        core.action("SystemTheme", dark=True)
        core.pump(0.5)
        seed_dir = core.work + "/seed"
        seeder = Seeder("dir=" + seed_dir, "out=" + seed_dir + "/pack.torrent", "name=pack",
                        "files=" + ",".join("%s:%d" % item for item in FILES), "piece=%d" % PIECE)
        ready = seeder.ready()
        core.action("SetUrl", text=os.path.normpath(ready["torrent"]))
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)
        wait_files(core, 30)
        core.action("StartDownload")
        core.pump(0.5)
        task = core.wait_for("the torrent task", lambda: core.task(url_part="pack.torrent"), 20)
        core.wait_task("COMPLETED", timeout=90, url_part="pack.torrent")
        core.action("RowClick", id=task["id"])
        ready_page(core)
        core.wait_for("a working tracker", lambda: (lambda r: r if r and r.get("state", 0) == TRACKER["WORKING"] else None)(tracker_with(core, ready["tracker"])), 40)
        core.pump(1.0)
        take("new-torrent-details-dark", width=1300, height=900)
        core.action("ToggleTheme")
        core.pump(0.6)
        take("new-torrent-details-light", width=1300, height=900)
        core.action("ToggleTheme")
        core.action("SettingPick", id="language", index=2)
        core.pump(1.0)
        take("new-torrent-details-ar", width=1300, height=900)
        core.action("SettingPick", id="language", index=1)
        core.action("RowClick", id=task["id"])

        core.debug("addDirect", {"url": SERVER + "/file/3MB.bin?key=sn-tl", "fileName": "x.bin"})
        core.action("ShowAddDialog", show=True)
        core.action("SetUrl", text=SERVER + "/file/3MB.bin?key=sn-tl2")
        core.pump(0.6)
        core.action("TestLink")
        core.wait_for("the test result", lambda: (core.snapshot("add") or {}).get("testLinkText", ""), 30)
        take("new-add-test-link-dark", width=1100, height=820)
        core.action("ShowAddDialog", show=False)

        core.action("OpenSettings")
        core.pump(0.5)
        for index in range(0, 8):
            core.action("SetSettingsSection", index=index)
            core.pump(0.5)
            if index == 4:
                core.action("SettingPick", id="proxyKind", index=1)
                core.pump(0.4)
            take("new-settings-%d-dark" % index, width=1100, height=900)
    except CheckFailed as failure:
        print("FAILED: " + str(failure))
        print(core.log_text()[-2000:])
        failed += 1
    finally:
        if seeder:
            seeder.stop()
    core.finish()
    return failed


if __name__ == "__main__":
    run()
