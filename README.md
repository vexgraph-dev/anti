# anti, by Vex, truly.

A radically unconventional framework for high-performance Java game engine development. 

## The Philosophy

"anti" is an absolute rejection of the traditional Java heap. It is a strict, less-or-zero-allocation, less-or-zero-GC architecture designed for maximum throughput and predictable microsecond-level latency. 

I (some) do not use the `new` keyword for data. I do not wrap primitives in objects. I do not box or unbox. I do not allow the Garbage Collector to pause the simulation (yup).

Everything, from our primitives and matrices to our circular spatial grids and network buffers, is packed into off-heap virtual memory using raw 64-bit pointers and the Foreign Function & Memory (FFM) API. The framework manages its own arenas, implements custom lockless free-lists, and manually dictates the memory lifecycle in a C-style paradigm, all while remaining within the JVM.

## Architecture

*   **Zero-GC Off-Heap Collections**: Structs, Lists, Arrays, and Spatial Partitioning Arrays (GridArray, CircularArray) are all allocated natively via `java.lang.foreign`.
*   **Bit-Packed Type System**: Types are globally tracked and accessed through a custom 32-bit integer masking system, allowing for blazing fast polymorphism without object headers.
*   **Raw Memory Dereferencing**: Fields are accessed directly via raw pointer offsets and `VarHandle` atomic operations.
*   **Multi-Threading**: Framework updates happen concurrently via dedicated lockless subsystems, interacting with ABA-tagged volatile memory.

This is Java stripped of its safety nets and rebuilt for raw performance.

## Development & Orchestration

This framework is built using an unconventional AI-orchestrated development process.

I leverage Google **[Antigravity](https://antigravity.google)** as my primary coding agent. However, the architecture is entirely mine. I orchestrate the AI's operations, strictly dictate its boundaries, and rigorously audit all generated code to ensure it flawlessly complies with my zero-allocation standards. The AI acts as a high-speed implementer of my architectural manifesto.

## Building & Dependencies

If you are exploring this codebase, be aware that it relies on a very specific set of native libraries to interface with the operating system and hardware directly. 

### Core Dependencies

*   **[OSHI](https://github.com/oshi/oshi)**: Used for low-level operating system and hardware information retrieval without relying on standard Java wrappers.
*   **[LWJGL 3](https://www.lwjgl.org/)**: The Lightweight Java Game Library. Used for raw native bindings to Vulkan, OpenGL, GLFW, OpenAL, and other C-libraries.

Both are vendored locally in `lib/` (an all-in-one LWJGL 3.4.2 custom distribution plus the OSHI FFM fork), so no network fetch is needed for the JVM path.

### Prerequisites

*   **JDK 25** — GraalVM 25 recommended (see §11 of `preferences.txt` for the native-image compatibility rules); any JDK 25 works for the JVM path. `run.sh` auto-detects it (`JAVA_HOME` overrides).
*   **Vulkan SDK** (macOS, optional) — enables validation layers. Auto-detected from the newest install under `~/VulkanSDK`; `VULKAN_SDK` overrides.

### How to Run

The canonical path builds everything and launches the windowed demo:

```bash
bash run.sh
```

`run.sh` is portable (no hardcoded user paths). It resolves a Java 25 JDK and the Vulkan SDK automatically, compiles all of `src/` plus `scratch/EngineTest.java` with `--release 25 --enable-preview`, then runs `EngineTest` under the same flags.

### Alternative Builds

**Terminal (javac / single-file runtime, no build tool required)** — use `run.sh` above. For manual compilation from an IDE's terminal:

```bash
javac -d out/production/anti -cp "out/production/anti:lib/*:lib/oshi/*:lib/lwjgl-release-3.4.2-custom/*" --release 25 --enable-preview $(find src -name "*.java" -not -path "*/api/*" -not -name "FFMRegistrationFeature.java") scratch/EngineTest.java
java --enable-preview --enable-native-access=ALL-UNNAMED -Xmx64m -cp "out/production/anti:lib/lwjgl-release-3.4.2-custom/*:lib/oshi/*" EngineTest
```

**Gradle ("elephant")** — requires a JDK 25; point `JAVA_HOME` at one if `gradlew` picks up a different JDK. For validation layers, `runEngine` expects the Vulkan SDK env (source the SDK's `setup-env.sh` first, as `run.sh` does automatically); otherwise it runs with validation unavailable.

```bash
./gradlew compileJava      # build the engine
./gradlew runEngine        # build + launch the windowed EngineTest demo
```

**Maven:**

```bash
mvn compile                # build the engine library (LWJGL from Central, OSHI from lib/)
```

Note: `mvn compile` builds the engine only — the `scratch/` demos are not part of the Maven build. Launch the demo via `bash run.sh` or `./gradlew runEngine` instead.

### Engine Runtime Notes

*   The demo boots the engine's **Core Draw Worker** (`thread.DrawThread`) to own the whole render loop — input dispatch, debounced swapchain rebuilds, and `produceOnce()`/`presentOnce()`. With a FIFO swapchain the worker sleeps on the display refresh (60/120Hz) right there; **Thread 0 is a pure AppKit event pump** that spins free and never sleeps.
*   On macOS the first launch auto-relaunches with `-XstartOnFirstThread` (the trampoline inside `EngineTest`).
*   Live hotkeys: `1`/`F1` and `5`/`F5` = FIFO (vsync), `2`-`4`/`F2`-`F4` = IMMEDIATE (uncapped or capped), toggling the swapchain present mode at runtime.

### How to Compile and Test

To compile or test this project, you must include the required library directories in your classpath. 

For manual compilation:
```bash
javac -d out/production/anti -cp "out/production/anti:lib/*:lib/oshi/*:lib/lwjgl-release-3.4.2-custom/*" src/<PreferredClass>.java
```

All empirical verification and runtime testing is done strictly via `scratch/ScratchTest.java` (using Java 26 preview features):
```bash
java --enable-preview --source 26 -cp "out/production/anti:lib/*:lib/oshi/*:lib/lwjgl-release-3.4.2-custom/*" scratch/ScratchTest.java
```
Will be using GraalVM Native Image for native compilation anytime I can get around to it.
