import ctypes
import hashlib
import json
import os
import queue
import shutil
import sys
import threading
import time
import urllib.request

sys.stdout.flush()
sys.stdout = os.fdopen(os.dup(1), "w", encoding="utf-8", errors="replace", buffering=1)
sys.stderr = sys.stdout

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
DEFAULT_WORK = os.environ.get("FJ_CORE_WORK", "E:/tools/fjhome/corehost")
SERVER = os.environ.get("FJ_TEST_SERVER", "http://127.0.0.1:18200")

TASK_FN = ctypes.CFUNCTYPE(None, ctypes.c_void_p)
POST_FN = ctypes.CFUNCTYPE(None, ctypes.c_void_p, ctypes.c_void_p)
NOTIFY_FN = ctypes.CFUNCTYPE(None, ctypes.c_uint)
HOST_FN = ctypes.CFUNCTYPE(ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p)
HOST_FREE_FN = ctypes.CFUNCTYPE(None, ctypes.c_void_p)

with open(os.path.join(REPO, "desktopNative", "ui", "generated", "protocol.json"), "r", encoding="utf-8") as _handle:
    PROTOCOL = json.load(_handle)
CHANNELS = {c["name"]: c["id"] for c in PROTOCOL["channels"]}
CHANNEL_KINDS = {c["name"]: c["kind"] for c in PROTOCOL["channels"]}
ENUMS = PROTOCOL["enums"]
FULL = 0x100


def dll_path(release=False):
    kind = "releaseShared" if release else "debugShared"
    return os.path.join(REPO, "desktopNative", "core", "build", "bin", "mingwX64", kind, "fjcore.dll")


class CheckFailed(Exception):
    pass


def complete(value, type_name):
    structs = PROTOCOL["structs"]
    if type_name.startswith("List<"):
        inner = type_name[5:-1]
        return [complete(item, inner) for item in (value or [])]
    if type_name in structs:
        source = value or {}
        return {name: complete(source.get(name), field_type) for name, field_type in structs[type_name]}
    if value is not None:
        return value
    return {"String": "", "Int": 0, "Float": 0.0, "Boolean": False}[type_name]


class Core:
    def __init__(self, name="default", release=None, fresh=True, config=None, host_services=None, before_start=None):
        if release is None:
            release = os.environ.get("FJ_TEST_RELEASE") == "1"
        self.work = os.path.join(DEFAULT_WORK, name).replace("\\", "/")
        self.home = self.work + "/home"
        self.downloads = self.work + "/downloads"
        if fresh and os.path.isdir(self.work):
            shutil.rmtree(self.work, ignore_errors=True)
        os.makedirs(self.home, exist_ok=True)
        os.makedirs(self.downloads, exist_ok=True)
        if before_start:
            before_start(self)
        self.tasks_queue = queue.Queue()
        self.ui_thread = threading.get_ident()
        self.changed = 0
        self.notifications = 0
        self.effects = []
        self.host_calls = []
        self.host_services = host_services or {}
        self._buffers = {}
        self.failures = []
        self.checks = 0
        path = dll_path(release)
        if not os.path.isfile(path):
            raise SystemExit("fjcore.dll not found: " + path)
        self.dll = ctypes.CDLL(path)
        self.dll.fj_start.argtypes = [ctypes.c_char_p, POST_FN, NOTIFY_FN, HOST_FN, HOST_FREE_FN]
        self.dll.fj_start.restype = ctypes.c_int
        self.dll.fj_snapshot.argtypes = [ctypes.c_int]
        self.dll.fj_snapshot.restype = ctypes.c_void_p
        self.dll.fj_action.argtypes = [ctypes.c_char_p]
        self.dll.fj_action.restype = None
        self.dll.fj_call.argtypes = [ctypes.c_char_p, ctypes.c_char_p]
        self.dll.fj_call.restype = ctypes.c_void_p
        self.dll.fj_free.argtypes = [ctypes.c_void_p]
        self.dll.fj_free.restype = None
        self.dll.fj_shutdown.argtypes = []
        self.dll.fj_shutdown.restype = None
        self._post = POST_FN(self._on_post)
        self._notify = NOTIFY_FN(self._on_notify)
        self._host = HOST_FN(self._on_host)
        self._host_free = HOST_FREE_FN(self._on_host_free)
        settings = {
            "home": self.home,
            "downloads": self.downloads,
            "debug": True,
            "headless": True,
            "bridge": False,
            "verbose": True,
            "kind": "development",
            "automaticDownloads": False,
        }
        if config:
            settings.update(config)
        self.config = settings
        started = time.time()
        code = self.dll.fj_start(json.dumps(settings).encode("utf-8"), self._post, self._notify, self._host, self._host_free)
        self.start_ms = int((time.time() - started) * 1000)
        if code != 0:
            raise SystemExit("fj_start returned %d" % code)

    def _on_post(self, fn, arg):
        self.tasks_queue.put((fn, arg))

    def _on_notify(self, mask):
        self.changed |= mask
        self.notifications += 1
        if mask & (1 << CHANNELS["effects"]):
            self.effects.extend(self.snapshot("effects"))

    def _on_host(self, name, args):
        name = name.decode("utf-8") if name else ""
        text = args.decode("utf-8") if args else "{}"
        self.host_calls.append((name, text))
        handler = self.host_services.get(name)
        result = handler(json.loads(text or "{}")) if handler else ""
        data = (result or "").encode("utf-8")
        buffer = ctypes.create_string_buffer(data)
        address = ctypes.addressof(buffer)
        self._buffers[address] = buffer
        return address

    def _on_host_free(self, pointer):
        self._buffers.pop(pointer, None)

    def pump(self, seconds=0.0):
        end = time.time() + seconds
        ran = 0
        while True:
            left = end - time.time()
            try:
                fn, arg = self.tasks_queue.get(timeout=max(0.0, min(left, 0.05)) if left > 0 else 0.0)
            except queue.Empty:
                if time.time() >= end:
                    return ran
                continue
            TASK_FN(fn)(arg)
            ran += 1

    def wait_for(self, what, predicate, timeout=30.0, step=0.05):
        end = time.time() + timeout
        while True:
            self.pump(step)
            value = predicate()
            if value:
                return value
            if time.time() >= end:
                raise CheckFailed("timed out after %.0f s waiting for %s" % (timeout, what))

    def _take(self, pointer):
        if not pointer:
            return ""
        try:
            return ctypes.string_at(pointer).decode("utf-8")
        finally:
            self.dll.fj_free(pointer)

    def snapshot_text(self, channel, full=True):
        number = CHANNELS[channel] if isinstance(channel, str) else channel
        return self._take(self.dll.fj_snapshot(number | (FULL if full else 0)))

    def snapshot(self, channel, full=True):
        text = self.snapshot_text(channel, full)
        return json.loads(text) if text else None

    def rows(self, channel):
        data = self.snapshot(channel, True) or {}
        return data.get("rows", [])

    def strings(self):
        data = self.snapshot("tr") or {}
        return dict(zip(PROTOCOL["tr"], data.get("values", [])))

    def fixture(self, path, extra=None):
        core = {}
        for channel in PROTOCOL["channels"]:
            name, kind, dto = channel["name"], channel["kind"], channel["dto"]
            if kind == "STRUCT":
                core[name] = complete(self.snapshot(name), dto)
            elif kind == "LIST":
                core[name] = complete(self.rows(name), "List<%s>" % dto)
        tr = self.snapshot("tr") or {}
        strings = dict(zip(PROTOCOL["tr"], tr.get("values", [])))
        strings["lang"] = tr.get("lang", "en")
        data = {
            "Core": core,
            "Tr": strings,
            "Dir": {"rtl": bool(tr.get("rtl", False))},
            "Theme": {"dark": bool(core.get("shell", {}).get("dark", False)), "font": tr.get("font", "Segoe UI")},
        }
        if extra:
            data.update(extra)
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(data, handle, ensure_ascii=False, indent=1)
        return data

    def action(self, action_type, **fields):
        refuse_in_tests(action_type, fields)
        body = {"type": action_type}
        body.update(fields)
        self.dll.fj_action(json.dumps(body).encode("utf-8"))
        self.pump(0.0)

    def call(self, name, args=None):
        text = json.dumps(args) if isinstance(args, (dict, list)) else (args or "")
        return self._take(self.dll.fj_call(name.encode("utf-8"), text.encode("utf-8")))

    def debug(self, name, args=None):
        text = self.call("debug." + name, args)
        try:
            return json.loads(text)
        except ValueError:
            return text

    def tasks(self):
        return self.debug("tasks") or []

    def task(self, task_id=None, file_name=None, url_part=None):
        for task in self.tasks():
            if task_id and task["id"] == task_id:
                return task
            if file_name and task["fileName"] == file_name:
                return task
            if url_part and url_part in task["url"]:
                return task
        return None

    def wait_task(self, status, timeout=60.0, **match):
        wanted = status if isinstance(status, (list, tuple, set)) else [status]

        def done():
            task = self.task(**match)
            if task and task["status"] in wanted:
                return task
            return None

        try:
            return self.wait_for("task %s to be %s" % (match, "/".join(wanted)), done, timeout)
        except CheckFailed:
            raise CheckFailed("task %s did not reach %s: %s" % (match, "/".join(wanted), json.dumps(self.task(**match))))

    def take_effects(self):
        self.pump(0.05)
        taken, self.effects = self.effects, []
        return taken

    def check(self, name, condition, detail=""):
        self.checks += 1
        if condition:
            print("  ok    " + name)
        else:
            self.failures.append(name)
            print("  FAIL  " + name + ((" : " + str(detail)) if detail else ""))
        return bool(condition)

    def log_text(self):
        path = os.path.join(self.home, "logs", "app.log")
        try:
            with open(path, "r", encoding="utf-8", errors="replace") as handle:
                return handle.read()
        except OSError:
            return ""

    def crash_text(self):
        path = os.path.join(self.home, "logs", "native-crash.log")
        try:
            with open(path, "r", encoding="utf-8", errors="replace") as handle:
                return handle.read()
        except OSError:
            return ""

    def shutdown(self):
        self.dll.fj_shutdown()
        self.pump(0.2)

    def finish(self):
        crash = self.crash_text()
        self.check("no entries in native-crash.log", crash == "", crash[:1500])
        self.shutdown()
        print("%d checks, %d failed" % (self.checks, len(self.failures)))
        sys.stdout.flush()
        os._exit(1 if self.failures else 0)


def refuse_in_tests(action_type, fields):
    key = str(fields.get("id", ""))
    reason = None
    if action_type == "DownloadTools" or (action_type == "SettingClick" and key in ("tools", "updateCheck")):
        reason = "it downloads from the internet"
    elif action_type == "SetWhenDone" and fields.get("index", 0) != 0:
        reason = "it can put the PC to sleep or shut it down"
    elif action_type == "SettingPick" and key == "whenDone" and fields.get("index", 0) != 0:
        reason = "it can put the PC to sleep or shut it down"
    elif action_type == "SettingToggle" and key == "watchClipboard" and fields.get("on"):
        reason = "it reads the owner's clipboard"
    if reason:
        raise SystemExit("action %s %s is not allowed in tests: %s" % (action_type, key, reason))


def sha256_of_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        while True:
            block = handle.read(1 << 20)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def http_text(url, timeout=10):
    with urllib.request.urlopen(url, timeout=timeout) as response:
        return response.read().decode("utf-8")


def server_sha256(path_and_query):
    text = http_text(SERVER + "/sha256/" + path_and_query).strip()
    try:
        return json.loads(text).get("sha256", text)
    except ValueError:
        return text.split()[0]


def server_stats(key=None):
    url = SERVER + "/stats" + (("?of=" + key) if key else "")
    return json.loads(http_text(url))


def server_reset():
    http_text(SERVER + "/reset")


def install_fake_ytdlp(core):
    source = os.path.join(REPO, "desktopNative", "tests", "fakes")
    exe = os.path.join(source, "build", "yt-dlp.exe")
    if not os.path.isfile(exe):
        raise SystemExit("build the fake first: desktopNative/tests/fakes/build.cmd")
    target = os.path.join(core.home, "tools", "yt-dlp-dir")
    os.makedirs(target, exist_ok=True)
    shutil.copy2(exe, os.path.join(target, "yt-dlp.exe"))
    for name in os.listdir(source):
        if name.endswith(".json"):
            shutil.copy2(os.path.join(source, name), os.path.join(target, name))
