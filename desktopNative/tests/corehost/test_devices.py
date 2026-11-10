import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, SERVER

SEND = {name: index for index, name in enumerate(ENUMS["DeviceSendCode"])}
PAIR = {name: index for index, name in enumerate(ENUMS["PairingCode"])}
DEVICES = "Galaxy A56 · Android|Android|paired;Office-PC · Windows|Windows|;MacBook · macOS|macOS|;Old phone · Android|Android|paired"


def sheet(core):
    return core.snapshot("deviceSheet") or {}


def run():
    core = Core("devices")
    try:
        core.pump(1.0)
        strings = core.strings()
        core.debug("addDirect", {"url": SERVER + "/file/40MB.bin?key=devices-a&throttle=300", "fileName": "big-video.mkv"})
        task = core.wait_task("DOWNLOADING", timeout=40, file_name="big-video.mkv")
        core.wait_for("some bytes", lambda: (core.task(task_id=task["id"]) or {}).get("downloadedBytes", 0) > 300 * 1024, 30)

        print("open the sheet")
        core.check("closed at start", not sheet(core).get("open", False) and core.snapshot("devicePairing").get("state", 0) == PAIR["NONE"])
        core.action("RowClick", id=task["id"], control=False, shift=False)
        core.pump(0.4)
        core.check("the details offer sending", core.snapshot("details").get("canShare"))
        core.action("DetailsShare")
        core.pump(0.5)
        s = sheet(core)
        core.check("the sheet opens and looks for devices", s.get("open") and s.get("searching") and s.get("fileName") == "big-video.mkv" and
                   not s.get("sendEnabled", False) and s.get("cancelEnabled"), s)

        print("devices")
        core.debug("devices", {"devices": DEVICES})
        core.pump(0.5)
        s = sheet(core)
        rows = core.rows("deviceRows")
        core.check("four devices, paired ones first, by name", [r.get("name") for r in rows] == ["Galaxy A56", "Old phone", "MacBook", "Office-PC"],
                   [r.get("name") for r in rows])
        core.check("group titles and lines", rows[0].get("header") == strings["sysDevicePairedLabel"] and not rows[0].get("divider", False) and
                   rows[1].get("header", "") == "" and rows[1].get("divider") and rows[2].get("header") == strings["sysDeviceOthers"] and
                   rows[3].get("divider"), rows)
        core.check("platform and icon", rows[0].get("platform") == " · Android" and rows[0].get("emoji") == "\U0001F4F1" and
                   rows[2].get("emoji") == "\U0001F4BB", rows[0])
        core.check("the first one is picked, send is possible", rows[0].get("selected") and s.get("sendEnabled") and not s.get("searching", False))
        core.check("a download with parts can be handed over: that is the default", s.get("canTransfer") and s.get("transfer"))
        core.check("no search box for four devices", not s.get("searchShown", False))
        core.action("DeviceSelect", id=rows[3]["id"])
        core.action("DeviceSetMode", transfer=False)
        core.action("DeviceSetDeleteAfter", on=True)
        core.pump(0.4)
        s = sheet(core)
        rows = core.rows("deviceRows")
        core.check("choice, mode and delete-after follow", rows[3].get("selected") and not rows[0].get("selected", False) and
                   not s.get("transfer", False) and s.get("deleteAfter"))
        core.action("DeviceSearch", text="mac")
        core.pump(0.4)
        core.check("search narrows the list", [r.get("name") for r in core.rows("deviceRows")] == ["MacBook"])
        core.action("DeviceSearch", text="zzz")
        core.pump(0.4)
        core.check("nothing matches", len(core.rows("deviceRows")) == 0 and sheet(core).get("noMatch"))
        core.action("DeviceSearch", text="")

        print("connect by address")
        core.action("DeviceToggleManual")
        core.pump(0.3)
        core.check("the address row opens, connect waits for text", sheet(core).get("manualShown") and not sheet(core).get("connectEnabled", False))
        core.action("DeviceManualIp", text="127.0.0.1")
        core.pump(0.3)
        core.check("with an address it can connect", sheet(core).get("connectEnabled") and sheet(core).get("manualIp") == "127.0.0.1")

        print("sending states")
        core.debug("devices", {"devices": DEVICES, "sending": "waiting"})
        core.pump(0.4)
        s = sheet(core)
        core.check("waiting for the other device", s.get("state") == SEND["WAITING"] and s.get("stateText") == strings["sysDeviceWaitingAccept"] and
                   not s.get("sendEnabled", False) and s.get("cancelEnabled") and not s.get("connectEnabled", False), s)
        core.debug("devices", {"devices": DEVICES, "sending": "sending:0.4"})
        core.pump(0.4)
        s = sheet(core)
        core.check("sending: progress, no way out", s.get("state") == SEND["SENDING"] and abs(s.get("progress", 0) - 0.4) < 0.01 and
                   s.get("locked") and not s.get("cancelEnabled", False), s)
        core.action("DeviceDismiss")
        core.pump(0.3)
        core.check("a click outside does not close it while sending", sheet(core).get("open"))
        core.debug("devices", {"devices": DEVICES, "sending": "failed:wait:30"})
        core.pump(0.4)
        s = sheet(core)
        core.check("a refusal is shown in words", s.get("state") == SEND["FAILED"] and s.get("stateText", "").startswith(strings["sysDeviceSendFailed"]) and
                   "30" in s.get("stateText", "") and "wait:" not in s.get("stateText", ""), s.get("stateText"))
        core.debug("devices", {"devices": DEVICES, "sending": "done"})
        core.pump(0.4)
        core.check("done names the device", sheet(core).get("state") == SEND["DONE"] and "Galaxy A56" in sheet(core).get("stateText", ""))

        print("pairing")
        core.debug("devices", {"devices": DEVICES, "pairing": "connecting"})
        core.pump(0.4)
        core.check("connecting", core.snapshot("devicePairing").get("state") == PAIR["CONNECTING"])
        core.debug("devices", {"devices": DEVICES, "pairing": "confirm:482913"})
        core.pump(0.4)
        pairing = core.snapshot("devicePairing")
        core.check("the code and the other device", pairing.get("state") == PAIR["CONFIRM"] and pairing.get("code") == "482913" and
                   "Galaxy A56" in pairing.get("peer", ""), pairing)
        core.debug("devices", {"devices": DEVICES, "pairing": "failed:declined"})
        core.pump(0.4)
        pairing = core.snapshot("devicePairing")
        core.check("a refused pairing is explained", pairing.get("state") == PAIR["FAILED"] and strings["pairDeclined"] in pairing.get("text", ""),
                   pairing.get("text"))

        print("close")
        core.debug("devices", {"devices": DEVICES})
        core.action("DeviceDismiss")
        core.pump(0.5)
        core.check("the sheet closes", not sheet(core).get("open", False) and len(core.rows("deviceRows")) == 0)
        core.debug("devices", {})
        core.action("Pause", id=task["id"])
        core.pump(0.5)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
