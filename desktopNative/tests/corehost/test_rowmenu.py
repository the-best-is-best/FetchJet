import os
import subprocess
import sys
import time
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER
from test_bridge import ALERT

PORT = int(os.environ.get("FJ_ROWMENU_PORT", "18264"))
BASE = "http://127.0.0.1:%d" % PORT
KEYS = ["OPEN", "OPEN_FOLDER", "PAUSE", "RESUME", "EXTRACT", "COMPRESS", "CUT", "EXTRACT_AUDIO", "SUBTITLES", "SEND_TO_DEVICE", "COPY_LINK", "SELECT", "DETAILS", "UNHIDE", "DELETE"]
KEY = {name: index for index, name in enumerate(KEYS)}


def make_site(folder):
    os.makedirs(folder, exist_ok=True)
    with zipfile.ZipFile(os.path.join(folder, "pack.zip"), "w") as z:
        z.writestr("a.txt", b"a" * 1000)
        z.writestr("b.txt", b"b" * 1000)
    open(os.path.join(folder, "song.mp3"), "wb").write(os.urandom(64 * 1024))
    open(os.path.join(folder, "plain.bin"), "wb").write(os.urandom(64 * 1024))


def add_and_wait(core, url, name, status=("COMPLETED", "ERROR")):
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text=url)
    core.pump(0.6)
    core.action("SetFileName", text=name)
    core.pump(0.2)
    core.action("StartDownload")
    return core.wait_task(list(status), timeout=40, file_name=name)


def menu(core, task_id):
    core.action("RowMenu", id=task_id)
    core.pump(0.3)
    return [KEYS[r.get("key", 0)] for r in core.rows("rowMenu") if r.get("key", 0) < len(KEYS)]


def labels(core):
    return [(r.get("label", ""), r.get("danger", False), r.get("divider", False)) for r in core.rows("rowMenu") if r.get("key", 0) < len(KEYS)]


def layout(core):
    return [(r.get("key", 0), r.get("header", ""), r.get("divider", False)) for r in core.rows("rowMenu")]


def run():
    core = Core("rowmenu")
    s = core.strings()
    site = os.path.join(core.work, "site")
    make_site(site)
    server = subprocess.Popen([sys.executable, "-m", "http.server", str(PORT), "--bind", "127.0.0.1", "--directory", site],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    try:
        core.pump(0.5)
        print("a finished plain file")
        plain = add_and_wait(core, BASE + "/plain.bin", "plain.bin")
        entries = menu(core, plain["id"])
        core.check("grouped: open, folder, details, send, copy, delete", entries == ["OPEN", "OPEN_FOLDER", "DETAILS", "SEND_TO_DEVICE", "COPY_LINK", "DELETE"], entries)
        shown = labels(core)
        core.check("the labels are filled and only delete is red", all(l for l, _, _ in shown) and [d for _, d, _ in shown] == [False] * 5 + [True], shown)
        rows = layout(core)
        core.check("seven rows with hide before delete", len(rows) == 7 and rows[5][0] == 100 and rows[6][0] == KEY["DELETE"], rows)
        core.check("a header at the start of each section", [h for _, h, _ in rows] == [s["rowMenuSectionOpen"], "", "", s["rowMenuSectionDownload"], "", s["rowMenuSectionManage"], ""], rows)
        core.check("a line before each section but the first", [v for _, _, v in rows] == [False, False, False, True, False, True, False], rows)
        core.check("headers and lines are not rows of their own", all(k == 100 or k < len(KEYS) for k, _, _ in rows), rows)
        core.check("no pause or resume on a finished file", "PAUSE" not in entries and "RESUME" not in entries)

        print("pick: details, copy link, delete")
        core.action("RowMenuPick", key=KEY["DETAILS"])
        core.pump(0.5)
        details = core.snapshot("details") or {}
        core.check("details opens on it", details.get("open") or details.get("name") == "plain.bin" or details.get("fileName") == "plain.bin", list(details.items())[:4])
        core.check("the menu closes after a pick", core.rows("rowMenu") == [])
        core.action("CloseDetails")
        menu(core, plain["id"])
        before = (core.snapshot("shell") or {}).get("toastText", "")
        core.action("RowMenuPick", key=KEY["COPY_LINK"])
        end = time.time() + 5
        toast = before
        while time.time() < end and toast == before:
            core.pump(0.2)
            toast = (core.snapshot("shell") or {}).get("toastText", "")
        core.check("copy link shows the copied toast", toast != before and "opied" in toast, (before, toast))
        menu(core, plain["id"])
        core.action("RowMenuPick", key=KEY["DELETE"])
        core.pump(0.5)
        kinds = [a.get("kind") for a in core.rows("alerts")]
        core.check("delete asks first (the remove dialog)", ALERT["REMOVE"] in kinds, kinds)
        core.action("AlertDismiss", kind=ALERT["REMOVE"], fromScrim=True)
        core.pump(0.3)
        core.check("nothing was removed without the answer", core.task(file_name="plain.bin") is not None)

        print("an archive and an audio file")
        pack = add_and_wait(core, BASE + "/pack.zip", "pack.zip")
        entries = menu(core, pack["id"])
        core.check("a zip offers extract", "EXTRACT" in entries and "COMPRESS" not in entries, entries)
        core.action("RowMenuPick", key=KEY["EXTRACT"])
        core.wait_for("the extracted folder", lambda: os.path.isfile(os.path.join(core.downloads, "pack", "b.txt")), 30)
        core.check("extract from the menu works", True)
        song = add_and_wait(core, BASE + "/song.mp3", "song.mp3")
        entries = menu(core, song["id"])
        core.check("an mp3 offers compress, not extract", "COMPRESS" in entries and "EXTRACT" not in entries, entries)
        core.check("an mp3 offers no extract audio", "EXTRACT_AUDIO" not in entries, entries)
        rows = layout(core)
        heads = [h for _, h, _ in rows if h]
        core.check("the media section has its header", s["rowMenuSectionMedia"] in heads and heads == [s["rowMenuSectionOpen"], s["rowMenuSectionMedia"], s["rowMenuSectionDownload"], s["rowMenuSectionManage"]], heads)
        core.action("RowMenuClose")
        core.pump(0.2)
        core.check("close empties the menu", core.rows("rowMenu") == [])

        print("a running download, then paused")
        core.action("ShowAddDialog", show=True)
        core.action("SetUrl", text=SERVER + "/file/30MB.bin?key=rowmenu&throttle=200")
        core.pump(0.6)
        core.action("StartDownload")
        running = core.wait_task(["DOWNLOADING"], timeout=40, url_part="key=rowmenu")
        entries = menu(core, running["id"])
        core.check("running: pause, no open", "PAUSE" in entries and "OPEN" not in entries and "RESUME" not in entries, entries)
        core.action("RowMenuPick", key=KEY["PAUSE"])
        core.wait_task(["PAUSED"], timeout=20, url_part="key=rowmenu")
        entries = menu(core, running["id"])
        core.check("paused: resume, no pause", "RESUME" in entries and "PAUSE" not in entries, entries)
        core.action("RowMenuPick", key=KEY["RESUME"])
        core.wait_task(["DOWNLOADING", "CONNECTING", "QUEUED"], timeout=20, url_part="key=rowmenu")
        core.check("resume from the menu starts it again", True)

        print("a pick that is not in the menu is ignored")
        menu(core, running["id"])
        core.action("RowMenuPick", key=KEY["EXTRACT"])
        core.pump(1)
        core.check("no folder made for a .bin", not os.path.exists(os.path.join(core.downloads, "30MB")))
        core.action("RowMenu", id="no-such-id")
        core.pump(0.3)
        core.check("an unknown id gives no menu", core.rows("rowMenu") == [])
        core.action("Pause", id=running["id"])
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    finally:
        server.kill()
    core.finish()


if __name__ == "__main__":
    run()
