import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bench  # noqa: E402

label, dll, runs, slots = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
queue = None if slots == "default" else {"maxConcurrentDownloads": int(slots)}
names = ["s%02d.bin" % i for i in range(50)]
size = 200 * bench.KB
lost = 0
for r in range(runs):
    key = "stress-%s-%d" % (label, r)
    items = [{"url": "%s/file/%s?size=200KB&key=%s" % (bench.SERVER, n, key), "fileName": n, "size": size} for n in names]
    rec = bench.fj_run("stress%s%d" % (label, r), "queue50", r, items, mode="queue", key=key, queue=queue, dll=dll, timeout=40)
    ok = rec.get("verified_files") == 50 and "error" not in rec
    extra = {}
    if not ok:
        lost += 1
        try:
            st = bench.server_stats(key)
            extra = {"server_requests": st.get("requests"), "server_bytes": st.get("bytes"), "expected_requests_if_49": 98}
        except Exception as exc:
            extra = {"stats_error": str(exc)}
    print(json.dumps({"label": label, "slots": slots, "run": r, "ok": ok, "seconds": rec.get("seconds"), "completed": rec.get("completed"), "error": rec.get("error"), **extra}), flush=True)
print("SUMMARY", label, slots, "runs", runs, "stuck", lost, flush=True)
