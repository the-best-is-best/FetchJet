import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER, server_sha256, server_reset, sha256_of_file


def add(core, url, name):
    core.check("addDirect accepted: " + name, core.debug("addDirect", {"url": url, "fileName": name}) == "ok")


def scratch_files(core, task_id):
    found = []
    for root, _, names in os.walk(core.work):
        if "task_" + task_id in root.replace("\\", "/"):
            found += [os.path.join(root, n) for n in names]
    return found


def saved_path(task):
    return os.path.join(task["destinationPath"], task["fileName"])


def expect_verified(core, key, name, size_name, timeout=60):
    task = core.wait_task(["COMPLETED", "ERROR"], timeout=timeout, url_part="key=" + key)
    path = saved_path(task)
    core.check(name + ": completed", task["status"] == "COMPLETED", (task["status"], task.get("error")))
    core.check(name + ": file correct", os.path.isfile(path) and sha256_of_file(path) == server_sha256(size_name))
    core.check(name + ": marked verified", task.get("checksumVerified") == "true", task.get("checksumVerified"))
    return task


def expect_refused(core, key, name, timeout=60):
    task = core.wait_task(["ERROR", "COMPLETED"], timeout=timeout, url_part="key=" + key)
    message = task.get("error") or ""
    core.check(name + ": stops with a checksum error", task["status"] == "ERROR" and "checksum" in message.lower(), (task["status"], message))
    core.check(name + ": marked as a mismatch", task.get("checksumVerified") == "false", task.get("checksumVerified"))
    core.pump(1.0)
    core.check(name + ": no file in the downloads folder", not os.path.exists(saved_path(task)), saved_path(task))
    core.check(name + ": nothing left in the cache", not scratch_files(core, task["id"]), scratch_files(core, task["id"]))
    return task


def run():
    server_reset()
    core = Core("integrity")
    try:
        core.pump(0.5)

        print("the server sends the right checksum (several connections)")
        add(core, SERVER + "/file/9MB.bin?key=in-good&digest=good", "in-good.bin")
        expect_verified(core, "in-good", "9 MB with a good Digest", "9MB.bin")

        print("the server sends a checksum the file does not match (several connections)")
        add(core, SERVER + "/file/9MB.bin?key=in-bad&digest=bad", "in-bad.bin")
        expect_refused(core, "in-bad", "9 MB with a bad Digest")

        print("the same on one connection (small file)")
        add(core, SERVER + "/file/300KB.bin?key=in-small-good&digest=good", "in-small-good.bin")
        expect_verified(core, "in-small-good", "300 KB with a good Digest", "300KB.bin")
        add(core, SERVER + "/file/300KB.bin?key=in-small-bad&digest=bad", "in-small-bad.bin")
        expect_refused(core, "in-small-bad", "300 KB with a bad Digest")

        print("the same on a server without ranges")
        add(core, SERVER + "/file/5MB.bin?key=in-norange-bad&digest=bad&norange=1", "in-norange-bad.bin")
        expect_refused(core, "in-norange-bad", "5 MB without ranges and a bad Digest")

        print("one part arrives with wrong bytes, the checksum catches it")
        add(core, SERVER + "/file/9MB.bin?key=in-part&digest=good&wrongpart=3", "in-part.bin")
        task = expect_refused(core, "in-part", "a corrupt part")
        core.action("Resume", id=task["id"])
        done = core.wait_task("COMPLETED", timeout=90, task_id=task["id"])
        core.check("resume downloads it again and it is verified",
                   done["status"] == "COMPLETED" and done.get("checksumVerified") == "true"
                   and os.path.isfile(saved_path(done)) and sha256_of_file(saved_path(done)) == server_sha256("9MB.bin"),
                   (done["status"], done.get("error"), done.get("checksumVerified")))

        print("no checksum from the server: nothing is claimed")
        add(core, SERVER + "/file/3MB.bin?key=in-none", "in-none.bin")
        task = core.wait_task("COMPLETED", timeout=60, url_part="key=in-none")
        core.check("completed without a verdict", task.get("checksumVerified") == "unknown", task.get("checksumVerified"))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
