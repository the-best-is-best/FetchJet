import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER, install_fake_ytdlp


def add_and_wait(core, url, name):
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text=url)
    core.pump(0.6)
    core.action("StartDownload")
    return core.wait_task(["COMPLETED", "ERROR"], timeout=40, file_name=name)


def links_of(core, task):
    core.action("RowClick", id=task["id"])
    core.pump(0.6)
    return (core.snapshot("details") or {}).get("links", [])


def run():
    core = Core("detailslinks", before_start=install_fake_ytdlp)
    try:
        core.pump(1.0)
        strings = core.strings()
        plain_url = SERVER + "/file/1MB.bin"
        secret_url = SERVER + "/file/2MB.bin?token=abc123"
        plain = add_and_wait(core, plain_url, "1MB.bin")
        secret = add_and_wait(core, secret_url, "2MB.bin")

        print("a plain link")
        rows = links_of(core, plain)
        source = next((r for r in rows if r.get("label") == strings["detailsSource"]), None)
        core.check("the Source row shows the link and has no note", source is not None and source.get("url") == plain_url and not source.get("note"), rows)

        print("a link with a token")
        rows = links_of(core, secret)
        source = next((r for r in rows if r.get("label") == strings["detailsSource"]), None)
        core.check("the Source row hides the link and says why", source is not None and not source.get("url") and source.get("note") == strings["detailsLinkSensitive"], rows)
        core.check("the token appears nowhere in the details", "abc123" not in str(core.snapshot("details")), None)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
