import json, sys
sys.argv = ["x"]
import bench
for r in range(2):
    key = "probe-phase-%d" % r
    url = bench.file_url("1GB.bin", None, key)
    rec = bench.fj_run("c8", "probe", r, [{"url": url, "fileName": "1GB.bin", "size": bench.GB}], behavior={"connections": 8}, key=key)
    print("total", round(rec["seconds"], 2), "last server byte at", round(rec["last_end_ms"]/1000, 2), "span", round(rec["server_span_ms"]/1000,2), "cpu", round(rec["cpu_s"],1), flush=True)
