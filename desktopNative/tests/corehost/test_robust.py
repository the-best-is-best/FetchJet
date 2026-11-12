import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER, server_sha256, server_stats, server_reset, sha256_of_file


def add(core, url, name):
    core.check("addDirect accepted: " + name, core.debug("addDirect", {"url": url, "fileName": name}) == "ok")


def scratch_files(core, task_id):
    found = []
    for root, _, names in os.walk(core.work):
        if "task_" + task_id in root.replace("\\", "/"):
            found += [os.path.join(root, n) for n in names if ".part" in n or n.endswith(".chunks")]
    return found


def run():
    server_reset()
    core = Core("robust")
    try:
        core.pump(0.5)

        print("a normal download still works (every part carries If-Range)")
        add(core, SERVER + "/file/9MB.bin?key=rb-normal", "rb-normal.bin")
        task = core.wait_task("COMPLETED", timeout=60, url_part="key=rb-normal")
        path = os.path.join(task["destinationPath"], task["fileName"])
        core.check("9 MB sha256 matches", os.path.isfile(path) and sha256_of_file(path) == server_sha256("9MB.bin"))

        print("the file changes on the server while it downloads")
        add(core, SERVER + "/file/12MB.bin?key=rb-flip&flipat=4&throttle=900", "rb-flip.bin")
        task = core.wait_task(["ERROR", "COMPLETED"], timeout=60, url_part="key=rb-flip")
        message = task.get("error") or ""
        core.check("the download stops with a clear message", task["status"] == "ERROR" and "changed" in message.lower(), (task["status"], message))
        core.pump(1.0)
        core.check("no parts are left behind", not scratch_files(core, task["id"]), scratch_files(core, task["id"]))
        core.action("Resume", id=task["id"])
        done = core.wait_task("COMPLETED", timeout=90, task_id=task["id"])
        path = os.path.join(done["destinationPath"], done["fileName"])
        core.check("resume downloads the new version completely",
                   os.path.isfile(path) and sha256_of_file(path) == server_sha256("12MB.bin"))

        print("a 'not a robot' page is not taken for the file")
        add(core, SERVER + "/file/3MB.bin?key=rb-robot&robot=1", "rb-robot.bin")
        task = core.wait_task("ERROR", timeout=30, url_part="key=rb-robot")
        core.check("it is reported, not saved", "robot" in (task.get("error") or "").lower() or "waf" in (task.get("error") or "").lower() or "challenge" in (task.get("error") or "").lower(), task.get("error"))

        print("a server without ranges")
        add(core, SERVER + "/file/5MB.bin?key=rb-norange&norange=1", "rb-norange.bin")
        task = core.wait_task("COMPLETED", timeout=60, url_part="key=rb-norange")
        path = os.path.join(task["destinationPath"], task["fileName"])
        core.check("one connection, file correct", os.path.isfile(path) and sha256_of_file(path) == server_sha256("5MB.bin"))

        print("a connection cut in the middle")
        add(core, SERVER + "/file/6MB.bin?key=rb-cut&cutonce=1MB", "rb-cut.bin")
        task = core.wait_task("COMPLETED", timeout=90, url_part="key=rb-cut")
        path = os.path.join(task["destinationPath"], task["fileName"])
        core.check("file correct after the retry", os.path.isfile(path) and sha256_of_file(path) == server_sha256("6MB.bin"))

        print("a server that allows two connections only")
        add(core, SERVER + "/file/8MB.bin?key=rb-two&maxconn=2&retryafter=1", "rb-two.bin")
        task = core.wait_task("COMPLETED", timeout=90, url_part="key=rb-two")
        path = os.path.join(task["destinationPath"], task["fileName"])
        core.check("file correct with 2 connections", os.path.isfile(path) and sha256_of_file(path) == server_sha256("8MB.bin"))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
