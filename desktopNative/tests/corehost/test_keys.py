import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, SERVER

KIND_TASK = ENUMS["RowKind"].index("TASK")
KEY = {name: index for index, name in enumerate(ENUMS["ListKeyCode"])}
RUNNING = ["DOWNLOADING", "CONNECTING", "QUEUED"]


def task_rows(core):
    return [r for r in core.rows("tasks") if r.get("kind", 0) == KIND_TASK]


def order(core):
    return [r["id"] for r in task_rows(core)]


def key(core, name, shift=False, rows=0):
    core.action("ListKey", key=KEY[name], shift=shift, rows=rows)
    core.pump(0.25)
    return core.snapshot("chrome")


def focus(core):
    return core.snapshot("chrome").get("focusId", "")


def checked(core):
    return [r["id"] for r in task_rows(core) if r.get("checked")]


def move_to(core, task_id):
    key(core, "HOME")
    for _ in range(len(order(core))):
        if focus(core) == task_id:
            return True
        key(core, "DOWN")
    return focus(core) == task_id


def run():
    core = Core("keys")
    try:
        core.pump(0.6)
        core.check("the new key codes are in the protocol",
                   all(name in KEY for name in ("UP", "DOWN", "HOME", "END", "PAGE_UP", "PAGE_DOWN", "OPEN", "DETAILS",
                                                "RESUME", "PAUSE", "OPEN_FILE", "OPEN_FOLDER")), list(KEY))

        print("three downloads: one finished, two running slowly")
        core.debug("addDirect", {"url": SERVER + "/file/1MB.bin?key=keys-a", "fileName": "alpha.bin"})
        alpha = core.wait_task("COMPLETED", timeout=40, file_name="alpha.bin")
        core.debug("addDirect", {"url": SERVER + "/file/1MB.bin?key=keys-b&throttle=4", "fileName": "beta.bin"})
        beta = core.wait_task("DOWNLOADING", timeout=40, file_name="beta.bin")
        core.debug("addDirect", {"url": SERVER + "/file/1MB.bin?key=keys-c&throttle=4", "fileName": "gamma.bin"})
        gamma = core.wait_task("DOWNLOADING", timeout=40, file_name="gamma.bin")
        core.pump(0.8)
        ids = order(core)
        core.check("three task rows", len(ids) == 3, ids)
        core.check("no keyboard row before a key", focus(core) == "" and core.snapshot("chrome").get("focusRow", 0) == 0)

        print("moving the row")
        before = core.snapshot("chrome").get("focusRev", 0)
        chrome = key(core, "DOWN")
        all_rows = [r.get("id") for r in core.rows("tasks")]
        core.check("Down without a row goes to the first one", chrome.get("focusId") == ids[0], chrome.get("focusId"))
        core.check("the row position counts the group headers",
                   chrome.get("focusRow", 0) == all_rows.index(ids[0]) + 1 and
                   chrome.get("focusHeads", 0) == all_rows.index(ids[0]), chrome)
        core.check("every move asks the list to scroll", chrome.get("focusRev", 0) > before, chrome.get("focusRev"))
        core.check("moving does not open the details", not chrome.get("detailsOpen", False))
        core.check("Down goes to the next row", key(core, "DOWN").get("focusId") == ids[1])
        core.check("End goes to the last row", key(core, "END").get("focusId") == ids[2])
        core.check("Down on the last row stays", key(core, "DOWN").get("focusId") == ids[2])
        core.check("Home goes to the first row", key(core, "HOME").get("focusId") == ids[0])
        core.check("Up on the first row stays", key(core, "UP").get("focusId") == ids[0])
        core.check("PageDown moves one screen", key(core, "PAGE_DOWN", rows=2).get("focusId") == ids[2])
        core.check("PageUp moves one screen", key(core, "PAGE_UP", rows=2).get("focusId") == ids[0])
        core.check("PageDown stops at the end", key(core, "PAGE_DOWN", rows=50).get("focusId") == ids[2])
        key(core, "HOME")

        print("shift extends the selection")
        chrome = key(core, "DOWN", shift=True)
        core.check("Shift+Down checks two rows", sorted(checked(core)) == sorted(ids[:2]) and chrome.get("focusId") == ids[1],
                   checked(core))
        key(core, "DOWN", shift=True)
        core.check("Shift+Down again checks three", len(checked(core)) == 3, checked(core))
        key(core, "UP", shift=True)
        core.check("Shift+Up shrinks the selection", sorted(checked(core)) == sorted(ids[:2]), checked(core))
        chrome = key(core, "END", shift=True)
        core.check("Shift+End checks to the end", len(checked(core)) == 3 and chrome.get("focusId") == ids[2], checked(core))
        chrome = key(core, "UP")
        core.check("a plain move clears the selection", checked(core) == [] and not chrome.get("selectionMode", False) and
                   chrome.get("focusId") == ids[1], chrome.get("focusId"))

        print("pause and resume")
        core.check("the row reaches beta", move_to(core, beta["id"]))
        key(core, "PAUSE")
        core.wait_task("PAUSED", timeout=20, task_id=beta["id"])
        core.check("Ctrl+P pauses only the row", (core.task(task_id=gamma["id"]) or {}).get("status") in RUNNING,
                   core.task(task_id=gamma["id"]))
        core.pump(0.6)
        core.check("the row stays on the paused task", focus(core) == beta["id"], focus(core))
        key(core, "PAUSE")
        core.pump(0.3)
        core.check("Ctrl+P on a paused task changes nothing", (core.task(task_id=beta["id"]) or {}).get("status") == "PAUSED")
        key(core, "RESUME")
        core.wait_task(RUNNING, timeout=20, task_id=beta["id"])
        key(core, "SELECT_ALL")
        key(core, "PAUSE")
        core.wait_for("both unfinished tasks paused",
                      lambda: all((core.task(task_id=t["id"]) or {}).get("status") == "PAUSED" for t in (beta, gamma)), 30)
        core.check("the finished task is untouched", (core.task(task_id=alpha["id"]) or {}).get("status") == "COMPLETED")
        key(core, "RESUME")
        core.wait_for("both unfinished tasks running again",
                      lambda: all((core.task(task_id=t["id"]) or {}).get("status") in RUNNING for t in (beta, gamma)), 30)
        key(core, "ESCAPE")
        core.check("escape clears the selection", checked(core) == [])

        print("enter and details")
        core.check("the row reaches gamma", move_to(core, gamma["id"]))
        chrome = key(core, "OPEN")
        core.check("Enter on an unfinished download opens its details", chrome.get("detailsOpen") and
                   [r["id"] for r in task_rows(core) if r.get("selected")] == [gamma["id"]], chrome.get("detailsOpen"))
        chrome = key(core, "UP")
        selected = [r["id"] for r in task_rows(core) if r.get("selected")]
        core.check("with the details open they follow the row", chrome.get("detailsOpen") and selected == [chrome.get("focusId")],
                   (selected, chrome.get("focusId")))
        core.action("CloseDetails")
        core.pump(0.2)
        core.check("the row reaches alpha", move_to(core, alpha["id"]))
        alerts = core.rows("alerts")
        chrome = key(core, "OPEN")
        core.check("Enter on a finished download opens the file, not the details", not chrome.get("detailsOpen", False), chrome)
        core.check("the file is there, no missing-file alert", core.rows("alerts") == alerts, core.rows("alerts"))
        key(core, "OPEN_FILE")
        key(core, "OPEN_FOLDER")
        chrome = key(core, "DETAILS")
        core.check("Alt+Enter opens the details of a finished download", chrome.get("detailsOpen") and
                   [r["id"] for r in task_rows(core) if r.get("selected")] == [alpha["id"]])
        move_to(core, alpha["id"])
        chrome = key(core, "DETAILS")
        core.check("Alt+Enter again closes the details of the same row", not chrome.get("detailsOpen", False), chrome)
        chrome = key(core, "DETAILS")
        core.check("and a third time opens them", chrome.get("detailsOpen", False), chrome)
        core.action("CloseDetails")
        core.pump(0.2)
        core.check("the row reaches beta again", move_to(core, beta["id"]))
        key(core, "OPEN_FILE")
        core.check("Ctrl+O does nothing on an unfinished download", not core.snapshot("chrome").get("detailsOpen", False) and
                   core.rows("alerts") == alerts, core.rows("alerts"))

        print("mouse and keyboard together")
        core.action("RowClick", id=beta["id"], control=False, shift=False)
        core.pump(0.2)
        chrome = core.snapshot("chrome")
        core.check("a click takes the keyboard row away", chrome.get("focusId", "") == "" and chrome.get("detailsOpen"), chrome)
        now = order(core)
        chrome = key(core, "DOWN")
        wanted = now[min(now.index(beta["id"]) + 1, len(now) - 1)]
        core.check("the next key continues from the clicked row", chrome.get("focusId") == wanted, (chrome.get("focusId"), wanted))
        core.action("CloseDetails")
        core.action("SetSearch", text="nothing-like-this")
        core.pump(0.2)
        before = core.snapshot("chrome").get("focusRev", 0)
        chrome = key(core, "DOWN")
        core.check("keys on an empty list do nothing", chrome.get("focusRev", 0) == before and chrome.get("focusRow", 0) == 0, chrome)
        core.action("SetSearch", text="")
        core.action("PauseAll")
        core.wait_for("everything paused", lambda: all(t["status"] in ("PAUSED", "COMPLETED") for t in core.tasks()), 30)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
    core.finish()


if __name__ == "__main__":
    run()
