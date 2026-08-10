# anti, by Vex, truly.

A zero-allocation, off-heap game engine framework for Java.

`anti` is an absolute rejection of the traditional Java heap. Primitives, matrices, collections, and network buffers live in native memory managed through the Foreign Function & Memory (FFM) API, addressed as raw 64-bit pointers with hand-written lockless free-lists and arenas. I do not use the `new` keyword for data. I do not wrap primitives in objects. I do not box or unbox. I do not allow the Garbage Collector to pause the simulation (yup).

The result is a GC-pressure-free core with predictable, microsecond-level latency — Java stripped of its safety nets, rebuilt for raw performance.

## What's in this repo

* `src/` — the engine. Off-heap primitives (`primitive.*`), a bit-packed type system (`oop.*`), flat spatial partitioning (`spatial.*`), lockless threading (`thread.*`), and more.
* `src/vulkan/` — the Vulkan render pipeline (LWJGL bindings), including a decoupled draw/present renderer (draw thread produces off-screen frames; a separate present thread blits to the swapchain at the display cadence).
* `src/window/` — a macOS native window backend (`macOSWindow`) that drives AppKit and Metal directly through FFM `objc_msgSend` — no GLFW.
* `src/process/EngineTest.java` — the windowed demo entry point: opens an 800x600 window, boots Vulkan on a CAMetalLayer, and runs the draw/present loop.
* `lib/` — vendored LWJGL 3.4.2 custom distribution plus an OSHI FFM fork; no network fetch needed for the JVM path.
* `natives/` — the native dylibs (`liblwjgl`, `libMoltenVK`, `libglfw`) used by the JVM path.
* `scratch/` — experimental demos and tests.
* `out/dist/` — packaged `.app` bundles (JVM-bundled and GraalVM native).

## Architecture in brief

* **Zero-GC off-heap collections** — structs, lists, arrays, and spatial partitioning grids allocated natively via `java.lang.foreign`, never `new`.
* **Bit-packed type system** — 32-bit masked type IDs give polymorphic access without object headers.
* **Raw memory access** — fields read/written through pointer offsets and atomic `VarHandle` operations.
* **Lockless subsystems** — dedicated threads (Draw, Networking, Scripting) communicate through ABA-tagged volatile memory.

## Development & Orchestration

This framework is built using an unconventional agentic coding process.

An agent does the typing, but the architecture is entirely mine. I dictate the boundaries, and every line it writes gets audited by me before it's let anywhere near the core. Nothing ships until it complies with my zero-allocation standards. The agent is a high-speed implementer of my architectural manifesto — and I read the diff.

## Requirements

* **JDK 25** — GraalVM 25 recommended for the native-image path; any JDK 25 works for the JVM path. `run.sh` auto-detects it (`JAVA_HOME` overrides).
* **Vulkan SDK** (macOS, optional) — enables validation layers. Auto-detected from the newest install under `~/VulkanSDK`; `VULKAN_SDK` overrides.

## Building & running

The canonical path compiles everything and launches the windowed demo:

```bash
bash run.sh
```

`run.sh` resolves the JDK and Vulkan SDK automatically, compiles all of `src/` plus the demo, and runs `EngineTest`. On macOS the process relaunches itself with `-XstartOnFirstThread` so the AppKit event pump owns the main thread; a Core Draw Worker owns the render loop.

Packaged `.app` bundles:

```bash
bash scratch/scripts/build_jpackage.sh   # JVM-bundled .app (full JVM included)
bash scratch/scripts/build_native.sh     # GraalVM native-image .app (~20MB, no JVM)
```

Gradle / Maven:

```bash
./gradlew compileJava    # build the engine
./gradlew runEngine      # build + launch the windowed demo
mvn compile              # engine only (demos live in scratch/)
```

## Engine runtime notes

* **Thread 0 is a pure AppKit event pump** — it spins free, dispatching input and never touching Vulkan. The Core Draw Worker (`thread.DrawThread`) owns the render loop: input dispatch, debounced swapchain rebuilds, and `produceOnce()`/`presentOnce()`. In FIFO mode the worker sleeps on the display refresh (60/120Hz); the present thread is software-paced in IMMEDIATE mode.
* **Live hotkeys**: `1`/`F1` and `5`/`F5` switch to FIFO (vsync); `2`-`4`/`F2`-`F4` switch to IMMEDIATE, toggling the swapchain present mode at runtime.

## Status

Much of the framework is implemented and verified: off-heap primitives, structs, collections, threading, HTTP/WS/JSON, security, windowing, and the Vulkan pipeline. Several subsystems are still stubs or early drafts (draw pipeline, ECS entities, mesh/materials, audio DAW, spatial trees). Native `.app` packaging is under active work.
