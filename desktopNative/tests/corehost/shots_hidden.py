import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core
from shots import shot
from test_hidden import BASE, PORT, NAMES, PIN, make_site, add_and_wait, type_pin, open_hidden


def run():
    core = Core("shots-hidden")
    site = os.path.join(core.work, "site")
    make_site(site)
    server = subprocess.Popen([sys.executable, "-m", "http.server", str(PORT), "--bind", "127.0.0.1", "--directory", site],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    try:
        core.pump(0.5)
        tasks = {name: add_and_wait(core, BASE + "/" + name, name) for name in NAMES}
        core.action("RowCheck", id=tasks["alpha.bin"]["id"], checked=True)
        core.action("RowCheck", id=tasks["bravo.bin"]["id"], checked=True)
        core.pump(0.3)
        core.action("HideChecked")
        core.pump(0.5)
        shot(core, "hidden-setpin-dark", width=1100, height=640)
        type_pin(core, "1234")
        type_pin(core, "1234")
        core.pump(0.5)
        for dark, theme in ((True, "dark"), (False, "light")):
            core.action("SystemTheme", dark=dark)
            open_hidden(core, "1234") if theme == "dark" else None
            core.pump(0.5)
            shot(core, "hidden-screen-" + theme, width=1100, height=640)
        core.action("RowCheck", id=tasks["alpha.bin"]["id"], checked=True)
        core.pump(0.4)
        shot(core, "hidden-selected-dark" if False else "hidden-selected-light", width=1100, height=640)
        core.action("SystemTheme", dark=True)
        core.action("HiddenLock")
        core.pump(0.4)
        open_hidden(core, "0000")
        core.pump(0.4)
        shot(core, "hidden-wrongpin-dark", width=1100, height=640)
        core.action("AlertDismiss", kind=PIN, fromScrim=False)
    finally:
        server.kill()
        core.finish()


if __name__ == "__main__":
    run()
