import os
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
NATIVE = os.path.abspath(os.path.join(HERE, "..", ".."))
BUILD = os.environ.get("FJ_HOST_DIR", os.path.join(NATIVE, "build", "Release"))
DESKRUN = os.path.join(NATIVE, "tools", "shot", "build", "Release", "deskrun.exe")
WORK = os.environ.get("FJ_HOST_WORK", "E:/tools/fjhome/portable-update").replace("/", "\\")
PORT = 28457
NEW_MARK = b"FJ-PORTABLE-UPDATE-NEW-BUILD"

checks = 0
failures = []


def check(name, condition, detail=""):
    global checks
    checks += 1
    print(("  ok    " if condition else "  FAIL  ") + name + ("" if condition else " : " + str(detail)[:400]))
    if not condition:
        failures.append(name)


def ping():
    try:
        with urllib.request.urlopen("http://127.0.0.1:%d/ping" % PORT, timeout=2) as response:
            return response.status
    except Exception:
        return 0


def processes_in(folder):
    script = ("Get-Process FetchJet -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '%s\\*' } | "
              "ForEach-Object { $_.Id }") % folder
    out = subprocess.run(["powershell", "-NoProfile", "-Command", script], stdout=subprocess.PIPE, text=True).stdout
    return [int(line) for line in out.split() if line.strip().isdigit()]


def stop(folder):
    for pid in processes_in(folder):
        subprocess.run(["taskkill", "/F", "/PID", str(pid)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def old_files(folder):
    found = []
    for base, dirs, files in os.walk(folder):
        found += [os.path.join(base, f) for f in files if f.lower().endswith(".fjold")]
    return found


def run():
    if not os.path.isfile(os.path.join(BUILD, "FetchJet.exe")) or not os.path.isfile(DESKRUN):
        raise SystemExit("build the host and deskrun first")
    app = WORK + "\\app"
    stop(app)
    shutil.rmtree(WORK, ignore_errors=True)
    shutil.copytree(BUILD, app)
    open(app + "\\FetchJet.portable", "w").write("portable\n")
    os.makedirs(app + "\\data", exist_ok=True)
    open(app + "\\data\\keep.txt", "w").write("the user's data\n")
    old_size = os.path.getsize(app + "\\FetchJet.exe")

    new_tree = WORK + "\\new"
    shutil.copytree(BUILD, new_tree)
    open(new_tree + "\\FetchJet.portable", "w").write("portable\n")
    with open(new_tree + "\\FetchJet.exe", "ab") as exe:
        exe.write(NEW_MARK)
    os.makedirs(new_tree + "\\docs", exist_ok=True)
    open(new_tree + "\\docs\\new-file.txt", "w").write("only in the new version\n")
    downloads = WORK + "\\download"
    os.makedirs(downloads)
    zip_path = downloads + "\\FetchJet-9.9.9-windows-portable.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as archive:
        for base, dirs, files in os.walk(new_tree):
            for name in files:
                full = os.path.join(base, name)
                archive.write(full, os.path.relpath(full, new_tree))

    env = dict(os.environ)
    env["FJ_TEST_INSTALL_UPDATE"] = zip_path
    env["FETCHJET_NO_USAGE_PING"] = "1"
    env.pop("FETCHJET_HOME", None)
    command = [DESKRUN, "--timeout", "60000", "--", app + "\\FetchJet.exe", "--debug", "--no-auto-downloads", "--no-focus",
               "--bridge-port", str(PORT)]
    print("the old portable app downloads nothing, installs the zip and restarts itself")
    started = time.time()
    result = subprocess.run(command, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    took = time.time() - started
    check("the old app closed by itself", took < 55, (took, result.stdout[-400:]))

    deadline = time.time() + 30
    while time.time() < deadline and not (processes_in(app) and ping() == 200 and not old_files(app)):
        time.sleep(0.5)

    with open(app + "\\FetchJet.exe", "rb") as exe:
        exe.seek(-len(NEW_MARK), 2)
        is_new = exe.read() == NEW_MARK
    check("FetchJet.exe in the folder is the new one", is_new and os.path.getsize(app + "\\FetchJet.exe") == old_size + len(NEW_MARK))
    check("a file only in the new version is there", os.path.isfile(app + "\\docs\\new-file.txt"))
    check("the user's data folder is kept", open(app + "\\data\\keep.txt").read() == "the user's data\n")
    check("the portable marker is still there", os.path.isfile(app + "\\FetchJet.portable"))
    running = processes_in(app)
    check("the new app is running from the same folder", len(running) == 1, running)
    check("it answers on the same bridge port (options kept)", ping() == 200)
    check("the old files were deleted by the new app", old_files(app) == [], old_files(app))
    check("the unzip folder was removed", not os.path.exists(app + "\\.fetchjet-update"))
    check("the downloaded zip is not left behind", not os.path.exists(zip_path))
    log_path = app + "\\data\\.fetch_jet\\logs\\app.log"
    log = open(log_path, encoding="utf-8", errors="replace").read() if os.path.isfile(log_path) else ""
    check("the log says the files were replaced and the new app started",
          "files of the portable FetchJet replaced" in log and "starting the new portable FetchJet" in log, log[-1200:])
    check("no update script and no cmd/powershell were used", "cmd.exe" not in log.lower() and "powershell" not in log.lower())

    stop(app)
    print("%d checks, %d failed" % (checks, len(failures)))
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    run()
