import os
import sys
import time
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import test_bridge
from corehost import Core, CheckFailed
from test_bridge import request, ORIGIN, EDGE, ALERT

PORT = int(os.environ.get("FJ_UNSAFE_PORT", "28471"))
test_bridge.PORT = PORT
DEAD = "http://127.0.0.1:9/"


def run():
    core = Core("unsafe_names", config={"bridge": True, "bridgePort": PORT})
    outside = os.path.join(os.path.dirname(os.path.dirname(core.downloads)), "outside-victim.txt")
    try:
        core.pump(1.5)
        ext = {"X-FetchJet": "1", "Origin": ORIGIN, "User-Agent": EDGE, "X-FetchJet-Ext": "3.5.0", "X-FetchJet-Pairing": "1"}
        seen = {}

        def allow():
            if core.rows("alerts") and not seen.get("done"):
                seen["done"] = True
                core.action("AlertDismiss", kind=ALERT["BRIDGE_PAIRING"], fromScrim=False)

        answer = request(core, "POST", "/pair", ext, {}, timeout=30, while_waiting=allow)
        token = (answer.json() or {}).get("token", "")
        core.check("paired", len(token) >= 32, (answer.status, answer.body[:60]))
        local = {"X-FetchJet": "1", "X-FetchJet-Token": token}
        import test_automation
        test_bridge.PORT = PORT
        test_automation.cli(core, ["automation", "on"], port=PORT)

        open(outside, "w").write("keep me")
        names = {
            "../../outside-victim.txt": "dots and slashes",
            "..\\..\\outside-victim.txt": "dots and backslashes",
            "/abs/outside-victim.txt": "an absolute path",
            "..": "only dots",
        }
        for name in names:
            answer = request(core, "POST", "/links", local, [{"url": DEAD + urllib.parse.quote(name, safe="") + ".bin", "fileName": name}])
            core.check("the link list took %s" % names[name], answer.status == 200, (answer.status, answer.body[:80]))
            core.pump(0.5)

        tasks = []
        end = time.time() + 20
        while time.time() < end and len(tasks) < len(names):
            core.pump(0.5)
            status = request(core, "GET", "/api/status", local).json() or {}
            tasks = [t for t in status.get("downloads", []) if t.get("url", "").startswith(DEAD)]
        core.check("all four links became downloads", len(tasks) == len(names), [t.get("name") for t in tasks])
        for task in tasks:
            name = task.get("name", "")
            core.check("no path in the saved name: %r" % name, "/" not in name and "\\" not in name and name not in (".", ".."), name)

        for task in tasks:
            request(core, "POST", "/api/delete?id=" + task["id"], local)
        core.pump(1.5)
        core.check("deleting them with their files left the file outside the folder", os.path.isfile(outside))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    finally:
        if os.path.isfile(outside):
            os.remove(outside)
    core.finish()


if __name__ == "__main__":
    run()
