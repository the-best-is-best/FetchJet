import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, SERVER, install_fake_ytdlp, server_sha256, sha256_of_file

MODE = {name: index for index, name in enumerate(ENUMS["AddModeCode"])}
LINE = {name: index for index, name in enumerate(ENUMS["LinkLineCode"])}
TAG = {name: index for index, name in enumerate(ENUMS["LinkTagCode"])}


def links(core):
    return core.rows("addLinks")


def run():
    core = Core("links", before_start=install_fake_ytdlp)
    try:
        core.pump(1.0)
        strings = core.strings()
        first = SERVER + "/file/1MB.bin?key=links-a"
        second = SERVER + "/file/2MB.bin?key=links-b"
        video = "https://www.youtube.com/watch?v=fakevideo01"
        broken = "https://www.youtube.com/watch?v=broken01"

        print("several links")
        core.action("ShowAddDialog", show=True)
        core.action("SetUrl", text="\n".join([first, second, video, broken]))
        core.pump(0.5)
        add = core.snapshot("add")
        listed = links(core)
        core.check("the dialog becomes a list of four links", add.get("mode") == MODE["LINK_LIST"] and len(listed) == 4, add.get("mode"))
        core.check("files and videos are tagged", [r.get("tagCode", 0) for r in listed] == [TAG["FILE"], TAG["FILE"], TAG["VIDEO"], TAG["VIDEO"]] and
                   listed[0].get("tag") == strings["linkTagFile"], [r.get("tag") for r in listed])
        core.check("titles and hosts", listed[0].get("title") == "1MB.bin" and listed[0].get("host") == "127.0.0.1:18200" and
                   listed[2].get("host") == "youtube.com", (listed[0].get("title"), listed[0].get("host"), listed[2].get("title")))
        core.check("all selected, the header counts them", all(r.get("selected") for r in listed) and add.get("linkAllSelected") and
                   "4" in add.get("linkSummary", ""), add.get("linkSummary"))
        core.check("no engine choice, file name or checksum in this mode", not add.get("fileNameShown", False) and
                   not add.get("checksumShown", False) and not add.get("connectionsShown", False))

        print("videos are read one by one")
        core.wait_for("the videos to be read", lambda: links(core)[2].get("line") == LINE["QUALITIES"] and
                      links(core)[3].get("line") == LINE["FAILED"], 90)
        listed = links(core)
        print("    " + listed[2].get("title", "") + " : " + " | ".join(listed[2].get("qualities", [])))
        core.check("the video got its title and qualities", listed[2].get("title") == "Big Buck Bunny - sample video" and
                   len(listed[2].get("qualities", [])) >= 2 and listed[2].get("qualityPick", 0) >= 1 and
                   listed[2].get("lineText") == listed[2]["qualities"][listed[2]["qualityPick"] - 1], listed[2])
        add = core.snapshot("add")
        core.check("a video that failed blocks the start", listed[3].get("lineText") == strings["sysFailedToInspectMediaQualities"] and
                   not add.get("primaryEnabled", False), add.get("primaryLabel"))
        other = 1 if listed[2]["qualityPick"] != 1 else 2
        core.action("SetLinkQuality", index=2, quality=listed[2]["qualityIndexes"][other - 1])
        core.pump(0.4)
        core.check("another quality can be picked", links(core)[2].get("qualityPick") == other and
                   links(core)[2].get("lineText") == listed[2]["qualities"][other - 1], links(core)[2].get("lineText"))
        core.action("ToggleLink", index=3, selected=False)
        core.pump(0.4)
        add = core.snapshot("add")
        core.check("unchecking the failed one frees the start", not links(core)[3].get("selected", False) and
                   links(core)[3].get("line", 0) == LINE["NONE"] and add.get("primaryEnabled") and "3" in add.get("linkSummary", "") and
                   not add.get("linkAllSelected", False), add.get("primaryLabel"))

        print("select none / all, folder")
        core.action("SelectAllLinks", selected=False)
        core.pump(0.4)
        add = core.snapshot("add")
        core.check("nothing selected: no start", not any(r.get("selected") for r in links(core)) and not add.get("primaryEnabled", False))
        core.action("ToggleLink", index=0, selected=True)
        core.action("ToggleLink", index=1, selected=True)
        rev = core.snapshot("add").get("linkFolderRev", 0)
        core.action("SetLinkFolder", name="batch one")
        core.pump(0.4)
        add = core.snapshot("add")
        core.check("the two files are selected, folder typed without an echo", "2" in add.get("linkSummary", "") and add.get("primaryEnabled") and
                   add.get("linkFolder") == "batch one" and add.get("linkFolderRev", 0) == rev, add.get("linkSummary"))

        print("start the two files")
        core.action("StartDownload")
        one = core.wait_task(["COMPLETED", "ERROR"], timeout=60, url_part="links-a")
        two = core.wait_task(["COMPLETED", "ERROR"], timeout=60, url_part="links-b")
        core.check("both completed", one["status"] == "COMPLETED" and two["status"] == "COMPLETED", (one["status"], two["status"]))
        core.check("they went into the folder", os.path.basename(os.path.normpath(one["destinationPath"])) == "batch one" and
                   os.path.isfile(os.path.join(one["destinationPath"], one["fileName"])), one["destinationPath"])
        core.check("the 2 MB file is right", sha256_of_file(os.path.join(two["destinationPath"], two["fileName"])) == server_sha256("2MB.bin"))
        core.check("only two downloads were made", len(core.tasks()) == 2, [t["fileName"] for t in core.tasks()])
        core.pump(0.5)
        core.check("the dialog closed and the list is cleared", not core.snapshot("add").get("visible", False) and len(links(core)) == 0)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
