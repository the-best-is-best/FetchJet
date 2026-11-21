import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, install_fake_ytdlp

STATE = {name: index for index, name in enumerate(ENUMS["MediaStateCode"])}
VIDEO = "https://www.youtube.com/watch?v=fakevideo01"


def media(core):
    return core.snapshot("addMedia") or {}


def page(core):
    return core.snapshot("subtitlesPage") or {}


def details(core):
    return core.snapshot("details") or {}


def row(core, key):
    found = [r for r in page(core).get("rows", []) if r.get("key") == key]
    return found[0] if found else {}


def open_page(core):
    core.action("DetailsSubtitles")
    core.wait_for("the subtitles page to open", lambda: page(core).get("open"), 10)
    core.wait_for("the page to read the video",
                  lambda: not page(core).get("reading", False) and (page(core).get("rows") or page(core).get("message")), 60)
    core.pump(0.3)


def run():
    core = Core("subtitles", before_start=install_fake_ytdlp)
    try:
        core.pump(1.5)
        strings = core.strings()

        print("download a video with its English subtitles next to it as VTT")
        core.action("SetUrl", text=VIDEO)
        core.action("ShowAddDialog", show=True)
        core.wait_for("qualities", lambda: media(core).get("state", 0) == STATE["QUALITIES"], 60)
        rows = core.rows("addQualities")
        low = [r for r in rows if "360" in r.get("label", "")]
        core.check("a 360p row exists", len(low) == 1, rows)
        core.action("SelectAllQualities", selected=False)
        core.action("ToggleQuality", index=low[0]["index"], selected=True)
        core.pump(0.2)
        core.check("no language ticked: the choices are not offered", not media(core).get("subtitlesAnySelected", False), media(core))
        core.action("ToggleSubtitle", code="en", selected=True)
        core.pump(0.2)
        core.check("a language ticked: step one is offered, the format follows only a separate file",
                   media(core).get("subtitlesAnySelected") is True and bool(strings.get("subtitlesEmbedInVideo")) and
                   bool(strings.get("subtitlesSeparateFile")), media(core))
        core.action("SetSubtitleFormat", format="vtt")
        core.pump(0.3)
        core.check("VTT picked: separate file, VTT", not media(core).get("embedSubtitles", False) and media(core).get("subtitleVtt") is True,
                   media(core))
        core.action("SetEmbedSubtitles", embed=True)
        core.pump(0.3)
        core.check("embed picked: VTT is no longer shown as picked", media(core).get("embedSubtitles") is True)
        core.action("SetSubtitleFormat", format="vtt")
        core.pump(0.3)
        core.action("StartDownload")
        task = core.wait_task(["COMPLETED", "ERROR"], timeout=120, url_part="media-360")
        core.check("the video download completed", task["status"] == "COMPLETED", task)
        folder = task["destinationPath"]
        base = os.path.splitext(task["fileName"])[0]
        print("    saved as " + task["fileName"])
        core.wait_for("the subtitle step to end",
                      lambda: os.path.isfile(os.path.join(folder, base + ".en.vtt")) and
                      not any(n.startswith(".fjsubs_") for n in os.listdir(folder)), 30)
        core.check("English subtitles were saved next to the video", os.path.isfile(os.path.join(folder, base + ".en.vtt")),
                   os.listdir(folder))
        core.check("no Arabic file yet", not os.path.isfile(os.path.join(folder, base + ".ar.vtt")))
        core.check("the temporary subtitle folder is gone", not any(n.startswith(".fjsubs_") for n in os.listdir(folder)),
                   os.listdir(folder))

        print("the details button")
        core.action("RowClick", id=task["id"], control=False, shift=False)
        core.pump(0.6)
        core.check("a finished video offers adding subtitles", details(core).get("canSubtitles") is True, details(core))
        core.check("the page is closed at start", not page(core).get("open", False))

        print("open the page")
        open_page(core)
        p = page(core)
        keys = [r.get("key") for r in p.get("rows", [])]
        print("    rows: " + ", ".join("%s%s" % (r.get("name"), " (there)" if r.get("present") else "") for r in p.get("rows", [])))
        core.check("the three tracks of the video are listed", sorted(keys) == ["ar", "en", "fr.auto"], keys)
        core.check("English is marked as already there and cannot be changed",
                   row(core, "en").get("present") and row(core, "en").get("checked") and not row(core, "en").get("enabled"), row(core, "en"))
        core.check("Arabic can be picked", row(core, "ar").get("enabled") and not row(core, "ar").get("checked"), row(core, "ar"))
        core.check("the note says the files go next to the video as .vtt", p.get("note") == strings["subtitlesGoBeside"].replace(".srt", ".vtt"), p.get("note"))
        core.check("nothing new picked: Add is off", not p.get("canAdd", False))

        print("add Arabic")
        core.action("SubtitlesToggle", key="ar", selected=True)
        core.pump(0.3)
        core.check("Add is on after picking Arabic", page(core).get("canAdd") is True, page(core))
        core.action("SubtitlesToggle", key="en", selected=False)
        core.pump(0.3)
        core.check("English stays as it was", row(core, "en").get("checked") is True, row(core, "en"))
        core.action("SubtitlesAdd")
        core.wait_for("adding to end", lambda: not page(core).get("running", False) and page(core).get("added", "") != "", 60)
        p = page(core)
        print("    " + p.get("added", ""))
        core.check("the page says Arabic was added", "Arabic" in p.get("added", ""), p.get("added"))
        core.check("no refused or failed language", p.get("refused", "") == "" and p.get("failed", "") == "", p)
        core.check("the Arabic file is next to the video", os.path.isfile(os.path.join(folder, base + ".ar.vtt")), os.listdir(folder))
        core.check("Arabic is now marked as there", row(core, "ar").get("present") is True, row(core, "ar"))
        core.check("nothing left to add: Add is off", not p.get("canAdd", False))

        print("open it again")
        core.action("SubtitlesDismiss")
        core.wait_for("the page to close", lambda: not page(core).get("open", False), 10)
        open_page(core)
        core.check("English and Arabic are both there after reopening",
                   row(core, "en").get("present") and row(core, "ar").get("present") and not row(core, "fr.auto").get("present"),
                   page(core).get("rows"))
        core.action("SubtitlesDismiss")
        core.wait_for("the page to close again", lambda: not page(core).get("open", False), 10)

        print("the row menu")
        core.action("RowMenu", id=task["id"])
        core.pump(0.3)
        labels = [r.get("label", "") for r in core.rows("rowMenu")]
        core.check("the menu of a finished video offers adding subtitles", strings["subtitlesAddButton"] in labels, labels)
        pick = [r for r in core.rows("rowMenu") if r.get("label") == strings["subtitlesAddButton"]]
        core.action("RowMenuPick", key=pick[0].get("key", 0) if pick else -1)
        core.wait_for("the page to open from the menu", lambda: page(core).get("open"), 10)
        core.check("the menu closes after the pick", core.rows("rowMenu") == [])
        core.action("SubtitlesDismiss")
        core.wait_for("the page to close after the menu", lambda: not page(core).get("open", False), 10)

        print("a file that is not a video")
        core.debug("addDirect", {"url": "http://127.0.0.1:18200/file/1MB.bin?key=subs-plain", "fileName": "plain.bin"})
        plain = core.wait_task("COMPLETED", timeout=40, file_name="plain.bin")
        core.action("RowClick", id=plain["id"], control=False, shift=False)
        core.pump(0.6)
        core.check("a plain file does not offer subtitles", not details(core).get("canSubtitles", False), details(core))
        core.action("RowMenu", id=plain["id"])
        core.pump(0.3)
        labels = [r.get("label", "") for r in core.rows("rowMenu")]
        core.check("nor does its row menu", strings["subtitlesAddButton"] not in labels, labels)
        core.action("RowMenuClose")
        core.pump(0.2)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
