import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, install_fake_ytdlp
from shots import MEDIA, OUT, shot

LIST = "https://www.youtube.com/playlist?list=PLgrow"


def run(language="en"):
    core = Core("shots-playlist-group", before_start=install_fake_ytdlp)
    failed = 0
    suffix = "" if language == "en" else "-" + language
    try:
        core.action("SystemTheme", dark=True)
        if language != "en":
            core.action("SetLanguage", choice=language)
        core.pump(0.5)
        core.action("SetUrl", text=LIST)
        core.action("ShowAddDialog", show=True)
        core.wait_for("playlist", lambda: (core.snapshot("addMedia") or {}).get("state", 0) == MEDIA["PLAYLIST"], 60)
        core.action("SetPlaylistQuality", quality=4)
        core.action("StartDownload")
        core.wait_for("four tasks", lambda: len(core.tasks()) == 4, 20)
        core.wait_for("done", lambda: all(t.get("status") == "COMPLETED" for t in core.tasks()), 120)
        core.pump(1.0)
        if not shot(core, "playlist-group-row" + suffix, width=1100, height=600):
            failed += 1
        playlist = core.tasks()[0].get("playlistId")
        core.action("OpenPlaylist", id=playlist)
        core.pump(0.5)
        core.action("PlaylistReadLanguages")
        core.wait_for("languages", lambda: len(core.snapshot("chrome").get("playlistLanguageChoices", [])) >= 2, 60)
        core.pump(0.5)
        if not shot(core, "playlist-group-page" + suffix, width=1100, height=600):
            failed += 1
    except CheckFailed as failure:
        failed += 1
        print("  FAIL  " + str(failure))
    core.shutdown()
    print("shots in " + OUT)
    sys.stdout.flush()
    os._exit(1 if failed else 0)


if __name__ == "__main__":
    run(sys.argv[1] if len(sys.argv) > 1 else "en")
