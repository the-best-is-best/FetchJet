import json, sys
sys.argv = ["x"]
import bench
for poll in (0.004, 0.05, 0.25):
    for r in range(2):
        key = "probe-poll-%s-%d" % (poll, r)
        url = bench.file_url("1GB.bin", None, key)
        rec = bench.fj_run("c8", "probe", r, [{"url": url, "fileName": "1GB.bin", "size": bench.GB}], behavior={"connections": 8}, key=key, poll=poll)
        print(poll, round(rec["seconds"], 2), "cpu", round(rec["cpu_s"], 1), "commit", round(rec["peak_commit_mb"], 1), "ok", rec["verified_files"], flush=True)
