import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed
from test_compress import install_ffmpeg, serve_folder, FFMPEG_SOURCE


def make_video(folder, name, seconds, size, tone):
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, name)
    subprocess.run([FFMPEG_SOURCE, "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "lavfi", "-i", "testsrc=size=%s:rate=25:duration=%d" % (size, seconds),
                    "-f", "lavfi", "-i", "sine=f=%d:d=%d:sample_rate=44100" % (tone, seconds),
                    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-preset", "ultrafast", "-g", "25",
                    "-c:a", "aac", "-b:a", "64k", "-shortest", "-movflags", "+faststart", path],
                   check=True, stdin=subprocess.DEVNULL, timeout=120)
    return path


def duration_of(path):
    result = subprocess.run([FFMPEG_SOURCE, "-hide_banner", "-i", path], capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=60)
    found = re.search(r"Duration: (\d+):(\d+):([\d.]+)", result.stderr)
    if not found:
        return 0.0
    return int(found.group(1)) * 3600 + int(found.group(2)) * 60 + float(found.group(3))


def merge_page(core):
    return (core.snapshot("audioCompress") or {}).get("merge", {}) or {}


def chrome(core):
    return core.snapshot("chrome") or {}


def check_rows(core, ids):
    core.action("ClearChecks")
    for task_id in ids:
        core.action("RowCheck", id=task_id, checked=True)
    core.pump(0.5)


def add_finished(core, port, name):
    core.debug("addDirect", {"url": "http://127.0.0.1:%d/%s" % (port, name), "fileName": name})
    return core.wait_task("COMPLETED", timeout=60, file_name=name)


def run():
    core = Core("merge", before_start=install_ffmpeg)
    media = os.path.join(core.work, "media")
    make_video(media, "part-a.mp4", 3, "320x240", 440)
    make_video(media, "part-b.mp4", 2, "320x240", 660)
    make_video(media, "wide.mp4", 2, "640x360", 550)
    with open(os.path.join(media, "notes.txt"), "w", encoding="utf-8") as handle:
        handle.write("not a video\n")
    server = serve_folder(media)
    s = core.strings()
    try:
        port = server.server_address[1]
        a = add_finished(core, port, "part-a.mp4")
        b = add_finished(core, port, "part-b.mp4")
        wide = add_finished(core, port, "wide.mp4")
        notes = add_finished(core, port, "notes.txt")
        core.pump(0.6)

        check_rows(core, [a["id"]])
        core.check("one video: no Merge button", not chrome(core).get("canMerge"))
        check_rows(core, [a["id"], notes["id"]])
        core.check("video + text file: no Merge button", not chrome(core).get("canMerge"))
        check_rows(core, [a["id"], b["id"]])
        core.check("two videos: Merge button shown", chrome(core).get("canMerge"), str(chrome(core).get("selectedCountText")))

        core.action("MergeChecked")
        core.wait_for("the merge dialog", lambda: merge_page(core).get("open"), 10)
        core.wait_for("the check", lambda: not merge_page(core).get("checking"), 60)
        page = merge_page(core)
        names = [item["name"] for item in page.get("items", [])]
        core.check("videos listed in download order", names == ["part-a.mp4", "part-b.mp4"], str(names))
        core.check("same kind: nothing flagged", not any(item.get("notSame") for item in page["items"]), page.get("message", ""))
        core.check("extension shown", page.get("extension", "").strip("⁦⁩") == ".mp4", page.get("extension", ""))
        core.check("a file name is suggested", page.get("fileName", "") != "")
        core.check("can start", page.get("canStart"), str(page))
        core.check("first row cannot move up", not page["items"][0].get("canUp") and page["items"][0].get("canDown"))
        core.check("last row cannot move down", page["items"][-1].get("canUp") and not page["items"][-1].get("canDown"))

        if names[0] != "part-a.mp4":
            core.action("VideoMergeMove", id=page["items"][1]["id"], up=True)
            core.wait_for("part-a first", lambda: merge_page(core)["items"][0]["name"] == "part-a.mp4", 10)
        core.action("VideoMergeMove", id=a["id"], up=False)
        core.wait_for("moved down", lambda: [i["name"] for i in merge_page(core)["items"]] == ["part-b.mp4", "part-a.mp4"], 10)
        core.check("move down swaps the rows", True)
        core.action("VideoMergeMove", id=a["id"], up=True)
        core.wait_for("moved back", lambda: [i["name"] for i in merge_page(core)["items"]] == ["part-a.mp4", "part-b.mp4"], 10)
        core.wait_for("the re-check", lambda: not merge_page(core).get("checking"), 60)

        core.action("VideoMergeName", text="")
        core.pump(0.3)
        core.check("empty name: cannot start", not merge_page(core).get("canStart"))
        core.action("VideoMergeName", text="joined")
        core.pump(0.3)
        core.check("name kept", merge_page(core).get("fileName") == "joined", merge_page(core).get("fileName", ""))
        core.check("can start with a name", merge_page(core).get("canStart"))

        core.action("VideoMergeStart")
        core.wait_for("the merge to finish", lambda: merge_page(core).get("saved") or merge_page(core).get("failed"), 120)
        page = merge_page(core)
        core.check("merge not failed", page.get("saved") and not page.get("failed"), page.get("message", ""))
        core.check("saved message", page.get("message") == s["mergeSaved"], page.get("message", ""))
        core.check("no Start after saving", not page.get("canStart"))
        core.check("no moving after saving", not any(i.get("canUp") or i.get("canDown") for i in page["items"]))
        merged = core.wait_task("COMPLETED", timeout=20, file_name="joined.mp4")
        core.check("a new finished row joined.mp4", merged is not None)
        path = os.path.join(core.downloads, "joined.mp4")
        core.check("merged file exists", os.path.isfile(path), path)
        total = duration_of(os.path.join(media, "part-a.mp4")) + duration_of(os.path.join(media, "part-b.mp4"))
        got = duration_of(path)
        print("    merged: %.3f s, parts %.3f s" % (got, total))
        core.check("merged length = sum of the parts", abs(got - total) <= 0.3, "%.3f vs %.3f" % (got, total))

        core.action("VideoMergeDismiss")
        core.wait_for("the dialog to close", lambda: not merge_page(core).get("open"), 10)

        check_rows(core, [a["id"], wide["id"]])
        core.check("different size: Merge button still offered", chrome(core).get("canMerge"))
        core.action("MergeChecked")
        core.wait_for("the merge dialog", lambda: merge_page(core).get("open"), 10)
        core.wait_for("the check", lambda: not merge_page(core).get("checking"), 60)
        page = merge_page(core)
        flagged = [item["name"] for item in page.get("items", []) if item.get("notSame")]
        core.check("different size: the video is flagged", flagged == ["wide.mp4"], str(page.get("items")))
        core.check("different size: refusal text", page.get("failed") and page.get("message") == s["mergeNotSame"], page.get("message", ""))
        core.check("different size: cannot start", not page.get("canStart"))
        before = len(core.tasks())
        core.action("VideoMergeStart")
        core.pump(1.5)
        core.check("different size: Start does nothing", not merge_page(core).get("running") and len(core.tasks()) == before)
        core.action("VideoMergeDismiss")
        core.wait_for("the dialog to close", lambda: not merge_page(core).get("open"), 10)
        core.action("ClearChecks")
    except CheckFailed as failure:
        print("FAILED: " + str(failure))
        print(merge_page(core))
        print(core.log_text()[-3000:])
    server.shutdown()
    crash = core.crash_text()
    core.check("no entries in native-crash.log", crash == "", crash[:1500])
    core.shutdown()
    print("%d checks, %d failed" % (core.checks, len(core.failures)))
    sys.stdout.flush()
    os._exit(1 if core.failures else 0)


if __name__ == "__main__":
    run()
