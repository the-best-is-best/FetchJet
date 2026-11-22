import json
import os
import sys
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "corehost"))
os.environ.setdefault("FETCHJET_NO_USAGE_PING", "1")
os.environ.setdefault("FJ_TEST_RELEASE", "1")

import corehost  # noqa: E402
from corehost import Core  # noqa: E402

if os.environ.get("FJ_CORE_DLL"):
    corehost.dll_path = lambda release=False: os.environ["FJ_CORE_DLL"]

spec = json.loads(sys.argv[1])
go = threading.Event()


def reader():
    for line in sys.stdin:
        if line.strip() == "GO":
            go.set()
            return


threading.Thread(target=reader, daemon=True).start()


def say(tag, value):
    print("%s %s" % (tag, json.dumps(value)), flush=True)


def main():
    mode = spec["mode"]
    config = {"verbose": False}
    config.update(spec.get("config", {}))
    t_import = time.time()
    def before(c):
        behavior = spec.get("behavior")
        if behavior:
            with open(os.path.join(c.home, "behavior.properties"), "w") as handle:
                for key, value in behavior.items():
                    handle.write("%s=%s" % (key, value) + chr(10))

    def before_queue(c):
        before(c)
        queue = spec.get("queue")
        if queue:
            with open(os.path.join(c.home, "queue.properties"), "w") as handle:
                for key, value in queue.items():
                    handle.write("%s=%s" % (key, value) + chr(10))

    core = Core(spec["name"], release=True, fresh=spec.get("fresh", True), config=config, before_start=before_queue)
    core.pump(spec.get("idle", 2.0))
    say("READY", {"start_ms": core.start_ms, "tasks_at_start": [(t["fileName"], t["status"], t["downloadedBytes"]) for t in core.tasks()]})
    while not go.is_set():
        core.pump(0.02)
    items = spec["items"]
    if mode in ("single", "queue", "latency"):
        t0 = time.time()
        for item in items:
            core.debug("addDirect", {"url": item["url"], "fileName": item["fileName"]})
        t_added = time.time()
        pending = {item["fileName"]: item for item in items}
        done = {}
        first_progress = None
        deadline = time.time() + spec.get("timeout", 600)
        while pending and time.time() < deadline:
            core.pump(spec.get("poll", 0.004))
            tasks = core.tasks()
            by_name = {t["fileName"]: t for t in tasks}
            for name in list(pending):
                task = by_name.get(name)
                if task is None:
                    continue
                if first_progress is None and task["downloadedBytes"] > 0:
                    first_progress = time.time()
                if task["status"] == "COMPLETED":
                    path = os.path.join(task["destinationPath"], task["fileName"])
                    if os.path.isfile(path) and os.path.getsize(path) == pending[name]["size"]:
                        done[name] = (time.time(), path)
                        del pending[name]
                elif task["status"] in ("FAILED", "ERROR"):
                    done[name] = (time.time(), None)
                    del pending[name]
            if mode == "latency" and first_progress:
                break
        t1 = time.time()
        say("RESULT", {
            "t0": t0, "t_added": t_added, "t1": t1, "seconds": t1 - t0, "add_call_ms": (t_added - t0) * 1000,
            "first_progress": first_progress,
            "completed": len([1 for v in done.values() if v[1]]), "failed_or_missing": len(pending) + len([1 for v in done.values() if not v[1]]),
            "paths": [v[1] for v in done.values() if v[1]][:60],
            "tasks": [(t["fileName"], t["status"]) for t in core.tasks() if t["status"] != "COMPLETED"][:10],
        })
    elif mode == "hold":
        item = items[0]
        core.debug("addDirect", {"url": item["url"], "fileName": item["fileName"]})
        say("HELD", {"t": time.time()})
        while True:
            core.pump(0.05)
    elif mode == "kill":
        item = items[0]
        t0 = time.time()
        core.debug("addDirect", {"url": item["url"], "fileName": item["fileName"]})
        threshold = int(item["size"] * spec["kill_at"])
        while True:
            core.pump(0.01)
            task = core.task(file_name=item["fileName"])
            if task and task["downloadedBytes"] >= threshold:
                break
            if time.time() - t0 > 120:
                break
        say("KILLME", {"downloaded": task["downloadedBytes"] if task else None, "status": task["status"] if task else None, "elapsed": time.time() - t0})
        time.sleep(3600)
    elif mode == "resume":
        item = items[0]
        t0 = time.time()
        tasks = core.tasks()
        task = core.task(file_name=item["fileName"])
        before = {"status": task["status"], "downloaded": task["downloadedBytes"]} if task else None
        if task and task["status"] != "DOWNLOADING":
            core.action("Resume", id=task["id"])
        deadline = time.time() + 300
        final = None
        while time.time() < deadline:
            core.pump(0.004)
            task = core.task(file_name=item["fileName"])
            if task and task["status"] == "COMPLETED":
                path = os.path.join(task["destinationPath"], task["fileName"])
                if os.path.isfile(path) and os.path.getsize(path) == item["size"]:
                    final = path
                    break
        t1 = time.time()
        say("RESULT", {"t0": t0, "t1": t1, "seconds": t1 - t0, "path": final, "before": before})
    core.shutdown()
    os._exit(0)


try:
    main()
except SystemExit as exc:
    say("ERROR", str(exc))
    os._exit(2)
except Exception as exc:  # noqa
    import traceback
    say("ERROR", traceback.format_exc())
    os._exit(3)
