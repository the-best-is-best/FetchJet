import glob
import json
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, install_fake_ytdlp
from test_bridge import ALERT
from test_hidden import (NAMES, add_and_wait, chrome_of, make_site, open_hidden, pin_alert, toast_of, type_pin,
                         visible_names, wait_toast)

PORT = int(os.environ.get("FJ_RECOVERY_PORT", "18267"))
BASE = "http://127.0.0.1:%d" % PORT
PIN = ALERT["HIDDEN_PIN"]
NOTICE = ALERT["HIDDEN_NOTICE"]
RECOVERY = ALERT["HIDDEN_RECOVERY"]
CODE = re.compile(r"^(\S{4}-){13}\S{4}$")


def alert_of(core, kind):
    return next((a for a in core.rows("alerts") if a.get("kind") == kind), None)


def pin_dialog(core):
    alert = pin_alert(core)
    if alert is not None:
        alert.setdefault("linkLabel", "")
    return alert


def services(auth, pick, available=True):
    def verify(args):
        auth["reasons"].append(args.get("reason", ""))
        return "1" if auth["allow"] else ""

    return {
        "authAvailable": lambda args: "1" if available else "",
        "authVerify": verify,
        "pickFile": lambda args: pick["path"],
    }


def wait_auth(core):
    core.wait_for("the auth probe", lambda: any(call[0] == "authAvailable" for call in core.host_calls), 10)
    core.pump(0.4)


def backup_files(core):
    return sorted(glob.glob(os.path.join(core.downloads, "FetchJet-backup-*.fjbackup")))


def run():
    auth = {"allow": True, "reasons": []}
    pick = {"path": ""}
    core = Core("recovery", before_start=install_fake_ytdlp, host_services=services(auth, pick))
    core.debug("hideAnyType", {"on": True})
    site = os.path.join(core.work, "site")
    make_site(site)
    open(os.path.join(site, "clip.mp4"), "wb").write(os.urandom(64 * 1024))
    server = subprocess.Popen([sys.executable, "-m", "http.server", str(PORT), "--bind", "127.0.0.1", "--directory", site],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    code = ""
    try:
        core.pump(0.5)
        wait_auth(core)
        strings = core.strings()
        clip = add_and_wait(core, BASE + "/clip.mp4", "clip.mp4")
        alpha = add_and_wait(core, BASE + "/alpha.bin", "alpha.bin")

        print("the More page rows")
        core.action("OpenSettings")
        core.pump(0.4)
        rows = core.rows("moreRows")
        ids = [r.get("id") for r in rows]
        backup = next((r for r in rows if r.get("id") == "more.backup"), {})
        restore = next((r for r in rows if r.get("id") == "more.restore"), {})
        core.check("More has Back up and Restore rows after Hidden", "more.backup" in ids and "more.restore" in ids and ids.index("more.hidden") < ids.index("more.backup") < ids.index("more.restore"), ids)
        core.check("they carry the shared titles and hints", backup.get("title") == strings["backupExport"] and backup.get("subtitle") == strings["backupExportHint"] and restore.get("title") == strings["backupRestore"] and restore.get("subtitle") == strings["backupRestoreHint"] and backup.get("clickable") and restore.get("clickable"), (backup, restore))
        core.action("SetFilter", filter=0)
        core.pump(0.3)

        print("first PIN: the set-PIN step offers the recovery restore")
        core.action("RowCheck", id=clip["id"], checked=True)
        core.action("HideChecked")
        core.pump(0.5)
        alert = pin_dialog(core)
        core.check("no lock yet: the link is Restore with recovery code", alert and alert["title"] == strings["hiddenSetPinTitle"] and alert["linkLabel"] == strings["hiddenRecoveryRestoreTitle"], alert)
        type_pin(core, "1234")
        type_pin(core, "1234")
        core.pump(0.8)
        notice = alert_of(core, NOTICE)
        core.check("the encryption notice opens for the mp4", notice is not None and "clip.mp4" in visible_names(core), (notice, visible_names(core)))
        core.check("no recovery code before the notice is accepted", alert_of(core, RECOVERY) is None)
        core.action("AlertConfirm", kind=NOTICE, checked=False)
        core.pump(1.5)
        shown = alert_of(core, RECOVERY)
        core.check("accepting the notice shows the recovery code once", shown is not None and shown["title"] == strings["hiddenRecoveryTitle"] and shown["text"] == strings["hiddenRecoveryBody"] and shown["confirmLabel"] == strings["hiddenRecoveryCopy"] and shown["dismissLabel"] == strings["close"], shown)
        code = (shown or {}).get("mono", "")
        core.check("the code is 14 groups of 4 joined with a dash", bool(CODE.match(code)), code)
        core.check("the notice is gone and the mp4 is hidden", alert_of(core, NOTICE) is None and "clip.mp4" not in visible_names(core), visible_names(core))
        core.action("AlertConfirm", kind=RECOVERY, checked=False)
        core.pump(0.3)
        opened = core.debug("opened") or {}
        core.check("Copy is counted, keeps the dialog and the code is not kept in the shared texts", opened.get("secretCopies") == 1 and not any(code in t for t in opened.get("shared", [])) and alert_of(core, RECOVERY) is not None, opened)
        core.check("Copy shows the copied toast", wait_toast(core, strings["copied"], 15), toast_of(core))
        core.action("AlertDismiss", kind=RECOVERY, fromScrim=False)
        core.pump(0.3)
        core.check("closing it removes the code from the state", alert_of(core, RECOVERY) is None and "recoveryCode" not in json.dumps(core.snapshot("alerts")) and code not in core.snapshot_text("alerts"), core.snapshot_text("alerts")[:200])

        print("Recovery code and Restore on the hidden bar")
        open_hidden(core, "1234")
        core.check("the hidden screen is open", chrome_of(core)["hiddenOpen"])
        core.action("HiddenRecoveryCode")
        core.pump(0.4)
        again = alert_of(core, RECOVERY)
        core.check("the bar shows the same code again", again is not None and again.get("mono") == code, again)
        core.action("AlertDismiss", kind=RECOVERY, fromScrim=True)
        core.pump(0.3)
        core.check("a click outside closes it", alert_of(core, RECOVERY) is None)
        core.action("HiddenRestore")
        core.pump(0.4)
        alert = pin_dialog(core)
        core.check("restore opens the code box: plain text, its own title, hint and label, no link", alert and alert["title"] == strings["hiddenRecoveryRestoreTitle"] and alert["text"] == strings["hiddenRecoveryRestoreHint"] and alert["inputLabel"] == strings["hiddenRecoveryEnter"] and not alert["inputSecret"] and alert["linkLabel"] == "", alert)
        core.action("AlertText", kind=PIN, text="AAAA-BBBB")
        core.action("AlertConfirm", kind=PIN, checked=False)
        core.pump(0.5)
        core.check("a wrong code closes the box and says so", pin_dialog(core) is None and wait_toast(core, strings["hiddenNoticeRestoreWrong"], 5), toast_of(core))
        core.action("HiddenRestore")
        core.pump(0.3)
        core.action("AlertText", kind=PIN, text="  " + code + " ")
        core.action("AlertConfirm", kind=PIN, checked=False)
        core.pump(0.6)
        core.check("the right code (with stray spaces) restores", pin_dialog(core) is None and wait_toast(core, strings["hiddenNoticeRestoreDone"], 5), toast_of(core))
        core.action("HiddenRestore")
        core.pump(0.3)
        core.action("AlertConfirm", kind=PIN, checked=False)
        core.pump(0.3)
        core.check("an empty code does nothing", pin_dialog(core) is not None)
        core.action("AlertDismiss", kind=PIN, fromScrim=False)
        core.pump(0.3)
        core.check("cancel closes the box and the hidden screen stays open", pin_dialog(core) is None and chrome_of(core)["hiddenOpen"])
        core.action("HiddenLock")
        core.pump(0.4)

        print("backup with a hidden download")
        core.action("OpenSettings")
        core.pump(0.3)
        core.action("SettingClick", id="more.backup")
        core.pump(1.0)
        core.check("it is refused with the hidden message and writes no file", wait_toast(core, strings["backupHiddenBlocked"], 8) and backup_files(core) == [], (toast_of(core), backup_files(core)))
        core.action("SetFilter", filter=0)
        core.pump(0.3)
        open_hidden(core, "1234")
        core.action("UnhideAll")
        core.pump(2.0)
        core.action("HiddenLock")
        core.pump(0.5)
        core.check("everything is visible again", "clip.mp4" in visible_names(core) and "alpha.bin" in visible_names(core), visible_names(core))

        print("backup and restore")
        core.action("OpenSettings")
        core.pump(0.3)
        core.action("SettingClick", id="more.backup")
        core.pump(1.5)
        files = backup_files(core)
        core.check("a backup file lands in the downloads folder", len(files) == 1, files)
        toast = toast_of(core)
        core.check("the toast names the file", bool(files) and toast.startswith(strings["detailsSavedTo"] + ": ") and os.path.basename(files[0]) in toast, toast)
        text = open(files[0], encoding="utf-8").read() if files else ""
        core.check("the file holds the downloads", "clip.mp4" in text and "alpha.bin" in text, text[:120])
        core.check("a text with the backup is not kept in the state", core.snapshot_text("alerts") is not None and "alpha.bin" not in (core.snapshot_text("moreRows") or ""))
        core.pump(1.0)
        core.action("SettingClick", id="more.backup")
        core.pump(1.5)
        files = backup_files(core)
        core.check("a second backup the same day gets its own name", len(files) == 2 and len(set(files)) == 2, files)

        junk = os.path.join(core.work, "junk.fjbackup")
        open(junk, "w", encoding="utf-8").write("this is not a backup")
        pick["path"] = junk
        before = len(core.host_calls)
        core.action("SettingClick", id="more.restore")
        core.pump(1.0)
        picks = [c for c in core.host_calls[before:] if c[0] == "pickFile"]
        asked = json.loads(picks[0][1]) if picks else {}
        core.check("restore asks the host to pick a .fjbackup or .json file", asked.get("pattern") == "*.fjbackup;*.json" and asked.get("title") == strings["backupRestore"], asked)
        core.check("a file that is not a backup is refused", wait_toast(core, strings["backupInvalid"], 8), toast_of(core))
        pick["path"] = ""
        core.pump(0.5)
        core.action("SettingClick", id="more.restore")
        core.pump(0.8)
        core.check("cancelling the picker changes nothing", toast_of(core) != strings["backupDone"].replace("{n}", "1"))
        pick["path"] = files[0]
        core.action("SettingClick", id="more.restore")
        core.pump(1.5)
        core.check("restoring the same backup into the same list adds nothing", wait_toast(core, strings["backupNothingNew"] + " " + strings["backupSkipped"].replace("{n}", "2"), 8), toast_of(core))

        print("forgot PIN")
        core.action("SetFilter", filter=0)
        core.pump(0.3)
        core.action("RowCheck", id=alpha["id"], checked=True)
        core.action("HideChecked")
        core.pump(0.6)
        core.check("hiding with a PIN set needs no dialog", pin_dialog(core) is None and "alpha.bin" not in visible_names(core), visible_names(core))
        alert = open_hidden(core, "0000")
        core.check("the unlock step offers Forgot PIN", alert and alert["linkLabel"] == strings["hiddenForgotPin"], alert)
        auth["allow"] = False
        core.action("AlertLink", kind=PIN)
        core.pump(0.8)
        alert = pin_dialog(core)
        core.check("the device asked with the reason", auth["reasons"] == [strings["hiddenForgotReason"]], auth["reasons"])
        core.check("a refused check changes nothing", alert and alert["inputLabel"] == strings["hiddenEnterPin"] and alert["title"] != strings["hiddenResetPinTitle"] and not chrome_of(core)["hiddenOpen"], alert)
        auth["allow"] = True
        core.action("AlertLink", kind=PIN)
        core.pump(1.0)
        alert = pin_dialog(core)
        core.check("an accepted check turns the box into set a new PIN", alert and alert["title"] == strings["hiddenResetPinTitle"] and alert["text"] == strings["hiddenSetPinHint"] and alert["inputLabel"] == strings["hiddenEnterPin"] and alert["inputSecret"] and alert["linkLabel"] == "" and alert["hint"] == "", alert)
        core.action("AlertDismiss", kind=PIN, fromScrim=False)
        core.pump(0.4)
        core.check("cancelling the reset closes the box", pin_dialog(core) is None and not chrome_of(core)["hiddenOpen"], pin_dialog(core))
        core.action("SettingClick", id="more.hidden")
        core.pump(0.4)
        alert = pin_dialog(core)
        core.check("the reset allowance is gone: the box asks for the PIN again", alert and alert["title"] == strings["hiddenTitle"] and alert["inputLabel"] == strings["hiddenEnterPin"] and alert["linkLabel"] == strings["hiddenForgotPin"], alert)
        core.action("AlertLink", kind=PIN)
        core.pump(1.0)
        alert = pin_dialog(core)
        core.check("Forgot PIN works again after a cancel", alert and alert["title"] == strings["hiddenResetPinTitle"], alert)
        type_pin(core, "12")
        alert = pin_dialog(core)
        core.check("a short PIN is refused", alert and alert["hint"] == strings["hiddenInvalidPin"] and alert["title"] == strings["hiddenResetPinTitle"], alert)
        type_pin(core, "5678")
        alert = pin_dialog(core)
        core.check("it asks for the PIN again", alert and alert["inputLabel"] == strings["hiddenConfirmPin"] and alert["title"] == strings["hiddenResetPinTitle"], alert)
        type_pin(core, "5679")
        alert = pin_dialog(core)
        core.check("a different repeat starts over", alert and alert["hint"] == strings["hiddenPinMismatch"] and alert["inputLabel"] == strings["hiddenEnterPin"], alert)
        type_pin(core, "5678")
        type_pin(core, "5678")
        core.pump(0.6)
        core.check("the reset opens the hidden screen with the new PIN", pin_dialog(core) is None and chrome_of(core)["hiddenOpen"] and visible_names(core) == ["alpha.bin"], visible_names(core))
        core.check("and says it is done", wait_toast(core, strings["hiddenNoticeResetDone"], 5), toast_of(core))
        core.action("HiddenLock")
        core.pump(0.4)
        alert = open_hidden(core, "1234")
        core.check("the old PIN no longer opens it and Forgot PIN is still offered", alert and alert["hint"] == strings["hiddenWrongPin"].replace("{n}", "4") and alert["linkLabel"] == strings["hiddenForgotPin"], alert)
        type_pin(core, "5678")
        core.check("the new PIN does", chrome_of(core)["hiddenOpen"])
        core.action("HiddenLock")
        core.pump(0.3)

        print("a locked-out box can still be reset")
        alert = open_hidden(core, "0000")
        for _ in range(4):
            type_pin(core, "0000")
        alert = pin_dialog(core)
        core.check("five wrong PINs lock the input", alert and alert["inputDisabled"], alert)
        core.action("AlertLink", kind=PIN)
        core.pump(1.0)
        alert = pin_dialog(core)
        core.check("Forgot PIN still works while locked and the new PIN box is usable", alert and alert["title"] == strings["hiddenResetPinTitle"] and not alert["inputDisabled"], alert)
        type_pin(core, "2468")
        type_pin(core, "2468")
        core.pump(0.5)
        core.check("it opens with the PIN just set", chrome_of(core)["hiddenOpen"] and pin_dialog(core) is None)
        core.action("HiddenLock")
        core.pump(0.3)
        core.check("no entries in the UI leak the recovery code", code not in "".join(core.snapshot_text(c) or "" for c in ("tasks", "chrome", "status", "shell", "details", "moreRows", "settingsRows")))

        keep = backup_files(core)[0]
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
        keep = ""
    finally:
        server.kill()
    if keep and not core.failures:
        child = subprocess.run([sys.executable, os.path.abspath(__file__), "restore", keep], capture_output=True, text=True, timeout=240)
        print(child.stdout.rstrip())
        core.check("the restore in a second, empty home passed", child.returncode == 0, child.stderr[-800:])
        child = subprocess.run([sys.executable, os.path.abspath(__file__), "noauth"], capture_output=True, text=True, timeout=240)
        print(child.stdout.rstrip())
        core.check("the host without a device check passed", child.returncode == 0, child.stderr[-800:])
    core.finish()


def run_restore(path):
    auth = {"allow": True, "reasons": []}
    pick = {"path": path}
    core = Core("recovery_restore", before_start=install_fake_ytdlp, host_services=services(auth, pick))
    core.debug("hideAnyType", {"on": True})
    try:
        core.pump(0.8)
        strings = core.strings()
        print("restore into an empty home")
        core.check("the list starts empty", visible_names(core) == [], visible_names(core))
        core.action("OpenSettings")
        core.pump(0.3)
        core.action("SettingClick", id="more.restore")
        core.pump(2.0)
        toast = toast_of(core)
        core.check("the toast reports the restored downloads", toast.startswith(strings["backupDone"].split("{n}")[0]) and "{n}" not in toast, toast)
        core.action("SetFilter", filter=0)
        core.pump(0.5)
        names = visible_names(core)
        core.check("the downloads are back in the list", "clip.mp4" in names and "alpha.bin" in names, names)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


def run_noauth():
    auth = {"allow": True, "reasons": []}
    pick = {"path": ""}
    core = Core("recovery_noauth", before_start=install_fake_ytdlp, host_services=services(auth, pick, available=False))
    core.debug("hideAnyType", {"on": True})
    site = os.path.join(core.work, "site")
    make_site(site)
    server = subprocess.Popen([sys.executable, "-m", "http.server", str(PORT + 1), "--bind", "127.0.0.1", "--directory", site],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    try:
        core.pump(0.5)
        wait_auth(core)
        strings = core.strings()
        print("a host with no device check")
        item = add_and_wait(core, "http://127.0.0.1:%d/alpha.bin" % (PORT + 1), "alpha.bin")
        core.action("RowCheck", id=item["id"], checked=True)
        core.action("HideChecked")
        core.pump(0.4)
        type_pin(core, "1234")
        type_pin(core, "1234")
        core.pump(0.6)
        alert = open_hidden(core, "0000")
        core.check("the unlock step has no Forgot PIN link", alert and alert.get("linkLabel", "") == "", alert)
        core.check("nothing asked the device", not any(call[0] == "authVerify" for call in core.host_calls))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    finally:
        server.kill()
    core.finish()


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "restore":
        run_restore(sys.argv[2])
    elif len(sys.argv) > 1 and sys.argv[1] == "noauth":
        run_noauth()
    else:
        run()
