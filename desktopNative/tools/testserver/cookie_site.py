import argparse
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SIZE = 3 * 1024 * 1024
COOKIE = "sess=abc123"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("%s %s cookie=%s" % (self.command, self.path, self.headers.get("Cookie")), flush=True)

    def page(self, text, extra=None):
        body = ("<html><body><h2>%s</h2><p>FetchJet cookie test page</p><p><a id=\"get\" href=\"/secret.bin\" style=\"font-size:40px\">Get secret file</a></p></body></html>" % text).encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or []):
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith("/login"):
            self.page("Signed in. You can press Done now.", [("Set-Cookie", COOKIE + "; Path=/; HttpOnly")])
            return
        if self.path.startswith("/secret.bin"):
            if COOKIE not in (self.headers.get("Cookie") or ""):
                body = b"forbidden"
                self.send_response(403)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
            start, end = 0, SIZE - 1
            match = re.match(r"bytes=(\d+)-(\d*)", self.headers.get("Range", ""))
            status = 200
            if match:
                start = int(match.group(1))
                end = int(match.group(2)) if match.group(2) else SIZE - 1
                status = 206
            length = end - start + 1
            self.send_response(status)
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Disposition", "attachment; filename=\"secret.bin\"")
            self.send_header("Content-Length", str(length))
            if status == 206:
                self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, SIZE))
            self.end_headers()
            self.wfile.write(b"x" * length)
            return
        self.page("Sign in page", [])


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=18300)
    args = parser.parse_args()
    ThreadingHTTPServer(("0.0.0.0", args.port), Handler).serve_forever()
