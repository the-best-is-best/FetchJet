import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed
from test_bridge import ALERT
from test_hidden import chrome_of, pin_alert, type_pin

PIN = ALERT["HIDDEN_PIN"]
SECRET_CHANNELS = ["tasks", "chrome", "status", "shell", "details", "alerts", "moreRows", "settingsRows"]


def services():
    return {"authAvailable": lambda args: "1"}


def dialog(core):
    alert = pin_alert(core)
    if alert is not None:
        for key in ("linkLabel", "extraLabel"):
            alert.setdefault(key, "")
        alert.setdefault("inputPattern", False)
    return alert


def switch(core):
    core.action("AlertExtra", kind=PIN)
    core.pump(0.4)
    return dialog(core)


def enter(core, text):
    type_pin(core, text)
    return dialog(core)


def open_from_more(core):
    core.action("OpenSettings")
    core.pump(0.4)
    core.action("SettingClick", id="more.hidden")
    core.pump(0.4)
    return dialog(core)


def run():
    core = Core("hidden_pattern", host_services=services())
    try:
        core.pump(0.6)
        strings = core.strings()

        print("first lock: a PIN by default, a pattern on request")
        alert = open_from_more(core)
        core.check("the set step asks for a PIN and offers a pattern", alert and alert["title"] == strings["hiddenSetPinTitle"] and alert["inputSecret"] and not alert["inputPattern"] and alert["extraLabel"] == strings["hiddenUsePattern"], alert)
        rev = alert["inputRev"] if alert else 0
        alert = switch(core)
        core.check("Use a pattern turns the box into the pattern pad with the pattern texts", alert and alert["inputPattern"] and not alert["inputSecret"] and alert["title"] == strings["hiddenPatternDraw"] and alert["text"] == strings["hiddenPatternTooShort"] and alert["inputLabel"] == strings["hiddenPatternEnter"] and alert["extraLabel"] == strings["hiddenUsePin"] and alert["inputRev"] > rev, alert)
        alert = enter(core, "123")
        core.check("three dots are refused", alert and alert["hint"] == strings["hiddenPatternTooShort"] and alert["hintError"] and alert["inputPattern"], alert)
        alert = enter(core, "1123")
        core.check("a dot used twice is refused", alert and alert["hint"] == strings["hiddenPatternTooShort"] and alert["inputLabel"] == strings["hiddenPatternEnter"], alert)
        alert = enter(core, "0123")
        core.check("a cell outside 1-9 is refused", alert and alert["hint"] == strings["hiddenPatternTooShort"], alert)
        alert = enter(core, "1235")
        core.check("the second step asks to draw it again, no switch there", alert and alert["inputLabel"] == strings["hiddenPatternAgain"] and alert["inputPattern"] and alert["extraLabel"] == "" and alert["hint"] == "", alert)
        alert = enter(core, "1236")
        core.check("a different pattern starts over with the mismatch message", alert and alert["hint"] == strings["hiddenPatternMismatch"] and alert["inputLabel"] == strings["hiddenPatternEnter"] and alert["inputPattern"], alert)
        enter(core, "1235")
        alert = enter(core, "1235")
        chrome = chrome_of(core)
        core.check("the pattern is set and the hidden screen opens", alert is None and chrome["hiddenOpen"], (alert, chrome["hiddenOpen"]))
        core.check("the bar says Change pattern and Remove pattern", chrome["hiddenChangeLabel"] == strings["hiddenChangePattern"] and chrome["hiddenRemoveLabel"] == strings["hiddenRemovePattern"], (chrome["hiddenChangeLabel"], chrome["hiddenRemoveLabel"]))
        core.action("HiddenLock")
        core.pump(0.4)

        print("unlock with the pattern")
        alert = open_from_more(core)
        core.check("the unlock step shows the pad, Forgot pattern and no switch", alert and alert["inputPattern"] and alert["title"] == strings["hiddenTitle"] and alert["inputLabel"] == strings["hiddenPatternEnter"] and alert["linkLabel"] == strings["hiddenForgotPattern"] and alert["extraLabel"] == "", alert)
        alert = enter(core, "9876")
        core.check("a wrong pattern shows the tries left", alert and alert["hint"] == strings["hiddenWrongPattern"].replace("{n}", "4") and alert["hintError"] and not chrome_of(core)["hiddenOpen"], alert)
        alert = switch(core)
        core.check("the unlock step cannot be switched to a PIN", alert and alert["inputPattern"] and alert["inputLabel"] == strings["hiddenPatternEnter"], alert)
        alert = enter(core, "1235")
        core.check("the right pattern opens the hidden screen", alert is None and chrome_of(core)["hiddenOpen"], alert)

        print("change the pattern to a PIN")
        core.action("HiddenChangePin")
        core.pump(0.4)
        alert = dialog(core)
        core.check("change asks for the current pattern", alert and alert["title"] == strings["hiddenChangePattern"] and alert["inputLabel"] == strings["hiddenOldPattern"] and alert["inputPattern"] and alert["extraLabel"] == "", alert)
        alert = enter(core, "1236")
        core.check("a wrong current pattern is refused", alert and alert["hint"] == strings["hiddenWrongPattern"].replace("{n}", "4") and alert["inputLabel"] == strings["hiddenOldPattern"], alert)
        alert = enter(core, "1235")
        core.check("the new step asks for a new pattern and offers a PIN", alert and alert["inputLabel"] == strings["hiddenNewPattern"] and alert["inputPattern"] and alert["extraLabel"] == strings["hiddenUsePin"], alert)
        alert = switch(core)
        core.check("Use a PIN turns it into a masked PIN box", alert and alert["inputLabel"] == strings["hiddenNewPin"] and alert["inputSecret"] and not alert["inputPattern"] and alert["extraLabel"] == strings["hiddenUsePattern"] and alert["title"] == strings["hiddenChangePattern"], alert)
        alert = enter(core, "12")
        core.check("a short PIN is refused with the PIN message", alert and alert["hint"] == strings["hiddenInvalidPin"] and alert["inputLabel"] == strings["hiddenNewPin"], alert)
        alert = enter(core, "2468")
        core.check("it asks for the PIN again", alert and alert["inputLabel"] == strings["hiddenConfirmPin"] and alert["inputSecret"], alert)
        alert = enter(core, "2468")
        chrome = chrome_of(core)
        core.check("the change closes the box and the bar says Change PIN", alert is None and chrome["hiddenChangeLabel"] == strings["hiddenChangePin"] and chrome["hiddenRemoveLabel"] == strings["hiddenRemovePin"], (alert, chrome["hiddenChangeLabel"]))
        core.action("HiddenLock")
        core.pump(0.4)

        print("the PIN now opens it, the pattern does not")
        alert = open_from_more(core)
        core.check("the unlock step is a PIN box again", alert and alert["inputSecret"] and not alert["inputPattern"] and alert["inputLabel"] == strings["hiddenEnterPin"] and alert["linkLabel"] == strings["hiddenForgotPin"], alert)
        alert = enter(core, "1235")
        core.check("the old pattern is a wrong PIN now", alert and alert["hint"] == strings["hiddenWrongPin"].replace("{n}", "4"), alert)
        alert = enter(core, "2468")
        core.check("the new PIN opens it", alert is None and chrome_of(core)["hiddenOpen"], alert)
        core.action("HiddenLock")
        core.pump(0.3)
        shown = "".join(core.snapshot_text(c) or "" for c in SECRET_CHANNELS)
        core.check("no pattern or PIN is kept in the UI state", "1235" not in shown and "2468" not in shown)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
