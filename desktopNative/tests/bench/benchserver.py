import argparse
import importlib.util
import json
import mmap
import os
import sys
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
SERVER_PY = os.path.join(HERE, "..", "..", "tools", "testserver", "server.py")

spec = importlib.util.spec_from_file_location("fjserver", SERVER_PY)
fjserver = importlib.util.module_from_spec(spec)
sys.modules["fjserver"] = fjserver
spec.loader.exec_module(fjserver)

EVENTS = []
EVENTS_LOCK = threading.Lock()


def install_fast_content(path):
    handle = open(path, "rb")
    view = mmap.mmap(handle.fileno(), 0, access=mmap.ACCESS_READ)
    size = len(view)

    def read(self, offset, length):
        end = min(offset + length, size)
        if offset >= size:
            raise ValueError("content file too small: %d > %d" % (offset + length, size))
        return view[offset:end]

    fjserver.Content.read = read
    return size


def install_sendfile(path):
    Handler = fjserver.Handler
    original = Handler.send_body

    def send_body(self, offset, length, rate, cut, chunked):
        if rate > 0 or cut is not None or chunked:
            return original(self, offset, length, rate, cut, chunked)
        self.wfile.flush()
        with open(path, "rb") as source:
            sent = self.connection.sendfile(source, offset, length)
        self.sent += sent
        self.state.live(self.key, sent)

    Handler.send_body = send_body


def install_events():
    Handler = fjserver.Handler
    original_dispatch = Handler.dispatch
    original_send_body = Handler.send_body

    def dispatch(self):
        self.bench_arrival = time.time()
        self.bench_body = None
        route = self.route
        if route == "/bench/events":
            with EVENTS_LOCK:
                data = list(EVENTS)
            wanted = self.q("key")
            if wanted:
                data = [e for e in data if e["key"] == wanted]
            return self.json(data)
        if route == "/bench/clear":
            with EVENTS_LOCK:
                EVENTS.clear()
            self.state.reset()
            return self.json({"cleared": True})
        try:
            return original_dispatch(self)
        finally:
            with EVENTS_LOCK:
                EVENTS.append({
                    "key": self.key, "port": self.client_address[1], "method": self.command,
                    "range": self.headers.get("Range", "-"), "arrival": self.bench_arrival,
                    "body": self.bench_body, "end": time.time(), "sent": self.sent, "status": self.status,
                })

    def send_body(self, offset, length, rate, cut, chunked):
        self.bench_body = time.time()
        return original_send_body(self, offset, length, rate, cut, chunked)

    Handler.dispatch = dispatch
    Handler.send_body = send_body


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=18290)
    parser.add_argument("--content", default=None, help="pre-generated content file; omitted = the stock generator")
    parser.add_argument("--throttle", type=int, default=0)
    parser.add_argument("--pidfile", default=None)
    parser.add_argument("--sendfile", action="store_true", help="serve unthrottled bodies with socket.sendfile (TransmitFile) from --content")
    parser.add_argument("--generate", default=None, help="write this many bytes of the stock content stream to --content and exit")
    args = parser.parse_args()
    if args.generate:
        total = int(args.generate)
        content = fjserver.Content()
        with open(args.content, "wb") as out:
            index = 0
            while index * fjserver.CHUNK < total:
                out.write(content.chunk(index))
                index += 1
        print("wrote", os.path.getsize(args.content))
        return
    if args.content:
        install_fast_content(args.content)
    if args.sendfile:
        install_sendfile(args.content)
    install_events()
    server = fjserver.make_server(args.port, None, args.throttle, True)
    if args.pidfile:
        with open(args.pidfile, "w") as f:
            f.write(str(os.getpid()))
    print("bench server on 127.0.0.1:%d content=%s" % (args.port, args.content or "stock"))
    sys.stdout.flush()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
