import hashlib
import os
import socket
import sys
import time
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, SERVER, server_sha256

SOCKS_LOGIN = 11081
SOCKS_OPEN = 11082
MAPPED = "files.fjtest.example"
HTTP_PORT = 18200


def through(port, url, timeout=120):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({"http": "http://127.0.0.1:%d" % port}))
    started = time.time()
    digest = hashlib.sha256()
    size = 0
    status = 0
    try:
        with opener.open(url, timeout=timeout) as response:
            status = response.status
            while True:
                chunk = response.read(65536)
                if not chunk:
                    break
                digest.update(chunk)
                size += len(chunk)
    except Exception as failure:
        return {"status": getattr(failure, "code", 0), "size": size, "seconds": time.time() - started, "sha": "", "error": repr(failure)}
    return {"status": status, "size": size, "seconds": time.time() - started, "sha": digest.hexdigest()}


def raw(port, request, read_until_close=True, timeout=20):
    connection = socket.create_connection(("127.0.0.1", port), timeout=timeout)
    connection.sendall(request)
    data = b""
    try:
        while True:
            chunk = connection.recv(65536)
            if not chunk:
                break
            data += chunk
            if not read_until_close and b"\r\n\r\n" in data:
                break
    except socket.timeout:
        pass
    return connection, data


def run():
    core = Core("proxylimit")
    try:
        print("unlimited, plain http")
        port = int(core.debug("proxyStart", {"limit": "0"}))
        core.check("the proxy listens on a loopback port", port > 0, port)
        got = through(port, SERVER + "/file/8MB.bin?key=pl-free")
        core.check("8 MB through the proxy match the server", got["status"] == 200 and got["sha"] == server_sha256("8MB.bin"), got)
        print("    %.1f MB/s" % (got["size"] / 1048576.0 / max(got["seconds"], 0.001)))

        print("CONNECT tunnel")
        connection, head = raw(port, b"CONNECT 127.0.0.1:%d HTTP/1.1\r\nHost: 127.0.0.1:%d\r\n\r\n" % (HTTP_PORT, HTTP_PORT), read_until_close=False)
        core.check("CONNECT is answered with 200", head.startswith(b"HTTP/1.1 200"), head[:80])
        connection.sendall(b"GET /file/1KB.bin HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
        body = b""
        while True:
            chunk = connection.recv(65536)
            if not chunk:
                break
            body += chunk
        connection.close()
        core.check("a request inside the tunnel gets the file", body.startswith(b"HTTP/1.1 200") and body.endswith(b"\r\n\r\n") is False and len(body) > 1024, body[:80])

        print("bad request")
        connection, head = raw(port, b"NONSENSE\r\n\r\n")
        connection.close()
        core.check("a broken request line gets 400", head.startswith(b"HTTP/1.1 400"), head[:80])

        print("512 KB/s")
        port = int(core.debug("proxyStart", {"limit": str(512 * 1024)}))
        got = through(port, SERVER + "/file/4MB.bin?key=pl-limit")
        rate = got["size"] / 1024.0 / max(got["seconds"], 0.001)
        core.check("4 MB at 512 KB/s match the server", got["status"] == 200 and got["sha"] == server_sha256("4MB.bin"), got)
        core.check("the limit holds (440-560 KB/s)", 440 <= rate <= 560, "%.1f KB/s in %.1f s" % (rate, got["seconds"]))
        print("    %.1f KB/s in %.1f s" % (rate, got["seconds"]))
        stopped = core.debug("proxyStop")
        core.check("the proxy counted the bytes and closed", isinstance(stopped, dict) and stopped.get("downloaded", 0) >= 4 * 1048576 and stopped.get("open") is False, stopped)
        refused = False
        try:
            socket.create_connection(("127.0.0.1", port), timeout=3).close()
        except OSError:
            refused = True
        core.check("the closed proxy no longer accepts", refused)

        print("SOCKS5 upstream with a login (name only the SOCKS proxy knows)")
        port = int(core.debug("proxyStart", {"limit": "0", "socksPort": str(SOCKS_LOGIN), "user": "fj", "password": "secret"}))
        got = through(port, "http://%s:%d/file/2MB.bin?key=pl-socks" % (MAPPED, HTTP_PORT))
        core.check("2 MB through the SOCKS5 upstream match the server", got["status"] == 200 and got["sha"] == server_sha256("2MB.bin"), got)
        port = int(core.debug("proxyStart", {"limit": "0", "socksPort": str(SOCKS_LOGIN), "user": "fj", "password": "wrong"}))
        got = through(port, "http://%s:%d/file/1MB.bin?key=pl-socks-wrong" % (MAPPED, HTTP_PORT))
        core.check("a wrong SOCKS5 password gives 407", got["status"] == 407, got)
        port = int(core.debug("proxyStart", {"limit": "0", "socksPort": str(SOCKS_OPEN)}))
        got = through(port, "http://%s:%d/file/1MB.bin?key=pl-socks-open" % (MAPPED, HTTP_PORT))
        core.check("1 MB through the open SOCKS5 upstream match the server", got["status"] == 200 and got["sha"] == server_sha256("1MB.bin"), got)
        core.debug("proxyStop")
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
