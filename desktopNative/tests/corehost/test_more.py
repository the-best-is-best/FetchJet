import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
VERSION_FILE = os.path.join(REPO, "sharedLogic", "src", "commonMain", "kotlin", "com", "michelle", "raouf", "fetch_jet", "core", "update", "AppVersion.kt")

SITE = "https://the-best-is-best.github.io/FetchJet/"
COMMUNITY = [
    ("facebook", "https://www.facebook.com/share/1JDiWEtQyR/"),
    ("issues", "https://github.com/the-best-is-best/FetchJet/issues"),
    ("source", "https://github.com/the-best-is-best/FetchJet"),
    ("privacy", "https://the-best-is-best.github.io/FetchJet/privacy.html"),
    ("stats", "https://fetchjet.the-best-is-best.workers.dev/dashboard"),
]


def app_version():
    text = open(VERSION_FILE, encoding="utf-8-sig").read()
    return re.search(r'APP_VERSION = "([^"]+)"', text).group(1)


def more_rows(core):
    return core.rows("moreRows")


def by_id(core, key):
    for item in more_rows(core):
        if item.get("id") == key:
            return item
    return {}


def opened(core):
    return core.debug("opened")


def run():
    core = Core("more")
    try:
        core.pump(1.0)
        strings = core.strings()
        core.check("closed: nothing is built", len(more_rows(core)) == 0)

        print("landing page")
        core.action("OpenSettings")
        core.pump(0.6)
        chrome = core.snapshot("chrome")
        nav = core.snapshot("settingsNav") or {}
        core.check("the destination is called More", chrome.get("settingsOpen") and chrome.get("title") == strings["moreTitle"] != strings["homeSettings"], chrome.get("title"))
        core.check("it lands on More, not on the settings list", not nav.get("inSettings", False), nav)
        rows = more_rows(core)
        core.check("there are rows", len(rows) > 6, len(rows))

        print("header")
        header = rows[0]
        core.check("FetchJet and the version", header.get("title") == "FetchJet" and app_version() in header.get("subtitle", "") and header.get("first"), header)
        update_button = [b.get("id") for r in rows for b in r.get("buttons", [])]
        core.check("the update row reuses the Updates buttons", update_button in (["updateCheck"], ["updateSee"], []), update_button)
        if update_button:
            core.check("it sits in the header group (same card)", not rows[1].get("first", False) and rows[1].get("buttons"), rows[1])

        print("settings row")
        settings = by_id(core, "more.settings")
        core.check("a Settings row with its hint", settings.get("title") == strings["homeSettings"] and settings.get("subtitle") == strings["moreSettingsHint"] and
                   settings.get("clickable"), settings)

        print("every device")
        site = by_id(core, "more.site")
        core.check("one row, the old section title as its label", site.get("title") == strings["moreEverywhereTitle"] and site.get("clickable") and
                   site.get("first") and site.get("side") == "↗" and not site.get("status"), site)
        core.check("no platform rows", not any(r.get("id", "").startswith("more.open.") for r in rows))
        before = len(opened(core)["links"])
        core.action("SettingClick", id="more.site")
        core.pump(0.2)
        links = opened(core)["links"]
        core.check("it opens the site", len(links) == before + 1 and links[-1] == SITE, links[-2:])

        print("share")
        share = by_id(core, "more.share")
        core.check("a Share row with a share glyph, not the open arrow", share.get("title") == strings["moreShare"] and share.get("clickable") and share.get("side") not in ("", "↗"), share)
        core.action("SettingClick", id="more.share")
        core.pump(0.4)
        text = "FetchJet - download manager\n" + SITE
        core.check("the share text and link were handed over", opened(core)["shared"] == [text], opened(core)["shared"])
        core.check("the toast says Copied", core.snapshot("shell").get("toastText") == strings["copied"], core.snapshot("shell").get("toastText"))

        print("community")
        keys = [r.get("id") for r in rows if r.get("id", "").startswith("more.link.")]
        core.check("exactly the five community rows", keys == ["more.link." + k for k, _ in COMMUNITY], keys)
        titled = [by_id(core, k).get("title") for k in keys]
        core.check("their titles", titled == [strings["moreFacebook"], strings["moreIssues"], strings["moreSource"], strings["morePrivacy"], strings["moreStats"]], titled)
        core.check("the group has its title", by_id(core, keys[0]).get("caption") == strings["moreCommunityTitle"].upper())
        for key, url in COMMUNITY:
            before = len(opened(core)["links"])
            core.action("SettingClick", id="more.link." + key)
            core.pump(0.2)
            links = opened(core)["links"]
            core.check("%s opens %s" % (key, url), len(links) == before + 1 and links[-1] == url, links[-2:])
        every = [r.get("title", "").lower() for r in rows]
        core.check("no licenses row", not any("licen" in t for t in every), every)
        core.check("an unknown id opens nothing", True)
        before = len(opened(core)["links"])
        core.action("SettingClick", id="more.open.NOPE")
        core.action("SettingClick", id="more.link.nope")
        core.pump(0.2)
        core.check("unknown ids open nothing", len(opened(core)["links"]) == before)

        print("settings one step deeper")
        core.action("SettingClick", id="more.settings")
        core.pump(0.4)
        nav = core.snapshot("settingsNav") or {}
        core.check("the settings list is shown", nav.get("inSettings") and len(nav.get("sections", [])) >= 9 and not nav.get("pageOpen", False), nav.get("inSettings"))
        core.action("SetSettingsSection", index=1)
        core.pump(0.4)
        nav = core.snapshot("settingsNav") or {}
        core.check("a page opens as before", nav.get("inSettings") and nav.get("pageOpen") and nav.get("current") == 1, nav)
        core.action("SetSettingsSection", index=-1)
        core.pump(0.3)
        nav = core.snapshot("settingsNav") or {}
        core.check("back from the page to the list", nav.get("inSettings") and not nav.get("pageOpen", False))
        core.action("OpenMore")
        core.pump(0.3)
        nav = core.snapshot("settingsNav") or {}
        core.check("back from the list to More", not nav.get("inSettings", False))
        core.action("SetSettingsSection", index=2)
        core.pump(0.3)
        nav = core.snapshot("settingsNav") or {}
        core.check("opening a page directly leaves More", nav.get("inSettings") and nav.get("current") == 2)

        print("closing and opening again lands on More")
        core.action("SetFilter", filter=0)
        core.pump(0.4)
        core.check("closed: rows are gone", len(more_rows(core)) == 0)
        core.action("OpenSettings")
        core.pump(0.5)
        nav = core.snapshot("settingsNav") or {}
        core.check("More again", not nav.get("inSettings", False) and len(more_rows(core)) > 6)

        print("Arabic")
        core.action("SetSettingsSection", index=0)
        core.pump(0.3)
        core.action("SettingPick", id="language", index=2)
        core.pump(1.0)
        tr = core.snapshot("tr")
        arabic = core.strings()
        nav = core.snapshot("settingsNav") or {}
        core.check("the language changed and the page stayed in settings", tr.get("lang") == "ar" and tr.get("rtl") and nav.get("inSettings"), nav.get("inSettings"))
        core.action("OpenMore")
        core.pump(0.5)
        rows = more_rows(core)
        core.check("More is rebuilt in Arabic", by_id(core, "more.settings").get("title") == arabic["homeSettings"] != strings["homeSettings"] and
                   by_id(core, "more.share").get("title") == arabic["moreShare"] != strings["moreShare"], by_id(core, "more.share"))
        core.action("SetSettingsSection", index=0)
        core.pump(0.3)
        core.action("SettingPick", id="language", index=1)
        core.pump(1.0)
        core.check("English again", core.snapshot("tr").get("lang") == "en")
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
