import os
import sys

os.environ["FETCHJET_TORRENT_DLL"] = os.environ.get("FETCHJET_TORRENT_DLL", "E:/tools/probes/fjtorrent/build/fjtorrent.dll")
os.environ["FETCHJET_TORRENT_LOCAL_ONLY"] = "1"
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, SERVER

ENGINE = {name: index for index, name in enumerate(ENUMS["EngineCode"])}
MAGNET = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"


def run():
    core = Core("addname")
    try:
        core.pump(0.5)
        strings = core.strings()
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)

        print("a link with no file name")
        core.action("SetUrl", text=SERVER + "/download")
        core.pump(0.5)
        add = core.snapshot("add")
        core.check("direct engine", add.get("engine", 0) == ENGINE["HTTP_DIRECT"], add.get("engine"))
        core.check("file name box shown and empty", add.get("fileNameShown") and add.get("fileName", "") == "", add.get("fileName"))
        core.check("start is off", not add.get("primaryEnabled", False), add.get("primaryLabel"))
        core.action("SetFileName", name="typed.bin")
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("typing a name turns start on", add.get("fileName") == "typed.bin" and add.get("primaryEnabled"), add)
        core.action("SetUrl", text=SERVER + "/file/1MB.bin?key=addname-a")
        core.pump(0.3)
        core.action("ResetFileName")
        core.pump(0.5)
        add = core.snapshot("add")
        core.check("a link with a name shows it and start is on", add.get("fileName") == "1MB.bin" and add.get("primaryEnabled"),
                   (add.get("fileName"), add.get("primaryEnabled")))

        print("engine switch while a magnet is inspected")
        core.action("SetUrl", text=MAGNET)
        core.pump(1.0)
        add = core.snapshot("add")
        core.check("torrent engine, inspecting", add.get("engine", 0) == ENGINE["BITTORRENT"] and add.get("fileName") == strings["findingName"], add.get("fileName"))
        for target in ("HTTP_DIRECT", "MEDIA_STREAM", "BITTORRENT"):
            core.action("SetEngine", engine=ENGINE[target])
            core.pump(1.0)
            add = core.snapshot("add")
            print("   ", target, "name", repr(add.get("fileName")), "error", add.get("error"))
            if target == "HTTP_DIRECT":
                core.check("direct: not stuck on finding the name", add.get("fileName", "") != strings["findingName"] and
                           True, add.get("fileName"))
            if target == "MEDIA_STREAM":
                core.check("media: no spinner left over from the torrent", add.get("engine", 0) == ENGINE["MEDIA_STREAM"], add)
            if target == "BITTORRENT":
                core.check("torrent again: a new inspect runs, no stale error", add.get("fileName") == strings["findingName"] and not add.get("error"), (add.get("fileName"), add.get("error")))

        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
    core.finish()


if __name__ == "__main__":
    run()
