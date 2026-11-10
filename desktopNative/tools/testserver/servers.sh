#!/usr/bin/env bash
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${FJ_TEST_WORK:-E:/tools/probes/winhelpers-work}"
RUN="$WORK/servers"
mkdir -p "$RUN"

stop_all() {
  for f in "$RUN"/*.pid; do
    [ -f "$f" ] || continue
    taskkill //PID "$(cat "$f")" //F > /dev/null 2>&1
    rm -f "$f"
  done
}

case "${1:-status}" in
  start)
    stop_all
    if [ ! -f "$RUN/tls-cert.pem" ]; then
      MSYS_NO_PATHCONV=1 openssl req -x509 -newkey rsa:2048 -nodes -keyout "$RUN/tls-key.pem" -out "$RUN/tls-cert.pem" \
        -days 3650 -subj "/CN=127.0.0.1" -addext "subjectAltName=IP:127.0.0.1,DNS:files.fjtest.example" > /dev/null 2>&1
    fi
    (python "$HERE/server.py" --port 18200 --log "$RUN/http.log" --pidfile "$RUN/http.pid" > /dev/null 2>&1 &)
    (python "$HERE/server.py" --port 18443 --cert "$RUN/tls-cert.pem" --key "$RUN/tls-key.pem" --log "$RUN/https.log" --pidfile "$RUN/https.pid" > /dev/null 2>&1 &)
    (python "$HERE/proxy.py" --http-port 18081 --socks-port 11081 --user fj --password secret --map files.fjtest.example=127.0.0.1 --log "$RUN/proxy-login.log" --pidfile "$RUN/proxy-login.pid" > /dev/null 2>&1 &)
    (python "$HERE/proxy.py" --http-port 18082 --socks-port 11082 --map files.fjtest.example=127.0.0.1 --log "$RUN/proxy-open.log" --pidfile "$RUN/proxy-open.pid" > /dev/null 2>&1 &)
    sleep 2
    bash "$HERE/servers.sh" status
    ;;
  stop)
    stop_all
    echo "stopped"
    ;;
  status)
    for f in "$RUN"/*.pid; do
      [ -f "$f" ] || continue
      if tasklist //FI "PID eq $(cat "$f")" 2>/dev/null | grep -q python; then echo "running  $(basename "$f" .pid)  pid $(cat "$f")"; else echo "dead     $(basename "$f" .pid)"; fi
    done
    ;;
esac
