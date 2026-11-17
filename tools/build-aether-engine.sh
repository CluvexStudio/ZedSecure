#!/usr/bin/env bash
# Build the pinned upstream Aether Rust engine for ZX's Android ABIs.
# This is additive: no existing ZX engine or source is replaced.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/native/aether/aether"
JNI="$ROOT/app/src/main/jniLibs"
API="${ANDROID_API:-24}"
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
  (cd "$SRC" && cargo ndk -t "$abi" --platform "$API" build --bin aether --release --locked --features tor)
  local bin="$SRC/target/$triple/release/aether"
  [ -x "$bin" ] || { echo "Aether executable missing: $bin" >&2; find "$SRC/target" -maxdepth 4 -type f -name aether -print >&2 || true; exit 1; }
  mkdir -p "$JNI/$abi"
  install -m 755 "$bin" "$JNI/$abi/libaether.so"
  local sysroot_triple="$triple"
  if [ "$abi" = "armeabi-v7a" ]; then sysroot_triple="arm-linux-androideabi"; fi
  local syslib_dir="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$sysroot_triple"
  local syslib="$syslib_dir/libc++_shared.so"
  if [ ! -f "$syslib" ] && [ -d "$syslib_dir" ]; then
    syslib="$(find "$syslib_dir" -name libc++_shared.so -type f | sort -V | tail -n1)"
  fi
  if [ -f "$syslib" ]; then
    install -m 644 "$syslib" "$JNI/$abi/libc++_shared.so"
  else
    echo "NDK libc++_shared.so not found for $abi ($sysroot_triple)" >&2
    exit 1
  fi
  echo "    packaged $JNI/$abi/libaether.so"
}
build_one arm64-v8a aarch64-linux-android
build_one armeabi-v7a armv7-linux-androideabi
build_one x86_64 x86_64-linux-android
