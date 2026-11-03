"""Makes a 16 KB torrent with no web seed (nothing downloads) for tracker tests (TESTING.md).

  python3 scripts/security-test/tiny_torrent.py out.torrent udp://tracker.bittor.pw:1337/announce ...

Serve it with `python3 -m http.server 18100 --bind 127.0.0.1` and add its URL through the bridge.
The verbose log line `[torrent ...] ... trackers:` then shows each tracker as ok / fails.
"""
import hashlib
import os
import sys


def bencode(x):
    if isinstance(x, int):
        return b"i%de" % x
    if isinstance(x, str):
        x = x.encode()
    if isinstance(x, bytes):
        return b"%d:" % len(x) + x
    if isinstance(x, list):
        return b"l" + b"".join(bencode(i) for i in x) + b"e"
    return b"d" + b"".join(bencode(k) + bencode(x[k]) for k in sorted(x)) + b"e"


out, trackers = sys.argv[1], sys.argv[2:]
data = os.urandom(16384)
info = {"name": os.path.splitext(os.path.basename(out))[0] + ".bin", "length": len(data),
        "piece length": 16384, "pieces": hashlib.sha1(data).digest()}
meta = {"info": info}
if trackers:
    meta["announce"] = trackers[0]
    meta["announce-list"] = [[t] for t in trackers]
open(out, "wb").write(bencode(meta))
print("wrote", out)
