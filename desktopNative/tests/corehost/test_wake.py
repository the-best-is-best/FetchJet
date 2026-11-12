import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER

DOWNLOADS = 1
MINUTE = 60 * 1000
SLACK = 20 * 1000


def wake(core):
    return core.debug("wake") or {}


def row(core, key):
    for item in core.rows("settingsRows"):
        if item.get("id") == key:
            return item
    return {}


def schedule(core, key, minutes):
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text=SERVER + "/file/1MB.bin?key=" + key)
    core.pump(0.3)
    core.action("SetFileName", name=key + ".bin")
    core.action("ScheduleQuick", minutes=minutes)
    core.pump(0.2)
    asked = int(time.time() * 1000)
    core.action("StartDownload")
    core.wait_for("the scheduled task " + key, lambda: (core.task(url_part="key=" + key) or {}).get("waitingInQueue"), timeout=20)
    return asked + minutes * MINUTE


def saved(core):
    path = os.path.join(core.home, "behavior.properties")
    if not os.path.isfile(path):
        return ""
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


def run():
    core = Core("wake")
    try:
        core.pump(0.5)
        state = wake(core)
        if not state.get("supported", False):
            print("  skip  wake timers are not supported on this system")
            core.finish()
            return
        core.check("off by default, nothing armed", not state.get("on", False) and state.get("armedAtMs", 0) == 0, state)

        print("a scheduled download while the setting is off")
        late = schedule(core, "wake-late", 30)
        core.pump(0.5)
        core.check("nothing is armed while the setting is off", wake(core).get("armedAtMs", 0) == 0, wake(core))

        print("the setting")
        core.action("OpenSettings")
        core.action("SetSettingsSection", index=DOWNLOADS)
        core.pump(0.6)
        item = row(core, "wakeForSchedule")
        core.check("the row is on the downloads page and off", item.get("id") == "wakeForSchedule" and not item.get("checked", False), item)
        core.action("SettingToggle", id="wakeForSchedule", on=True)
        core.pump(0.6)
        core.check("the row shows on", row(core, "wakeForSchedule").get("checked", False))
        core.check("saved in behavior.properties", "wakeForSchedule=true" in saved(core), saved(core))
        armed = wake(core).get("armedAtMs", 0)
        core.check("armed one minute before the start", abs(armed - (late - MINUTE)) < SLACK, (armed, late))

        print("an earlier download moves the timer")
        early = schedule(core, "wake-early", 10)
        core.pump(0.6)
        armed = wake(core).get("armedAtMs", 0)
        core.check("armed for the earlier start", abs(armed - (early - MINUTE)) < SLACK, (armed, early))

        print("off clears it")
        core.action("SettingToggle", id="wakeForSchedule", on=False)
        core.pump(0.6)
        core.check("nothing armed after turning it off", wake(core).get("armedAtMs", 0) == 0, wake(core))
        core.check("saved as off", "wakeForSchedule=false" in saved(core), saved(core))
        core.action("SettingToggle", id="wakeForSchedule", on=True)
        core.pump(0.6)
        armed = wake(core).get("armedAtMs", 0)
        core.check("on again arms it again", abs(armed - (early - MINUTE)) < SLACK, (armed, early))
        core.action("SettingToggle", id="wakeForSchedule", on=False)
        core.pump(0.4)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
    core.finish()


if __name__ == "__main__":
    run()
