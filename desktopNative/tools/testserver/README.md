# Local test servers (no internet)

Everything here listens on 127.0.0.1 only and uses the Python standard library.

- `server.py`: HTTP (or HTTPS) file server with every awkward behaviour a download engine meets.
- `ftp.py`: FTP / FTPS server (login `fj` / `secret`, `--anonymous`, explicit TLS and TLS from the first byte,
  `--no-rest`, `--maxconn`, `--throttle`, `--cut`). `/file/..` style names are not needed: any path ending
  with a size name (`dir/8MB.bin`) is a file with the same bytes as the HTTP server's. `servers.sh` starts
  it on `2121` (plain + anonymous), `2122` (explicit TLS) / `2990` (implicit TLS), `2123` (no REST) and
  `2124` (2 connections at most). `python ftp.py --selftest` checks it with Python's `ftplib`.
- `proxy.py`: HTTP proxy (plain + CONNECT) and SOCKS5 proxy, with or without a login.
- `servers.sh start | stop | status`: starts the usual set and keeps their process ids:
  HTTP `18200`, HTTPS `18443` (self-signed certificate made with `openssl` on first start),
  proxies with the login `fj` / `secret` on `18081` (HTTP) and `11081` (SOCKS5), proxies without
  a login on `18082` / `11082`. Work files and logs go to `E:/tools/probes/winhelpers-work/servers`
  (`FJ_TEST_WORK` changes the folder). The proxies map the name `files.fjtest.example` to
  127.0.0.1, so a test can use a host name that is not a loopback address.

```
bash desktopNative/tools/testserver/servers.sh start
python desktopNative/tools/testserver/server.py --selftest
```

## server.py

```
python server.py [--port 18200] [--log file] [--throttle KB/s] [--cert cert.pem --key key.pem] [--pidfile file] [--verbose]
python server.py --selftest
```

### Files

`/file/<name>` serves generated content. The size comes from the name (`/file/10MB.bin`,
`/file/300KB.bin`, `/file/5GB.bin`) or from `?size=10MB`. The content is fixed: 16-byte lines, each
the offset of the line in hex (`000000000000010\n`), so any range can be checked by itself and a
wrong offset is visible at once. `/sha256/<name>` returns the SHA-256 of the same file (with the
same `size` / `base` parameters). Nothing is stored on disk; a 5 GB file costs no memory.

The server supports `Range` (also suffix and open ranges, `416` when out of bounds), `HEAD`,
`ETag` / `Last-Modified` / `If-None-Match` (304) / `If-Range`, and `Accept-Ranges: bytes`.

Query options of `/file/...` (combine freely):

| Option | Effect |
|---|---|
| `size=10MB` | file size when the name has none |
| `base=N` | the content starts at offset N of the endless stream (used by HLS segments) |
| `throttle=K` | at most K KB/s for this connection (the `--throttle` default applies otherwise) |
| `delay=MS` | wait MS before answering (slow first byte) |
| `bodydelay=MS` | send the headers, then wait MS before the body |
| `maxconn=N` | at most N connections at once for this key; the rest get `busy` (default 429) |
| `busy=503` | the status for refused connections; `retryafter=S` adds `Retry-After` |
| `fail=N:CODE` | the first N requests for this key answer CODE (default 503), then it works |
| `status=CODE` | always answer CODE |
| `cut=100KB` | close the connection (reset) after that many body bytes |
| `cutonce=100KB` | the same, but only for the first request of this key |
| `norange=1` | ignore `Range` and answer 200 with the whole file, no `Accept-Ranges` |
| `nohead=1` | answer 405 to `HEAD` |
| `chunked=1` | chunked transfer, no `Content-Length` |
| `nolength=1` | no `Content-Length`, the body ends when the connection closes |
| `noetag=1` | no `ETag` / `Last-Modified` |
| `v=2` | another version: different `ETag` and `Last-Modified` |
| `flipat=N` | from the Nth request of this key on, the file is version 2 (it changed while downloading) |
| `wrongpart=N` | the Nth request of this key sends the bytes of the wrong offset (a corrupt part) |
| `slowreq=N:KB` | only the Nth request of this key is limited to KB/s (a slow last connection) |
| `robot=1` | answer 200 with an HTML "are you a robot" page instead of the file |
| `auth=user:pass` | require HTTP Basic login (401 with `WWW-Authenticate` otherwise) |
| `name=<text>` | add `Content-Disposition: attachment; filename*=UTF-8''<text>` |
| `type=<mime>` | `Content-Type` (default `application/octet-stream`) |
| `cookie=<text>` | add `Set-Cookie` |
| `digest=good` / `digest=bad` | add `Digest: sha-256=<base64>` with the file's real SHA-256, or with a wrong one |
| `key=<text>` | counters are kept per key (default: the path); use one key per test |

### Other paths

| Path | Effect |
|---|---|
| `/redirect/abs/<path>` | 302 to an absolute URL of `/<path>` |
| `/redirect/rel/<path>` | 302 with a relative `Location` (`../../...`) |
| `/redirect/root/<path>` | 302 with `Location: /<path>` |
| `/redirect/proto/<path>` | 302 with `Location: //127.0.0.1:port/<path>` |
| `/redirect/loop/x` | redirects to itself |
| `...?n=5&code=307` | a chain of n redirects with that status; other query options are passed on |
| `/hls/master.m3u8?segments=8&segsize=256KB` | HLS master playlist with three variants |
| `/hls/<variant>/index.m3u8`, `/hls/<variant>/segN.ts` | media playlist and its segments; joined, the segments equal `/file/x?size=segments*segsize` |
| `/echo` | JSON with the method, path, request headers and body size / SHA-256 |
| `/status/<code>` | that status |
| `/stall` | never answers; `/stall?after=headers` sends headers, then nothing |
| `/stream?kbps=256` | endless chunked stream |
| `/stats`, `/stats?of=<key>` | counters: `requests`, `active`, `maxActive` (most connections at once), `bytes`, `liveBytes` (counted while sending), `statuses`, `ranges` (the `Range` headers seen), `methods` |
| `/reset` | clear the counters |

The request log (`--log`) has one line per request: time, client port, method, path, range,
status, bytes sent, milliseconds.

## proxy.py

```
python proxy.py [--http-port 18081] [--socks-port 11081] [--user fj --password secret] [--map name=address] [--log file] [--pidfile file]
```

Without `--user` no login is asked. The log shows what went through:
`HTTP-PROXY OK GET http://...`, `HTTP-PROXY OK CONNECT host:port`, `HTTP-PROXY 407 ...` (no or
wrong login), `SOCKS OK host:port name|ip user=...`, `SOCKS AUTH-FAIL user`. A test that claims
"the request went through the proxy" must show the matching log line.

`scripts/network-test/test_proxies.py` (the older script used for the iOS tests) is still there;
this one adds the host map, the no-login mode and SOCKS5 host names.
