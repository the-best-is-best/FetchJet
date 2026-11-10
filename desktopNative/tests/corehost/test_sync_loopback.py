import json
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import DEFAULT_WORK, ENUMS, SERVER

PAIR = {name: index for index, name in enumerate(ENUMS["PairingCode"])}
SEND = {name: index for index, name in enumerate(ENUMS["DeviceSendCode"])}
SENDER_HOST = "127.0.0.2"
RECEIVER_HOST = "127.0.0.3"
SHARED = os.path.join(DEFAULT_WORK, "sync-loopback-shared")


def put(name, value):
    with open(os.path.join(SHARED, name + ".tmp"), "w", encoding="utf-8") as handle:
        json.dump(value, handle)
    os.replace(os.path.join(SHARED, name + ".tmp"), os.path.join(SHARED, name))


def get(name, timeout=60.0):
    path = os.path.join(SHARED, name)
    end = time.time() + timeout
    while time.time() < end:
        if os.path.isfile(path):
            with open(path, encoding="utf-8") as handle:
                return json.load(handle)
        time.sleep(0.1)
    return None


def pairing(core):
    return core.snapshot("devicePairing") or {}


def receiver():
    from corehost import Core, CheckFailed
    core = Core("sync-receiver")
    try:
        core.action("SettingToggle", id="visibleToDevices", on=True)
        core.pump(1.0)
        put("receiver-ready", True)
        confirm = core.wait_for("a pairing code", lambda: pairing(core) if pairing(core).get("state") == PAIR["CONFIRM"] else None, 60)
        put("receiver-code", confirm.get("code"))
        core.action("PairingConfirm")
        offer = core.wait_for("an offer", lambda: pairing(core) if pairing(core).get("state") == PAIR["OFFER"] else None, 60)
        core.check("receiver: the offer names the file", offer.get("fileName") == "sync-link.bin", offer)
        core.action("PairingConfirm")
        task = core.wait_for("the received link as a task", lambda: core.task(url_part="key=sync-loopback"), 60)
        core.check("receiver: the link arrived as a task", task is not None and task.get("fileName") == "sync-link.bin", task)
        put("receiver-result", {"ok": task is not None})
        get("sender-done", 120)
    except CheckFailed as failure:
        core.check(str(failure), False)
        put("receiver-result", {"ok": False, "why": str(failure)})
        print(core.log_text()[-3000:])
    core.finish()


def sender():
    from corehost import Core, CheckFailed
    core = Core("sync-sender")
    try:
        core.pump(0.5)
        core.check("sender: the receiver started", get("receiver-ready") is True)
        url = SERVER + "/file/2MB.bin?key=sync-loopback&throttle=200"
        core.debug("addDirect", {"url": url, "fileName": "sync-link.bin"})
        task = core.wait_task(["DOWNLOADING", "COMPLETED"], timeout=40, file_name="sync-link.bin")
        core.action("Pause", id=task["id"])
        core.wait_task(["PAUSED", "COMPLETED"], timeout=20, task_id=task["id"])
        core.action("RowClick", id=task["id"], control=False, shift=False)
        core.pump(0.3)
        core.action("DetailsShare")
        core.pump(0.5)
        core.action("DeviceToggleManual")
        core.action("DeviceManualIp", text=RECEIVER_HOST)
        core.pump(0.2)
        core.action("DeviceManualConnect")
        confirm = core.wait_for("a pairing code", lambda: pairing(core) if pairing(core).get("state") == PAIR["CONFIRM"] else None, 60)
        other = get("receiver-code")
        core.check("both sides show the same six-digit code", confirm.get("code") == other and len(str(other or "")) == 6, (confirm.get("code"), other))
        core.action("PairingConfirm")
        row = core.wait_for("the receiver in the list", lambda: next((r for r in core.rows("deviceRows") if r.get("paired")), None), 60)
        core.check("the receiver is paired and listed", row is not None, core.rows("deviceRows"))
        core.action("DeviceSelect", id=row["id"])
        core.action("DeviceSetMode", transfer=False)
        core.pump(0.2)
        core.action("DeviceSend")
        seen = []

        def finished():
            sheet = core.snapshot("deviceSheet") or {}
            seen.append(sheet.get("state", 0))
            if sheet.get("state") in (SEND["DONE"], SEND["FAILED"]) or not sheet.get("open", False):
                return {"state": sheet.get("state", 0), "open": sheet.get("open", False)}
            return None

        done = core.wait_for("the send to finish", finished, 90)
        core.check("sender: the link was sent", done.get("state", 0) != SEND["FAILED"] and SEND["FAILED"] not in seen, (done, sorted(set(seen))))
        put("sender-done", True)
        result = get("receiver-result", 60) or {}
        core.check("receiver confirmed the arrival", result.get("ok"), result)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(json.dumps(core.snapshot("deviceSheet")))
        print(core.log_text()[-3000:])
    put("sender-done", True)
    core.finish()


def main():
    if not sys.platform.startswith("linux"):
        print("skip  test_sync_loopback needs several loopback addresses (127.0.0.2 / .3): Linux only")
        return
    role = sys.argv[1] if len(sys.argv) > 1 else ""
    if role == "receiver":
        os.environ["FETCHJET_SYNC_BIND_HOST"] = RECEIVER_HOST
        return receiver()
    if role == "sender":
        os.environ["FETCHJET_SYNC_BIND_HOST"] = SENDER_HOST
        return sender()
    import shutil
    shutil.rmtree(SHARED, ignore_errors=True)
    os.makedirs(SHARED, exist_ok=True)
    env = dict(os.environ, FETCHJET_SYNC_UDP_PORT="28491", FETCHJET_SYNC_TCP_PORT="28492", FETCHJET_SYNC_NO_BROADCAST="1")
    here = os.path.abspath(__file__)
    b = subprocess.Popen([sys.executable, here, "receiver"], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    time.sleep(1.0)
    a = subprocess.Popen([sys.executable, here, "sender"], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    out_a, _ = a.communicate(timeout=300)
    out_b, _ = b.communicate(timeout=120)
    print("--- sender\n" + out_a)
    print("--- receiver\n" + out_b)
    sys.exit(1 if a.returncode or b.returncode else 0)


if __name__ == "__main__":
    main()
