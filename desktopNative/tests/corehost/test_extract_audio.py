import hashlib
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS
from test_compress import install_ffmpeg, serve_folder, FFMPEG_SOURCE

MEDIA = os.environ.get("FJ_AUDIO_MEDIA", "E:/tools/probes/extract-desktop/media")
TOOL = {name: index for index, name in enumerate(ENUMS["DetailToolCode"])}


def audio_hash(path):
    result = subprocess.run([FFMPEG_SOURCE, "-hide_banner", "-v", "error", "-i", path, "-map", "0:a:0", "-c", "copy", "-f", "data", "-"],
                            capture_output=True, timeout=120)
    return hashlib.md5(result.stdout).hexdigest() if result.returncode == 0 and result.stdout else ""


def has_video(path):
    result = subprocess.run([FFMPEG_SOURCE, "-hide_banner", "-i", path], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=60)
    return "Video:" in result.stderr


def details(core):
    return core.snapshot("details") or {}


def tool(core, code):
    return next((t for t in details(core).get("fileTools", []) if t.get("code", 0) == code), None)


def find(core, name):
    return next((os.path.join(r, name) for r, _, f in os.walk(core.downloads) if name in f), "")


def audio_tasks(core):
    return [t for t in core.tasks() if "(audio)" in t["fileName"] and t["status"] == "COMPLETED"]


def toast(core):
    return (core.snapshot("shell") or {}).get("toastText", "")


def run():
    core = Core("extract-audio", before_start=install_ffmpeg)
    server = serve_folder(MEDIA)
    s = core.strings()
    try:
        port = server.server_address[1]
        core.debug("addDirect", {"url": "http://127.0.0.1:%d/talk.mp4" % port, "fileName": "talk.mp4"})
        video = core.wait_task("COMPLETED", timeout=60, file_name="talk.mp4")
        core.wait_for("the source on disk", lambda: find(core, "talk.mp4"), 20)
        source = find(core, "talk.mp4")

        print("details: the button in the file group")
        core.action("RowClick", id=video["id"], control=False, shift=False)
        core.wait_for("details", lambda: details(core).get("open"), 10)
        button = tool(core, TOOL["EXTRACT_AUDIO"])
        core.check("extract audio is in the file group", button is not None and button.get("label") == s["audioExtractTitle"], details(core).get("fileTools"))
        core.check("it is not in the media group", all(t.get("code") != TOOL["EXTRACT_AUDIO"] for t in details(core).get("mediaTools", [])))
        core.check("it is enabled", button is not None and not button.get("disabled", False), button)

        before = toast(core)
        core.action("DetailsTool", code=TOOL["EXTRACT_AUDIO"])
        seen_running = False
        end = time.time() + 60
        while time.time() < end and not audio_tasks(core):
            core.pump(0.05)
            b = tool(core, TOOL["EXTRACT_AUDIO"])
            if b and b.get("disabled") and b.get("label") == s["audioExtractRunning"]:
                seen_running = True
        print("  info  running state seen in the details: %s" % seen_running)
        made = audio_tasks(core)
        core.check("the audio file is a finished task", len(made) == 1, [t["fileName"] for t in core.tasks()])
        first = made[0] if made else {}
        core.wait_for("the done toast", lambda: toast(core) != before, 10)
        core.check("a done toast", toast(core) != before and toast(core) != "", toast(core))
        out = find(core, first.get("fileName", "?"))
        core.check("the file is on disk, audio only", os.path.isfile(out) and not has_video(out), out)
        core.check("its audio equals the source's audio", out and audio_hash(out) == audio_hash(source) != "", (audio_hash(out) if out else "", audio_hash(source)))
        core.pump(0.6)
        clips = [c.get("name") for c in details(core).get("clips", [])]
        core.check("it shows in the source's clips", first.get("fileName") in clips, clips)
        b = tool(core, TOOL["EXTRACT_AUDIO"])
        core.check("the button is enabled again", b is not None and not b.get("disabled", False) and b.get("label") == s["audioExtractTitle"], b)
        core.action("CloseDetails")

        print("row menu: extract audio in the media section")
        core.action("RowMenu", id=video["id"])
        core.pump(0.3)
        rows = core.rows("rowMenu")
        entry = next((r for r in rows if r.get("label") == s["audioExtractTitle"]), None)
        core.check("the row menu offers extract audio", entry is not None, [r.get("label") for r in rows])
        heads = [r.get("header") for r in rows if r.get("header")]
        core.check("the menu has the media header", s["rowMenuSectionMedia"] in heads, heads)
        media_at = next(i for i, r in enumerate(rows) if r.get("header") == s["rowMenuSectionMedia"])
        entry_at = rows.index(entry)
        core.check("extract audio sits in the media section", entry_at >= media_at and all(not r.get("header") for r in rows[media_at + 1:entry_at + 1]), (media_at, entry_at))
        core.action("RowMenuPick", key=entry["key"])
        core.wait_for("a second audio file", lambda: len(audio_tasks(core)) == 2, 60)
        names = sorted(t["fileName"] for t in audio_tasks(core))
        core.check("the second one has its own name", len(set(names)) == 2, names)
        second = next(t for t in audio_tasks(core) if t["id"] != first.get("id"))
        out2 = find(core, second["fileName"])
        core.check("the second one has the same audio", out2 and audio_hash(out2) == audio_hash(source), out2)

        print("the audio file itself")
        core.action("RowMenu", id=first["id"])
        core.pump(0.3)
        labels = [r.get("label") for r in core.rows("rowMenu")]
        core.check("an audio file offers no extract audio", s["audioExtractTitle"] not in labels, labels)
        core.action("RowMenuClose")
        core.action("RowClick", id=first["id"], control=False, shift=False)
        core.pump(0.5)
        core.check("no extract audio button on the audio file", tool(core, TOOL["EXTRACT_AUDIO"]) is None, details(core).get("fileTools"))
        core.action("CloseDetails")

        print("a webm (opus) and a video without sound")
        core.debug("addDirect", {"url": "http://127.0.0.1:%d/talk.webm" % port, "fileName": "talk.webm"})
        webm = core.wait_task("COMPLETED", timeout=60, file_name="talk.webm")
        core.action("RowClick", id=webm["id"], control=False, shift=False)
        core.wait_for("details", lambda: details(core).get("id") == webm["id"], 10)
        core.action("DetailsTool", code=TOOL["EXTRACT_AUDIO"])
        core.wait_for("the webm audio", lambda: any(t["fileName"].startswith("talk (audio)") and not t["fileName"].endswith(".m4a") for t in audio_tasks(core)), 60)
        opus = next(t for t in audio_tasks(core) if t["fileName"].startswith("talk (audio)") and not t["fileName"].endswith(".m4a"))
        core.check("the webm gives an opus file with the same audio", opus["fileName"].endswith(".opus") and audio_hash(find(core, opus["fileName"])) == audio_hash(find(core, "talk.webm")), opus["fileName"])
        core.action("CloseDetails")
        core.debug("addDirect", {"url": "http://127.0.0.1:%d/silent.mp4" % port, "fileName": "silent.mp4"})
        silent = core.wait_task("COMPLETED", timeout=60, file_name="silent.mp4")
        count = len(audio_tasks(core))
        before = toast(core)
        core.action("RowMenu", id=silent["id"])
        core.pump(0.3)
        key = next((r["key"] for r in core.rows("rowMenu") if r.get("label") == s["audioExtractTitle"]), None)
        core.check("the silent video still offers it (sound is unknown before)", key is not None)
        if key is not None:
            core.action("RowMenuPick", key=key)
            seen = []
            end = time.time() + 30
            while time.time() < end and s["audioExtractNoAudio"] not in seen and s["audioExtractFailed"] not in seen:
                core.pump(0.1)
                if toast(core) not in seen:
                    seen.append(toast(core))
            core.check("a video without sound says so", s["audioExtractNoAudio"] in seen, seen)
            core.check("no file was added for it", len(audio_tasks(core)) == count)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    finally:
        server.shutdown()
    core.finish()


if __name__ == "__main__":
    run()
