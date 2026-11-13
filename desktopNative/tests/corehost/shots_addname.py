import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, SERVER
from shots import shot


def run():
    core = Core("shots-addname")
    try:
        core.action("SystemTheme", dark=True)
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)
        core.action("SetUrl", text=SERVER + "/download")
        core.pump(0.6)
        shot(core, "addname-empty", width=1100, height=820)
        core.action("SetUrl", text=SERVER + "/file/1MB.bin?key=sn-an")
        core.pump(0.6)
        shot(core, "addname-1mb", width=1100, height=820)
    finally:
        core.finish()


if __name__ == "__main__":
    run()
