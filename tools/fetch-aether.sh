#!/usr/bin/env bash
set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AETHER_RELEASE="${1:-latest}"

echo "Fetching Aether native binaries..."

mkdir -p "$REPO_ROOT/desktop/src/main/resources/bin/linux"
mkdir -p "$REPO_ROOT/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$REPO_ROOT/app/src/main/jniLibs/armeabi-v7a"

if [ -f "$REPO_ROOT/desktop/src/main/resources/bin/linux/aether" ]; then
    echo "Linux aether binary already staged."
else
    echo "Downloading Linux x86_64 aether from GitHub Releases..."
    curl -fsSL "https://github.com/CluvexStudio/Aether/releases/latest/download/aether-linux-x86_64.tar.gz" | tar -xz -C "$REPO_ROOT/desktop/src/main/resources/bin/linux/"
    chmod +x "$REPO_ROOT/desktop/src/main/resources/bin/linux/aether"
fi

echo "Aether binaries ready."
