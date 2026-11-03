import base64, socket, socketserver, struct, sys, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

USER, PASS = 'fj', 'secret'
LOG = open(sys.argv[1] if len(sys.argv) > 1 else 'proxies.log', 'a', buffering=1)


def log(*a):
    LOG.write(' '.join(str(x) for x in a) + '\n')


def pipe(a, b):
    try:
        while True:
            d = a.recv(65536)
            if not d:
                break
            b.sendall(d)
    except Exception:
        pass
    finally:
        for s in (a, b):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except Exception:
                pass


def relay(c, r):
    t = threading.Thread(target=pipe, args=(r, c), daemon=True)
    t.start()
    pipe(c, r)
    t.join()


class HttpProxy(socketserver.StreamRequestHandler):
    def handle(self):
        head = b''
        while b'\r\n\r\n' not in head:
            d = self.connection.recv(4096)
            if not d:
                return
            head += d
        hdr, rest = head.split(b'\r\n\r\n', 1)
        lines = hdr.decode('latin1').split('\r\n')
        method, target, _ = lines[0].split(' ', 2)
        h = {l.split(':', 1)[0].lower(): l.split(':', 1)[1].strip() for l in lines[1:] if ':' in l}
        ok = h.get('proxy-authorization') == 'Basic ' + base64.b64encode(f'{USER}:{PASS}'.encode()).decode()
        if not ok:
            log('HTTP-PROXY 407', method, target)
            self.connection.sendall(b'HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm="fj"\r\nContent-Length: 0\r\n\r\n')
            return
        log('HTTP-PROXY OK', method, target)
        if method == 'CONNECT':
            host, port = target.rsplit(':', 1)
            r = socket.create_connection((host, int(port)))
            self.connection.sendall(b'HTTP/1.1 200 Connection established\r\n\r\n')
            relay(self.connection, r)
        else:
            from urllib.parse import urlsplit
            u = urlsplit(target)
            r = socket.create_connection((u.hostname, u.port or 80))
            path = u.path + ('?' + u.query if u.query else '')
            out = [f'{method} {path} HTTP/1.1'] + [l for l in lines[1:] if not l.lower().startswith(('proxy-', 'connection'))] + ['Connection: close']
            r.sendall(('\r\n'.join(out) + '\r\n\r\n').encode('latin1') + rest)
            relay(self.connection, r)


class Socks5(socketserver.StreamRequestHandler):
    def handle(self):
        c = self.connection
        ver, n = c.recv(2)
        methods = c.recv(n)
        if 2 not in methods:
            log('SOCKS no user/pass method offered', list(methods))
            c.sendall(b'\x05\xff'); return
        c.sendall(b'\x05\x02')
        c.recv(1); ul = c.recv(1)[0]; u = c.recv(ul).decode(); pl = c.recv(1)[0]; p = c.recv(pl).decode()
        if (u, p) != (USER, PASS):
            log('SOCKS bad login', u); c.sendall(b'\x01\x01'); return
        c.sendall(b'\x01\x00')
        _, cmd, _, atyp = c.recv(4)
        if atyp == 1:
            host = socket.inet_ntoa(c.recv(4))
        elif atyp == 3:
            host = c.recv(c.recv(1)[0]).decode()
        else:
            host = socket.inet_ntop(socket.AF_INET6, c.recv(16))
        port = struct.unpack('>H', c.recv(2))[0]
        log('SOCKS OK', host, port)
        r = socket.create_connection((host, port))
        c.sendall(b'\x05\x00\x00\x01' + socket.inet_aton('0.0.0.0') + b'\x00\x00')
        relay(c, r)


class BasicAuthSite(BaseHTTPRequestHandler):
    def do_GET(self):
        ok = self.headers.get('Authorization') == 'Basic ' + base64.b64encode(b'site:pw').decode()
        log('SITE', self.path, 'auth-ok' if ok else 'no-auth')
        if not ok:
            self.send_response(401); self.send_header('WWW-Authenticate', 'Basic realm="x"'); self.send_header('Content-Length', '0'); self.end_headers(); return
        body = b'x' * 3_000_000
        self.send_response(200); self.send_header('Content-Length', str(len(body))); self.send_header('Content-Type', 'application/octet-stream'); self.end_headers(); self.wfile.write(body)

    do_HEAD = do_GET

    def log_message(self, *a):
        pass


class T(socketserver.ThreadingTCPServer):
    daemon_threads = True
    allow_reuse_address = True


if __name__ == '__main__':
    servers = [T(('127.0.0.1', 18080), HttpProxy), T(('127.0.0.1', 11080), Socks5), ThreadingHTTPServer(('127.0.0.1', 18090), BasicAuthSite)]
    for s in servers:
        threading.Thread(target=s.serve_forever, daemon=True).start()
    log('ready')
    threading.Event().wait()
