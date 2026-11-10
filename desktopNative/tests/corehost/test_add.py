import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, SERVER, server_sha256, server_stats, sha256_of_file

ENGINE = {name: index for index, name in enumerate(ENUMS["EngineCode"])}


def run():
    core = Core("add")
    try:
        core.pump(0.5)
        strings = core.strings()
        add = core.snapshot("add")
        core.check("dialog is closed at start", not add.get("visible", False), add)

        print("empty dialog")
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("dialog opens", add.get("visible"))
        core.check("no link: the button asks for one and is off", add.get("primaryLabel") == strings["addEnterUrl"] and
                   not add.get("primaryEnabled", False), add.get("primaryLabel"))
        core.check("engines: direct and media, no torrent yet",
                   [e.get("engine", 0) for e in add.get("engines", [])] == [ENGINE["HTTP_DIRECT"], ENGINE["MEDIA_STREAM"]],
                   add.get("engines"))
        core.check("start now is the default", add.get("scheduleNow") and not add.get("scheduleAtTime", False))
        core.check("three quick folders", len(add.get("quickFolders", [])) == 3)
        core.check("destination is the downloads folder",
                   os.path.normcase(add.get("destination", "")) == os.path.normcase(os.path.normpath(core.downloads)), add.get("destination"))

        print("typing a link")
        url = SERVER + "/file/6MB.bin?key=add-a&throttle=400"
        first_rev = add.get("urlRev", 0)
        core.action("SetUrl", text=url)
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("typed text is not echoed back", add.get("urlRev", 0) == first_rev and add.get("url") == url, add.get("urlRev"))
        core.check("direct engine, connections and checksum shown", add.get("engine", 0) == ENGINE["HTTP_DIRECT"] and
                   add.get("connectionsShown") and add.get("checksumShown"))
        core.check("file name suggested", add.get("fileNameShown") and add.get("fileName") == "6MB.bin", add.get("fileName"))
        core.check("button says start and is on", add.get("primaryLabel") == strings["addStartDownload"] and add.get("primaryEnabled"),
                   add.get("primaryLabel"))

        print("pasted text with one link keeps only the link")
        core.action("SetUrl", text="Look at this file " + url + " it is great")
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("only the link stays and the field is told to take it", add.get("url") == url and add.get("urlRev", 0) > first_rev,
                   (add.get("url"), add.get("urlRev")))

        print("file name")
        name_rev = add.get("fileNameRev", 0)
        core.action("SetFileName", name="my file.bin")
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("typed name kept, reset offered", add.get("fileName") == "my file.bin" and add.get("fileNameCanReset") and
                   add.get("fileNameRev", 0) == name_rev, add)
        core.action("ResetFileName")
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("reset brings the suggestion back", add.get("fileName") == "6MB.bin" and add.get("fileNameRev", 0) > name_rev and
                   not add.get("fileNameCanReset", False), add.get("fileName"))

        print("schedule")
        core.action("ScheduleQuick", minutes=30)
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("in 30 minutes", add.get("scheduleStartsAt", "") != "" and add.get("primaryLabel") == strings["addScheduleDownload"],
                   add.get("scheduleStartsAt"))
        core.action("ScheduleAtTime")
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("time fields open with the current time", add.get("scheduleAtTime") and len(add.get("scheduleHour", "")) == 2 and
                   len(add.get("scheduleMinute", "")) == 2, add)
        core.action("SetScheduleHour", text="1x2")
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("hour keeps digits only", add.get("scheduleHour") == "12", add.get("scheduleHour"))
        core.action("SetScheduleDay", offset=1)
        core.action("ApplyScheduleTime")
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("a time tomorrow is accepted", add.get("scheduleError", "") == "" and
                   any(d.get("selected") and d.get("value", 0) == 1 for d in add.get("scheduleDays", [])), add.get("scheduleError"))
        core.action("ScheduleQuick", minutes=0)
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("back to now", add.get("scheduleNow") and add.get("scheduleStartsAt", "") == "")

        print("connections and folders")
        core.action("SetConnections", count=8)
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("8 connections chosen", any(c.get("selected") and c.get("value", 0) == 8 for c in add.get("connections", [])))
        sub = os.path.join(core.downloads, "sub folder")
        os.makedirs(sub, exist_ok=True)
        dest_rev = add.get("destinationRev", 0)
        core.action("SetQuickFolder", path=sub)
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("quick folder replaces the destination text", os.path.normcase(add.get("destination", "")) == os.path.normcase(sub) and
                   add.get("destinationRev", 0) > dest_rev, add.get("destination"))

        print("start")
        core.action("StartDownload")
        task = core.wait_task("COMPLETED", timeout=60, url_part="key=add-a")
        core.pump(0.5)
        add = core.snapshot("add")
        core.check("dialog closed after the start", not add.get("visible", False))
        path = os.path.join(task["destinationPath"], task["fileName"])
        core.check("saved in the chosen folder", os.path.normcase(os.path.dirname(path)) == os.path.normcase(sub), path)
        core.check("content is right", os.path.isfile(path) and sha256_of_file(path) == server_sha256("6MB.bin"))
        most = server_stats("add-a").get("maxActive", 0)
        core.check("the chosen 8 connections were used", most >= 6, most)

        print("engine choice")
        core.action("SetUrl", text=SERVER + "/file/1MB.bin?key=add-b")
        core.action("ShowAddDialog", show=True)
        core.action("SetEngine", engine=ENGINE["MEDIA_STREAM"])
        core.pump(0.5)
        add = core.snapshot("add")
        core.check("media engine: no connections, no checksum", add.get("engine", 0) == ENGINE["MEDIA_STREAM"] and
                   not add.get("connectionsShown", False) and not add.get("checksumShown", False), add)
        core.check("media needs an inspection first", not add.get("primaryEnabled", False), add.get("primaryLabel"))
        core.action("SetEngine", engine=ENGINE["HTTP_DIRECT"])
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        core.check("dialog closes", not core.snapshot("add").get("visible", False))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
    core.finish()


if __name__ == "__main__":
    run()
