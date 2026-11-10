#!/usr/bin/env bash
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${FJ_TEST_WORK:-E:/tools/probes/winhelpers-work}"
EXE="$HERE/build/bin/mingwX64/debugExecutable/winhelpers.exe"
[ -f "$HERE/build/bin/mingwX64/releaseExecutable/winhelpers.exe" ] && [ "${FJ_TEST_RELEASE:-0}" = "1" ] && EXE="$HERE/build/bin/mingwX64/releaseExecutable/winhelpers.exe"
MODES="${*:-crypto secrets system files zip process net proxy sync}"
mkdir -p "$WORK"
FAILED=0
PS_DPAPI='Add-Type -AssemblyName System.Security; $b = [Convert]::FromBase64String([Console]::In.ReadLine()); [Convert]::ToBase64String([Security.Cryptography.ProtectedData]::METHOD($b, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser))'

value() { grep "^$1=" "$WORK/vectors.txt" | head -1 | cut -d= -f2- | tr -d '\r'; }

for mode in $MODES; do
  echo "=== $mode"
  case "$mode" in
    crypto)
      java "$HERE/Vectors.java" gen "$WORK" || FAILED=1
      "$EXE" "$WORK" crypto || FAILED=1
      java "$HERE/Vectors.java" check "$WORK" || FAILED=1
      ;;
    secrets)
      [ -f "$WORK/vectors.txt" ] || java "$HERE/Vectors.java" gen "$WORK"
      value secret.key.b64 | powershell.exe -NoProfile -NonInteractive -Command "${PS_DPAPI/METHOD/Protect}" | tr -d '\r' > "$WORK/secrets.key"
      "$EXE" "$WORK" secrets || FAILED=1
      BACK=$(tr -d '\r\n' < "$WORK/native-protected.b64" | powershell.exe -NoProfile -NonInteractive -Command "${PS_DPAPI/METHOD/Unprotect}" | tr -d '\r\n')
      if [ "$BACK" = "$(value secret.key.b64)" ]; then echo "PASS  the JVM app's PowerShell call opens the key the native code protected"; else echo "FAIL  the JVM app's PowerShell call opens the key the native code protected"; FAILED=1; fi
      ;;
    system)
      OFFSET=$(powershell.exe -NoProfile -NonInteractive -Command "[long][TimeZoneInfo]::Local.GetUtcOffset([DateTime]::Now).TotalMilliseconds" | tr -d '\r\n')
      MINUTES=$(powershell.exe -NoProfile -NonInteractive -Command "\$n=[DateTime]::Now; \$n.Hour*60+\$n.Minute" | tr -d '\r\n')
      "$EXE" "$WORK" system "offset=$OFFSET" "minutes=$MINUTES" || FAILED=1
      ;;
    zip)
      python "$HERE/make_zip.py" "$WORK/zip" || FAILED=1
      "$EXE" "$WORK" zip || FAILED=1
      ;;
    torrent)
      FETCHJET_TORRENT_LOCAL_ONLY=1 "$EXE" "$WORK" torrent ${FJ_TEST_ARGS:-} || FAILED=1
      ;;
    sync)
      SYNC_HOST=$(powershell.exe -NoProfile -NonInteractive -Command "[System.Net.Dns]::GetHostName()" | tr -d '\r\n')
      FETCHJET_SYNC_NO_BROADCAST=1 "$EXE" "$WORK" sync "host=$SYNC_HOST" ${FJ_TEST_ARGS:-} || FAILED=1
      ;;
    syncjvm)
      FETCHJET_SYNC_NO_BROADCAST=1 "$EXE" "$WORK" syncjvm "java=$(cygpath -w "$(command -v java)")" "cp=$(cygpath -w "$HERE/jvmpeer/build/peer-classpath.txt")" ${FJ_TEST_ARGS:-} || FAILED=1
      ;;
    *)
      shift_args=""
      "$EXE" "$WORK" "$mode" ${FJ_TEST_ARGS:-} || FAILED=1
      ;;
  esac
done
echo "=== result: $([ $FAILED = 0 ] && echo all passed || echo FAILURES)"
exit $FAILED
