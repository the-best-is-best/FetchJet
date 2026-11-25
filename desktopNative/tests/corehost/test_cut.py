import glob
import json
import os
import re
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, REPO, ON_LINUX, install_libmpv, run_window, window_runner, PLAYER_EXE, PLAYER_LIBMPV
from test_compress import install_ffmpeg, serve_folder, select, FFMPEG_SOURCE

MEDIA = os.environ.get("FJ_CUT_MEDIA", "/mnt/e/tools/mpv-spike/media" if ON_LINUX else "E:/tools/mpv-spike/media")
EXE = PLAYER_EXE
LIBMPV = PLAYER_LIBMPV
OUT = os.environ.get("FJ_SHOT_OUT", "")
MP3_FRAME = 1152.0 / 44100.0
LONG_DIR = os.environ.get("FJ_CUT_LONG", os.path.join(tempfile.gettempdir(), "fj-cut-long"))
FONT = "C\\:/Windows/Fonts/arial.ttf" if os.name == "nt" else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


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
    longs = None
    if audio and os.environ.get("FJ_CUT_SKIP_LONG") != "1":
        try:
            longs = long_media(core)
        except CheckFailed as failure:
            print("FAILED: " + str(failure))
    crash = core.crash_text()
    core.check("no entries in native-crash.log", crash == "", crash[:1500])
    core.shutdown()
    if audio:
        try:
            host_part(core, audio)
        except CheckFailed as failure:
            print("FAILED: " + str(failure))
    if audio and longs:
        try:
            long_video_part(core, longs, audio["key"])
        except CheckFailed as failure:
            print("FAILED: " + str(failure))
        try:
            long_audio_part(core, longs, audio["key"])
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
    core.check("a 30 s song fills the strip (view = whole song)", near(first, "view", 0) and near(first, "span", 30, 0.2) and first.get("zoomout") == "0", first)
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
        core.check("a short video fills the strip (no zoom out, view from 0)", near(nudge, "view", 0) and nudge.get("zoomout") == "0"
                   and float(nudge.get("span", "0")) < 120, nudge)


def near(state, key, wanted, tolerance=0.01):
    try:
        return abs(float(state.get(key, "nan")) - wanted) <= tolerance
    except ValueError:
        return False


def num(state, key):
    try:
        return float(state.get(key, "nan"))
    except ValueError:
        return float("nan")


def make_long_video():
    os.makedirs(LONG_DIR, exist_ok=True)
    path = os.path.join(LONG_DIR, "long60.mp4")
    if os.path.isfile(path) and os.path.getsize(path) > 1000000:
        return path
    text = "drawtext=fontfile='%s':text='%%{pts\\:hms}':x=8:y=8:fontsize=30:fontcolor=white:box=1:boxcolor=black" % FONT
    subprocess.run([FFMPEG_SOURCE, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "testsrc2=s=320x180:r=10:d=3600",
                    "-f", "lavfi", "-i", "sine=f=330:d=3600:sample_rate=22050", "-vf", text, "-c:v", "libx264", "-preset", "ultrafast",
                    "-crf", "36", "-g", "50", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "32k", "-ac", "1", "-shortest", path + ".part.mp4"],
                   check=True, stdin=subprocess.DEVNULL, timeout=900)
    os.replace(path + ".part.mp4", path)
    return path


def make_long_song():
    os.makedirs(LONG_DIR, exist_ok=True)
    path = os.path.join(LONG_DIR, "long40.mp3")
    if os.path.isfile(path) and os.path.getsize(path) > 100000:
        return path
    subprocess.run([FFMPEG_SOURCE, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "sine=f=440:d=2400:sample_rate=22050",
                    "-af", "volume='if(lt(mod(t,60),30),1,0.05)':eval=frame", "-c:a", "libmp3lame", "-b:a", "48k", "-ac", "1", path + ".part.mp3"],
                   check=True, stdin=subprocess.DEVNULL, timeout=600)
    os.replace(path + ".part.mp3", path)
    return path


def long_media(core):
    print("long media: a 60 minute video and a 40 minute mp3")
    make_long_video()
    make_long_song()
    server = serve_folder(LONG_DIR)
    try:
        port = server.server_address[1]
        core.debug("addDirect", {"url": "http://127.0.0.1:%d/long60.mp4" % port, "fileName": "long60.mp4"})
        video = core.wait_task("COMPLETED", timeout=300, file_name="long60.mp4")
        core.debug("addDirect", {"url": "http://127.0.0.1:%d/long40.mp3" % port, "fileName": "long40.mp3"})
        song = core.wait_task("COMPLETED", timeout=300, file_name="long40.mp3")
    finally:
        server.shutdown()
    core.pump(3.0)
    return {"video": video, "song": song}


def by_command(found):
    out = {}
    for state in found:
        out.setdefault(state["command"], []).append(state)
    return out


def open_steps(task, key, settle):
    return [{"type": "RowMenu", "id": task["id"]}, {"type": "RowMenuPick", "key": key}, *wait(2),
            {"type": "VideoCutChoose", "timeline": True}, *wait(settle)]


def new_files(core, pattern, before):
    after = set(glob.glob(os.path.join(core.downloads, "**", pattern), recursive=True))
    return sorted(after - before)


def long_video_part(core, longs, key):
    missing = [p for p in [EXE, *window_runner(), LIBMPV] if not os.path.isfile(p)]
    if missing:
        print("SKIP the long video part, missing: " + ", ".join(missing))
        return
    install_libmpv(core.home, LIBMPV)
    video = longs["video"]
    duration = 3600.0
    print("player window: the timeline of a 60 minute video")
    before = set(glob.glob(os.path.join(core.downloads, "**", "*.mp4"), recursive=True))
    steps = [*open_steps(video, key, 12), {"player": "opened"},
             {"player": "trimpan", "value": 0.5}, {"player": "trimzoom", "value": 0.001},
             {"player": "trimzoom", "value": 1000}, {"player": "trimzoom", "value": 120.0 / duration},
             {"player": "trimselect", "value": 0}, {"player": "trimoverview", "value": 0.75}, *wait(1), {"player": "atfortyfive"},
             {"player": "trimdragview", "value": 0.5}, {"player": "trimdragend"},
             {"player": "trimselect", "value": 1}, {"player": "trimoverview", "value": 0.75},
             {"player": "trimdragview", "value": 1.03}, *wait(2), {"player": "edge"}, {"player": "trimdragend"}, {"player": "edgeend"},
             {"player": "trimoverview", "value": 0.75}, {"player": "trimdragview", "value": (2710.0 - 2640.0) / 120.0}, {"player": "trimdragend"},
             {"player": "trimpan", "value": 5}, {"player": "trimnudge", "value": 1}, {"player": "trimnudge", "value": -1},
             {"player": "trimzoom", "value": 0.001}, {"player": "trimplay"}, *wait(12), {"player": "playing"}, *wait(12),
             {"player": "trimapply"}, *wait(14)]
    log = run_app(core, "cut-long-video", steps)
    found = trim_lines(log)
    by = by_command(found)
    core.check("long video trim lines were logged", len(found) >= 20, len(found))
    if len(found) < 20:
        print(log[-3000:])
        return
    first = by.get("opened", [{}])[0]
    print("    opened: " + str(first))
    core.check("the default view shows about 120 s", near(first, "span", 120) and near(first, "view", 0), first)
    core.check("the strip is much wider than the view (can zoom out)", first.get("zoomout") == "1" and first.get("zoomin") == "1", first)
    core.check("about one thumbnail per 64 px", 0 < num(first, "tile") <= 16 and num(first, "strip") / (120 / num(first, "tile")) < 140, first)
    loaded, total = (first.get("thumbs", "0/0").split("/") + ["0"])[:2]
    core.check("thumbnails for the view and one view of margin", int(total) >= 10 and int(total) <= 80 and int(loaded) >= 4, first)
    pan = by.get("trimpan", [{}])[0]
    core.check("pan by half a view moves 60 s", near(pan, "view", 60), pan)
    zooms = by.get("trimzoom", [])
    core.check("zoom in stops at 5 s per view", len(zooms) >= 3 and near(zooms[0], "span", 5) and zooms[0].get("zoomin") == "0", zooms[:1])
    core.check("zoom out stops at the whole clip", len(zooms) >= 3 and near(zooms[1], "span", duration, 0.5) and near(zooms[1], "view", 0)
               and zooms[1].get("zoomout") == "0", zooms[1:2])
    core.check("zoom back to 120 s", len(zooms) >= 3 and near(zooms[2], "span", 120, 0.05), zooms[2:3])
    at45 = by.get("atfortyfive", [{}])[0]
    core.check("the overview click shows minute 45 in the middle", near(at45, "view", 2640, 0.05), at45)
    core.check("thumbnails follow the view", int(at45.get("thumbs", "0/0").split("/")[1]) >= 10 and int(at45.get("made", "0")) > int(first.get("made", "0")), at45)
    start = by.get("trimdragview", [{}])[0]
    core.check("the start handle dragged on the content lands on 45:00", start.get("s") == "27000" and near(start, "view", 2640, 0.05), start)
    selected = by.get("trimselect", [{}, {}])[-1]
    core.check("selecting the end handle scrolls it into view", num(selected, "view") + num(selected, "span") >= duration - 0.01, selected)
    edge = by.get("edge", [{}])[0]
    core.check("dragging past the edge keeps scrolling", edge.get("edge") == "1" and num(edge, "view") > 2650, edge)
    core.check("and the handle moves with it", int(edge.get("e", "0")) > 27600 and abs(int(edge.get("e", "0")) / 10.0 - (num(edge, "view") + num(edge, "span"))) < 1.0, edge)
    edge_end = by.get("edgeend", [{}])[0]
    core.check("the scroll stops on release", edge_end.get("edge") == "0", edge_end)
    end = by.get("trimdragview", [{}])[-1]
    core.check("the end handle lands on 45:10", end.get("e") == "27100", end)
    nudges = by.get("trimnudge", [])
    core.check("a nudge scrolls the handle into view", len(nudges) == 2 and num(nudges[0], "view") <= 2710.1 <= num(nudges[0], "view") + num(nudges[0], "span")
               and nudges[0].get("e") == "27101" and nudges[1].get("e") == "27100", nudges)
    playing = by.get("playing", [{}])[0]
    core.check("the view follows the playhead in Play the part", near(playing, "span", 5) and num(playing, "view") > 2701 and
               num(playing, "view") <= num(playing, "pos") <= num(playing, "view") + num(playing, "span") and playing.get("range") == "1", playing)
    made = [p for p in new_files(core, "*.mp4", before)]
    core.check("the long timeline cut made one new mp4", len(made) == 1, made)
    if made:
        fps, frames = measure(made[0])
        length = frames / fps if fps else 0
        print("    clip from 45:00: fps=%s frames=%d length=%.3f s" % (fps, frames, length))
        core.check("the clip from minute 45 is 10 s within one frame", fps > 0 and abs(length - 10.0) <= 1.0 / fps + 1e-6, "%.3f" % length)

    print("player window: screenshots of the long video timeline")
    run_app(core, "cut-long-default", [*open_steps(video, key, 16)], capture_after=500 * 20 + 2500)
    run_app(core, "cut-long-zoomed", [*open_steps(video, key, 8), {"player": "trimzoom", "value": 0.1}, *wait(10)],
            capture_after=500 * 22 + 2500)
    run_app(core, "cut-long-45", [*open_steps(video, key, 8), {"player": "trimoverview", "value": 0.75}, {"player": "trimseek", "value": 2700},
                                  *wait(12)], capture_after=500 * 25 + 2500)


def long_audio_part(core, longs, key):
    missing = [p for p in [EXE, *window_runner(), LIBMPV] if not os.path.isfile(p)]
    if missing:
        print("SKIP the long audio part, missing: " + ", ".join(missing))
        return
    song = longs["song"]
    duration = 2400.0
    print("player window: the timeline of a 40 minute mp3")
    before = set(glob.glob(os.path.join(core.downloads, "**", "*.mp3"), recursive=True))
    steps = [*open_steps(song, key, 16), {"player": "opened"},
             {"player": "trimpan", "value": 1}, {"player": "trimzoom", "value": 1000}, {"player": "trimzoom", "value": 0.0001},
             {"player": "trimzoom", "value": 120.0 / 5.0},
             {"player": "trimselect", "value": 0}, {"player": "trimoverview", "value": 0.75}, {"player": "trimdragview", "value": 0.5},
             {"player": "trimdragend"}, {"player": "trimselect", "value": 1}, {"player": "trimoverview", "value": 0.75},
             {"player": "trimdragview", "value": (1810.0 - 1740.0) / 120.0}, {"player": "trimdragend"}, *wait(2),
             {"player": "trimapply"}, *wait(12)]
    log = run_app(core, "cut-long-audio", steps)
    found = trim_lines(log)
    by = by_command(found)
    core.check("long audio trim lines were logged", len(found) >= 10, len(found))
    if len(found) < 10:
        print(log[-3000:])
        return
    first = by.get("opened", [{}])[0]
    print("    opened: " + str(first))
    core.check("the long song opens with about 120 s in view", near(first, "span", 120) and near(first, "view", 0) and first.get("audio") == "1", first)
    core.check("the wave is fetched once at 20 bars a second, at most 4000", int(first.get("wave", "0")) >= 3990, first)
    core.check("only the visible slice is drawn", 0 < int(first.get("slice", "0")) <= 600, first)
    pan = by.get("trimpan", [{}])[0]
    core.check("pan one view", near(pan, "view", 120), pan)
    zooms = by.get("trimzoom", [])
    whole = zooms[0] if zooms else {}
    core.check("whole song in view: bars grouped, few drawn", near(whole, "span", duration, 0.5) and int(whole.get("group", "0")) > 1
               and int(whole.get("slice", "0")) <= num(whole, "strip") / 2, whole)
    core.check("zoom in stops at 5 s", len(zooms) >= 2 and near(zooms[1], "span", 5) and zooms[1].get("group") == "1", zooms[1:2])
    start = by.get("trimdragview", [{}])[0]
    core.check("audio start handle at 30:00, in the middle of the view", abs(int(start.get("s", "0")) / 100.0 - (num(start, "view") + 60)) <= 0.011
               and abs(int(start.get("s", "0")) / 100.0 - 1800) < 0.1, start)
    end = by.get("trimdragview", [{}])[-1]
    core.check("audio end handle 10 s later", int(end.get("e", "0")) - int(end.get("s", "0")) == 1000, end)
    made = new_files(core, "*.mp3", before)
    core.check("the long audio cut made one new mp3", len(made) == 1, made)
    if made:
        length = audio_length(made[0])
        print("    audio clip from 30:00: %.4f s" % length)
        core.check("the audio clip is 10 s within one mp3 frame", abs(length - 10.0) <= 1152.0 / 22050.0 + 1e-3, "%.4f" % length)
    run_app(core, "cut-long-audio-shot", [*open_steps(song, key, 14), {"player": "trimoverview", "value": 0.75}, *wait(3)],
            capture_after=500 * 22 + 2500)


if __name__ == "__main__":
    run()
