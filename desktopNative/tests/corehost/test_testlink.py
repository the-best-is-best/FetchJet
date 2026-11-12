import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, SERVER

ENGINE = {name: index for index, name in enumerate(ENUMS["EngineCode"])}
FTP = "ftp://fj:secret@127.0.0.1:2121/x/2MB.bin"
FTP_BAD = "ftp://fj:nope@127.0.0.1:2121/x/2MB.bin"
MB = 1024 * 1024


def probe(core, label, url, timeout=30):
    print(label)
    core.action("SetUrl", text=url)
    core.pump(0.3)
    add = core.snapshot("add")
    core.check(label + ": the button is shown and nothing is written yet", add.get("testLinkShown") and
               not add.get("testLinkBusy", False) and add.get("testLinkText", "") == "", add)
    core.action("TestLink")

    def done():
        state = core.debug("testLink")
        if isinstance(state, dict) and state.get("code"):
            return state
        return None

    result = core.wait_for(label + ": a result", done, timeout)
    core.pump(0.3)
    add = core.snapshot("add")
    core.check(label + ": the button is free again and a line is written", not add.get("testLinkBusy", False) and
               add.get("testLinkText", "") != "", add.get("testLinkText"))
    return result, add


def run():
    core = Core("testlink")
    try:
        core.pump(0.5)
        strings = core.strings()
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("no link: no test button", not add.get("testLinkShown", False), add)

        result, add = probe(core, "a file that can resume", SERVER + "/file/3MB.bin?key=tl-ok")
        core.check("ok, 3 MB, resumable", result.get("code") == "ok" and result.get("size") == 3 * MB and result.get("resumable") is True, result)
        text = add.get("testLinkText", "")
        core.check("the line says OK, the size and resumable", add.get("testLinkOk") and text.startswith(strings["testLinkOk"]) and
                   "3" in text and "MB" in text and text.endswith(strings["testLinkResumable"]), text)
        core.check("the same name as the suggestion is not repeated", strings["testLinkServerName"] not in text, text)
        core.check("no download was started", core.task(url_part="key=tl-ok") is None)

        print("the result goes away when the link changes")
        core.action("SetUrl", text=SERVER + "/file/3MB.bin?key=tl-ok2")
        core.pump(0.3)
        add = core.snapshot("add")
        core.check("line cleared", add.get("testLinkText", "") == "" and core.debug("testLink") == {}, add.get("testLinkText"))

        result, add = probe(core, "a server without ranges", SERVER + "/file/3MB.bin?key=tl-norange&norange=1")
        core.check("ok, not resumable", result.get("code") == "ok" and result.get("size") == 3 * MB and result.get("resumable") is False, result)
        core.check("the line says not resumable", add.get("testLinkText", "").endswith(strings["testLinkNotResumable"]), add.get("testLinkText"))

        result, add = probe(core, "a size the server does not say", SERVER + "/file/1MB.bin?key=tl-chunked&chunked=1")
        core.check("ok, size unknown", result.get("code") == "ok" and result.get("size") == -1, result)
        core.check("the line says size unknown", add.get("testLinkText", "").endswith(strings["testLinkSizeUnknown"]), add.get("testLinkText"))

        result, add = probe(core, "the server names the file", SERVER + "/file/1MB.bin?key=tl-name&name=real%20name.zip")
        core.check("the server's name is reported", result.get("code") == "ok" and result.get("fileName") == "real name.zip", result)
        core.check("the line shows the server's name", "real name.zip" in add.get("testLinkText", ""), add.get("testLinkText"))

        result, add = probe(core, "403", SERVER + "/file/3MB.bin?key=tl-403&status=403")
        core.check("refused, 403", result.get("code") == "refused" and result.get("status") == 403, result)
        core.check("the line gives the reason and the number", not add.get("testLinkOk", False) and
                   add.get("testLinkText") == strings["testLinkRefused"] + " (403)", add.get("testLinkText"))

        result, add = probe(core, "404", SERVER + "/file/3MB.bin?key=tl-404&status=404")
        core.check("not found, 404", result.get("code") == "not found" and result.get("status") == 404 and
                   add.get("testLinkText") == strings["testLinkNotFound"] + " (404)", (result, add.get("testLinkText")))

        result, add = probe(core, "a web page instead of a file", SERVER + "/file/4KB.bin?key=tl-html&type=text/html")
        core.check("web page", result.get("code") == "web page" and add.get("testLinkText") == strings["testLinkWebPage"],
                   (result, add.get("testLinkText")))

        result, add = probe(core, "a robot check page", SERVER + "/file/3MB.bin?key=tl-robot&robot=1")
        core.check("robot check", result.get("code") == "robot check" and add.get("testLinkText") == strings["testLinkRobot"],
                   (result, add.get("testLinkText")))

        result, add = probe(core, "nothing listens on the port", "http://127.0.0.1:18299/file/1MB.bin")
        core.check("no connection", result.get("code") == "no connection" and add.get("testLinkText") == strings["testLinkNoConnection"],
                   (result, add.get("testLinkText")))

        result, add = probe(core, "FTP with the login in the link", FTP)
        core.check("ok, 2 MB, resumable", result.get("code") == "ok" and result.get("size") == 2 * MB and result.get("resumable") is True, result)

        result, add = probe(core, "FTP with a wrong password", FTP_BAD)
        core.check("login refused", result.get("code") == "ftp login refused" and add.get("testLinkText") == strings["testLinkFtpLogin"],
                   (result, add.get("testLinkText")))

        print("busy state and the 20 s limit")
        core.action("SetUrl", text=SERVER + "/file/1MB.bin?key=tl-stall&delay=40000")
        core.pump(0.3)
        core.action("TestLink")
        core.pump(0.5)
        add = core.snapshot("add")
        core.check("busy while testing", add.get("testLinkBusy") and add.get("testLinkText", "") == "", add)
        state = core.wait_for("the time limit", lambda: (core.debug("testLink") or {}).get("code") and core.debug("testLink"), 30)
        core.pump(0.3)
        core.check("timeout after about 20 s", state.get("code") == "timeout" and
                   core.snapshot("add").get("testLinkText") == strings["testLinkTimeout"], state)

        print("a test still running is dropped when the dialog closes")
        core.action("TestLink")
        core.pump(0.5)
        core.check("testing again", core.snapshot("add").get("testLinkBusy", False))
        core.action("ShowAddDialog", show=False)
        core.pump(0.5)
        core.check("nothing left after closing", core.debug("testLink") == {})

        print("other engines and link lists have no test button")
        core.action("SetUrl", text=SERVER + "/file/1MB.bin?key=tl-engine")
        core.action("ShowAddDialog", show=True)
        core.action("SetEngine", engine=ENGINE["MEDIA_STREAM"])
        core.pump(0.5)
        core.check("media engine: hidden", not core.snapshot("add").get("testLinkShown", False))
        core.action("SetEngine", engine=ENGINE["HTTP_DIRECT"])
        core.pump(0.3)
        core.check("direct engine: shown", core.snapshot("add").get("testLinkShown", False))
        core.action("SetUrl", text=SERVER + "/file/1MB.bin?key=tl-l1\n" + SERVER + "/file/1MB.bin?key=tl-l2")
        core.pump(0.5)
        core.check("two links: hidden", not core.snapshot("add").get("testLinkShown", False))
        core.action("SetUrl", text="")
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        core.check("nothing was downloaded by the tests", not [t for t in core.tasks() if "key=tl-" in t.get("url", "")], core.tasks())
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
    core.finish()


if __name__ == "__main__":
    run()
