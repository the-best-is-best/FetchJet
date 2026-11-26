import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, SERVER, server_reset, server_sha256, sha256_of_file

MODE = {name: index for index, name in enumerate(ENUMS["AddModeCode"])}
COUNT = 10


def wait_all(core, count, timeout=90):
    def done():
        tasks = core.tasks()
        if len(tasks) >= count and all(t["status"] in ("COMPLETED", "ERROR") for t in tasks):
            return tasks
        return None

    return core.wait_for("%d downloads to end" % count, done, timeout)


def verify(core, tasks, label, sha):
    names = [t["fileName"] for t in tasks]
    paths = [os.path.join(t["destinationPath"], t["fileName"]) for t in tasks]
    core.check(label + ": every task completed", all(t["status"] == "COMPLETED" for t in tasks), [(t["fileName"], t["status"], t["error"]) for t in tasks])
    core.check(label + ": the tasks have distinct file names", len({n.lower() for n in names}) == len(tasks), sorted(names))
    core.check(label + ": each task has its own file on disk", all(os.path.isfile(p) for p in paths) and len(set(paths)) == len(tasks), sorted(names))
    core.check(label + ": every file is right", all(os.path.isfile(p) and sha256_of_file(p) == sha for p in paths))


def start_list(core, links):
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text="\n".join(links))
    core.pump(0.5)
    core.wait_for("the link list", lambda: core.snapshot("add").get("mode") == MODE["LINK_LIST"] and len(core.rows("addLinks")) == len(links), 30)
    core.action("StartDownload")


def clear(core):
    for task in core.tasks():
        core.action("Remove", id=task["id"])
        core.action("ConfirmRemove", deleteFiles=True)
    core.wait_for("the list to be empty", lambda: len(core.tasks()) == 0, 30)
    core.pump(0.3)


def link_list(core):
    sha = server_sha256("1MB.bin")
    print("ten links of one file name through the link list")
    start_list(core, [SERVER + "/file/1MB.bin?k=%d" % i for i in range(1, COUNT + 1)])
    tasks = wait_all(core, COUNT)
    verify(core, tasks, "link list", sha)
    core.check("their names are 1MB.bin and the numbered copies", sorted(t["fileName"] for t in tasks) ==
               sorted(["1MB.bin"] + ["1MB (%d).bin" % i for i in range(1, COUNT)]), sorted(t["fileName"] for t in tasks))


def served(core):
    sha = server_sha256("1MB.bin")
    print("ten links the server names (no name in the link)")
    for i in range(1, COUNT + 1):
        core.debug("addDirect", {"url": SERVER + "/file/1MB.bin?name=served.bin&k=%d" % i, "fileName": ""})
    tasks = wait_all(core, COUNT)
    verify(core, tasks, "server named", sha)
    core.check("they are served.bin and the numbered copies", sorted(t["fileName"] for t in tasks) ==
               sorted(["served.bin"] + ["served (%d).bin" % i for i in range(1, COUNT)]), sorted(t["fileName"] for t in tasks))


def two_folders(core):
    sha = server_sha256("1MB.bin")
    print("two rows of a page with the same name from two folders")
    start_list(core, [SERVER + "/file/dir-a/photo.jpg?size=1MB", SERVER + "/file/dir-b/photo.jpg?size=1MB"])
    tasks = wait_all(core, 2)
    verify(core, tasks, "two folders", sha)
    core.check("they are photo.jpg and photo (1).jpg", sorted(t["fileName"] for t in tasks) == ["photo (1).jpg", "photo.jpg"], sorted(t["fileName"] for t in tasks))


def existing_name(core):
    sha = server_sha256("1MB.bin")
    kind = ENUMS["ChoiceDialogKind"].index("FILE_CONFLICT")
    print("a name already on disk with no download: the single Add dialog still asks")
    state = core.debug("state")
    existing = os.path.join(state["destinationPath"], "1MB.bin")
    os.makedirs(state["destinationPath"], exist_ok=True)
    with open(existing, "wb") as handle:
        handle.write(b"old")
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text=SERVER + "/file/1MB.bin?k=500")
    core.pump(0.6)
    core.action("StartDownload")
    core.wait_for("the file conflict question", lambda: core.debug("state").get("fileConflict") == "1MB.bin", 20)
    core.check("it asks, and nothing is replaced yet", os.path.getsize(existing) == 3 and len(core.tasks()) == 0)
    core.action("ChoicePrimary", kind=kind)
    task = core.wait_task("COMPLETED", timeout=60, url_part="k=500")
    core.check("save as a copy keeps the old file and adds 1MB (1).bin",
               task["fileName"] == "1MB (1).bin" and os.path.getsize(existing) == 3 and
               sha256_of_file(os.path.join(task["destinationPath"], "1MB (1).bin")) == sha, task["fileName"])
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text=SERVER + "/file/1MB.bin?k=501")
    core.pump(0.6)
    core.action("StartDownload")
    core.wait_for("the file conflict question again", lambda: core.debug("state").get("fileConflict") == "1MB.bin", 20)
    core.action("ChoiceSecondary", kind=kind)
    task = core.wait_task("COMPLETED", timeout=60, url_part="k=501")
    core.check("replace puts the new file over the old one under the same name",
               task["fileName"] == "1MB.bin" and sha256_of_file(existing) == sha, task["fileName"])


def while_running(core):
    sha = server_sha256("2MB.bin")
    print("a second download of the same name while the first still runs: it gets a numbered name")
    for key in ("k=800", "k=801"):
        core.action("ShowAddDialog", show=True)
        core.action("SetUrl", text=SERVER + "/file/2MB.bin?%s&throttle=250" % key)
        core.pump(0.6)
        core.action("StartDownload")
        core.wait_for("the download " + key, lambda: core.task(url_part=key) is not None, 20)
    first = core.task(url_part="k=800")
    second = core.task(url_part="k=801")
    core.check("the first keeps the name, the second is numbered", first["fileName"] == "2MB.bin" and second["fileName"] == "2MB (1).bin",
               (first["fileName"], second["fileName"]))
    first = core.wait_task("COMPLETED", timeout=60, url_part="k=800")
    second = core.wait_task("COMPLETED", timeout=60, url_part="k=801")
    paths = [os.path.join(t["destinationPath"], t["fileName"]) for t in (first, second)]
    core.check("both files are on disk, whole and apart", paths[0] != paths[1] and all(os.path.isfile(p) and sha256_of_file(p) == sha for p in paths))


def run():
    server_reset()
    core = Core("samename")
    try:
        core.pump(0.8)
        for body in (link_list, served, two_folders, existing_name, while_running):
            body(core)
            clear(core)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
