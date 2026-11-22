import json
import statistics
import sys
from collections import OrderedDict

path = sys.argv[1] if len(sys.argv) > 1 else "E:/tools/bench/results/runs.jsonl"
rows = [json.loads(l) for l in open(path)]
groups = OrderedDict()
for r in rows:
    if r.get("warmup") or "error" in r:
        continue
    groups.setdefault((r["scenario"], r["program"], r["variant"]), []).append(r)


def col(rs, name):
    return [r[name] for r in rs if r.get(name) is not None]


def fmt(values, scale=1.0, digits=3):
    if not values:
        return "-"
    v = [x * scale for x in values]
    return "%s (%s-%s)" % (round(statistics.median(v), digits), round(min(v), digits), round(max(v), digits))


current = None
for (scn, prog, var), rs in groups.items():
    if scn == "queue50" and var == "auto":
        var = "auto-3slots"
    if scn != current:
        current = scn
        print("\n### %s" % scn)
        print("| program / variant | n | time s median (min-max) | conns (server max) | requests | verified ok | peak commit MB | idle WS MB | CPU s | first data ms |")
        print("|---|---|---|---|---|---|---|---|---|---|")
    ok = sum(r["verified_files"] for r in rs)
    exp = sum(r["expected_files"] for r in rs)
    print("| %s %s | %d | %s | %s | %s | %d/%d | %s | %s | %s | %s |" % (
        prog, var, len(rs), fmt(col(rs, "seconds")), fmt(col(rs, "max_conn"), 1, 1), fmt(col(rs, "requests"), 1, 0), ok, exp,
        fmt(col(rs, "peak_commit_mb"), 1, 1), fmt(col(rs, "idle_ws_mb"), 1, 1), fmt(col(rs, "cpu_s"), 1, 2), fmt(col(rs, "first_data_ms"), 1, 1)))
errors = [r for r in rows if "error" in r]
if errors:
    print("\nerrors:", len(errors))
    for e in errors[:5]:
        print(json.dumps(e)[:300])
