#!/bin/bash
# make_vkapp.sh — assemble vktest.app: a native, double-clickable hello triangle.
#
# Self-contained: binary + SPIR-V shaders in Resources. MoltenVK is loaded at
# runtime from its installed location (brew), so it is not bundled.
#
# Usage: ./make_vkapp.sh          # build + assemble + size report
#        open vktest.app          # launch
set -euo pipefail

CLION_CMAKE="/Applications/CLion.app/Contents/bin/cmake/mac/aarch64/bin/cmake"
if [ -x "$CLION_CMAKE" ]; then CMAKE="$CLION_CMAKE"; else CMAKE="$(command -v cmake)"; fi

APP="vktest.app"
BIN="vk_test"

echo "== building $BIN =="
"$CMAKE" --build "${BUILD_DIR:-build-vk}" --target "$BIN"

echo "== assembling $APP =="
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources/spv"

cp "build-vk/$BIN" "$APP/Contents/MacOS/vktest"
cp src/vulkan/spv/hello_triangle_vert.spv "$APP/Contents/Resources/spv/"
cp src/vulkan/spv/hello_triangle_frag.spv "$APP/Contents/Resources/spv/"

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key><string>vktest</string>
    <key>CFBundleDisplayName</key><string>anti vktest</string>
    <key>CFBundleIdentifier</key><string>dev.vexgraph.anti.vktest</string>
    <key>CFBundleExecutable</key><string>vktest</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
    <key>CFBundleShortVersionString</key><string>0.1.0</string>
    <key>CFBundleVersion</key><string>1</string>
    <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
PLIST

echo "== app size =="
du -k  "$APP/Contents/MacOS/vktest" | awk '{printf "%8.1f KB  executable\n", $1}'
ls -l "$APP/Contents/Resources/spv"/*.spv | awk '{s+=$5} END {printf "%8.1f KB  shaders (2 spv)\n", s/1024}'
du -sk "$APP"                       | awk '{printf "%8.1f KB  total bundle\n", $1}'
echo "== done: ./$APP =="
