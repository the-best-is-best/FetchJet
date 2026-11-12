import argparse
import hashlib
import os
import socket
import ssl
import sys
import threading
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from server import Content, parse_size  # noqa: E402

MDTM = "20261102100000"


class State:
    def __init__(self, args):
        self.args = args
        self.content = Content()
        self.lock = threading.Lock()
        self.logins = 0
        self.active = 0
        self.max_active = 0
        self.log_file = open(args.log, "a", encoding="utf-8") if args.log else None
        self.commands = []
        self.rests = []
        self.retrs = 0
        self.tls_data = 0

    def log(self, line):
        stamp = time.strftime("%H:%M:%S ")
        with self.lock:
            self.commands.append(line)
            if self.log_file:
                self.log_file.write(stamp + line + "\n")
                self.log_file.flush()


def file_size(path):
    name = path.rstrip("/").rsplit("/", 1)[-1]
    if path.endswith("/") or "." not in name:
        return None
    return parse_size(name, None)


class Session(threading.Thread):
    def __init__(self, state, conn, implicit_ctx=None):
        super().__init__(daemon=True)
        self.state = state
        self.conn = conn
        self.implicit_ctx = implicit_ctx
        self.ctx = state.ctx
        self.user = None
        self.authed = False
        self.tls = False
        self.prot_private = False
        self.rest = 0
        self.passive = None
        self.cwd = ""
        self.out = None
        self.rfile = None

    def send(self, text):
        self.conn.sendall((text + "\r\n").encode("utf-8"))

    def attach(self):
        self.rfile = self.conn.makefile("rb")

    def run(self):
        args = self.state.args
        with self.state.lock:
            self.state.active += 1
            self.state.max_active = max(self.state.max_active, self.state.active)
            over = args.maxconn and self.state.active > args.maxconn
        try:
            if self.implicit_ctx is not None:
                self.conn = self.implicit_ctx.wrap_socket(self.conn, server_side=True)
                self.tls = True
            self.attach()
            if over:
                self.send("421 Too many connections from your address")
                return
            self.send("220 FetchJet test FTP server")
            while True:
                raw = self.rfile.readline()
                if not raw:
                    return
                line = raw.decode("utf-8", "replace").rstrip("\r\n")
                if not line:
                    continue
                cmd, _, arg = line.partition(" ")
                cmd = cmd.upper()
                self.state.log(("PASS ****" if cmd == "PASS" else line))
                if cmd == "QUIT":
                    self.send("221 Bye")
                    return
                handler = getattr(self, "do_" + cmd, None)
                if handler is None:
                    self.send("502 Command not implemented")
                    continue
                handler(arg)
        except (OSError, ssl.SSLError) as error:
            self.state.log("CONTROL ERROR " + repr(error))
        finally:
            with self.state.lock:
                self.state.active -= 1
            try:
                self.conn.close()
            except OSError:
                pass

    def need_login(self):
        if not self.authed:
            self.send("530 Please login with USER and PASS")
            return True
        return False

    def do_USER(self, arg):
        self.user = arg
        if self.state.args.anonymous and arg.lower() in ("anonymous", "ftp"):
            self.send("331 Anonymous login ok, send your email as the password")
        else:
            self.send("331 Password required")

    def do_PASS(self, arg):
        args = self.state.args
        ok = (self.user == args.user and arg == args.password) or (args.anonymous and (self.user or "").lower() in ("anonymous", "ftp"))
        if args.require_tls and not self.tls:
            self.send("530 Login requires TLS (AUTH TLS first)")
            return
        if ok:
            self.authed = True
            with self.state.lock:
                self.state.logins += 1
            self.send("230 Login ok")
        else:
            self.send("530 Login incorrect")

    def do_AUTH(self, arg):
        if arg.upper() not in ("TLS", "SSL") or self.ctx is None:
            self.send("504 Not supported")
            return
        self.send("234 Proceed with negotiation")
        self.conn = self.ctx.wrap_socket(self.conn, server_side=True)
        self.tls = True
        self.attach()

    def do_PBSZ(self, arg):
        self.send("200 PBSZ=0")

    def do_PROT(self, arg):
        self.prot_private = arg.upper() == "P"
        self.send("200 Protection level set")

    def do_SYST(self, arg):
        self.send("215 UNIX Type: L8")

    def do_FEAT(self, arg):
        lines = ["211-Features:", " UTF8", " SIZE", " MDTM", " EPSV", " PASV"]
        if not self.state.args.no_rest:
            lines.append(" REST STREAM")
        if self.ctx is not None:
            lines += [" AUTH TLS", " PBSZ", " PROT"]
        lines.append("211 End")
        for line in lines:
            self.send(line)

    def do_OPTS(self, arg):
        self.send("200 OK")

    def do_NOOP(self, arg):
        self.send("200 OK")

    def do_TYPE(self, arg):
        self.send("200 Type set")

    def do_PWD(self, arg):
        self.send('257 "/"')

    def do_CWD(self, arg):
        if self.need_login():
            return
        if arg.endswith("/") or file_size(arg) is None:
            self.send("250 OK")
        else:
            self.send("550 Not a directory")

    def do_CDUP(self, arg):
        self.send("250 OK")

    def do_SIZE(self, arg):
        if self.need_login():
            return
        size = file_size(arg)
        if size is None:
            self.send("550 No such file")
        else:
            self.send("213 %d" % size)

    def do_MDTM(self, arg):
        if self.need_login():
            return
        if file_size(arg) is None:
            self.send("550 No such file")
        else:
            self.send("213 " + (self.state.args.mdtm or MDTM))

    def do_REST(self, arg):
        if self.state.args.no_rest:
            self.send("502 REST not supported")
            return
        self.rest = int(arg or 0)
        self.send("350 Restarting at %d" % self.rest)

    def open_passive(self):
        if self.passive:
            self.passive.close()
        listener = socket.socket()
        listener.bind(("127.0.0.1", 0))
        listener.listen(1)
        listener.settimeout(20)
        self.passive = listener
        return listener.getsockname()[1]

    def do_PASV(self, arg):
        if self.need_login():
            return
        port = self.open_passive()
        self.send("227 Entering Passive Mode (127,0,0,1,%d,%d)" % (port // 256, port % 256))

    def do_EPSV(self, arg):
        if self.need_login():
            return
        if self.state.args.no_epsv:
            self.send("500 EPSV not supported")
            return
        port = self.open_passive()
        self.send("229 Entering Extended Passive Mode (|||%d|)" % port)

    def do_RETR(self, arg):
        if self.need_login():
            return
        size = file_size(arg)
        if size is None:
            self.send("550 No such file")
            return
        if not self.passive:
            self.send("425 Use PASV first")
            return
        start, self.rest = self.rest, 0
        listener, self.passive = self.passive, None
        try:
            data, _ = listener.accept()
        except OSError:
            self.send("425 Cannot open data connection")
            return
        finally:
            listener.close()
        with self.state.lock:
            self.state.retrs += 1
            self.state.rests.append(start)
        self.send("150 Opening data connection")
        try:
            if self.prot_private and self.ctx is not None:
                data = self.ctx.wrap_socket(data, server_side=True)
                with self.state.lock:
                    self.state.tls_data += 1
            rate = self.state.args.throttle * 1024
            began = time.time()
            sent = 0
            offset = start
            cut = parse_size(self.state.args.cut, None) if self.state.args.cut else None
            while offset < size:
                take = min(64 * 1024, size - offset)
                chunk = self.state.content.read(offset, take)
                if cut is not None and sent + len(chunk) > cut:
                    chunk = chunk[:max(0, cut - sent)]
                    data.sendall(chunk)
                    data.close()
                    self.send("426 Connection closed; transfer aborted")
                    return
                data.sendall(chunk)
                sent += len(chunk)
                offset += len(chunk)
                if rate > 0:
                    ahead = sent / rate - (time.time() - began)
                    if ahead > 0:
                        time.sleep(ahead)
            finish_data(data)
            self.send("226 Transfer complete")
        except (OSError, ssl.SSLError) as error:
            self.state.log("DATA ERROR " + repr(error))
            try:
                data.close()
            except OSError:
                pass
            try:
                self.send("426 Connection closed; transfer aborted")
            except OSError:
                pass

    def do_ABOR(self, arg):
        self.send("226 Nothing to abort")


def finish_data(data):
    """End a data connection the polite way: a TLS close_notify, then FIN, so the client never sees a reset."""
    try:
        if isinstance(data, ssl.SSLSocket):
            data = data.unwrap()
        data.shutdown(socket.SHUT_WR)
        data.settimeout(2)
        try:
            while data.recv(4096):
                pass
        except (OSError, ssl.SSLError):
            pass
    except (OSError, ssl.SSLError, ValueError):
        pass
    try:
        data.close()
    except OSError:
        pass


def serve(state, port, implicit_ctx=None):
    server = socket.socket()
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", port))
    server.listen(32)
    while True:
        conn, _ = server.accept()
        Session(state, conn, implicit_ctx).start()


def selftest(args):
    import ftplib
    args.user, args.password = "fj", "secret"
    state = State(args)
    state.ctx = None
    port = 0
    probe = socket.socket()
    probe.bind(("127.0.0.1", 0))
    port = probe.getsockname()[1]
    probe.close()
    threading.Thread(target=serve, args=(state, port), daemon=True).start()
    time.sleep(0.3)
    failed = 0

    def check(name, ok, detail=""):
        nonlocal failed
        print(("PASS  " if ok else "FAIL  ") + name + (("  [" + str(detail) + "]") if detail else ""))
        if not ok:
            failed += 1

    ftp = ftplib.FTP()
    ftp.connect("127.0.0.1", port, timeout=10)
    try:
        ftp.login("fj", "wrong")
        check("a wrong password is refused", False)
    except ftplib.error_perm as e:
        check("a wrong password is refused", str(e).startswith("530"), e)
    ftp.login("fj", "secret")
    check("size from the name", ftp.size("dir/2MB.bin") == 2 * 1024 * 1024)
    body = bytearray()
    ftp.retrbinary("RETR dir/2MB.bin", body.extend)
    sha = hashlib.sha256(body).hexdigest()
    check("a whole file matches the HTTP server's bytes", len(body) == 2 * 1024 * 1024 and sha == state.content.sha256(0, 2 * 1024 * 1024), sha[:12])
    part = bytearray()
    ftp.retrbinary("RETR dir/2MB.bin", part.extend, rest=1000000)
    check("REST continues in the middle", bytes(part) == bytes(body[1000000:]))
    ftp.quit()
    print("selftest: %d failed" % failed)
    return failed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=2121)
    parser.add_argument("--implicit-port", type=int, default=0, help="also listen here with TLS from the first byte (FTPS)")
    parser.add_argument("--cert", default=None)
    parser.add_argument("--key", default=None)
    parser.add_argument("--user", default="fj")
    parser.add_argument("--password", default="secret")
    parser.add_argument("--anonymous", action="store_true")
    parser.add_argument("--no-rest", action="store_true")
    parser.add_argument("--no-epsv", action="store_true")
    parser.add_argument("--require-tls", action="store_true")
    parser.add_argument("--maxconn", type=int, default=0, help="more control connections at once than this get 421")
    parser.add_argument("--throttle", type=int, default=0, help="KB/s per data connection")
    parser.add_argument("--cut", default=None, help="close every data connection after this many bytes")
    parser.add_argument("--mdtm", default=None)
    parser.add_argument("--log", default=None)
    parser.add_argument("--pidfile", default=None)
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args()
    if args.selftest:
        sys.exit(1 if selftest(args) else 0)
    state = State(args)
    state.ctx = None
    if args.cert:
        state.ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        state.ctx.load_cert_chain(args.cert, args.key or args.cert)
    if args.pidfile:
        with open(args.pidfile, "w") as handle:
            handle.write(str(os.getpid()))
    if args.implicit_port and state.ctx is not None:
        threading.Thread(target=serve, args=(state, args.implicit_port, state.ctx), daemon=True).start()
    serve(state, args.port)


if __name__ == "__main__":
    main()
