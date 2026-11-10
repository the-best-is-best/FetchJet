import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, SERVER, install_fake_ytdlp
from shots import shot

GENERAL, DOWNLOADS, BROWSER, NETWORK, MEDIA, UPDATES = range(6)
ALERT = {name: index for index, name in enumerate(ENUMS["AlertKind"])}
LINE = {name: index for index, name in enumerate(ENUMS["LinkLineCode"])}


def run(only=None):
    core = Core("shots-settings", before_start=install_fake_ytdlp)
    failed = 0

    def take(name, **kwargs):
        nonlocal failed
        if only and not any(part in name for part in only):
            return
        core.pump(0.5)
        if not shot(core, name, **kwargs):
            failed += 1

    def page(index):
        core.action("SetSettingsSection", index=index)
        core.pump(0.6)

    try:
        core.action("SystemTheme", dark=True)
        devices = ("Galaxy A56 \u00b7 Android|Android|paired;Office-PC \u00b7 Windows|Windows|;MacBook \u00b7 macOS|macOS|;"
                   "Old phone \u00b7 Android|Android|paired")
        core.debug("addDirect", {"url": SERVER + "/file/40MB.bin?key=shots-devices&throttle=200", "fileName": "big-video.mkv"})
        shared = core.wait_task("DOWNLOADING", timeout=40, file_name="big-video.mkv")
        core.action("RowClick", id=shared["id"], control=False, shift=False)
        core.action("DetailsShare")
        take("device-sheet-searching-dark", width=1100, height=750)
        core.debug("devices", {"devices": devices})
        take("device-sheet-dark", width=1100, height=820)
        core.action("DeviceSetDeleteAfter", on=True)
        take("device-confirm-dark", width=1100, height=820, extra=["--click", "765,702", "--wait", "200"])
        core.action("DeviceSetDeleteAfter", on=False)
        core.action("ToggleTheme")
        core.action("SettingPick", id="language", index=2)
        core.pump(1.0)
        take("device-confirm-light-ar", width=1100, height=820, extra=["--click", "335,702", "--wait", "200"])
        core.action("SettingPick", id="language", index=1)
        core.action("ToggleTheme")
        core.pump(1.0)
        core.action("DeviceToggleManual")
        core.debug("devices", {"devices": devices, "sending": "sending:0.4"})
        take("device-sheet-sending-dark", width=1100, height=900)
        core.debug("devices", {"devices": devices, "pairing": "confirm:482913"})
        take("device-pairing-dark", width=1100, height=820)
        core.action("ToggleTheme")
        core.action("SettingPick", id="language", index=2)
        core.pump(1.0)
        core.debug("devices", {"devices": devices, "sending": "waiting"})
        take("device-sheet-light-ar", width=1100, height=900)
        core.action("SettingPick", id="language", index=1)
        core.action("ToggleTheme")
        core.debug("devices", {"devices": devices})
        core.action("DeviceDismiss")
        core.debug("devices", {})
        core.action("Remove", id=shared["id"])
        core.action("ConfirmRemove", deleteFiles=True)
        core.action("CloseDetails")
        core.pump(0.5)
        if not only or any("receiv" in part or "preparing" in part for part in only):
            core.debug("devices", {"devices": devices, "receiving": "0.62"})
            take("device-receiving-dark", width=1100, height=760)
            core.debug("devices", {"devices": devices, "receiving": "unknown"})
            take("device-receiving-unknown-dark", width=1100, height=760)
            core.debug("devices", {"devices": devices, "receiving": "failed:no-answer"})
            take("device-receive-failed-dark", width=1100, height=760)
            parts = os.path.join(core.work, "recv-in", "recv-1")
            os.makedirs(parts, exist_ok=True)
            with open(os.path.join(parts, "holiday-video.mp4.part0"), "wb") as handle:
                handle.write(bytes(4096))
            with open(os.path.join(parts, "holiday-video.mp4.chunks"), "w") as handle:
                handle.write("0 0 372244479")
            core.debug("receive", {"url": SERVER + "/file/40MB.bin?key=shots-recv&delay=120000", "fileName": "holiday-video.mp4",
                                   "total": "372244480", "downloaded": "50331648", "from": "Galaxy A56",
                                   "parts": os.path.join(parts, "holiday-video.mp4.part0") + "|" + os.path.join(parts, "holiday-video.mp4.chunks")})
            core.debug("receive", {"url": SERVER + "/file/40MB.bin?key=shots-recv2&delay=120000", "fileName": "song-link.mp3",
                                   "total": "8388608", "from": "Galaxy A56"})
            core.debug("devices", {})
            core.pump(1.0)
            take("row-preparing-dark", width=1200, height=520)
            core.action("SettingPick", id="language", index=2)
            core.pump(1.0)
            take("row-preparing-dark-ar", width=1200, height=520)
            core.debug("devices", {"devices": devices, "receiving": "0.62"})
            take("device-receiving-dark-ar", width=1100, height=760)
            core.debug("devices", {})
            core.action("SettingPick", id="language", index=1)
            core.pump(0.5)
            for name in ("holiday-video.mp4", "song-link.mp3"):
                t = core.task(file_name=name)
                if t:
                    core.action("Remove", id=t["id"])
                    core.action("ConfirmRemove", deleteFiles=True)
            core.pump(0.5)
        core.action("ShowAddDialog", show=True)
        core.action("SetUrl", text="\n".join([
            SERVER + "/file/1MB.bin?key=shots-links-a",
            SERVER + "/file/photos-2026.zip?key=shots-links-b",
            "https://www.youtube.com/watch?v=fakevideo01",
            "https://www.youtube.com/watch?v=broken01",
            "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Sintel+4K",
        ]))
        core.wait_for("the videos to be read", lambda: len(core.rows("addLinks")) == 5 and
                      core.rows("addLinks")[2].get("line", 0) == LINE["QUALITIES"] and core.rows("addLinks")[3].get("line", 0) == LINE["FAILED"], 90)
        take("add-links-dark", width=1100, height=900)
        take("add-links-dark-2", width=1100, height=900, extra=["--wait", "200", "--scroll", "550,450,0,-500", "--wait", "400"])
        core.action("SettingPick", id="language", index=2)
        core.pump(1.0)
        take("add-links-dark-ar", width=1100, height=900)
        core.action("SettingPick", id="language", index=1)
        core.action("ShowAddDialog", show=False)
        core.action("SetUrl", text="")
        core.action("OpenSettings")
        core.pump(1.0)
        take("settings-general-dark", width=1200, height=900)
        take("settings-general-dark-2", width=1200, height=900, extra=["--wait", "200", "--scroll", "800,500,0,-600", "--wait", "400"])
        page(DOWNLOADS)
        take("settings-downloads-dark", width=1200, height=900)
        core.action("SettingToggle", id="schedule", on=True)
        core.action("SettingClick", id="addWindow")
        take("settings-downloads-dark-2", width=1200, height=900, extra=["--wait", "200", "--scroll", "800,500,0,-560", "--wait", "400"])
        take("settings-downloads-dark-3", width=1200, height=900, extra=["--wait", "200", "--scroll", "800,500,0,-1300", "--wait", "400"])
        core.action("SettingClick", id="window.0.remove")
        core.action("SettingToggle", id="schedule", on=False)
        page(BROWSER)
        take("settings-browser-dark", width=1200, height=760)
        page(NETWORK)
        take("settings-network-dark", width=1200, height=900)
        core.action("SettingPick", id="proxyKind", index=1)
        core.action("SettingText", id="proxyHost", text="proxy.example.net")
        core.action("SettingText", id="proxyPort", text="99999")
        core.action("SettingText", id="proxyUser", text="michelle")
        core.action("SettingText", id="proxyPassword", text="secret")
        core.action("SettingText", id="loginSite", text="files.example.com")
        core.action("SettingText", id="loginUser", text="reader")
        take("settings-network-dark-2", width=1200, height=980)
        core.action("SettingPick", id="proxyKind", index=0)
        page(MEDIA)
        take("settings-media-dark", width=1200, height=760)
        page(UPDATES)
        take("settings-updates-dark", width=1200, height=760)
        notes = ("## What is new\n- Faster start and a much smaller memory use\n- **Native** Windows app\n"
                 "- Speed schedule in Settings > Downloads\n- See the [full list](https://example.com/notes)\n\n"
                 "## Fixes\n- Pausing a download closes its connections at once\n- Arabic file names in the list\n")
        core.debug("update", {"version": "1.5.0", "notes": notes, "assetName": "FetchJet-1.5.0.msi", "assetSize": "48234567"})
        take("update-dialog-dark", width=1100, height=750)
        core.debug("update", {"version": "1.5.0", "notes": notes, "assetName": "FetchJet-1.5.0.msi", "assetSize": "48234567",
                              "canInstall": "true", "progress": "0.42"})
        take("update-dialog-progress-dark", width=1100, height=750)
        core.action("UpdateLater", fromScrim=False)
        take("settings-updates-available-dark", width=1200, height=760)
        core.debug("update", {})
        core.debug("extensionSteps", {"folder": "C:\\Users\\name\\AppData\\Local\\FetchJetDev\\.fetch_jet\\extension\\chromium"})
        take("extension-steps-dark", width=1100, height=750)
        core.action("AlertDismiss", kind=ALERT["EXTENSION_STEPS"], fromScrim=False)
        core.debug("sections", {"all": "true"})
        core.pump(0.5)
        page(6)
        core.action("SettingPick", id="uploadLimit", index=5)
        take("settings-torrent-dark", width=1200, height=760)
        take("settings-torrent-narrow-dark", width=700, height=700)
        core.action("SettingPick", id="uploadLimit", index=0)
        page(3)
        take("settings-devices-dark", width=1200, height=760)
        core.debug("sections", {"all": "false"})
        core.pump(0.5)
        page(GENERAL)
        take("settings-narrow-list-dark", width=760, height=700)
        core.action("SetSettingsSection", index=-1)
        take("settings-narrow-list-dark-2", width=760, height=700)
        page(DOWNLOADS)
        take("settings-narrow-page-dark", width=760, height=700)
        core.action("ToggleTheme")
        page(GENERAL)
        take("settings-general-light", width=1200, height=900)
        page(DOWNLOADS)
        take("settings-downloads-light", width=1200, height=900)
        core.action("SettingPick", id="language", index=2)
        core.pump(1.0)
        take("settings-general-light-ar", width=1200, height=900)
        page(DOWNLOADS)
        take("settings-downloads-light-ar", width=1200, height=900)
        page(NETWORK)
        core.action("SettingPick", id="proxyKind", index=2)
        take("settings-network-light-ar", width=1200, height=900)
        core.action("SettingPick", id="proxyKind", index=0)
        page(MEDIA)
        core.action("ToggleTheme")
        take("settings-media-dark-ar", width=1200, height=760)
        core.action("SettingPick", id="language", index=1)
    except CheckFailed as failure:
        print("FAILED: " + str(failure))
        print(core.log_text()[-2000:])
        failed += 1
    core.shutdown()
    print("done, %d failed" % failed)
    return failed


if __name__ == "__main__":
    sys.exit(1 if run(sys.argv[1:]) else 0)
