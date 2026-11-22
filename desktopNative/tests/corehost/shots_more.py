import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core
from shots import shot


def run(only=None):
    core = Core("shots-more")
    failed = 0

    def take(name, **kwargs):
        nonlocal failed
        if only and not any(part in name for part in only):
            return
        core.pump(0.5)
        if not shot(core, name, **kwargs):
            failed += 1

    try:
        core.action("SystemTheme", dark=True)
        core.action("OpenSettings")
        core.pump(1.0)
        take("more-dark-520", width=520, height=1400)
        take("more-dark-1100", width=1100, height=1100)
        take("more-dark-1100-wide", width=1500, height=1100)
        core.action("ToggleTheme")
        core.pump(0.5)
        take("more-light-520", width=520, height=1400)
        take("more-light-1100", width=1100, height=1100)
        core.action("SettingClick", id="more.settings")
        core.pump(0.6)
        take("more-settings-list-light-520", width=520, height=800)
        take("more-settings-list-light-1100", width=1100, height=800)
        core.action("OpenMore")
        core.action("SetSettingsSection", index=0)
        core.action("SettingPick", id="language", index=2)
        core.pump(1.0)
        core.action("OpenMore")
        core.pump(0.6)
        take("more-light-520-ar", width=520, height=1400)
        take("more-light-1100-ar", width=1100, height=1100)
        core.action("ToggleTheme")
        core.pump(0.5)
        take("more-dark-1100-ar", width=1100, height=1100)
        core.action("SettingClick", id="more.settings")
        core.pump(0.6)
        take("more-settings-list-dark-520-ar", width=520, height=800)
        core.action("SetSettingsSection", index=0)
        core.action("SettingPick", id="language", index=1)
        core.pump(0.5)
    finally:
        core.finish()
    return failed


if __name__ == "__main__":
    sys.exit(1 if run(sys.argv[1:] or None) else 0)
