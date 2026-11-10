import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, install_fake_ytdlp, server_sha256, sha256_of_file

STATE = {name: index for index, name in enumerate(ENUMS["MediaStateCode"])}
ENGINE = {name: index for index, name in enumerate(ENUMS["EngineCode"])}
FIELD = {name: index for index, name in enumerate(ENUMS["ClipPickerField"])}
CHOICE = {name: index for index, name in enumerate(ENUMS["ChoiceDialogKind"])}
VIDEO = "https://www.youtube.com/watch?v=fakevideo01"


def media(core):
    return core.snapshot("addMedia") or {}


def run():
    core = Core("media", before_start=install_fake_ytdlp)
    try:
        core.pump(1.5)
        strings = core.strings()
        state = core.debug("state")
        core.check("the media engine is seen as installed", state["ytDlpInstalled"], state["ytDlpStatus"])

        print("inspect a video")
        core.action("SetUrl", text=VIDEO)
        core.action("ShowAddDialog", show=True)
        core.pump(0.2)
        core.check("media engine picked for the link", core.snapshot("add").get("engine", 0) == ENGINE["MEDIA_STREAM"])
        core.wait_for("qualities", lambda: media(core).get("state", 0) == STATE["QUALITIES"], 60)
        m = media(core)
        rows = core.rows("addQualities")
        print("    " + " | ".join(r.get("label", "") for r in rows))
        core.check("qualities are listed", len(rows) >= 2, rows)
        core.check("title of the video is the card title", m.get("qualitiesTitle") == "Big Buck Bunny - sample video", m.get("qualitiesTitle"))
        core.check("one quality is preselected and counted", any(r.get("selected") for r in rows) and m.get("qualitiesCount", "") != "", m.get("qualitiesCount"))
        subs = core.rows("addSubtitles")
        core.check("subtitles listed, auto ones marked", m.get("subtitlesShown") and len(subs) == 3 and
                   any(s.get("automatic") for s in subs), subs)
        core.check("clip is offered (duration known)", m.get("clipAvailable") and not m.get("clipEnabled", False), m)
        add = core.snapshot("add")
        core.check("start is possible", add.get("primaryEnabled") and add.get("primaryLabel") == strings["addStartDownload"], add.get("primaryLabel"))
        core.check("file name comes from the title", "Big Buck Bunny" in add.get("fileName", ""), add.get("fileName"))

        print("clip")
        core.action("SetClipEnabled", on=True)
        core.pump(0.3)
        m = media(core)
        core.check("clip on: full range filled in", m.get("clipEnabled") and m.get("clipStart") == "00:00" and
                   m.get("clipEnd") == "00:10:34", (m.get("clipStart"), m.get("clipEnd")))
        core.check("quick chips fit the 10 minute video", [c.get("end") for c in m.get("clipChips", [])] == ["00:10:34", "00:05:00"],
                   m.get("clipChips"))
        core.action("SetClipRange", start="00:08:00", end="00:05:00")
        core.pump(0.3)
        m = media(core)
        add = core.snapshot("add")
        core.check("start after end is an error and blocks the start", m.get("clipStartError") and m.get("clipError", "") != "" and
                   not add.get("primaryEnabled", False) and add.get("primaryLabel") == strings["invalidTimeRange"], add.get("primaryLabel"))
        core.action("OpenClipPicker", end=True)
        core.pump(0.3)
        picker = core.snapshot("clipPicker")
        core.check("end picker opens with the end time", picker.get("open") and picker.get("minutes") == "05" and
                   not picker.get("valid", False), picker)
        core.action("ClipPickerQuick", code=4)
        core.pump(0.3)
        picker = core.snapshot("clipPicker")
        core.check("end of video chip", picker.get("minutes") == "10" and picker.get("seconds") == "34" and picker.get("valid"), picker)
        core.action("ClipPickerStep", field=FIELD["MINUTES"], up=False)
        core.action("ClipPickerType", field=FIELD["SECONDS"], text="75")
        core.pump(0.3)
        picker = core.snapshot("clipPicker")
        core.check("arrow down and typed seconds are clamped", picker.get("minutes") == "09" and picker.get("seconds") == "59", picker)
        core.action("ClipPickerApply")
        core.pump(0.3)
        m = media(core)
        core.check("picker result is applied", not core.snapshot("clipPicker").get("open", False) and m.get("clipEnd") == "00:09:59" and
                   m.get("clipError", "") == "", m.get("clipEnd"))
        core.action("SetClipEnabled", on=False)

        print("download the 360p file with the app's own engine")
        core.action("SelectAllQualities", selected=False)
        low = [r for r in rows if "360" in r.get("label", "")]
        core.check("a 360p row exists", len(low) == 1, rows)
        core.action("ToggleQuality", index=low[0]["index"], selected=True)
        core.pump(0.3)
        core.check("one quality selected", [r.get("selected", False) for r in core.rows("addQualities")].count(True) == 1)
        core.action("StartDownload")
        task = core.wait_task(["COMPLETED", "ERROR"], timeout=90, url_part="media-360")
        core.check("media download completed", task["status"] == "COMPLETED", task)
        path = os.path.join(task["destinationPath"], task["fileName"])
        core.check("the file is the server's 20 MB file", os.path.isfile(path) and sha256_of_file(path) == server_sha256("20MB.bin"), path)
        core.check("it is a media task with the page link kept", task["engine"] == "MEDIA_STREAM", task["engine"])
        print("    saved as " + task["fileName"])

        print("the same link again")
        core.action("SetUrl", text=VIDEO)
        core.action("ShowAddDialog", show=True)
        core.wait_for("qualities again", lambda: media(core).get("state", 0) == STATE["QUALITIES"], 60)
        core.action("SelectAllQualities", selected=False)
        core.action("ToggleQuality", index=low[0]["index"], selected=True)
        core.action("StartDownload")
        core.wait_for("a question about the existing download or file", lambda: len(core.rows("choices")) > 0, 20)
        choice = core.rows("choices")[0]
        print("    dialog: " + choice.get("title", ""))
        core.check("the dialog has three answers", choice.get("primaryLabel") and choice.get("secondaryLabel") and choice.get("cancelLabel"), choice)
        core.action("ChoiceDismiss", kind=choice.get("kind", 0))
        core.pump(0.5)
        core.check("dismiss closes it", len(core.rows("choices")) == 0)
        core.action("ShowAddDialog", show=False)

        print("playlist")
        core.action("SetUrl", text="https://www.youtube.com/playlist?list=PLfake")
        core.action("ShowAddDialog", show=True)
        core.wait_for("playlist", lambda: media(core).get("state", 0) == STATE["PLAYLIST"], 60)
        m = media(core)
        entries = core.rows("addPlaylist")
        core.check("four videos, all selected", len(entries) == 4 and all(e.get("selected") for e in entries) and
                   m.get("playlistAllSelected"), entries)
        core.check("title, folder and durations", m.get("playlistTitle") == "Learning Kotlin - full course" and
                   m.get("playlistFolder") == m.get("playlistTitle") and entries[3].get("duration") == "1:06:50", m)
        core.check("an Arabic title is flagged", entries[2].get("titleRtl") is True)
        add = core.snapshot("add")
        core.check("button counts the videos", "4" in add.get("primaryLabel", "") and add.get("primaryEnabled"), add.get("primaryLabel"))
        core.action("TogglePlaylistEntry", index=1, selected=False)
        core.action("SetPlaylistQuality", quality=5)
        core.pump(0.3)
        m = media(core)
        core.check("audio quality offers the audio format choice", m.get("audioShown") and len(m.get("audioFormats", [])) >= 2, m.get("audioFormats"))
        core.check("three videos left", "3" in core.snapshot("add").get("primaryLabel", ""))
        core.action("ShowAddDialog", show=False)

        print("a link that fails")
        core.action("SetUrl", text="https://www.youtube.com/watch?v=broken01")
        core.action("ShowAddDialog", show=True)
        core.wait_for("inspection error", lambda: media(core).get("state", 0) == STATE["ERROR"], 90)
        core.pump(0.4)
        add = core.snapshot("add")
        core.check("error is shown with retry", media(core).get("error", "") != "" and add.get("showRetry") and
                   not add.get("primaryEnabled", False), add.get("error"))
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
