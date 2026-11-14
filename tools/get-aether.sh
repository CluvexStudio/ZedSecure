#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="${1:?android, linux, windows, macos-arm64 or macos-x86_64}"
VERSION=v2.4.0
CACHE="${CORES_CACHE:-$ROOT/build/cores}/CluvexStudio/Aether/$VERSION"
mkdir -p "$CACHE"

digest() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi | awk '{print $1}'
}

fetch_aether() {
  local asset=$1 want
  want=$(awk -v a="$asset" '$2 == a {print $1}' "$ROOT/tools/aether-sha256.txt")
  [ -n "$want" ] || { echo "No pinned Aether checksum for $asset" >&2; exit 1; }
  if [ ! -s "$CACHE/$asset" ] || [ "$(digest "$CACHE/$asset")" != "$want" ]; then
    curl -fsSL --retry 3 -o "$CACHE/$asset.part" "https://github.com/CluvexStudio/Aether/releases/download/$VERSION/$asset"
    [ "$(digest "$CACHE/$asset.part")" = "$want" ] || { rm -f "$CACHE/$asset.part"; echo "Aether checksum mismatch: $asset" >&2; exit 1; }
    mv "$CACHE/$asset.part" "$CACHE/$asset"
  fi
  printf '%s\n' "$CACHE/$asset"
}

install_aether() {
  local asset=$1 dest=$2 binary=$3 archive stage
  archive=$(fetch_aether "$asset")
  stage=$(mktemp -d "$CACHE/unpack.XXXXXX")
  case "$asset" in
    *.zip) unzip -q "$archive" -d "$stage" ;;
    *) tar -xzf "$archive" -C "$stage" ;;
  esac
  [ -s "$stage/$binary" ] && [ -d "$stage/LICENSES" ] || { echo "Incomplete Aether archive: $asset" >&2; rm -rf "$stage"; exit 1; }
  mkdir -p "$dest"
  cp "$stage/$binary" "$dest/$binary"
  chmod +x "$dest/$binary"
  # Preserve all upstream third-party notices with the packaged executable.
  rm -rf "$dest/aether-licenses"
  cp -R "$stage/LICENSES" "$dest/aether-licenses"
  rm -rf "$stage"
}

case "$TARGET" in
  android)
    for pair in arm64-v8a:arm64 armeabi-v7a:armv7 x86_64:x86_64; do
      abi=${pair%:*}; arch=${pair#*:}
      dest="$CACHE/android-$abi"
      install_aether "aether-android-$arch.tar.gz" "$dest" aether
      mkdir -p "$ROOT/app/src/main/jniLibs/$abi" "$ROOT/app/src/main/assets/aether/$abi"
      # Android 10+ executes packaged native files from nativeLibraryDir, never filesDir.
      cp "$dest/aether" "$ROOT/app/src/main/jniLibs/$abi/libaether.so"
      rm -rf "$ROOT/app/src/main/assets/aether/$abi/LICENSES"
      cp -R "$dest/aether-licenses" "$ROOT/app/src/main/assets/aether/$abi/LICENSES"
    done
    ;;
  linux) install_aether aether-linux-x86_64-musl.tar.gz "$ROOT/desktop/src/main/resources/bin/linux" aether ;;
  windows) install_aether aether-windows-x86_64.zip "$ROOT/desktop/src/main/resources/bin/windows" aether.exe ;;
  macos-arm64|macos-x86_64) install_aether "aether-$TARGET.tar.gz" "$ROOT/desktop/src/main/resources/bin/macos" aether ;;
  *) echo "Unknown Aether target: $TARGET" >&2; exit 1 ;;
esac
