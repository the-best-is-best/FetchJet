import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, install_fake_ytdlp

STATE = {name: index for index, name in enumerate(ENUMS["MediaStateCode"])}
LIST = "https://www.youtube.com/playlist?list=PLfake"


def media(core):
    return core.snapshot("addMedia") or {}


def open_list(core):
    core.action("ShowAddDialog", show=False)
    core.pump(0.3)
    core.action("SetUrl", text=LIST)
    core.action("ShowAddDialog", show=True)
    core.wait_for("the playlist", lambda: media(core).get("state", 0) == STATE["PLAYLIST"], 60)
    core.pump(0.3)


def size_bytes(text):
    clean = "".join(c for c in text if c not in "⁦⁧⁨⁩‎‏")
    number, unit = clean.replace(",", ".").split()
    return float(number) * {"B": 1, "KB": 1024, "MB": 1024 ** 2, "GB": 1024 ** 3}[unit]


def run():
    core = Core("playlist", before_start=install_fake_ytdlp)
    try:
        core.pump(1.0)
        strings = core.strings()

        print("sizes are read in the background")
        open_list(core)
        rows = core.rows("addPlaylist")
        core.check("four videos, numbered 1-4", [r.get("number") for r in rows] == ["1.", "2.", "3.", "4."], rows)
        core.wait_for("every size", lambda: all(r.get("size") for r in core.rows("addPlaylist")), 60)
        rows = core.rows("addPlaylist")
        core.check("every row has a size and none is still busy", all(r.get("size") and not r.get("sizeBusy") for r in rows),
                   [(r.get("size"), r.get("sizeBusy")) for r in rows])
        best = size_bytes(rows[0]["size"])
        core.action("SetPlaylistQuality", quality=4)
        core.pump(0.5)
        small = core.rows("addPlaylist")[0].get("size", "")
        core.check("360p is smaller than Best", small and size_bytes(small) < best, (rows[0]["size"], small))
        core.action("SetPlaylistQuality", quality=0)
        core.pump(0.3)

        print("oldest first")
        core.action("SetPlaylistOldestFirst", on=True)
        core.pump(0.3)
        rows = core.rows("addPlaylist")
        core.check("the last video comes first, numbered 1", rows[0].get("title") == "04 Classes" and rows[0].get("number") == "1.",
                   [(r.get("number"), r.get("title")) for r in rows])
        core.check("the switch shows on", media(core).get("playlistOldestFirst") is True)
        core.check("sizes stay after the order changed", all(r.get("size") for r in rows))

        print("queue two videos for later, oldest first")
        core.action("TogglePlaylistEntry", index=1, selected=False)
        core.action("TogglePlaylistEntry", index=2, selected=False)
        core.action("ScheduleQuick", minutes=60)
        core.pump(0.3)
        core.action("StartDownload")
        core.wait_for("two tasks", lambda: len(core.tasks()) == 2, 20)
        tasks = core.tasks()
        names = [t.get("fileName") for t in tasks]
        core.check("both are queued", len(tasks) == 2, names)
        ids = {t.get("playlistId", "") for t in tasks}
        core.check("both carry the same playlist id", len(ids) == 1 and "" not in ids, ids)

        print("the same playlist again")
        open_list(core)
        rows = core.rows("addPlaylist")
        before = {r.get("title"): (bool(r.get("before")), bool(r.get("selected"))) for r in rows}
        core.check("the two queued videos are marked and unticked",
                   before.get("04 Classes") == (True, False) and before.get("01 Introduction") == (True, False), before)
        core.check("the others are not",
                   before.get("02 Variables and types") == (False, True) and before.get("03 الدوال والتعابير") == (False, True), before)
        core.check("a new list starts newest first again", not media(core).get("playlistOldestFirst") and rows[0].get("title") == "01 Introduction")
        core.check("the text for it exists", bool(strings.get("playlistDownloadedBefore")))

        print("download the other two now: one notice when both are done")
        core.action("SetPlaylistQuality", quality=4)
        core.action("ScheduleQuick", minutes=0)
        core.pump(0.3)
        core.action("StartDownload")
        core.wait_for("four tasks", lambda: len(core.tasks()) == 4, 20)
        fresh = [t for t in core.tasks() if t.get("playlistId") not in ids]
        core.check("the two new ones share a new playlist id", len(fresh) == 2 and len({t.get("playlistId") for t in fresh}) == 1,
                   [(t.get("fileName"), t.get("playlistId")) for t in fresh])
        new_ids = {t["id"] for t in fresh}
        core.wait_for("both finished", lambda: all(t.get("status") in ("COMPLETED", "ERROR") for t in core.tasks() if t["id"] in new_ids), 120)
        core.pump(1.5)
        statuses = [t.get("status") for t in core.tasks() if t["id"] in new_ids]
        log = core.log_text()
        done_lines = [line for line in log.splitlines() if "Playlist done:" in line]
        core.check("both ended (the fake yt-dlp cannot download a playlist quality, so they fail)", len(statuses) == 2, statuses)
        core.check("exactly one notice for the list, counting both videos",
                   len(done_lines) == 1 and "(0 finished, 2 failed)" in done_lines[0] and "Learning Kotlin" in done_lines[0], done_lines)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
    core.finish()


if __name__ == "__main__":
    run()
