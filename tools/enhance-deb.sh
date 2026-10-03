#!/bin/bash
set -e

deb_file=$(ls out/*.deb 2>/dev/null | head -1)
if [ -z "$deb_file" ]; then
  echo "No deb file found in out/"
  exit 0
fi

echo "Enhancing $deb_file with bundled runtime bin/java and wrapper launcher..."
patch_dir=$(mktemp -d)
mkdir -p "$patch_dir/data" "$patch_dir/control"
cd "$patch_dir"

ar x "$GITHUB_WORKSPACE/$deb_file"
tar -xf control.tar.* -C control/
tar -xf data.tar.* -C data/

# 1. Ensure lib/runtime/bin/java exists
mkdir -p data/opt/zedsecure/lib/runtime/bin
if [ ! -f data/opt/zedsecure/lib/runtime/bin/java ]; then
  if [ -n "$JAVA_HOME" ] && [ -f "$JAVA_HOME/bin/java" ]; then
    cp "$JAVA_HOME/bin/java" data/opt/zedsecure/lib/runtime/bin/java
  elif command -v java >/dev/null 2>&1; then
    cp "$(command -v java)" data/opt/zedsecure/lib/runtime/bin/java
  fi
fi
chmod +x data/opt/zedsecure/lib/runtime/bin/java 2>/dev/null || true

# 2. Add launcher wrapper
cat << 'EOF' > data/opt/zedsecure/bin/zedsecure
#!/bin/sh
SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
APPDIR="$SCRIPT_DIR/../lib/app"
RUNTIME="$SCRIPT_DIR/../lib/runtime"

export _JAVA_AWT_WM_NONREPARENTING=1
export WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-1}"
export DISPLAY="${DISPLAY:-:1}"

if [ -x "$RUNTIME/bin/java" ]; then
  JAVA_EXEC="$RUNTIME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA_EXEC="java"
else
  exec "$SCRIPT_DIR/ZedSecure" "$@"
fi

exec "$JAVA_EXEC" \
  -Dskiko.linux.autodetection=true \
  -Dskiko.vsync.enabled=false \
  -Djpackage.app-version=3.1.3 \
  -Dcompose.application.resources.dir="$APPDIR/resources" \
  -Dcompose.application.configure.swing.globals=true \
  -Dskiko.library.path="$APPDIR" \
  -cp "$APPDIR/*" \
  dev.cluvex.zedsecure.desktop.GuiKt "$@"
EOF
chmod +x data/opt/zedsecure/bin/zedsecure

mkdir -p data/usr/bin
ln -sf /opt/zedsecure/bin/zedsecure data/usr/bin/zedsecure

# 3. Repack deb
tar --use-compress-program=zstd -cf data.tar.zst -C data .
tar -czf control.tar.gz -C control .
ar rcs "$GITHUB_WORKSPACE/$deb_file" debian-binary control.tar.gz data.tar.zst

rm -rf "$patch_dir"
echo "Enhanced $deb_file successfully."
