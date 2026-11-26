import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed
from test_bridge import ALERT
from test_compress import install_ffmpeg, serve_folder, select, estimate_ready, FFMPEG_NAME
from test_rowmenu import KEY, menu


def make_media(core):
    folder = os.path.join(core.work, "media")
    os.makedirs(folder, exist_ok=True)
    ffmpeg = os.path.join(core.home, "tools", FFMPEG_NAME)
    for name in ("one.mp4", "two.mp4"):
        subprocess.run([ffmpeg, "-y", "-v", "error", "-f", "lavfi", "-i", "testsrc=size=320x240:rate=25:duration=6",
                        "-f", "lavfi", "-i", "sine=frequency=440:duration=6", "-shortest", "-c:v", "mpeg4", "-q:v", "5",
                        "-c:a", "aac", "-b:a", "96k", os.path.join(folder, name)],
                       check=True, stdin=subprocess.DEVNULL, timeout=120)
    subprocess.run([ffmpeg, "-y", "-v", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=20", "-ac", "2", "-ar", "44100",
                    os.path.join(folder, "tone.wav")], check=True, stdin=subprocess.DEVNULL, timeout=120)
    return folder


def cut_page(core):
    return (core.snapshot("audioCompress") or {}).get("cut", {}) or {}


def compress_page(core):
    return core.snapshot("audioCompress") or {}


def toast(core):
    return (core.snapshot("shell") or {}).get("toastText", "")


def path_of(task):
    return os.path.join(task["destinationPath"], task["fileName"])


def open_cut(core, task_id):
    entries = menu(core, task_id)
    core.check("the row menu offers Cut", "CUT" in entries, entries)
    core.action("RowMenuPick", key=KEY["CUT"])
    core.wait_for("the cut chooser", lambda: cut_page(core).get("open") and cut_page(core).get("chooser"), 10)
    core.action("VideoCutChoose", timeline=False)
    core.wait_for("the time view", lambda: cut_page(core).get("open") and not cut_page(core).get("chooser"), 10)
    core.wait_for("the duration", lambda: cut_page(core).get("duration", "") != "", 30)


def remove_alert(core, task_id):
    menu(core, task_id)
    core.action("RowMenuPick", key=KEY["DELETE"])
    core.pump(0.5)
    return next((a for a in core.rows("alerts") if a.get("kind") == ALERT["REMOVE"]), None)


def wait_gone(core, task_id, timeout=15):
    core.wait_for("the task to leave the list", lambda: core.task(task_id=task_id) is None, timeout)


def run():
    core = Core("delete_original", before_start=install_ffmpeg)
    server = None
    try:
        core.pump(0.6)
        s = core.strings()
        core.action("SettingToggle", id="deleteToTrash", on=False)
        core.pump(0.3)
        folder = make_media(core)
        server = serve_folder(folder)
        base = "http://127.0.0.1:%d/" % server.server_address[1]

        print("cut with Delete the original")
        core.debug("addDirect", {"url": base + "one.mp4", "fileName": "one.mp4"})
        video = core.wait_task("COMPLETED", timeout=60, file_name="one.mp4")
        original = path_of(video)
        core.pump(0.5)
        open_cut(core, video["id"])
        page = cut_page(core)
        core.check("the option is offered when nothing was made from the file", page.get("canDeleteOriginal") is True, page)
        core.check("the option is off by default", page.get("deleteOriginal", False) is False, page)
        core.action("VideoCutDeleteOriginal", on=True)
        core.pump(0.3)
        core.check("the option can be ticked", cut_page(core).get("deleteOriginal") is True, cut_page(core))
        before_toast = toast(core)
        core.action("VideoCutApply", startMs=1000, endMs=3000)
        core.wait_for("the page to close after the cut", lambda: not cut_page(core).get("open"), 120)
        core.wait_for("the original task to go", lambda: core.task(task_id=video["id"]) is None, 15)
        tasks = core.tasks()
        promoted = next((t for t in tasks if "(cut " in t["fileName"] and t["fileName"].startswith("one")), None)
        core.check("the clip is in the list", promoted is not None, [t["fileName"] for t in tasks])
        promoted = promoted or {}
        core.check("the clip is now a main download", promoted.get("cutFrom") == "" and promoted.get("status") == "COMPLETED", promoted)
        core.check("the clip took the original link", promoted.get("url") == video["url"], (promoted.get("url"), video["url"]))
        core.check("the clip keeps its range", promoted.get("cutStartMs") == 1000 and promoted.get("cutEndMs") == 3000, promoted)
        core.check("the original file is deleted", not os.path.exists(original), original)
        core.check("the clip file is there", os.path.isfile(path_of(promoted)), path_of(promoted))
        end = time.time() + 5
        while time.time() < end and toast(core) == before_toast:
            core.pump(0.2)
        core.check("a saved toast is shown", toast(core) == s["videoCutSaved"], toast(core))

        print("the promoted clip is a normal download")
        open_cut(core, promoted["id"])
        core.check("it can be cut, and offers the option again", cut_page(core).get("canDeleteOriginal") is True, cut_page(core))
        core.action("VideoCutApply", startMs=0, endMs=1000)
        core.wait_for("the plain cut", lambda: not cut_page(core).get("running") and len(cut_page(core).get("clips", [])) == 1, 120)
        page = cut_page(core)
        core.check("a cut without the option keeps the page open", page.get("open") and not page.get("failed"), page)
        core.check("with a clip made the option is not offered", not page.get("canDeleteOriginal", False), page)
        core.check("the promoted file is kept", core.task(task_id=promoted["id"]) is not None and os.path.isfile(path_of(promoted)))
        sub_clip = page["clips"][0]
        core.action("VideoCutDismiss")
        core.wait_for("the page to close", lambda: not cut_page(core).get("open"), 10)

        print("removing a file removes its clips")
        alert = remove_alert(core, promoted["id"])
        core.check("the remove dialog says one more item goes", alert is not None and
                   alert.get("hint") == s["removeAlsoDerived"].replace("{n}", "1"), alert)
        core.action("AlertConfirm", kind=ALERT["REMOVE"], checked=True)
        wait_gone(core, promoted["id"])
        core.wait_for("the clip task to go", lambda: core.task(file_name=os.path.basename(sub_clip["path"])) is None, 15)
        core.check("the files are deleted", not os.path.exists(path_of(promoted)) and not os.path.exists(sub_clip["path"]),
                   (path_of(promoted), sub_clip["path"]))

        print("removing a clip alone keeps its source")
        core.debug("addDirect", {"url": base + "two.mp4", "fileName": "two.mp4"})
        two = core.wait_task("COMPLETED", timeout=60, file_name="two.mp4")
        core.pump(0.5)
        open_cut(core, two["id"])
        core.action("VideoCutApply", startMs=500, endMs=2000)
        core.wait_for("the clip", lambda: not cut_page(core).get("running") and len(cut_page(core).get("clips", [])) == 1, 120)
        clip = cut_page(core)["clips"][0]
        core.action("VideoCutDismiss")
        core.wait_for("the page to close", lambda: not cut_page(core).get("open"), 10)
        clip_task = core.task(file_name=os.path.basename(clip["path"]))
        core.check("the clip task belongs to its source", clip_task is not None and clip_task.get("cutFrom") == two["id"], clip_task)
        alert = remove_alert(core, clip_task["id"])
        core.check("a clip has nothing made from it", alert is not None and alert.get("hint", "") == "", alert)
        core.action("AlertConfirm", kind=ALERT["REMOVE"], checked=True)
        wait_gone(core, clip_task["id"])
        for _ in range(100):
            if not os.path.exists(clip["path"]):
                break
            core.pump(0.1)
        core.check("the clip file is deleted", not os.path.exists(clip["path"]), clip["path"])
        core.check("the source is untouched", core.task(task_id=two["id"]) is not None and os.path.isfile(path_of(two)))
        alert = remove_alert(core, two["id"])
        core.check("the source now has nothing made from it", alert is not None and alert.get("hint", "") == "", alert)
        core.action("AlertDismiss", kind=ALERT["REMOVE"], fromScrim=True)
        core.pump(0.3)

        print("compress with Delete the original")
        core.debug("addDirect", {"url": base + "tone.wav", "fileName": "tone.wav"})
        sound = core.wait_task("COMPLETED", timeout=60, file_name="tone.wav")
        wav = path_of(sound)
        core.pump(0.5)
        select(core, sound["id"])
        core.action("DetailsCompress")
        core.wait_for("the compress page", lambda: compress_page(core).get("open"), 10)
        core.wait_for("the estimate", lambda: estimate_ready(core), 60)
        p = compress_page(core)
        core.check("the option is offered", p.get("canDeleteOriginal") is True and p.get("deleteOriginal", False) is False, p)
        core.action("AudioCompressSetLevel", level=6)
        core.action("AudioCompressDeleteOriginal", on=True)
        core.pump(0.3)
        core.check("the option can be ticked", compress_page(core).get("deleteOriginal") is True)
        core.action("AudioCompressStart")
        core.wait_for("the page to close after compressing", lambda: not compress_page(core).get("open"), 180)
        core.pump(0.5)
        same = core.task(task_id=sound["id"]) or {}
        core.check("the task stays with the same id", same.get("status") == "COMPLETED", same)
        core.check("the task now points at the compressed file", same.get("fileName", "").endswith(".m4a") and
                   same.get("fileName") != "tone.wav", same.get("fileName"))
        new_path = path_of(same) if same else ""
        core.check("the compressed file is there and its size is known", os.path.isfile(new_path) and
                   same.get("totalBytes") == os.path.getsize(new_path), (new_path, same.get("totalBytes")))
        core.check("the original wav is deleted", not os.path.exists(wav), wav)
        end = time.time() + 5
        while time.time() < end and toast(core) != s["audioCompressSaved"]:
            core.pump(0.2)
        core.check("the compress saved toast", toast(core) == s["audioCompressSaved"], toast(core))

        print("the compressed task can be compressed again, and removal takes the copy")
        select(core, sound["id"])
        core.action("DetailsCompress")
        core.wait_for("the compress page", lambda: compress_page(core).get("open"), 10)
        core.wait_for("the page to read the file", lambda: estimate_ready(core) or compress_page(core).get("nothing"), 60)
        p = compress_page(core)
        core.check("no copies are listed for the replaced file", len(p.get("copies", [])) == 0, p.get("copies"))
        copy_path = ""
        if not p.get("nothing"):
            core.action("AudioCompressSetMode", simple=False)
            core.action("AudioCompressSetBitrate", value=32)
            core.pump(0.4)
            core.action("AudioCompressStart")
            core.wait_for("the copy", lambda: not compress_page(core).get("running") and len(compress_page(core).get("copies", [])) == 1, 180)
            p = compress_page(core)
            copy_path = p["copies"][0]["path"]
            core.check("with a copy the option is not offered", not p.get("canDeleteOriginal", False) and p.get("open"), p)
        core.action("AudioCompressDismiss")
        core.pump(0.4)
        if copy_path:
            alert = remove_alert(core, sound["id"])
            core.check("the dialog counts the copy", alert is not None and alert.get("hint") == s["removeAlsoDerived"].replace("{n}", "1"), alert)
            core.action("AlertConfirm", kind=ALERT["REMOVE"], checked=True)
            wait_gone(core, sound["id"])
            core.pump(0.5)
            core.check("the copy is deleted with it", not os.path.exists(copy_path), copy_path)
            core.check("the compressed file is deleted", not os.path.exists(new_path), new_path)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    if server:
        server.shutdown()
    core.finish()


if __name__ == "__main__":
    run()
