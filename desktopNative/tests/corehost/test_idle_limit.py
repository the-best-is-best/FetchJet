import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER, server_sha256, sha256_of_file

LIMIT = 512 * 1024
DOWNLOADS_PAGE = 1
IN_USE = 170000
AWAY = 600000


def settings_row(core, key):
    for item in core.rows("settingsRows"):
        if item.get("id") == key:
            return item
    return {}


def rate_kb(core, task_id, seconds):
    first = core.task(task_id=task_id)["downloadedBytes"]
    started = time.time()
    core.pump(seconds)
    last = core.task(task_id=task_id)["downloadedBytes"]
    return (last - first) / 1024.0 / max(time.time() - started, 0.001)


def run():
    core = Core("idlelimit")
    try:
        strings = core.strings()
        print("the limit and the setting")
        core.action("ToggleTurtle")
        core.pump(0.5)
        core.check("the global limit is 512 KB/s", core.debug("state")["globalLimit"] == LIMIT, core.debug("state")["globalLimit"])
        faked = core.debug("idle", {"ms": str(IN_USE)})
        core.check("the faked idle time is read", isinstance(faked, dict) and faked.get("idleMs") == IN_USE and faked.get("onlyInUse") is False, faked)
        print("    idle time from the system: %s ms" % faked.get("osIdleMs"))
        core.action("OpenSettings")
        core.pump(0.5)
        core.action("SetSettingsSection", index=DOWNLOADS_PAGE)
        core.pump(0.5)
        row = settings_row(core, "speedLimitOnlyInUse")
        core.check("the switch is on the downloads page, off", row.get("title") == strings.get("speedLimitWhileInUseTitle") and
                   not row.get("checked", False), row)

        print("setting off, user away: the limit stays")
        core.debug("idle", {"ms": str(AWAY)})
        core.debug("addDirect", {"url": SERVER + "/file/8MB.bin?key=idle-off", "fileName": "idle-off.bin"})
        task = core.wait_task("DOWNLOADING", timeout=30, url_part="key=idle-off")
        core.pump(1.5)
        rate = rate_kb(core, task["id"], 4.0)
        core.check("limited while the setting is off (300-700 KB/s)", 300 <= rate <= 700, "%.1f KB/s" % rate)
        print("    %.1f KB/s" % rate)
        core.action("Pause", id=task["id"])
        core.wait_task("PAUSED", timeout=20, task_id=task["id"])

        print("setting on, user at the computer (under 3 minutes idle)")
        core.debug("idle", {"ms": str(IN_USE)})
        core.action("SettingToggle", id="speedLimitOnlyInUse", on=True)
        core.pump(0.5)
        core.check("the switch shows on", settings_row(core, "speedLimitOnlyInUse").get("checked", False))
        core.check("the setting is on in the core", core.debug("idle", {"ms": str(IN_USE)}).get("onlyInUse") is True)
        core.debug("addDirect", {"url": SERVER + "/file/24MB.bin?key=idle-on", "fileName": "idle-on.bin"})
        task = core.wait_task("DOWNLOADING", timeout=30, url_part="key=idle-on")
        core.pump(1.5)
        rate = rate_kb(core, task["id"], 4.0)
        core.check("limited while in use (300-700 KB/s)", 300 <= rate <= 700, "%.1f KB/s" % rate)
        print("    %.1f KB/s" % rate)

        print("user away for 10 minutes: unlimited")
        left = 24 * 1024 * 1024 - core.task(task_id=task["id"])["downloadedBytes"]
        core.debug("idle", {"ms": str(AWAY)})
        started = time.time()
        done = core.wait_task("COMPLETED", timeout=60, task_id=task["id"])
        seconds = time.time() - started
        core.check("the rest came without the limit (under 15 s, the limit needs %.0f s)" % (left / float(LIMIT)),
                   seconds < 15 and left / float(LIMIT) > 25, "%.1f s for %.1f MB" % (seconds, left / 1048576.0))
        path = os.path.join(done["destinationPath"], done["fileName"])
        core.check("the file is correct", os.path.isfile(path) and sha256_of_file(path) == server_sha256("24MB.bin"))
        print("    %.1f MB in %.1f s" % (left / 1048576.0, seconds))

        print("user is back: the limit applies again")
        core.debug("idle", {"ms": "0"})
        core.debug("addDirect", {"url": SERVER + "/file/8MB.bin?key=idle-back", "fileName": "idle-back.bin"})
        task = core.wait_task("DOWNLOADING", timeout=30, url_part="key=idle-back")
        core.pump(1.5)
        rate = rate_kb(core, task["id"], 4.0)
        core.check("limited again (300-700 KB/s)", 300 <= rate <= 700, "%.1f KB/s" % rate)
        print("    %.1f KB/s" % rate)

        print("away in the middle of a download, then back")
        core.debug("idle", {"ms": str(AWAY)})
        core.pump(0.2)
        core.debug("idle", {"ms": "0"})
        current = core.task(task_id=task["id"])
        if current["status"] == "DOWNLOADING":
            core.pump(1.5)
            current = core.task(task_id=task["id"])
        if current["status"] == "DOWNLOADING":
            rate = rate_kb(core, task["id"], 3.0)
            core.check("limited after coming back (under 700 KB/s)", rate <= 700, "%.1f KB/s" % rate)
        else:
            print("    the download finished while away: nothing left to measure")
        core.action("Pause", id=task["id"])

        core.action("SettingToggle", id="speedLimitOnlyInUse", on=False)
        core.pump(0.3)
        core.check("the switch is off again", not settings_row(core, "speedLimitOnlyInUse").get("checked", False))
        core.debug("idle", {"ms": ""})
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
