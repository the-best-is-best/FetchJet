import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, server_sha256, sha256_of_file

PLAIN = "ftp://127.0.0.1:2121"
TLS_EXPLICIT = "ftpes://127.0.0.1:2122"
TLS_IMPLICIT = "ftps://127.0.0.1:2990"
NO_REST = "ftp://127.0.0.1:2123"
BUSY = "ftp://127.0.0.1:2124"


def add(core, url, name):
    core.check("addDirect accepted: " + name, core.debug("addDirect", {"url": url, "fileName": name}) == "ok")


def fetch(core, label, url, name, size_name, timeout=90, expect="COMPLETED"):
    print(label)
    add(core, url, name)
    task = core.wait_task([expect, "ERROR"] if expect != "ERROR" else "ERROR", timeout=timeout, file_name=name)
    if expect == "COMPLETED":
        path = os.path.join(task["destinationPath"], task["fileName"])
        core.check(label + ": completed", task["status"] == "COMPLETED", (task["status"], task.get("error")))
        core.check(label + ": bytes match", os.path.isfile(path) and sha256_of_file(path) == server_sha256(size_name), path)
    return task


def run():
    core = Core("ftp")
    try:
        core.pump(0.5)

        task = fetch(core, "plain FTP with the login in the link, 9 MB in parts", "ftp://fj:secret@127.0.0.1:2121/pub/dir/9MB.bin", "ftp-9.bin", "9MB.bin")
        core.check("several connections were used", task.get("connections", 0) > 1, task.get("connections"))
        core.check("the server said it can resume", task.get("resumable") == "true", task.get("resumable"))

        fetch(core, "anonymous FTP, a small file", PLAIN + "/pub/1MB.bin".replace("pub", "pub"), "ftp-anon.bin", "1MB.bin")

        task = fetch(core, "FTP over explicit TLS (AUTH TLS), 6 MB in parts", "ftpes://fj:secret@127.0.0.1:2122/secure/6MB.bin", "ftp-explicit.bin", "6MB.bin")
        core.check("TLS: several connections were used", task.get("connections", 0) > 1, task.get("connections"))
        core.check("TLS: the server said it can resume", task.get("resumable") == "true", task.get("resumable"))
        fetch(core, "FTP over implicit TLS (port 2990)", "ftps://fj:secret@127.0.0.1:2990/secure/5MB.bin", "ftp-implicit.bin", "5MB.bin")

        fetch(core, "a server without REST: one connection from the start", "ftp://fj:secret@127.0.0.1:2123/x/4MB.bin", "ftp-norest.bin", "4MB.bin")

        print("a wrong password")
        add(core, "ftp://fj:nope@127.0.0.1:2121/x/1MB.bin", "ftp-bad.bin")
        task = core.wait_task("ERROR", timeout=40, file_name="ftp-bad.bin")
        core.check("the refusal is explained", "login" in (task.get("error") or "").lower() or "530" in (task.get("error") or ""), task.get("error"))

        print("a name that is not a file")
        add(core, "ftp://fj:secret@127.0.0.1:2121/pub/", "ftp-folder.bin")
        task = core.wait_task("ERROR", timeout=40, file_name="ftp-folder.bin")
        core.check("a folder link is explained", "folder" in (task.get("error") or "").lower(), task.get("error"))

        print("a server that allows two connections: the rest are told to wait")
        fetch(core, "2 connections at most", "ftp://fj:secret@127.0.0.1:2124/x/8MB.bin", "ftp-busy.bin", "8MB.bin", timeout=120)

        print("pause and resume in the middle of an FTP download")
        add(core, "ftp://fj:secret@127.0.0.1:2121/x/6MB.bin", "ftp-pause.bin")
        task = core.wait_task("DOWNLOADING", timeout=30, file_name="ftp-pause.bin")
        core.pump(0.4)
        core.action("Pause", id=task["id"])
        paused = core.wait_task("PAUSED", timeout=20, task_id=task["id"])
        core.action("Resume", id=task["id"])
        done = core.wait_task("COMPLETED", timeout=90, task_id=task["id"])
        path = os.path.join(done["destinationPath"], done["fileName"])
        core.check("the resumed file is complete and correct", os.path.isfile(path) and sha256_of_file(path) == server_sha256("6MB.bin"))

        print("pause and resume in the middle of an FTP over TLS download")
        add(core, "ftpes://fj:secret@127.0.0.1:2122/secure/64MB.bin", "ftps-pause.bin")
        task = core.wait_task("DOWNLOADING", timeout=30, file_name="ftps-pause.bin")
        core.wait_for("the first TLS bytes", lambda: int((core.task(task_id=task["id"]) or {}).get("downloadedBytes", 0) or 0) > 0 or (core.task(task_id=task["id"]) or {}).get("status") != "DOWNLOADING", timeout=30)
        core.action("Pause", id=task["id"])
        paused = core.wait_task(["PAUSED", "COMPLETED", "ERROR"], timeout=20, task_id=task["id"])
        core.check("TLS: paused before the end", paused["status"] == "PAUSED", (paused["status"], paused.get("error")))
        core.pump(1.0)
        scratch = [n for root, _, names in os.walk(core.work) for n in names if n.startswith("ftps-pause.bin.part") and n.endswith(".curl")]
        core.check("TLS: no curl temp file is left after the pause", not scratch, scratch)
        if paused["status"] == "PAUSED":
            core.action("Resume", id=task["id"])
        done = core.wait_task(["COMPLETED", "ERROR"], timeout=120, task_id=task["id"])
        path = os.path.join(done["destinationPath"], done["fileName"])
        core.check("TLS: the resumed download completed", done["status"] == "COMPLETED", (done["status"], done.get("error")))
        core.check("TLS: the resumed file is complete and correct", os.path.isfile(path) and sha256_of_file(path) == server_sha256("64MB.bin"))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
