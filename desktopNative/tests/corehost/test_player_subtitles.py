import glob
import json
import os
import shutil
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, REPO
from test_hidden import add_and_wait, open_hidden, pin_alert, type_pin, visible_names

PORT = int(os.environ.get("FJ_PLAYER_SUBS_PORT", "18269"))
BASE = "http://127.0.0.1:%d" % PORT
PIN = "2468"
FFMPEG = os.environ.get("FJ_TEST_FFMPEG", os.path.join(os.path.expanduser("~"), ".fetch_jet", "tools", "ffmpeg.exe"))
EXE = os.environ.get("FJ_PLAYER_EXE", os.path.join(REPO, "desktopNative", "build-release", "Release", "FetchJet.exe"))
LIBMPV = os.environ.get("FJ_PLAYER_LIBMPV", "E:/tools/mpv-subs/dist/libmpv-2.dll")
DESKRUN = os.path.join(REPO, "desktopNative", "tools", "shot", "build", "Release", "deskrun.exe")
OUT = os.environ.get("FJ_SHOT_OUT", "")
ARABIC = "مرحبا بالعالم ترجمة عربية"


def srt(text):
    return "1\n00:00:00,000 --> 00:00:40,000\n%s\n" % text


def make_media(folder):
    os.makedirs(folder, exist_ok=True)
    for name, text in [("emb_en.srt", "EMBEDDED ENGLISH LINE"), ("emb_fr.srt", "PISTE FRANCAISE")]:
        open(os.path.join(folder, name), "w", encoding="utf-8").write(srt(text))
    subprocess.run([FFMPEG, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "color=c=0x204060:s=640x360:d=40:r=25",
                    "-f", "lavfi", "-i", "sine=f=440:d=40", "-i", os.path.join(folder, "emb_en.srt"), "-i", os.path.join(folder, "emb_fr.srt"),
                    "-map", "0", "-map", "1", "-map", "2", "-map", "3", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-c:s", "mov_text", "-metadata:s:s:0", "language=eng", "-metadata:s:s:0", "title=English CC",
                    "-metadata:s:s:1", "language=fre", os.path.join(folder, "movie.mp4")], check=True, stdin=subprocess.DEVNULL, timeout=120)
    subprocess.run([FFMPEG, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "color=c=0x402020:s=640x360:d=5:r=25",
                    "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", os.path.join(folder, "plain.mp4")],
                   check=True, stdin=subprocess.DEVNULL, timeout=120)


def task_path(task):
    return os.path.join(task.get("destinationPath", ""), task.get("fileName", "")).replace("\\", "/")


def saved(core):
    files = glob.glob(os.path.join(core.home, "**", "player_subtitles.json"), recursive=True)
    if not files:
        return {}
    return json.load(open(files[0], encoding="utf-8"))


def subtitles(core, task_id, path, hidden=False):
    text = core.call("player.subtitles", {"id": task_id, "path": path, "hidden": "true" if hidden else "false"})
    return json.loads(text) if text else {}


def blocks(log):
    found = []
    current = None
    for line in log.splitlines():
        if not line.startswith("  "):
            current = None
            if line.split(" ", 1)[0] == "subs":
                current = {"command": line, "tracks": [], "menu": [], "state": {}}
                found.append(current)
            continue
        if current is None:
            continue
        body = line.strip()
        if body.startswith("subs "):
            for part in body[5:].split(" "):
                if "=" in part:
                    key, value = part.split("=", 1)
                    current["state"][key] = value
            current["state"]["notice"] = body.split("notice=", 1)[1] if "notice=" in body else ""
        elif body.startswith("sub "):
            entry = {}
            rest = body[4:]
            for key in ["key", "id", "external", "selected"]:
                value, rest = rest.split(" ", 1)
                entry[key] = value.split("=", 1)[1]
            label, file = rest.split(" file=", 1)
            entry["label"] = label.split("=", 1)[1]
            entry["file"] = file
            current["tracks"].append(entry)
        elif body.startswith("menu "):
            head, rest = body[5:].split(" header=", 1)
            header, rest = rest.split(" label=", 1)
            label, divider = rest.rsplit(" divider=", 1)
            current["menu"].append({"key": int(head.split("=", 1)[1]), "header": header, "label": label, "divider": divider == "1"})
    return found


def selected(block):
    return [t for t in block["tracks"] if t["selected"] == "1"]


def run_app(core, name, steps, sub_file, capture_after=None):
    script = os.path.join(core.work, name + "-script.txt")
    with open(script, "w", encoding="utf-8") as f:
        for step in steps:
            f.write(json.dumps(step, ensure_ascii=False) + "\n")
    log = os.path.join(core.work, name + "-player.log")
    if os.path.exists(log):
        os.remove(log)
    env = dict(os.environ, FJ_PLAYER_LOG=log, FJ_PLAYER_AO="null", FETCHJET_NO_USAGE_PING="1", FJ_PLAYER_SUB_FILE=sub_file)
    total = 500 * len(steps) + 6000
    after = capture_after if capture_after else total - 2000
    capture = os.path.join(OUT or core.work, name + ".bmp")
    command = [DESKRUN, "--timeout", str(total + 4000), "--after", str(after), "--capture", capture, "--",
               EXE, "--home", core.home, "--downloads", core.downloads, "--no-bridge", "--no-focus", "--no-auto-downloads", "--script", script]
    result = subprocess.run(command, env=env, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=total / 1000 + 60)
    print("  " + (result.stdout + result.stderr).strip().replace("\n", "\n  ")[-600:])
    text = open(log, encoding="utf-8", errors="replace").read() if os.path.exists(log) else ""
    return text


def wait(n):
    return [{"player": "state"}] * n


def core_part(core, ids, paths):
    movie, plain = ids["movie.mp4"], ids["plain.mp4"]
    print("side files next to the video, the app language first")
    answer = subtitles(core, movie, paths["movie.mp4"])
    labels = [f["label"] for f in answer.get("files", [])]
    core.check("both side files are listed, en first", labels == ["en", "ar"], answer)
    core.check("the first choice is the side file in the app language", answer.get("choice", "").lower().endswith("movie.en.srt"), answer.get("choice"))
    core.check("a video with no side files lists none", subtitles(core, plain, paths["plain.mp4"]).get("files") == [], subtitles(core, plain, paths["plain.mp4"]))

    print("a choice is remembered per download")
    core.check("remember off answers ok", core.call("player.subtitle", {"id": plain, "choice": "off"}) == "ok")
    core.check("off comes back", subtitles(core, plain, paths["plain.mp4"]).get("choice") == "off")
    core.call("player.subtitle", {"id": plain, "choice": "track:2"})
    core.check("a track inside the video comes back", subtitles(core, plain, paths["plain.mp4"]).get("choice") == "track:2")
    extra = paths["movie.mp4"].rsplit("/", 1)[0] + "/extra.srt"
    core.call("player.subtitle", {"id": plain, "choice": "file:" + extra})
    core.check("an opened file comes back", subtitles(core, plain, paths["plain.mp4"]).get("choice") == "file:" + extra)
    core.check("the other download is not changed", subtitles(core, movie, paths["movie.mp4"]).get("choice", "").lower().endswith("movie.en.srt"))
    core.check("an unknown choice is refused", core.call("player.subtitle", {"id": plain, "choice": "nonsense"}) == "")
    core.check("the store holds only the remembered download", list(saved(core).keys()) == [plain], saved(core))


def hidden_part(core, ids, paths):
    print("hidden: no side files and nothing remembered")
    secret = ids["secret.mp4"]
    before = dict(saved(core))
    core.check("the hidden flag refuses to remember", core.call("player.subtitle", {"id": secret, "choice": "off", "hidden": "true"}) == "")
    answer = subtitles(core, secret, paths["movie.mp4"], hidden=True)
    core.check("a hidden item lists no side files", answer.get("files") == [], answer)
    core.check("a hidden item has no saved choice", answer.get("choice") == "", answer)
    core.action("RowCheck", id=secret, checked=True)
    core.pump(0.3)
    core.action("HideChecked")
    core.pump(0.5)
    if pin_alert(core) is not None:
        type_pin(core, PIN)
        type_pin(core, PIN)
    core.pump(0.6)
    notice = next((a for a in core.rows("alerts") if a.get("title") and not a.get("inputSecret") and a.get("confirmLabel")), None)
    if notice is not None:
        core.action("AlertConfirm", kind=notice["kind"], checked=False)
    core.wait_for("the file leaves the list", lambda: "secret.mp4" not in visible_names(core), 30)
    open_hidden(core, PIN)
    core.pump(0.6)
    core.check("a hidden download is refused even without the flag", core.call("player.subtitle", {"id": secret, "choice": "track:1"}) == "")
    answer = subtitles(core, secret, paths["movie.mp4"])
    core.check("and lists no side files without the flag", answer.get("files") == [] and answer.get("choice") == "", answer)
    core.check("the store did not change", saved(core) == before, (before, saved(core)))
    core.check("the store never names the hidden download", secret not in json.dumps(saved(core)))
    core.action("HiddenLock")
    core.pump(0.6)


def host_part(core, ids, paths):
    missing = [p for p in [EXE, DESKRUN, LIBMPV] if not os.path.isfile(p)]
    if missing:
        print("SKIP the player window part, missing: " + ", ".join(missing))
        return
    tools = os.path.join(core.home, "tools")
    os.makedirs(tools, exist_ok=True)
    shutil.copy2(LIBMPV, os.path.join(tools, "libmpv-2.dll"))
    movie = ids["movie.mp4"]
    folder = paths["movie.mp4"].rsplit("/", 1)[0]
    extra = folder + "/extra.srt"
    broken = folder + "/broken.srt"

    print("player window: menu, pick off / track / file, open a file, reopen")
    steps = [{"type": "RowQuickAction", "id": movie}, *wait(8), {"player": "subs"},
             {"player": "subpick", "value": 1003}, *wait(3), {"player": "subs"},
             {"player": "subpick", "value": 0}, *wait(3), {"player": "subs"},
             {"player": "subpick", "value": 1000}, *wait(3), {"player": "subs"},
             {"player": "subopen"}, *wait(4), {"player": "subs"},
             {"player": "close"}, *wait(3),
             {"type": "RowQuickAction", "id": movie}, *wait(8), {"player": "subs"},
             {"player": "subpick", "value": 1003}, *wait(3), {"player": "submenu"}, *wait(3), {"player": "subs"}]
    log = run_app(core, "subs-run1", steps, extra)
    found = blocks(log)
    core.check("every subs step was logged", len(found) == 7, [b["command"] for b in found])
    if len(found) < 7:
        print(log[-3000:])
        return
    first, ar, off, track, opened, reopened, menu = found
    kinds = [(t["external"], t["label"]) for t in first["tracks"]]
    core.check("the tracks: two inside the video, two side files", kinds == [("0", "eng"), ("0", "fre"), ("1", "en"), ("1", "ar")], kinds)
    core.check("the side file in the app language is on at open", [t["label"] for t in selected(first)] == ["en"], selected(first))
    labels = [(m["key"], m["header"], m["label"]) for m in first["menu"]]
    core.check("menu: Off, the video's tracks, the files, Open file", labels == [
        (0, "Subtitles", "Off"), (1000, "In the video", "eng"), (1001, "", "fre"), (1002, "Files", "en"), (1003, "", "ar"),
        (1, "", "Open subtitle file…")], labels)
    core.check("the checked entry is the shown track", first["state"].get("checked") == "1002", first["state"])
    core.check("pick the Arabic file", [t["label"] for t in selected(ar)] == ["ar"] and ar["state"].get("checked") == "1003", ar["state"])
    core.check("pick Off", selected(off) == [] and off["state"].get("checked") == "0" and off["state"].get("on") == "0", off["state"])
    core.check("pick a track inside the video", [t["id"] for t in selected(track)] == ["1"] and track["state"].get("choice") == "track:1", track["state"])
    core.check("an opened file is added and shown", [t["label"] for t in selected(opened)] == ["extra.srt"], opened["tracks"])
    core.check("the opened file is the choice", opened["state"].get("choice", "").lower() == ("file:" + extra).lower(), opened["state"])
    core.check("after reopening, the opened file is loaded and shown again", [t["label"] for t in selected(reopened)] == ["extra.srt"], reopened["tracks"])
    order = [(m["key"], m["label"]) for m in reopened["menu"]]
    core.check("the menu keeps the side files first, then the opened file", order[3:6] == [(1002, "en"), (1003, "ar"), (1004, "extra.srt")], order)
    core.check("the Arabic pick before closing", [t["label"] for t in selected(menu)] == ["ar"], menu["tracks"])
    store = saved(core)
    core.check("the last pick is remembered on disk", store.get(movie, "").lower().endswith("movie.ar.srt"), store)

    print("a file that is not a subtitle shows a short message; the remembered Arabic file is on at open")
    steps = [{"type": "RowQuickAction", "id": movie}, *wait(8), {"player": "subs"},
             {"player": "subopen"}, {"player": "pause"}, *wait(2), {"player": "subs"}, *wait(4)]
    log = run_app(core, "subs-run2", steps, broken, capture_after=500 * (len(steps) - 2))
    found = blocks(log)
    core.check("both subs steps were logged", len(found) == 2, [b["command"] for b in found])
    if len(found) == 2:
        core.check("the remembered Arabic file is on at open", [t["label"] for t in selected(found[0])] == ["ar"], found[0]["tracks"])
        core.check("the broken file shows the failure message", found[1]["state"].get("notice", "").startswith("Could not read"), found[1]["state"])
        core.check("the broken file is not remembered", saved(core).get(movie, "").lower().endswith("movie.ar.srt"), saved(core))
        core.check("the shown track stays the Arabic file", [t["label"] for t in selected(found[1])] == ["ar"], found[1]["tracks"])


def run():
    core = Core("player_subtitles")
    media = os.path.join(core.work, "site")
    make_media(media)
    shutil.copy2(os.path.join(media, "movie.mp4"), os.path.join(media, "secret.mp4"))
    server = subprocess.Popen([sys.executable, "-m", "http.server", str(PORT), "--bind", "127.0.0.1", "--directory", media],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    try:
        core.pump(0.5)
        tasks = {name: add_and_wait(core, BASE + "/" + name, name) for name in ["movie.mp4", "plain.mp4", "secret.mp4"]}
        core.check("the media is downloaded", all(t and t["status"] == "COMPLETED" for t in tasks.values()), [t and t["status"] for t in tasks.values()])
        ids = {name: t["id"] for name, t in tasks.items()}
        paths = {name: task_path(t) for name, t in tasks.items()}
        folder = os.path.dirname(paths["movie.mp4"])
        open(os.path.join(folder, "movie.en.srt"), "w", encoding="utf-8").write(srt("SIDE FILE ENGLISH"))
        open(os.path.join(folder, "movie.ar.srt"), "w", encoding="utf-8").write(srt(ARABIC))
        open(os.path.join(folder, "extra.srt"), "w", encoding="utf-8").write(srt("OPENED BY HAND"))
        open(os.path.join(folder, "broken.srt"), "wb").write(b"\x00\x01\x02 not a subtitle \xff\xfe\x00")
        core_part(core, ids, paths)
        hidden_part(core, ids, paths)
        core.call("player.subtitle", {"id": ids["plain.mp4"], "choice": "off"})
    except CheckFailed as failure:
        print("FAILED: " + str(failure))
        print(core.log_text()[-2000:])
        server.terminate()
        core.finish()
        return
    finally:
        server.terminate()
    crash = core.crash_text()
    core.check("no entries in native-crash.log", crash == "", crash[:1500])
    core.shutdown()
    try:
        host_part(core, ids, paths)
    except CheckFailed as failure:
        print("FAILED: " + str(failure))
    print("%d checks, %d failed" % (core.checks, len(core.failures)))
    sys.stdout.flush()
    os._exit(1 if core.failures else 0)


if __name__ == "__main__":
    run()
