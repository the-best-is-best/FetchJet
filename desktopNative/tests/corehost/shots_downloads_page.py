import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, SERVER, install_fake_ytdlp
from shots import shot


core_work = [""]


def run():
    core = Core("shots-downloads-page", before_start=install_fake_ytdlp, host_services={"pickFolder": lambda args: os.path.join(core_work[0], "Example")})
    core_work[0] = core.work
    try:
        for name in ("alpha.zip", "song.mp3", "movie.mkv"):
            core.debug("addDirect", {"url": SERVER + "/file/1MB.bin?key=dp-" + name, "fileName": name})
            core.wait_task("COMPLETED", timeout=40, file_name=name)
        core.action("SystemTheme", dark=True)
        core.action("OpenSettings")
        core.action("SetSettingsSection", index=3)
        core.action("SettingToggle", id="extractArchives", on=True)
        core.action("SettingText", id="ruleSite", text="github.com/the-best-is-best/FetchJet")
        core.action("SettingClick", id="ruleChoose")
        core.pump(0.5)
        core.action("SettingClick", id="ruleAdd")
        core.pump(0.5)
        core.action("SettingText", id="ruleSite", text="udemy.com/course/kotlin")
        core.pump(1.0)
        shot(core, "settings-files-dark-long", width=1100, height=3200)
    finally:
        core.finish()


if __name__ == "__main__":
    run()
