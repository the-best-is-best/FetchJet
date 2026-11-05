"""Cookie / redirect test server for FetchJet (TESTING.md, "Cookies and redirects").

  python3 scripts/security-test/redirect_server.py /tmp/redirect.log

Port 18300 on 127.0.0.1. Every request is written to the log with its Host, Cookie and
Authorization headers.
  /r<n>  -> 302 to http://localhost:18300/c<n>.bin  (another site: the Cookie must NOT follow)
  /s<n>  -> 302 to http://127.0.0.1:18300/k<n>.bin  (same site: the Cookie stays)
  anything else -> a 2 KB file (Range supported)
"""
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = open(sys.argv[1] if len(sys.argv) > 1 else "redirect.log", "a", buffering=1)
BODY = b"x" * 2048


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_HEAD(self):
        self.do_GET(head=True)

    def do_GET(self, head=False):
        LOG.write(f"{time.strftime('%H:%M:%S')} {self.command} host={self.headers.get('Host')} "
                  f"path={self.path} cookie={self.headers.get('Cookie')} "
                  f"auth={self.headers.get('Authorization')}\n")
        path = self.path.split("?")[0]
        if path.startswith("/r") or path.startswith("/s"):
            target = ("http://localhost:18300/c" if path.startswith("/r")
                      else "http://127.0.0.1:18300/k") + path[2:] + ".bin"
            self.send_response(302)
            self.send_header("Location", target)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        rng = self.headers.get("Range")
        if rng:
            a, b = rng.split("=")[1].split("-")
            a = int(a)
            b = min(int(b) if b else len(BODY) - 1, len(BODY) - 1)
            self.send_response(206)
            self.send_header("Content-Range", f"bytes {a}-{b}/{len(BODY)}")
            data = BODY[a:b + 1]
        else:
            self.send_response(200)
            data = BODY
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Content-Type", "application/octet-stream")
        self.end_headers()
        if not head:
            self.wfile.write(data)


ThreadingHTTPServer(("127.0.0.1", 18300), Handler).serve_forever()
