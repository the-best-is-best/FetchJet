import gzip
import io
import os
import subprocess
import sys
import tarfile
import time
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed

PORT = int(os.environ.get("FJ_EXTRACT_PORT", "18261"))
BASE = "http://127.0.0.1:%d" % PORT


def make_site(folder):
    os.makedirs(folder, exist_ok=True)
    text = ("alpha beta gamma delta " * 4000).encode()
    for name in ("bundle-a.zip", "bundle-b.zip", "bundle-c.zip", "bundle-d.zip"):
        with zipfile.ZipFile(os.path.join(folder, name), "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("one.txt", text[:5000])
            z.writestr("dir/two.txt", text[:9000])
            script = zipfile.ZipInfo("run.sh")
            script.create_system = 3
            script.external_attr = 0o100755 << 16
            z.writestr(script, b"#!/bin/sh\nexit 0\n")
    with zipfile.ZipFile(os.path.join(folder, "slip.zip"), "w") as z:
        z.writestr("../escaped.txt", b"nope")
        z.writestr("fine.txt", b"fine")
        z.writestr("fine2.txt", b"fine2")
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w:gz") as t:
        for name, data in (("proj/a.txt", b"aaa"), ("proj/sub/b.txt", b"bbb"), ("proj/run.sh", b"#!/bin/sh\nexit 0\n")):
            info = tarfile.TarInfo(name)
            info.size = len(data)
            if name.endswith(".sh"):
                info.mode = 0o755
            t.addfile(info, io.BytesIO(data))
    open(os.path.join(folder, "proj.tar.gz"), "wb").write(buffer.getvalue())
    open(os.path.join(folder, "note.txt.gz"), "wb").write(gzip.compress(text))
    broken = open(os.path.join(folder, "bundle-a.zip"), "rb").read()
    open(os.path.join(folder, "broken.zip"), "wb").write(broken[:60] + b"\x00" * 40 + broken[100:])
    return text


def add_and_wait(core, name):
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text=BASE + "/" + name)
    core.pump(0.6)
    core.action("StartDownload")
    return core.wait_task(["COMPLETED", "ERROR"], timeout=40, file_name=name)


def run():
    core = Core("extract")
    site = os.path.join(core.work, "site")
    text = make_site(site)
    server = subprocess.Popen([sys.executable, "-m", "http.server", str(PORT), "--bind", "127.0.0.1", "--directory", site],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    downloads = core.downloads
    try:
        core.pump(0.5)
        print("off by default")
        row = [r for r in core.rows("settingsRows") if r.get("id") == "extractArchives"]
        add_and_wait(core, "bundle-a.zip")
        core.pump(3)
        core.check("nothing is opened while the setting is off", os.path.isfile(os.path.join(downloads, "bundle-a.zip")) and
                   not os.path.exists(os.path.join(downloads, "bundle-a")), os.listdir(downloads))

        print("on")
        core.action("SettingToggle", id="extractArchives", on=True)
        core.pump(0.5)
        saved = open(os.path.join(core.home, "behavior.properties"), encoding="utf-8").read()
        core.check("the setting is saved", "extractArchives=true" in saved, saved)
        core.pump(3)
        core.check("an archive downloaded before is not opened afterwards", not os.path.exists(os.path.join(downloads, "bundle-a")),
                   os.listdir(downloads))

        add_and_wait(core, "bundle-b.zip")
        folder = os.path.join(downloads, "bundle-b")
        core.wait_for("the folder bundle-b", lambda: os.path.isfile(os.path.join(folder, "dir", "two.txt")), 30)
        core.check("a zip with several things goes into a folder named after it", open(os.path.join(folder, "one.txt"), "rb").read() == text[:5000] and
                   open(os.path.join(folder, "dir", "two.txt"), "rb").read() == text[:9000], os.listdir(folder))
        core.check("the archive stays", os.path.isfile(os.path.join(downloads, "bundle-b.zip")))
        core.check("no work folder is left", not [n for n in os.listdir(downloads) if "fetchjet-extract" in n], os.listdir(downloads))
        if os.name != "nt":
            core.check("a zip program keeps its run permission", os.access(os.path.join(folder, "run.sh"), os.X_OK))
            core.check("a plain file in a zip is not made runnable", not os.access(os.path.join(folder, "one.txt"), os.X_OK))

        add_and_wait(core, "proj.tar.gz")
        core.wait_for("the folder proj", lambda: os.path.isfile(os.path.join(downloads, "proj", "sub", "b.txt")), 30)
        core.check("a tar.gz with one top folder puts that folder next to the archive", open(os.path.join(downloads, "proj", "a.txt"), "rb").read() == b"aaa")
        if os.name != "nt":
            core.check("a tar program keeps its run permission", os.access(os.path.join(downloads, "proj", "run.sh"), os.X_OK))
            core.check("a plain file in a tar is not made runnable", not os.access(os.path.join(downloads, "proj", "a.txt"), os.X_OK))

        add_and_wait(core, "note.txt.gz")
        core.wait_for("note.txt", lambda: os.path.isfile(os.path.join(downloads, "note.txt")), 30)
        core.check("a .gz of one file gives that file", open(os.path.join(downloads, "note.txt"), "rb").read() == text)

        add_and_wait(core, "slip.zip")
        core.wait_for("the folder slip", lambda: os.path.isfile(os.path.join(downloads, "slip", "fine.txt")), 30)
        core.check("a name that leaves the folder is skipped", not os.path.exists(os.path.join(downloads, "escaped.txt")) and
                   not os.path.exists(os.path.join(os.path.dirname(downloads), "escaped.txt")))

        add_and_wait(core, "broken.zip")
        core.pump(4)
        core.check("a broken zip leaves the archive and nothing else", os.path.isfile(os.path.join(downloads, "broken.zip")) and
                   not os.path.exists(os.path.join(downloads, "broken")) and
                   not [n for n in os.listdir(downloads) if "fetchjet-extract" in n], os.listdir(downloads))
        core.check("the failure is in the log", "Extract failed for broken.zip" in core.log_text(), core.log_text()[-600:])

        print("the Extract button, with the setting off")
        core.action("SettingToggle", id="extractArchives", on=False)
        core.pump(0.4)
        done = add_and_wait(core, "bundle-c.zip")
        core.pump(2)
        core.check("bundle-c.zip is a finished archive", done["status"] == "COMPLETED" and not os.path.exists(os.path.join(downloads, "bundle-c")))
        core.action("RowExtract", id=done["id"])
        core.wait_for("the folder bundle-c", lambda: os.path.isfile(os.path.join(downloads, "bundle-c", "dir", "two.txt")), 30)
        core.check("the row button opens it even with the setting off", os.path.isfile(os.path.join(downloads, "bundle-c", "one.txt")))
        core.action("RowExtract", id=done["id"])
        core.pump(0.5)
        core.wait_for("the second copy", lambda: os.path.isfile(os.path.join(downloads, "bundle-c (1)", "dir", "two.txt")), 30)
        core.check("a second press makes a numbered copy", True)
        core.action("SettingToggle", id="extractArchives", on=False)
        core.pump(0.3)

        print("off again")
        core.action("SettingToggle", id="extractArchives", on=False)
        core.pump(0.4)
        add_and_wait(core, "bundle-d.zip")
        core.pump(3)
        core.check("off again: a new archive is not opened by itself", not os.path.exists(os.path.join(downloads, "bundle-d")), os.listdir(downloads))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    finally:
        server.kill()
    core.finish()


if __name__ == "__main__":
    run()
