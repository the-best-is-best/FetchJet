import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, SERVER, install_fake_ytdlp

STATE = {name: index for index, name in enumerate(ENUMS["MediaStateCode"])}
KIND = {name: index for index, name in enumerate(ENUMS["RowKind"])}
LIST = "https://www.youtube.com/playlist?list=PLfake"


def rows(core, kind):
    return [r for r in core.rows("tasks") if r.get("kind", 0) == KIND[kind]]


def key(path):
    return "dir:" + (path.replace("\\", "/").rstrip("/") or "/")


def add(core, name, folder, later=False):
    os.makedirs(folder, exist_ok=True)
    core.action("ShowAddDialog", show=False)
    core.pump(0.2)
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text=SERVER + "/file/" + name + "?size=1MB&key=fg-" + name)
    core.pump(0.5)
    core.action("SetDestination", path=folder)
    if later:
        core.action("ScheduleQuick", minutes=60)
    core.pump(0.3)
    core.action("StartDownload")
    if later:
        core.wait_for(name, lambda: any(t.get("fileName") == name for t in core.tasks()), 20)
    else:
        core.wait_task("COMPLETED", timeout=40, file_name=name)


def run():
    core = Core("folder_group", before_start=install_fake_ytdlp)
    try:
        core.pump(1.0)
        strings = core.strings()
        root = core.downloads
        work = os.path.join(root, "Work", "Video")
        home = os.path.join(root, "Home", "Video")
        later = os.path.join(root, "Later")
        add(core, "a.bin", root)
        add(core, "b.bin", root)
        add(core, "c.bin", work)
        add(core, "d.bin", home)
        add(core, "e.bin", later, later=True)
        add(core, "f.bin", later, later=True)
        core.action("ShowAddDialog", show=False)
        core.pump(0.8)

        print("the setting is off: every download is its own row")
        core.check("six download rows, no group rows", len(rows(core, "TASK")) == 6 and not rows(core, "GROUP"),
                   [(r.get("kind"), r.get("name")) for r in core.rows("tasks")])

        print("turn it on: one row per folder, the main Download folder too")
        core.action("SettingToggle", id="groupByFolder", on=True)
        core.pump(0.8)
        groups = {r.get("id"): r for r in rows(core, "GROUP")}
        core.check("four folder rows and no download rows", len(groups) == 4 and not rows(core, "TASK"), list(groups))
        root_row = groups.get(key(root), {})
        core.check("the main folder is a row named after it with 2 files",
                   root_row.get("name") == os.path.basename(root.rstrip("/\\")) and "2" in root_row.get("stateLine", ""), root_row)
        core.check("two folders called Video show enough of the path",
                   groups.get(key(work), {}).get("name") == "Work/Video" and groups.get(key(home), {}).get("name") == "Home/Video",
                   (groups.get(key(work), {}).get("name"), groups.get(key(home), {}).get("name")))
        core.check("the row is marked as a folder", root_row.get("tag") == "DIR", root_row.get("tag"))
        core.check("a finished folder reads 2 of 2 done", root_row.get("completed") is True, root_row)

        print("a playlist stays its own row")
        core.action("ShowAddDialog", show=True)
        core.action("SetUrl", text=LIST)
        core.wait_for("the playlist", lambda: (core.snapshot("addMedia") or {}).get("state", 0) == STATE["PLAYLIST"], 60)
        core.pump(0.4)
        core.action("TogglePlaylistEntry", index=2, selected=False)
        core.action("TogglePlaylistEntry", index=3, selected=False)
        core.action("ScheduleQuick", minutes=60)
        core.pump(0.3)
        core.action("StartDownload")
        core.wait_for("eight tasks", lambda: len(core.tasks()) == 8, 20)
        core.pump(0.8)
        playlist_rows = [r for r in rows(core, "GROUP") if r.get("id", "").startswith("pl:")]
        folder_rows = [r for r in rows(core, "GROUP") if r.get("id", "").startswith("dir:")]
        core.check("one playlist row next to the four folder rows", len(playlist_rows) == 1 and len(folder_rows) == 4,
                   [r.get("id") for r in rows(core, "GROUP")])
        core.check("the playlist's folder is not a folder row",
                   not any("Learning Kotlin" in r.get("name", "") for r in folder_rows), [r.get("name") for r in folder_rows])

        print("open a folder: its downloads as rows")
        core.action("RowClick", id=key(later), control=False, shift=False)
        core.pump(0.6)
        chrome = core.snapshot("chrome")
        core.check("the folder page is open, named after the folder", chrome.get("playlistOpen") and chrome.get("groupIsFolder") and
                   chrome.get("title") == "Later", (chrome.get("title"), chrome.get("groupIsFolder")))
        core.check("it shows the folder's full path", chrome.get("groupFolderPath", "").replace("\\", "/").rstrip("/") == later.replace("\\", "/").rstrip("/"),
                   chrome.get("groupFolderPath"))
        names = sorted(r.get("name") for r in rows(core, "TASK"))
        core.check("its two downloads, no group rows", names == ["e.bin", "f.bin"] and not rows(core, "GROUP"), names)

        print("pause all, then resume all")
        mine = lambda: [t for t in core.tasks() if t.get("fileName") in ("e.bin", "f.bin")]
        core.action("PlaylistPauseAll")
        core.wait_for("paused", lambda: all(t.get("status") == "PAUSED" for t in mine()), 20)
        core.check("both paused", all(t.get("status") == "PAUSED" for t in mine()), [t.get("status") for t in mine()])
        core.pump(0.5)
        core.check("the page offers resume", core.snapshot("chrome").get("playlistCanResume"))
        core.action("PlaylistResumeAll")
        core.wait_for("resumed", lambda: all(t.get("status") != "PAUSED" for t in mine()), 20)
        core.check("both running or done again", all(t.get("status") != "PAUSED" for t in mine()), [t.get("status") for t in mine()])

        print("delete all in the folder")
        core.action("PlaylistRemove")
        core.pump(0.4)
        core.action("ConfirmRemove", deleteFiles=True)
        core.wait_for("gone", lambda: not mine(), 30)
        core.pump(1.0)
        core.check("the folder's downloads are gone", not mine())
        core.check("the page closed by itself", not core.snapshot("chrome").get("playlistOpen"))
        core.check("the folder row is gone", key(later) not in {r.get("id") for r in rows(core, "GROUP")},
                   [r.get("id") for r in rows(core, "GROUP")])
        core.check("the other folders stay", {key(root), key(work), key(home)} <= {r.get("id") for r in rows(core, "GROUP")})

        print("a folder whose last download is deleted disappears")
        core.action("RowClick", id=key(home), control=False, shift=False)
        core.pump(0.5)
        core.action("PlaylistRemove")
        core.pump(0.4)
        core.action("ConfirmRemove", deleteFiles=False)
        core.wait_for("d gone", lambda: not any(t.get("fileName") == "d.bin" for t in core.tasks()), 30)
        core.pump(1.0)
        core.check("no Home/Video row left", key(home) not in {r.get("id") for r in rows(core, "GROUP")})
        core.check("and Work/Video is now just Video", next((r.get("name") for r in rows(core, "GROUP") if r.get("id") == key(work)), "") == "Video")

        print("turn it off again")
        core.action("SettingToggle", id="groupByFolder", on=False)
        core.pump(0.8)
        core.check("plain rows again, the playlist still one row",
                   len(rows(core, "TASK")) == 3 and len(rows(core, "GROUP")) == 1,
                   [(r.get("kind"), r.get("name")) for r in core.rows("tasks")])
        core.check("the texts exist", bool(strings.get("groupByFolder")) and bool(strings.get("folderGroupFiles")))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
