import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER

LIMIT = 512 * 1024


def rate_kb(core, task_id, seconds):
    first = core.task(task_id=task_id)["downloadedBytes"]
    started = time.time()
    core.pump(seconds)
    last = core.task(task_id=task_id)["downloadedBytes"]
    return (last - first) / 1024.0 / max(time.time() - started, 0.001)


def run():
    core = Core("unlimitedmidway")
    try:
        print("a download under the limit, then back to no limit while it runs")
        core.action("ToggleTurtle")
        core.pump(0.5)
        core.check("the global limit is 512 KB/s", core.debug("state")["globalLimit"] == LIMIT, core.debug("state")["globalLimit"])
        core.debug("addDirect", {"url": SERVER + "/file/256MB.bin?size=256MB&key=midway", "fileName": "midway.bin"})
        task = core.wait_task("DOWNLOADING", timeout=30, url_part="key=midway")
        core.pump(1.5)
        limited = rate_kb(core, task["id"], 4.0)
        core.check("limited (300-700 KB/s)", 300 <= limited <= 700, "%.1f KB/s" % limited)
        print("    limited: %.1f KB/s" % limited)

        core.action("ToggleTurtle")
        core.pump(0.5)
        core.check("no global limit now", core.debug("state")["globalLimit"] == 0, core.debug("state")["globalLimit"])
        core.pump(1.0)
        state = core.task(task_id=task["id"])
        core.check("the same download keeps running", state["status"] in ("DOWNLOADING", "COMPLETED"), state["status"])
        free = rate_kb(core, task["id"], 3.0) if state["status"] == "DOWNLOADING" else float("inf")
        core.check("it speeds up at once without a pause or restart (over 5 MB/s)", free > 5 * 1024, "%.1f KB/s" % free)
        print("    after no limit: %.1f KB/s" % free)
        if core.task(task_id=task["id"])["status"] == "DOWNLOADING":
            core.action("Pause", id=task["id"])
            core.wait_task("PAUSED", timeout=20, task_id=task["id"])
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
