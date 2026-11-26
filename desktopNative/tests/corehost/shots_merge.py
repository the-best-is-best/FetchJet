import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed
from shots_cut import shot, four
from test_compress import install_ffmpeg, serve_folder
from test_merge import make_video, add_finished, check_rows, merge_page


def running(data):
    merge = data["Core"]["audioCompress"]["merge"]
    merge.update({"open": True, "checking": False, "running": True, "progress": 0.42, "percent": "42%",
                  "elapsed": "0:03", "timeLeft": "0:04", "canStart": False})


def run():
    core = Core("shots-merge", before_start=install_ffmpeg)
    media = os.path.join(core.work, "media")
    make_video(media, "holiday-part-1.mp4", 3, "320x240", 440)
    make_video(media, "holiday-part-2.mp4", 2, "320x240", 660)
    make_video(media, "holiday-wide.mp4", 2, "640x360", 550)
    server = serve_folder(media)
    failed = 0

    def take(name, patch=None):
        nonlocal failed
        core.pump(0.6)
        if not shot(core, "merge-" + name, patch=patch):
            failed += 1

    try:
        core.action("SystemTheme", dark=True)
        core.pump(0.6)
        port = server.server_address[1]
        one = add_finished(core, port, "holiday-part-1.mp4")
        two = add_finished(core, port, "holiday-part-2.mp4")
        wide = add_finished(core, port, "holiday-wide.mp4")
        core.pump(0.6)
        check_rows(core, [one["id"], two["id"]])
        four(core, "bar", take)
        core.action("MergeChecked")
        core.wait_for("the dialog", lambda: merge_page(core).get("open") and not merge_page(core).get("checking"), 60)
        four(core, "dialog", take)
        four(core, "progress", take, running)
        core.action("VideoMergeStart")
        core.wait_for("the merge", lambda: merge_page(core).get("saved") or merge_page(core).get("failed"), 120)
        take("saved-dark-en")
        core.action("VideoMergeDismiss")
        core.wait_for("closed", lambda: not merge_page(core).get("open"), 10)
        check_rows(core, [one["id"], two["id"]])
        core.action("MergeChecked")
        core.wait_for("the dialog", lambda: merge_page(core).get("open") and not merge_page(core).get("checking"), 60)
        core.action("VideoMergeStart")
        core.wait_for("the name question", lambda: merge_page(core).get("conflict"), 10)
        four(core, "conflict", take)
        core.action("VideoMergeConflictDismiss")
        core.action("VideoMergeDismiss")
        core.wait_for("closed", lambda: not merge_page(core).get("open"), 10)
        check_rows(core, [one["id"], wide["id"]])
        core.action("MergeChecked")
        core.wait_for("the refusal", lambda: merge_page(core).get("open") and not merge_page(core).get("checking"), 60)
        take("notsame-dark-en")
    except CheckFailed as failure:
        print("FAILED: " + str(failure))
        failed += 1
    server.shutdown()
    core.shutdown()
    print("%d shots failed" % failed)
    sys.stdout.flush()
    os._exit(1 if failed else 0)


if __name__ == "__main__":
    run()
