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

        print("the list is read twice at once: the sizes still arrive (no -2 left by a stopped reader)")
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        core.action("SetUrl", text=LIST)
        core.action("ShowAddDialog", show=True)
        core.wait_for("the playlist", lambda: media(core).get("state", 0) == STATE["PLAYLIST"], 60)
        core.action("InspectMedia")
        core.wait_for("the playlist again", lambda: media(core).get("state", 0) == STATE["PLAYLIST"], 60)
        core.wait_for("every size or a failure", lambda: all(r.get("size") or not r.get("sizeBusy") for r in core.rows("addPlaylist")), 60)
        rows = core.rows("addPlaylist")
        core.check("every video got its size", all(r.get("size") for r in rows), [(r.get("title"), r.get("size"), r.get("sizeBusy")) for r in rows])

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

        print("subtitles for the whole list")
        core.wait_for("the subtitle languages", lambda: len(core.rows("addSubtitles")) >= 3, 30)
        subs = core.rows("addSubtitles")
        codes = sorted(r.get("code") or r.get("langCode") or "" for r in subs)
        core.check("the languages of the videos are offered once each (en, ar, fr)", codes == ["ar", "en", "fr"], subs)
        core.check("the subtitles card shows", media(core).get("subtitlesShown") is True)
        core.action("SetPlaylistQuality", quality=5)
        core.pump(0.3)
        core.check("not for Audio only", not media(core).get("subtitlesShown"))
        core.action("SetPlaylistQuality", quality=0)
        core.action("SelectAllPlaylist", selected=False)
        core.pump(0.3)
        core.check("not when no video is ticked", not media(core).get("subtitlesShown"))
        core.action("SelectAllPlaylist", selected=True)
        core.pump(0.3)
        core.check("back when a video is ticked", media(core).get("subtitlesShown") is True)

        print("oldest first")
        core.check("a playlist link (list=) does not offer Oldest first", not media(core).get("playlistOldestFirstOffered"))
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
        core.action("ToggleSubtitle", code="en", selected=True)
        core.action("ScheduleQuick", minutes=60)
        core.pump(0.3)
        core.action("StartDownload")
        core.wait_for("two tasks", lambda: len(core.tasks()) == 2, 20)
        tasks = core.tasks()
        core.check("each queued video carries its own English track", all(t.get("subtitles") == "en" for t in tasks),
                   [(t.get("fileName"), t.get("subtitles"), t.get("subtitleLanguages")) for t in tasks])
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
        core.action("SelectAllPlaylist", selected=True)
        core.pump(0.3)
        ticked = {r.get("title"): bool(r.get("selected")) for r in core.rows("addPlaylist")}
        core.check("Select all leaves the downloaded-before videos unticked",
                   ticked.get("04 Classes") is False and ticked.get("01 Introduction") is False and ticked.get("02 Variables and types") is True, ticked)
        core.check("and the button then offers Select none", media(core).get("playlistAllSelected") is True)
        core.check("Select all is offered while a video can still be ticked and the count line does not carry the downloaded part",
                   media(core).get("playlistPickable") is True and strings["grabDownloadedCount"].replace("%1", "2") == media(core).get("playlistBefore") and
                   strings["grabDownloadedCount"].replace("%1", "2") not in media(core).get("playlistSummary", ""),
                   (media(core).get("playlistPickable"), media(core).get("playlistBefore"), media(core).get("playlistSummary")))
        core.check("the subtitle choice was cleared after queueing", not any(r.get("selected") for r in core.rows("addSubtitles")))

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
        core.check("both downloaded", statuses == ["COMPLETED", "COMPLETED"], statuses)
        core.check("exactly one notice for the list, counting both videos",
                   len(done_lines) == 1 and "(2 finished, 0 failed)" in done_lines[0] and "Learning Kotlin" in done_lines[0], done_lines)

        print("unavailable videos in a list")
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        core.action("SetUrl", text="https://www.youtube.com/playlist?list=PLgone")
        core.action("ShowAddDialog", show=True)
        core.wait_for("the list", lambda: media(core).get("state", 0) == STATE["PLAYLIST"], 60)
        rows = {r.get("title"): r for r in core.rows("addPlaylist")}
        gone = rows.get("yi50KlsCBio", {})
        core.check("a video with no title and no duration is marked Not available and unticked",
                   bool(gone.get("unavailable")) and not gone.get("selected"), gone)
        core.wait_for("the reads", lambda: not any(r.get("sizeBusy") for r in core.rows("addPlaylist")), 60)
        rows = {r.get("title"): r for r in core.rows("addPlaylist")}
        bad = rows.get("02 Cannot be read", {})
        core.check("a video that gives no quality becomes Not available and unticked", bool(bad.get("unavailable")) and not bad.get("selected"), bad)
        good = rows.get("03 Third", {})
        core.check("a good video stays ticked with its size", bool(good.get("selected")) and bool(good.get("size")) and not good.get("unavailable"), good)
        core.action("SelectAllPlaylist", selected=False)
        core.action("SelectAllPlaylist", selected=True)
        core.pump(0.3)
        rows = {r.get("title"): r for r in core.rows("addPlaylist")}
        core.check("Select all leaves the unavailable ones unticked",
                   not rows["yi50KlsCBio"].get("selected") and not rows["02 Cannot be read"].get("selected") and rows["03 Third"].get("selected"),
                   [(t, r.get("selected")) for t, r in rows.items()])
        core.check("the Not available text exists", bool(strings.get("playlistNotAvailable")))

        print("subtitles in the several-links list")
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        before_ids = {t["id"] for t in core.tasks()}
        core.action("SetUrl", text="https://www.youtube.com/watch?v=fakelist001" + chr(10) + "https://www.youtube.com/watch?v=fakelist002")
        core.action("ShowAddDialog", show=True)
        core.wait_for("the link rows read", lambda: len(core.rows("addLinks")) == 2 and len(core.rows("addSubtitles")) >= 3, 60)
        codes = sorted(r.get("code") for r in core.rows("addSubtitles"))
        core.check("the list offers the videos' languages once each", codes == ["ar", "en", "fr"], codes)
        core.check("the subtitles card shows for the list", media(core).get("subtitlesShown") is True)
        core.action("ToggleSubtitle", code="ar", selected=True)
        core.action("ScheduleQuick", minutes=60)
        core.pump(0.5)
        try:
            core.wait_for("start on", lambda: core.snapshot("add").get("primaryEnabled") is True, 20)
        except CheckFailed:
            pass
        add = core.snapshot("add")
        core.check("the list can start", add.get("primaryEnabled") is True, (add.get("primaryLabel"), add.get("primaryEnabled"), core.rows("addLinks")))
        core.action("StartDownload")
        core.wait_for("two link tasks", lambda: len([t for t in core.tasks() if t["id"] not in before_ids]) == 2, 20)
        added = [t for t in core.tasks() if t["id"] not in before_ids]
        core.check("each link video carries its own Arabic track", all(t.get("subtitles") == "ar" for t in added),
                   [(t.get("fileName"), t.get("subtitles")) for t in added])

        print("a channel link offers Oldest first")
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        core.action("SetUrl", text="https://www.youtube.com/@fakechannel/videos")
        core.action("ShowAddDialog", show=True)
        core.wait_for("the channel list", lambda: media(core).get("state", 0) == STATE["PLAYLIST"], 60)
        core.check("Oldest first is offered for a channel", media(core).get("playlistOldestFirstOffered") is True)
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
    core.finish()


if __name__ == "__main__":
    run()
