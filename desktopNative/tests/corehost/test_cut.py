import glob
import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, REPO, ON_LINUX, install_libmpv, run_window, window_runner, PLAYER_EXE, PLAYER_LIBMPV
from test_compress import install_ffmpeg, serve_folder, select, FFMPEG_SOURCE

MEDIA = os.environ.get("FJ_CUT_MEDIA", "/mnt/e/tools/mpv-spike/media" if ON_LINUX else "E:/tools/mpv-spike/media")
EXE = PLAYER_EXE
LIBMPV = PLAYER_LIBMPV
OUT = os.environ.get("FJ_SHOT_OUT", "")
MP3_FRAME = 1152.0 / 44100.0


def make_song(folder):
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, "blocks.mp3")
    subprocess.run([FFMPEG_SOURCE, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "sine=f=440:d=30:sample_rate=44100",
                    "-af", "volume='if(lt(mod(t,6),3),1,0.03)':eval=frame", "-c:a", "libmp3lame", "-b:a", "128k", path],
                   check=True, stdin=subprocess.DEVNULL, timeout=120)
    return path


def make_m4a(folder):
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, "blocks.m4a")
    subprocess.run([FFMPEG_SOURCE, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "sine=f=440:d=30:sample_rate=44100",
                    "-af", "volume='if(lt(mod(t,6),3),1,0.03)':eval=frame", "-c:a", "aac", "-b:a", "128k", path],
                   check=True, stdin=subprocess.DEVNULL, timeout=120)
    return path


def audio_length(path):
    result = subprocess.run([FFMPEG_SOURCE, "-hide_banner", "-loglevel", "error", "-i", path, "-map", "0:a:0", "-ac", "1", "-ar", "44100",
                             "-f", "s16le", "-"], capture_output=True, timeout=120)
    return len(result.stdout) / 2.0 / 44100.0


def wave_blocks(peaks, bars_per_block):
    loud, quiet = [], []
    for i, peak in enumerate(peaks):
        block, offset = divmod(i, bars_per_block)
        if offset == 0 or offset == bars_per_block - 1:
            continue
        (loud if block % 2 == 0 else quiet).append(peak)
    return loud, quiet


def cut_page(core):
    return (core.snapshot("audioCompress") or {}).get("cut", {}) or {}


def details(core):
    return core.snapshot("details") or {}


def measure(path):
    result = subprocess.run([FFMPEG_SOURCE, "-hide_banner", "-i", path, "-map", "0:v:0", "-f", "null", "-"],
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
    text = result.stderr
    fps = re.search(r"Video:.*?, ([\d.]+) fps", text)
    frames = re.findall(r"frame=\s*(\d+)", text)
    return (float(fps.group(1)) if fps else 0.0), (int(frames[-1]) if frames else 0)


def menu_labels(core):
    return [row["label"] for row in core.rows("rowMenu")]


def run():
    core = Core("cut", before_start=install_ffmpeg)
    server = serve_folder(MEDIA)
    s = core.strings()
    try:
        port = server.server_address[1]
        core.debug("addDirect", {"url": "http://127.0.0.1:%d/one.mp4" % port, "fileName": "one.mp4"})
        video = core.wait_task("COMPLETED", timeout=60, file_name="one.mp4")
        core.pump(0.5)

        core.action("RowMenu", id=video["id"])
        core.pump(0.3)
        core.check("row menu offers Cut", s["videoCutTitle"] in menu_labels(core), str(menu_labels(core)))
        key = next(r["key"] for r in core.rows("rowMenu") if r["label"] == s["videoCutTitle"])
        core.action("RowMenuPick", key=key)
        core.wait_for("the chooser", lambda: cut_page(core).get("open") and cut_page(core).get("chooser"), 10)
        core.check("chooser shown", cut_page(core).get("chooser"))

        core.action("VideoCutChoose", timeline=False)
        core.wait_for("the time view", lambda: cut_page(core).get("open") and not cut_page(core).get("chooser"), 10)
        core.wait_for("the duration", lambda: cut_page(core).get("duration", "") != "", 20)

        core.action("VideoCutPickTime", end=False)
        core.pump(0.2)
        core.check("start picker open", (core.snapshot("clipPicker") or {}).get("open"))
        core.action("ClipPickerType", field=2, text="02")
        core.action("ClipPickerApply")
        core.pump(0.3)
        core.action("VideoCutPickTime", end=True)
        core.pump(0.2)
        core.action("ClipPickerType", field=0, text="00")
        core.action("ClipPickerType", field=1, text="00")
        core.action("ClipPickerType", field=2, text="05")
        core.action("ClipPickerApply")
        core.pump(0.3)
        page = cut_page(core)
        core.check("range 2 s to 5 s", "00:00:02" in page.get("start", "") and "00:00:05" in page.get("end", ""),
                   page.get("start", "") + " / " + page.get("end", ""))
        core.check("can start", page.get("canStart"))
        core.action("VideoCutStart")
        core.wait_for("the cut to finish", lambda: not cut_page(core).get("running") and len(cut_page(core).get("clips", [])) == 1, 120)
        page = cut_page(core)
        core.check("not failed", not page.get("failed"), page.get("message", ""))
        clip = page["clips"][0]
        core.check("clip file exists", os.path.isfile(clip["path"]), clip["path"])
        fps, frames = measure(clip["path"])
        length = frames / fps if fps else 0
        print("    clip: fps=%s frames=%d length=%.4f s" % (fps, frames, length))
        core.check("clip is 3 s within one frame", fps > 0 and abs(length - 3.0) <= 1.0 / fps + 1e-6,
                   "fps=%s frames=%d length=%.4f" % (fps, frames, length))
        clip_task = core.task(file_name=os.path.basename(clip["path"]))
        core.check("a new finished task", clip_task is not None and clip_task["status"] == "COMPLETED")

        core.action("VideoCutApply", startMs=1000, endMs=2500)
        core.wait_for("the second cut", lambda: not cut_page(core).get("running") and len(cut_page(core).get("clips", [])) == 2, 120)
        second = [c for c in cut_page(core)["clips"] if c["path"] != clip["path"]][0]
        fps, frames = measure(second["path"])
        core.check("VideoCutApply clip is 1.5 s within one frame", fps > 0 and abs(frames / fps - 1.5) <= 1.0 / fps + 1e-6,
                   "frames=%d" % frames)

        core.action("VideoCutDismiss")
        core.wait_for("the dialog to close", lambda: not cut_page(core).get("open"), 10)
        select(core, video["id"])
        core.wait_for("clips in details", lambda: len(details(core).get("clips", [])) == 2, 10)
        tools = [t["label"] for t in details(core).get("mediaTools", [])]
        core.check("details offers Cut", s["videoCutTitle"] in tools, str(tools))

        core.action("VideoCutDeleteClip", path=second["path"])
        core.wait_for("one clip left", lambda: len(details(core).get("clips", [])) == 1, 10)
        core.pump(2.0)
        core.check("deleted file is gone", not os.path.isfile(second["path"]), second["path"])

        select(core, clip_task["id"])
        core.wait_for("clip details", lambda: details(core).get("id") == clip_task["id"], 10)
        tools = [t["label"] for t in details(core).get("mediaTools", [])]
        core.check("a clip has no Cut in details", s["videoCutTitle"] not in tools, str(tools))
        core.check("a clip shows no clips", len(details(core).get("clips", [])) == 0)
        core.action("RowMenu", id=clip_task["id"])
        core.pump(0.3)
        core.check("a clip has no Cut in the row menu", s["videoCutTitle"] not in menu_labels(core), str(menu_labels(core)))
        core.action("RowMenuClose")
    except CheckFailed as failure:
        print("FAILED: " + str(failure))
        print(cut_page(core))
        print(core.log_text()[-3000:])
    server.shutdown()
    audio = None
    try:
        audio = audio_part(core, s)
    except CheckFailed as failure:
        print("FAILED: " + str(failure))
        print(cut_page(core))
        print(core.log_text()[-3000:])
    crash = core.crash_text()
    core.check("no entries in native-crash.log", crash == "", crash[:1500])
    core.shutdown()
    if audio:
        try:
            host_part(core, audio)
        except CheckFailed as failure:
            print("FAILED: " + str(failure))
    print("%d checks, %d failed" % (core.checks, len(core.failures)))
    sys.stdout.flush()
    os._exit(1 if core.failures else 0)


def audio_part(core, s):
    print("audio: timeline offered, sound wave, cut by time")
    site = os.path.join(core.work, "site")
    make_song(site)
    server = serve_folder(site)
    try:
        port = server.server_address[1]
        core.debug("addDirect", {"url": "http://127.0.0.1:%d/blocks.mp3" % port, "fileName": "blocks.mp3"})
        song = core.wait_task("COMPLETED", timeout=60, file_name="blocks.mp3")
    finally:
        server.shutdown()
    core.pump(0.5)
    path = os.path.join(song.get("destinationPath", ""), song["fileName"]).replace("\\", "/")
    core.action("RowMenu", id=song["id"])
    core.pump(0.3)
    core.check("row menu offers Cut audio", s["audioCutTitle"] in menu_labels(core), str(menu_labels(core)))
    key = next(r["key"] for r in core.rows("rowMenu") if r["label"] == s["audioCutTitle"])
    core.action("RowMenuPick", key=key)
    core.wait_for("the audio chooser", lambda: cut_page(core).get("open") and cut_page(core).get("chooser"), 10)
    page = cut_page(core)
    core.check("the page knows it is audio", page.get("audio"), page)
    core.check("the timeline is offered for an mp3", not page.get("timelineOff"), page)

    peaks = json.loads(core.call("player.waveform", {"path": path, "bars": 60, "durationMs": 30000}) or "[]")
    core.check("waveform gives one peak per bar", len(peaks) == 60, len(peaks))
    loud, quiet = wave_blocks(peaks, 6)
    core.check("loud blocks are tall and quiet blocks are low", loud and quiet and min(loud) > 0.8 and max(quiet) < 0.2,
               "loud min %.3f quiet max %.3f" % (min(loud or [0]), max(quiet or [1])))
    core.check("waveform refuses no bars", core.call("player.waveform", {"path": path, "bars": 0}) == "")
    m4a = make_m4a(os.path.join(core.work, "m4a")).replace("\\", "/")
    peaks = json.loads(core.call("player.waveform", {"path": m4a, "bars": 60, "durationMs": 30000}) or "[]")
    loud, quiet = wave_blocks(peaks, 6)
    core.check("m4a waveform: 60 peaks, loud blocks 10x the quiet ones", len(peaks) == 60 and loud and quiet and min(loud) > 10 * max(quiet),
               "n %d loud min %.3f quiet max %.3f" % (len(peaks), min(loud or [0]), max(quiet or [1])))

    core.action("VideoCutChoose", timeline=False)
    core.wait_for("the audio time view", lambda: cut_page(core).get("open") and not cut_page(core).get("chooser"), 10)
    core.wait_for("the audio duration", lambda: cut_page(core).get("duration", "") != "", 20)
    core.action("VideoCutApply", startMs=2340, endMs=7890)
    core.wait_for("the audio cut", lambda: not cut_page(core).get("running") and len(cut_page(core).get("clips", [])) == 1, 120)
    page = cut_page(core)
    core.check("audio cut not failed", not page.get("failed"), page.get("message", ""))
    clip = page["clips"][0]
    length = audio_length(clip["path"])
    print("    audio clip: %.4f s" % length)
    core.check("audio clip is 5.55 s within one mp3 frame", abs(length - 5.55) <= MP3_FRAME + 1e-3, "%.4f" % length)
    core.check("the audio clip is a finished task", (core.task(file_name=os.path.basename(clip["path"])) or {}).get("status") == "COMPLETED")
    core.action("VideoCutDismiss")
    core.wait_for("the audio dialog to close", lambda: not cut_page(core).get("open"), 10)
    video = core.task(file_name="one.mp4")
    return {"song": song, "path": path, "key": key, "clip": clip["path"], "video": video}


def wait(n):
    return [{"player": "state"}] * n


def trim_lines(log):
    found = []
    command = ""
    for line in log.splitlines():
        if not line.startswith("  "):
            command = line.split(" ", 1)[0]
            continue
        body = line.strip()
        if not body.startswith("trim "):
            continue
        state = {"command": command}
        for part in body[5:].split(" "):
            if "=" in part:
                k, v = part.split("=", 1)
                state[k] = v
        found.append(state)
    return found


def run_app(core, name, steps, capture_after=None):
    script = os.path.join(core.work, name + "-script.txt")
    with open(script, "w", encoding="utf-8") as f:
        for step in steps:
            f.write(json.dumps(step, ensure_ascii=False) + "\n")
    log = os.path.join(core.work, name + "-player.log")
    if os.path.exists(log):
        os.remove(log)
    env = dict(os.environ, FJ_PLAYER_LOG=log, FJ_PLAYER_AO="null", FETCHJET_NO_USAGE_PING="1")
    total = 500 * len(steps) + 6000
    after = capture_after if capture_after else total - 2000
    capture = os.path.join(OUT or core.work, name + ".bmp")
    output = run_window([EXE, "--home", core.home, "--downloads", core.downloads, "--no-bridge", "--no-focus", "--no-auto-downloads", "--script", script], env, total, after, capture)
    print("  " + output.strip().replace("\n", "\n  ")[-600:])
    print("  screenshot: " + capture)
    return open(log, encoding="utf-8", errors="replace").read() if os.path.exists(log) else ""


def host_part(core, audio):
    missing = [p for p in [EXE, *window_runner(), LIBMPV] if not os.path.isfile(p)]
    if missing:
        print("SKIP the player window part, missing: " + ", ".join(missing))
        return
    install_libmpv(core.home, LIBMPV)
    song = audio["song"]

    print("player window: the audio timeline")
    before = set(glob.glob(os.path.join(core.downloads, "**", "*.mp3"), recursive=True))
    steps = [{"type": "RowMenu", "id": song["id"]}, {"type": "RowMenuPick", "key": audio["key"]}, *wait(2),
             {"type": "VideoCutChoose", "timeline": True}, *wait(8),
             {"player": "trimselect", "value": 0}, {"player": "trimnudge", "value": 1},
             {"player": "trimstart", "value": 0.1}, {"player": "trimnudge", "value": 1},
             {"player": "trimselect", "value": 1}, {"player": "trimend", "value": 0.5}, {"player": "trimnudge", "value": -1},
             *wait(3), {"player": "trimapply"}, *wait(12)]
    log = run_app(core, "cut-audio", steps)
    found = trim_lines(log)
    core.check("trim lines were logged", len(found) >= 6, len(found))
    if len(found) < 6:
        print(log[-3000:])
        return
    first = found[0]
    print("    " + str(first))
    core.check("the timeline opens in audio mode", first.get("audio") == "1", first)
    core.check("audio steps are 10 ms (100 per second)", first.get("fps") == "100.000", first)
    core.check("the sound wave is loaded", int(first.get("wave", "0")) >= 40, first)
    core.check("no filmstrip for audio", first.get("thumbs") == "0/0", first)
    by = {}
    for state in found:
        by.setdefault(state["command"], []).append(state)
    nudges = by.get("trimnudge", [])
    core.check("three nudges logged", len(nudges) == 3, [n.get("start") + "/" + n.get("end") for n in nudges])
    if len(nudges) == 3:
        core.check("nudge from the start moves 10 ms", nudges[0]["s"] == "1" and nudges[0]["start"] == "0:00:00.010", nudges[0])
        dragged = by.get("trimstart", [{}])[-1], by.get("trimend", [{}])[-1]
        core.check("start after the drag plus one nudge = 10 ms later", int(nudges[1]["s"]) == int(dragged[0].get("s", -9)) + 1, (dragged[0], nudges[1]))
        core.check("end after the drag minus one nudge = 10 ms earlier", int(nudges[2]["e"]) == int(dragged[1].get("e", -9)) - 1, (dragged[1], nudges[2]))
    after = set(glob.glob(os.path.join(core.downloads, "**", "*.mp3"), recursive=True))
    made = [p for p in after - before if os.path.abspath(p) != os.path.abspath(audio["clip"])]
    core.check("the timeline cut made one new mp3", len(made) == 1, made)
    if made:
        length = audio_length(made[0])
        print("    timeline clip: %.4f s" % length)
        wanted = (int(nudges[2]["e"]) - int(nudges[2]["s"])) / 100.0 if len(nudges) == 3 else 0
        core.check("timeline clip is %.2f s within one mp3 frame" % wanted, abs(length - wanted) <= MP3_FRAME + 1e-3, "%.4f" % length)
        cache = glob.glob(os.path.join(core.home, "**", "tasks_cache.json"), recursive=True)
        listed = cache and os.path.basename(made[0]) in open(cache[0], encoding="utf-8").read()
        core.check("the clip is listed as a download", listed, cache)

    print("player window: screenshot of the audio timeline")
    steps = [{"type": "RowMenu", "id": song["id"]}, {"type": "RowMenuPick", "key": audio["key"]}, *wait(2),
             {"type": "VideoCutChoose", "timeline": True}, *wait(6),
             {"player": "trimstart", "value": 0.1}, {"player": "trimend", "value": 0.5}, *wait(2)]
    run_app(core, "cut-audio-shot", steps, capture_after=500 * len(steps) + 2500)

    video = audio.get("video")
    if not video:
        return
    print("player window: the video timeline is unchanged")
    steps = [{"type": "RowMenu", "id": video["id"]}, {"type": "RowMenuPick", "key": audio["key"]}, *wait(2),
             {"type": "VideoCutChoose", "timeline": True}, *wait(10),
             {"player": "trimselect", "value": 0}, {"player": "trimnudge", "value": 1}, *wait(2), {"player": "trimcancel"}, *wait(2)]
    log = run_app(core, "cut-video", steps)
    found = trim_lines(log)
    core.check("video trim lines were logged", len(found) >= 2, len(found))
    if len(found) >= 2:
        nudge = next((f for f in found if f["command"] == "trimnudge"), found[-1])
        print("    " + str(nudge))
        core.check("the video timeline is not audio", nudge.get("audio") == "0" and nudge.get("wave") == "0", nudge)
        core.check("the video fps comes from the file", nudge.get("fps") not in ("100.000", "30.000?"), nudge)
        loaded, total = nudge.get("thumbs", "0/0").split("/")
        core.check("the filmstrip is there", int(total) >= 4 and int(loaded) >= 1, nudge)
        core.check("a video nudge is one frame", nudge.get("s") == "1", nudge)


if __name__ == "__main__":
    run()
