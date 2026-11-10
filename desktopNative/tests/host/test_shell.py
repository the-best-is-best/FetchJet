import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
NATIVE = os.path.abspath(os.path.join(HERE, "..", ".."))
EXE = os.environ.get("FJ_HOST_EXE", os.path.join(NATIVE, "build", "Release", "FetchJet.exe"))
DESKRUN = os.path.join(NATIVE, "tools", "shot", "build", "Release", "deskrun.exe")
WORK = os.environ.get("FJ_HOST_WORK", "E:/tools/fjhome/hosttest").replace("/", "\\")
OUT = os.environ.get("FJ_SHOT_OUT", "E:/tools/probes/ui/shots").replace("/", "\\")
SERVER = os.environ.get("FJ_TEST_SERVER", "http://127.0.0.1:18200")
PORT = 28459

checks = 0
failures = []


def check(name, condition, detail=""):
    global checks
    checks += 1
    print(("  ok    " if condition else "  FAIL  ") + name + ("" if condition else " : " + str(detail)[:300]))
    if not condition:
        failures.append(name)


def app_arguments(extra):
    return [EXE, "--home", WORK + "\\home", "--downloads", WORK + "\\downloads", "--no-auto-downloads", "--no-focus",
            "--bridge-port", str(PORT)] + extra


def deskrun(extra, timeout_ms, after_ms=0, capture=None):
    command = [DESKRUN, "--timeout", str(timeout_ms), "--info"]
    if after_ms:
        command += ["--after", str(after_ms)]
    if capture:
        command += ["--capture", capture]
    return command + ["--"] + app_arguments(extra)


def get(path, headers=None, timeout=3):
    request = urllib.request.Request("http://127.0.0.1:%d%s" % (PORT, path), headers=headers or {})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except Exception as failure:
        return 0, repr(failure)


def to_png(path):
    try:
        from PIL import Image
        Image.open(path).save(os.path.splitext(path)[0] + ".png")
    except Exception as failure:
        print("    (no png: %s)" % failure)


def run():
    if not os.path.isfile(EXE) or not os.path.isfile(DESKRUN):
        raise SystemExit("build the host and deskrun first")
    shutil.rmtree(WORK, ignore_errors=True)
    os.makedirs(WORK + "\\home", exist_ok=True)
    os.makedirs(WORK + "\\downloads", exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    picture = OUT + "\\host-second-instance.bmp"

    print("a start with a link, nothing else running")
    alone = subprocess.run(deskrun([SERVER + "/file/2MB.bin?key=start-link"], 9000, 6000, OUT + "\\host-start-link.bmp"),
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    log_path = WORK + "\\home\\logs\\app.log"
    first_log = open(log_path, encoding="utf-8", errors="replace").read() if os.path.isfile(log_path) else ""
    check("the app starts and takes the link by itself", "dispatched a URL" in first_log and "window class 'Window Class'" in alone.stdout,
          (first_log[-300:], alone.stdout[-300:]))
    for name in [n for n in os.listdir(OUT) if n.startswith("host-start-link") and n.endswith(".bmp")]:
        to_png(os.path.join(OUT, name))

    print("first instance (hidden desktop, bridge on the test port)")
    first = subprocess.Popen(deskrun([], 26000, 17000, picture), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    status, body = 0, ""
    for _ in range(40):
        time.sleep(0.5)
        status, body = get("/ping")
        if status == 200:
            break
    check("the bridge of the real app answers", status == 200 and body == "PONG", (status, body))
    status, body = get("/config")
    check("config is served", status == 200 and '"app":"FetchJet"' in body, body[:120])

    print("second instance with a link")
    link = SERVER + "/file/1MB.bin?key=second-instance"
    started = time.time()
    second = subprocess.run(deskrun([link], 20000), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    took = time.time() - started
    check("the second instance ends by itself, quickly", second.returncode == 0 and took < 12, (second.returncode, round(took, 1), second.stdout[-300:]))
    print("    it took %.1f s" % took)

    print("second instance without a link")
    started = time.time()
    third = subprocess.run(deskrun([], 20000), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    took = time.time() - started
    check("it also ends by itself", third.returncode == 0 and took < 12, (third.returncode, round(took, 1)))

    output, _ = first.communicate(timeout=60)
    memory = re.search(r"private (\d+) KB, working set (\d+) KB", output)
    if memory:
        print("    first instance: private %.1f MB, working set %.1f MB" % (int(memory.group(1)) / 1024, int(memory.group(2)) / 1024))
    log = ""
    if os.path.isfile(log_path):
        log = open(log_path, encoding="utf-8", errors="replace").read()
    check("the first instance got the link", log.count("dispatched a URL") >= 2, log[-400:])
    check("the bridge was started on the test port", "bridge: listening on 127.0.0.1:%d" % PORT in log, log[-400:])
    crash = WORK + "\\home\\logs\\native-crash.log"
    check("no crash log", not os.path.isfile(crash) or os.path.getsize(crash) == 0)
    captured = [name for name in os.listdir(OUT) if name.startswith("host-second-instance") and name.endswith(".bmp")]
    for name in captured:
        to_png(os.path.join(OUT, name))
    print("    pictures: " + ", ".join(sorted(captured)))
    print("%d checks, %d failed" % (checks, len(failures)))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(run())
