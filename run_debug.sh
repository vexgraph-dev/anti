#!/bin/bash
# run_debug.sh — risk-free debug build + run.
#
# Builds a separate Debug configuration (never touches CLion's cmake-build-debug)
# with the full clang sanitizer stack on, then runs the binary under it:
#
#   AddressSanitizer   — heap/stack/use-after-free, buffer overruns
#   UndefinedBehavior  — signed overflow, bad shifts, misaligned access...
#   malloc guardrails  — MallocScribble/GuardEdges/StackLogging via env
#
# Usage:
#   ./run_debug.sh                  # build+run target 'anti'
#   ./run_debug.sh anti_window      # pick another target
#   ./run_debug.sh anti --lldb      # drop into lldb if a crash fires
#
# Env overrides:
#   ANTI_DEBUG_BUILD=build-debug    # alternate build dir

set -euo pipefail

# --- locate cmake (CLion-bundled first, then PATH) ---------------------------
CLION_CMAKE="/Applications/CLion.app/Contents/bin/cmake/mac/aarch64/bin/cmake"
if [ -x "$CLION_CMAKE" ]; then CMAKE="$CLION_CMAKE";
elif command -v cmake >/dev/null 2>&1; then CMAKE="$(command -v cmake)";
else echo "error: cmake not found (open CLion once, or install it)"; exit 1; fi

TARGET="${1:-anti}"
LLDB_MODE="${2:-}"
BUILD_DIR="${ANTI_DEBUG_BUILD:-build-debug}"

# --- sanitizer flags ----------------------------------------------------------
# -fno-omit-frame-pointer      : keep real stack traces in ASan reports
# -fno-optimize-sibling-calls  : tail-call elision hides frames from ASan
# -fno-sanitize-recover=all    : die on FIRST UB instead of printing + continuing
SAN_FLAGS=(-fsanitize=address,undefined
           -fno-omit-frame-pointer
           -fno-optimize-sibling-calls
           -fno-sanitize-recover=all)

echo "== configuring Debug build (sanitizers on) =="
"$CMAKE" -S . -B "$BUILD_DIR" \
    -DCMAKE_BUILD_TYPE=Debug \
    -DCMAKE_C_FLAGS="${SAN_FLAGS[*]}" \
    -DCMAKE_OBJC_FLAGS="${SAN_FLAGS[*]}" \
    >/dev/null

echo "== building $TARGET =="
"$CMAKE" --build "$BUILD_DIR" --target "$TARGET"

BIN="$BUILD_DIR/$TARGET"
if [ ! -x "$BIN" ]; then echo "error: $BIN missing after build"; exit 1; fi

# --- runtime armor ------------------------------------------------------------
# macOS has no LeakSanitizer, so use the malloc debugger instead.
export MallocGuardEdges=1
export MallocScribble=1          # fill freed memory with 0xaa
export MallocStackLogging=1      # remember backtrace of every allocation
export MallocStackLoggingNoCompact=1

# halting behavior: first error aborts the process, then report stack traces.
export ASAN_OPTIONS="halt_on_error=1:abort_on_error=1:strict_string_checks=1:detect_leaks=0"
export UBSAN_OPTIONS="halt_on_error=1:print_stacktrace=1"

echo "== running $BIN under ASan/UBSan =="
if [ "$LLDB_MODE" = "--lldb" ]; then
    lldb -o run -o bt -- "$BIN"
else
    exec "$BIN"
fi