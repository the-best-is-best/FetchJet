import os
import shutil
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, REPO

FAKE = os.path.join(REPO, "desktopNative", "tests", "fakes", "build", "fake-installer.exe")


def run():
    if not os.path.isfile(FAKE):
        raise SystemExit("build the fake first: desktopNative/tests/fakes/build.cmd")
    core = Core("update_install")
    folder = os.path.join(core.home, "update folder")
    os.makedirs(folder, exist_ok=True)
    installer = os.path.join(folder, "FetchJet-9.9.9-setup.exe")
    shutil.copy2(FAKE, installer)
    ran = os.path.join(folder, "installer-ran.txt")
    try:
        core.pump(1.0)

        print("a missing installer")
        answer = core.debug("installUpdate", {"path": os.path.join(folder, "nothing.exe")})
        core.check("it is refused", str(answer).startswith("error"), answer)

        print("install the update: the installer waits until the app is closed")
        answer = core.debug("installUpdate", {"path": installer})
        core.check("the call is accepted", answer == "ok", answer)
        core.pump(2.0)
        core.check("the installer has not started while the app runs", not os.path.exists(ran))

        closing = int(time.time() * 1000)
        core.dll.fj_shutdown()
        deadline = time.time() + 10
        while not os.path.exists(ran) and time.time() < deadline:
            time.sleep(0.1)
        core.check("the installer started after the app closed", os.path.exists(ran))
        if os.path.exists(ran):
            lines = open(ran, encoding="utf-8").read().splitlines()
            started = int(lines[0])
            core.check("it started after the shutdown began", started >= closing - 50, (started, closing))
            core.check("it was started directly, with no cmd.exe or powershell",
                       "cmd" not in lines[1].lower() and "powershell" not in lines[1].lower(), lines[1])
            core.check("its command line is the installer itself",
                       os.path.normcase(os.path.normpath(installer)) in os.path.normcase(lines[1]), lines[1])
        leftovers = [name for name in os.listdir(folder) if name.endswith(".cmd")]
        core.check("no update script was written", leftovers == [], leftovers)
        log = core.log_text()
        core.check("the log says the installer was started", "Update: starting" in log, log[-800:])
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
    crash = core.crash_text()
    core.check("no entries in native-crash.log", crash == "", crash[:1500])
    print("%d checks, %d failed" % (core.checks, len(core.failures)))
    sys.stdout.flush()
    os._exit(1 if core.failures else 0)


if __name__ == "__main__":
    run()
