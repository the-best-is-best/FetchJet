import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, install_fake_ytdlp

KIND = {name: index for index, name in enumerate(ENUMS["SettingKind"])}
TONE = {name: index for index, name in enumerate(ENUMS["ToneCode"])}
ALERT = {name: index for index, name in enumerate(ENUMS["AlertKind"])}
GENERAL, DOWNLOADS, BROWSER, NETWORK, MEDIA, UPDATES = range(6)


def nav(core):
    return core.snapshot("settingsNav") or {}


def rows(core):
    return core.rows("settingsRows")


def row(core, key):
    for item in rows(core):
        if item.get("id") == key or item.get("one", {}).get("id") == key or item.get("two", {}).get("id") == key:
            return item
    return {}


def field(core, key):
    item = row(core, key)
    return item.get("one", {}) if item.get("one", {}).get("id") == key else item.get("two", {})


def button(core, key):
    for item in rows(core):
        for entry in item.get("buttons", []):
            if entry.get("id") == key:
                return entry
    return {}


def hint(core, index):
    return nav(core).get("sections", [])[index]


def open_page(core, index):
    core.action("SetSettingsSection", index=index)
    core.pump(0.5)


def run():
    core = Core("settings", before_start=install_fake_ytdlp)
    try:
        core.pump(1.0)
        strings = core.strings()
        core.check("closed: no rows are built", len(rows(core)) == 0 and not nav(core).get("sections"))

        print("open settings")
        core.action("OpenSettings")
        core.pump(0.6)
        sections = nav(core).get("sections", [])
        core.check("six pages (no devices, no torrents yet)", [s.get("title") for s in sections] == [
            strings["sectionGeneral"], strings["sectionDownloads"], strings["sectionBrowser"], strings["sectionNetwork"],
            strings["sectionMedia"], strings["sectionUpdates"]], [s.get("title") for s in sections])
        core.check("the first page is shown, the list is still the start", nav(core).get("current", 0) == 0 and
                   not nav(core).get("pageOpen", False) and sections[0].get("selected") and nav(core).get("title") == strings["sectionGeneral"])
        core.check("the home screen knows", core.snapshot("chrome").get("settingsOpen"))
        core.check("media page says setup needed (no FFmpeg)", sections[MEDIA].get("hint") == strings["summarySetupNeeded"] and
                   sections[MEDIA].get("hintTone") == TONE["PAUSED"], sections[MEDIA])

        print("general")
        language = row(core, "language")
        core.check("language menu: system + 15 languages", language.get("kind") == KIND["MENU"] and len(language.get("options", [])) == 16 and
                   language.get("pick") == 1, language.get("pick"))
        core.check("clipboard watch is off and the second switch is disabled", not row(core, "watchClipboard").get("checked", False) and
                   not row(core, "clipboardVideoPages").get("enabled", False))
        core.check("groups have a caption on the first row and a footer on the last", row(core, "watchClipboard").get("first") and
                   row(core, "watchClipboard").get("caption") == strings["clipboardGroup"].upper() and
                   row(core, "clipboardVideoPages").get("footer") == strings["clipboardFooter"] and
                   row(core, "clipboardVideoPages").get("divider"))
        core.action("SettingPick", id="theme", index=2)
        core.pump(0.4)
        core.check("dark theme picked", row(core, "theme").get("pick") == 3 and core.snapshot("shell").get("dark") and
                   hint(core, GENERAL).get("hint", "").startswith(strings["settingsThemeDark"]), hint(core, GENERAL))
        core.action("SettingPick", id="theme", index=1)
        core.pump(0.4)
        core.check("light theme picked", not core.snapshot("shell").get("dark", False))
        core.action("SettingPick", id="theme", index=0)
        core.action("SettingPick", id="speedUnit", index=2)
        core.pump(0.4)
        core.check("speed unit Mbps", row(core, "speedUnit").get("pick") == 3 and hint(core, GENERAL).get("hint", "").endswith("Mbps"),
                   hint(core, GENERAL))
        core.action("SettingPick", id="speedUnit", index=0)
        core.check("when-done choice is listed", len(row(core, "whenDone").get("options", [])) == 4 and row(core, "whenDone").get("pick") == 1)

        print("language")
        open_page(core, DOWNLOADS)
        core.check("a page is open", nav(core).get("pageOpen") and nav(core).get("current") == DOWNLOADS)
        open_page(core, GENERAL)
        core.action("SettingPick", id="language", index=2)
        core.pump(1.0)
        tr = core.snapshot("tr")
        core.check("Arabic: strings and direction change", tr.get("lang") == "ar" and tr.get("rtl"), tr.get("lang"))
        core.check("the panel starts again at the list", not nav(core).get("pageOpen", False))
        core.check("rows are rebuilt in Arabic", row(core, "language").get("pick") == 3 and
                   row(core, "watchClipboard").get("title") == core.strings()["watchClipboard"] != strings["watchClipboard"])
        core.action("SettingPick", id="language", index=1)
        core.pump(1.0)
        core.check("English again", core.snapshot("tr").get("lang") == "en" and row(core, "language").get("pick") == 2)

        print("downloads: folder")
        open_page(core, DOWNLOADS)
        folder = field(core, "destination")
        core.check("the save folder is the downloads folder", os.path.normcase(folder.get("value", "")) ==
                   os.path.normcase(os.path.normpath(core.downloads)), folder.get("value"))
        core.check("no 'use Downloads' button while it is the default", not button(core, "useDownloads") and button(core, "chooseFolder").get("filled"))
        other = os.path.join(core.home, "other folder")
        core.action("SettingText", id="destination", text=other)
        core.pump(0.4)
        core.check("typed folder is saved and not echoed back", core.debug("state")["destinationPath"] == other and
                   field(core, "destination").get("rev", 0) == folder.get("rev", 0) and hint(core, DOWNLOADS).get("hint", "").startswith("other folder"),
                   hint(core, DOWNLOADS))
        core.check("'use Downloads' is offered now", button(core, "useDownloads").get("enabled"))
        core.action("SettingClick", id="useDownloads")
        core.pump(0.4)
        core.check("back to Downloads, the field is told to take it", os.path.normcase(field(core, "destination").get("value", "")) ==
                   os.path.normcase(os.path.normpath(core.downloads)) and field(core, "destination").get("rev", 0) > folder.get("rev", 0))

        print("downloads: connections, presets, queue")
        core.action("SettingPick", id="connections", index=3)
        core.pump(0.4)
        core.check("8 connections picked", row(core, "connections").get("pick") == 4)
        core.action("SettingPick", id="connections", index=0)
        fast = row(core, "preset.fast")
        core.check("fast preset row", fast.get("kind") == KIND["PRESET"] and fast.get("one", {}).get("value") != "" and
                   fast.get("options") == ["KB/s", "MB/s", "Kbps", "Mbps"], fast)
        first_value, first_pick = fast["one"]["value"], fast["pick"]
        core.action("SettingText", id="preset.fast", text="7")
        core.pump(0.4)
        core.check("typed 7 with MB/s", row(core, "preset.fast").get("side") == "7 MB/s", row(core, "preset.fast").get("side"))
        core.action("SettingPick", id="preset.fast", index=0)
        core.pump(0.4)
        fast = row(core, "preset.fast")
        core.check("unit KB/s keeps the number", fast.get("side") == "7 KB/s" and fast["one"]["value"] == "7" and fast.get("pick") == 1, fast.get("side"))
        rev = fast["one"].get("rev", 0)
        core.action("SettingText", id="preset.fast", text="7x")
        core.pump(0.4)
        fast = row(core, "preset.fast")
        core.check("a letter is refused and the field is corrected", fast["one"]["value"] == "7" and fast["one"].get("rev", 0) > rev, fast["one"])
        core.action("SettingPick", id="preset.fast", index=first_pick - 1)
        core.action("SettingText", id="preset.fast", text=first_value)
        core.pump(0.4)
        core.check("preset restored", row(core, "preset.fast").get("side") == first_value + " MB/s", row(core, "preset.fast").get("side"))
        core.action("SettingPick", id="queue", index=0)
        core.pump(0.4)
        core.check("one download at a time", row(core, "queue").get("pick") == 1 and "1" in row(core, "queue").get("subtitle", ""),
                   row(core, "queue").get("subtitle"))
        core.check("the summary follows", "1" in hint(core, DOWNLOADS).get("hint", ""), hint(core, DOWNLOADS))
        core.action("SettingPick", id="queue", index=2)
        was = row(core, "sortIntoFolders").get("checked", False)
        core.action("SettingToggle", id="sortIntoFolders", on=not was)
        core.pump(0.4)
        core.check("sort into folders toggles and its text follows", row(core, "sortIntoFolders").get("checked", False) != was and
                   row(core, "sortIntoFolders").get("subtitle") == (strings["sortIntoFoldersOff"] if was else strings["sortIntoFoldersOn"]))
        core.action("SettingToggle", id="sortIntoFolders", on=was)

        print("downloads: speed schedule")
        core.check("schedule is off: only the switch", not row(core, "schedule").get("checked", False) and not button(core, "addWindow"))
        core.action("SettingToggle", id="schedule", on=True)
        core.pump(0.4)
        core.check("on: the empty note and the add button", button(core, "addWindow").get("filled") and
                   any(r.get("title") == strings["speedScheduleEmpty"] for r in rows(core)))
        core.action("SettingClick", id="addWindow")
        core.pump(0.4)
        window = row(core, "window.0.from")
        core.check("a window 8:00 AM to 6:00 PM with 1 MB/s", window.get("one", {}).get("value") == "8:00" and
                   window["one"].get("toggle") == "AM" and window.get("two", {}).get("value") == "6:00" and
                   window["two"].get("toggle") == "PM" and window.get("removeId") == "window.0.remove" and
                   row(core, "window.0.cap").get("side") == "1 MB/s", window)
        core.action("SettingText", id="window.0.from", text="9:30")
        core.action("SettingClick", id="window.0.from.pm")
        core.pump(0.4)
        saved = json.load(open(os.path.join(core.home, "schedule.json"), encoding="utf-8")) if os.path.isfile(
            os.path.join(core.home, "schedule.json")) else None
        window = row(core, "window.0.from")
        core.check("9:30 PM typed and switched", window["one"].get("value") == "9:30" and window["one"].get("toggle") == "PM", window["one"])
        if saved is not None:
            core.check("the schedule file has 21:30", saved.get("enabled") and saved["windows"][0]["startMinutes"] == 21 * 60 + 30, saved)
        rev = window["one"].get("rev", 0)
        core.action("SettingText", id="window.0.from", text="99:99")
        core.pump(0.4)
        window = row(core, "window.0.from")
        core.check("a wrong time stays in the field without an echo", window["one"].get("value") == "99:99" and window["one"].get("rev", 0) == rev)
        core.action("SettingText", id="window.0.cap", text="2.5")
        core.pump(0.4)
        core.check("the window's limit follows", row(core, "window.0.cap").get("side") == "2.5 MB/s", row(core, "window.0.cap").get("side"))
        core.action("SettingClick", id="window.0.remove")
        core.pump(0.4)
        core.check("removed", not row(core, "window.0.from") and button(core, "addWindow"))
        core.action("SettingToggle", id="schedule", on=False)
        core.pump(0.3)

        print("network: proxy")
        open_page(core, NETWORK)
        core.check("proxy is off, nothing to save", row(core, "proxyKind").get("pick") == 1 and not button(core, "proxySave").get("enabled", False) and
                   not field(core, "proxyHost"))
        core.action("SettingPick", id="proxyKind", index=1)
        core.pump(0.4)
        core.check("HTTP: fields appear, password waits for a user name", field(core, "proxyHost").get("enabled") and
                   field(core, "proxyPort").get("width") == 110 and not field(core, "proxyPassword").get("enabled", False) and
                   field(core, "proxyPassword").get("secret") and not button(core, "proxySave").get("enabled", False))
        core.action("SettingText", id="proxyHost", text="127.0.0.1")
        core.pump(0.3)
        core.check("server without a port is an error", any(r.get("title") == strings["netProxyBadPort"] and r.get("tone") == TONE["ERROR"]
                                                              for r in rows(core)))
        port_rev = field(core, "proxyPort").get("rev", 0)
        core.action("SettingText", id="proxyPort", text="18089x")
        core.pump(0.4)
        core.check("port keeps digits only", field(core, "proxyPort").get("value") == "18089" and field(core, "proxyPort").get("rev", 0) > port_rev)
        core.check("now it can be saved", button(core, "proxySave").get("enabled") and
                   not any(r.get("title") == strings["netProxyBadPort"] for r in rows(core)))
        core.action("SettingClick", id="proxySave")
        core.pump(0.5)
        core.check("saved: label, summary in green", any(r.get("status") == strings["netSaved"] for r in rows(core)) and
                   hint(core, NETWORK).get("hint") == "HTTP 127.0.0.1:18089" and hint(core, NETWORK).get("hintTone") == TONE["COMPLETED"],
                   hint(core, NETWORK))
        network_file = os.path.join(core.home, "network.json")
        if os.path.isfile(network_file):
            core.check("network.json has the proxy", json.load(open(network_file, encoding="utf-8"))["proxy"]["port"] == 18089)
        core.action("SettingPick", id="proxyKind", index=0)
        core.pump(0.3)
        core.check("switching to Off waits for Save", button(core, "proxySave").get("enabled") and hint(core, NETWORK).get("hint") != strings["netProxyOff"])
        core.action("SettingClick", id="proxySave")
        core.pump(0.5)
        core.check("proxy off again", hint(core, NETWORK).get("hint") == strings["netProxyOff"], hint(core, NETWORK))

        print("network: site logins")
        core.check("no logins yet", any(r.get("title") == strings["netLoginsEmpty"] for r in rows(core)) and
                   not button(core, "loginAdd").get("enabled", False))
        core.action("SettingText", id="loginSite", text="https://www.Example.com/files")
        core.action("SettingText", id="loginUser", text="reader")
        core.action("SettingText", id="loginPassword", text="pw")
        core.pump(0.4)
        core.check("add is possible", button(core, "loginAdd").get("enabled"))
        site_rev = field(core, "loginSite").get("rev", 0)
        core.action("SettingClick", id="loginAdd")
        core.pump(0.5)
        login = [r for r in rows(core) if r.get("title") == "example.com"]
        core.check("the login is listed by its clean site name", len(login) == 1 and login[0].get("subtitle") == "reader" and
                   login[0]["buttons"][0]["id"] == "login.remove.example.com", login)
        core.check("the form is emptied", field(core, "loginSite").get("value", "") == "" and field(core, "loginSite").get("rev", 0) > site_rev and
                   field(core, "loginUser").get("value", "") == "")
        core.check("the summary counts it", hint(core, NETWORK).get("hint") != strings["netProxyOff"], hint(core, NETWORK))
        core.action("SettingClick", id="login.remove.example.com")
        core.pump(0.4)
        core.check("removed", any(r.get("title") == strings["netLoginsEmpty"] for r in rows(core)))

        print("media tools")
        open_page(core, MEDIA)
        listed = rows(core)
        core.check("yt-dlp ready, FFmpeg missing", listed[0].get("title") == "yt-dlp" and listed[0].get("status") == strings["settingsReady"] and
                   listed[0].get("statusTone") == TONE["COMPLETED"] and listed[1].get("status") == strings["ffmpegMissing"], listed[:2])
        tools = button(core, "tools")
        core.check("the button offers the download", tools.get("label") == strings["mediaDownloadTheTools"] and tools.get("filled") and
                   tools.get("enabled") and not tools.get("busy", False), tools)
        core.check("it stands outside the card", any(r.get("bare") and r.get("kind") == KIND["BUTTONS"] for r in listed))
        core.check("the tools folder row opens the folder", row(core, "toolsFolder").get("clickable") and
                   os.path.normcase(row(core, "toolsFolder").get("subtitle", "")).startswith(os.path.normcase(os.path.normpath(core.home))))

        print("updates")
        open_page(core, UPDATES)
        listed = rows(core)
        core.check("version row with the check button", listed[0].get("title", "").startswith("FetchJet ") and
                   button(core, "updateCheck").get("enabled") and listed[0].get("subtitle") == strings["youHaveLatest"], listed[0])
        auto = row(core, "autoCheck").get("checked", False)
        core.action("SettingToggle", id="autoCheck", on=not auto)
        core.pump(0.4)
        core.check("automatic check toggles", row(core, "autoCheck").get("checked", False) != auto)
        core.action("SettingToggle", id="autoCheck", on=auto)
        core.check("release notes row is a link", row(core, "releaseNotes").get("clickable") and row(core, "releaseNotes").get("side") == "↗")

        print("a new version")
        core.check("no update dialog", not (core.snapshot("updateDialog") or {}).get("open", False))
        core.debug("update", {"version": "9.9.0", "notes": "## New\n- **Fast** start\n- [Notes](https://example.com/n)\n",
                              "assetName": "FetchJet-9.9.0.msi", "assetSize": "1048576"})
        core.pump(0.5)
        dialog = core.snapshot("updateDialog")
        core.check("the dialog opens with both versions", dialog.get("open") and "9.9.0" in dialog.get("versionLine", "") and
                   listed[0].get("title", "")[9:] in dialog.get("versionLine", ""), dialog.get("versionLine"))
        core.check("notes are shown as plain text", dialog.get("notes") == "New\n\u2022 Fast start\n\u2022 Notes", dialog.get("notes"))
        core.check("this build cannot install by itself: it offers the file", "1.0 MB" in dialog.get("confirmLabel", "") and
                   dialog.get("confirmEnabled") and dialog.get("skipLabel") == strings["updateSkipThisVersion"] and
                   not dialog.get("locked", False), dialog.get("confirmLabel"))
        core.check("the page offers it too", button(core, "updateSee").get("filled") and hint(core, UPDATES).get("hintTone") == TONE["COMPLETED"] and
                   "9.9.0" in hint(core, UPDATES).get("hint", ""), hint(core, UPDATES))
        core.action("UpdateLater", fromScrim=True)
        core.pump(0.4)
        core.check("later closes it", not core.snapshot("updateDialog").get("open", False) and button(core, "updateSee").get("filled"))
        core.action("SettingClick", id="updateSee")
        core.pump(0.4)
        core.check("'see what is new' opens it again", core.snapshot("updateDialog").get("open"))
        core.debug("update", {"version": "9.9.0", "notes": "x", "assetName": "FetchJet-9.9.0.msi", "canInstall": "true", "progress": "0.5"})
        core.pump(0.4)
        dialog = core.snapshot("updateDialog")
        core.check("while downloading: progress, no skip, the button waits", dialog.get("progressShown") and abs(dialog.get("progress", 0) - 0.5) < 0.01 and
                   dialog.get("skipLabel", "") == "" and not dialog.get("confirmEnabled", False) and dialog.get("locked"), dialog)
        core.action("UpdateLater", fromScrim=True)
        core.pump(0.3)
        core.check("a click outside does not close it then", core.snapshot("updateDialog").get("open"))
        core.debug("update", {})
        core.pump(0.4)
        core.check("gone", not core.snapshot("updateDialog").get("open", False) and button(core, "updateCheck").get("enabled"))

        print("browser extension")
        open_page(core, BROWSER)
        core.wait_for("the browsers", lambda: len(rows(core)) > 0, 15)
        listed = rows(core)
        print("    " + ", ".join("%s%s" % (r.get("title"), "" if r["buttons"][0].get("filled") else " (not installed)") for r in listed if r.get("buttons")))
        core.check("every browser has one button", all(len(r.get("buttons", [])) == 1 for r in listed) and len(listed) >= 3, listed)
        missing = [r for r in listed if r.get("subtitle") == strings["browserNotInstalled"]]
        if missing:
            core.check("a browser that is not installed has the warning sign", missing[0].get("subtitleEmoji") != "")
            core.action("SettingClick", id=missing[0]["buttons"][0]["id"])
            core.pump(0.4)
            alerts = core.rows("alerts")
            core.check("asking for it shows the download question", len(alerts) == 1 and alerts[0].get("kind") == ALERT["EXTENSION_MISSING"] and
                       missing[0]["title"] in alerts[0].get("title", ""), alerts)
            core.action("AlertDismiss", kind=ALERT["EXTENSION_MISSING"], fromScrim=False)
            core.pump(0.4)
            core.check("closed", len(core.rows("alerts")) == 0)

        print("pages that wait for their engines")
        core.debug("sections", {"all": "true"})
        core.pump(0.5)
        titles = [s.get("title") for s in nav(core).get("sections", [])]
        core.check("all eight pages in the desktop order", titles == [
            strings["sectionGeneral"], strings["sectionDownloads"], strings["sectionBrowser"], strings["sectionDevices"],
            strings["sectionNetwork"], strings["sectionMedia"], strings["sectionTorrent"], strings["sectionUpdates"]], titles)
        open_page(core, titles.index(strings["sectionTorrent"]))
        chips = row(core, "uploadLimit")
        core.check("upload limit: six choices, no limit picked", len(chips.get("options", [])) == 6 and chips.get("pick") == 1 and
                   not row(core, "uploadLimit.custom") and rows(core)[0].get("side") == strings["speedLimitNoLimit"], chips)
        core.action("SettingPick", id="uploadLimit", index=2)
        core.pump(0.4)
        core.check("1 MB/s picked", row(core, "uploadLimit").get("pick") == 3 and rows(core)[0].get("side") == "1 MB/s", rows(core)[0].get("side"))
        core.action("SettingPick", id="uploadLimit", index=5)
        core.pump(0.4)
        box = row(core, "uploadLimit.custom")
        core.check("custom starts at 2 MB/s", row(core, "uploadLimit").get("pick") == 6 and box.get("one", {}).get("value") == "2" and
                   box.get("pick") == 2 and box.get("hint") == "= 2 MB/s" and rows(core)[0].get("side") == "2 MB/s", box)
        core.action("SettingText", id="uploadLimit.custom", text="3.5")
        core.pump(0.4)
        core.check("typed 3.5", rows(core)[0].get("side") == "3.5 MB/s" and row(core, "uploadLimit.custom").get("hint") == "= 3 MB/s",
                   rows(core)[0].get("side"))
        core.action("SettingPick", id="uploadLimit.custom", index=3)
        core.pump(0.4)
        box = row(core, "uploadLimit.custom")
        core.check("the unit Mbps changes the limit", box.get("pick") == 4 and rows(core)[0].get("side") == "427 KB/s" and
                   box.get("hint") == "= 3 Mbps (0 MB/s)", (rows(core)[0].get("side"), box.get("hint")))
        core.action("SettingText", id="uploadLimit.custom", text="")
        core.pump(0.4)
        core.check("an empty box means no limit", row(core, "uploadLimit").get("pick") == 1 and not row(core, "uploadLimit.custom"))
        leech = row(core, "leech").get("checked", False)
        core.action("SettingToggle", id="leech", on=not leech)
        core.pump(0.4)
        core.check("download-only switch", row(core, "leech").get("checked", False) != leech and
                   (hint(core, titles.index(strings["sectionTorrent"])).get("hint") == (strings["summaryUploadingOn"] if leech else strings["summaryUploadingOff"])))
        core.action("SettingToggle", id="leech", on=leech)
        open_page(core, titles.index(strings["sectionDevices"]))
        core.check("devices page: hidden by default, nothing paired", not row(core, "visibleToDevices").get("checked", False) and
                   any(r.get("title") == strings["noPairedDevices"] for r in rows(core)) and
                   any(r.get("title") == strings["noBlockedDevices"] for r in rows(core)))
        core.debug("sections", {"all": "false"})
        core.pump(0.5)

        print("leave")
        core.action("SetSettingsSection", index=-1)
        core.pump(0.3)
        core.check("back to the list of pages", not nav(core).get("pageOpen", False))
        core.action("SetFilter", filter=0)
        core.pump(0.5)
        core.check("settings closed: nothing is kept", len(rows(core)) == 0 and not core.snapshot("chrome").get("settingsOpen", False))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
