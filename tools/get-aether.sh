#!/usr/bin/env bash
# Fetches the pinned Aether core (CluvexStudio/Aether) into the desktop
# resources as tor_tree-style trees (binary + pt/ + files.txt) for
# BundledTree.extract. Standalone on purpose: does not touch
# get-cores.sh/cores.txt, so stock engines are unaffected.
set -euo pipefail

AETHER_VERSION="v2.3.0"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="${1:?linux, windows, macos-arm64 or macos-x86_64}"

digest() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi | awk '{print $1}'
}

case "$TARGET" in
  linux)       ASSET="aether-linux-x86_64.tar.gz" ; SUB="linux"   ; BIN="aether" ;;
  windows)     ASSET="aether-windows-x86_64.zip"    ; SUB="windows" ; BIN="aether.exe" ;;
  macos-arm64) ASSET="aether-macos-arm64.tar.gz"   ; SUB="macos"   ; BIN="aether" ;;
  macos-x86_64) ASSET="aether-macos-x86_64.tar.gz" ; SUB="macos"   ; BIN="aether" ;;
  *) echo "unknown target $TARGET" >&2; exit 1 ;;
esac

BASE="https://github.com/CluvexStudio/Aether/releases/download/$AETHER_VERSION"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
curl -fsSL --retry 3 -o "$tmp/$ASSET" "$BASE/$ASSET"
curl -fsSL --retry 3 -o "$tmp/SHA256SUMS.txt" "$BASE/SHA256SUMS.txt"
want=$(awk -v a="$ASSET" '$2 == a { print $1 }' "$tmp/SHA256SUMS.txt")
[ -n "$want" ] || { echo "$ASSET is not in upstream SHA256SUMS.txt" >&2; exit 1; }
[ "$(digest "$tmp/$ASSET")" = "$want" ] || { echo "$ASSET checksum mismatch" >&2; exit 1; }

dest="$ROOT/desktop/src/main/resources/bin/$SUB/aether"
rm -rf "$dest"
mkdir -p "$dest"
case "$ASSET" in
  *.zip) unzip -q -o "$tmp/$ASSET" -d "$tmp/unpack" ;;
  *.tar.gz) mkdir -p "$tmp/unpack" && tar xzf "$tmp/$ASSET" -C "$tmp/unpack" ;;
esac
found=$(find "$tmp/unpack" -type f -name "$BIN" | head -1)
[ -n "$found" ] || { echo "$BIN missing from $ASSET" >&2; exit 1; }
cp "$found" "$dest/$BIN"
chmod +x "$dest/$BIN"
pt=$(find "$tmp/unpack" -type d -name pt | head -1)
if [ -n "$pt" ]; then
  cp -r "$pt" "$dest/pt"
fi
( cd "$dest" && find . -type f ! -name files.txt | sed 's#^\./##' | sort > files.txt )
echo "aether $TARGET staged at $dest"
