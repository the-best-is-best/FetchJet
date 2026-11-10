import argparse
import base64
import select
import socket
import socketserver
import struct
import sys
import threading
import time

CONFIG = {"user": None, "password": None, "map": {}, "log": None, "lock": threading.Lock(), "counts": {}}


def log(*parts):
    line = time.strftime("%H:%M:%S ") + " ".join(str(p) for p in parts)
    with CONFIG["lock"]:
        key = " ".join(str(p) for p in parts[:2])
        CONFIG["counts"][key] = CONFIG["counts"].get(key, 0) + 1
        if CONFIG["log"]:
            CONFIG["log"].write(line + "\n")
            CONFIG["log"].flush()


def resolve(host):
    return CONFIG["map"].get(host.lower(), host)


def relay(a, b):
    sockets = [a, b]
    try:
        while True:
            ready, _, broken = select.select(sockets, [], sockets, 120)
            if broken or not ready:
                return
            for s in ready:
                data = s.recv(65536)
                if not data:
                    return
                (b if s is a else a).sendall(data)
    except OSError:
        return
    finally:
        for s in sockets:
            try:
                s.close()
            except OSError:
                pass


def read_head(conn):
    data = b""
    while b"\r\n\r\n" not in data:
        chunk = conn.recv(65536)
        if not chunk:
            return None, b""
        data += chunk
        if len(data) > 1 << 20:
            return None, b""
    head, _, rest = data.partition(b"\r\n\r\n")
    return head.decode("latin-1"), rest


class HttpProxy(socketserver.BaseRequestHandler):
    def handle(self):
        conn = self.request
        head, rest = read_head(conn)
        if head is None:
            return
        lines = head.split("\r\n")
        try:
            method, target, version = lines[0].split(" ", 2)
        except ValueError:
            return
        headers = [line.split(":", 1) for line in lines[1:] if ":" in line]
        lookup = {k.strip().lower(): v.strip() for k, v in headers}
        if CONFIG["user"] is not None:
            expected = "Basic " + base64.b64encode(("%s:%s" % (CONFIG["user"], CONFIG["password"])).encode()).decode()
            if lookup.get("proxy-authorization") != expected:
                log("HTTP-PROXY", "407", method, target)
                conn.sendall(b"HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"fj-test\"\r\n"
                             b"Content-Length: 0\r\nConnection: close\r\n\r\n")
                return
        try:
            if method.upper() == "CONNECT":
                host, _, port = target.rpartition(":")
                upstream = socket.create_connection((resolve(host.strip("[]")), int(port)), timeout=20)
                log("HTTP-PROXY", "OK", "CONNECT", target)
                conn.sendall(b"HTTP/1.1 200 Connection established\r\n\r\n")
                if rest:
                    upstream.sendall(rest)
            else:
                if "://" not in target:
                    log("HTTP-PROXY", "400", method, target)
                    conn.sendall(b"HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                    return
                after = target.split("://", 1)[1]
                authority, slash, path = after.partition("/")
                host, _, port = authority.partition(":")
                upstream = socket.create_connection((resolve(host), int(port or 80)), timeout=20)
                log("HTTP-PROXY", "OK", method, target)
                out = ["%s /%s %s" % (method, path, version)]
                for name, value in headers:
                    if name.strip().lower() in ("proxy-authorization", "proxy-connection", "connection"):
                        continue
                    out.append("%s:%s" % (name, value))
                out.append("Connection: close")
                upstream.sendall(("\r\n".join(out) + "\r\n\r\n").encode("latin-1") + rest)
        except OSError as e:
            log("HTTP-PROXY", "502", method, target, e)
            try:
                conn.sendall(b"HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            except OSError:
                pass
            return
        relay(conn, upstream)


def read_exact(conn, count):
    data = b""
    while len(data) < count:
        chunk = conn.recv(count - len(data))
        if not chunk:
            raise OSError("closed")
        data += chunk
    return data


class SocksProxy(socketserver.BaseRequestHandler):
    def handle(self):
        conn = self.request
        try:
            version, count = read_exact(conn, 2)
            methods = read_exact(conn, count)
            if version != 5:
                log("SOCKS", "BAD-VERSION", version)
                return
            user = ""
            if CONFIG["user"] is not None:
                if 2 not in methods:
                    log("SOCKS", "NO-LOGIN-OFFERED")
                    conn.sendall(b"\x05\xff")
                    return
                conn.sendall(b"\x05\x02")
                _, ulen = read_exact(conn, 2)
                user = read_exact(conn, ulen).decode("utf-8", "replace")
                plen = read_exact(conn, 1)[0]
                password = read_exact(conn, plen).decode("utf-8", "replace")
                if (user, password) != (CONFIG["user"], CONFIG["password"]):
                    log("SOCKS", "AUTH-FAIL", user)
                    conn.sendall(b"\x01\x01")
                    return
                conn.sendall(b"\x01\x00")
            else:
                conn.sendall(b"\x05\x00")
            _, command, _, kind = read_exact(conn, 4)
            if kind == 1:
                host = socket.inet_ntoa(read_exact(conn, 4))
                how = "ip"
            elif kind == 3:
                host = read_exact(conn, read_exact(conn, 1)[0]).decode("idna")
                how = "name"
            elif kind == 4:
                host = socket.inet_ntop(socket.AF_INET6, read_exact(conn, 16))
                how = "ip6"
            else:
                return
            port = struct.unpack("!H", read_exact(conn, 2))[0]
            if command != 1:
                conn.sendall(b"\x05\x07\x00\x01" + b"\x00" * 6)
                return
            try:
                upstream = socket.create_connection((resolve(host), port), timeout=20)
            except OSError as e:
                log("SOCKS", "FAIL", "%s:%d" % (host, port), e)
                conn.sendall(b"\x05\x05\x00\x01" + b"\x00" * 6)
                return
            log("SOCKS", "OK", "%s:%d" % (host, port), how, "user=" + user)
            conn.sendall(b"\x05\x00\x00\x01" + b"\x00" * 6)
        except OSError:
            return
        relay(conn, upstream)


class Threaded(socketserver.ThreadingTCPServer):
    daemon_threads = True
    allow_reuse_address = True


def main():
    parser = argparse.ArgumentParser(description="FetchJet local test proxies: HTTP (CONNECT + plain) and SOCKS5, 127.0.0.1 only")
    parser.add_argument("--http-port", type=int, default=18081)
    parser.add_argument("--socks-port", type=int, default=11081)
    parser.add_argument("--user", default=None, help="require this login (HTTP Basic / SOCKS5 user+password)")
    parser.add_argument("--password", default="")
    parser.add_argument("--map", action="append", default=[], help="name=address: connect to address when the client asks for name")
    parser.add_argument("--log", default=None)
    parser.add_argument("--pidfile", default=None, help="write the process id to this file")
    args = parser.parse_args()
    CONFIG["user"] = args.user
    CONFIG["password"] = args.password
    for item in args.map:
        name, _, address = item.partition("=")
        CONFIG["map"][name.lower()] = address
    if args.log:
        CONFIG["log"] = open(args.log, "a", encoding="utf-8")
    http = Threaded(("127.0.0.1", args.http_port), HttpProxy)
    if args.pidfile:
        import os
        with open(args.pidfile, "w") as f:
            f.write(str(os.getpid()))
    socks = Threaded(("127.0.0.1", args.socks_port), SocksProxy)
    threading.Thread(target=http.serve_forever, daemon=True).start()
    print("test proxies: http 127.0.0.1:%d, socks5 127.0.0.1:%d, login %s" % (args.http_port, args.socks_port, "required" if args.user else "not required"))
    sys.stdout.flush()
    try:
        socks.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
