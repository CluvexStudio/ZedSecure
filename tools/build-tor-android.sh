#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOR_VERSION="${TOR_VERSION:-0.4.9.13}"
OPENSSL_VERSION="${OPENSSL_VERSION:-3.6.5}"
LIBEVENT_VERSION="${LIBEVENT_VERSION:-2.1.12-stable}"
API="${API:-24}"
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64}"
OUT="${OUT:-$ROOT/build/tor-android}"
WORK="${WORK:-$ROOT/build/tor-android-work}"
NDK="${NDK:?set NDK to an Android NDK}"
JOBS="${JOBS:-$(nproc)}"

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
export PATH="$TOOLCHAIN/bin:$PATH"
export ANDROID_NDK_ROOT="$NDK"

mkdir -p "$WORK/src" "$OUT"
fetch() {
  [ -f "$WORK/src/$2" ] || curl -fsSL --retry 3 -o "$WORK/src/$2" "$1"
}
fetch "https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/openssl-$OPENSSL_VERSION.tar.gz" "openssl.tar.gz"
fetch "https://github.com/libevent/libevent/releases/download/release-$LIBEVENT_VERSION/libevent-$LIBEVENT_VERSION.tar.gz" "libevent.tar.gz"
fetch "https://archive.torproject.org/tor-package-archive/tor-$TOR_VERSION.tar.gz" "tor.tar.gz"

for abi in $ABIS; do
  case "$abi" in
    arm64-v8a) ossl=android-arm64; host=aarch64-linux-android; cc=aarch64-linux-android$API-clang ;;
    armeabi-v7a) ossl=android-arm; host=arm-linux-androideabi; cc=armv7a-linux-androideabi$API-clang ;;
    x86_64) ossl=android-x86_64; host=x86_64-linux-android; cc=x86_64-linux-android$API-clang ;;
    *) echo "unknown ABI $abi" >&2; exit 1 ;;
  esac
  build="$WORK/$abi"
  prefix="$build/prefix"
  rm -rf "$build" && mkdir -p "$build" "$prefix"
  export CC="$cc" AR=llvm-ar RANLIB=llvm-ranlib STRIP=llvm-strip

  tar -xzf "$WORK/src/openssl.tar.gz" -C "$build"
  (cd "$build/openssl-$OPENSSL_VERSION" &&
    ./Configure "$ossl" -D__ANDROID_API__="$API" no-shared no-tests no-docs no-apps no-module \
      --prefix="$prefix" --libdir=lib > "$build/openssl.log" 2>&1 &&
    make -j"$JOBS" build_libs >> "$build/openssl.log" 2>&1 &&
    make install_dev >> "$build/openssl.log" 2>&1) || { tail -40 "$build/openssl.log"; exit 1; }

  tar -xzf "$WORK/src/libevent.tar.gz" -C "$build"
  (cd "$build/libevent-$LIBEVENT_VERSION" &&
    ./configure --host="$host" --prefix="$prefix" --disable-shared --enable-static --disable-openssl \
      --disable-samples --disable-libevent-regress --disable-debug-mode > "$build/libevent.log" 2>&1 &&
    make -j"$JOBS" >> "$build/libevent.log" 2>&1 &&
    make install >> "$build/libevent.log" 2>&1) || { tail -40 "$build/libevent.log"; exit 1; }

  tar -xzf "$WORK/src/tor.tar.gz" -C "$build"
  (cd "$build/tor-$TOR_VERSION" &&
    patch -p1 < "$ROOT/tools/tor-fake-sni.patch" > "$build/tor.log" 2>&1 &&
    LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384" \
    ./configure --host="$host" \
      --enable-static-libevent --with-libevent-dir="$prefix" \
      --enable-static-openssl --with-openssl-dir="$prefix" \
      --disable-asciidoc --disable-manpage --disable-html-manual --disable-unittests \
      --disable-tool-name-check --disable-module-relay --disable-module-dirauth \
      --disable-lzma --disable-zstd --disable-seccomp --disable-libscrypt >> "$build/tor.log" 2>&1 &&
    make -j"$JOBS" src/app/tor >> "$build/tor.log" 2>&1) || { tail -60 "$build/tor.log"; exit 1; }

  mkdir -p "$OUT/$abi"
  cp "$build/tor-$TOR_VERSION/src/app/tor" "$OUT/$abi/libtor.so"
  llvm-strip "$OUT/$abi/libtor.so"
  ls -l "$OUT/$abi/libtor.so"
done
