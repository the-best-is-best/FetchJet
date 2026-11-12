import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, PROTOCOL, SERVER

KIND_HEADER = ENUMS["RowKind"].index("HEADER")
KIND_TASK = ENUMS["RowKind"].index("TASK")
FILTER = {name: index for index, name in enumerate(ENUMS["SideFilterCode"])}
KEY = {name: index for index, name in enumerate(ENUMS["ListKeyCode"])}
ARABIC_NAME = "تقرير المبيعات.pdf"


def task_rows(core):
    return [r for r in core.rows("tasks") if r.get("kind", 0) == KIND_TASK]


def header_rows(core):
    return [r for r in core.rows("tasks") if r.get("kind", 0) == KIND_HEADER]


def run():
    core = Core("home")
    try:
        core.pump(0.6)
        strings = core.strings()
        core.check("tr channel has every string",
                   len(strings) == len(PROTOCOL["tr"]) and strings.get("homeAddDownload") == "Add download",
                   (len(strings), strings.get("homeAddDownload")))
        shell = core.snapshot("shell")
        core.check("window title", shell.get("windowTitle", "").startswith("FetchJet ") and " - " in shell.get("windowTitle", ""), shell)
        chrome = core.snapshot("chrome")
        core.check("empty list state", chrome.get("listEmpty") and chrome.get("emptyTitle") == strings["homeNoTasksInView"], chrome)
        core.check("six state filters, files in type filters", len(chrome.get("navState", [])) == 6 and
                   any(e.get("filter", 0) == FILTER["FILES"] for e in chrome.get("navType", [])), chrome.get("navType"))
        core.check("torrents hidden while the backend is missing",
                   not any(e.get("filter", 0) == FILTER["TORRENTS"] for e in chrome.get("navType", [])))
        core.check("title is the filter label", chrome.get("title") == strings["homeFilterAllDownloads"], chrome.get("title"))
        status = core.snapshot("status")
        core.check("status bar texts", status.get("version", "").startswith("v") and "off" in status.get("limit", "") and
                   status.get("summary", "").startswith("0 "), status)

        print("three downloads: one finished, one running slowly, one paused")
        core.debug("addDirect", {"url": SERVER + "/file/1MB.bin?key=home-a", "fileName": "alpha.zip"})
        core.wait_task("COMPLETED", timeout=40, file_name="alpha.zip")
        core.debug("addDirect", {"url": SERVER + "/file/40MB.bin?key=home-b&throttle=300", "fileName": ARABIC_NAME})
        core.wait_task("DOWNLOADING", timeout=40, file_name=ARABIC_NAME)
        core.debug("addDirect", {"url": SERVER + "/file/30MB.bin?key=home-c&throttle=300", "fileName": "movie.mkv"})
        third = core.wait_task("DOWNLOADING", timeout=40, file_name="movie.mkv")
        core.wait_for("bytes on the third", lambda: (core.task(task_id=third["id"]) or {}).get("downloadedBytes", 0) > 100 * 1024, 30)
        core.action("Pause", id=third["id"])
        core.wait_task("PAUSED", timeout=20, task_id=third["id"])
        core.pump(0.8)

        headers = header_rows(core)
        core.check("three group headers: run, stop, done", [h.get("headerKey") for h in headers] == ["run", "stop", "done"],
                   [h.get("headerKey") for h in headers])
        core.check("headers carry counts", all(h.get("headerCount") == 1 for h in headers), headers)
        by_name = {r.get("name"): r for r in task_rows(core)}
        core.check("three task rows", set(by_name) == {"alpha.zip", ARABIC_NAME, "movie.mkv"}, list(by_name))
        arabic = by_name.get(ARABIC_NAME, {})
        core.check("Arabic name is flagged right-to-left", arabic.get("nameRtl") is True and not by_name["alpha.zip"].get("nameRtl", False))
        core.check("running row: percent, speed and action", "%" in arabic.get("stateLine", "") and
                   arabic.get("speed", "—") != "—" and
                   arabic.get("action", 0) == ENUMS["RowActionCode"].index("PAUSE"), arabic)
        core.check("tags", by_name["alpha.zip"].get("tag") == "ZIP" and by_name["movie.mkv"].get("tag") == "VID" and
                   arabic.get("tag") == "PDF", [r.get("tag") for r in by_name.values()])
        done = by_name["alpha.zip"]
        core.check("completed row", done.get("completed") and done.get("percent") == "100%" and
                   done.get("action", 0) == ENUMS["RowActionCode"].index("OPEN") and
                   done.get("stateLine", "").startswith("Completed"), done)
        paused = by_name["movie.mkv"]
        core.check("paused row", paused.get("stateLine", "").startswith("Paused") and
                   paused.get("barColor", 0) == ENUMS["BarColorCode"].index("PAUSED") and
                   paused.get("action", 0) == ENUMS["RowActionCode"].index("RESUME"), paused)
        chrome = core.snapshot("chrome")
        counts = {e.get("filter", 0): e.get("count", 0) for e in chrome["navState"]}
        core.check("sidebar counts", counts.get(FILTER["ALL"]) == 3 and counts.get(FILTER["DOWNLOADING"]) == 1 and
                   counts.get(FILTER["PAUSED"]) == 1 and counts.get(FILTER["COMPLETED"]) == 1, counts)
        core.check("toolbar flags", chrome.get("showToolbar") and chrome.get("anyPausable") and chrome.get("anyResumable"), chrome)
        status = core.snapshot("status")
        core.check("status bar counts one running download", status.get("summary", "").startswith("1 "), status)
        core.check("total speed is shown", "KB/s" in status.get("downSpeed", "") or "MB/s" in status.get("downSpeed", ""), status)

        print("fixture for screenshots")
        fixture = os.path.join(core.work, "home-fixture.json")
        core.fixture(fixture)
        core.check("fixture written", os.path.getsize(fixture) > 5000)

        print("selection and details")
        core.action("RowClick", id=done["id"], control=False, shift=False)
        core.pump(0.2)
        core.check("plain click opens details", core.snapshot("chrome").get("detailsOpen") and
                   any(r.get("selected") for r in task_rows(core) if r["id"] == done["id"]))
        core.action("RowClick", id=arabic["id"], control=True, shift=False)
        core.pump(0.2)
        chrome = core.snapshot("chrome")
        core.check("ctrl click starts the selection", chrome.get("selectionMode") and chrome.get("selectedCountText", "").startswith("1"), chrome)
        core.action("RowClick", id=done["id"], control=False, shift=True)
        core.pump(0.2)
        checked = [r["name"] for r in task_rows(core) if r.get("checked")]
        core.check("shift click checks the range", len(checked) == 3, checked)
        core.check("all checked flag", core.snapshot("chrome").get("allChecked"))
        core.action("ListKey", key=KEY["ESCAPE"])
        core.pump(0.2)
        core.check("escape clears the selection", not core.snapshot("chrome").get("selectionMode", False))
        core.action("ListKey", key=KEY["SELECT_ALL"])
        core.pump(0.2)
        core.check("select all checks every shown row", len([r for r in task_rows(core) if r.get("checked")]) == 3)
        core.action("ClearChecks")

        print("groups, filters, search, sort")
        core.action("ToggleGroup", key="done")
        core.pump(0.2)
        core.check("folded group hides its rows", "alpha.zip" not in [r["name"] for r in task_rows(core)] and
                   any(h.get("headerCollapsed") for h in header_rows(core)))
        core.action("ToggleGroup", key="done")
        core.action("SetFilter", filter=FILTER["PAUSED"])
        core.pump(0.2)
        core.check("paused filter: one row, no headers", [r["name"] for r in task_rows(core)] == ["movie.mkv"] and not header_rows(core),
                   core.rows("tasks"))
        core.check("title follows the filter", core.snapshot("chrome").get("title") == strings["homeFilterPaused"])
        core.action("SetFilter", filter=FILTER["ALL"])
        core.action("SetSearch", text="ALPHA")
        core.pump(0.2)
        core.check("search ignores case", [r["name"] for r in task_rows(core)] == ["alpha.zip"])
        core.action("SetSearch", text="nothing-like-this")
        core.pump(0.2)
        chrome = core.snapshot("chrome")
        core.check("no match text", chrome.get("listEmpty") and "nothing-like-this" in chrome.get("emptyTitle", ""), chrome.get("emptyTitle"))
        core.action("SetSearch", text="")
        core.action("SetSort", index=4)
        core.pump(0.4)
        chrome = core.snapshot("chrome")
        core.check("sort menu marks name order", chrome["sortOptions"][4].get("checked") and
                   strings["sysSortNameAz"] in chrome.get("sortLabel", ""), chrome.get("sortLabel"))

        print("speed control and theme")
        core.action("ToggleTurtle")
        core.pump(0.5)
        chrome = core.snapshot("chrome")
        core.check("turtle mode", chrome.get("turtleActive") and chrome.get("modeLabel") == "512 KB/s", chrome.get("modeLabel"))
        core.check("status bar shows the limit", "512.0 KB/s" in core.snapshot("status").get("limit", ""), core.snapshot("status"))
        core.action("PickSpeedOption", index=3)
        core.pump(0.5)
        chrome = core.snapshot("chrome")
        core.check("unlimited again", not chrome.get("turtleActive", False) and chrome["speedOptions"][3].get("checked"), chrome.get("modeLabel"))
        core.action("SystemTheme", dark=True)
        core.pump(0.2)
        core.check("system dark theme is followed", core.snapshot("shell").get("dark") is True)
        core.action("ToggleTheme")
        core.pump(0.3)
        core.check("theme toggle leaves system mode", core.snapshot("shell").get("dark", False) is False)
        core.action("OpenSettings")
        core.pump(0.2)
        chrome = core.snapshot("chrome")
        core.check("settings open", chrome.get("settingsOpen") and chrome.get("title") == strings["homeSettings"] and
                   not chrome.get("detailsOpen", False), chrome.get("title"))
        core.action("SetFilter", filter=FILTER["ALL"])

        print("row actions")
        core.action("RowQuickAction", id=paused["id"])
        core.wait_task(["DOWNLOADING", "CONNECTING", "QUEUED"], timeout=20, task_id=paused["id"])
        core.action("CyclePriority", id=paused["id"])
        core.pump(0.6)
        row = [r for r in task_rows(core) if r["id"] == paused["id"]][0]
        core.check("priority chip cycles to High", row.get("priority") == 1 and row.get("priorityLabel") == strings["priorityHigh"], row)
        core.action("PauseAll")
        core.wait_for("everything paused", lambda: all(t["status"] in ("PAUSED", "COMPLETED") for t in core.tasks()), 30)
        core.pump(0.6)
        core.check("nothing is pausable any more", not core.snapshot("chrome").get("anyPausable", False))
        stats = core.debug("stats")
        print("    notifier: " + str(stats) + ", notifications seen: %d" % core.notifications)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
    core.finish()


if __name__ == "__main__":
    run()
