import hashlib
import os
import random
import sys
import zipfile

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
rng = random.Random(11)
files = {
    "readme.txt": b"plain ascii\n" * 50,
    "docs/عربي/ملف نصي.txt": ("محتوى عربي\n" + "line\n" * 100).encode("utf-8"),
    "docs/中文/文件.bin": bytes(rng.getrandbits(8) for _ in range(70000)),
    "emoji 😀/🎵 song.txt": "music".encode("utf-8"),
    "deep/a/b/c/d/e/f/file.dat": b"x" * 300000,
    "stored/raw.bin": bytes(rng.getrandbits(8) for _ in range(200000)),
    "empty.txt": b"",
    "big/random-50mb.bin": rng.randbytes(50 * 1024 * 1024),
    "big/zeros-30mb.bin": b"\0" * (30 * 1024 * 1024),
    "yt-dlp/_internal/lib.pyd": rng.randbytes(3 * 1024 * 1024),
}
with zipfile.ZipFile(os.path.join(out, "test.zip"), "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for name, data in files.items():
        method = zipfile.ZIP_STORED if name.startswith("stored/") else zipfile.ZIP_DEFLATED
        z.writestr(zipfile.ZipInfo(name), data, compress_type=method)
    z.writestr(zipfile.ZipInfo("empty-dir/"), b"")
with open(os.path.join(out, "manifest.txt"), "w", encoding="utf-8") as m:
    for name, data in files.items():
        m.write("%s=%s\n" % (name, hashlib.sha256(data).hexdigest()))
with zipfile.ZipFile(os.path.join(out, "slip.zip"), "w") as z:
    z.writestr("ok.txt", b"ok")
    z.writestr("../evil.txt", b"evil")
whole = open(os.path.join(out, "test.zip"), "rb").read()
open(os.path.join(out, "truncated.zip"), "wb").write(whole[: len(whole) // 2])
with zipfile.ZipFile(os.path.join(out, "corrupt.zip"), "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("a.txt", b"hello world " * 5000)
data = bytearray(open(os.path.join(out, "corrupt.zip"), "rb").read())
data[60] ^= 0xFF
data[61] ^= 0xFF
open(os.path.join(out, "corrupt.zip"), "wb").write(data)
print("zip files written to", out, os.path.getsize(os.path.join(out, "test.zip")))
