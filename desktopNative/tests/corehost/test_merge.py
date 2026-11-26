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


def open_merge(core, ids, name):
    check_rows(core, ids)
    core.action("MergeChecked")
    core.wait_for("the merge dialog", lambda: merge_page(core).get("open"), 10)
    core.wait_for("the check", lambda: not merge_page(core).get("checking"), 60)
    core.action("VideoMergeName", text=name)
    core.pump(0.3)


def close_merge(core):
    core.action("VideoMergeDismiss")
    core.wait_for("the dialog to close", lambda: not merge_page(core).get("open"), 10)


def named_rows(core, name):
    return [t for t in core.tasks() if t.get("fileName") == name]


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

        close_merge(core)

        open_merge(core, [a["id"], b["id"]], "joined")
        core.check("same name: can press Merge", merge_page(core).get("canStart"))
        core.action("VideoMergeStart")
        core.wait_for("the name question", lambda: merge_page(core).get("conflict"), 10)
        page = merge_page(core)
        core.check("conflict: taken name shown", page.get("conflictName") == "joined.mp4", page.get("conflictName", ""))
        core.check("conflict: copy name shown", page.get("copyName") == "joined (1).mp4", page.get("copyName", ""))
        core.check("conflict: save-as-copy label names the copy", "joined (1).mp4" in page.get("saveCopyLabel", ""), page.get("saveCopyLabel", ""))
        core.check("conflict: cannot start, not running", not page.get("canStart") and not page.get("running"))
        core.action("VideoMergeConflictDismiss")
        core.pump(0.4)
        page = merge_page(core)
        core.check("Cancel clears the conflict", not page.get("conflict") and page.get("conflictName", "") == "" and page.get("open"), str(page))
        core.check("Cancel: Merge offered again", page.get("canStart"))
        core.action("VideoMergeStart")
        core.wait_for("the name question again", lambda: merge_page(core).get("conflict"), 10)
        core.action("VideoMergeName", text="joined-x")
        core.pump(0.4)
        core.check("typing a new name clears the conflict", not merge_page(core).get("conflict") and merge_page(core).get("canStart"))
        core.action("VideoMergeName", text="joined")
        core.pump(0.3)
        core.action("VideoMergeStart")
        core.wait_for("the name question", lambda: merge_page(core).get("conflict"), 10)
        original_size = os.path.getsize(path)
        original_mtime = os.path.getmtime(path)
        core.action("VideoMergeSaveCopy")
        core.wait_for("the copy merge", lambda: merge_page(core).get("saved") or merge_page(core).get("failed"), 120)
        core.check("copy: merge not failed", merge_page(core).get("saved") and not merge_page(core).get("failed"), merge_page(core).get("message", ""))
        copy_row = core.wait_task("COMPLETED", timeout=20, file_name="joined (1).mp4")
        copy_path = os.path.join(core.downloads, "joined (1).mp4")
        core.check("copy: new row joined (1).mp4", copy_row is not None)
        core.check("copy: both files exist", os.path.isfile(path) and os.path.isfile(copy_path))
        core.check("copy: original untouched", os.path.getmtime(path) == original_mtime and os.path.getsize(path) == original_size)
        core.check("copy: original row kept", len(named_rows(core, "joined.mp4")) == 1)
        close_merge(core)

        open_merge(core, [b["id"], a["id"]], "joined")
        core.action("VideoMergeStart")
        core.wait_for("the name question", lambda: merge_page(core).get("conflict"), 10)
        core.check("overwrite round: copy name skips the used one", merge_page(core).get("copyName") == "joined (2).mp4", merge_page(core).get("copyName", ""))
        core.action("VideoMergeOverwrite")
        core.wait_for("the overwrite merge", lambda: merge_page(core).get("saved") or merge_page(core).get("failed"), 120)
        core.check("overwrite: merge not failed", merge_page(core).get("saved") and not merge_page(core).get("failed"), merge_page(core).get("message", ""))
        core.pump(1.0)
        core.check("overwrite: no (2) file", not os.path.exists(os.path.join(core.downloads, "joined (2).mp4")))
        core.check("overwrite: no (2) row", not named_rows(core, "joined (2).mp4"))
        core.check("overwrite: file replaced", os.path.isfile(path) and os.path.getmtime(path) != original_mtime)
        rows = named_rows(core, "joined.mp4")
        core.check("overwrite: one row with the name", len(rows) == 1, str(len(rows)))
        core.check("overwrite: the row is the new one", rows and rows[0].get("id") != merged.get("id"), str(rows[:1]))
        core.check("overwrite: the copy is kept", os.path.isfile(copy_path) and len(named_rows(core, "joined (1).mp4")) == 1)
        close_merge(core)

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
