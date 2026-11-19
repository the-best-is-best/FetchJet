import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, install_fake_ytdlp

STATE = {name: index for index, name in enumerate(ENUMS["MediaStateCode"])}
KIND = {name: index for index, name in enumerate(ENUMS["RowKind"])}
LIST = "https://www.youtube.com/playlist?list=PLgrow"
TITLE = "Learning Kotlin - full course"


def media(core):
    return core.snapshot("addMedia") or {}


def rows(core, kind):
    return [r for r in core.rows("tasks") if r.get("kind", 0) == KIND[kind]]


def tasks(core):
    return core.tasks()


def wait_list(core):
    core.wait_for("the playlist", lambda: media(core).get("state", 0) == STATE["PLAYLIST"], 60)
    core.pump(0.5)


def run():
    core = Core("playlist_group", before_start=install_fake_ytdlp)
    grown = os.path.join(core.home, "tools", "yt-dlp-dir", "playlist-grown")
    try:
        core.pump(1.0)
        strings = core.strings()

        print("a playlist of three videos for later: one row in the list")
        core.action("SetUrl", text=LIST)
        core.action("ShowAddDialog", show=True)
        wait_list(core)
        core.action("TogglePlaylistEntry", index=3, selected=False)
        core.action("SetPlaylistQuality", quality=4)
        core.action("ScheduleQuick", minutes=60)
        core.pump(0.5)
        core.action("StartDownload")
        core.wait_for("three tasks", lambda: len(tasks(core)) == 3, 20)
        core.pump(1.0)
        ids = {t.get("playlistId") for t in tasks(core)}
        core.check("the three videos share one playlist id", len(ids) == 1 and "" not in ids, ids)
        playlist_id = next(iter(ids))
        groups = rows(core, "GROUP")
        core.check("the list shows one playlist row and no video rows",
                   len(groups) == 1 and not rows(core, "TASK"), (groups, rows(core, "TASK")))
        group = groups[0] if groups else {}
        core.check("the row is named after the playlist and counts its videos",
                   group.get("name") == TITLE and "0" in group.get("stateLine", "") and "3" in group.get("stateLine", ""),
                   group)
        core.check("its id is the playlist row id", group.get("id") == "pl:" + playlist_id, group.get("id"))

        print("search finds the playlist by its title")
        core.action("SetSearch", text="Kotlin - full")
        core.pump(0.6)
        core.check("the playlist row is found", len(rows(core, "GROUP")) == 1, core.rows("tasks"))
        core.action("SetSearch", text="")
        core.pump(0.4)

        print("open it: its videos as rows")
        core.action("RowClick", id="pl:" + playlist_id, control=False, shift=False)
        core.pump(0.6)
        chrome = core.snapshot("chrome")
        core.check("the playlist page is open with its title", chrome.get("playlistOpen") and chrome.get("title") == TITLE, chrome.get("title"))
        video_rows = rows(core, "TASK")
        core.check("three video rows, no playlist row", len(video_rows) == 3 and not rows(core, "GROUP"), [r.get("name") for r in video_rows])

        print("pause all, then resume all")
        core.action("PlaylistPauseAll")
        core.wait_for("all paused", lambda: all(t.get("status") == "PAUSED" for t in tasks(core)), 20)
        core.check("every video is paused", all(t.get("status") == "PAUSED" for t in tasks(core)), [t.get("status") for t in tasks(core)])
        core.pump(0.6)
        core.check("the page offers resume", core.snapshot("chrome").get("playlistCanResume"))
        core.action("PlaylistResumeAll")
        core.wait_for("all done", lambda: all(t.get("status") == "COMPLETED" for t in tasks(core)), 120)
        core.pump(1.5)
        core.check("every video downloaded", all(t.get("status") == "COMPLETED" for t in tasks(core)), [t.get("status") for t in tasks(core)])
        log = core.log_text()
        done_lines = [line for line in log.splitlines() if "Playlist done:" in line]
        core.check("one notice for the playlist", len(done_lines) == 1 and "(3 finished, 0 failed)" in done_lines[0], done_lines)
        core.action("ClosePlaylist")
        core.pump(0.6)
        group = (rows(core, "GROUP") or [{}])[0]
        core.check("back in the list the row says 3 of 3 and is complete",
                   group.get("completed") and "3" in group.get("stateLine", ""), group)

        print("check for new videos: the list grew by two")
        open(grown, "w").close()
        core.action("OpenPlaylist", id=playlist_id)
        core.pump(0.4)
        core.action("PlaylistCheck")
        wait_list(core)
        entries = core.rows("addPlaylist")
        by_title = {e.get("title"): e for e in entries}
        core.check("six videos listed", len(entries) == 6, [e.get("title") for e in entries])
        old = [by_title.get(t, {}) for t in ("01 Introduction", "02 Variables and types", "03 الدوال والتعابير")]
        core.check("the three in the playlist are marked and unticked",
                   all(e.get("inPlaylist") and not e.get("selected") for e in old), old)
        new = [by_title.get(t, {}) for t in ("04 Classes", "05 Collections", "06 Coroutines")]
        core.check("the others are ticked", all(e.get("selected") and not e.get("inPlaylist") for e in new), new)
        core.action("TogglePlaylistEntry", index=0, selected=True)
        core.pump(0.3)
        core.check("a video already in it cannot be ticked", not core.rows("addPlaylist")[0].get("selected"))
        core.action("ScheduleQuick", minutes=60)
        core.pump(0.4)
        core.action("StartDownload")
        core.wait_for("six tasks", lambda: len(tasks(core)) == 6, 20)
        ids = {t.get("playlistId") for t in tasks(core)}
        core.check("the new videos joined the same playlist", ids == {playlist_id}, ids)
        folders = {t.get("destinationPath") for t in tasks(core)}
        core.check("in the same folder", len(folders) == 1, folders)
        core.pump(0.6)
        chrome = core.snapshot("chrome")
        core.check("the page counts six videos", "6" in chrome.get("playlistSummary", ""), chrome.get("playlistSummary"))

        print("add a subtitle language to the playlist")
        core.action("PlaylistReadLanguages")
        core.wait_for("languages", lambda: len(core.snapshot("chrome").get("playlistLanguageChoices", [])) >= 2, 60)
        choices = [c.get("value") for c in core.snapshot("chrome").get("playlistLanguageChoices", [])]
        core.check("the videos' languages are offered", "ar" in choices and "en" in choices, choices)
        core.action("PlaylistAddLanguage", code="ar")
        core.wait_for("added", lambda: not core.snapshot("chrome").get("playlistLanguagesBusy") and
                      core.snapshot("chrome").get("playlistLanguagesNote", "") != "", 120)
        core.pump(1.0)
        finished = [t for t in tasks(core) if t.get("status") == "COMPLETED"]
        waiting = [t for t in tasks(core) if t.get("status") != "COMPLETED"]
        core.check("each finished video got the Arabic track", finished and all("ar" in (t.get("subtitles") or "") for t in finished),
                   [(t.get("fileName"), t.get("subtitles")) for t in finished])
        core.check("each waiting video will fetch it when it downloads", waiting and all("ar" in (t.get("subtitleLanguages") or "") for t in waiting),
                   [(t.get("fileName"), t.get("subtitleLanguages")) for t in waiting])
        chrome = core.snapshot("chrome")
        core.check("the page names the playlist's language", "Arabic" in chrome.get("playlistSubtitles", "") or "ar" in chrome.get("playlistSubtitles", ""),
                   chrome.get("playlistSubtitles"))
        core.check("the note says how many got it", chrome.get("playlistLanguagesNote", "") != "", chrome.get("playlistLanguagesNote"))

        print("delete the playlist")
        core.action("PlaylistRemove")
        core.pump(0.4)
        core.action("ConfirmRemove", deleteFiles=True)
        core.wait_for("no tasks", lambda: len(tasks(core)) == 0, 30)
        core.pump(1.0)
        core.check("every video is gone", len(tasks(core)) == 0)
        core.check("the page closed and no playlist row is left",
                   not core.snapshot("chrome").get("playlistOpen") and not rows(core, "GROUP"))
        stores = [os.path.join(b, f) for b, _, fs in os.walk(core.home) for f in fs if f == "playlists.json"]
        text = open(stores[0], encoding="utf-8").read() if stores else "[]"
        core.check("the playlist store file exists", bool(stores))
        core.check("the saved playlist record is removed", playlist_id not in text, text[:200])
        core.check("the texts exist", bool(strings.get("playlistCheckNew")) and bool(strings.get("playlistGroupDone")))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
