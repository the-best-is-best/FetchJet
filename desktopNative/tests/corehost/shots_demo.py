import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, SERVER, install_fake_ytdlp
from shots import shot

MB = 1024 * 1024
NOW = int(time.time() * 1000)

DONE = [
    ("Big Buck Bunny 1080p.mp4", "https://download.blender.org/peach/bigbuckbunny_movies/big_buck_bunny_1080p_h264.mov", 263, "MEDIA_STREAM"),
    ("Sintel 2010 4K.mkv", "magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10&dn=Sintel", 1240, "BITTORRENT"),
    ("Lofi Study Mix.mp3", "https://example.org/audio/lofi-study-mix.mp3", 92, "MEDIA_STREAM"),
    ("Project Assets.zip", "https://example.org/files/project-assets.zip", 48, "HTTP_DIRECT"),
    ("Annual Report 2026.pdf", "https://example.org/docs/annual-report-2026.pdf", 6, "HTTP_DIRECT"),
    ("LibreOffice 25.8.msi", "https://example.org/apps/libreoffice-25.8.msi", 352, "HTTP_DIRECT"),
]


def seed(core):
    install_fake_ytdlp(core)
    tasks = []
    for i, (name, url, size, engine) in enumerate(DONE):
        tasks.append({
            "id": "demo-%d" % i, "url": url, "fileName": name, "destinationPath": core.downloads, "engineType": engine,
            "status": "COMPLETED", "totalBytes": size * MB, "downloadedBytes": size * MB, "progress": 1.0,
            "createdAtEpochMs": NOW - (i + 1) * 3_600_000,
        })
    tasks.append({
        "id": "demo-paused", "url": "https://example.org/iso/debian-13-netinst.iso", "fileName": "Debian 13 netinst.iso",
        "destinationPath": core.downloads, "engineType": "HTTP_DIRECT", "status": "PAUSED", "totalBytes": 754 * MB,
        "downloadedBytes": 341 * MB, "progress": 341 / 754, "createdAtEpochMs": NOW - 600_000, "serverSupportsResume": "true",
    })
    for folder in (core.home, os.path.join(core.home, "settings")):
        os.makedirs(folder, exist_ok=True)
        with open(os.path.join(folder, "tasks_cache.json"), "w", encoding="utf-8") as f:
            json.dump(tasks, f)
    with open(os.path.join(core.home, "behavior.properties"), "a", encoding="utf-8") as f:
        f.write("askedMainDownloader=true\n")


def run():
    core = Core("shots-demo", before_start=seed)
    try:
        core.pump(1.0)
        core.action("SystemTheme", dark=True)
        for name, size, kb in (("Ubuntu-24.04-desktop-amd64.iso", "1500MB", 900), ("Tears-of-Steel-1080p.mp4", "700MB", 450)):
            core.debug("addDirect", {"url": SERVER + "/file/%s?size=%s&key=shot-%d&throttle=%d" % (name, size, kb, kb), "fileName": name})
        core.pump(16.0)
        shot(core, "demo-desktop", width=1920, height=1020)
    finally:
        core.finish()


if __name__ == "__main__":
    run()
