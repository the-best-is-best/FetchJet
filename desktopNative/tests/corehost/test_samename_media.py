import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, install_fake_ytdlp, server_sha256, sha256_of_file

STATE = {name: index for index, name in enumerate(ENUMS["MediaStateCode"])}


def add_video(core, url):
    core.action("SetUrl", text=url)
    core.action("ShowAddDialog", show=True)
    core.wait_for("qualities of " + url, lambda: (core.snapshot("addMedia") or {}).get("state", 0) == STATE["QUALITIES"], 60)
    rows = core.rows("addQualities")
    low = [r for r in rows if "360" in r.get("label", "")]
    core.action("SelectAllQualities", selected=False)
    core.action("ToggleQuality", index=low[0]["index"], selected=True)
    core.pump(0.3)
    core.action("StartDownload")


def run():
    core = Core("samename_media", before_start=install_fake_ytdlp)
    try:
        core.pump(1.5)
        sha = server_sha256("20MB.bin")
        core.action("ToggleTurtle")
        core.pump(0.5)
        print("two video links with the same title, the second added while the first still downloads")
        add_video(core, "https://www.youtube.com/watch?v=fakevideo01")
        core.wait_for("the first to download", lambda: any(t["status"] == "DOWNLOADING" for t in core.tasks()), 60)
        add_video(core, "https://www.youtube.com/watch?v=fakevideo02")
        core.wait_for("two tasks", lambda: len(core.tasks()) == 2, 30)
        names = sorted(t["fileName"] for t in core.tasks())
        print("    names: " + " | ".join(names))
        core.check("the two downloads have different names", len({n.lower() for n in names}) == 2, names)
        core.action("PickSpeedOption", index=3)

        def done():
            tasks = core.tasks()
            return tasks if len(tasks) == 2 and all(t["status"] in ("COMPLETED", "ERROR") for t in tasks) else None

        tasks = core.wait_for("both to finish", done, 180)
        paths = [os.path.join(t["destinationPath"], t["fileName"]) for t in tasks]
        core.check("both completed", all(t["status"] == "COMPLETED" for t in tasks), [(t["fileName"], t["status"], t.get("error")) for t in tasks])
        core.check("two files on disk, each whole, none written over the other",
                   paths[0] != paths[1] and all(os.path.isfile(p) and sha256_of_file(p) == sha for p in paths), paths)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
