import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, install_fake_ytdlp

STATE = {name: index for index, name in enumerate(ENUMS["MediaStateCode"])}
FINAL = "https://www.youtube.com/watch?v=jNQXAC9IVRw"


def media(core):
    return core.snapshot("addMedia") or {}


def calls(core):
    path = os.path.join(core.home, "tools", "yt-dlp-dir", "fake-calls.log")
    if not os.path.isfile(path):
        return []
    with open(path, encoding="utf-8", errors="replace") as handle:
        return [line.strip() for line in handle if "youtube.com" in line]


def run():
    core = Core("typing", before_start=install_fake_ytdlp)
    try:
        core.pump(1.0)
        core.check("no call yet", calls(core) == [], calls(core))

        print("a link typed one character at a time is inspected once, when typing stops")
        core.action("ShowAddDialog", show=True)
        core.pump(0.3)
        for end in range(len("https://www.youtube.com/watch?v=j"), len(FINAL) + 1):
            core.action("SetUrl", text=FINAL[:end])
            last = time.time()
            core.pump(0.12)
        core.pump(0.2)
        early = calls(core)
        core.check("nothing is inspected while characters keep coming", early == [], early)
        core.wait_for("the first call", lambda: len(calls(core)) > 0, 30)
        typed_delay = time.time() - last
        core.wait_for("the qualities", lambda: media(core).get("state", 0) == STATE["QUALITIES"], 30)
        seen = calls(core)
        partials = [FINAL[:end] for end in range(len("https://www.youtube.com/watch?v=j"), len(FINAL))]
        stale = [line for line in seen if any(line.endswith(" " + p) or line.endswith(p) for p in partials)]
        core.check("only the last link reached yt-dlp", len(seen) >= 1 and not stale and all(FINAL in line for line in seen), seen[:6])

        print("a pasted link starts without the long wait")
        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
        core.action("ShowAddDialog", show=True)
        core.pump(0.2)
        core.action("SetUrl", text="")
        core.pump(0.3)
        before = len(calls(core))
        started = time.time()
        core.action("SetUrl", text="https://www.youtube.com/watch?v=pasted12345")
        core.wait_for("a call", lambda: len(calls(core)) > before, 10)
        delay = time.time() - started
        core.check("the pasted link starts at least 0.4 s sooner than a typed one", delay < typed_delay - 0.4, "paste %.2f s, typed %.2f s" % (delay, typed_delay))
        core.check("the typed link waited for the typing to stop", typed_delay > 0.65, "%.2f s" % typed_delay)
        core.action("ShowAddDialog", show=False)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
