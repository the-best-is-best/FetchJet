import os
import subprocess
import sys
import time
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed

PORT = int(os.environ.get("FJ_SCAN_PORT", "18262"))
BASE = "http://127.0.0.1:%d" % PORT

FAKE_SCANNER = """import os, sys
path = sys.argv[1]
name = os.path.basename(path)
with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "scanned.txt"), "a", encoding="utf-8") as log:
    log.write(name + "\\n")
if "gone" in name:
    os.remove(path)
    sys.exit(0)
if "bad" in name:
    print("FAKE-Virus found")
    sys.exit(1)
sys.exit(0)
"""


def make_site(folder):
    os.makedirs(folder, exist_ok=True)
    for name in ("plain-a.bin", "plain-b.bin", "plain-c.bin", "bad-one.bin", "gone-one.bin", "plain-d.bin"):
        open(os.path.join(folder, name), "wb").write(os.urandom(200 * 1024))
    for name in ("bad-pack.zip", "good-pack.zip"):
        with zipfile.ZipFile(os.path.join(folder, name), "w") as z:
            z.writestr("a.txt", b"a" * 1000)
            z.writestr("b.txt", b"b" * 1000)


def add_and_wait(core, name):
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text=BASE + "/" + name)
    core.pump(0.6)
    core.action("StartDownload")
    return core.wait_task(["COMPLETED", "ERROR"], timeout=40, file_name=name)


def scan_line(core, name):
    for line in core.log_text().splitlines():
        if "Scan " + name + ":" in line:
            return line
    return ""


def details_of(core, task_id):
    core.action("RowClick", id=task_id, control=False, shift=False)
    core.pump(0.6)
    return core.snapshot("details") or {}


def run():
    core = Core("scan")
    site = os.path.join(core.work, "site")
    make_site(site)
    tools = os.path.join(core.work, "tools")
    os.makedirs(tools, exist_ok=True)
    scanner = os.path.join(tools, "fakescan.py")
    open(scanner, "w", encoding="utf-8").write(FAKE_SCANNER)
    scanned_list = os.path.join(tools, "scanned.txt")
    server = subprocess.Popen([sys.executable, "-m", "http.server", str(PORT), "--bind", "127.0.0.1", "--directory", site],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    downloads = core.downloads
    try:
        core.pump(0.5)
        print("off by default")
        ids = [r.get("id") for r in core.rows("settingsRows")]
        add_and_wait(core, "plain-a.bin")
        core.pump(2)
        core.check("no scan while the setting is off", scan_line(core, "plain-a.bin") == "", scan_line(core, "plain-a.bin"))

        core.action("SettingToggle", id="scanFiles", on=True)
        core.pump(0.5)
        saved = open(os.path.join(core.home, "behavior.properties"), encoding="utf-8").read()
        core.check("the setting is saved", "scanFiles=true" in saved, saved)
        done = add_and_wait(core, "plain-b.bin")
        line = core.wait_for("the scan of plain-b.bin", lambda: scan_line(core, "plain-b.bin"), 60)
        path = os.path.join(downloads, "plain-b.bin")
        if os.name == "nt":
            print("on, with the antivirus Windows knows")
            core.check("Windows' attachment scan says clean", ": clean" in line, line)
            zone = ""
            try:
                zone = open(path + ":Zone.Identifier", encoding="utf-8", errors="replace").read()
            except OSError as error:
                zone = "missing: %s" % error
            core.check("the file got the Mark of the Web (the scan ran through Windows)", "ZoneId=3" in zone, zone)
            core.check("the source address is in it", BASE + "/plain-b.bin" in zone, zone)
            core.check("the file is still there", os.path.isfile(path))
            shown = details_of(core, done["id"])
            core.check("the details show the clean result", shown.get("scanShown") and shown.get("scanText"), shown.get("scanText"))
        else:
            print("on, with no command: this OS has no built-in scanner")
            core.check("no scanner = 'none', not clean", ": none" in line, line)
            core.check("the file is still there", os.path.isfile(path))
            shown = details_of(core, done["id"])
            core.check("the details show no scan verdict", not shown.get("scanShown"), shown.get("scanText"))
        core.check("an already finished file is not scanned again", not scan_line(core, "plain-a.bin"))

        print("a scanner command")
        command = '"%s" "%s" [File]' % (sys.executable, scanner)
        core.action("SettingText", id="scanCommand", text=command)
        core.pump(0.5)
        saved = open(os.path.join(core.home, "behavior.properties"), encoding="utf-8").read()
        core.check("the command is saved", "scanCommand=" in saved and "fakescan.py" in saved, saved)
        add_and_wait(core, "plain-c.bin")
        line = core.wait_for("the scan of plain-c.bin", lambda: scan_line(core, "plain-c.bin"), 60)
        core.check("exit code 0 = clean", ": clean" in line, line)
        core.check("the command got the file", "plain-c.bin" in open(scanned_list, encoding="utf-8").read())

        bad = add_and_wait(core, "bad-one.bin")
        line = core.wait_for("the scan of bad-one.bin", lambda: scan_line(core, "bad-one.bin"), 60)
        core.check("a non-zero exit code = threat, with the scanner's last line", ": threat" in line and "FAKE-Virus" in line, line)
        shown = details_of(core, bad["id"])
        core.check("the details show the threat in the error colour", shown.get("scanShown") and shown.get("scanTone") == 5, shown)

        add_and_wait(core, "gone-one.bin")
        line = core.wait_for("the scan of gone-one.bin", lambda: scan_line(core, "gone-one.bin"), 60)
        core.check("a file the scanner removed = threat", ": threat" in line and "removed" in line, line)

        print("scan, then extract")
        core.action("SettingToggle", id="extractArchives", on=True)
        core.pump(0.4)
        add_and_wait(core, "bad-pack.zip")
        core.wait_for("the scan of bad-pack.zip", lambda: scan_line(core, "bad-pack.zip"), 60)
        core.pump(3)
        core.check("an archive with a threat is not extracted", not os.path.exists(os.path.join(downloads, "bad-pack")), os.listdir(downloads))
        add_and_wait(core, "good-pack.zip")
        core.wait_for("the folder good-pack", lambda: os.path.isfile(os.path.join(downloads, "good-pack", "b.txt")), 30)
        core.check("a clean archive is extracted after the scan", True)
        core.action("SettingToggle", id="extractArchives", on=False)

        print("restart keeps the result")
        core.pump(2)
        cache = open(os.path.join(core.home, "settings", "tasks_cache.json"), encoding="utf-8").read() \
            if os.path.isfile(os.path.join(core.home, "settings", "tasks_cache.json")) else ""
        if not cache:
            for root, _, files in os.walk(core.home):
                if "tasks_cache.json" in files:
                    cache = open(os.path.join(root, "tasks_cache.json"), encoding="utf-8").read()
                    break
        core.check("the verdict is saved with the task", '"scanResult":"threat"' in cache.replace(" ", "") and
                   '"scanResult":"clean"' in cache.replace(" ", ""), cache[:300])

        print("off again")
        core.action("SettingToggle", id="scanFiles", on=False)
        core.pump(0.4)
        add_and_wait(core, "plain-d.bin")
        core.pump(3)
        core.check("off again: no scan", scan_line(core, "plain-d.bin") == "")
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    finally:
        server.kill()
    core.finish()


if __name__ == "__main__":
    run()
