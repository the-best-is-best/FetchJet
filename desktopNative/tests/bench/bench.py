import glob
import hashlib
import json
import os
import queue
import shutil
import socket
import statistics
import subprocess
import sys
import threading
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from winproc import Watched, MB  # noqa: E402

BENCH = os.environ.get("BENCH_ROOT", "E:/tools/bench")
RESULTS = os.environ.get("BENCH_RESULTS", BENCH + "/results")
NO_CLI = os.environ.get("BENCH_NO_CLI") == "1"
FJ_DLLS = [tuple(x.split("=", 1)) for x in os.environ.get("BENCH_FJ_DLLS", "=").split(";")]
OUT = BENCH + "/out"
FJHOME = BENCH + "/fjhome"
CONTENT = BENCH + "/data/content.bin"
MDM_DIR = glob.glob(BENCH + "/mdm/app/*/")[0].replace("\\", "/")
MDM_CLI = MDM_DIR + "misterdownloadmanager-cli.exe"
MDM_APP = MDM_DIR + "misterdownloadmanager.exe"
PORT = int(os.environ.get("BENCH_PORT", "18290"))
SERVER = "http://127.0.0.1:%d" % PORT
KB = 1024
MBYTES = 1024 * 1024
GB = 1024 * MBYTES

ENV = dict(os.environ)
ENV["FETCHJET_NO_USAGE_PING"] = "1"
ENV["FJ_TEST_RELEASE"] = "1"
ENV["FJ_CORE_WORK"] = FJHOME


def get(path, timeout=30):
    with urllib.request.urlopen(SERVER + path, timeout=timeout) as r:
        return r.read()


def server_clear():
    get("/bench/clear")


def server_stats(key):
    return json.loads(get("/stats?of=" + key))


def server_events(key):
    return json.loads(get("/bench/events?key=" + key))


_expected = {}


def expected_sha(size):
    cache = RESULTS + "/expected.json"
    if not _expected and os.path.isfile(cache):
        _expected.update(json.load(open(cache)))
    k = str(size)
    if k not in _expected:
        h = hashlib.sha256()
        left = size
        with open(CONTENT, "rb") as f:
            while left > 0:
                b = f.read(min(left, 4 * MBYTES))
                h.update(b)
                left -= len(b)
        _expected[k] = h.hexdigest()
        json.dump(_expected, open(cache, "w"))
    return _expected[k]


def sha_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            b = f.read(4 * MBYTES)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


def server_metrics(key, t0):
    stats = server_stats(key)
    events = server_events(key)
    gets = [e for e in events if e["method"] == "GET"]
    arrivals = [e["arrival"] for e in events]
    data_starts = [e["body"] for e in gets if e["body"] and e["sent"] > 4096]
    any_body = [e["body"] for e in gets if e["body"]]
    return {
        "max_conn": stats.get("maxActive", 0),
        "requests": stats.get("requests", 0),
        "ports": len(set(e["port"] for e in events)),
        "bytes_served": stats.get("bytes", 0),
        "server_span_ms": (max(e["end"] for e in events) - min(arrivals)) * 1000 if arrivals else None,
        "last_end_ms": (max(e["end"] for e in events) - t0) * 1000 if arrivals else None,
        "first_request_ms": (min(arrivals) - t0) * 1000 if arrivals else None,
        "first_body_ms": (min(any_body) - t0) * 1000 if any_body else None,
        "first_data_ms": (min(data_starts) - t0) * 1000 if data_starts else None,
        "statuses": stats.get("statuses", {}),
    }


class Lines:
    def __init__(self, stream):
        self.q = queue.Queue()
        self.all = []
        threading.Thread(target=self._run, args=(stream,), daemon=True).start()

    def _run(self, stream):
        for raw in iter(stream.readline, b""):
            line = raw.decode("utf-8", "replace").strip()
            item = (time.time(), line)
            self.all.append(item)
            self.q.put(item)
        self.q.put(None)

    def wait_for(self, prefix, timeout):
        end = time.time() + timeout
        while True:
            left = end - time.time()
            if left <= 0:
                return None
            try:
                item = self.q.get(timeout=left)
            except queue.Empty:
                return None
            if item is None:
                return None
            if item[1].startswith(prefix):
                return item


def clean(*dirs):
    for d in dirs:
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d, exist_ok=True)


def file_url(name, size_text=None, key="k", extra=""):
    q = "key=%s" % key
    if size_text:
        q += "&size=%s" % size_text
    return "%s/file/%s?%s%s" % (SERVER, name, q, extra)


# ---------------------------------------------------------------- FetchJet


def fj_spec(name, items, mode="single", behavior=None, **extra):
    spec = {"name": name, "mode": mode, "items": items}
    if behavior:
        spec["behavior"] = behavior
    spec.update(extra)
    return spec


def fj_start(spec, dll=None):
    env = dict(ENV)
    if dll:
        env["FJ_CORE_DLL"] = dll
    w = Watched([sys.executable, HERE + "/fj_runner.py", json.dumps(spec)], env=env, cwd=OUT, stdin=subprocess.PIPE)
    lines = Lines(w.proc.stdout)
    return w, lines


def fj_run(variant, scenario, run, items, mode="single", behavior=None, key=None, queue=None, check_sizes=True, timeout=600, dll=None, **extra):
    extra.setdefault("timeout", timeout)
    name = "fj-%s-%s" % (variant, scenario)
    label = dll and next((l for l, p in FJ_DLLS if p == dll), "")
    shutil.rmtree(FJHOME + "/" + name, ignore_errors=True)
    server_clear()
    w, lines = fj_start(fj_spec(name, items, mode, behavior, queue=queue, **extra), dll)
    ready = lines.wait_for("READY", 60)
    if not ready:
        w.kill()
        return {"error": "no READY", "log": [l for _, l in lines.all][-5:]}
    idle = w.now_mb()
    ready_info = json.loads(ready[1][6:])
    t_go = time.time()
    w.proc.stdin.write(b"GO\n")
    w.proc.stdin.flush()
    res = lines.wait_for("RESULT", timeout)
    w.proc.wait(timeout=30)
    stats = w.stats()
    w.close()
    if not res:
        return {"error": "no RESULT", "log": [l for _, l in lines.all][-5:], **stats}
    result = json.loads(res[1][7:])
    record = {"program": "FetchJet", "variant": variant + (("@" + label) if label else ""), "scenario": scenario, "run": run,
              "seconds": result["seconds"], "idle_ws_mb": idle["ws"] if idle else None, "idle_private_mb": idle["private"] if idle else None,
              "core_start_ms": ready_info["start_ms"], **stats}
    metrics = server_metrics(key or items[0]["url"].split("key=")[1].split("&")[0], result["t0"])
    record.update(metrics)
    record["completed"] = result["completed"]
    record["failed_or_missing"] = result["failed_or_missing"]
    ok = 0
    for p in result["paths"]:
        size = os.path.getsize(p)
        want = next((i for i in items if i["fileName"] == os.path.basename(p)), None)
        if want and size == want["size"] and (not check_sizes or sha_file(p) == expected_sha(size)):
            ok += 1
    record["verified_files"] = ok
    record["expected_files"] = len(items)
    record["first_progress_ms"] = (result["first_progress"] - result["t0"]) * 1000 if result.get("first_progress") else None
    shutil.rmtree(FJHOME + "/" + name, ignore_errors=True)
    return record


# ---------------------------------------------------------------- MDM CLI


def mdm_get(variant, scenario, run, url, size, key, conns=None, outdir=None, sha=True, timeout=600):
    outdir = outdir or OUT + "/mdm"
    clean(outdir)
    server_clear()
    args = [MDM_CLI, "--json", "get", url, "-o", outdir]
    if conns:
        args += ["-c", str(conns)]
    t0 = time.time()
    w = Watched(args, env=ENV, cwd=OUT)
    lines = Lines(w.proc.stdout)
    try:
        w.proc.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        w.kill()
    t1 = time.time()
    time.sleep(0.05)
    stats = w.stats()
    w.close()
    done = None
    maxconn = 0
    for _, l in lines.all:
        try:
            j = json.loads(l)
        except ValueError:
            continue
        if j.get("event") == "done":
            done = j
        if j.get("event") == "progress":
            maxconn = max(maxconn, j.get("connections") or 0)
    files = [f for f in os.listdir(outdir)]
    path = os.path.join(outdir, os.path.basename(url.split("?")[0]))
    ok = os.path.isfile(path) and os.path.getsize(path) == size
    sha_ok = ok and (not sha or sha_file(path) == expected_sha(size))
    record = {"program": "MDM", "variant": variant, "scenario": scenario, "run": run, "seconds": t1 - t0,
              "mdm_reported_s": done["seconds"] if done else None, "mdm_max_conn_reported": maxconn,
              "exit": w.proc.returncode, "verified_files": 1 if sha_ok else 0, "expected_files": 1,
              "leftover": [f for f in files if f != os.path.basename(path)], **stats}
    record.update(server_metrics(key, t0))
    if not done:
        record["log"] = [l for _, l in lines.all][-4:]
    shutil.rmtree(outdir, ignore_errors=True)
    return record


def mdm_get_sequence(variant, scenario, run, urls, sizes, key, conns=None):
    outdir = OUT + "/mdm"
    clean(outdir)
    server_clear()
    t0 = time.time()
    cpu = 0.0
    peak = 0.0
    for url in urls:
        args = [MDM_CLI, "--json", "get", url, "-o", outdir]
        if conns:
            args += ["-c", str(conns)]
        w = Watched(args, env=ENV, cwd=OUT, sample_ms=100)
        Lines(w.proc.stdout)
        w.proc.wait(timeout=120)
        s = w.stats()
        cpu += s["cpu_s"]
        peak = max(peak, s["peak_commit_mb"])
        w.close()
    t1 = time.time()
    ok = 0
    for url, size in zip(urls, sizes):
        p = os.path.join(outdir, os.path.basename(url.split("?")[0]))
        if os.path.isfile(p) and os.path.getsize(p) == size and sha_file(p) == expected_sha(size):
            ok += 1
    record = {"program": "MDM", "variant": variant, "scenario": scenario, "run": run, "seconds": t1 - t0,
              "cpu_s": cpu, "peak_commit_mb": peak, "verified_files": ok, "expected_files": len(urls)}
    record.update(server_metrics(key, t0))
    shutil.rmtree(outdir, ignore_errors=True)
    return record


# ---------------------------------------------------------------- MDM headless app (queue)


def ipc(port, token, payload, timeout=10):
    body = dict(payload)
    body["token"] = token
    with socket.create_connection(("127.0.0.1", port), timeout=timeout) as s:
        s.sendall((json.dumps(body) + "\n").encode())
        data = s.makefile("rb").readline().decode()
    try:
        return json.loads(data)
    except ValueError:
        return data.strip()


def netstat_for(pid):
    out = subprocess.run(["netstat", "-ano"], capture_output=True, text=True, creationflags=0x08000000).stdout
    rows = []
    for line in out.splitlines():
        parts = line.split()
        if len(parts) >= 4 and parts[-1] == str(pid) and parts[0] in ("TCP", "UDP"):
            rows.append((parts[0], parts[1], parts[2], parts[3] if parts[0] == "TCP" else ""))
    return rows


def mdm_headless_queue(variant, scenario, run, urls, sizes, key, outdir=None):
    outdir = outdir or OUT + "/mdm"
    clean(outdir)
    data = BENCH + "/mdm/data-headless"
    shutil.rmtree(data, ignore_errors=True)
    os.makedirs(data, exist_ok=True)
    server_clear()
    env = dict(ENV)
    w = Watched([MDM_APP, "--headless", "--data", data, "--downloads", outdir, "--listen", "127.0.0.1", "--port", "18463"], env=env, cwd=OUT)
    Lines(w.proc.stdout)
    ipc_file = None
    seen = set()
    end = time.time() + 30
    while time.time() < end:
        seen |= set(netstat_for(w.proc.pid))
        for cand in (data + "/ipc.json", data + "/data/ipc.json"):
            if os.path.isfile(cand):
                ipc_file = cand
        if ipc_file:
            break
        time.sleep(0.05)
    if not ipc_file:
        w.kill()
        return {"error": "no ipc.json", "files": os.listdir(data)}
    time.sleep(0.3)
    info = json.load(open(ipc_file))
    port, token = info["port"], info["token"]
    ipc(port, token, {"api": True})
    stop = time.time() + 2.0
    while time.time() < stop:
        seen |= set(netstat_for(w.proc.pid))
        time.sleep(0.1)
    idle = w.now_mb()
    t0 = time.time()
    for url in urls:
        ipc(port, token, {"url": url, "dir": outdir, "silent": True})
    t_added = time.time()
    done_at = None
    states = {}
    end = time.time() + 300
    while time.time() < end:
        answer = ipc(port, token, {"query": "list"})
        downloads = answer.get("downloads", []) if isinstance(answer, dict) else []
        states = {}
        for d in downloads:
            states[d["status"]] = states.get(d["status"], 0) + 1
        if len(downloads) >= len(urls) and states.get("Complete", 0) == len(urls):
            done_at = time.time()
            break
        if states.get("Error"):
            break
        time.sleep(0.01)
    seen |= set(netstat_for(w.proc.pid))
    stats = w.stats()
    ok = 0
    for url, size in zip(urls, sizes):
        p = os.path.join(outdir, os.path.basename(url.split("?")[0]))
        if os.path.isfile(p) and os.path.getsize(p) == size and sha_file(p) == expected_sha(size):
            ok += 1
    record = {"program": "MDM", "variant": variant, "scenario": scenario, "run": run,
              "seconds": (done_at - t0) if done_at else None, "add_ms": (t_added - t0) * 1000,
              "idle_ws_mb": idle["ws"] if idle else None, "idle_private_mb": idle["private"] if idle else None,
              "states": states, "verified_files": ok, "expected_files": len(urls),
              "net_seen": sorted(set("%s %s -> %s" % (r[0], r[1], r[2]) for r in seen if not r[2].startswith(("127.0.0.1", "[::1]")) and r[2] not in ("0.0.0.0:0", "*:*", "[::]:0")))[:10], **stats}
    record.update(server_metrics(key, t0))
    w.kill()
    w.close()
    time.sleep(0.3)
    shutil.rmtree(outdir, ignore_errors=True)
    return record


# ---------------------------------------------------------------- scenarios


def save(record):
    record["time"] = time.strftime("%H:%M:%S")
    with open(RESULTS + "/runs.jsonl", "a") as f:
        f.write(json.dumps(record) + "\n")
    brief = {k: (round(v, 3) if isinstance(v, float) else v) for k, v in record.items()
             if k in ("program", "variant", "scenario", "run", "seconds", "max_conn", "verified_files", "expected_files",
                      "peak_commit_mb", "error", "first_data_ms", "bytes_served", "mdm_reported_s", "exit")}
    print(json.dumps(brief), flush=True)


TAG = os.environ.get("BENCH_TAG", "")


def single_cells(scn, size_bytes, name, runs, throttle=None, mdm_variants=((None, "default8"),), fj_variants=(("auto", None), ("c8", {"connections": 8}))):
    scn = scn + TAG
    extra = "&throttle=%d" % throttle if throttle else ""
    for r in range(runs + 1):
        order = ["fj", "mdm"] if r % 2 == 0 else ["mdm", "fj"]
        for who in order:
            if who == "fj":
                for vname, behavior in fj_variants:
                  for dlabel, dpath in (FJ_DLLS if r % 2 == 0 else FJ_DLLS[::-1]):
                    key = "fj-%s%s-%s-%d" % (vname, dlabel, scn, r)
                    url = file_url(name, None, key, extra)
                    rec = fj_run(vname, scn, r, [{"url": url, "fileName": name, "size": size_bytes}], behavior=behavior, key=key, dll=dpath or None)
                    rec["warmup"] = r == 0
                    save(rec)
            else:
                key = "mdm-app-%s-%d" % (scn, r)
                url = file_url(name, None, key, extra)
                rec = mdm_headless_queue("app-default8", scn, r, [url], [size_bytes], key)
                rec["warmup"] = r == 0
                save(rec)
                for conns, vname in ([] if NO_CLI else mdm_variants):
                    key = "mdm-cli-%s-%s-%d" % (vname, scn, r)
                    url = file_url(name, None, key, extra)
                    rec = mdm_get("cli-" + vname, scn, r, url, size_bytes, key, conns)
                    rec["warmup"] = r == 0
                    save(rec)


def scn_small(runs):
    names = ["s%02d.bin" % i for i in range(50)]
    size = 200 * KB
    for r in range(runs + 1):
        for dlabel, dpath in (FJ_DLLS if r % 2 == 0 else FJ_DLLS[::-1]):
            for vname, queue in (("auto-3slots", None), ("10-slots", {"maxConcurrentDownloads": 10}), ("unlimited-slots", {"maxConcurrentDownloads": 0})):
                key = "fj-%s%s-queue-%d" % (vname, dlabel, r)
                items = [{"url": "%s/file/%s?size=200KB&key=%s" % (SERVER, n, key), "fileName": n, "size": size} for n in names]
                rec = fj_run(vname, "queue50", r, items, mode="queue", key=key, queue=queue, dll=dpath or None)
                rec["warmup"] = r == 0
                save(rec)
        key = "mdm-app-queue-%d" % r
        urls = ["%s/file/%s?size=200KB&key=%s" % (SERVER, n, key) for n in names]
        rec = mdm_headless_queue("headless-app", "queue50", r, urls, [size] * 50, key)
        rec["warmup"] = r == 0
        save(rec)
        if NO_CLI:
            continue
        key = "mdm-cli-queue-%d" % r
        urls = ["%s/file/%s?size=200KB&key=%s" % (SERVER, n, key) for n in names]
        rec = mdm_get_sequence("cli-sequential", "queue50", r, urls, [size] * 50, key)
        rec["warmup"] = r == 0
        save(rec)


def scn_small_fj_unlimited(runs):
    names = ["s%02d.bin" % i for i in range(50)]
    size = 200 * KB
    for r in range(runs + 1):
        for vname, queue in (("unlimited-slots", {"maxConcurrentDownloads": 0}), ("10-slots", {"maxConcurrentDownloads": 10})):
            key = "fj-%s-queue-%d" % (vname, r)
            items = [{"url": "%s/file/%s?size=200KB&key=%s" % (SERVER, n, key), "fileName": n, "size": size} for n in names]
            rec = fj_run(vname, "queue50", r, items, mode="queue", key=key, queue=queue)
            rec["warmup"] = r == 0
            save(rec)


def wait_served(key, threshold, timeout=200):
    end = time.time() + timeout
    live = 0
    while time.time() < end:
        try:
            live = server_stats(key).get("liveBytes", 0)
        except Exception:
            live = 0
        if live >= threshold:
            return {"served_at_kill": live}
        time.sleep(0.005)
    return {"served_at_kill": live, "timeout": True}


def scn_resume(runs, throttle=1024, size=100 * MBYTES, kill_at=0.5, fj_behavior=None, fj_variant="auto", do_mdm=True):
    name = "100MB.bin"
    for r in range(runs + 1):
        for dlabel, dpath in (FJ_DLLS if r % 2 == 0 else FJ_DLLS[::-1]):
            key = "fj-resume%s-%d" % (dlabel, r)
            url = file_url(name, None, key, "&throttle=%d" % throttle)
            item = {"url": url, "fileName": name, "size": size}
            wname = "fj-resume"
            shutil.rmtree(FJHOME + "/" + wname, ignore_errors=True)
            server_clear()
            w, lines = fj_start(fj_spec(wname, [item], "hold", fj_behavior), dpath or None)
            ready = lines.wait_for("READY", 60)
            w.proc.stdin.write(b"GO" + bytes([10]))
            w.proc.stdin.flush()
            lines.wait_for("HELD", 30)
            killed_info = wait_served(key, size * kill_at)
            w.kill()
            w.proc.wait(timeout=30)
            w.close()
            part_files = []
            for root, _, files in os.walk(FJHOME + "/" + wname + "/downloads"):
                part_files += [os.path.join(root, f) for f in files]
            disk_after = sum(os.path.getsize(p) for p in part_files)
            t_spawn = time.time()
            w2, lines2 = fj_start(fj_spec(wname, [item], "resume", fj_behavior, fresh=False, idle=0.2), dpath or None)
            ready2 = lines2.wait_for("READY", 60)
            t_ready = time.time()
            w2.proc.stdin.write(b"GO\n")
            w2.proc.stdin.flush()
            res = lines2.wait_for("RESULT", 200)
            t_end = time.time()
            w2.proc.wait(timeout=30)
            st = w2.stats()
            w2.close()
            rj = json.loads(res[1][7:]) if res else {}
            path = rj.get("path")
            ok = bool(path) and os.path.getsize(path) == size and sha_file(path) == expected_sha(size)
            stats = server_stats(key)
            rec = {"program": "FetchJet", "variant": fj_variant + (("@" + dlabel) if dlabel else ""), "scenario": "resume", "run": r, "warmup": r == 0,
                   "seconds": t_end - t_spawn, "resume_core_s": rj.get("seconds"), "boot_s": t_ready - t_spawn, "before": rj.get("before"),
                   "killed_at": killed_info, "disk_after_kill": disk_after, "verified_files": 1 if ok else 0, "expected_files": 1, "bytes_served": stats.get("bytes"),
                   "requests": stats.get("requests"), "files_after_kill": [os.path.basename(p) for p in part_files][:6],
                   "ready2": ready2[1][:200] if ready2 else None, **st}
            save(rec)
            shutil.rmtree(FJHOME + "/" + wname, ignore_errors=True)
        if not do_mdm:
            continue
        # MDM
        key = "mdm-resume-%d" % r
        url = file_url(name, None, key, "&throttle=%d" % throttle)
        outdir = OUT + "/mdm"
        clean(outdir)
        server_clear()
        w = Watched([MDM_CLI, "--json", "get", url, "-o", outdir], env=ENV, cwd=OUT)
        lines = Lines(w.proc.stdout)
        killed = wait_served(key, size * kill_at)
        w.kill()
        w.proc.wait(timeout=30)
        w.close()
        time.sleep(0.2)
        left = os.listdir(outdir)
        states = [f for f in left if f.endswith(".mdm.json")]
        t_spawn = time.time()
        if not states:
            save({"program": "MDM", "variant": "default8", "scenario": "resume", "run": r, "error": "no state file", "left": left})
            continue
        w = Watched([MDM_CLI, "--json", "resume", os.path.join(outdir, states[0])], env=ENV, cwd=OUT)
        lines = Lines(w.proc.stdout)
        w.proc.wait(timeout=300)
        t_end = time.time()
        st = w.stats()
        w.close()
        path = os.path.join(outdir, name)
        ok = os.path.isfile(path) and os.path.getsize(path) == size and sha_file(path) == expected_sha(size)
        stats = server_stats(key)
        rec = {"program": "MDM", "variant": "default8", "scenario": "resume", "run": r, "warmup": r == 0, "seconds": t_end - t_spawn,
               "killed_at": killed, "files_after_kill": left, "verified_files": 1 if ok else 0, "expected_files": 1,
               "bytes_served": stats.get("bytes"), "requests": stats.get("requests"), "tail": [l for _, l in lines.all][-2:], **st}
        save(rec)
        shutil.rmtree(outdir, ignore_errors=True)


def main():
    which = sys.argv[1]
    runs = int(sys.argv[2]) if len(sys.argv) > 2 else 5
    os.makedirs(RESULTS, exist_ok=True)
    if which == "s1":
        single_cells("1MB", 1 * MBYTES, "1MB.bin", runs)
    elif which == "s2":
        single_cells("10MB", 10 * MBYTES, "10MB.bin", runs)
    elif which == "s3":
        single_cells("100MB", 100 * MBYTES, "100MB.bin", runs, mdm_variants=((None, "default8"), (32, "c32")))
    elif which == "s4":
        single_cells("1GB", GB, "1GB.bin", runs, mdm_variants=((None, "default8"), (32, "c32")))
    elif which == "s5":
        scn_small(runs)
    elif which == "s6":
        single_cells("throttled100", 100 * MBYTES, "100MB.bin", runs, throttle=2048, mdm_variants=((None, "default8"), (32, "c32")))
    elif which == "s7":
        scn_resume(runs)
    elif which == "s5b":
        scn_small_fj_unlimited(runs)
    elif which == "s7b":
        scn_resume(runs, fj_behavior={"connections": 8}, fj_variant="c8", do_mdm=False)
    elif which == "s8":
        single_cells("latency1MB", 1 * MBYTES, "1MB.bin", runs)
    elif which == "smoke":
        single_cells("smoke", 10 * MBYTES, "10MB.bin", 1)


if __name__ == "__main__":
    main()
