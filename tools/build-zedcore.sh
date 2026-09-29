#!/usr/bin/env bash
set -e -u -x

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export GOTOOLCHAIN=go1.26.3
export PATH="$HOME/go/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ANDROID_API=24
PSI_DIR="$ROOT/reference/psiphon-tunnel-core/MobileLibrary/Android"

pick_newest() { ls -d "$1"/*/ 2>/dev/null | sort -V | tail -1 | sed 's:/*$::'; }

export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(pick_newest "$ANDROID_HOME/ndk")}"
[ -d "$ANDROID_NDK_HOME" ] || { echo "no NDK under $ANDROID_HOME/ndk" >&2; exit 1; }

ANDROID_JAR="$(ls "$ANDROID_HOME"/platforms/*/android.jar 2>/dev/null | sort -V | tail -1)"
[ -f "$ANDROID_JAR" ] || { echo "no android.jar under $ANDROID_HOME/platforms" >&2; exit 1; }

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME:-}/bin/javac" ]; then
  for candidate in \
      /usr/lib/jvm/java-21-openjdk /usr/lib64/jvm/java-21-openjdk \
      /usr/lib/jvm/java-17-openjdk /usr/lib64/jvm/java-17-openjdk \
      $(ls -d "$HOME"/.gradle/jdks/*/ 2>/dev/null | sort -V) ; do
    if [ -x "$candidate/bin/javac" ]; then JAVA_HOME="$candidate"; break; fi
  done
fi
[ -x "${JAVA_HOME:-}/bin/javac" ] || { echo "no JDK with javac found; set JAVA_HOME" >&2; exit 1; }
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

export TMPDIR="${ZEDCORE_TMPDIR:-$HOME/.cache/zedcore-build-tmp}"
export GOTMPDIR="$TMPDIR"
mkdir -p "$TMPDIR"

command -v gomobile >/dev/null || {
  echo "gomobile not on PATH. Install it with:" >&2
  echo "  go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init" >&2
  exit 1
}

echo ">>> ndk=$ANDROID_NDK_HOME"
echo ">>> jdk=$JAVA_HOME"
echo ">>> android.jar=$ANDROID_JAR"

export CGO_LDFLAGS="-Wl,-z,max-page-size=16384,-z,common-page-size=16384"
LDFLAGS="-checklinkname=0 -s -w -X runtime.godebugDefault=multipathtcp=0,tlssha1=1 -extldflags=-Wl,-z,max-page-size=16384,-z,common-page-size=16384"

SINGBOX_TAGS="$(paste -sd, "$ROOT/tools/singbox-tags.txt")"

TARGETS="${TARGETS:-android/arm64,android/arm,android/amd64}"

RAW="$ROOT/app/libs/zedcore-raw.aar"
OUT="$ROOT/app/libs/zedcore.aar"
mkdir -p "$ROOT/app/libs"

echo ">>> gomobile bind (Xray + sing-box + Psiphon + DNSTT + VayDNS + MasterDNS) with $(go version)"
cd "$ROOT/vendor/AndroidLibXrayLite"
gomobile bind -v -x -trimpath -androidapi "$ANDROID_API" -target="$TARGETS" -tags="$SINGBOX_TAGS" -ldflags="$LDFLAGS" -o "$RAW" \
  github.com/2dust/AndroidLibXrayLite \
  github.com/sagernet/sing-box/experimental/libbox \
  github.com/Psiphon-Labs/psiphon-tunnel-core/MobileLibrary/psi \
  zeddns \
  masterdnsvpn-go/mobile

echo ">>> inject ca.psiphon.PsiphonTunnel"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
unzip -o "$RAW" -d "$WORK/psi" >/dev/null

"$JAVA_HOME/bin/javac" -d "$WORK" -bootclasspath "$ANDROID_JAR" -source 1.8 -target 1.8 \
  -classpath "$WORK/psi/classes.jar" "$PSI_DIR/PsiphonTunnel/PsiphonTunnel.java"
( cd "$WORK" && "$JAVA_HOME/bin/jar" uf psi/classes.jar ca/psiphon/*.class )

cat > "$WORK/psi/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ca.psiphon">
    <uses-sdk android:minSdkVersion="24" />
</manifest>
EOF
rm -rf "$WORK/psi/res/xml" 2>/dev/null || true

printf -- '-keep class psi.** { *; }\n-keep class ca.psiphon.** { *; }\n-keep class libbox.** { *; }\n' >> "$WORK/psi/proguard.txt"

rm -f "$OUT.tmp"
( cd "$WORK/psi" && zip -r "$OUT.tmp" ./ >/dev/null )
mv -f "$OUT.tmp" "$OUT"
rm -f "$RAW"
echo ">>> wrote $OUT"
ls -la "$OUT"
