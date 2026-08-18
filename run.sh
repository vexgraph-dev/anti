#!/bin/bash
# Build and run the Anti Engine windowed demo with Vulkan SDK validation layers
# active, under a Java 25 runtime. Fully portable: JAVA_HOME and the Vulkan SDK
# are resolved dynamically (no hardcoded user paths). Override with JAVA_HOME and
# VULKAN_SDK env vars if auto-detection misses your setup.
#
# This is the committed, public launcher (mirrors scratch/run.sh).
#
# Usage examples:
#   bash run.sh             # canonical: build everything then launch the demo
#   JAVA_HOME=... bash run.sh

# 1. Locate the repository root (this script lives at the root of the repo)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"

# 2. Locate a Java 25 JDK (JAVA_HOME wins, then the standard macOS GraalVM install paths)
if [ -z "$JAVA_HOME" ]; then
    for candidate in \
        "$HOME/Library/Java/JavaVirtualMachines"/graalvm-jdk-25/Contents/Home \
        /Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home \
        /Library/Java/JavaVirtualMachines/graalvm-jdk-25/Contents/Home; do
        if [ -x "$candidate/bin/java" ]; then
            JAVA_HOME="$candidate"
            break
        fi
    done
fi
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
    echo "error: no Java 25 JDK found. Install GraalVM 25 (or any JDK 25) and/or set JAVA_HOME." >&2
    exit 1
fi
echo "[run.sh] Using JAVA_HOME=$JAVA_HOME"

# 3. Locate the Vulkan SDK (VULKAN_SDK wins, then the newest install with a setup-env.sh under ~/VulkanSDK)
if [ -z "$VULKAN_SDK" ]; then
    VULKAN_SDK_DIR="$(for d in "$HOME"/VulkanSDK/*/; do [ -f "$d/setup-env.sh" ] && printf '%s\n' "$d"; done | sort -V | tail -n 1)"
    if [ -n "$VULKAN_SDK_DIR" ]; then
        VULKAN_SDK="${VULKAN_SDK_DIR%/}"
    fi
fi
if [ -n "$VULKAN_SDK" ] && [ -f "$VULKAN_SDK/setup-env.sh" ]; then
    echo "[run.sh] Using Vulkan SDK: $VULKAN_SDK"
    source "$VULKAN_SDK/setup-env.sh"
else
    echo "Warning: Vulkan SDK setup-env.sh not found (set VULKAN_SDK to the SDK root to enable validation layers)"
fi

# 4. Change directory to project root so relative paths for resources (like shaders) resolve correctly
cd "$ROOT_DIR" || exit 1

# 4b. GraalVM-native readiness gate: compile the standalone analyzer and lint the
#     engine source for §13 denylist violations + FFM descriptor gaps.
if [ "${ANTI_SKIP_GRAAL_CHECK:-0}" != "1" ]; then
    bash scratch/scripts/graal_check.sh --root src || {
        echo "[run.sh] GraalVM analyzer gate FAILED (set ANTI_SKIP_GRAAL_CHECK=1 to bypass)"; exit 1;
    }
fi

LWJGL_CP="lib/lwjgl-release-3.4.2-custom/lwjgl.jar:lib/lwjgl-release-3.4.2-custom/lwjgl-vulkan.jar:lib/lwjgl-release-3.4.2-custom/lwjgl-stb.jar:lib/lwjgl-release-3.4.2-custom/lwjgl-openal.jar:lib/lwjgl-release-3.4.2-custom/lwjgl-unsafe.jar:lib/lwjgl-release-3.4.2-custom/lwjgl-natives-macos-arm64.jar:lib/lwjgl-release-3.4.2-custom/lwjgl-vulkan-natives-macos-arm64.jar"
CP="out/production/anti:$LWJGL_CP"

# 5. Build the whole project fresh before running.
#    - Skip the native-image-only FFMRegistrationFeature (needs the GraalVM hosted SDK, not
#      available on the JVM classpath), the nested src/api sub-repository, and the
#      src/graal analyzer package (standalone tool, never shipped in the engine).
#    - Recompile EngineTest into out/production/anti so the JVM can load it as a class.
echo "[run.sh] Building project..."
SOURCES=$(find src -name "*.java" -not -path "*/api/*" -not -name "FFMRegistrationFeature.java" -not -path "*/graal/*")
"$JAVA_HOME/bin/javac" --release 25 --enable-preview -cp "$CP" -d out/production/anti $SOURCES || {
    echo "[run.sh] BUILD FAILED"; exit 1;
}
# Make sure the compiled SPIR-V shaders (gradient/triangle) are on the classpath
# the same way build_native.sh embeds them, so getResourceAsStream() resolves them.
mkdir -p out/production/anti/vulkan/spv
cp src/vulkan/spv/*.spv out/production/anti/vulkan/spv/
echo "[run.sh] Build OK."

if [ "$1" == "--native" ]; then
    echo "[run.sh] Compiling FFMRegistrationFeature against GraalVM hosted SDK..."
    "$JAVA_HOME/bin/javac" --release 25 --enable-preview \
        --module-path "$JAVA_HOME/jmods" --add-modules org.graalvm.nativeimage \
        -cp "$CP" \
        -d out/production/anti \
        src/config/FFMRegistrationFeature.java

    echo "[run.sh] Building standalone Native Binary..."
    # FFM downcalls are registered programmatically via FFMRegistrationFeature
    # (no tracing agent required). --enable-native-access keeps the FFM runtime happy.
    "$JAVA_HOME/bin/native-image" \
        --no-fallback \
        --enable-preview \
        --enable-native-access=ALL-UNNAMED \
        --gc=epsilon \
        -H:+UnlockExperimentalVMOptions \
        -H:+ForeignAPISupport \
        -H:+SharedArenaSupport \
        -Os \
        --emit build-report \
        -H:IncludeLocales=en \
        -H:+RemoveUnusedSymbols \
        -cp "$CP" \
        --features=config.FFMRegistrationFeature \
        -H:ConfigurationFileDirectories=native-image-config \
        --initialize-at-run-time=window.macOSWindow \
        --initialize-at-run-time=nio.ForeignMemory \
        -H:IncludeResources='.*\.spv|StringLookup\.ini' \
        -Dmac.firstThread=true \
        process.EngineTest \
        AntiEngine

    echo "[run.sh] Build complete! Run it with: ./AntiEngine"
    exit 0
else
    # Normal JVM Run
    # 6. Run EngineTest
    export MTL_CAPTURE_ENABLED=1
    "$JAVA_HOME/bin/java" --enable-preview --enable-native-access=ALL-UNNAMED -Xmx64m \
        -cp "$CP" \
        process.EngineTest
fi