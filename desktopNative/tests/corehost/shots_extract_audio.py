import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, ENUMS, REPO
from shots import SHOT
from test_compress import install_ffmpeg, serve_folder

MEDIA = os.environ.get("FJ_AUDIO_MEDIA", "E:/tools/probes/extract-desktop/media")
OUT = os.environ.get("FJ_SHOT_OUT", "E:/tools/probes/extract-desktop")
BEFORE = os.environ.get("FJ_BEFORE_UI", "E:/tools/probes/extract-desktop/before-ui/desktopNative")
AFTER = os.path.join(REPO, "desktopNative")


def strip_new(data):
    for row in data["Core"].get("rowMenu", []):
        row.pop("header", None)
        row.pop("disabled", None)
    for tool in data["Core"].get("details", {}).get("fileTools", []) + data["Core"].get("details", {}).get("mediaTools", []):
        tool.pop("disabled", None)
    old = open(os.path.join(BEFORE, "ui", "generated", "tr.slint"), encoding="utf-8").read()
    data["Tr"] = {k: v for k, v in data["Tr"].items() if k == "lang" or ("<string> " + k + ":") in old}
    return data


def render(core, name, width, height, extra=None):
    os.makedirs(OUT, exist_ok=True)
    fixture = os.path.join(OUT, name + ".json")
    data = core.fixture(fixture, {"Emoji": {"ready": True}})
    for which, root in (("after", AFTER), ("before", BEFORE)):
        path = fixture
        if which == "before":
            path = os.path.join(OUT, name + "-before.json")
            with open(path, "w", encoding="utf-8") as handle:
                json.dump(strip_new(json.loads(json.dumps(data))), handle, ensure_ascii=False)
        command = [SHOT, os.path.join(root, "ui", "app.slint"), "AppWindow", "--json", path, "--width", str(width), "--height", str(height),
                   "--emoji-callback", "Emoji.icon", "--out", os.path.join(OUT, name + "-" + which + ".png"), "--quiet",
                   "--font-dir", os.path.join(root, "res", "fonts"), "--emoji-dir", os.path.join(root, "res", "emoji"),
                   "--dark" if data["Theme"]["dark"] else "--light"] + (extra or [])
        result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
        print(("  shot  " if result.returncode == 0 else "  FAIL  ") + name + "-" + which +
              ("" if result.returncode == 0 else " : " + (result.stdout + result.stderr).strip()[-600:]))


def run():
    core = Core("shots-extract-audio", before_start=install_ffmpeg)
    server = serve_folder(MEDIA)
    try:
        port = server.server_address[1]
        core.debug("addDirect", {"url": "http://127.0.0.1:%d/talk.mp4" % port, "fileName": "talk.mp4"})
        video = core.wait_task("COMPLETED", timeout=60, file_name="talk.mp4")
        core.debug("addDirect", {"url": "http://127.0.0.1:%d/silent.mp4" % port, "fileName": "notes.mp4"})
        core.wait_task("COMPLETED", timeout=60, file_name="notes.mp4")
        core.pump(0.5)
        rows = [r for r in core.rows("tasks") if r.get("id")]
        index = next(i for i, r in enumerate(rows) if r.get("id") == video["id"])
        for language in ("en", "ar"):
            core.action("SetLanguage", choice=language)
            for dark, theme in ((True, "dark"), (False, "light")):
                core.action("SystemTheme", dark=dark)
                core.action("CloseDetails")
                core.action("RowMenu", id=video["id"])
                core.pump(0.8)
                x = 400 if language == "ar" else 640
                render(core, "rowmenu-%s-%s" % (theme, language), 1100, 640, ["--right-click", "%d,%d" % (x, 140 + index * 74)])
                if theme == "dark":
                    render(core, "rowmenu-small-%s-%s" % (theme, language), 760, 480, ["--right-click", "%d,%d" % (x - 200, 140 + index * 74)])
                core.action("RowMenuClose")
                core.action("RowClick", id=video["id"], control=False, shift=False)
                core.pump(0.8)
                render(core, "details-%s-%s" % (theme, language), 1360, 900)
        core.action("SetLanguage", choice="en")
        core.action("SystemTheme", dark=True)
        core.action("DetailsTool", code=ENUMS["DetailToolCode"].index("EXTRACT_AUDIO"))
        core.pump(0.15)
        render(core, "details-running-dark-en", 1360, 900)
        core.pump(3)
        core.action("CloseDetails")
        core.action("RowMenu", id=video["id"])
        core.pump(0.2)
        core.action("RowMenuPick", key=next(r["key"] for r in core.rows("rowMenu") if r.get("label") == core.strings()["audioExtractTitle"]))
        core.action("RowMenu", id=video["id"])
        core.pump(0.05)
        render(core, "rowmenu-running-dark-en", 1100, 640, ["--right-click", "640,%d" % (140 + index * 74)])
        core.action("RowMenuClose")
        core.pump(3)
    finally:
        server.shutdown()
        core.finish()


if __name__ == "__main__":
    run()
