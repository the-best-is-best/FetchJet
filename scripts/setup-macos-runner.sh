#!/usr/bin/env bash
# One-shot setup of a GitHub Actions self-hosted runner on a Mac (Apple Silicon or Intel).
# It builds the macOS DMG and the iOS IPA.
#   bash setup-macos-runner.sh            (token fetched with gh, or asked for)
#   bash setup-macos-runner.sh <TOKEN>
set -euo pipefail
REPO="the-best-is-best/FetchJet"
LABEL="mac-host"
DIR="$HOME/actions-runner"
TOKEN="${1:-}"
say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

say "1/6 Xcode"
if ! xcodebuild -version >/dev/null 2>&1; then
  echo "Install Xcode from the App Store, open it once (accept the license), then run this again."
  exit 1
fi
sudo xcodebuild -license accept || true
xcodebuild -version

say "2/6 Homebrew + tools (JDK 21, cmake, boost, gh, jq)"
if ! command -v brew >/dev/null; then
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
  eval "$(/opt/homebrew/bin/brew shellenv 2>/dev/null || /usr/local/bin/brew shellenv)"
fi
brew install --quiet openjdk@21 cmake boost gh jq || true
JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"

say "3/6 Registration token"
if [ -z "$TOKEN" ]; then
  gh auth status >/dev/null 2>&1 || gh auth login
  TOKEN=$(gh api -X POST "repos/$REPO/actions/runners/registration-token" --jq .token || true)
fi
[ -n "$TOKEN" ] || read -r -p "Paste the token from https://github.com/$REPO/settings/actions/runners/new : " TOKEN

say "4/6 Runner download"
mkdir -p "$DIR" && cd "$DIR"
if [ ! -x ./config.sh ]; then
  ARCH=$( [ "$(uname -m)" = "arm64" ] && echo arm64 || echo x64 )
  VER=$(curl -fsSL https://api.github.com/repos/actions/runner/releases/latest | jq -r .tag_name | sed 's/^v//')
  curl -fsSL -o runner.tgz "https://github.com/actions/runner/releases/download/v${VER}/actions-runner-osx-${ARCH}-${VER}.tar.gz"
  tar xzf runner.tgz && rm runner.tgz
fi

say "5/6 Register (label: $LABEL)"
[ -f .runner ] || ./config.sh --unattended --url "https://github.com/$REPO" --token "$TOKEN" \
  --name "mac-$(hostname -s)" --labels "$LABEL" --work _work --replace
cat > .env <<EOF
JAVA_HOME=$JAVA_HOME
PATH=$JAVA_HOME/bin:$(brew --prefix)/bin:/usr/bin:/bin:/usr/sbin:/sbin
EOF

say "6/6 Start as a login service"
./svc.sh install || true
./svc.sh start

cat <<EOF

Done. On GitHub (once): https://github.com/$REPO/settings/variables/actions
  RUNNER_MACOS = $LABEL
The Mac must stay awake and logged in while a build runs.
EOF
