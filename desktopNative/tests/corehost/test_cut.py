import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed
from test_compress import install_ffmpeg, serve_folder, select, FFMPEG_SOURCE

MEDIA = os.environ.get("FJ_CUT_MEDIA", "E:/tools/mpv-spike/media")


def cut_page(core):
    return (core.snapshot("audioCompress") or {}).get("cut", {}) or {}


def details(core):
    return core.snapshot("details") or {}


def measure(path):
    result = subprocess.run([FFMPEG_SOURCE, "-hide_banner", "-i", path, "-map", "0:v:0", "-f", "null", "-"],
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
    text = result.stderr
    fps = re.search(r"Video:.*?, ([\d.]+) fps", text)
    frames = re.findall(r"frame=\s*(\d+)", text)
    return (float(fps.group(1)) if fps else 0.0), (int(frames[-1]) if frames else 0)


def menu_labels(core):
    return [row["label"] for row in core.rows("rowMenu")]


def run():
    core = Core("cut", before_start=install_ffmpeg)
    server = serve_folder(MEDIA)
    s = core.strings()
    try:
        port = server.server_address[1]
        core.debug("addDirect", {"url": "http://127.0.0.1:%d/one.mp4" % port, "fileName": "one.mp4"})
        video = core.wait_task("COMPLETED", timeout=60, file_name="one.mp4")
        core.pump(0.5)

        core.action("RowMenu", id=video["id"])
        core.pump(0.3)
        core.check("row menu offers Cut", s["videoCutTitle"] in menu_labels(core), str(menu_labels(core)))
        key = next(r["key"] for r in core.rows("rowMenu") if r["label"] == s["videoCutTitle"])
        core.action("RowMenuPick", key=key)
        core.wait_for("the chooser", lambda: cut_page(core).get("open") and cut_page(core).get("chooser"), 10)
        core.check("chooser shown", cut_page(core).get("chooser"))

        core.action("VideoCutChoose", timeline=False)
        core.wait_for("the time view", lambda: cut_page(core).get("open") and not cut_page(core).get("chooser"), 10)
        core.wait_for("the duration", lambda: cut_page(core).get("duration", "") != "", 20)

        core.action("VideoCutPickTime", end=False)
        core.pump(0.2)
        core.check("start picker open", (core.snapshot("clipPicker") or {}).get("open"))
        core.action("ClipPickerType", field=2, text="02")
        core.action("ClipPickerApply")
        core.pump(0.3)
        core.action("VideoCutPickTime", end=True)
        core.pump(0.2)
        core.action("ClipPickerType", field=0, text="00")
        core.action("ClipPickerType", field=1, text="00")
        core.action("ClipPickerType", field=2, text="05")
        core.action("ClipPickerApply")
        core.pump(0.3)
        page = cut_page(core)
        core.check("range 2 s to 5 s", "00:00:02" in page.get("start", "") and "00:00:05" in page.get("end", ""),
                   page.get("start", "") + " / " + page.get("end", ""))
        core.check("can start", page.get("canStart"))
        core.action("VideoCutStart")
        core.wait_for("the cut to finish", lambda: not cut_page(core).get("running") and len(cut_page(core).get("clips", [])) == 1, 120)
        page = cut_page(core)
        core.check("not failed", not page.get("failed"), page.get("message", ""))
        clip = page["clips"][0]
        core.check("clip file exists", os.path.isfile(clip["path"]), clip["path"])
        fps, frames = measure(clip["path"])
        length = frames / fps if fps else 0
        print("    clip: fps=%s frames=%d length=%.4f s" % (fps, frames, length))
        core.check("clip is 3 s within one frame", fps > 0 and abs(length - 3.0) <= 1.0 / fps + 1e-6,
                   "fps=%s frames=%d length=%.4f" % (fps, frames, length))
        clip_task = core.task(file_name=os.path.basename(clip["path"]))
        core.check("a new finished task", clip_task is not None and clip_task["status"] == "COMPLETED")

        core.action("VideoCutApply", startMs=1000, endMs=2500)
        core.wait_for("the second cut", lambda: not cut_page(core).get("running") and len(cut_page(core).get("clips", [])) == 2, 120)
        second = [c for c in cut_page(core)["clips"] if c["path"] != clip["path"]][0]
        fps, frames = measure(second["path"])
        core.check("VideoCutApply clip is 1.5 s within one frame", fps > 0 and abs(frames / fps - 1.5) <= 1.0 / fps + 1e-6,
                   "frames=%d" % frames)

        core.action("VideoCutDismiss")
        core.wait_for("the dialog to close", lambda: not cut_page(core).get("open"), 10)
        select(core, video["id"])
        core.wait_for("clips in details", lambda: len(details(core).get("clips", [])) == 2, 10)
        tools = [t["label"] for t in details(core).get("mediaTools", [])]
        core.check("details offers Cut", s["videoCutTitle"] in tools, str(tools))

        core.action("VideoCutDeleteClip", path=second["path"])
        core.wait_for("one clip left", lambda: len(details(core).get("clips", [])) == 1, 10)
        core.pump(2.0)
        core.check("deleted file is gone", not os.path.isfile(second["path"]), second["path"])

        select(core, clip_task["id"])
        core.wait_for("clip details", lambda: details(core).get("id") == clip_task["id"], 10)
        tools = [t["label"] for t in details(core).get("mediaTools", [])]
        core.check("a clip has no Cut in details", s["videoCutTitle"] not in tools, str(tools))
        core.check("a clip shows no clips", len(details(core).get("clips", [])) == 0)
        core.action("RowMenu", id=clip_task["id"])
        core.pump(0.3)
        core.check("a clip has no Cut in the row menu", s["videoCutTitle"] not in menu_labels(core), str(menu_labels(core)))
        core.action("RowMenuClose")
    except CheckFailed as failure:
        print("FAILED: " + str(failure))
        print(cut_page(core))
        print(core.log_text()[-3000:])
    server.shutdown()
    core.finish()


if __name__ == "__main__":
    run()
