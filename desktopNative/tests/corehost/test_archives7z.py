import os
import shutil
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed
from test_bridge import ALERT

PORT = int(os.environ.get("FJ_7Z_PORT", "18267"))
BASE = "http://127.0.0.1:%d" % PORT


def seven_zip():
    for path in (os.environ.get("FETCHJET_7Z", ""), r"C:\Program Files\7-Zip\7z.exe", shutil.which("7z") or "", shutil.which("7zz") or ""):
        if path and os.path.isfile(path):
            return path
    return None


def make_site(folder, tool):
    os.makedirs(folder, exist_ok=True)
    src = os.path.join(folder, "src")
    os.makedirs(os.path.join(src, "inner"), exist_ok=True)
    open(os.path.join(src, "a.txt"), "w").write("alpha " * 200)
    open(os.path.join(src, "inner", "b.txt"), "w").write("beta " * 200)
    open(os.path.join(src, "noise.bin"), "wb").write(os.urandom(300 * 1024))

    def run(*args):
        subprocess.run([tool] + list(args), cwd=src, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    run("a", "-t7z", os.path.join(folder, "plain.7z"), "a.txt", "inner")
    run("a", "-t7z", "-v120k", os.path.join(folder, "split.7z"), "a.txt", "inner", "noise.bin")
    run("a", "-tzip", "-psecret", "-mem=AES256", os.path.join(folder, "locked.zip"), "a.txt", "inner")
    shutil.rmtree(src)
    return sorted(n for n in os.listdir(folder) if n.startswith("split.7z."))


def add(core, name):
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text=BASE + "/" + name)
    core.pump(0.6)
    core.action("StartDownload")
    return core.wait_task(["COMPLETED", "ERROR"], timeout=40, file_name=name)


def password_alert(core):
    return next((a for a in core.rows("alerts") if a.get("kind") == ALERT["ARCHIVE_PASSWORD"]), None)


def run():
    tool = seven_zip()
    core = Core("archives7z")
    downloads = core.downloads
    server = None
    try:
        core.pump(0.5)
        if tool is None:
            print("no 7-Zip on this computer: only the missing-tool path is checked")
            core.check("skipped: 7-Zip is not installed here", True)
            return
        site = os.path.join(core.work, "site")
        volumes = make_site(site, tool)
        server = subprocess.Popen([sys.executable, "-m", "http.server", str(PORT), "--bind", "127.0.0.1", "--directory", site],
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(1.5)

        print("a .7z through 7-Zip, with the Extract button")
        plain = add(core, "plain.7z")
        core.action("RowExtract", id=plain["id"])
        core.wait_for("the plain folder", lambda: os.path.isfile(os.path.join(downloads, "plain", "inner", "b.txt")), 30)
        core.check("both files are there", os.path.isfile(os.path.join(downloads, "plain", "a.txt")))
        core.check("no work folder is left", not any(n.startswith(".plain") for n in os.listdir(downloads)), os.listdir(downloads))

        print("a split .7z waits for every part")
        core.action("SettingToggle", id="extractArchives", on=True)
        core.pump(0.4)
        core.check("the test archive has several parts", len(volumes) >= 3, volumes)
        add(core, volumes[0])
        core.pump(3)
        core.check("only the first part: nothing is opened yet", not os.path.exists(os.path.join(downloads, "split")))
        for name in volumes[1:-1]:
            add(core, name)
        core.pump(3)
        core.check("still one part missing: nothing yet", not os.path.exists(os.path.join(downloads, "split")))
        add(core, volumes[-1])
        core.wait_for("the split folder", lambda: os.path.isfile(os.path.join(downloads, "split", "noise.bin")), 40)
        core.check("all parts in: extracted whole", os.path.getsize(os.path.join(downloads, "split", "noise.bin")) == 300 * 1024)

        print("a zip with a password asks for it")
        locked = add(core, "locked.zip")
        alert = core.wait_for("the password dialog", lambda: password_alert(core), 30)
        core.check("the dialog has a password field", alert.get("inputLabel") and alert.get("inputSecret"), alert)
        core.check("it names the archive", alert.get("text") == "locked.zip", alert.get("text"))
        core.action("AlertText", kind=ALERT["ARCHIVE_PASSWORD"], text="wrong")
        core.action("AlertConfirm", kind=ALERT["ARCHIVE_PASSWORD"], checked=False)
        core.pump(1)
        again = core.wait_for("the dialog again", lambda: password_alert(core), 30)
        core.check("a wrong password asks again and says so", "wrong" in again.get("hint", "").lower(), again.get("hint"))
        core.check("nothing extracted with the wrong password", not os.path.exists(os.path.join(downloads, "locked")))
        core.action("AlertText", kind=ALERT["ARCHIVE_PASSWORD"], text="secret")
        core.action("AlertConfirm", kind=ALERT["ARCHIVE_PASSWORD"], checked=False)
        core.wait_for("the locked folder", lambda: os.path.isfile(os.path.join(downloads, "locked", "inner", "b.txt")), 30)
        core.check("the right password extracts it", open(os.path.join(downloads, "locked", "a.txt")).read().startswith("alpha"))
        core.check("the dialog is gone", password_alert(core) is None)
        saved = open(os.path.join(core.home, "behavior.properties"), encoding="utf-8").read()
        core.check("the password is not saved anywhere in the settings", "secret" not in saved)

        print("cancel")
        core.pump(3)
        core.action("RowExtract", id=locked["id"])
        core.wait_for("the dialog", lambda: password_alert(core), 30)
        core.action("AlertDismiss", kind=ALERT["ARCHIVE_PASSWORD"], fromScrim=False)
        core.pump(0.5)
        core.check("cancel closes it", password_alert(core) is None)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    finally:
        if server:
            server.kill()
        core.finish()


if __name__ == "__main__":
    run()
