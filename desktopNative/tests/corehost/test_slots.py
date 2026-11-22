import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER, server_reset, server_sha256, sha256_of_file

COUNT = int(os.environ.get("FJ_SLOTS_COUNT", "40"))
LIMIT = 3


def running(tasks):
    return len([t for t in tasks if t["status"] in ("DOWNLOADING", "CONNECTING") or (t["status"] == "QUEUED" and not t.get("waitingInQueue", False))])


def run():
    server_reset()
    core = Core("slots")
    try:
        core.pump(0.8)
        sha = server_sha256("2MB.bin")
        print("%d slow downloads added in a tight loop, three at a time" % COUNT)
        for i in range(COUNT):
            core.debug("addDirect", {"url": SERVER + "/file/2MB.bin?throttle=1500&key=slot-%d" % i, "fileName": "slot-%d.bin" % i})
        peak = 0
        samples = 0
        deadline = time.time() + 120
        tasks = []
        while time.time() < deadline:
            core.pump(0.1)
            tasks = core.tasks()
            peak = max(peak, running(tasks))
            samples += 1
            if len(tasks) == COUNT and all(t["status"] in ("COMPLETED", "ERROR") for t in tasks):
                break
        print("    samples %d, most running at once %d" % (samples, peak))
        core.check("every task exists and completed", len(tasks) == COUNT and all(t["status"] == "COMPLETED" for t in tasks),
                   [(t["fileName"], t["status"]) for t in tasks])
        core.check("never more than %d running at once" % LIMIT, peak <= LIMIT, peak)
        core.check("it did use all %d slots" % LIMIT, peak == LIMIT, peak)
        core.check("every file is right", all(sha256_of_file(os.path.join(t["destinationPath"], t["fileName"])) == sha for t in tasks))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
