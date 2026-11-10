import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ON_LINUX, SERVER, server_sha256, sha256_of_file

FILE_NAME = "ملف عربي طويل.bin"


def long_name():
    name = "عربي تجربة/مجلد طويل"
    while len(name) < 240:
        name += "/مجلد فرعي باسم طويل جدا"
    return name


def disk(path):
    if ON_LINUX:
        return path
    full = os.path.abspath(path.replace("/", "\\"))
    return full if full.startswith("\\\\?\\") else "\\\\?\\" + full


def files_under(folder):
    found = []
    for root, _, names in os.walk(disk(folder)):
        found.extend(names)
    return found


def run():
    core = Core(long_name())
    try:
        print("work folder: %d characters" % len(core.work))
        core.check("the work folder is longer than 260 characters", len(core.work) > 260, len(core.work))
        core.pump(0.5)
        state = core.debug("state")
        core.check("destination is the Arabic long folder",
                   os.path.normcase(os.path.normpath(state["destinationPath"])) == os.path.normcase(os.path.normpath(core.downloads)),
                   state["destinationPath"])

        url = SERVER + "/file/6MB.bin?key=paths-a&throttle=600"
        core.check("addDirect accepted", core.debug("addDirect", {"url": url, "fileName": FILE_NAME}) == "ok")
        task = core.wait_task("DOWNLOADING", timeout=30, url_part="key=paths-a")
        core.wait_for("some bytes", lambda: (core.task(task_id=task["id"]) or {}).get("downloadedBytes", 0) > 600 * 1024, 30)
        core.action("Pause", id=task["id"])
        paused = core.wait_task("PAUSED", timeout=20, task_id=task["id"])
        core.pump(1.0)
        names = files_under(core.work)
        core.check("part files are written under the Arabic name", any(n.startswith(FILE_NAME + ".part") for n in names), names)
        core.check("the piece list (.chunks) is written", any(n == FILE_NAME + ".chunks" for n in names), names)
        core.check("paused with partial data", 0 < paused["downloadedBytes"] < 6 * 1024 * 1024, paused["downloadedBytes"])
        core.action("Resume", id=task["id"])
        done = core.wait_task("COMPLETED", timeout=90, task_id=task["id"])
        path = os.path.join(done["destinationPath"], done["fileName"])
        core.check("file name kept", done["fileName"] == FILE_NAME, done["fileName"])
        core.check("file exists at the exact Unicode path", os.path.isfile(disk(path)), path)
        core.check("sha256 matches the server", os.path.isfile(disk(path)) and sha256_of_file(disk(path)) == server_sha256("6MB.bin"))
        core.pump(1.0)
        names = files_under(core.work)
        core.check("part files are removed after the merge", not any(".part" in n or n.endswith(".chunks") for n in names), names)

        url2 = SERVER + "/file/2MB.bin?key=paths-b"
        core.debug("addDirect", {"url": url2, "fileName": "ملف ثان.bin"})
        second = core.wait_task("COMPLETED", timeout=60, url_part="key=paths-b")
        second_path = os.path.join(second["destinationPath"], second["fileName"])
        core.check("a second Arabic file without a pause", second["fileName"] == "ملف ثان.bin", second["fileName"])
        core.check("second file is correct", os.path.isfile(disk(second_path)) and sha256_of_file(disk(second_path)) == server_sha256("2MB.bin"))

        core.action("Remove", id=done["id"])
        core.pump(0.2)
        core.action("ConfirmRemove", deleteFiles=True)
        core.wait_for("task removed", lambda: core.task(task_id=done["id"]) is None, 15)
        core.pump(0.5)
        core.check("remove with file deletes it", not os.path.exists(disk(path)), path)

        core.action("SetLanguage", choice="ar")
        core.pump(1.5)
        core.check("tasks_cache.json written in the Arabic long home", os.path.isfile(disk(os.path.join(core.home, "tasks_cache.json"))))
        core.check("settings written in the Arabic long home", any(n.startswith("settings") for n in files_under(core.home)), files_under(core.home))
        log = core.log_text()
        core.check("app.log is written in the Arabic long home", "FetchJet started" in log, log[:200])
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
