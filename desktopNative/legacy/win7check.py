import os
import re
import subprocess
import sys

DUMPBIN = os.environ.get("FJ_DUMPBIN", "dumpbin.exe")
SYS = sys.argv[1]
SHIPPED = sys.argv[2]
binaries = sys.argv[3:]


def run(*args):
    return subprocess.run([DUMPBIN, "/nologo", *args], capture_output=True, text=True, errors="ignore").stdout


export_cache = {}


def exports(path):
    if path in export_cache:
        return export_cache[path]
    names = set()
    for line in run("/exports", path).splitlines():
        m = re.match(r"^\s+\d+\s+[0-9A-F]+\s+(?:[0-9A-F]{8}\s+)?(\S+)", line)
        if m:
            names.add(m.group(1))
    export_cache[path] = names
    return names


def imports(path):
    out = {}
    cur = None
    for line in run("/imports", path).splitlines():
        m = re.match(r"^\s{4}(\S+\.(?:dll|DLL|drv))\s*$", line)
        if m:
            cur = m.group(1).lower()
            out.setdefault(cur, set())
            continue
        m = re.match(r"^\s+[0-9A-F]+\s+([A-Za-z_?@][\w@?$]*)\s*$", line)
        if m and cur:
            out[cur].add(m.group(1))
    return out


def find(dll):
    if dll == "comctl32.dll":
        return os.path.join(SYS, "comctl32v6.dll")
    for folder in (SHIPPED, SYS):
        p = os.path.join(folder, dll)
        if os.path.exists(p):
            return p
    return None


bad = 0
for b in binaries:
    found = imports(b)
    if not found:
        print(os.path.basename(b), "PROBLEMS")
        print("    no imports read (dumpbin missing?)")
        bad += 1
        continue
    problems = []
    for dll, names in sorted(found.items()):
        if dll.startswith("api-ms-win-crt-"):
            if not os.path.exists(os.path.join(SHIPPED, dll)):
                problems.append(f"{dll} not shipped")
            continue
        path = find(dll)
        if not path:
            problems.append(f"{dll}: DLL not on Windows 7 and not shipped")
            continue
        ex = exports(path)
        missing = sorted(n for n in names if n not in ex and n not in ("Characteristics", "_RDATA", "fothk", "malloc_h"))
        if missing:
            problems.append(f"{dll} ({os.path.basename(os.path.dirname(path))}): missing {missing}")
    print(os.path.basename(b), "OK" if not problems else "PROBLEMS")
    for p in problems:
        print("   ", p)
    bad += bool(problems)
sys.exit(1 if bad else 0)
