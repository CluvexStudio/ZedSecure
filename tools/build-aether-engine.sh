#!/usr/bin/env bash
# Build the pinned upstream Aether Rust engine for ZX's Android ABIs.
# This is additive: no existing ZX engine or source is replaced.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/native/aether/aether"
JNI="$ROOT/app/src/main/jniLibs"
API="${ANDROID_API:-26}"
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  NDK_ROOT="${ANDROID_HOME:-$HOME/Android/Sdk}/ndk"
  ANDROID_NDK_HOME="$(find "$NDK_ROOT" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n1)"
fi
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [ ! -f "$SRC/Cargo.toml" ]; then
  echo "Pinned Aether source missing; initialize submodules recursively." >&2; exit 1
fi
if [ -z "$NDK" ] || [ ! -d "$NDK/toolchains/llvm/prebuilt/linux-x86_64" ]; then
  echo "Linux Android NDK not found. Set ANDROID_NDK_HOME to the NDK root." >&2; exit 1
fi
export ANDROID_NDK_HOME="$NDK" ANDROID_NDK_ROOT="$NDK"
command -v cargo-ndk >/dev/null || { echo "cargo-ndk is required" >&2; exit 1; }
build_one() {
  local abi="$1" triple="$2"
  echo "==> Building pinned Aether for $abi ($triple)"
  (cd "$SRC" && cargo ndk -t "$abi" --platform "$API" build --release --locked --features tor)
  local bin="$SRC/target/$triple/release/aether"
  [ -x "$bin" ] || { echo "Aether executable missing: $bin" >&2; find "$SRC/target" -maxdepth 4 -type f -name aether -print >&2 || true; exit 1; }
  mkdir -p "$JNI/$abi"
  install -m 755 "$bin" "$JNI/$abi/libaether.so"
  local syslib="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$triple/libc++_shared.so"
  if [ -f "$syslib" ]; then install -m 644 "$syslib" "$JNI/$abi/libc++_shared.so"; fi
  echo "    packaged $JNI/$abi/libaether.so"
}
build_one arm64-v8a aarch64-linux-android
build_one armeabi-v7a armv7-linux-androideabi
build_one x86_64 x86_64-linux-android
