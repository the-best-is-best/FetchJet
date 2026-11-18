import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER


def norm(path):
    return os.path.normcase(os.path.normpath(path or ""))


def settings_text(core):
    return open(os.path.join(core.home, "behavior.properties"), encoding="utf-8").read()


PICKED = {"path": ""}


def add_rule(core, site, folder):
    core.action("SettingText", id="ruleSite", text=site)
    PICKED["path"] = folder
    core.action("SettingClick", id="ruleChoose")
    core.pump(0.4)
    core.action("SettingClick", id="ruleAdd")
    core.pump(0.4)


def dialog_destination(core, url):
    core.action("ShowAddDialog", show=True)
    core.action("SetUrl", text=url)
    core.pump(0.8)
    add = core.snapshot("add") or {}
    return add.get("destination", ""), add.get("folderRule", "")


def run():
    remembered = {}

    def seed(core):
        remembered["path"] = os.path.join(core.work, "my-archives")
        os.makedirs(remembered["path"], exist_ok=True)
        with open(os.path.join(core.home, "behavior.properties"), "a", encoding="utf-8") as f:
            f.write("askedMainDownloader=true\nsortIntoFolders=true\nfolder.Archives=%s\n" % remembered["path"].replace("\\", "/"))

    core = Core("siterules", before_start=seed, host_services={"pickFolder": lambda args: PICKED["path"]})
    local_rule = os.path.join(core.work, "from-local")
    test_rule = os.path.join(core.work, "from-example")
    try:
        core.pump(0.5)
        core.action("OpenSettings")
        core.action("SetSettingsSection", index=1)
        core.pump(0.5)
        print("rules are added from Settings")
        add_rule(core, "127.0.0.1", local_rule)
        add_rule(core, "https://www.Example.test/?ref=1", test_rule)
        add_rule(core, "not a site", test_rule)
        repo_rule = os.path.join(core.work, "from-repo")
        add_rule(core, "https://www.Example.test/Repo/Name/?tab=1", repo_rule)
        saved = settings_text(core)
        core.check("three rules saved, the site and link part cleaned", "siteFolders=" in saved and '"site":"example.test"' in saved and '"site":"example.test/repo/name"' in saved and saved.count('"site":') == 3, saved[-400:])
        rows = core.rows("settingsRows")
        titles = [r.get("title", "") for r in rows]
        core.check("the rules are listed in Settings", "127.0.0.1" in titles and "example.test" in titles and "example.test/repo/name" in titles, titles)
        chooser = next((r for r in rows if r.get("id") == "ruleChoose"), {})
        core.check("the folder is a chooser row, not a text field", chooser.get("clickable") and not any(r.get("one", {}).get("id") == "ruleFolder" for r in rows), chooser)
        core.check("the picker was asked", any(name == "pickFolder" for name, _ in core.host_calls), core.host_calls[-3:])

        print("a rule wins over the kind-of-file folder")
        core.action("ShowAddDialog", show=True)
        core.action("SetUrl", text=SERVER + "/file/pack-a.zip?size=1MB&key=sr-a")
        core.pump(0.8)
        add = core.snapshot("add") or {}
        core.check("the dialog shows the rule's folder", norm(add.get("destination")) == norm(local_rule), add.get("destination"))
        core.check("and says it came from a rule", "127.0.0.1" in add.get("folderRule", ""), add.get("folderRule"))
        core.action("StartDownload")
        task = core.wait_task("COMPLETED", timeout=40, file_name="pack-a.zip")
        core.check("saved in the rule's folder, no Archives folder added", norm(task["destinationPath"]) == norm(local_rule), task["destinationPath"])
        core.check("the file is there", os.path.isfile(os.path.join(local_rule, "pack-a.zip")))
        core.check("the remembered Archives folder was not changed", "folder.Archives=" + remembered["path"].replace("\\", "/") in settings_text(core))

        print("no rule: today's behaviour")
        destination, rule = dialog_destination(core, "http://other.invalid/z.zip")
        core.check("a zip from another site uses the remembered Archives folder", norm(destination) == norm(remembered["path"]), destination)
        core.check("no rule line", rule == "", rule)

        print("sub-domains, and a look-alike")
        core.action("ShowAddDialog", show=False)
        core.debug("addDirect", {"url": "http://dl.example.test/x.bin", "fileName": "x.bin"})
        core.debug("addDirect", {"url": "http://example.test.evil.invalid/y.bin", "fileName": "y.bin"})
        core.pump(1.5)
        x = core.task(file_name="x.bin") or {}
        y = core.task(file_name="y.bin") or {}
        core.check("a sub-domain follows the rule (no dialog: bridge / list path)", norm(x.get("destinationPath")) == norm(test_rule), x.get("destinationPath"))
        core.check("a look-alike host does not", norm(y.get("destinationPath")) != norm(test_rule), y.get("destinationPath"))
        core.debug("addDirect", {"url": "http://example.test/repo/name/releases/r.bin", "fileName": "r.bin"})
        core.debug("addDirect", {"url": "http://example.test/repo/other/s.bin", "fileName": "s.bin"})
        core.pump(1.5)
        r = core.task(file_name="r.bin") or {}
        s2 = core.task(file_name="s.bin") or {}
        core.check("a link under the link rule goes to its folder", norm(r.get("destinationPath")) == norm(repo_rule), r.get("destinationPath"))
        core.check("another path on the site uses the site rule", norm(s2.get("destinationPath")) == norm(test_rule), s2.get("destinationPath"))

        print("a folder picked by hand wins")
        mine = os.path.join(core.work, "picked")
        os.makedirs(mine, exist_ok=True)
        core.action("ShowAddDialog", show=True)
        core.action("SetUrl", text=SERVER + "/file/pack-b.zip?size=1MB&key=sr-b")
        core.pump(0.6)
        core.action("SetDestination", path=mine)
        core.pump(0.4)
        add = core.snapshot("add") or {}
        core.check("the rule line goes away", add.get("folderRule", "") == "", add.get("folderRule"))
        core.action("StartDownload")
        task = core.wait_task("COMPLETED", timeout=40, file_name="pack-b.zip")
        core.check("saved where it was picked", norm(task["destinationPath"]).startswith(norm(mine)), task["destinationPath"])

        print("a list of links")
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        same = "http://example.test/repo/name/a.bin\nhttp://example.test/repo/name/b.bin"
        destination, rule = dialog_destination(core, same)
        core.check("a list whose links all follow one rule uses its folder", norm(destination) == norm(repo_rule), destination)
        core.check("and says so", "example.test/repo/name" in rule, rule)
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        mixed = "http://example.test/repo/name/a.bin\nhttp://other.invalid/c.bin"
        destination, rule = dialog_destination(core, mixed)
        core.check("a mixed list does not", norm(destination) != norm(repo_rule) and rule == "", (destination, rule))
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)

        print("remove")
        core.action("SettingClick", id="rule.remove.example.test")
        core.pump(0.4)
        saved = settings_text(core)
        core.check("removed from the file", '"site":"example.test"' not in saved and '"site":"127.0.0.1"' in saved, saved[-200:])
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
