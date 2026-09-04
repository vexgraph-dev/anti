# vexspoke, by Vex, truly.

A zero-allocation, relational C23 platform library — everything is a pointer.

A play on the word **bespoke** — a *bespoken* C platform library tailor-crafted down to the cache line, register, and bit. `vexspoke` serves as the central spoke of the `vexgraph` vertical integration stack.

`vexspoke` is an absolute rejection of the traditional engine paradigm. There are no object graphs, no garbage collectors, and no hidden heap allocations. Memory is a relational table: every block knows its own type and length via a negative-offset header, every pool is a column store of equal-stride slots, and every registered symbol is a row whose value is the address of another typed block. Pointers are first-class, self-describing values — joinable without registry lookups.

The result is a lock-free, cache-coherent core with predictable, microsecond-level latency: C stripped of its comfort abstractions, rebuilt for raw, bare-metal performance.

---

## Workspace Integration & How to Use It

`vexspoke` is designed to be consumed as the foundational Layer 1 bedrock library within a vertically integrated ecosystem (such as `vexgraph`) or embedded standalone into custom applications:

```
vexgraph/
├── cmake-build-debug/           # Out-of-tree CMake build artifacts & staged SPVs
├── main/
│   └── vk_test.c                # Full-stack engine, window, and UI integration probe
├── projects/                    # Vertically integrated subsystem repositories
│   ├── vexspoke/                # Bedrock C23 platform runtime (this library)
│   │   └── src/                 # Memory pools, BitPacked OOP, atomics, math, loop
│   ├── hotcwap/                 # Dynamic hot-reloading & native OS windowing
│   │   └── src/                 # Window abstraction, AppKit Cocoa bridge, loader
│   ├── darling/                 # Retained-mode UI nodes & Vulkan render passes
│   │   └── src/                 # Canvas, panels, labels, font baking, CoreText, SDF
│   └── [other projects connecting to each other go here]
│       └── api-haven/           # Telemetry schemas & Discord webhook transmitters
├── CMakeLists.txt               # Umbrella workspace orchestrator
└── preferences.md               # Engine architectural style preferences (Rules 1–24)
```

### 1. In-Tree Integration (Subdirectory)
When nested inside an umbrella workspace, include `vexspoke` directly:

```cmake
# In your top-level CMakeLists.txt
add_subdirectory(projects/vexspoke)

add_executable(my_app main.c)
target_link_libraries(my_app PRIVATE vexspoke)
```

### 2. Standalone Integration (FetchContent Seam)
When building an independent downstream repository (`hotcwap`, `darling`, or external tools), guard with the target seam:

```cmake
if(NOT TARGET vexspoke)
    include(FetchContent)
    FetchContent_Declare(
        vexspoke
        GIT_REPOSITORY https://github.com/vexgraph-dev/vexspoke.git
        GIT_TAG main
    )
    FetchContent_MakeAvailable(vexspoke)
endif()

target_link_libraries(my_app PRIVATE vexspoke)
```

---

## What's in this repo

* **`src/annotation/`** — Zero-cost C23 static assert markers (`;;OVERVIEW`, `;;DRAFT`, `;;INTENTION`, `;;PLATFORM_EXCLUSIVE`).
* **`src/c23/constructor.h`** — Java-style arity constructor overloading (`Class(...)` $\rightarrow$ `Class_0`, `Class_1`) via pure preprocessor dispatch.
* **`src/nio/mem.h/.c`** — The self-describing memory lens (`Memory`). Every allocation carries `[type_id][length][payload]`. Walking back 16 bytes yields the header; `Memory_type()` and `Memory_length()` are free pointer subtractions.
* **`src/bit/bit.h/.c`** — The lockless width pool (`BitPool`). ABA-tagged freelists recycle slots; freed slots return at the *exact same address*.
* **`src/oop/type.h`** — Bit-packed type system (`Type`). One 32-bit masked ID encodes form, class, and variant.
* **`src/oop/Class.h/.c`** — Reflection and stride tables for off-heap structs.
* **`src/atomic/ring.h/.c`** — Lockless MPMC ring buffer (`RingBuffer`), the inter-thread messaging highway.
* **`src/atomic/spin.h/.c`** — C23 `stdatomic` ticket locks (`SpinLock`) with bounded spin backoff.
* **`src/relational/variable.h/.c`** — Relational symbol registry (`Variable`). Name $\rightarrow$ `(classId, targetPointer)`.
* **`src/lang/`** — Zero-allocation math primitives: `FastMath`, `Vec2`, `Vec3`, `Vec4`, and `Mat4`.
* **`src/struct/`** — High-performance off-heap collections: `List`, `Map`, `Queue`, `Deque`, `Stack`, `Set`, `MinHeap`, and `SparseSet`.
* **`src/io/vexhome.h/.c`** — Canonical `VexHome` user storage layouts (`~/vex`).
* **`src/io/file.h/.c`** & **`src/io/log.h/.c`** — Zero-allocation file operations and binary event logging.
* **`src/net/`** — Zero-allocation HTTP client, URL parser, JSON serializer, and TLS streaming abstractions.
* **`src/engine/loop.h/.c`** — Fixed-timestep engine loop (`Loop`).
* **`src/vulkan/`** — Core Vulkan instance, MoltenVK loader, swapchain management, and baseline SPIR-V shaders (`hello_triangle`, `solid_quad`).
* **`src/objc/`** — Hardware platform bridges: TouchID biometric authentication, Apple SecureTransport TLS, and CoreAudio.
* **`src/main/main.c`** — Standalone headless harness: 4 concurrent producer threads racing into a shared ring, verified at `received=100/100 ticks=1`.

---

## Architecture in brief

* **Self-describing memory** — Every pointer carries its own `type_id` + `length` negative-offset header. A raw pointer *is* a typed, introspectable value.
* **Zero steady-state allocation** — The arena doctrine carves memory once from the OS; pools, rings, and tables recycle memory in-place. Zero `malloc` in the frame loop.
* **Lockless concurrency** — Inter-thread work is distributed through ABA-tagged atomic slots and compare-and-swap (CAS), never blocking mutexes.
* **Relational joining** — Symbols resolve to typed addresses directly; relational queries join memory blocks without object graphs.
* **Strict C23 dialect** — Banned `->` arrow sugar (Rule 1), mandatory two-layer access cap (Rule 10), and destination-last parameter order (Rule 9).

---

## Requirements

* A modern C23 compiler (Clang recommended, `-std=gnu23` enabled).
* Apple Silicon (arm64 macOS) or Linux.
* CMake $\ge$ 4.3.
* Vulkan SDK (MoltenVK on macOS).

---

## Building & Verification

To build and run the standalone verification harness directly within `vexspoke`:

```bash
mkdir build-debug && cd build-debug
/Applications/CLion.app/Contents/bin/cmake/mac/aarch64/bin/cmake .. -DCMAKE_BUILD_TYPE=Debug
cmake --build . --target anti
./anti
```

**Expected output:**
```
== anti memory ==
type=0x20000001 len=16
== anti bit pool ==
recycled a -> c=0x10199ec60 (same=1)
== anti ring + spin + loop ==
...
received=100/100 ticks=1
```

Enforced compilation flags: `-Wall -Wextra -Werror -mcpu=native` (Apple Silicon host baseline).
