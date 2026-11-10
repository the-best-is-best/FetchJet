import hashlib
import json
import os
import re
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET

SDK = os.environ.get("ANDROID_SDK", r"C:\Users\miche\AppData\Local\Android\Sdk")
ADB = os.path.join(SDK, "platform-tools", "adb.exe")
SERIAL = os.environ.get("FJ_EMULATOR", "emulator-5554")
PKG = "com.michelle.raouf.fetch_jet"
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
APK = os.path.join(REPO, "androidApp", "build", "outputs", "apk", "debug", "androidApp-x86_64-debug.apk")
EXE = os.path.join(HERE, "build", "bin", "mingwX64", "debugExecutable", "winhelpers.exe")
WORK = os.environ.get("FJ_TEST_WORK", "E:/tools/probes/winhelpers-work") + "/emu"
FORWARD = 28472
HTTP_PORT = 18295
RUN = time.strftime("%H%M%S")
LINK_NAME = f"fj-link-{RUN}.bin"
HANDED_NAME = f"fj-handed-{RUN}.bin"

passes = 0
failures = 0


def check(name, ok, detail=""):
    global passes, failures
    if ok:
        passes += 1
    else:
        failures += 1
    print(("PASS  " if ok else "FAIL  ") + name + (f"  [{detail}]" if detail and not ok else ""), flush=True)


def adb(*args, check_rc=True, timeout=120):
    result = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, timeout=timeout)
    text = result.stdout.decode("utf-8", "replace") + result.stderr.decode("utf-8", "replace")
    if check_rc and result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {text.strip()}")
    return text


def run_as(command):
    return adb("shell", f"run-as {PKG} sh -c '{command}'", check_rc=False)


def wait_for(timeout, test, step=0.5):
    end = time.time() + timeout
    while time.time() < end:
        value = test()
        if value:
            return value
        time.sleep(step)
    return test()


def ui_nodes():
    adb("shell", "uiautomator dump /sdcard/fj-ui.xml", check_rc=False, timeout=60)
    raw = subprocess.run([ADB, "-s", SERIAL, "exec-out", "cat /sdcard/fj-ui.xml"], capture_output=True, timeout=60).stdout
    try:
        root = ET.fromstring(raw.decode("utf-8", "replace"))
    except ET.ParseError:
        return []
    nodes = []
    for node in root.iter("node"):
        bounds = re.findall(r"\d+", node.get("bounds", ""))
        if len(bounds) == 4:
            x1, y1, x2, y2 = map(int, bounds)
            nodes.append((node.get("text", ""), node.get("content-desc", ""), (x1 + x2) // 2, (y1 + y2) // 2))
    return nodes


def find_text(text):
    for label, desc, x, y in ui_nodes():
        if label == text or desc == text:
            return x, y
    return None


def screen_has(text):
    return any(text in label or text in desc for label, desc, _, _ in ui_nodes())


def tap_text(text, timeout=30):
    spot = wait_for(timeout, lambda: find_text(text), 1.0)
    if spot:
        adb("shell", f"input tap {spot[0]} {spot[1]}")
    return spot is not None


def logcat_sync():
    return [line for line in adb("logcat", "-d", check_rc=False, timeout=60).splitlines() if "FJSYNC" in line]


def tasks():
    raw = run_as("cat files/settings/tasks_cache.json")
    try:
        data = json.loads(raw)
    except ValueError:
        return []
    return data if isinstance(data, list) else data.get("tasks", [])


def task_named(name):
    return next((t for t in tasks() if t.get("fileName") == name), None)


class Peer:
    def __init__(self):
        self.lines = []
        self.lock = threading.Lock()
        self.process = subprocess.Popen(
            [EXE, WORK, "syncpeer", "host=127.0.0.1", f"tcp={FORWARD}", "udp=28491"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        )
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self):
        for raw in self.process.stdout:
            line = raw.decode("utf-8", "replace").rstrip("\r\n")
            print("PEER| " + line, flush=True)
            with self.lock:
                self.lines.append(line)

    def send(self, *parts):
        self.process.stdin.write(("\t".join(parts) + "\n").encode("utf-8"))
        self.process.stdin.flush()

    def count(self):
        with self.lock:
            return len(self.lines)

    def wait_line(self, timeout, test, start=0):
        def found():
            with self.lock:
                hit = next((l for l in self.lines[start:] if test(l)), None)
            if hit is None:
                self.send("tick")
            return hit
        return wait_for(timeout, found, 0.3)

    def close(self):
        try:
            self.send("quit")
            self.process.wait(timeout=10)
        except Exception:
            self.process.kill()


def main():
    devices = subprocess.run([ADB, "devices"], capture_output=True, text=True).stdout
    if not SERIAL.startswith("emulator-") or f"{SERIAL}\tdevice" not in devices:
        print(f"no emulator {SERIAL}: {devices}")
        return 2
    os.makedirs(WORK, exist_ok=True)
    link_bytes = os.urandom(100_000)
    handed_bytes = os.urandom(300_000)
    with open(os.path.join(WORK, LINK_NAME), "wb") as handle:
        handle.write(link_bytes)
    handed_path = os.path.join(WORK, HANDED_NAME)
    with open(handed_path, "wb") as handle:
        handle.write(handed_bytes)

    out = adb("install", "-r", APK, check_rc=False, timeout=300)
    if "Success" not in out and "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in out:
        adb("uninstall", PKG, check_rc=False)
        out = adb("install", "-r", APK, check_rc=False, timeout=300)
    check("debug APK installed on the emulator", "Success" in out, out.strip()[-300:])
    adb("shell", f"am force-stop {PKG}", check_rc=False)
    adb("shell", f"pm grant {PKG} android.permission.POST_NOTIFICATIONS", check_rc=False)
    adb("shell", f"dumpsys deviceidle whitelist +{PKG}", check_rc=False)
    adb("shell", "input keyevent KEYCODE_HOME", check_rc=False)
    run_as("mkdir -p files/settings; f=files/settings/behavior.properties; touch $f; grep -v ^visibleToDevices= $f > $f.tmp; echo visibleToDevices=true >> $f.tmp; mv $f.tmp $f")
    adb("logcat", "-c", check_rc=False)
    visible = False
    for _ in range(3):
        time.sleep(2)
        adb("shell", f"am start -W -f 0x10008000 -n {PKG}/.MainActivity", check_rc=False)
        visible = wait_for(20, lambda: any("FJSYNC visible tcpPort=28462" in l for l in logcat_sync()), 1.0)
        if visible:
            break
    check("emulator app is visible on TCP 28462 (inside the emulator)", visible, "\n".join(logcat_sync()[-10:]))

    http = subprocess.Popen([sys.executable, "-m", "http.server", str(HTTP_PORT), "--bind", "127.0.0.1", "--directory", WORK],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    adb("forward", f"tcp:{FORWARD}", "tcp:28462")
    peer = Peer()
    try:
        hello = peer.wait_line(20, lambda l: l.startswith("PEER\t"))
        check("native peer started (hidden, nothing bound)", hello is not None)

        start = peer.count()
        peer.send("pair", "127.0.0.1")
        confirm = peer.wait_line(20, lambda l: l.startswith("PSTATE\tconfirm:"), start)
        check("native -> emulator: hello answered with a code", confirm is not None)
        code, emu_id = confirm.split(":")[1], confirm.split(":")[2]
        shown = wait_for(20, lambda: screen_has(code), 1.0)
        check("emulator shows the same 6-digit code", shown, code)
        check("emulator: tap Codes match", tap_text("Codes match"))
        peer.send("confirm")
        start = peer.count()
        peer.send("state")
        state = peer.wait_line(5, lambda l: l.startswith("STATE\t"), start)
        check("native side stored the pairing", state is not None and emu_id in state.split("\t")[2], state)
        paired = wait_for(10, lambda: hello.split("\t")[1] in run_as("cat files/settings/devices.json"), 1.0)
        check("emulator stored the pairing (devices.json)", paired)

        start = peer.count()
        link_url = f"http://10.0.2.2:{HTTP_PORT}/{LINK_NAME}"
        peer.send("link", emu_id, link_url, LINK_NAME)
        check("link only: emulator shows the offer", tap_text("Accept", 30))
        sent = peer.wait_line(30, lambda l: l.startswith("SENT\t"), start)
        check("link only: native sender gets success", sent is not None and sent.split("\t")[1] == "true", sent)
        link_task = wait_for(30, lambda: task_named(LINK_NAME), 1.0)
        check("link only: emulator queued the task (url stored encrypted)", link_task is not None and (link_task.get("url") == link_url or str(link_task.get("url", "")).startswith("fjenc1:")), link_task)
        done = wait_for(60, lambda: (task_named(LINK_NAME) or {}).get("status") == "COMPLETED", 2.0)
        check("link only: emulator downloaded it from the host (loopback server)", done, task_named(LINK_NAME))

        start = peer.count()
        handed_url = f"http://10.0.2.2:{HTTP_PORT}/{HANDED_NAME}"
        peer.send("finished", emu_id, handed_url, handed_path, HANDED_NAME, str(len(handed_bytes)))
        check("transfer: emulator shows the offer", tap_text("Accept", 30))
        sent = peer.wait_line(60, lambda l: l.startswith("SENT\t"), start)
        check("transfer: native sender gets success", sent is not None and sent.split("\t")[1] == "true", sent)
        received = wait_for(30, lambda: any("FJSYNC" in l and HANDED_NAME in l for l in logcat_sync()), 1.0)
        check("transfer: emulator logged the offer for the file", received)
        finished = wait_for(60, lambda: (task_named(HANDED_NAME) or {}).get("status") == "COMPLETED", 2.0)
        task = task_named(HANDED_NAME) or {}
        check("transfer: emulator finished the task from the parts", finished, task)
        target = f"{task.get('destinationPath', '').rstrip('/')}/{HANDED_NAME}"
        digest = adb("shell", f"sha256sum '{target}'", check_rc=False).split(" ")[0].strip()
        if len(digest) != 64:
            digest = run_as(f"sha256sum \"{target}\"").split(" ")[0].strip()
        check("transfer: the file on the emulator equals the sent bytes (SHA-256)", digest == hashlib.sha256(handed_bytes).hexdigest(), f"{target} {digest}")
    finally:
        peer.close()
        adb("forward", "--remove", f"tcp:{FORWARD}", check_rc=False)
        http.terminate()
    print("\n".join(logcat_sync()[-25:]))
    print(f"DONE  emulator sync: {passes} passed, {failures} failed")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
