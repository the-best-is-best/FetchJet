import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, SERVER

SEND = {name: index for index, name in enumerate(ENUMS["DeviceSendCode"])}
PAIR = {name: index for index, name in enumerate(ENUMS["PairingCode"])}
BAR = {name: index for index, name in enumerate(ENUMS["BarColorCode"])}
DEVICES = "Galaxy A56 · Android|Android|paired;Office-PC · Windows|Windows|;MacBook · macOS|macOS|;Old phone · Android|Android|paired"

os.environ["FETCHJET_SYNC_UDP_PORT"] = "28491"
os.environ["FETCHJET_SYNC_TCP_PORT"] = "28492"
os.environ["FETCHJET_SYNC_BIND_HOST"] = "127.0.0.1"
os.environ["FETCHJET_SYNC_NO_BROADCAST"] = "1"


def sheet(core):
    return core.snapshot("deviceSheet") or {}


def row_named(core, name):
    return next((r for r in core.rows("tasks") if r.get("name") == name), None)


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
        core.check("the send confirmation gets the device and the size", s.get("deviceName") == "Office-PC · Windows" and
                   s.get("deviceEmoji") == "\U0001F4BB" and "MB" in s.get("fileSize", ""), s)
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
        core.debug("devices", {"devices": DEVICES, "pairing": "offer:big-video.mkv:150001"})
        core.pump(0.4)
        pairing = core.snapshot("devicePairing")
        core.check("a send offer shows the file, its size and the sender", pairing.get("state") == PAIR["OFFER"] and
                   pairing.get("fileName") == "big-video.mkv" and "146" in pairing.get("fileSize", "") and
                   "Galaxy A56" in pairing.get("text", ""), pairing)
        core.debug("devices", {"devices": DEVICES, "pairing": "offer:link.bin:0"})
        core.pump(0.4)
        core.check("an offer without a size shows no size", core.snapshot("devicePairing").get("fileSize", "") == "", core.snapshot("devicePairing"))
        core.debug("devices", {"devices": DEVICES, "sending": "failed:no-answer"})
        core.pump(0.4)
        core.check("no answer is explained to the sender", strings["deviceNoAnswer"] in sheet(core).get("stateText", ""), sheet(core).get("stateText"))

        print("receiving")
        core.debug("devices", {"devices": DEVICES, "receiving": "0"})
        core.pump(0.4)
        p = core.snapshot("devicePairing")
        core.check("after Accept the receiver shows the file, the sender and an empty bar", p.get("state") == PAIR["RECEIVING"] and
                   p.get("fileName") == "big-video.mkv" and "Galaxy A56" in p.get("text", "") and p.get("progress", 0) == 0 and
                   not p.get("indeterminate", False) and p.get("percent") == "0%", p)
        core.debug("devices", {"devices": DEVICES, "receiving": "0.62"})
        core.pump(0.4)
        p = core.snapshot("devicePairing")
        core.check("receiving: the bar follows the bytes", abs(p.get("progress", 0) - 0.62) < 0.01 and p.get("percent") == "62%", p)
        core.debug("devices", {"devices": DEVICES, "receiving": "1"})
        core.pump(0.4)
        core.check("receiving: full at the end", core.snapshot("devicePairing").get("percent") == "100%", core.snapshot("devicePairing"))
        core.debug("devices", {"devices": DEVICES, "receiving": "unknown"})
        core.pump(0.4)
        p = core.snapshot("devicePairing")
        core.check("receiving from an old sender (no sizes): moving bar, no percent", p.get("state") == PAIR["RECEIVING"] and p.get("indeterminate") and
                   p.get("percent", "") == "", p)
        core.debug("devices", {"devices": DEVICES, "pairing": "offer:other.bin:10", "receiving": "0.5"})
        core.pump(0.4)
        core.check("a new offer shows over the receive dialog", core.snapshot("devicePairing").get("state") == PAIR["OFFER"])
        core.debug("devices", {"devices": DEVICES, "receiving": "failed:no-answer"})
        core.pump(0.4)
        p = core.snapshot("devicePairing")
        core.check("a failed receive is explained", p.get("state") == PAIR["RECEIVE_FAILED"] and p.get("text", "").startswith(strings["sysDeviceReceiveFailed"]) and
                   strings["deviceNoAnswer"] in p.get("text", ""), p)
        core.action("PairingCancel")
        core.pump(0.4)
        core.check("Close hides the receive dialog", core.snapshot("devicePairing").get("state", 0) == PAIR["NONE"], core.snapshot("devicePairing"))
        core.debug("devices", {"devices": DEVICES, "receiving": "done"})
        core.pump(0.4)
        core.check("a finished receive closes the dialog", core.snapshot("devicePairing").get("state", 0) == PAIR["NONE"], core.snapshot("devicePairing"))

        print("close")
        core.debug("devices", {"devices": DEVICES})
        core.action("DeviceDismiss")
        core.pump(0.5)
        core.check("the sheet closes", not sheet(core).get("open", False) and len(core.rows("deviceRows")) == 0)
        core.debug("devices", {})
        core.action("Pause", id=task["id"])
        core.pump(0.5)

        print("a received download: Preparing file")
        step = strings["stepPreparingFile"]
        parts = os.path.join(core.work, "recv-in", "recv-1")
        os.makedirs(parts, exist_ok=True)
        with open(os.path.join(parts, "handed.bin.part0"), "wb") as handle:
            handle.write(os.urandom(300000))
        with open(os.path.join(parts, "handed.bin.chunks"), "w") as handle:
            handle.write("0 0 299999")
        core.debug("receive", {"url": SERVER + "/file/1MB.bin?key=devices-r", "fileName": "handed.bin", "total": "300000",
                               "downloaded": "300000", "from": "Galaxy A56",
                               "parts": os.path.join(parts, "handed.bin.part0") + "|" + os.path.join(parts, "handed.bin.chunks")})
        core.wait_for("the handed-over row", lambda: row_named(core, "handed.bin") is not None, 5)
        core.pump(0.6)
        r = row_named(core, "handed.bin")
        t = core.task(file_name="handed.bin") or {}
        core.check("handed over: the row says Preparing file with the sizes at once", r.get("preparing") and r.get("stateLine", "").startswith(step) and
                   "/" in r.get("stateLine", "") and r.get("size", "") not in ("", "—"), r)
        core.check("handed over: the preparing bar uses the primary colour", r.get("barColor", 0) == BAR["PRIMARY"], r.get("barColor"))
        core.check("handed over: the task carries the step and the sender's sizes", t.get("step") == "Preparing file" and t.get("totalBytes") == 300000 and
                   t.get("downloadedBytes") == 300000, t)
        done = core.wait_task("COMPLETED", timeout=30, file_name="handed.bin")
        core.pump(0.6)
        core.check("handed over: finished from the parts, the step is gone", done.get("step", "") == "" and done.get("totalBytes") == 300000 and
                   not (row_named(core, "handed.bin") or {}).get("preparing", False), done)

        core.debug("receive", {"url": SERVER + "/file/2MB.bin?key=devices-l&delay=3000&throttle=300", "fileName": "linked.bin",
                               "total": "2097152", "from": "Galaxy A56"})
        core.wait_for("the link row", lambda: row_named(core, "linked.bin") is not None, 5)
        core.pump(0.4)
        r = row_named(core, "linked.bin")
        t = core.task(file_name="linked.bin") or {}
        core.check("link only: Preparing file with the total, nothing downloaded yet", r.get("preparing") and r.get("stateLine", "").startswith(step) and
                   "/" not in r.get("stateLine", "") and t.get("downloadedBytes") == 0, r)
        core.check("link only: the preparing bar uses the primary colour", r.get("barColor", 0) == BAR["PRIMARY"], r.get("barColor"))
        t = core.wait_task("DOWNLOADING", timeout=30, file_name="linked.bin")
        core.pump(0.6)
        core.check("link only: the engine's own status replaces it", t.get("step", "") == "" and
                   not (row_named(core, "linked.bin") or {}).get("preparing", False), t)
        core.action("Pause", id=t["id"])
        core.pump(0.5)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
