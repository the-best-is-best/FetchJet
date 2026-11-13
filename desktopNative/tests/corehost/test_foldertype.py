import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER


def norm(path):
    return os.path.normcase(os.path.normpath(path))


def add_and_wait(core, url, name):
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text=url)
    core.pump(0.6)
    add = core.snapshot("add")
    destination = add.get("destination", "")
    core.action("StartDownload")
    task = core.wait_task("COMPLETED", timeout=40, file_name=name)
    return destination, task


def run():
    core = Core("foldertype")
    try:
        core.pump(0.5)
        core.action("SettingToggle", id="sortIntoFolders", on=True)
        core.pump(0.3)
        downloads = norm(core.downloads)

        print("default folder: a video goes to Video/")
        destination, task = add_and_wait(core, SERVER + "/file/clip-a.mp4?size=1MB&key=ft-a", "clip-a.mp4")
        core.check("the dialog shows the Video folder under downloads", norm(destination) == norm(os.path.join(downloads, "Video")), destination)
        core.check("the task is saved there", norm(task["destinationPath"]) == norm(os.path.join(downloads, "Video")), task["destinationPath"])

        print("a remembered video folder is used as it is")
        mine = os.path.join(core.downloads, "Media")
        os.makedirs(mine, exist_ok=True)
        core.action("ShowAddDialog", show=True)
        core.action("SetUrl", text=SERVER + "/file/clip-b.mp4?size=1MB&key=ft-b")
        core.pump(0.5)
        core.action("SetDestination", path=mine)
        core.pump(0.3)
        core.action("StartDownload")
        task = core.wait_task("COMPLETED", timeout=40, file_name="clip-b.mp4")
        core.check("no extra Video folder inside it", norm(task["destinationPath"]) == norm(mine), task["destinationPath"])
        core.check("the file is there", os.path.isfile(os.path.join(mine, "clip-b.mp4")))

        print("the next video link starts in the remembered folder")
        core.action("ShowAddDialog", show=True)
        core.action("SetUrl", text=SERVER + "/file/clip-c.mp4?size=1MB&key=ft-c")
        core.pump(0.6)
        add = core.snapshot("add")
        core.check("the dialog offers the remembered folder", norm(add.get("destination", "")) == norm(mine), add.get("destination"))
        core.action("StartDownload")
        task = core.wait_task("COMPLETED", timeout=40, file_name="clip-c.mp4")
        core.check("saved in it, still no extra folder", norm(task["destinationPath"]) == norm(mine), task["destinationPath"])

        print("a zip still goes to the default folder's Archives")
        destination, task = add_and_wait(core, SERVER + "/file/pack.zip?size=1MB&key=ft-d", "pack.zip")
        core.check("zip is not in the video folder", not norm(task["destinationPath"]).startswith(norm(mine)), task["destinationPath"])
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
    core.finish()


if __name__ == "__main__":
    run()
