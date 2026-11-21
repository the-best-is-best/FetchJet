import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, SERVER, server_sha256, sha256_of_file

MODE = {name: index for index, name in enumerate(ENUMS["AddModeCode"])}
TAG = {name: index for index, name in enumerate(ENUMS["LinkTagCode"])}
START = SERVER + "/site/start.html"


def links(core):
    return core.rows("addLinks")


def titles(core):
    return sorted(r.get("title", "") for r in links(core))


def reset_url(core, url):
    core.action("SetUrl", text="")
    core.pump(0.3)
    core.action("SetUrl", text=url)
    core.pump(0.3)


def wait_card(core, timeout=15):
    core.wait_for("the page card", lambda: core.snapshot("add").get("grabShown", False), timeout)


def wait_probe(core, timeout=15):
    core.wait_for("the link check", lambda: not core.snapshot("add").get("checkingLink", False), timeout)
    core.pump(0.2)


def grab(core, depth, url=START, wait_rows=True):
    reset_url(core, url)
    wait_card(core)
    core.action("SetGrabDepth", depth=depth)
    core.pump(0.2)
    core.action("GrabPage")
    core.wait_for("the grab to finish", lambda: not core.snapshot("add").get("grabRunning", False) and
                  (len(links(core)) > 0 or core.snapshot("add").get("grabNote", "") != ""), 40)
    core.pump(0.4)


def run():
    core = Core("grab")
    try:
        core.pump(0.5)
        strings = core.strings()

        print("the card")
        core.action("ShowAddDialog", show=True)
        core.action("SetUrl", text=SERVER + "/file/1MB.zip?key=grab-file")
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("a file link: no card", not add.get("grabShown", False), add.get("grabShown"))
        core.action("SetUrl", text="https://www.youtube.com/watch?v=abc")
        core.pump(0.3)
        core.check("a video link: no card", not core.snapshot("add").get("grabShown", False))
        core.action("SetUrl", text=START)
        core.pump(0.1)
        add = core.snapshot("add")
        core.check("a page link: while it is checked there is no card, no engine choice and the checking line shows",
                   not add.get("grabShown", False) and add.get("checkingLink", False) and
                   add.get("engineHidden", False), (add.get("grabShown"), add.get("checkingLink"), add.get("engineHidden")))
        wait_card(core)
        wait_probe(core)
        add = core.snapshot("add")
        core.check("a page link: card with three depths, the first chosen", add.get("grabShown") and not add.get("grabRunning", False) and
                   [d.get("value") for d in add.get("grabDepths", [])] == [1, 2, 3] and
                   [d.get("selected", False) for d in add.get("grabDepths", [])] == [True, False, False] and
                   add["grabDepths"][0].get("label") == strings["grabDepth1"] and add["grabDepths"][2].get("label") == strings["grabDepth3"] and
                   all(strings.get("grabDepthShort" + str(n)) for n in (1, 2, 3)),
                   add.get("grabDepths"))
        core.check("no list yet", add.get("mode") != MODE["LINK_LIST"] and not add.get("linkGrabbed", False))
        core.action("SetGrabDepth", depth=2)
        core.pump(0.2)
        core.check("another depth can be chosen", [d.get("selected", False) for d in core.snapshot("add").get("grabDepths", [])] == [False, True, False])

        core.check("a plain page: the card and no engine choice", add.get("grabShown", False) and add.get("engineHidden", False),
                   (add.get("grabShown"), add.get("engineHidden")))

        print("what the link turns out to be")
        for label, url, card, picker in (
            ("a file with no extension in its link", SERVER + "/site/bundle", False, False),
            ("a file sent as an attachment", SERVER + "/site/download?id=7", False, False),
            ("a page with a video in it", SERVER + "/site/video.html", True, True),
            ("a page", SERVER + "/site/empty.html", True, False),
            ("a page that answers 404", SERVER + "/site/nothing.html", True, True),
        ):
            reset_url(core, url)
            wait_probe(core)
            add = core.snapshot("add")
            core.check(label + ": card " + str(card) + ", engine choice " + str(picker),
                       add.get("grabShown", False) == card and (not add.get("engineHidden", False)) == picker,
                       (add.get("grabShown"), add.get("engineHidden")))
            if picker:
                names = [e.get("engine", 0) for e in add.get("engines", [])]
                core.check(label + ": the choice is Direct and Media only",
                           names == [ENUMS["EngineCode"].index("HTTP_DIRECT"), ENUMS["EngineCode"].index("MEDIA_STREAM")], names)
        reset_url(core, SERVER + "/site/video.html")
        wait_probe(core)
        core.action("SetEngine", engine=ENUMS["EngineCode"].index("MEDIA_STREAM"))
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("the Media engine on a video page: no card, the choice stays, Media is selected",
                   not add.get("grabShown", False) and not add.get("engineHidden", False) and
                   add.get("engine", 0) == ENUMS["EngineCode"].index("MEDIA_STREAM"),
                   (add.get("grabShown"), add.get("engineHidden"), add.get("engine")))
        core.action("SetEngine", engine=ENUMS["EngineCode"].index("HTTP_DIRECT"))
        core.pump(0.3)
        core.check("back to Direct: the card returns", core.snapshot("add").get("grabShown", False))
        reset_url(core, START)
        wait_card(core)

        print("depth 1")
        grab(core, 1)
        add = core.snapshot("add")
        listed = links(core)
        print("    " + ", ".join(titles(core)))
        core.check("the dialog shows a link list of the 5 files of the page", add.get("mode") == MODE["LINK_LIST"] and
                   titles(core) == ["100KB.jpg", "1MB.bin", "2MB.zip", "300KB.png", "report-7.pdf"], titles(core))
        core.check("the card stays for another search, the list is marked as found files", add.get("grabShown") and add.get("linkGrabbed"))
        core.check("the folder is named after the page title", add.get("linkFolder") == "Start", add.get("linkFolder"))
        core.check("the note counts files and pages", add.get("grabNote") == strings["grabFound"].replace("%1", "5").replace("%2", "1"),
                   add.get("grabNote"))
        by_name = {r["title"]: r for r in listed}
        core.check("size and the page host under a name the server told", "1.0 MB" in by_name["1MB.bin"].get("host", "") and
                   "127.0.0.1:18200" in by_name["1MB.bin"].get("host", "") and "6.1 KB" in by_name["report-7.pdf"].get("host", ""),
                   (by_name["1MB.bin"].get("host"), by_name["report-7.pdf"].get("host")))
        core.pump(1.5)
        sized = {r["title"]: r for r in links(core)}
        core.check("the size of a file the page named itself is filled in after the list shows",
                   "2.0 MB" in sized["2MB.zip"].get("host", "") and "127.0.0.1:18200" in sized["2MB.zip"].get("host", ""),
                   sized["2MB.zip"].get("host"))
        core.check("the tag is the file type", by_name["2MB.zip"].get("tag") == "ZIP" and
                   by_name["300KB.png"].get("tag") == "PNG" and by_name["1MB.bin"].get("tag") == "BIN",
                   [r.get("tag") for r in listed])
        core.check("all selected", all(r.get("selected") for r in listed) and add.get("linkAllSelected") and "5" in add.get("linkSummary", ""))
        groups = add.get("grabGroups", [])
        core.check("type chips in order with counts",
                   [g.get("folder") for g in groups] == ["Archives", "Documents", "Images", "Other"] and
                   [g.get("label") for g in groups] == [strings["grabGroupArchives"] + "  1", strings["grabGroupDocuments"] + "  1",
                                                        strings["grabGroupImages"] + "  2", strings["grabGroupOther"] + "  1"] and
                   all(g.get("selected") for g in groups), groups)
        core.check("start is on and counts them", add.get("primaryEnabled") and "5" in add.get("primaryLabel", ""), add.get("primaryLabel"))

        print("a type chip")
        core.action("ToggleGrabGroup", folder="Images", selected=False)
        core.pump(0.4)
        add = core.snapshot("add")
        by_name = {r["title"]: r for r in links(core)}
        core.check("the two images leave the list, the others stay checked",
                   sorted(by_name) == ["1MB.bin", "2MB.zip", "report-7.pdf"] and all(r.get("selected") for r in by_name.values()),
                   [(r["title"], r.get("selected")) for r in links(core)])
        core.check("the chip is off, the others on, the summary counts only the listed files",
                   [g.get("selected", False) for g in add.get("grabGroups", [])] == [True, True, False, True] and
                   "3" in add.get("linkSummary", "") and "5" not in add.get("linkSummary", ""),
                   (add.get("grabGroups"), add.get("linkSummary")))
        core.check("all listed are checked and start counts three", add.get("linkAllSelected") and "3" in add.get("primaryLabel", ""), add.get("primaryLabel"))
        core.action("SelectAllLinks", selected=False)
        core.pump(0.3)
        core.action("SelectAllLinks", selected=True)
        core.pump(0.3)
        core.check("select all does not bring the hidden images back", sorted(r["title"] for r in links(core)) == ["1MB.bin", "2MB.zip", "report-7.pdf"] and
                   "3" in core.snapshot("add").get("primaryLabel", ""), core.snapshot("add").get("primaryLabel"))
        core.action("ToggleGrabGroup", folder="Images", selected=True)
        core.pump(0.3)
        core.check("the chip brings both images back checked", titles(core) == ["100KB.jpg", "1MB.bin", "2MB.zip", "300KB.png", "report-7.pdf"] and
                   all(r.get("selected") for r in links(core)) and all(g.get("selected") for g in core.snapshot("add").get("grabGroups", [])))
        for kind in ("Archives", "Documents", "Images", "Other"):
            core.action("ToggleGrabGroup", folder=kind, selected=False)
        core.pump(0.4)
        add = core.snapshot("add")
        core.check("every chip off: no rows, the chips stay, nothing to start",
                   len(links(core)) == 0 and len(add.get("grabGroups", [])) == 4 and not any(g.get("selected", False) for g in add.get("grabGroups", [])) and
                   not add.get("primaryEnabled", False) and not add.get("linkPickable", False), (add.get("grabGroups"), add.get("primaryEnabled")))
        for kind in ("Archives", "Documents", "Images", "Other"):
            core.action("ToggleGrabGroup", folder=kind, selected=True)
        core.pump(0.4)
        core.check("all chips on again: all five rows are back and checked", len(links(core)) == 5 and all(r.get("selected") for r in links(core)))

        print("depth 2 and 3")
        grab(core, 2)
        core.check("one more page: the song appears, the other site's page is not followed",
                   titles(core) == ["100KB.jpg", "1MB.bin", "2MB.zip", "300KB.png", "3MB.mp3", "report-7.pdf"], titles(core))
        add = core.snapshot("add")
        by_name = {r["title"]: r for r in links(core)}
        core.check("the song is a Music file from the second page",
                   by_name["3MB.mp3"].get("tag") == "MP3" and by_name["3MB.mp3"].get("tagCode") == TAG["AUDIO"] and
                   add.get("grabNote") == strings["grabFound"].replace("%1", "6").replace("%2", "2") and
                   [g.get("folder") for g in add.get("grabGroups", [])] == ["Music", "Archives", "Documents", "Images", "Other"],
                   (add.get("grabNote"), [g.get("folder") for g in add.get("grabGroups", [])]))
        grab(core, 3)
        core.check("two levels: the film appears too",
                   titles(core) == ["100KB.jpg", "1MB.bin", "2MB.zip", "300KB.png", "3MB.mp3", "4MB.mkv", "report-7.pdf"], titles(core))
        core.check("it is video", {r["title"]: r for r in links(core)}["4MB.mkv"].get("tagCode") == TAG["VIDEO"])

        print("start the checked files")
        grab(core, 2)
        core.action("ToggleGrabGroup", folder="Images", selected=False)
        core.action("ToggleGrabGroup", folder="Other", selected=False)
        core.pump(0.4)
        add = core.snapshot("add")
        core.check("three files checked", "3" in add.get("linkSummary", "") and add.get("primaryEnabled"), add.get("linkSummary"))
        core.action("StartDownload")
        zipped = core.wait_task(["COMPLETED", "ERROR"], timeout=60, file_name="2MB.zip")
        pdf = core.wait_task(["COMPLETED", "ERROR"], timeout=60, file_name="report-7.pdf")
        song = core.wait_task(["COMPLETED", "ERROR"], timeout=60, file_name="3MB.mp3")
        core.check("all three completed", zipped["status"] == "COMPLETED" and pdf["status"] == "COMPLETED" and song["status"] == "COMPLETED",
                   (zipped["status"], pdf["status"], song["status"]))
        core.check("they went into the folder named after the page",
                   all(os.path.basename(os.path.normpath(t["destinationPath"])) == "Start" for t in (zipped, pdf, song)),
                   [t["destinationPath"] for t in (zipped, pdf, song)])
        paths = [os.path.join(t["destinationPath"], t["fileName"]) for t in (zipped, pdf, song)]
        core.check("sizes are right", [os.path.getsize(p) if os.path.isfile(p) else -1 for p in paths] == [2 * 1024 * 1024, 6300, 3 * 1024 * 1024],
                   [os.path.getsize(p) if os.path.isfile(p) else -1 for p in paths])
        core.check("the zip content is right", sha256_of_file(paths[0]) == server_sha256("2MB.zip"))
        core.check("only the three were made", len(core.tasks()) == 3, [t["fileName"] for t in core.tasks()])
        ids = {t.get("playlistId", "") for t in (zipped, pdf, song)}
        core.check("the three files belong to one group named after the page", len(ids) == 1 and "" not in ids and next(iter(ids)).startswith("pg_"), ids)
        core.pump(0.5)
        core.check("the dialog closed and the list is cleared", not core.snapshot("add").get("visible", False) and len(links(core)) == 0)
        page_id = next(iter(ids))
        core.action("RowClick", id="pl:" + page_id, control=False, shift=False)
        core.pump(0.6)
        chrome = core.snapshot("chrome")
        core.check("the group page is open and is a page group", chrome.get("playlistOpen") and chrome.get("groupIsPage") and not chrome.get("groupIsFolder"),
                   (chrome.get("playlistOpen"), chrome.get("groupIsPage"), chrome.get("groupIsFolder")))
        core.check("the check button is offered and no subtitle language row is shown", chrome.get("playlistCanCheck") and
                   not chrome.get("playlistLanguagesShown", False), (chrome.get("playlistCanCheck"), chrome.get("playlistLanguagesShown")))
        core.check("the new texts exist", bool(strings.get("grabCheckNew")) and strings["grabCheckNew"] != strings.get("playlistCheckNew"),
                   strings.get("grabCheckNew"))
        core.action("ClosePlaylist")
        core.pump(0.4)

        print("files already downloaded are locked")
        core.action("ShowAddDialog", show=True)
        grab(core, 2)
        by_name = {r["title"]: r for r in links(core)}
        core.check("the three downloaded files are locked and unchecked, the others are free",
                   all(by_name[n].get("rowLocked", False) and not by_name[n].get("selected", False) for n in ("2MB.zip", "report-7.pdf", "3MB.mp3")) and
                   not any(by_name[n].get("rowLocked", False) for n in ("100KB.jpg", "1MB.bin", "300KB.png")),
                   [(r["title"], r.get("rowLocked", False), r.get("selected", False)) for r in links(core)])
        core.check("the counter says 3 downloaded before", strings["grabDownloadedCount"].replace("%1", "3") in core.snapshot("add").get("linkSummary", ""), core.snapshot("add").get("linkSummary"))
        core.action("ToggleLink", index=by_name["2MB.zip"].get("index", 0), selected=True)
        core.pump(0.3)
        core.check("a tap on a locked row changes nothing",
                   not {r["title"]: r for r in links(core)}["2MB.zip"].get("selected", False))
        core.check("the tags show the file types", {r["title"]: r.get("tag") for r in links(core)} ==
                   {"100KB.jpg": "JPG", "1MB.bin": "BIN", "2MB.zip": "ZIP", "300KB.png": "PNG", "report-7.pdf": "PDF", "3MB.mp3": "MP3"},
                   {r["title"]: r.get("tag") for r in links(core)})
        core.check("the colour follows the kind, not the type", all(r.get("tagCode", 0) == (TAG["AUDIO"] if r["title"].endswith(".mp3") else TAG["FILE"]) for r in links(core)))
        core.check("the engine choice is gone once files are listed", core.snapshot("add").get("engineHidden", False))
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)

        print("live rows, stop keeps them")
        core.action("ShowAddDialog", show=True)
        reset_url(core, SERVER + "/site/slow.html")
        wait_card(core)
        wait_probe(core)
        core.check("a plain page before the scan: the card, no engine choice", core.snapshot("add").get("grabShown", False) and
                   core.snapshot("add").get("engineHidden", False))
        core.action("SetGrabDepth", depth=2)
        core.pump(0.2)
        core.action("GrabPage")
        core.wait_for("rows while the crawl runs", lambda: len(links(core)) >= 2, 6)
        add = core.snapshot("add")
        core.check("the rows are there and the crawl is still reading", add.get("grabRunning") and len(links(core)) == 2 and
                   sorted(r["title"] for r in links(core)) == ["1MB.zip", "3MB.mp3"], (add.get("grabRunning"), titles(core)))
        core.check("the tags: ZIP, MP3 (audio colour)", {r["title"]: (r.get("tag"), r.get("tagCode", 0)) for r in links(core)} ==
                   {"1MB.zip": ("ZIP", 0), "3MB.mp3": ("MP3", TAG["AUDIO"])},
                   {r["title"]: (r.get("tag"), r.get("tagCode", 0)) for r in links(core)})
        core.check("no engine choice while reading", add.get("engineHidden", False))
        core.check("Download works while the crawl runs", add.get("primaryEnabled", False), add.get("primaryLabel"))
        core.action("GrabCancel")
        core.pump(0.5)
        add = core.snapshot("add")
        core.check("stopped: the rows stay and the note says so", not add.get("grabRunning", False) and len(links(core)) == 2 and
                   add.get("grabNote") == strings["grabStopped"].replace("%1", "2"), (add.get("grabNote"), len(links(core))))
        core.pump(7.5)
        core.check("the late page does not add its file", len(links(core)) == 2, titles(core))
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)

        print("nothing found")
        core.action("ShowAddDialog", show=True)
        grab(core, 1, SERVER + "/site/empty.html")
        add = core.snapshot("add")
        core.check("the note says so and the card stays for another try", add.get("grabNote") == strings["grabNothing"] and add.get("grabShown") and
                   len(links(core)) == 0 and add.get("mode") != MODE["LINK_LIST"], (add.get("grabNote"), add.get("mode")))

        print("stop while reading")
        reset_url(core, START + "?delay=8000")
        wait_card(core)
        core.action("GrabPage")
        core.pump(0.6)
        add = core.snapshot("add")
        core.check("the card shows the reading text and the count", add.get("grabRunning") and
                   add.get("grabReading") == strings["grabReading"].replace("%1", "0"), add.get("grabReading"))
        core.action("GrabCancel")
        core.pump(0.5)
        add = core.snapshot("add")
        core.check("stopped: back to the choices, no rows, no note", not add.get("grabRunning", False) and add.get("grabShown") and
                   len(links(core)) == 0 and add.get("grabNote", "") == "", add)
        core.pump(8.5)
        core.check("the late page does not fill the list", len(links(core)) == 0 and not core.snapshot("add").get("linkGrabbed", False))

        print("3000 files")
        started = time.time()
        grab(core, 1, SERVER + "/site/big.html")
        took = time.time() - started
        add = core.snapshot("add")
        core.check("the list holds 3000 rows and says it stopped at the limit", len(links(core)) == 3000 and strings["grabLimit"] in add.get("grabNote", ""),
                   (len(links(core)), add.get("grabNote")))
        print("    read and listed in %.1f s" % took)
        core.check("one group: Images, 3000", [(g.get("folder"), g.get("label")) for g in add.get("grabGroups", [])] ==
                   [("Images", strings["grabGroupImages"] + "  3000")], add.get("grabGroups"))
        before = time.time()
        core.action("ToggleLink", index=1500, selected=False)
        core.wait_for("one row to change", lambda: not links(core)[1500].get("selected", False), 10)
        one = time.time() - before
        before = time.time()
        core.action("ToggleGrabGroup", folder="Images", selected=False)
        core.wait_for("all rows to uncheck", lambda: not any(r.get("selected") for r in links(core)), 20)
        many = time.time() - before
        print("    one row %.2f s, the whole type %.2f s" % (one, many))
        core.check("a change in 3000 rows is quick", one < 2.0 and many < 5.0, (one, many))
        core.check("nothing checked: no start", not core.snapshot("add").get("primaryEnabled", False))
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        core.check("closing clears the 3000 rows", len(links(core)) == 0)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
