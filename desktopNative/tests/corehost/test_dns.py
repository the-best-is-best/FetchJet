import base64
import json
import os
import socket
import struct
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER

DOH_PORT = int(os.environ.get("FJ_DOH_PORT", "18291"))
UDP_PORT = int(os.environ.get("FJ_UDPDNS_PORT", "18292"))
NAME = "files.fjtest.example"
SERVER_PORT = int(SERVER.rsplit(":", 1)[1])
asked = {"doh": [], "udp": []}


def answer(query):
    ident = query[:2]
    at = 12
    labels = []
    while query[at] != 0:
        length = query[at]
        labels.append(query[at + 1:at + 1 + length].decode())
        at += 1 + length
    qtype = struct.unpack(">H", query[at + 1:at + 3])[0]
    question = query[12:at + 5]
    name = ".".join(labels).lower()
    found = name == NAME and qtype == 1
    head = ident + struct.pack(">HHHHH", 0x8180 if found else 0x8183, 1, 1 if found else 0, 0, 0)
    body = question
    if found:
        body += b"\xc0\x0c" + struct.pack(">HHIH", 1, 1, 60, 4) + bytes([127, 0, 0, 1])
    return name, head + body


class DohHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        raw = parse_qs(urlparse(self.path).query).get("dns", [""])[0]
        query = base64.urlsafe_b64decode(raw + "=" * (-len(raw) % 4))
        name, data = answer(query)
        asked["doh"].append(name)
        self.send_response(200)
        self.send_header("Content-Type", "application/dns-message")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


def udp_server(sock):
    while True:
        try:
            data, peer = sock.recvfrom(2048)
        except OSError:
            return
        name, reply = answer(data)
        asked["udp"].append(name)
        sock.sendto(reply, peer)


def set_dns(core, mode, custom=None, adapter=None):
    core.action("SettingPick", id="dnsMode", index=mode)
    core.pump(0.3)
    if custom is not None:
        core.action("SettingText", id="dnsCustom", text=custom)
    if adapter is not None:
        core.action("SettingText", id="netAdapter", text=adapter)
    if custom is not None or adapter is not None:
        core.pump(0.2)
        core.action("SettingClick", id="dnsSave")
    core.pump(0.8)


def fetch(core, key, name):
    core.debug("addDirect", {"url": "http://%s:%d/file/1MB.bin?key=%s" % (NAME, SERVER_PORT, key), "fileName": name})
    return core.wait_task(["COMPLETED", "ERROR"], timeout=60, file_name=name)


def run():
    doh = ThreadingHTTPServer(("127.0.0.1", DOH_PORT), DohHandler)
    threading.Thread(target=doh.serve_forever, daemon=True).start()
    udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udp.bind(("127.0.0.1", UDP_PORT))
    threading.Thread(target=udp_server, args=(udp,), daemon=True).start()
    core = Core("dns")
    try:
        core.pump(0.5)
        core.action("OpenSettings")
        core.action("SetSettingsSection", index=6)
        core.pump(0.5)
        print("system DNS does not know the test name")
        task = fetch(core, "dns-0", "sys.bin")
        core.check("with the system DNS the download fails", task["status"] == "ERROR", task.get("error"))
        core.check("our DNS servers were not asked", asked["doh"] == [] and asked["udp"] == [])

        print("DNS over HTTPS (a local stub)")
        set_dns(core, 5, custom="http://127.0.0.1:%d/dns-query" % DOH_PORT)
        saved = json.load(open(os.path.join(core.home, "network.json"), encoding="utf-8")) \
            if os.path.isfile(os.path.join(core.home, "network.json")) else {}
        core.check("saved in network.json", (saved.get("dns") or {}).get("mode") == "CUSTOM", saved.get("dns"))
        task = fetch(core, "dns-a", "doh.bin")
        core.check("the download works through our DNS", task["status"] == "COMPLETED", task.get("error"))
        core.check("the DoH stub was asked for the name", NAME in asked["doh"], asked["doh"])
        core.check("the file is whole", os.path.getsize(os.path.join(task["destinationPath"], "doh.bin")) == 1024 * 1024)

        print("plain DNS servers (UDP)")
        set_dns(core, 5, custom="127.0.0.1:%d" % UDP_PORT)
        task = fetch(core, "dns-b", "udp.bin")
        core.check("the download works with a UDP DNS server", task["status"] == "COMPLETED", task.get("error"))
        core.check("the UDP stub was asked", NAME in asked["udp"], asked["udp"])

        print("network adapter")
        set_dns(core, 5, adapter="127.0.0.1")
        task = fetch(core, "dns-c", "adapter-ok.bin")
        core.check("an adapter that exists: the download works", task["status"] == "COMPLETED", task.get("error"))
        set_dns(core, 5, adapter="203.0.113.7")
        task = fetch(core, "dns-d", "adapter-missing.bin")
        core.check("an adapter that is not on this computer fails", task["status"] == "ERROR", task.get("status"))
        message = core.wait_for("the error text", lambda: (core.task(file_name="adapter-missing.bin") or {}).get("error") or "", 15)
        core.check("and says the adapter is not available", "network adapter 203.0.113.7 is not available" in message, message[:200])

        print("back to the system")
        set_dns(core, 0, adapter="")
        before = len(asked["doh"]) + len(asked["udp"])
        task = fetch(core, "dns-e", "sys2.bin")
        core.check("system again: the test name fails", task["status"] == "ERROR")
        core.check("and our DNS is not asked any more", len(asked["doh"]) + len(asked["udp"]) == before)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2500:])
    finally:
        doh.shutdown()
        udp.close()
    core.finish()


if __name__ == "__main__":
    run()
