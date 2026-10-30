#!/usr/bin/env bash
# One-shot setup of a GitHub Actions self-hosted runner inside WSL (Ubuntu).
# Run from Windows PowerShell:   wsl bash /mnt/e/tbib/FetchJet/scripts/setup-wsl-runner.sh
# Optional: pass the registration token from GitHub (Settings -> Actions -> Runners ->
# New self-hosted runner) as the first argument; otherwise it is fetched with gh.exe.
set -euo pipefail

REPO="the-best-is-best/FetchJet"
LABEL="wsl-linux"
DIR="$HOME/actions-runner"
TOKEN="${1:-}"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

say "1/5 Installing packages (JDK 21, git, node...)"
sudo apt-get update -y
sudo apt-get install -y openjdk-21-jdk curl unzip zip git nodejs npm jq ca-certificates

say "2/5 Getting a runner registration token"
if [ -z "$TOKEN" ]; then
  for gh in gh gh.exe "/mnt/c/Program Files/GitHub CLI/gh.exe"; do
    if command -v "$gh" >/dev/null 2>&1 || [ -x "$gh" ]; then
      TOKEN=$("$gh" api -X POST "repos/$REPO/actions/runners/registration-token" --jq .token 2>/dev/null | tr -d '\r' || true)
      [ -n "$TOKEN" ] && { echo "token from $gh"; break; }
    fi
  done
fi
if [ -z "$TOKEN" ]; then
  echo "Could not get a token automatically."
  echo "Open https://github.com/$REPO/settings/actions/runners/new , copy the value after --token,"
  read -r -p "and paste it here: " TOKEN
fi

say "3/5 Downloading the runner"
mkdir -p "$DIR" && cd "$DIR"
if [ ! -x ./config.sh ]; then
  VER=$(curl -fsSL https://api.github.com/repos/actions/runner/releases/latest | jq -r .tag_name | sed 's/^v//')
  curl -fsSL -o runner.tgz "https://github.com/actions/runner/releases/download/v${VER}/actions-runner-linux-x64-${VER}.tar.gz"
  tar xzf runner.tgz && rm runner.tgz
  sudo ./bin/installdependencies.sh || true
fi

say "4/5 Registering with GitHub (label: $LABEL)"
if [ ! -f .runner ]; then
  ./config.sh --unattended --url "https://github.com/$REPO" --token "$TOKEN" \
    --name "wsl-$(hostname)" --labels "$LABEL" --work _work --replace
else
  echo "already registered"
fi

say "5/5 Starting the runner"
if [ "$(ps -p 1 -o comm= 2>/dev/null)" = "systemd" ]; then
  sudo ./svc.sh install "$USER" || true
  sudo ./svc.sh start
  echo "Installed as a service: it starts by itself whenever WSL runs."
else
  echo "systemd is off in this WSL; starting in the background instead."
  echo "(To make it permanent: add [boot] systemd=true to /etc/wsl.conf, then 'wsl --shutdown'.)"
  nohup ./run.sh > "$DIR/runner.log" 2>&1 &
fi

cat <<EOF

Done. Last step on GitHub (once):
  https://github.com/$REPO/settings/variables/actions
  New repository variable:  RUNNER_LINUX = $LABEL
The runner shows as "Idle" at https://github.com/$REPO/settings/actions/runners
EOF
