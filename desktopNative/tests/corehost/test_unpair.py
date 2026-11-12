import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS
from test_bridge import PORT, ALERT, ORIGIN, EDGE, request


def run():
    core = Core("unpair", config={"bridge": True, "bridgePort": PORT})
    try:
        core.pump(1.5)
        ext = {"X-FetchJet": "1", "Origin": ORIGIN, "User-Agent": EDGE, "X-FetchJet-Ext": "3.3.88", "X-FetchJet-Pairing": "1"}
        seen = {}

        def allow():
            if core.rows("alerts") and not seen.get("done"):
                seen["done"] = True
                core.action("AlertDismiss", kind=ALERT["BRIDGE_PAIRING"], fromScrim=False)

        answer = request(core, "POST", "/pair", ext, {}, timeout=30, while_waiting=allow)
        token = (answer.json() or {}).get("token", "")
        core.check("allow gives a token", answer.status == 200 and len(token) >= 32, (answer.status, answer.body[:60], answer.error))
        paired = dict(ext)
        paired["X-FetchJet-Token"] = token
        answer = request(core, "GET", "/config", paired)
        core.check("config says paired", (answer.json() or {}).get("paired") is True, answer.body[:80])
        answer = request(core, "POST", "/links", paired, [])
        core.check("the token is accepted before unpairing", answer.status != 401, (answer.status, answer.body[:60]))
        core.pump(1.0)

        core.action("OpenSettings")
        core.pump(0.6)
        core.action("SettingClick", id="unpairBrowsers")
        core.pump(0.8)

        answer = request(core, "GET", "/config", paired)
        core.check("the old token no longer counts as paired", (answer.json() or {}).get("paired") is False, answer.body[:80])
        answer = request(core, "POST", "/links", paired, [])
        core.check("the old token is refused with pairing required", answer.status == 401 and answer.body == "pairing required", (answer.status, answer.body[:60]))

        seen.clear()
        core.pump(8.5)
        answer = request(core, "POST", "/pair", ext, {}, timeout=30, while_waiting=allow)
        fresh = (answer.json() or {}).get("token", "")
        core.check("pairing again gives a different token", answer.status == 200 and len(fresh) >= 32 and fresh != token, (answer.status, answer.body[:60]))
        again = dict(ext)
        again["X-FetchJet-Token"] = fresh
        answer = request(core, "POST", "/links", again, [])
        core.check("the new token works", answer.status != 401, (answer.status, answer.body[:60]))
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
