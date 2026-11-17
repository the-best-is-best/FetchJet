import os
import subprocess
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed

PORT = int(os.environ.get("FJ_TRASH_PORT", "18266"))
BASE = "http://127.0.0.1:%d" % PORT


def windows_bin_entries(drive_root, name):
    sid = subprocess.run(["whoami", "/user", "/fo", "csv", "/nh"], capture_output=True, text=True).stdout.strip().split(",")[-1].strip('"')
    folder = os.path.join(drive_root, "$Recycle.Bin", sid)
    found = []
    try:
        entries = os.listdir(folder)
    except OSError:
        return found
    needle = name.encode("utf-16-le")
    for entry in entries:
        if not entry.startswith("$I"):
            continue
        try:
            data = open(os.path.join(folder, entry), "rb").read()
        except OSError:
            continue
        if needle in data:
            found.append((os.path.join(folder, entry), os.path.join(folder, "$R" + entry[2:])))
    return found


def purge(entries):
    for info, item in entries:
        for path in (item, info):
            try:
                if os.path.isdir(path):
                    import shutil
                    shutil.rmtree(path, ignore_errors=True)
                else:
                    os.remove(path)
            except OSError:
                pass


def in_trash(core, name):
    if os.name == "nt":
        return windows_bin_entries(os.path.splitdrive(core.work)[0] + os.sep, name)
    if sys.platform == "darwin":
        item = os.path.join(os.path.expanduser("~"), ".Trash", name)
        return [(item, item)] if os.path.exists(item) else []
    files =os.path.join(os.environ["XDG_DATA_HOME"], "Trash", "files", name)
    info = os.path.join(os.environ["XDG_DATA_HOME"], "Trash", "info", name + ".trashinfo")
    return [(info, files)] if os.path.exists(files) and os.path.isfile(info) else []


def add_and_wait(core, name):
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text=BASE + "/" + name)
    core.pump(0.6)
    core.action("StartDownload")
    return core.wait_task(["COMPLETED", "ERROR"], timeout=40, file_name=name)


def delete_with_file(core, task):
    core.action("Remove", id=task["id"])
    core.pump(0.4)
    core.action("ConfirmRemove", deleteFiles=True)
    core.wait_for("the row to go", lambda: core.task(task_id=task["id"]) is None, 15)
    core.pump(1)


def run():
    if os.name != "nt":
        os.environ["XDG_DATA_HOME"] = os.path.join(os.path.expanduser("~"), "fetchjet-native", "work", "trash-xdg")
    core = Core("trash")
    site = os.path.join(core.work, "site")
    os.makedirs(site, exist_ok=True)
    tag = "fj-trash-%d" % os.getpid()
    for name in (tag + "-a.bin", tag + "-b.bin"):
        open(os.path.join(site, name), "wb").write(os.urandom(64 * 1024))
    with zipfile.ZipFile(os.path.join(site, tag + "-pack.zip"), "w") as z:
        z.writestr("one.txt", b"1" * 100)
        z.writestr("two.txt", b"2" * 100)
    server = subprocess.Popen([sys.executable, "-m", "http.server", str(PORT), "--bind", "127.0.0.1", "--directory", site],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    import time
    time.sleep(1.5)
    leftovers = []
    try:
        core.pump(0.5)
        saved = lambda: open(os.path.join(core.home, "behavior.properties"), encoding="utf-8").read() \
            if os.path.isfile(os.path.join(core.home, "behavior.properties")) else ""
        print("on by default: a deleted finished file goes to the trash")
        a = add_and_wait(core, tag + "-a.bin")
        path_a = os.path.join(a["destinationPath"], tag + "-a.bin")
        core.check("downloaded", os.path.isfile(path_a))
        delete_with_file(core, a)
        core.check("the file left its folder", not os.path.exists(path_a))
        found = in_trash(core, tag + "-a.bin")
        leftovers += found
        core.check("and it is in the trash", len(found) == 1, found)

        print("off: deleted for good")
        core.action("SettingToggle", id="deleteToTrash", on=False)
        core.pump(0.4)
        core.check("the switch is saved", "deleteToTrash=false" in saved(), saved()[-200:])
        b = add_and_wait(core, tag + "-b.bin")
        path_b = os.path.join(b["destinationPath"], tag + "-b.bin")
        delete_with_file(core, b)
        core.check("the file is gone", not os.path.exists(path_b))
        found = in_trash(core, tag + "-b.bin")
        leftovers += found
        core.check("and not in the trash", found == [], found)

        print("archive to the trash after extracting")
        core.action("SettingToggle", id="extractArchives", on=True)
        core.action("SettingToggle", id="trashArchive", on=True)
        core.pump(0.4)
        pack = add_and_wait(core, tag + "-pack.zip")
        folder = os.path.join(pack["destinationPath"], tag + "-pack")
        core.wait_for("the extracted folder", lambda: os.path.isfile(os.path.join(folder, "two.txt")), 30)
        core.pump(1.5)
        core.check("the files were extracted", os.path.isfile(os.path.join(folder, "one.txt")))
        core.check("the archive left its folder", not os.path.exists(os.path.join(pack["destinationPath"], tag + "-pack.zip")))
        found = in_trash(core, tag + "-pack.zip")
        leftovers += found
        core.check("and it is in the trash", len(found) == 1, found)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    finally:
        server.kill()
        purge(leftovers)
    core.finish()


if __name__ == "__main__":
    run()
