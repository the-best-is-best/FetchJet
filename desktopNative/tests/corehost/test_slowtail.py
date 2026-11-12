import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER, server_sha256, server_stats, server_reset, sha256_of_file


def run():
    server_reset()
    core = Core("slowtail")
    try:
        core.pump(0.5)
        print("one connection of eight is very slow (30 KB/s) while the others run at full speed")
        url = SERVER + "/file/24MB.bin?key=st-slow&slowreq=5:30"
        started = time.time()
        core.check("addDirect accepted", core.debug("addDirect", {"url": url, "fileName": "st-slow.bin"}) == "ok")
        task = core.wait_task("COMPLETED", timeout=150, url_part="key=st-slow")
        seconds = time.time() - started
        path = os.path.join(task["destinationPath"], task["fileName"])
        core.check("file correct", os.path.isfile(path) and sha256_of_file(path) == server_sha256("24MB.bin"))
        stats = server_stats("st-slow")
        print("    %.1f s, requests: %s, most connections at once: %s" % (seconds, stats.get("requests"), stats.get("maxActive")))
        core.check("the slow connection did not hold the end (under 25 s)", seconds < 25, "%.1f s" % seconds)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
