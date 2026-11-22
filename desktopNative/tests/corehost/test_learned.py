import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, install_fake_ytdlp

STATE = {name: index for index, name in enumerate(ENUMS["MediaStateCode"])}
ENGINE = {name: index for index, name in enumerate(ENUMS["EngineCode"])}
FIRST = "https://videosite-example.test/watch/clip-one"
SECOND = "https://videosite-example.test/watch/clip-two"
OTHER = "https://other-example.test/watch/clip-one"


def media(core):
    return core.snapshot("addMedia") or {}


def run():
    core = Core("learned", before_start=install_fake_ytdlp)
    try:
        core.pump(1.5)
        print("an unknown site is a normal link at first")
        core.action("SetUrl", text=FIRST)
        core.action("ShowAddDialog", show=True)
        core.pump(0.5)
        core.check("direct engine for the unknown host", core.snapshot("add").get("engine", 0) == ENGINE["HTTP_DIRECT"], core.snapshot("add").get("engine"))

        print("the owner picks Media and it finds qualities")
        core.action("SetEngine", engine=ENGINE["MEDIA_STREAM"])
        core.wait_for("qualities", lambda: media(core).get("state", 0) == STATE["QUALITIES"], 60)
        core.check("qualities listed", len(core.rows("addQualities")) >= 2, core.rows("addQualities"))
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)

        print("the host is learned: the next link of it is Media")
        core.action("SetUrl", text=SECOND)
        core.action("ShowAddDialog", show=True)
        core.pump(0.5)
        core.check("media engine picked by itself", core.snapshot("add").get("engine", 0) == ENGINE["MEDIA_STREAM"], core.snapshot("add").get("engine"))
        core.action("ShowAddDialog", show=False)
        core.action("SetUrl", text=OTHER)
        core.action("ShowAddDialog", show=True)
        core.pump(0.5)
        core.check("another host stays direct", core.snapshot("add").get("engine", 0) == ENGINE["HTTP_DIRECT"], core.snapshot("add").get("engine"))
        core.action("ShowAddDialog", show=False)

        learned = os.path.join(core.home, "settings", "learned_hosts.txt")
        found = [p for p in (learned, os.path.join(core.home, "learned_hosts.txt")) if os.path.isfile(p)]
        core.check("the host is saved on disk", found and "videosite-example.test" in open(found[0]).read(), found)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
    core.finish()


if __name__ == "__main__":
    run()
