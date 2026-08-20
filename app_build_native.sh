#!/bin/bash
# app_build_native.sh — build anti and package it as a macOS .app bundle.
#
# Produces ./anti.app ready to publish/drag into /Applications:
#
#   anti.app/
#   └── Contents/
#       ├── Info.plist          (bundle metadata, executable name, etc.)
#       └── MacOS/anti          (the native binary)
#
# arm64 macOS refuses to run unsigned binaries, so the bundle is ad-hoc
# codesigned (identity "-"). A real release would swap "-" for your Developer ID.
#
# Usage:
#   ./app_build_native.sh                  # build 'anti_window' as 'anti.app'
#   ./app_build_native.sh mytarget MyApp   # pick a target + bundle name
#
# Env overrides:
#   ANTI_RELEASE_BUILD=build-release       # alternate build dir

set -euo pipefail

# --- locate cmake (CLion-bundled first, then PATH) ---------------------------
CLION_CMAKE="/Applications/CLion.app/Contents/bin/cmake/mac/aarch64/bin/cmake"
if [ -x "$CLION_CMAKE" ]; then CMAKE="$CLION_CMAKE";
elif command -v cmake >/dev/null 2>&1; then CMAKE="$(command -v cmake)";
else echo "error: cmake not found (open CLion once, or install it)"; exit 1; fi

TARGET="${1:-anti_window}"
APP_NAME="${2:-anti}"
BUILD_DIR="${ANTI_RELEASE_BUILD:-build-release}"
OUT_DIR="${ANTI_OUT_DIR:-out}"                    # standalone app lands here
APP_DIR="$OUT_DIR/$APP_NAME.app"
BUNDLE_ID="${ANTI_BUNDLE_ID:-dev.vexgraph.$APP_NAME}"

echo "== configuring Release build =="
"$CMAKE" -S . -B "$BUILD_DIR" -DCMAKE_BUILD_TYPE=Release >/dev/null

echo "== building $TARGET =="
"$CMAKE" --build "$BUILD_DIR" --target "$TARGET"

BIN="$BUILD_DIR/$TARGET"
if [ ! -x "$BIN" ]; then echo "error: $BIN missing after build"; exit 1; fi

# --- assemble the .app bundle ------------------------------------------------
rm -rf "$APP_DIR"
mkdir -p "$APP_DIR/Contents/MacOS"
cp "$BIN" "$APP_DIR/Contents/MacOS/$APP_NAME"

cat > "$APP_DIR/Contents/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key>
    <string>$APP_NAME</string>
    <key>CFBundleDisplayName</key>
    <string>$APP_NAME</string>
    <key>CFBundleIdentifier</key>
    <string>$BUNDLE_ID</string>
    <key>CFBundleExecutable</key>
    <string>$APP_NAME</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleShortVersionString</key>
    <string>0.1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSMinimumSystemVersion</key>
    <string>13.0</string>
    <key>NSPrincipalClass</key>
    <string>NSApplication</string>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
EOF

echo "== ad-hoc codesigning $APP_DIR (arm64 requires a signature to launch) =="
codesign --force --sign - "$APP_DIR"

echo
echo "built $APP_DIR"
echo "  binary: $APP_DIR/Contents/MacOS/$APP_NAME"
echo "  run it with: open $APP_DIR"