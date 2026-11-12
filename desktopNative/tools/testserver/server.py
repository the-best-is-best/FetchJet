import argparse
import base64
import hashlib
import json
import re
import socket
import sys
import threading
import time
from collections import OrderedDict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, quote, unquote, urlsplit

LINE = 16
CHUNK = 256 * 1024
UNITS = {"": 1, "B": 1, "KB": 1024, "MB": 1024 * 1024, "GB": 1024 * 1024 * 1024}
LAST_MODIFIED = "Mon, 02 Nov 2026 10:00:00 GMT"


def parse_size(text, fallback=None):
    m = re.match(r"^\s*(\d+)\s*(B|KB|MB|GB)?", text or "", re.I)
    if not m:
        return fallback
    return int(m.group(1)) * UNITS[(m.group(2) or "").upper()]


class Content:
    def __init__(self):
        self.lock = threading.Lock()
        self.chunks = OrderedDict()
        self.hashes = {}

    def chunk(self, index):
        with self.lock:
            data = self.chunks.get(index)
            if data is not None:
                self.chunks.move_to_end(index)
                return data
        start = index * CHUNK
        data = b"".join(b"%015x\n" % x for x in range(start, start + CHUNK, LINE))
        with self.lock:
            self.chunks[index] = data
            while len(self.chunks) > 48:
                self.chunks.popitem(last=False)
        return data

    def read(self, offset, length):
        out = []
        end = offset + length
        while offset < end:
            index = offset // CHUNK
            inside = offset - index * CHUNK
            take = min(CHUNK - inside, end - offset)
            out.append(self.chunk(index)[inside:inside + take])
            offset += take
        return b"".join(out)

    def sha256(self, base, size):
        key = (base, size)
        with self.lock:
            known = self.hashes.get(key)
        if known:
            return known
        h = hashlib.sha256()
        offset = base
        end = base + size
        while offset < end:
            take = min(CHUNK, end - offset)
            h.update(self.read(offset, take))
            offset += take
        value = h.hexdigest()
        with self.lock:
            self.hashes[key] = value
        return value


class State:
    def __init__(self, log_path, default_throttle, quiet):
        self.lock = threading.Lock()
        self.content = Content()
        self.log_path = log_path
        self.log_file = open(log_path, "a", encoding="utf-8") if log_path else None
        self.default_throttle = default_throttle
        self.quiet = quiet
        self.started = time.time()
        self.keys = {}
        self.total_requests = 0

    def entry(self, key):
        e = self.keys.get(key)
        if e is None:
            e = {"requests": 0, "active": 0, "maxActive": 0, "bytes": 0, "liveBytes": 0, "statuses": {}, "ranges": [], "methods": {}}
            self.keys[key] = e
        return e

    def begin(self, key, method, range_header):
        with self.lock:
            self.total_requests += 1
            e = self.entry(key)
            e["requests"] += 1
            e["methods"][method] = e["methods"].get(method, 0) + 1
            if range_header and len(e["ranges"]) < 400:
                e["ranges"].append(range_header)
            return e["requests"]

    def enter(self, key, cap):
        with self.lock:
            e = self.entry(key)
            if cap and e["active"] >= cap:
                return False
            e["active"] += 1
            e["maxActive"] = max(e["maxActive"], e["active"])
            return True

    def leave(self, key):
        with self.lock:
            e = self.entry(key)
            e["active"] = max(0, e["active"] - 1)

    def live(self, key, count):
        with self.lock:
            self.entry(key)["liveBytes"] += count

    def finish(self, key, status, sent):
        with self.lock:
            e = self.entry(key)
            e["bytes"] += sent
            e["statuses"][str(status)] = e["statuses"].get(str(status), 0) + 1

    def log(self, line):
        if self.log_file:
            with self.lock:
                self.log_file.write(line + "\n")
                self.log_file.flush()
        if not self.quiet:
            sys.stdout.write(line + "\n")
            sys.stdout.flush()

    def reset(self):
        with self.lock:
            active = {k: v["active"] for k, v in self.keys.items() if v["active"]}
            self.keys = {}
            for k, n in active.items():
                self.entry(k)["active"] = n
            self.total_requests = 0


class Cut(Exception):
    pass


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "FetchJetTestServer/1"

    def log_message(self, fmt, *args):
        pass

    @property
    def state(self):
        return self.server.state

    def q(self, name, default=None):
        values = self.query.get(name)
        return values[0] if values else default

    def qi(self, name, default=0):
        try:
            return int(self.q(name, default))
        except (TypeError, ValueError):
            return default

    def do_GET(self):
        self.handle_any()

    def do_HEAD(self):
        self.handle_any()

    def do_POST(self):
        self.handle_any()

    def do_PUT(self):
        self.handle_any()

    def do_OPTIONS(self):
        self.handle_any()

    def handle_any(self):
        started = time.time()
        parts = urlsplit(self.path)
        self.query = parse_qs(parts.query, keep_blank_values=True)
        self.route = unquote(parts.path)
        self.key = self.q("key") or self.route
        self.sent = 0
        self.status = 0
        self.entered = False
        self.number = self.state.begin(self.key, self.command, self.headers.get("Range"))
        try:
            self.dispatch()
        except Cut:
            self.close_connection = True
            self.abort()
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, socket.timeout, OSError):
            self.close_connection = True
        finally:
            if self.entered:
                self.state.leave(self.key)
            self.state.finish(self.key, self.status, self.sent)
            took = int((time.time() - started) * 1000)
            self.state.log("%s %5d %-4s %s range=%s status=%s sent=%d ms=%d" % (
                time.strftime("%H:%M:%S"), self.client_address[1], self.command, self.path,
                self.headers.get("Range", "-"), self.status, self.sent, took))

    def abort(self):
        try:
            self.connection.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, b"\x01\x00\x00\x00")
        except OSError:
            pass
        try:
            self.connection.close()
        except OSError:
            pass

    def head(self, status, headers, length=None, close=False):
        self.status = status
        self.send_response(status)
        for name, value in headers:
            self.send_header(name, value)
        if length is not None:
            self.send_header("Content-Length", str(length))
        if close:
            self.send_header("Connection", "close")
            self.close_connection = True
        self.end_headers()

    def simple(self, status, body, content_type="text/plain; charset=utf-8", headers=()):
        data = body if isinstance(body, bytes) else body.encode("utf-8")
        self.head(status, [("Content-Type", content_type)] + list(headers), len(data))
        if self.command != "HEAD":
            self.wfile.write(data)
            self.sent += len(data)

    def json(self, value, status=200):
        self.simple(status, json.dumps(value, ensure_ascii=False, indent=1), "application/json; charset=utf-8")

    def read_body(self):
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length > 0 else b""

    def origin(self):
        return "127.0.0.1:%d" % self.server.server_address[1]

    def dispatch(self):
        route = self.route
        delay = self.qi("delay")
        if delay > 0:
            time.sleep(delay / 1000.0)
        if route == "/" or route == "/help":
            return self.simple(200, "FetchJet test server. See README.md next to server.py.\n")
        if route == "/stats":
            with self.state.lock:
                data = {"totalRequests": self.state.total_requests, "keys": json.loads(json.dumps(self.state.keys))}
            wanted = self.q("of")
            if wanted is not None:
                data = data["keys"].get(wanted, {})
            return self.json(data)
        if route == "/reset":
            self.state.reset()
            return self.json({"reset": True})
        if route == "/echo":
            body = self.read_body()
            return self.json({
                "method": self.command,
                "path": self.path,
                "headers": {k: v for k, v in self.headers.items()},
                "bodyBytes": len(body),
                "bodySha256": hashlib.sha256(body).hexdigest(),
                "bodyText": body[:4096].decode("utf-8", "replace"),
            })
        if route == "/stall":
            if self.q("after") == "headers":
                self.head(200, [("Content-Type", "application/octet-stream")], 1000000)
            self.status = self.status or -1
            time.sleep(self.qi("seconds", 3600))
            raise Cut()
        if route == "/stream":
            return self.stream()
        if route.startswith("/redirect/"):
            return self.redirect(route[len("/redirect/"):])
        if route.startswith("/sha256/"):
            size = self.file_size(route)
            if size is None:
                return self.simple(400, "no size in the name; use 10MB.bin or ?size=10MB\n")
            return self.simple(200, self.state.content.sha256(self.qi("base"), size) + "\n")
        if route.startswith("/file/"):
            return self.file(route)
        if route.startswith("/hls/"):
            return self.hls(route)
        if route.startswith("/status/"):
            code = parse_size(route[len("/status/"):], 500)
            return self.simple(code, "status %d\n" % code)
        return self.simple(404, "not found\n")

    def file_size(self, route):
        size = parse_size(self.q("size"))
        if size is None:
            size = parse_size(route.rsplit("/", 1)[-1])
        return size

    def stream(self):
        rate = self.qi("kbps", 256) * 1024
        self.head(200, [("Content-Type", "application/octet-stream"), ("Transfer-Encoding", "chunked")])
        offset = 0
        piece = max(1024, rate // 20)
        limit = parse_size(self.q("size"), None)
        while limit is None or offset < limit:
            data = self.state.content.read(offset, piece)
            self.wfile.write(b"%x\r\n" % len(data) + data + b"\r\n")
            self.wfile.flush()
            self.sent += len(data)
            offset += len(data)
            time.sleep(0.05)
        self.wfile.write(b"0\r\n\r\n")

    def redirect(self, rest):
        mode, _, target = rest.partition("/")
        code = self.qi("code", 302)
        hops = self.qi("n", 1)
        extra = "&".join("%s=%s" % (k, quote(v[0])) for k, v in self.query.items() if k not in ("code", "n"))
        if mode == "loop":
            location = "/redirect/loop/x"
        elif hops > 1:
            location = "/redirect/%s/%s?n=%d&code=%d" % (mode, quote(target), hops - 1, code)
            if extra:
                location += "&" + extra
        else:
            path = "/" + quote(target) + ("?" + extra if extra else "")
            if mode == "abs":
                location = "http://" + self.origin() + path
            elif mode == "proto":
                location = "//" + self.origin() + path
            elif mode == "rel":
                location = "../" * (self.route.count("/") - 1) + path[1:]
            elif mode == "root":
                location = path
            else:
                return self.simple(400, "redirect mode must be abs, rel, root, proto or loop\n")
        self.simple(code, "redirect\n", headers=[("Location", location)])

    def auth_ok(self):
        wanted = self.q("auth")
        if not wanted:
            return True
        header = self.headers.get("Authorization", "")
        expected = "Basic " + base64.b64encode(wanted.encode("utf-8")).decode("ascii")
        if header == expected:
            return True
        self.simple(401, "login needed\n", headers=[("WWW-Authenticate", 'Basic realm="fetchjet-test"')])
        return False

    def file(self, route):
        size = self.file_size(route)
        if size is None:
            return self.simple(400, "no size in the name; use /file/10MB.bin or ?size=10MB\n")
        if not self.auth_ok():
            return
        forced = self.q("status")
        if forced:
            return self.simple(int(forced), "forced status\n")
        if self.q("robot"):
            return self.simple(200, "<html><body><h1>Are you a robot?</h1><p>Please verify you are human.</p></body></html>", content_type="text/html; charset=utf-8")
        fail = self.q("fail")
        if fail:
            count, _, code = fail.partition(":")
            if self.number <= int(count):
                headers = [("Retry-After", self.q("retryafter"))] if self.q("retryafter") else []
                return self.simple(int(code or 503), "failing request %d\n" % self.number, headers=headers)
        if self.command == "HEAD" and self.q("nohead"):
            return self.simple(405, "")
        cap = self.qi("maxconn")
        if not self.state.enter(self.key, cap):
            headers = [("Retry-After", self.q("retryafter"))] if self.q("retryafter") else []
            return self.simple(int(self.q("busy", 429)), "too many connections\n", headers=headers + [("Connection", "close")])
        self.entered = True

        base = self.qi("base")
        version = self.q("v", "1")
        flip_at = self.qi("flipat")
        if flip_at and self.number >= flip_at:
            version = "2"
        wrong_part = self.qi("wrongpart")
        if wrong_part and self.number == wrong_part:
            base += LINE
        etag = '"fj-%d-%s"' % (size, version)
        last_modified = LAST_MODIFIED if version == "1" else "Tue, 03 Nov 2026 10:00:00 GMT"
        name = self.q("name")
        headers = [("Content-Type", self.q("type", "application/octet-stream"))]
        if not self.q("noetag"):
            headers += [("ETag", etag), ("Last-Modified", last_modified)]
        if name:
            headers.append(("Content-Disposition", "attachment; filename*=UTF-8''" + quote(name)))
        if self.q("cookie"):
            headers.append(("Set-Cookie", self.q("cookie")))
        digest = self.q("digest")
        if digest:
            raw = bytes.fromhex(self.state.content.sha256(self.qi("base"), size))
            if digest == "bad":
                raw = bytes(b ^ 0xFF for b in raw)
            headers.append(("Digest", "sha-256=" + base64.b64encode(raw).decode("ascii")))
        if self.headers.get("If-None-Match") == etag:
            return self.head(304, headers, 0)

        start, end = 0, size - 1
        partial = False
        range_header = self.headers.get("Range")
        honour = bool(range_header) and not self.q("norange")
        if honour and self.headers.get("If-Range") not in (None, etag, last_modified):
            honour = False
        if honour:
            m = re.match(r"^bytes=(\d*)-(\d*)$", range_header.strip())
            if not m or (m.group(1) == "" and m.group(2) == ""):
                return self.simple(416, "", headers=[("Content-Range", "bytes */%d" % size)])
            if m.group(1) == "":
                start = max(0, size - int(m.group(2)))
            else:
                start = int(m.group(1))
                if m.group(2) != "":
                    end = min(size - 1, int(m.group(2)))
            if start >= size or start > end:
                return self.simple(416, "", headers=[("Content-Range", "bytes */%d" % size)])
            partial = True
        if not self.q("norange"):
            headers.append(("Accept-Ranges", "bytes"))
        length = end - start + 1
        if partial:
            headers.append(("Content-Range", "bytes %d-%d/%d" % (start, end, size)))
        chunked = bool(self.q("chunked"))
        no_length = bool(self.q("nolength"))
        if chunked:
            headers.append(("Transfer-Encoding", "chunked"))
        self.head(206 if partial else 200, headers, None if (chunked or no_length) else length, close=no_length)
        if self.command == "HEAD":
            return
        body_delay = self.qi("bodydelay")
        if body_delay > 0:
            self.wfile.flush()
            time.sleep(body_delay / 1000.0)
        cut = parse_size(self.q("cut"))
        cut_once = parse_size(self.q("cutonce"))
        if cut is None and cut_once is not None and self.number == 1:
            cut = cut_once
        throttle = self.qi("throttle", self.state.default_throttle) * 1024
        slow_req = self.q("slowreq")
        if slow_req:
            which, _, kb = slow_req.partition(":")
            if self.number == int(which):
                throttle = int(kb or 30) * 1024
        self.send_body(base + start, length, throttle, cut, chunked)

    def send_body(self, offset, length, rate, cut, chunked):
        piece = 64 * 1024 if rate <= 0 else max(1024, min(64 * 1024, rate // 10))
        began = time.time()
        done = 0
        while done < length:
            take = min(piece, length - done)
            if cut is not None and done + take > cut:
                take = cut - done
                if take <= 0:
                    self.wfile.flush()
                    raise Cut()
            data = self.state.content.read(offset + done, take)
            if chunked:
                self.wfile.write(b"%x\r\n" % len(data) + data + b"\r\n")
            else:
                self.wfile.write(data)
            done += take
            self.sent += take
            self.state.live(self.key, take)
            if rate > 0:
                self.wfile.flush()
                ahead = done / rate - (time.time() - began)
                if ahead > 0:
                    time.sleep(ahead)
        if chunked:
            self.wfile.write(b"0\r\n\r\n")

    def hls(self, route):
        segments = self.qi("segments", 8)
        seg_size = parse_size(self.q("segsize"), 256 * 1024)
        tail = "segments=%d&segsize=%d" % (segments, seg_size)
        for name in ("throttle", "fail", "maxconn", "busy"):
            if self.q(name):
                tail += "&%s=%s" % (name, quote(self.q(name)))
        if route == "/hls/master.m3u8":
            text = "#EXTM3U\n"
            for label, band, res in (("360p", 800000, "640x360"), ("720p", 2800000, "1280x720"), ("1080p", 5000000, "1920x1080")):
                text += "#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%s\n%s/index.m3u8?%s\n" % (band, res, label, tail)
            return self.simple(200, text, "application/vnd.apple.mpegurl")
        m = re.match(r"^/hls/([^/]+)/index\.m3u8$", route)
        if m:
            text = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:0\n"
            for i in range(segments):
                text += "#EXTINF:4.0,\nseg%d.ts?%s\n" % (i, tail)
            text += "#EXT-X-ENDLIST\n"
            return self.simple(200, text, "application/vnd.apple.mpegurl")
        m = re.match(r"^/hls/([^/]+)/seg(\d+)\.ts$", route)
        if m:
            index = int(m.group(2))
            if index >= segments:
                return self.simple(404, "no such segment\n")
            self.query["size"] = [str(seg_size)]
            self.query["base"] = [str(index * seg_size)]
            self.query["type"] = ["video/mp2t"]
            return self.file(route)
        return self.simple(404, "not found\n")


class Server(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True
    request_queue_size = 128


def make_server(port, log_path=None, throttle=0, quiet=True, cert=None, key=None):
    server = Server(("127.0.0.1", port), Handler)
    server.state = State(log_path, throttle, quiet)
    if cert:
        import ssl
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(cert, key or cert)
        server.socket = context.wrap_socket(server.socket, server_side=True)
    return server


def selftest():
    import http.client
    import urllib.request

    server = make_server(0)
    port = server.server_address[1]
    threading.Thread(target=server.serve_forever, daemon=True).start()
    base = "http://127.0.0.1:%d" % port
    fails = []

    def check(name, ok, detail=""):
        print(("PASS  " if ok else "FAIL  ") + name + (("  [" + str(detail) + "]") if detail else ""))
        if not ok:
            fails.append(name)

    def request(method, path, headers=None, read=True):
        conn = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
        conn.request(method, path, headers=headers or {})
        response = conn.getresponse()
        body = response.read() if read else b""
        return response, body, conn

    r, body, _ = request("GET", "/file/1MB.bin")
    sha = urllib.request.urlopen(base + "/sha256/1MB.bin").read().decode().strip()
    check("full file 200 with length and sha256", r.status == 200 and len(body) == 1048576 and hashlib.sha256(body).hexdigest() == sha)
    check("content is offset lines", body[:16] == b"000000000000000\n" and body[16:32] == b"000000000000010\n")
    r, part, _ = request("GET", "/file/1MB.bin", {"Range": "bytes=1000-1999"})
    check("range 206 with Content-Range", r.status == 206 and part == body[1000:2000] and r.getheader("Content-Range") == "bytes 1000-1999/1048576")
    r, part, _ = request("GET", "/file/1MB.bin", {"Range": "bytes=-100"})
    check("suffix range", r.status == 206 and part == body[-100:])
    r, part, _ = request("GET", "/file/1MB.bin", {"Range": "bytes=1048000-"})
    check("open range", r.status == 206 and part == body[1048000:])
    r, part, _ = request("GET", "/file/1MB.bin", {"Range": "bytes=2000000-"})
    check("unsatisfiable range 416", r.status == 416 and r.getheader("Content-Range") == "bytes */1048576")
    r, part, _ = request("HEAD", "/file/1MB.bin")
    check("HEAD has length, no body", r.status == 200 and r.getheader("Content-Length") == "1048576" and part == b"" and r.getheader("Accept-Ranges") == "bytes")
    r, part, _ = request("HEAD", "/file/1MB.bin?nohead=1")
    check("nohead answers 405", r.status == 405)
    r, part, _ = request("GET", "/file/1MB.bin?norange=1", {"Range": "bytes=0-99"})
    check("norange ignores Range (200, full)", r.status == 200 and len(part) == 1048576 and r.getheader("Accept-Ranges") is None)
    r, part, _ = request("GET", "/file/200KB.bin?chunked=1")
    check("chunked without Content-Length", r.status == 200 and r.getheader("Content-Length") is None and part == body[:204800])
    r, part, _ = request("GET", "/file/200KB.bin?nolength=1")
    check("no length, connection close", r.status == 200 and r.getheader("Content-Length") is None and part == body[:204800])
    t = time.time()
    r, part, _ = request("GET", "/file/200KB.bin?throttle=400")
    took = time.time() - t
    check("throttle 400 KB/s takes about 0.5 s for 200 KB", 0.35 < took < 1.2 and part == body[:204800], "%.2f s" % took)
    t = time.time()
    r, part, _ = request("GET", "/file/1KB.bin?delay=400")
    check("slow first byte", time.time() - t >= 0.4 and r.status == 200)
    try:
        r, part, _ = request("GET", "/file/1MB.bin?cut=100KB")
        ok = len(part) == 102400
    except (http.client.IncompleteRead, ConnectionError) as e:
        partial = getattr(e, "partial", b"")
        ok = len(partial) <= 102400
    check("mid-body disconnect after 100 KB", ok)
    try:
        request("GET", "/file/1MB.bin?cutonce=50KB&key=once")
        first_ok = False
    except (http.client.IncompleteRead, ConnectionError):
        first_ok = True
    r, part, _ = request("GET", "/file/1MB.bin?cutonce=50KB&key=once")
    check("cutonce cuts only the first request", first_ok and len(part) == 1048576)
    r, part, _ = request("GET", "/file/1KB.bin?auth=site:pw")
    check("basic auth 401 without login", r.status == 401 and "Basic" in r.getheader("WWW-Authenticate", ""))
    r, part, _ = request("GET", "/file/1KB.bin?auth=site:pw", {"Authorization": "Basic " + base64.b64encode(b"site:pw").decode()})
    check("basic auth 200 with login", r.status == 200 and len(part) == 1024)
    r, part, _ = request("GET", "/file/1KB.bin?fail=2:503&key=f1")
    r2, _, _ = request("GET", "/file/1KB.bin?fail=2:503&key=f1")
    r3, _, _ = request("GET", "/file/1KB.bin?fail=2:503&key=f1")
    check("fail=2:503 fails twice then works", (r.status, r2.status, r3.status) == (503, 503, 200))
    r, part, _ = request("GET", "/file/1KB.bin", {"If-None-Match": '"fj-1024-1"'})
    check("ETag and 304", r.status == 304)
    r, part, _ = request("GET", "/file/1MB.bin", {"Range": "bytes=10-19", "If-Range": '"other"'})
    check("If-Range mismatch gives the full file", r.status == 200 and len(part) == 1048576)
    r, part, _ = request("GET", "/file/1MB.bin", {"Range": "bytes=10-19", "If-Range": '"fj-1048576-1"'})
    check("If-Range match gives 206", r.status == 206 and part == body[10:20])
    r, part, _ = request("GET", "/file/1MB.bin?digest=good")
    good_digest = "sha-256=" + base64.b64encode(hashlib.sha256(body).digest()).decode()
    r2, _, _ = request("GET", "/file/1MB.bin?digest=bad", {"Range": "bytes=0-9"})
    check("digest=good sends the SHA-256 as Digest, digest=bad another one",
          r.getheader("Digest") == good_digest and r2.getheader("Digest") not in (None, good_digest))
    for mode in ("abs", "rel", "root", "proto"):
        r, part, _ = request("GET", "/redirect/%s/file/2KB.bin" % mode)
        location = r.getheader("Location", "")
        final = urllib.request.urlopen(base + "/redirect/%s/file/2KB.bin" % mode).read()
        check("redirect %s" % mode, r.status == 302 and len(final) == 2048, location)
    r, part, _ = request("GET", "/redirect/abs/file/2KB.bin?n=3&code=307")
    check("redirect chain keeps code and counts down", r.status == 307 and "n=2" in r.getheader("Location", ""))
    r, part, _ = request("GET", "/echo", {"Cookie": "a=1", "Referer": "http://x/", "User-Agent": "UA-1", "X-Test": "v"})
    echoed = json.loads(part)
    check("echo returns request headers", echoed["headers"].get("Cookie") == "a=1" and echoed["headers"].get("User-Agent") == "UA-1")
    name = quote("تقرير نهائي.pdf")
    r, part, _ = request("GET", "/file/" + name + "?size=3KB&name=" + name)
    check("Arabic path and Content-Disposition", r.status == 200 and len(part) == 3072 and name in r.getheader("Content-Disposition", ""))

    holders = []
    for _ in range(3):
        try:
            holders.append(request("GET", "/file/8MB.bin?maxconn=1&busy=429&throttle=200&key=cap", read=False))
        except Exception as e:
            holders.append((None, None, None))
    statuses = sorted(h[0].status for h in holders if h[0] is not None)
    check("maxconn=1 answers 429 to the extra connections", statuses == [200, 429, 429], statuses)
    for h in holders:
        if h[2] is not None:
            h[2].close()
    time.sleep(0.3)
    stats = json.loads(urllib.request.urlopen(base + "/stats?of=cap").read())
    check("stats keep the maximum of parallel connections", stats.get("maxActive") == 1 and stats.get("requests") == 3, stats)

    master = urllib.request.urlopen(base + "/hls/master.m3u8?segments=4&segsize=64KB").read().decode()
    check("hls master lists variants", master.count("index.m3u8") == 3)
    media = urllib.request.urlopen(base + "/hls/720p/index.m3u8?segments=4&segsize=64KB").read().decode()
    joined = b""
    for line in media.splitlines():
        if line.startswith("seg"):
            joined += urllib.request.urlopen(base + "/hls/720p/" + line).read()
    whole = urllib.request.urlopen(base + "/sha256/x?size=262144").read().decode().strip()
    check("hls segments join to the plain file", media.count("#EXTINF") == 4 and hashlib.sha256(joined).hexdigest() == whole)

    r, _, conn = request("GET", "/stream?kbps=64", read=False)
    first = r.read(4096)
    conn.close()
    check("endless stream starts", r.status == 200 and len(first) == 4096)
    conn = http.client.HTTPConnection("127.0.0.1", port, timeout=1)
    conn.request("GET", "/stall")
    try:
        conn.getresponse()
        stalled = False
    except (socket.timeout, TimeoutError):
        stalled = True
    conn.close()
    check("stall never answers", stalled)

    t = time.time()
    h = hashlib.sha256()
    with urllib.request.urlopen(base + "/file/64MB.bin") as response:
        while True:
            data = response.read(1 << 20)
            if not data:
                break
            h.update(data)
    took = time.time() - t
    sha64 = urllib.request.urlopen(base + "/sha256/64MB.bin").read().decode().strip()
    check("64 MB served and hashed", h.hexdigest() == sha64, "%.1f s, %.0f MB/s" % (took, 64 / took))
    server.shutdown()
    print("selftest: %d failed" % len(fails))
    return 1 if fails else 0


def main():
    parser = argparse.ArgumentParser(description="FetchJet local HTTP test server (127.0.0.1 only)")
    parser.add_argument("--port", type=int, default=18200)
    parser.add_argument("--log", default=None, help="append one line per request to this file")
    parser.add_argument("--throttle", type=int, default=0, help="default speed limit per connection in KB/s (0 = none)")
    parser.add_argument("--verbose", action="store_true", help="also print each request")
    parser.add_argument("--cert", default=None, help="PEM certificate: serve HTTPS instead of HTTP")
    parser.add_argument("--key", default=None, help="PEM private key (default: inside --cert)")
    parser.add_argument("--pidfile", default=None, help="write the process id to this file")
    parser.add_argument("--selftest", action="store_true", help="run the built-in checks on a free port and exit")
    args = parser.parse_args()
    if args.selftest:
        sys.exit(selftest())
    server = make_server(args.port, args.log, args.throttle, not args.verbose, args.cert, args.key)
    if args.pidfile:
        import os
        with open(args.pidfile, "w") as f:
            f.write(str(os.getpid()))
    print("fetchjet test server on %s://127.0.0.1:%d" % ("https" if args.cert else "http", server.server_address[1]))
    sys.stdout.flush()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
