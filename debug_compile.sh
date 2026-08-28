#!/usr/bin/env bash
#
# debug_compile.sh — debug build + AI code-bundle generator for vk_test.
#
#   ./debug_compile.sh            # build binary AND regenerate code.txt
#   ./debug_compile.sh txt        # ONLY regenerate code.txt (no compile)
#   ./debug_compile.sh run        # build, write bundle, then launch
#   QUICK=1 ./debug_compile.sh    # skip -Werror
#
# The bundle (code.txt) contains every source below PLUS its sibling .h
# header when one exists — an AI reading vulkan.c without vk.h is reading
# half a conversation. Overwritten fresh on every run, never stale.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/vk_test_debug"
BUNDLE="$ROOT/code.txt"
OBJ="$ROOT/.debug-obj"
mkdir -p "$OBJ"

CC=clang
CFLAGS=(-std=gnu11 -g -O0 -fno-omit-frame-pointer -mcpu=native -Wall -Wextra)
if [[ "${QUICK:-0}" != "1" ]]; then
    CFLAGS+=(-Werror)
fi
COMMON=(-I"$ROOT/src" -I"$ROOT/src/objc" -I/opt/homebrew/include
        -DANTI_SPV_DIR="\"$ROOT/src/vulkan/spv\""
        -D_DEBUG=1)
FRAMEWORKS=(-framework Cocoa -framework AppKit -framework QuartzCore
            -framework CoreGraphics -framework Metal -framework IOKit
            -framework IOSurface)

# --- the tree that matters ----------------------------------------------------
SOURCES=(
    src/main/vk_test.c
    src/vulkan/vulkan.c
    src/vulkan/vk_view.c
    src/vulkan/vk_scene.c
    src/vulkan/vk_iosurface.c
    src/vulkan/vulkan_mac.c
    src/objc/window_cocoa.m
    src/objc/panel_cocoa.m
    src/window/panel_bridge.c
    src/atomic/ring.c
    src/atomic/spin.c
    src/buffers/buffer.c
    src/buffers/color_buffer.c
    src/darling/container.c
    src/darling/panel.c
    src/darling/scene.c
    src/darling/canvas.c
    src/darling/picture.c
    src/oop/type.c
    src/oop/stride.c
    src/oop/struct.c
    src/oop/fields.c
    src/nio/mem.c
    src/thread/thread.c
    src/time/nanotime.c
    src/time/clock.c
    src/input/key.c
    src/input/mouse.c
    src/input/touch.c
    src/input/focus.c
    src/system/display_monitor.c
    src/system/display_info.c
    src/system/hardware_info.c
    src/system/graphics_info.c
    src/objc/discovery.m
    src/lang/mat4.c
    src/lang/vec2.c
    src/lang/vec4.c
    src/lang/fastmath.c
    src/struct/list.c
    src/struct/array.c
    src/struct/map.c
    src/struct/collection.c
    src/util/hash.c
)

MODE="${1:-}"

# --- AI bundle: sources + sibling headers, fresh every run --------------------
emit_bundle() {
    : > "$BUNDLE"
    local emitted=()
    for src in "${SOURCES[@]}"; do
        emitted+=("$src")
        hdr="${src%.*}.h"
        [[ -f "$ROOT/$hdr" ]] && emitted+=("$hdr")
    done
    for f in "${emitted[@]}"; do
        {
            echo ""
            echo "================================================================================"
            echo "FILE: $f"
            echo "================================================================================"
            cat "$ROOT/$f"
        } >> "$BUNDLE"
    done
    echo "bundle -> $BUNDLE ($(wc -l < "$BUNDLE") lines, ${#emitted[@]} files)"
}

if [[ "$MODE" == "txt" ]]; then
    emit_bundle
    exit 0
fi

# --- compile -------------------------------------------------------------------
OBJS=()
for src in "${SOURCES[@]}"; do
    obj="$OBJ/$(echo "$src" | tr '/' '_').o"
    OBJS+=("$obj")
    if [[ "$src" == *.m ]]; then
        $CC "${CFLAGS[@]}" "${COMMON[@]}" -x objective-c -fobjc-arc \
            -c "$ROOT/$src" -o "$obj"
    else
        $CC "${CFLAGS[@]}" "${COMMON[@]}" -c "$ROOT/$src" -o "$obj"
    fi
done

$CC "${OBJS[@]}" -pthread "${FRAMEWORKS[@]}" -o "$OUT"
echo "built -> $OUT"

emit_bundle

if [[ "$MODE" == "run" ]]; then
    shift || true
    exec "$OUT" "$@"
fi
