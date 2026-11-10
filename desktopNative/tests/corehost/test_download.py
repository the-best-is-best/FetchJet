import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER, server_sha256, server_stats, sha256_of_file


def run():
    core = Core("download")
    try:
        print("start: fj_start took %d ms" % core.start_ms)
        core.check("ping answers", core.call("ping") == "pong")
        core.check("version is reported", core.call("version") != "")
        core.pump(0.5)
        state = core.debug("state")
        core.check("destination is the scratch downloads folder",
                   os.path.normcase(os.path.normpath(state["destinationPath"])) == os.path.normcase(os.path.normpath(core.downloads)),
                   state["destinationPath"])
        core.check("no tasks at first start", state["tasks"] == 0, state["tasks"])

        print("direct add (as the browser extension does)")
        url = SERVER + "/file/8MB.bin?key=core-direct"
        core.check("addDirect accepted", core.debug("addDirect", {"url": url, "fileName": "direct-8MB.bin"}) == "ok")
        started = time.time()
        task = core.wait_task("COMPLETED", timeout=60, url_part="key=core-direct")
        seconds = time.time() - started
        path = os.path.join(task["destinationPath"], task["fileName"])
        core.check("file name kept", task["fileName"] == "direct-8MB.bin", task["fileName"])
        core.check("file exists", os.path.isfile(path), path)
        core.check("size is 8 MB", os.path.isfile(path) and os.path.getsize(path) == 8 * 1024 * 1024)
        core.check("sha256 matches the server", os.path.isfile(path) and sha256_of_file(path) == server_sha256("8MB.bin"))
        stats = server_stats("core-direct")
        core.check("several connections were used", stats.get("maxActive", 0) > 1, stats.get("maxActive"))
        print("    8 MB in %.1f s, most connections at once: %s" % (seconds, stats.get("maxActive")))
        core.pump(0.3)
        shell = core.snapshot("shell")
        core.check("a toast was published", shell.get("toastText", "") != "", shell)

        print("Add dialog path")
        url2 = SERVER + "/file/3MB.bin?key=core-dialog&name=dialog%20file.bin"
        core.action("SetUrl", text=url2)
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)
        state = core.debug("state")
        core.check("dialog is open", state["isAddDialogVisible"])
        core.check("engine is direct", state["detectedEngine"] == "HTTP_DIRECT", state["detectedEngine"])
        core.check("a file name is suggested", state["suggestedFileName"] != "", state["suggestedFileName"])
        core.action("StartDownload")
        task = core.wait_task("COMPLETED", timeout=60, url_part="key=core-dialog")
        path = os.path.join(task["destinationPath"], task["fileName"])
        core.check("dialog closed after start", not core.debug("state")["isAddDialogVisible"])
        core.check("file exists", os.path.isfile(path), path)
        core.check("sha256 matches the server", os.path.isfile(path) and sha256_of_file(path) == server_sha256("3MB.bin"))
        print("    saved as " + task["fileName"])

        print("pause and resume")
        url3 = SERVER + "/file/6MB.bin?key=core-pause&throttle=600"
        core.debug("addDirect", {"url": url3, "fileName": "pause-6MB.bin"})
        task = core.wait_task("DOWNLOADING", timeout=30, url_part="key=core-pause")
        core.wait_for("some bytes", lambda: (core.task(task_id=task["id"]) or {}).get("downloadedBytes", 0) > 300 * 1024, 30)
        core.action("Pause", id=task["id"])
        paused = core.wait_task("PAUSED", timeout=20, task_id=task["id"])
        core.pump(1.0)
        before = server_stats("core-pause")
        core.check("server sees no open connection while paused", before.get("active", -1) == 0, before.get("active"))
        frozen = core.task(task_id=task["id"])["downloadedBytes"]
        core.pump(1.0)
        core.check("no progress while paused", core.task(task_id=task["id"])["downloadedBytes"] == frozen)
        core.check("paused with partial data", 0 < paused["downloadedBytes"] < 6 * 1024 * 1024, paused["downloadedBytes"])
        core.action("Resume", id=task["id"])
        done = core.wait_task("COMPLETED", timeout=90, task_id=task["id"])
        path = os.path.join(done["destinationPath"], done["fileName"])
        core.check("resumed file is complete and correct",
                   os.path.isfile(path) and sha256_of_file(path) == server_sha256("6MB.bin"))
        ranges = server_stats("core-pause").get("ranges", [])
        core.check("resume asked for ranges, not the whole file again", any(not r.startswith("bytes=0-") for r in ranges), ranges[-6:])

        print("remove with file")
        core.action("Remove", id=done["id"])
        core.pump(0.2)
        core.check("remove asks first", core.debug("state")["removeConfirmation"] == done["id"])
        core.action("ConfirmRemove", deleteFiles=True)
        core.wait_for("task removed", lambda: core.task(task_id=done["id"]) is None, 15)
        core.pump(0.5)
        core.check("file deleted", not os.path.exists(path), path)

        print("files in the app home")
        core.pump(1.5)
        home = core.home
        core.check("tasks_cache.json written", os.path.isfile(os.path.join(home, "tasks_cache.json")))
        log = core.log_text()
        core.check("app.log has the start header", "FetchJet started" in log, log[:200])
        core.check("app.log has engine lines", "native core" in log, log[:400])
        core.check("nothing written outside the scratch home", not os.path.exists(os.path.join(home, "..", "..", ".fetch_jet")))
        memory = core.debug("memory")
        print("    private bytes %.1f MB, working set %.1f MB (with Python)" % (memory["privateBytes"] / 1048576.0, memory["workingSet"] / 1048576.0))
        print("    notifier: " + str(core.debug("stats")))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
