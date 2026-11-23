import json
import os
import subprocess
import sys
import time
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER, ENUMS, install_fake_ytdlp
from test_bridge import ALERT

PORT = int(os.environ.get("FJ_HIDDEN_PORT", "18266"))
BASE = "http://127.0.0.1:%d" % PORT
PIN = ALERT["HIDDEN_PIN"]
NAMES = ["alpha.bin", "bravo.bin", "charlie.bin"]
LEAK_CHANNELS = ["tasks", "chrome", "status", "shell", "details", "alerts", "moreRows", "rowMenu", "settingsRows", "torrentDetails", "audioCompress", "subtitlesPage"]
FINAL = os.environ.get("FJ_HIDDEN_FINAL") == "1"
KIND = {name: index for index, name in enumerate(ENUMS["RowKind"])}
STATE = {name: index for index, name in enumerate(ENUMS["MediaStateCode"])}
LIST = "https://www.youtube.com/playlist?list=PLgrow"
TITLE = "Learning Kotlin - full course"
KEYS = ["OPEN", "OPEN_FOLDER", "PAUSE", "RESUME", "EXTRACT", "COMPRESS", "SUBTITLES", "SEND_TO_DEVICE", "COPY_LINK", "SELECT", "DETAILS", "UNHIDE", "DELETE"]


def make_site(folder):
    os.makedirs(folder, exist_ok=True)
    for name in NAMES:
        open(os.path.join(folder, name), "wb").write(os.urandom(48 * 1024))


def add_and_wait(core, url, name):
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text=url)
    core.pump(0.6)
    core.action("SetFileName", text=name)
    core.pump(0.2)
    core.action("StartDownload")
    return core.wait_task(["COMPLETED", "ERROR"], timeout=40, file_name=name)


class Loose(dict):
    def __missing__(self, key):
        return False


def loose(value):
    if isinstance(value, dict):
        return Loose({k: loose(v) for k, v in value.items()})
    if isinstance(value, list):
        return [loose(v) for v in value]
    return value


def chrome_of(core):
    return loose(core.snapshot("chrome") or {})


def wait_toast(core, text, timeout=15):
    try:
        core.wait_for("the toast", lambda: toast_of(core) == text, timeout)
        return True
    except CheckFailed:
        return False


def pin_alert(core):
    for alert in core.rows("alerts"):
        if alert.get("kind") == PIN:
            base = {"title": "", "text": "", "hint": "", "hintError": False, "inputLabel": "", "inputSecret": False, "inputRev": 0,
                    "inputDisabled": False, "confirmDanger": False, "confirmLabel": ""}
            base.update(alert)
            return base
    return None


def type_pin(core, text):
    core.action("AlertText", kind=PIN, text=text)
    core.action("AlertConfirm", kind=PIN, checked=False)
    core.pump(0.4)


def visible_names(core):
    return sorted(r.get("name", "") for r in core.rows("tasks") if r.get("kind", 0) == 0)


def leaks(core, words):
    found = []
    for channel in LEAK_CHANNELS:
        text = core.snapshot_text(channel) or ""
        for word in words:
            if word in text:
                found.append((channel, word))
    for task in core.tasks():
        for word in words:
            if word in json.dumps(task):
                found.append(("debug.tasks", word))
    return found


def add_hidden(core):
    return (core.debug("state") or {}).get("addHidden")


def toast_of(core):
    return (core.snapshot("shell") or {}).get("toastText", "")


def open_hidden(core, pin):
    core.action("OpenSettings")
    core.pump(0.4)
    core.action("SettingClick", id="more.hidden")
    core.pump(0.4)
    alert = pin_alert(core)
    if alert is None:
        return None
    type_pin(core, pin)
    return pin_alert(core)


def server_up():
    try:
        urllib.request.urlopen(SERVER + "/", timeout=2)
        return True
    except Exception:
        try:
            urllib.request.urlopen(SERVER + "/file/1MB.bin?key=hidden-probe", timeout=2)
            return True
        except Exception:
            return False


def run():
    core = Core("hidden", before_start=install_fake_ytdlp)
    site = os.path.join(core.work, "site")
    make_site(site)
    clip_bytes = os.urandom(64 * 1024)
    open(os.path.join(site, "clip.mp4"), "wb").write(clip_bytes)
    open(os.path.join(site, "doc.zip"), "wb").write(os.urandom(32 * 1024))
    open(os.path.join(site, "delta.bin"), "wb").write(os.urandom(48 * 1024))
    server = subprocess.Popen([sys.executable, "-m", "http.server", str(PORT), "--bind", "127.0.0.1", "--directory", site],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    try:
        core.pump(0.5)
        strings = core.strings()
        tasks = {}
        for name in NAMES:
            tasks[name] = add_and_wait(core, BASE + "/" + name, name)
        core.check("three finished downloads in the list", visible_names(core) == NAMES, visible_names(core))

        print("the More page entry")
        core.action("OpenSettings")
        core.pump(0.4)
        rows = core.rows("moreRows")
        entry = [r for r in rows if r.get("id") == "more.hidden"]
        core.check("More has a Hidden row with no count", len(entry) == 1 and entry[0].get("title") == strings["hiddenTitle"] and not entry[0].get("subtitle") and not entry[0].get("status") and entry[0].get("clickable"), entry)
        core.action("SetFilter", filter=0)
        core.pump(0.3)

        print("hide with no PIN set: the set-PIN dialog first")
        core.action("RowCheck", id=tasks["alpha.bin"]["id"], checked=True)
        core.action("RowCheck", id=tasks["bravo.bin"]["id"], checked=True)
        core.pump(0.4)
        core.action("HideChecked")
        core.pump(0.4)
        alert = pin_alert(core)
        core.check("the set-PIN dialog opens, masked", alert is not None and alert["title"] == strings["hiddenSetPinTitle"] and alert["inputSecret"] and alert["inputLabel"] == strings["hiddenEnterPin"] and alert["text"] == strings["hiddenSetPinHint"], alert)
        core.check("nothing hidden before the PIN is set", visible_names(core) == NAMES, visible_names(core))
        rev = alert["inputRev"]
        type_pin(core, "12")
        alert = pin_alert(core)
        core.check("a short PIN is refused with the message and a cleared box", alert and alert["hint"] == strings["hiddenInvalidPin"] and alert["hintError"] and alert["inputRev"] > rev and alert["inputLabel"] == strings["hiddenEnterPin"], alert)
        type_pin(core, "12ab")
        alert = pin_alert(core)
        core.check("letters are refused", alert and alert["hint"] == strings["hiddenInvalidPin"], alert)
        type_pin(core, "1234")
        alert = pin_alert(core)
        core.check("the second step asks for the PIN again", alert and alert["inputLabel"] == strings["hiddenConfirmPin"] and alert["hint"] == "", alert)
        type_pin(core, "4321")
        alert = pin_alert(core)
        core.check("a different second PIN starts over with the mismatch message", alert and alert["hint"] == strings["hiddenPinMismatch"] and alert["inputLabel"] == strings["hiddenEnterPin"], alert)
        type_pin(core, "1234")
        type_pin(core, "1234")
        core.pump(0.5)
        core.check("the dialog closes", pin_alert(core) is None)
        core.check("the two checked downloads left the list", visible_names(core) == ["charlie.bin"], visible_names(core))
        core.check("the toast says where they went", wait_toast(core, strings["hiddenHiddenToast"]), toast_of(core))
        chrome = chrome_of(core)
        core.check("the hidden screen is not open and the selection is gone", not chrome["hiddenOpen"] and not chrome["selectionMode"], (chrome["hiddenOpen"], chrome["selectionMode"]))
        all_entry = chrome["navState"][0]
        core.check("the All counter counts only visible downloads", all_entry["count"] == 1, all_entry)
        core.check("the hidden names are nowhere in the UI state", leaks(core, ["alpha", "bravo"]) == [], leaks(core, ["alpha", "bravo"]))
        core.action("SetSearch", text="alpha")
        core.pump(0.4)
        core.check("searching for a hidden name finds nothing", visible_names(core) == [], visible_names(core))
        core.action("SetSearch", text="")
        core.pump(0.3)

        print("unlock: wrong PIN, then the right one")
        alert = open_hidden(core, "1111")
        core.check("a wrong PIN shows the tries left", alert and alert["hint"] == strings["hiddenWrongPin"].replace("{n}", "4") and alert["hintError"] and not alert["inputDisabled"], alert)
        core.check("the screen is still closed", not chrome_of(core)["hiddenOpen"])
        type_pin(core, "1234")
        core.check("the right PIN closes the dialog", pin_alert(core) is None)
        chrome = chrome_of(core)
        core.check("the hidden screen is open with its title and note", chrome["hiddenOpen"] and chrome["title"] == strings["hiddenTitle"] and chrome["hiddenNote"] == strings["hiddenNoteEncrypted"], (chrome["hiddenOpen"], chrome["title"]))
        core.check("it lists exactly the hidden ones", visible_names(core) == ["alpha.bin", "bravo.bin"], visible_names(core))
        core.check("the toolbar of the normal list is off", not chrome["showToolbar"])
        core.check("the counters still do not count them", chrome["navState"][0]["count"] == 1, chrome["navState"][0])
        core.check("the sidebar highlights nothing", not any(e["selected"] for e in chrome["navState"] + chrome["navType"]))

        print("work on a hidden download: details, menu")
        alpha = tasks["alpha.bin"]["id"]
        core.action("RowClick", id=alpha, control=False, shift=False)
        core.pump(0.6)
        details = core.snapshot("details") or {}
        core.check("details open for a hidden download", json.dumps(details).count("alpha.bin") > 0 and chrome_of(core)["detailsOpen"], list(details.items())[:3])
        core.action("CloseDetails")
        core.action("RowMenu", id=alpha)
        core.pump(0.3)
        keys = [r.get("key") for r in core.rows("rowMenu")]
        labels = [r.get("label") for r in core.rows("rowMenu")]
        core.check("the menu offers Unhide, not Hide, and no send to device", KEYS.index("UNHIDE") in keys and 100 not in keys and KEYS.index("SEND_TO_DEVICE") not in keys and strings["hiddenUnhide"] in labels, (keys, labels))
        core.check("Unhide sits just before Delete", keys[-2:] == [KEYS.index("UNHIDE"), KEYS.index("DELETE")], keys)
        core.action("RowMenuPick", key=KEYS.index("UNHIDE"))
        core.pump(0.5)
        core.check("unhiding from the menu empties its place in the hidden screen", visible_names(core) == ["bravo.bin"], visible_names(core))
        core.action("RowCheck", id=tasks["bravo.bin"]["id"], checked=True)
        core.pump(0.3)
        core.check("the selection bar is the same, with Unhide", chrome_of(core)["selectionMode"])
        core.action("UnhideChecked")
        core.pump(0.5)
        core.check("unhide selected moves it back", visible_names(core) == [] and chrome_of(core)["listEmpty"], visible_names(core))
        core.check("the empty hidden screen says so", chrome_of(core)["emptyTitle"] == strings["hiddenEmpty"], chrome_of(core)["emptyTitle"])
        core.action("HiddenLock")
        core.pump(0.5)
        core.check("lock leaves the screen", not chrome_of(core)["hiddenOpen"])
        core.check("alpha and bravo are in the normal list again", visible_names(core) == NAMES, visible_names(core))
        core.action("RowMenu", id=tasks["alpha.bin"]["id"])
        core.pump(0.3)
        keys = [r.get("key") for r in core.rows("rowMenu")]
        core.check("the normal menu offers Hide before Delete", keys[-2:] == [100, KEYS.index("DELETE")], keys)
        core.action("RowMenuPick", key=100)
        core.pump(0.5)
        core.check("hide from the menu hides it at once (PIN exists)", visible_names(core) == ["bravo.bin", "charlie.bin"] and pin_alert(core) is None, visible_names(core))
        core.check("and shows the toast", wait_toast(core, strings["hiddenHiddenToast"]), toast_of(core))
        core.check("no leak after the menu hide", leaks(core, ["alpha"]) == [], leaks(core, ["alpha"]))

        print("change the PIN")
        open_hidden(core, "1234")
        core.check("the hidden screen is open", chrome_of(core)["hiddenOpen"] and visible_names(core) == ["alpha.bin"], visible_names(core))
        core.action("HiddenChangePin")
        core.pump(0.4)
        alert = pin_alert(core)
        core.check("change PIN asks for the current PIN", alert and alert["title"] == strings["hiddenChangePin"] and alert["inputLabel"] == strings["hiddenOldPin"], alert)
        type_pin(core, "9999")
        alert = pin_alert(core)
        core.check("a wrong current PIN is refused with the tries left", alert and alert["hint"] == strings["hiddenWrongPin"].replace("{n}", "4") and alert["inputLabel"] == strings["hiddenOldPin"], alert)
        core.check("the screen stays open", chrome_of(core)["hiddenOpen"])
        type_pin(core, "1234")
        alert = pin_alert(core)
        core.check("then it asks for the new PIN", alert and alert["inputLabel"] == strings["hiddenNewPin"], alert)
        type_pin(core, "99")
        alert = pin_alert(core)
        core.check("a short new PIN is refused", alert and alert["hint"] == strings["hiddenInvalidPin"] and alert["inputLabel"] == strings["hiddenNewPin"], alert)
        type_pin(core, "5678")
        alert = pin_alert(core)
        core.check("and asks for it again", alert and alert["inputLabel"] == strings["hiddenConfirmPin"], alert)
        type_pin(core, "5679")
        alert = pin_alert(core)
        core.check("a different repeat goes back to the new PIN with the mismatch message", alert and alert["hint"] == strings["hiddenPinMismatch"] and alert["inputLabel"] == strings["hiddenNewPin"], alert)
        type_pin(core, "5678")
        type_pin(core, "5678")
        core.check("the change finishes and the dialog closes", pin_alert(core) is None and chrome_of(core)["hiddenOpen"])
        core.action("HiddenLock")
        core.pump(0.4)
        alert = open_hidden(core, "1234")
        core.check("the old PIN no longer works", alert and alert["hint"] == strings["hiddenWrongPin"].replace("{n}", "4"), alert)
        type_pin(core, "5678")
        core.check("the new PIN opens it", chrome_of(core)["hiddenOpen"] and visible_names(core) == ["alpha.bin"], visible_names(core))

        print("locking leaves nothing behind")
        core.action("RowClick", id=alpha, control=False, shift=False)
        core.pump(0.5)
        core.action("RowMenu", id=alpha)
        core.pump(0.2)
        core.action("HiddenLock")
        core.pump(0.6)
        chrome = chrome_of(core)
        core.check("the screen, details, menu and selection are gone", not chrome["hiddenOpen"] and not chrome["detailsOpen"] and core.rows("rowMenu") == [] and not chrome["selectionMode"], (chrome["hiddenOpen"], chrome["detailsOpen"]))
        core.check("the details channel holds no name", leaks(core, ["alpha"]) == [], leaks(core, ["alpha"]))
        core.action("HiddenLock")
        core.pump(0.3)
        core.check("locking when nothing is open does nothing harmful", visible_names(core) == ["bravo.bin", "charlie.bin"], visible_names(core))
        core.action("OpenSettings")
        core.pump(0.3)
        core.action("SettingClick", id="more.hidden")
        core.pump(0.3)
        core.check("the PIN dialog is open", pin_alert(core) is not None)
        core.action("HiddenLock")
        core.pump(0.3)
        core.check("minimizing or hiding the window closes the PIN dialog", pin_alert(core) is None)
        core.action("SetFilter", filter=0)
        core.pump(0.3)
        open_hidden(core, "5678")
        core.check("opened again", chrome_of(core)["hiddenOpen"])
        core.action("OpenSettings")
        core.pump(0.6)
        core.check("going to More locks it", not chrome_of(core)["hiddenOpen"] and "alpha.bin" not in visible_names(core))
        core.action("SetFilter", filter=0)
        core.pump(0.3)
        open_hidden(core, "5678")
        core.action("SetFilter", filter=1)
        core.pump(0.6)
        core.check("picking a sidebar filter locks it", not chrome_of(core)["hiddenOpen"] and "alpha.bin" not in visible_names(core))
        core.action("SetFilter", filter=0)
        core.pump(0.3)

        print("wrong PIN back-off")
        alert = open_hidden(core, "0000")
        for expected in ("3", "2", "1"):
            type_pin(core, "0000")
            alert = pin_alert(core)
            core.check("tries left count down to " + expected, alert and alert["hint"] == strings["hiddenWrongPin"].replace("{n}", expected), alert)
        type_pin(core, "0000")
        alert = pin_alert(core)
        core.check("the fifth wrong PIN locks the input with a countdown", alert and alert["inputDisabled"] and "30" in alert["hint"] and alert["hint"].startswith(strings["hiddenLockedFor"].split("{n}")[0]), alert)
        type_pin(core, "5678")
        core.check("the right PIN is not taken while locked", not chrome_of(core)["hiddenOpen"] and pin_alert(core) is not None)
        first = pin_alert(core)["hint"]
        core.pump(2.2)
        second = pin_alert(core)["hint"]
        core.check("the countdown ticks", first != second, (first, second))
        core.action("AlertDismiss", kind=PIN, fromScrim=False)
        core.pump(0.4)
        core.check("cancel closes it", pin_alert(core) is None and not chrome_of(core)["hiddenOpen"])
        core.action("OpenSettings")
        core.pump(0.2)
        core.action("SettingClick", id="more.hidden")
        core.pump(0.3)
        type_pin(core, "5678")
        alert = pin_alert(core)
        core.check("the back-off survives closing the dialog", alert and alert["inputDisabled"], alert)
        core.action("AlertDismiss", kind=PIN, fromScrim=False)
        deadline = time.time() + 50
        while time.time() < deadline and not chrome_of(core)["hiddenOpen"]:
            core.pump(3)
            core.action("OpenSettings")
            core.action("SettingClick", id="more.hidden")
            core.pump(0.3)
            type_pin(core, "5678")
        core.check("after the wait the right PIN opens it", chrome_of(core)["hiddenOpen"] and visible_names(core) == ["alpha.bin"], visible_names(core))

        print("unhide all, then hide again and remove the PIN")
        core.action("UnhideAll")
        core.pump(0.5)
        core.check("unhide all empties the hidden screen", visible_names(core) == [], visible_names(core))
        core.action("HiddenLock")
        core.pump(0.5)
        core.check("all three are in the normal list", visible_names(core) == NAMES, visible_names(core))
        core.action("RowCheck", id=tasks["alpha.bin"]["id"], checked=True)
        core.action("RowCheck", id=tasks["charlie.bin"]["id"], checked=True)
        core.pump(0.3)
        core.action("HideChecked")
        core.pump(0.5)
        core.check("hide selected works with a PIN already set", visible_names(core) == ["bravo.bin"] and pin_alert(core) is None, visible_names(core))
        open_hidden(core, "5678")
        core.action("HiddenRemovePin")
        core.pump(0.4)
        alert = pin_alert(core)
        core.check("remove PIN warns and asks for the PIN", alert and alert["title"] == strings["hiddenRemovePin"] and alert["text"] == strings["hiddenRemoveWarn"] and alert["confirmDanger"] and alert["confirmLabel"] == strings["hiddenRemovePin"], alert)
        type_pin(core, "0000")
        alert = pin_alert(core)
        core.check("a wrong PIN does not remove it", alert and alert["hint"] == strings["hiddenWrongPin"].replace("{n}", "4") and chrome_of(core)["hiddenOpen"], alert)
        type_pin(core, "5678")
        core.pump(0.5)
        core.check("removing shows everything again and leaves the screen", pin_alert(core) is None and not chrome_of(core)["hiddenOpen"] and visible_names(core) == NAMES, visible_names(core))
        core.action("OpenSettings")
        core.pump(0.3)
        core.action("SettingClick", id="more.hidden")
        core.pump(0.3)
        alert = pin_alert(core)
        core.check("with no PIN, Hidden asks to set one again", alert and alert["title"] == strings["hiddenSetPinTitle"], alert)
        type_pin(core, "2468")
        type_pin(core, "2468")
        core.pump(0.5)
        core.check("setting it from the More page opens the empty hidden screen", chrome_of(core)["hiddenOpen"] and chrome_of(core)["listEmpty"], chrome_of(core)["hiddenOpen"])
        core.action("HiddenLock")
        core.pump(0.4)

        print("a running hidden download stays out of the tray text")
        if server_up():
            core.action("SetFilter", filter=0)
            core.action("ShowAddDialog", show=True)
            core.action("SetUrl", text=SERVER + "/file/30MB.bin?key=hiddenrun&throttle=200")
            core.pump(0.6)
            core.action("StartDownload")
            running = core.wait_task(["DOWNLOADING"], timeout=40, url_part="key=hiddenrun")
            core.wait_for("the tray text to list it", lambda: "30MB" in (core.snapshot("shell") or {}).get("trayTooltip", ""), 8)
            core.action("RowMenu", id=running["id"])
            core.pump(0.3)
            core.action("RowMenuPick", key=100)
            core.pump(1.5)
            core.check("it left the list", all("30MB" not in n for n in visible_names(core)), visible_names(core))
            tip = (core.snapshot("shell") or {}).get("trayTooltip", "")
            core.check("the tray text no longer names it", "30MB" not in tip and "FetchJet:" not in tip, tip)
            status = core.snapshot("status") or {}
            core.check("the status bar counts none of it", "1" not in status.get("summary", "") and "0 KB/s" in status.get("downSpeed", ""), status)
            wait_toast(core, strings["hiddenHiddenToast"], 20)
            core.check("no leak while it runs hidden", leaks(core, ["30MB"]) == [], leaks(core, ["30MB"]))
            open_hidden(core, "2468")
            row = [r for r in core.rows("tasks") if "30MB" in r.get("name", "")]
            core.check("it shows in the hidden screen and went on downloading", len(row) == 1 and (row[0].get("completed") or row[0].get("progress", 0) > 0), row)
            core.action("Pause", id=running["id"])
            core.pump(0.5)
            core.action("HiddenLock")
            core.pump(0.3)
        else:
            print("  skip  the local test server is not running (servers.sh start)")
        print("the first-time encryption notice")
        notice = ALERT["HIDDEN_NOTICE"]

        def notice_alert():
            return next((a for a in core.rows("alerts") if a.get("kind") == notice), None)

        core.action("SetFilter", filter=0)
        core.pump(0.3)
        clip = add_and_wait(core, BASE + "/clip.mp4", "clip.mp4")
        doc = add_and_wait(core, BASE + "/doc.zip", "doc.zip")
        core.action("RowCheck", id=doc["id"], checked=True)
        core.action("HideChecked")
        core.pump(0.8)
        core.check("a zip hides at once with no notice", "doc.zip" not in visible_names(core) and notice_alert() is None, (visible_names(core), notice_alert()))
        core.action("RowCheck", id=clip["id"], checked=True)
        core.action("HideChecked")
        core.pump(0.6)
        alert = notice_alert() or {}
        core.check("an mp4 waits: it stays in the list and the notice opens", "clip.mp4" in visible_names(core) and alert.get("title") == strings["hiddenNoticeTitle"] and alert.get("text") == strings["hiddenNoticeBody"] and alert.get("confirmLabel") == strings["hiddenNoticeAccept"] and alert.get("dismissLabel") == strings["cancel"], (visible_names(core), alert))
        core.action("AlertDismiss", kind=notice, fromScrim=False)
        core.pump(0.5)
        core.check("cancel closes the notice and hides nothing", notice_alert() is None and "clip.mp4" in visible_names(core), visible_names(core))
        core.action("HideChecked")
        core.pump(0.5)
        core.check("hiding again asks again", notice_alert() is not None)
        core.action("AlertConfirm", kind=notice, checked=False)
        core.pump(1.5)
        core.check("accepting hides it at once", notice_alert() is None and "clip.mp4" not in visible_names(core), visible_names(core))
        core.check("and shows the toast", wait_toast(core, strings["hiddenHiddenToast"], 10), toast_of(core))
        core.check("the name is nowhere in the UI state", leaks(core, ["clip.mp4"]) == [], leaks(core, ["clip.mp4"]))
        core.action("RowCheck", id=doc["id"], checked=False)
        open_hidden(core, "2468")
        chrome = chrome_of(core)
        core.check("the hidden note says audio and video are encrypted", chrome["hiddenNote"] == strings["hiddenNoteEncrypted"], chrome["hiddenNote"])
        core.check("the mp4 is in the hidden screen", "clip.mp4" in visible_names(core), visible_names(core))
        core.action("RowQuickAction", id=clip["id"])
        core.wait_for("the opened path", lambda: (core.debug("opened") or {}).get("hidden"), 30)
        path = core.debug("opened")["hidden"][-1]
        same = os.path.isfile(path) and open(path, "rb").read() == clip_bytes
        core.check("open hands the OS a decrypted copy named like the file", path.replace("\\", "/").endswith("/clip.mp4") and same, path)
        core.action("CloseDetails")
        core.pump(0.8)
        core.check("closing removes the copy", not os.path.exists(path), path)
        core.action("RowQuickAction", id=clip["id"])
        core.wait_for("the second open", lambda: len((core.debug("opened") or {}).get("hidden", [])) >= 2, 30)
        path = core.debug("opened")["hidden"][-1]
        core.action("HiddenLock")
        core.pump(1.0)
        core.check("locking removes the copy", not os.path.exists(path), path)
        open_hidden(core, "2468")
        core.action("UnhideAll")
        core.pump(1.0)
        core.action("HiddenLock")
        core.pump(0.5)
        core.check("unhiding restores a playable mp4 and the zip", {"clip.mp4", "doc.zip"} <= set(visible_names(core)), visible_names(core))
        core.action("RowCheck", id=clip["id"], checked=True)
        core.action("RowCheck", id=doc["id"], checked=True)
        core.action("HideChecked")
        core.pump(1.0)
        core.check("the notice is not asked a second time", notice_alert() is None and "clip.mp4" not in visible_names(core), visible_names(core))
        open_hidden(core, "2468")
        core.action("UnhideAll")
        core.pump(1.0)
        core.action("HiddenLock")
        core.pump(0.5)

        core.action("RowCheck", id=clip["id"], checked=True)
        core.action("RowCheck", id=doc["id"], checked=True)
        core.action("RemoveChecked")
        core.pump(0.4)
        core.action("ConfirmRemove", deleteFiles=True)
        core.pump(1.0)
        core.check("the two test files are gone again", "clip.mp4" not in visible_names(core) and "doc.zip" not in visible_names(core), visible_names(core))

        print("download as hidden: Add dialog opened from the Hidden page")
        open_hidden(core, "2468")
        core.check("the Hidden page is open", chrome_of(core)["hiddenOpen"])
        core.check("the Add dialog is closed", not core.snapshot("add").get("visible"))
        core.action("ShowAddDialog", show=True)
        core.pump(0.5)
        core.check("New download opens the Add dialog with hidden on", core.snapshot("add").get("visible") is True and add_hidden(core) is True, core.snapshot("add"))
        core.check("the Hidden page stays open behind it", chrome_of(core)["hiddenOpen"])
        core.action("ShowAddDialog", show=False)
        core.pump(0.4)
        core.check("closing the dialog without starting turns hidden off", not core.snapshot("add").get("visible") and not add_hidden(core), core.snapshot("add"))
        core.action("HiddenLock")
        core.pump(0.4)
        core.action("ShowAddDialog", show=True)
        core.pump(0.4)
        core.check("a normal dialog has hidden off", core.snapshot("add").get("visible") is True and not add_hidden(core), core.snapshot("add"))
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        open_hidden(core, "2468")
        core.action("ShowAddDialog", show=True)
        core.pump(0.4)
        core.action("SetUrl", text=BASE + "/delta.bin")
        core.pump(0.6)
        core.action("SetFileName", text="delta.bin")
        core.pump(0.2)
        core.action("StartDownload")
        core.check("the toast says it went to Hidden", wait_toast(core, strings["hiddenHiddenToast"], 15), toast_of(core))
        core.pump(1.5)
        if FINAL:
            core.check("it is in the Hidden page", "delta.bin" in visible_names(core), visible_names(core))
            core.action("HiddenLock")
            core.pump(0.4)
            core.check("the added download is not in the list", "delta.bin" not in visible_names(core), visible_names(core))
            core.check("its name is nowhere in the UI state", leaks(core, ["delta"]) == [], leaks(core, ["delta"]))
            core.action("ShowAddDialog", show=True)
            core.pump(0.4)
            core.check("hidden is off in the next dialog", not add_hidden(core))
            core.action("ShowAddDialog", show=False)
            core.pump(0.3)
            open_hidden(core, "2468")
            core.check("it is in the hidden screen", "delta.bin" in visible_names(core), visible_names(core))
        else:
            print("  note  the add-hidden behaviour of the shared add flow is not checked (FJ_HIDDEN_FINAL=1 once it exists)")
        core.action("HiddenLock")
        core.pump(0.4)
        open_hidden(core, "2468")
        core.action("HiddenRemovePin")
        core.pump(0.4)
        type_pin(core, "2468")
        core.pump(0.4)
        core.check("the PIN is removed for the next steps", pin_alert(core) is None)
        core.action("OpenSettings")
        core.pump(0.3)
        core.action("SettingClick", id="more.hidden")
        core.pump(0.3)
        type_pin(core, "1357")
        type_pin(core, "1357")
        core.pump(0.4)
        core.action("HiddenLock")
        core.pump(0.4)

        print("a hidden playlist groups like the list")
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        core.action("SetUrl", text=LIST)
        core.action("ShowAddDialog", show=True)
        core.wait_for("the playlist", lambda: (core.snapshot("addMedia") or {}).get("state", 0) == STATE["PLAYLIST"], 60)
        core.pump(0.5)
        core.action("TogglePlaylistEntry", index=3, selected=False)
        core.action("SetPlaylistQuality", quality=4)
        core.action("ScheduleQuick", minutes=60)
        core.pump(0.5)
        core.action("StartDownload")
        core.wait_for("three playlist tasks", lambda: len([t for t in core.tasks() if t.get("playlistId")]) == 3, 20)
        core.pump(1.0)
        plist = [t for t in core.tasks() if t.get("playlistId")]
        plist_ids = {t["id"] for t in plist}
        pkey = "pl:" + plist[0]["playlistId"]

        def row_ids(kind):
            return [r.get("id") for r in core.rows("tasks") if r.get("kind", 0) == KIND[kind]]

        core.check("the normal list shows the playlist row", pkey in row_ids("GROUP"), row_ids("GROUP"))
        core.action("RowCheck", id=pkey, checked=True)
        core.action("HideChecked")
        core.pump(1.0)
        core.check("hidden: the normal list shows neither the row nor its videos", pkey not in row_ids("GROUP") and not (plist_ids & set(row_ids("TASK"))), (row_ids("GROUP"), row_ids("TASK")))
        open_hidden(core, "1357")
        core.check("the hidden screen has one playlist row and none of its videos", row_ids("GROUP") == [pkey] and not (plist_ids & set(row_ids("TASK"))), (row_ids("GROUP"), row_ids("TASK")))
        group = [r for r in core.rows("tasks") if r.get("id") == pkey][0]
        core.check("the row is named after the playlist and counts its videos", group.get("name") == TITLE and "3" in group.get("stateLine", ""), group)
        core.action("RowClick", id=pkey, control=False, shift=False)
        core.pump(0.6)
        chrome = chrome_of(core)
        core.check("it opens a page inside the hidden screen", chrome["hiddenOpen"] and chrome["playlistOpen"] and chrome["title"] == TITLE and not chrome["groupIsFolder"], (chrome["hiddenOpen"], chrome["playlistOpen"], chrome["title"]))
        core.check("the page lists its three videos and no group row", len(row_ids("TASK")) == 3 and not row_ids("GROUP") and set(row_ids("TASK")) == plist_ids, row_ids("TASK"))
        core.action("PlaylistPauseAll")
        core.wait_for("all paused", lambda: all(t.get("status") == "PAUSED" for t in core.tasks() if t["id"] in plist_ids), 20)
        core.pump(0.6)
        core.check("pause all paused them and the page offers resume", chrome_of(core)["playlistCanResume"])
        core.action("PlaylistResumeAll")
        core.wait_for("all done", lambda: all(t.get("status") == "COMPLETED" for t in core.tasks() if t["id"] in plist_ids), 120)
        core.pump(1.0)
        core.check("resume all finished them", True)
        core.action("ClosePlaylist")
        core.pump(0.6)
        chrome = chrome_of(core)
        core.check("back is the hidden list with the group row", chrome["hiddenOpen"] and not chrome["playlistOpen"] and row_ids("GROUP") == [pkey], (chrome["playlistOpen"], row_ids("GROUP")))
        core.action("RowClick", id=pkey, control=False, shift=False)
        core.pump(0.5)
        core.action("UnhideGroup")
        try:
            core.wait_for("the group to empty", lambda: pkey not in row_ids("GROUP"), 30)
        except CheckFailed:
            pass
        core.pump(0.5)
        chrome = chrome_of(core)
        core.check("unhide group leaves the page and empties the group", not chrome["playlistOpen"] and pkey not in row_ids("GROUP") and not (plist_ids & set(row_ids("TASK"))), (chrome["playlistOpen"], row_ids("GROUP")))
        core.action("HiddenLock")
        core.pump(0.6)
        core.check("the group is in the normal list again", pkey in row_ids("GROUP"), row_ids("GROUP"))
        core.action("RowCheck", id=pkey, checked=True)
        core.action("HideChecked")
        core.pump(1.0)
        open_hidden(core, "1357")
        core.action("RowCheck", id=pkey, checked=True)
        core.pump(0.3)
        core.action("UnhideChecked")
        try:
            core.wait_for("the group to empty", lambda: pkey not in row_ids("GROUP"), 30)
        except CheckFailed:
            pass
        core.pump(0.5)
        core.check("unhide selected on a group row unhides all its videos", pkey not in row_ids("GROUP") and not (plist_ids & set(row_ids("TASK"))), (row_ids("GROUP"), row_ids("TASK")))
        core.action("HiddenLock")
        core.pump(0.6)
        core.check("the group row is back in the normal list", pkey in row_ids("GROUP"), row_ids("GROUP"))
        core.action("RowCheck", id=pkey, checked=True)
        core.action("RemoveChecked")
        core.pump(0.4)
        core.action("ConfirmRemove", deleteFiles=True)
        core.pump(1.0)

        print("hide a folder group")
        core.action("SettingToggle", id="groupByFolder", on=True)
        core.pump(0.8)
        groups = [r for r in core.rows("tasks") if r.get("id", "").startswith("dir:")]
        core.check("the download folder is one group row", len(groups) == 1 and groups[0].get("id", "").startswith("dir:"), [(r.get("kind"), r.get("id")) for r in core.rows("tasks")])
        core.action("RowCheck", id=groups[0]["id"], checked=True)
        core.pump(0.3)
        core.action("HideChecked")
        core.pump(1.0)
        if FINAL:
            core.check("the group is gone from the list", not core.rows("tasks"), [(r.get("kind"), r.get("id")) for r in core.rows("tasks")])
            core.check("the toast says where it went", wait_toast(core, strings["hiddenHiddenToast"], 10), toast_of(core))
            core.check("no name leaks", leaks(core, ["alpha", "bravo", "charlie", "delta"]) == [], leaks(core, ["alpha", "bravo", "charlie", "delta"]))
            open_hidden(core, "1357")
            core.check("its downloads show in the hidden screen as one group row", any(r.get("id", "").startswith("dir:") for r in core.rows("tasks")), [r.get("id") for r in core.rows("tasks")])
            core.action("HiddenLock")
            core.pump(0.3)
        else:
            print("  note  the folder hide result is not checked (FJ_HIDDEN_FINAL=1 once OnHideFolder is implemented)")
        open_hidden(core, "1357")
        dirs = [r for r in core.rows("tasks") if r.get("id", "").startswith("dir:")]
        core.check("the hidden folder is one group row on the hidden screen only", len(dirs) == 1 and dirs[0].get("kind") == KIND["GROUP"], [(r.get("kind"), r.get("id")) for r in core.rows("tasks")])
        if dirs:
            dkey = dirs[0]["id"]
            core.action("RowClick", id=dkey, control=False, shift=False)
            core.pump(0.6)
            chrome = chrome_of(core)
            core.check("it opens a folder page inside the hidden screen", chrome["hiddenOpen"] and chrome["playlistOpen"] and chrome["groupIsFolder"], (chrome["playlistOpen"], chrome["groupIsFolder"]))
            core.check("the page lists the files as rows", len([r for r in core.rows("tasks") if r.get("kind", 0) == KIND["TASK"]]) >= 3, len(core.rows("tasks")))
            core.action("UnhideGroup")
            core.pump(1.5)
            core.check("unhide group empties the hidden screen of it", not [r for r in core.rows("tasks") if r.get("id", "").startswith("dir:")] and not chrome_of(core)["playlistOpen"])
            core.action("HiddenLock")
            core.pump(0.6)
            core.check("the folder group is in the normal list again", [r.get("id") for r in core.rows("tasks") if r.get("id", "").startswith("dir:")] == [dkey], [r.get("id") for r in core.rows("tasks")])
        core.action("SettingToggle", id="groupByFolder", on=False)
        core.pump(0.5)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    finally:
        server.kill()
    core.finish()


if __name__ == "__main__":
    run()
