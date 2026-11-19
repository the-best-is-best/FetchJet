import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, install_fake_ytdlp
from shots import OUT, shot
from test_folder_group import add, key


def run():
    core = Core("shots-folder-group", before_start=install_fake_ytdlp)
    failed = 0
    try:
        core.action("SystemTheme", dark=True)
        core.pump(0.5)
        root = core.downloads
        add(core, "report.pdf", root)
        add(core, "setup.exe", root)
        add(core, "lecture-1.mp4", os.path.join(root, "Work", "Video"))
        add(core, "trailer.mp4", os.path.join(root, "Home", "Video"))
        add(core, "song.mp3", os.path.join(root, "Music"), later=True)
        core.action("ShowAddDialog", show=False)
        core.action("SettingToggle", id="groupByFolder", on=True)
        core.pump(0.8)
        if not shot(core, "folder-group-rows", width=1100, height=600):
            failed += 1
        core.action("RowClick", id=key(root), control=False, shift=False)
        core.pump(0.8)
        if not shot(core, "folder-group-page", width=1100, height=600):
            failed += 1
        core.action("ClosePlaylist")
        core.action("SetLanguage", choice="ar")
        core.pump(0.8)
        if not shot(core, "folder-group-rows-ar", width=1100, height=600):
            failed += 1
    except CheckFailed as failure:
        failed += 1
        print("  FAIL  " + str(failure))
    core.shutdown()
    print("shots in " + OUT)
    sys.stdout.flush()
    os._exit(1 if failed else 0)


if __name__ == "__main__":
    run()
