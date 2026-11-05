#!/bin/bash
# Signs release files for the in-app updater: writes <file>.sig next to each file (base64
# ECDSA P-256 / SHA-256 signature). The key is the UPDATE_SIGNING_KEY secret (PEM); the app
# trusts its public key (UPDATE_PUBLIC_KEYS in core/update/UpdateSignature.kt) and refuses to
# install an update without a valid .sig.
set -euo pipefail
if [ -z "${UPDATE_SIGNING_KEY:-}" ]; then
  echo "::error::Secret UPDATE_SIGNING_KEY is missing: the app would refuse this release's updates."
  exit 1
fi
KEY="$(mktemp)"
trap 'rm -f "$KEY"' EXIT
chmod 600 "$KEY"
printf '%s\n' "$UPDATE_SIGNING_KEY" > "$KEY"
for f in "$@"; do
  openssl dgst -sha256 -sign "$KEY" "$f" | openssl base64 -A > "$f.sig"
  echo "signed $(basename "$f")"
done
