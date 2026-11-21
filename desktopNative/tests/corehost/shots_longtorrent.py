import os
import sys

TORRENT_DLL = os.environ.get("FETCHJET_TORRENT_DLL", "E:/tools/probes/fjtorrent/build/fjtorrent.dll")
os.environ["FETCHJET_TORRENT_DLL"] = TORRENT_DLL
os.environ["FETCHJET_TORRENT_LOCAL_ONLY"] = "1"

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed
from shots import shot, OUT
from test_torrent import Seeder, wait_files

NAME = "Some.Very.Long.Torrent.Name.2026.1080p.WEB-DL.DDP5.1.H.264.Complete.Season.Collection.With.Extras.And.Subtitles-GROUPNAME"
FILES = ",".join("episode%02d.mkv:%d" % (index, 70000 + index * 1000) for index in range(1, 13))


def run(dark):
    core = Core("shots-longtorrent")
    seeder = None
    failed = 0
    name = "dark" if dark else "light"
    try:
        core.action("SystemTheme", dark=dark)
        core.pump(0.6)
        seed_dir = core.work + "/seed"
        seeder = Seeder("dir=" + seed_dir, "out=" + seed_dir + "/long.torrent", "name=" + NAME, "files=" + FILES, "piece=16384")
        ready = seeder.ready()
        if not ready:
            raise CheckFailed("seeder did not start: " + " | ".join(seeder.lines[-3:]))
        core.action("SetUrl", text=os.path.normpath(ready["torrent"]))
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)
        wait_files(core, 30)
        core.pump(0.8)
        print("  name length " + str(len(NAME)))
        for width, height in ((520, 900), (1100, 820)):
            if not shot(core, "torrent-long-%s-%d" % (name, width), width=width, height=height):
                failed += 1
    except CheckFailed as failure:
        failed += 1
        print("  FAIL  " + str(failure))
    if seeder:
        seeder.stop()
    core.shutdown()
    print("shots in " + OUT)
    sys.stdout.flush()
    os._exit(1 if failed else 0)


if __name__ == "__main__":
    run("--light" not in sys.argv)
