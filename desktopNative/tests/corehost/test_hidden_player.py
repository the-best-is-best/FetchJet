import glob
import json
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, install_fake_ytdlp
from test_bridge import EFFECT
from test_hidden import add_and_wait, chrome_of, open_hidden, pin_alert, type_pin, visible_names, KIND

PORT = int(os.environ.get("FJ_HIDDEN_PLAYER_PORT", "18267"))
BASE = "http://127.0.0.1:%d" % PORT
PIN = "2468"


def copies(core):
    return sorted(p for p in glob.glob(os.path.join(core.home, "**", "hidden_play", "**", "*"), recursive=True) if os.path.isfile(p))


def effects_of(core, kind):
    return [e for e in core.take_effects() if e.get("kind", 0) == EFFECT[kind]]


def wait_path(core, task_id, timeout=30):
    found = []

    def done():
        for e in core.take_effects():
            if e.get("kind", 0) == EFFECT["PLAYER_PATH"] and e.get("title") == task_id:
                found.append(e.get("text", ""))
        return bool(found)

    core.wait_for("the decrypted path of " + task_id, done, timeout)
    return found[0]


def run():
    core = Core("hidden_player", before_start=install_fake_ytdlp)
    site = os.path.join(core.work, "site")
    os.makedirs(site, exist_ok=True)
    data = {"one.mp4": os.urandom(96 * 1024), "two.mp4": os.urandom(80 * 1024), "three.mp3": os.urandom(40 * 1024)}
    for name, blob in data.items():
        open(os.path.join(site, name), "wb").write(blob)
    server = subprocess.Popen([sys.executable, "-m", "http.server", str(PORT), "--bind", "127.0.0.1", "--directory", site],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    try:
        core.pump(0.5)
        tasks = {name: add_and_wait(core, BASE + "/" + name, name) for name in data}
        core.check("three finished media files", all(t and t["status"] == "COMPLETED" for t in tasks.values()), [t and t["status"] for t in tasks.values()])
        ids = {name: t["id"] for name, t in tasks.items()}

        print("normal playback is untouched: the queue of a visible file has real paths")
        core.take_effects()
        core.check("no hidden queue for a visible task", core.debug("playHidden", {"id": ids["one.mp4"]}) in ("", None))

        print("hide the three with encryption")
        for name in data:
            core.action("RowCheck", id=ids[name], checked=True)
        core.pump(0.3)
        core.action("HideChecked")
        core.pump(0.5)
        alert = pin_alert(core)
        if alert is not None:
            type_pin(core, PIN)
            type_pin(core, PIN)
        core.pump(0.6)
        notice = next((a for a in core.rows("alerts") if a.get("title") and not a.get("inputSecret") and a.get("confirmLabel")), None)
        if notice is not None:
            core.action("AlertConfirm", kind=notice["kind"], checked=False)
        core.wait_for("the files leave the list", lambda: not set(data) & set(visible_names(core)), 30)
        stored = glob.glob(os.path.join(core.home, "**", "*.fjh"), recursive=True)
        core.check("the media is stored encrypted", len(stored) >= 3, stored)

        print("locked: nothing can be played or prepared")
        core.check("no queue while locked", core.debug("playHidden", {"id": ids["one.mp4"]}) in ("", None))
        core.take_effects()
        core.action("PlayerPrepare", id=ids["one.mp4"])
        path = wait_path(core, ids["one.mp4"])
        core.check("prepare while locked answers an empty path", path == "", path)
        core.check("and writes no copy", copies(core) == [], copies(core))

        print("unlock and play one file")
        open_hidden(core, PIN)
        core.check("the hidden screen is open", chrome_of(core)["hiddenOpen"])
        core.take_effects()
        core.check("the debug play answers ok", core.debug("playHidden", {"id": ids["one.mp4"]}) == "ok")
        opened = effects_of(core, "OPEN_PLAYER")
        queue = json.loads(opened[0]["text"]) if opened else {}
        items = queue.get("items", [])
        core.check("OPEN_PLAYER carries the hidden item with no path", len(items) >= 1 and all(i.get("hidden") and not i.get("path") for i in items), items)
        core.check("the queue starts at the clicked file", items and items[queue.get("index", 0)]["id"] == ids["one.mp4"], queue.get("index"))
        core.check("the group key is not a plain id", queue.get("groupKey", "") == "" or queue.get("groupKey", "").startswith("h:"), queue.get("groupKey"))
        core.check("nothing is decrypted before the host asks", copies(core) == [], copies(core))

        core.action("PlayerPrepare", id=ids["one.mp4"])
        path = wait_path(core, ids["one.mp4"])
        core.check("prepare answers a path in the hidden play folder", "hidden_play" in path.replace("\\", "/") and os.path.isfile(path), path)
        core.check("the copy is the original file", os.path.isfile(path) and open(path, "rb").read() == data["one.mp4"])
        core.check("exactly one copy exists", len(copies(core)) == 1, copies(core))

        print("move to the next item: release, then prepare the next")
        core.action("PlayerRelease", id=ids["one.mp4"])
        core.wait_for("the first copy is deleted", lambda: not os.path.exists(path), 10)
        core.check("release deletes the copy", not os.path.exists(path), path)
        core.action("PlayerPrepare", id=ids["two.mp4"])
        path2 = wait_path(core, ids["two.mp4"])
        core.check("the second file is decrypted", os.path.isfile(path2) and open(path2, "rb").read() == data["two.mp4"], path2)
        core.action("PlayerPrepare", id=ids["three.mp3"])
        path3 = wait_path(core, ids["three.mp3"])
        core.check("preparing another drops the earlier copy", os.path.isfile(path3) and not os.path.exists(path2), (path2, path3))

        print("close: every copy is gone")
        core.action("PlayerClosed")
        core.wait_for("all copies are gone", lambda: copies(core) == [], 10)
        core.check("closing the player leaves no copies", copies(core) == [], copies(core))
        core.check("the hidden screen stays open", chrome_of(core)["hiddenOpen"])

        print("a late answer after close is not delivered")
        core.take_effects()
        core.action("PlayerPrepare", id=ids["one.mp4"])
        core.action("PlayerClosed")
        core.pump(3.0)
        late = [e.get("text", "") for e in effects_of(core, "PLAYER_PATH")]
        core.wait_for("no copy after the cancelled prepare", lambda: copies(core) == [], 10)
        core.check("a prepare cut short by close delivers no path and leaves no copy", all(t == "" for t in late) and copies(core) == [], (late, copies(core)))

        print("lock while playing closes the player and deletes the copy")
        core.debug("playHidden", {"id": ids["two.mp4"]})
        core.take_effects()
        core.action("PlayerPrepare", id=ids["two.mp4"])
        path2 = wait_path(core, ids["two.mp4"])
        core.check("the copy exists before the lock", os.path.isfile(path2), path2)
        core.action("HiddenLock")
        core.pump(0.6)
        closes = effects_of(core, "CLOSE_PLAYER")
        core.check("the lock asks the host to close the player", len(closes) == 1, closes)
        core.wait_for("the copy is deleted by the lock", lambda: copies(core) == [], 10)
        core.check("the lock deletes the copy", copies(core) == [], copies(core))

        print("a hidden folder page gets the group Play")
        core.action("SettingToggle", id="groupByFolder", on=True)
        core.pump(0.6)
        open_hidden(core, PIN)
        dirs = [r for r in core.rows("tasks") if r.get("id", "").startswith("dir:")]
        core.check("the hidden files show as one folder group", len(dirs) == 1 and dirs[0].get("kind") == KIND["GROUP"], [(r.get("kind"), r.get("id")) for r in core.rows("tasks")])
        if dirs:
            core.action("RowClick", id=dirs[0]["id"], control=False, shift=False)
            core.pump(0.6)
            chrome = chrome_of(core)
            core.check("the folder page is open with the Play button", chrome["playlistOpen"] and chrome["playlistCanPlay"], (chrome["playlistOpen"], chrome["playlistCanPlay"]))
            core.take_effects()
            core.check("the group queue opens", core.debug("playHidden", {"id": ""}) == "ok")
            opened = effects_of(core, "OPEN_PLAYER")
            queue = json.loads(opened[0]["text"]) if opened else {}
            items = queue.get("items", [])
            core.check("the group queue lists the three hidden files, no paths", len(items) == 3 and all(i.get("hidden") and not i.get("path") for i in items), items)
            core.check("the group key is hashed", queue.get("groupKey", "").startswith("h:"), queue.get("groupKey"))
            core.action("PlayerClosed")
            core.pump(0.3)
            core.action("SettingPick", id="desktopPlayer", index=1)
            core.pump(0.6)
            chrome = chrome_of(core)
            core.check("with the system app chosen the group Play is hidden", not chrome["playlistCanPlay"], chrome["playlistCanPlay"])
            core.check("and no hidden queue is made", core.debug("playHidden", {"id": ""}) in ("", None))
            core.action("SettingPick", id="desktopPlayer", index=0)
            core.pump(0.4)
        core.action("HiddenLock")
        core.pump(0.4)
        core.action("SettingToggle", id="groupByFolder", on=False)
        core.pump(0.3)

        print("Settings: the player row has two options")
        core.action("OpenSettings")
        core.pump(0.4)
        found = []
        for section in range(0, 12):
            core.action("SetSettingsSection", index=section)
            core.pump(0.2)
            row = next((r for r in core.rows("settingsRows") if r.get("id") == "desktopPlayer"), None)
            if row:
                found.append(row)
                break
        if found:
            row = found[0]
            strings = core.strings()
            core.check("two options: in-app player and system app", row.get("options") == [strings["playerModeOn"], strings["playerModeOff"]], row.get("options"))
            core.check("in-app player is picked", row.get("pick") == 1, row.get("pick"))
        else:
            print("  note  the player row was not found in the settings pages")
        core.check("no copies left at the end", copies(core) == [], copies(core))
    except CheckFailed as failure:
        core.check(str(failure), False)
    finally:
        server.terminate()
    core.finish()


if __name__ == "__main__":
    run()
