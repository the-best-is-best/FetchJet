import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER, server_sha256, server_stats, server_reset, sha256_of_file


def add(core, url, name):
    core.check("addDirect accepted: " + name, core.debug("addDirect", {"url": url, "fileName": name}) == "ok")


def refused(key):
    return int(server_stats(key).get("statuses", {}).get("429", 0))


def run():
    server_reset()
    core = Core("hostlimit")
    try:
        core.pump(0.5)

        print("the first download from a server that allows two connections")
        add(core, SERVER + "/file/24MB.bin?key=hl-first&maxconn=2&retryafter=1&throttle=4000", "hl-first.bin")
        task = core.wait_task("COMPLETED", timeout=120, url_part="key=hl-first")
        path = os.path.join(task["destinationPath"], task["fileName"])
        core.check("first file correct", os.path.isfile(path) and sha256_of_file(path) == server_sha256("24MB.bin"))
        first = refused("hl-first")
        core.check("it opened more connections than allowed and was refused", first >= 3, server_stats("hl-first").get("statuses"))

        print("the next download from the same server starts with what was learned")
        add(core, SERVER + "/file/24MB.bin?key=hl-second&maxconn=2&retryafter=1&throttle=4000", "hl-second.bin")
        task = core.wait_task("COMPLETED", timeout=120, url_part="key=hl-second")
        path = os.path.join(task["destinationPath"], task["fileName"])
        core.check("second file correct", os.path.isfile(path) and sha256_of_file(path) == server_sha256("24MB.bin"))
        second = refused("hl-second")
        core.check("the second download is refused at most twice", second <= 2, server_stats("hl-second").get("statuses"))
        core.check("far fewer refusals than the first", second < first, (first, second))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
