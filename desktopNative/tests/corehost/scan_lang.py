import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed

WORD = re.compile(r"[A-Za-z]{4,}")
ALLOWED = {"FetchJet", "Edge", "Firefox", "Chrome", "Brave", "Safari", "Opera", "Vivaldi", "yt-dlp", "ffmpeg", "libtorrent", "BitTorrent",
           "Windows", "Android", "macOS", "Linux", "iPhone", "HTTP", "HTTPS", "SOCKS", "magnet", "torrent", "Mbps", "Kbps", "WARP",
           "deno", "node", "QuickJS", "YouTube", "Facebook", "Vimeo", "TikTok", "SoundCloud", "Python", "curl", "sha256", "MB/s", "KB/s", "PDF", "ZIP", "https", "http", "apk"}
SECTIONS = 8


def latin_words(text):
    out = []
    for word in WORD.findall(text or ""):
        if word in ALLOWED or word.lower() in {a.lower() for a in ALLOWED}:
            continue
        out.append(word)
    return out


def texts(row):
    for key, value in row.items():
        if isinstance(value, str) and key not in ("id", "kind"):
            yield key, value
        elif isinstance(value, list):
            for item in value:
                if isinstance(item, str):
                    yield key, item


def run():
    core = Core("scanlang")
    found = {}
    english = set()
    try:
        core.pump(0.5)
        core.action("OpenSettings")
        core.pump(0.5)
        core.action("SettingPick", id="language", index=1)
        core.pump(0.8)
        for section in range(SECTIONS):
            core.action("SetSettingsSection", index=section)
            core.pump(0.4)
            for nav in core.rows("settingsNav"):
                english.update(v for _, v in texts(nav))
            for row in core.rows("settingsRows"):
                english.update(v for _, v in texts(row))
        for lang_index in range(2, 16):
            core.action("SettingPick", id="language", index=lang_index)
            core.pump(0.8)
            code = (core.snapshot("tr") or {}).get("lang", str(lang_index))
            for section in range(SECTIONS):
                core.action("SetSettingsSection", index=section)
                core.pump(0.4)
                for nav in core.rows("settingsNav"):
                    for key, value in texts(nav):
                        if value in english and len(WORD.findall(value)) >= 1 and latin_words(value):
                            found.setdefault((code, "nav", value), set()).add("same-as-en")
                for row in core.rows("settingsRows"):
                    for key, value in texts(row):
                        if value in english and latin_words(value) and "\\" not in value:
                            found.setdefault((code, "sec%d:%s" % (section, row.get("id", "")), value), set()).add("same-as-en")
        core.action("SettingPick", id="language", index=1)
    except CheckFailed as failure:
        print("FAILED:", failure)
    for (code, where, value), words in sorted(found.items()):
        print("%s | %s | %s | %s" % (code, where, ",".join(sorted(words)), value[:90].replace("\n", " ")))
    print("TOTAL", len(found))
    core.finish()


if __name__ == "__main__":
    run()
