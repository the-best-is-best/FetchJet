import datetime
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed
from shots import shot

MB = 1024 * 1024
TODAY = datetime.date.today()


def at(day, hour=12):
    return int(datetime.datetime(day.year, day.month, day.day, hour, 0).timestamp() * 1000)


def first_of_month(day):
    return day.replace(day=1)


def last_month_day(day):
    return first_of_month(day) - datetime.timedelta(days=1)


TASKS = [
    ("https://www.youtube.com/watch?v=a", "Talk.mp4", 820 * MB, TODAY),
    ("https://www.youtube.com/watch?v=b", "Song.m4a", 9 * MB, TODAY),
    ("https://github.com/the-best-is-best/FetchJet/releases/download/v1.4.1/FetchJet.msi", "FetchJet.msi", 31 * MB, TODAY),
    ("https://www.udemy.com/course/kotlin/learn/lecture/1", "Lecture 1.mp4", 140 * MB, first_of_month(TODAY)),
    ("https://releases.ubuntu.com/24.04/ubuntu.iso", "ubuntu.iso", 5800 * MB, last_month_day(TODAY)),
    ("https://soundcloud.com/a/b", "Mix.mp3", 60 * MB, TODAY - datetime.timedelta(days=40)),
    ("https://example.org/old.zip", "old.zip", 12 * MB, TODAY.replace(year=TODAY.year - 1)),
]


def seed(core):
    entries = []
    for index, (url, name, size, day) in enumerate(TASKS):
        entries.append({
            "id": "report-%d" % index, "url": url, "fileName": name, "destinationPath": core.downloads,
            "status": "COMPLETED", "totalBytes": size, "downloadedBytes": size, "progress": 1.0,
            "createdAtEpochMs": at(day), "originalUrl": url,
        })
    for folder in (core.home, os.path.join(core.home, "settings")):
        os.makedirs(folder, exist_ok=True)
        with open(os.path.join(folder, "tasks_cache.json"), "w", encoding="utf-8") as f:
            json.dump(entries, f)
    with open(os.path.join(core.home, "behavior.properties"), "a", encoding="utf-8") as f:
        f.write("askedMainDownloader=true\n")


def rows(core):
    return core.rows("settingsRows")


def row(core, rid):
    return next((r for r in rows(core) if r.get("id") == rid), {})


def section_index(core, title):
    nav = core.snapshot("settingsNav") or {}
    for i, sec in enumerate(nav.get("sections", [])):
        if sec.get("title") == title:
            return i
    return -1


def tiles(core):
    return [t.get("value") for t in row(core, "reportStats").get("tiles", [])]


def run():
    core = Core("report", before_start=seed)
    strings = core.strings()
    try:
        core.pump(1.0)
        core.action("SystemTheme", dark=True)
        core.action("OpenSettings")
        core.pump(0.4)
        core.check("the Report page is hidden", section_index(core, strings.get("sectionReport", "Report")) < 0)
        core.debug("sections", {"all": "true"})
        core.pump(0.4)
        index = section_index(core, strings.get("sectionReport", "Report"))
        core.check("it is there when hidden pages are shown", index >= 0, (core.snapshot("settingsNav") or {}).get("sections"))
        core.action("SetSettingsSection", index=1)
        core.pump(0.4)
        core.check("the report left the Downloads page", not row(core, "reportStats") and not row(core, "reportMonth"))
        core.action("SetSettingsSection", index=index)
        core.pump(0.6)

        period = row(core, "reportPeriod")
        period2 = row(core, "reportPeriod2")
        core.check("seven periods on two lines, this month first", len(period.get("options", [])) + len(period2.get("options", [])) == 7 and period.get("pick") == 1 and period2.get("pick", 0) == 0, (period.get("options"), period2.get("options")))
        this_month = sum(1 for t in TASKS if t[3].year == TODAY.year and t[3].month == TODAY.month)
        core.check("this month counts its files", tiles(core)[0] == str(this_month), tiles(core))
        bars = row(core, "reportSites").get("bars", [])
        core.check("sites sorted by size, youtube first", bars and bars[0].get("label") == "youtube.com", [b.get("label") for b in bars])
        core.check("the first bar is full", bars and abs(bars[0].get("fraction", 0) - 1.0) < 0.001, bars[:1])
        shot(core, "report-this-month-dark", width=1180, height=980)

        core.action("SettingPick", id="reportPeriod2", index=1)
        core.pump(0.5)
        core.check("all time counts every file", tiles(core)[0] == str(len(TASKS)), tiles(core))
        core.check("all time: the iso is the top site", row(core, "reportSites").get("bars", [{}])[0].get("label") == "releases.ubuntu.com")

        core.action("SettingPick", id="reportPeriod", index=1)
        core.pump(0.5)
        lm = last_month_day(TODAY)
        wanted = sum(1 for t in TASKS if t[3].year == lm.year and t[3].month == lm.month)
        core.check("last month counts its files", tiles(core)[0] == str(wanted), (tiles(core), wanted))

        print("choose dates")
        core.action("SettingPick", id="reportPeriod2", index=2)
        core.pump(0.5)
        cal = row(core, "calendar")
        core.check("a calendar opens for the start date", cal.get("days") and len(cal.get("options", [])) == 7, cal.get("title"))
        start = TODAY - datetime.timedelta(days=45)
        while row(core, "calendar").get("title", "").split(" ")[-1] != str(start.year) or \
                ("cal.day.%04d-%02d-01" % (start.year, start.month)) not in [d.get("id") for d in row(core, "calendar").get("days", [])]:
            core.action("SettingClick", id="cal.prev")
            core.pump(0.2)
        core.action("SettingClick", id="cal.day." + start.isoformat())
        core.pump(0.4)
        cal = row(core, "calendar")
        core.check("then it asks for the end date", cal.get("subtitle") == strings.get("reportTo", "To"), cal.get("subtitle"))
        shot(core, "report-dates-dark", width=1180, height=1180)
        core.action("SettingClick", id="cal.day." + TODAY.isoformat())
        core.pump(0.4)
        core.check("the calendar closes after the end date", not row(core, "calendar"))
        wanted = sum(1 for t in TASKS if start <= t[3] <= TODAY)
        core.check("the chosen dates count their files", tiles(core)[0] == str(wanted), (tiles(core), wanted))

        core.action("SystemTheme", dark=False)
        core.pump(0.4)
        shot(core, "report-dates-light", width=1180, height=980)

        print("Arabic")
        core.action("SystemTheme", dark=True)
        core.action("SetSettingsSection", index=0)
        core.pump(0.4)
        language = row(core, "language")
        arabic = next((i for i, name in enumerate(language.get("options", [])) if name == "العربية"), -1)
        core.check("Arabic is offered", arabic >= 0, language.get("options"))
        core.action("SettingPick", id="language", index=arabic)
        core.pump(0.8)
        core.action("SetSettingsSection", index=section_index(core, core.strings().get("sectionReport")))
        core.pump(0.4)
        core.action("SettingPick", id="reportPeriod2", index=2)
        core.pump(0.4)
        core.check("the calendar uses Arabic day names", row(core, "calendar").get("options", [""])[0] == "ن", row(core, "calendar").get("options"))
        shot(core, "report-dates-ar", width=1180, height=1180)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    core.finish()


if __name__ == "__main__":
    run()
