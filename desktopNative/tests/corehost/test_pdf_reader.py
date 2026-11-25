import glob
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

PORT = int(os.environ.get("FJ_PDF_PORT", "18274"))
BASE = "http://127.0.0.1:%d" % PORT
GZ_NAME = "pdfium.gz"
os.environ["FETCHJET_NO_USAGE_PING"] = "1"
os.environ["FETCHJET_PDFIUM_URL"] = BASE + "/" + GZ_NAME

from corehost import Core, CheckFailed, ENUMS, ON_LINUX, ON_MAC, PLAYER_EXE, run_window, sha256_of_file, window_runner
from test_hidden import add_and_wait, open_hidden, pin_alert, type_pin, visible_names

PIN = "2468"
EFFECT = {name: index for index, name in enumerate(ENUMS["EffectKind"])}
CHOICE = {name: index for index, name in enumerate(ENUMS["ChoiceDialogKind"])}
PDFIUM_NAME = "libpdfium.dylib" if ON_MAC else "libpdfium.so" if ON_LINUX else "pdfium.dll"
GZ_SOURCE = os.environ.get("FJ_PDFIUM_GZ", os.path.expanduser("~/fetchjet-native/work/pdfium/pdfium-linux-x64-8179.gz") if ON_LINUX and not ON_MAC
                           else os.path.expanduser("~/fetchjet-native/work/pdfium/pdfium-macos-arm64-8179.gz") if ON_MAC
                           else "E:/tools/pdfium/pdfium-windows-x64-8179.gz")
EXE = PLAYER_EXE
OUT = os.environ.get("FJ_SHOT_OUT", "")
ARABIC = "تقرير الشهر.pdf"


def pdf_bytes(pages, encrypted=False):
    objects = []
    count = len(pages)
    kids = " ".join("%d 0 R" % (6 + 2 * i) for i in range(count))
    objects.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    objects.append(("<< /Type /Pages /Kids [%s] /Count %d >>" % (kids, count)).encode())
    objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    objects.append(b"<< /Filter /Standard /V 1 /R 2 /O <" + b"41" * 32 + b"> /U <" + b"42" * 32 + b"> /P -4 >>")
    for i, (width, height, label) in enumerate(pages):
        shade = 0.55 + 0.4 * ((i % 5) / 4.0)
        stream = ("q %.2f 0.85 1 rg 36 36 %d %d re f Q q 0.1 0.2 0.5 RG 6 w 36 36 %d %d re S Q "
                  "BT /F1 64 Tf 72 %d Td (%s) Tj ET BT /F1 20 Tf 72 %d Td (FetchJet PDF reader test) Tj ET"
                  % (shade, width - 72, height - 72, width - 72, height - 72, height - 160, label, height - 210)).encode()
        objects.append(b"<< /Length %d >>\nstream\n" % len(stream) + stream + b"\nendstream")
        objects.append(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %d %d] /Resources << /Font << /F1 3 0 R >> >> /Contents %d 0 R >>"
                        % (width, height, 5 + 2 * i)).encode())
    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += b"%d 0 obj\n" % number + body + b"\nendobj\n"
    xref = len(out)
    out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objects) + 1)
    for offset in offsets:
        out += b"%010d 00000 n \n" % offset
    trailer = b"<< /Size %d /Root 1 0 R" % (len(objects) + 1)
    if encrypted:
        trailer += b" /Encrypt 4 0 R /ID [<00112233445566778899aabbccddeeff> <00112233445566778899aabbccddeeff>]"
    out += b"trailer\n" + trailer + b" >>\nstartxref\n%d\n%%%%EOF\n" % xref
    return bytes(out)


def make_site(folder):
    os.makedirs(folder, exist_ok=True)
    book = [(612, 792, "Page %d" % (i + 1)) for i in range(30)]
    book[4] = (842, 595, "Page 5 wide")
    open(os.path.join(folder, "book.pdf"), "wb").write(pdf_bytes(book))
    open(os.path.join(folder, "locked.pdf"), "wb").write(pdf_bytes([(612, 792, "Locked")], encrypted=True))
    open(os.path.join(folder, ARABIC), "wb").write(pdf_bytes([(595, 842, "Arabic name %d" % (i + 1)) for i in range(5)]))
    open(os.path.join(folder, "secret.pdf"), "wb").write(pdf_bytes([(612, 792, "Secret %d" % (i + 1)) for i in range(3)]))
    shutil.copy2(GZ_SOURCE, os.path.join(folder, GZ_NAME))


def tools(core):
    return os.path.join(core.home, "tools")


def library(core):
    return os.path.join(tools(core), PDFIUM_NAME)


def marker(core):
    path = os.path.join(tools(core), "pdfium.sha256")
    return open(path, encoding="utf-8").read().strip() if os.path.isfile(path) else ""


def opened(core):
    return [json.loads(e.get("text") or "{}") for e in core.take_effects() if e.get("kind", 0) == EFFECT["OPEN_PDF"]]


def ask(core):
    return next((c for c in core.rows("choices") if c.get("kind") == CHOICE["PDF_READER"]), None)


def open_row(core, task_id, settle=0.6):
    core.take_effects()
    core.action("RowQuickAction", id=task_id)
    core.pump(settle)


def wait_open(core, timeout=60):
    found = []

    def done():
        found.extend(opened(core))
        return bool(found)

    core.wait_for("the reader to open", done, timeout)
    return found[0]


def pick(core, index):
    core.action("SettingPick", id="pdfReader", index=index)
    core.pump(0.3)


def pages_store(core):
    files = glob.glob(os.path.join(core.home, "**", "pdf_pages.json"), recursive=True)
    return json.load(open(files[0], encoding="utf-8")) if files else {}


def settings_row(core, row_id):
    core.action("OpenSettings")
    core.pump(0.4)
    for section in range(0, 14):
        core.action("SetSettingsSection", index=section)
        core.pump(0.2)
        rows = core.rows("settingsRows")
        row = next((r for r in rows if r.get("id") == row_id), None)
        if row:
            return row, rows
    return None, []


def core_part(core, ids):
    book = ids["book.pdf"]
    strings = core.strings()
    gz_sha = sha256_of_file(GZ_SOURCE)

    print("settings: the PDF reader row, ask by default")
    row, _ = settings_row(core, "pdfReader")
    core.check("the PDF reader row is on the media tools page", row is not None)
    if row:
        core.check("three options: ask, in-app reader, system app",
                   row.get("options") == [strings["playerModeAsk"], strings["pdfModeOn"], strings["playerModeOff"]], row.get("options"))
        core.check("ask is picked by default", row.get("pick") == 1, row.get("pick"))
    core.action("SetFilter", filter=0)
    core.pump(0.3)

    print("ask: the dialog, dismissed, then asked again")
    open_row(core, book)
    dialog = ask(core)
    core.check("opening a PDF asks once", dialog is not None, core.rows("choices"))
    if dialog:
        core.check("the dialog names the reader download", dialog.get("title") == strings["pdfAskTitle"] and dialog.get("primaryLabel") == strings["pdfAskDownload"], dialog)
        core.check("the other choice is the system app", dialog.get("secondaryLabel") == strings["playerAskSystem"], dialog.get("secondaryLabel"))
        core.check("the dialog says it can be changed later", strings["playerAskLater"] in dialog.get("question", ""), dialog.get("question"))
    core.check("nothing opened while asking", opened(core) == [])
    core.check("nothing downloaded while asking", not os.path.exists(library(core)))
    core.action("ChoiceDismiss", kind=CHOICE["PDF_READER"])
    core.pump(0.3)
    core.check("cancel closes the dialog", ask(core) is None)
    open_row(core, book)
    core.check("after cancel it asks again", ask(core) is not None)

    print("ask -> system app: remembered, no dialog after")
    core.action("ChoiceSecondary", kind=CHOICE["PDF_READER"])
    core.pump(0.4)
    core.check("the system app choice closes the dialog", ask(core) is None)
    row, _ = settings_row(core, "pdfReader")
    core.check("the system app is now the mode", row and row.get("pick") == 3, row and row.get("pick"))
    core.action("SetFilter", filter=0)
    open_row(core, book)
    core.check("system app mode: no dialog", ask(core) is None)
    core.check("system app mode: the reader does not open", opened(core) == [])

    print("in-app mode with no reader: it is downloaded, then the PDF opens")
    pick(core, 1)
    row, rows = settings_row(core, "pdfReader")
    core.check("in-app is picked", row and row.get("pick") == 2, row and row.get("pick"))
    buttons = [b.get("id") for r in rows for b in r.get("buttons", [])]
    core.check("the download button shows while the reader is missing", "pdfLibrary" in buttons, buttons)
    core.check("the download note shows", any(r.get("title") == strings["pdfAskText"] for r in rows))
    core.action("SetFilter", filter=0)
    open_row(core, book, 0.1)
    first = wait_open(core)
    core.check("the reader file is in tools", os.path.isfile(library(core)))
    core.check("the installed sha marker is the pinned one", marker(core) == gz_sha, marker(core))
    core.check("the open message has the path of the file", first.get("path", "").replace("\\", "/").endswith("/book.pdf") and not first.get("hidden"), first)
    core.check("it starts at the first page", first.get("page") == 0, first)
    core.check("the title is the file name", first.get("title") == "book.pdf", first.get("title"))
    row, rows = settings_row(core, "pdfReader")
    buttons = [b.get("id") for r in rows for b in r.get("buttons", [])]
    core.check("the download button is gone once the reader is ready", "pdfLibrary" not in buttons, buttons)
    core.action("SetFilter", filter=0)

    print("ask mode with the reader ready: opens directly")
    pick(core, 0)
    open_row(core, book)
    core.check("no dialog when the reader is ready", ask(core) is None)
    core.check("the reader opens at once", len(opened(core)) == 1)

    print("ask mode with no reader: the dialog's download sets in-app and opens")
    os.remove(library(core))
    open_row(core, book)
    core.check("the dialog comes back when the reader is missing", ask(core) is not None)
    core.action("ChoicePrimary", kind=CHOICE["PDF_READER"])
    wait_open(core)
    core.check("download from the dialog installs the reader", os.path.isfile(library(core)) and marker(core) == gz_sha)
    row, _ = settings_row(core, "pdfReader")
    core.check("the dialog download sets in-app", row and row.get("pick") == 2, row and row.get("pick"))
    core.action("SetFilter", filter=0)

    print("an outdated copy is replaced before opening, even in ask mode")
    pick(core, 0)
    open(os.path.join(tools(core), "pdfium.sha256"), "w").write("0" * 64)
    with open(library(core), "wb") as handle:
        handle.write(b"old copy")
    open_row(core, book, 0.1)
    core.check("no dialog for an outdated copy", ask(core) is None)
    wait_open(core)
    core.check("the outdated file was replaced", os.path.getsize(library(core)) > 1000000 and marker(core) == gz_sha,
               (os.path.getsize(library(core)), marker(core)))

    print("system app mode never opens the reader")
    pick(core, 2)
    open_row(core, book)
    core.check("off: no reader, no dialog", opened(core) == [] and ask(core) is None)
    pick(core, 1)

    print("last page per download")
    core.check("remember answers ok", core.call("pdf.page", {"id": book, "page": 7}) == "ok")
    core.check("the store has the page", pages_store(core).get(book) == "7", pages_store(core))
    open_row(core, book)
    again = opened(core)
    core.check("the reader opens at the remembered page", again and again[0].get("page") == 7, again)

    print("shared helpers")
    core.check("page label", core.call("pdf.label", {"page": 2, "count": 120}) == "3 / 120")
    core.check("go to page in range", core.call("pdf.goto", {"text": " 12 ", "count": 30}) == "11")
    core.check("go to page out of range", core.call("pdf.goto", {"text": "31", "count": 30}) == "-1")
    core.check("go to page not a number", core.call("pdf.goto", {"text": "abc", "count": 30}) == "-1")
    core.check("zoom in", abs(float(core.call("pdf.zoom", {"zoom": "1.0", "in": "true"})) - 1.25) < 1e-4)
    core.check("zoom out is clamped", abs(float(core.call("pdf.zoom", {"zoom": "0.5", "in": "false"})) - 0.5) < 1e-4)

    print("non-ASCII file name")
    open_row(core, ids[ARABIC])
    arabic = opened(core)
    core.check("the Arabic-named PDF opens", arabic and arabic[0].get("path", "").endswith(ARABIC) and arabic[0].get("title") == ARABIC, arabic)


def hidden_part(core, ids):
    secret = ids["secret.pdf"]
    print("hidden: opens from the private folder, nothing remembered")
    core.debug("hideAnyType", {"on": "true"})
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
    core.wait_for("the PDF leaves the list", lambda: "secret.pdf" not in visible_names(core), 30)
    core.check("locked: the hidden PDF does not open", core.call("pdf.page", {"id": secret, "page": 1}) == "")
    open_hidden(core, PIN)
    core.pump(0.6)
    before = dict(pages_store(core))
    open_row(core, secret, 0.1)
    found = wait_open(core, 20)
    path = found.get("path", "").replace("\\", "/")
    core.check("the hidden PDF opens with the hidden flag", found.get("hidden") is True, found)
    core.check("from the private folder, not the downloads folder", path.endswith("/secret.pdf") and not path.startswith(core.downloads), path)
    core.check("a hidden PDF starts at the first page", found.get("page") == 0, found)
    core.check("its page is not remembered", core.call("pdf.page", {"id": secret, "page": 2}) == "")
    core.check("the page store did not change", pages_store(core) == before and secret not in json.dumps(pages_store(core)), (before, pages_store(core)))
    core.take_effects()
    core.action("HiddenLock")
    core.pump(0.6)
    closed = [e for e in core.take_effects() if e.get("kind", 0) == EFFECT["CLOSE_PDF"]]
    core.check("locking the hidden page closes the reader", len(closed) == 1, closed)


def parse_log(text):
    found = []
    for line in text.splitlines():
        if " -> " not in line:
            continue
        head, rest = line.split(" -> ", 1)
        state = {"command": head}
        error = ""
        if " error=" in rest:
            rest, tail = rest.split(" error=", 1)
            error = tail.split(" id=", 1)[0]
        for part in rest.split(" "):
            if "=" in part:
                key, value = part.split("=", 1)
                state[key] = value
        label = re.search(r"label=(.*?) zoom=", line)
        state["label"] = label.group(1) if label else ""
        state["error"] = error
        found.append(state)
    return found


def run_app(core, name, steps):
    script = os.path.join(core.work, name + "-script.txt")
    with open(script, "w", encoding="utf-8") as handle:
        for step in steps:
            handle.write(json.dumps(step, ensure_ascii=False) + "\n")
    log = os.path.join(core.work, name + "-pdf.log")
    if os.path.exists(log):
        os.remove(log)
    env = dict(os.environ, FJ_PDF_LOG=log, FETCHJET_NO_USAGE_PING="1")
    total = 500 * len(steps) + 6000
    folder = OUT or core.work
    os.makedirs(folder, exist_ok=True)
    capture = os.path.join(folder, name + ".bmp")
    output = run_window([EXE, "--home", core.home, "--downloads", core.downloads, "--no-bridge", "--no-focus", "--no-auto-downloads", "--script", script],
                        env, total, total - 2000, capture)
    print("  " + output.strip().replace("\n", "\n  ")[-300:])
    shots = sorted(glob.glob(os.path.join(folder, name + "*.bmp")), key=os.path.getsize)
    if shots:
        try:
            from PIL import Image
            png = os.path.join(folder, name + ".png")
            Image.open(shots[-1]).save(png)
            for shot in shots:
                os.remove(shot)
            print("  screenshot " + png)
        except Exception as error:
            print("  screenshot " + shots[-1] + " (" + str(error) + ")")
    return parse_log(open(log, encoding="utf-8", errors="replace").read() if os.path.exists(log) else "")


def wait(n):
    return [{"pdf": "state"}] * n


def host_part(core, ids):
    missing = [p for p in [EXE, *window_runner()] if not os.path.isfile(p)]
    if missing:
        print("SKIP the reader window part, missing: " + ", ".join(missing))
        return
    book = ids["book.pdf"]
    core.check("the reader is installed for the window part", os.path.isfile(library(core)))

    print("reader window: restore, go to page, zoom, fit, keys, close, reopen")
    steps = [{"type": "RowQuickAction", "id": book}, *wait(6),
             {"pdf": "goto", "value": 12}, *wait(3),
             {"pdf": "zoomin"}, *wait(4),
             {"pdf": "zoomout"}, *wait(3),
             {"pdf": "zoomin"}, {"pdf": "zoomin"}, *wait(4),
             {"pdf": "fit"}, *wait(3),
             {"pdf": "end"}, *wait(3),
             {"pdf": "home"}, *wait(3),
             {"pdf": "step", "value": 1}, *wait(2),
             {"pdf": "gotobad"}, *wait(1),
             {"pdf": "goto", "value": 3}, *wait(3),
             {"pdf": "close"}, *wait(2),
             {"type": "RowQuickAction", "id": book}, *wait(6)]
    states = run_app(core, "pdf-run1", steps)
    core.check("the reader logged its steps", len(states) == 55, len(states))
    if len(states) < 55:
        return
    first = states[5]
    core.check("the book opened with 30 pages", first.get("open") == "1" and first.get("count") == "30" and first.get("loading") == "0", first)
    core.check("it opened at the remembered page 8", first.get("page") == "7" and first.get("label") == "8 / 30", first)
    core.check("fit width is the default", first.get("fit") == "1", first)
    core.check("the visible pages are drawn", int(first.get("ready", 0)) >= 1 and first.get("ready") == first.get("visible"), first)
    after_goto = states[9]
    core.check("go to page 12", after_goto.get("page") == "11" and after_goto.get("label") == "12 / 30", after_goto)
    fit_zoom = float(first["zoom"])
    zoomed = states[14]
    core.check("zoom in is 1.25 times and leaves fit", abs(float(zoomed["zoom"]) - fit_zoom * 1.25) < 0.002 and zoomed.get("fit") == "0", (fit_zoom, zoomed))
    core.check("zoom in keeps the page", zoomed.get("page") == "11", zoomed)
    core.check("the page is drawn again at the new size", int(zoomed.get("pw", 0)) > int(after_goto.get("pw", 0)) and zoomed.get("ready") == zoomed.get("visible"),
               (after_goto.get("pw"), zoomed.get("pw"), zoomed.get("ready"), zoomed.get("visible")))
    back = states[18]
    core.check("zoom out goes back", abs(float(back["zoom"]) - fit_zoom) < 0.002, back)
    twice = states[24]
    core.check("two zoom-ins", abs(float(twice["zoom"]) - fit_zoom * 1.5625) < 0.003, twice)
    fit = states[28]
    core.check("fit width returns to the fitted zoom", fit.get("fit") == "1" and abs(float(fit["zoom"]) - fit_zoom) < 0.002 and fit.get("page") == "11", fit)
    end = states[32]
    core.check("End goes to the last page", end.get("page") == "29" and end.get("label") == "30 / 30", end)
    home = states[36]
    core.check("Home goes to the first page", home.get("page") == "0" and float(home.get("y", 1)) == 0.0, home)
    stepped = states[39]
    core.check("next page", stepped.get("page") == "1", stepped)
    bad = states[41]
    core.check("a bad page number does not move", bad.get("page") == "1", bad)
    went = states[45]
    core.check("go to page 3", went.get("page") == "2", went)
    closed = states[48]
    core.check("close", closed.get("open") == "0", closed)
    reopened = states[-1]
    core.check("reopening restores page 3", reopened.get("open") == "1" and reopened.get("page") == "2" and reopened.get("label") == "3 / 30", reopened)
    core.check("the reopened page is drawn", int(reopened.get("ready", 0)) >= 1 and reopened.get("ready") == reopened.get("visible") and reopened.get("empty") == "0", reopened)
    core.check("the last page is on disk", pages_store(core).get(book) == "2", pages_store(core))

    print("a password PDF shows the password message")
    steps = [{"type": "RowQuickAction", "id": ids["locked.pdf"]}, *wait(5)]
    states = run_app(core, "pdf-locked", steps)
    last = states[-1] if states else {}
    core.check("the password message shows", last.get("error") == PASSWORD_TEXT and last.get("count") == "0", last)

    print("a non-ASCII file name opens and draws")
    steps = [{"type": "RowQuickAction", "id": ids[ARABIC]}, *wait(5), {"pdf": "zoomin"}, *wait(4)]
    states = run_app(core, "pdf-arabic", steps)
    last = states[-1] if states else {}
    core.check("the Arabic-named PDF has 5 pages", last.get("count") == "5" and last.get("error") == "", last)
    core.check("and its pages are drawn", int(last.get("ready", 0)) >= 1 and last.get("ready") == last.get("visible"), last)


def run():
    core = Core("pdf_reader")
    site = os.path.join(core.work, "site")
    make_site(site)
    os.environ["FETCHJET_PDFIUM_SHA256"] = sha256_of_file(GZ_SOURCE)
    server = subprocess.Popen([sys.executable, "-m", "http.server", str(PORT), "--bind", "127.0.0.1", "--directory", site],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    ids = {}
    try:
        core.pump(0.5)
        for name in ["book.pdf", "locked.pdf", ARABIC, "secret.pdf"]:
            if name == ARABIC:
                core.debug("addDirect", {"url": BASE + "/" + urllib.parse.quote(name), "fileName": name})
                task = core.wait_task(["COMPLETED", "ERROR"], timeout=40, file_name=name)
            else:
                task = add_and_wait(core, BASE + "/" + name, name)
            core.check("downloaded " + name, task and task["status"] == "COMPLETED", task and task["status"])
            ids[name] = task["id"]
        core_part(core, ids)
        hidden_part(core, ids)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
        server.terminate()
        core.finish()
        return
    finally:
        server.terminate()
    crash = core.crash_text()
    core.check("no entries in native-crash.log", crash == "", crash[:1500])
    globals()["PASSWORD_TEXT"] = core.strings()["pdfPasswordProtected"]
    core.shutdown()
    try:
        host_part(core, ids)
    except CheckFailed as failure:
        core.check(str(failure), False)
    print("%d checks, %d failed" % (core.checks, len(core.failures)))
    sys.stdout.flush()
    os._exit(1 if core.failures else 0)


if __name__ == "__main__":
    run()
