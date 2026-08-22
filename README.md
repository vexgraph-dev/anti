# anti, by Vex, truly.

A zero-allocation, relational C11 game engine — everything is a pointer.

`anti` is an absolute rejection of the traditional engine paradigm. There are no
object graphs, no garbage collectors, no hidden allocations. Memory is a
relational table: every block knows its own type and length, every pool is a
column store of equal-stride slots, and every registered symbol is a row whose
value is the address of another typed block. Pointers are first-class,
self-describing values — joinable without a registry lookup.

The result is a lock-free, cache-coherent core with predictable, microsecond-level
latency: C stripped of its comfort abstractions, rebuilt for raw performance.

## What's in this repo

* `src/oop/type.h` — the bit-packed type system (`Type`). One 32-bit masked ID
  encodes form, class, and variant; every block carries it in its header.
* `src/nio/mem.h/.c` — the memory lens (`Memory`). Every allocation is a
  self-describing block: `[type_id][length][payload]`. Walking back 16 bytes
  yields the header, so `Memory_type()`/`Memory_length()` are free.
* `src/bit/bit.h/.c` — the lockless width pool (`BitPool`). ABA-tagged
  freelists recycle slots; freed slots come back at the same address.
* `src/atomic/spin.h/.c` — `SpinLock` on C11 atomics (ticket lock, bounded
  wait).
* `src/atomic/ring.h/.c` — the MPMC ring buffer (`RingBuffer`), the inter-thread
  channel.
* `src/engine/loop.h/.c` — the fixed-timestep engine loop (`Loop`).
* `src/relational/variable.h/.c` — the relational symbol registry (`Variable`).
  Name → (classId, targetPointer): the table that makes everything-a-pointer
  work.
* `src/window/` — the macOS native window backend (`Window`, AppKit via
  Objective-C). No GLFW.
* `main.c` — the demo tying it together: 4 producers race into a shared ring,
  the engine loop drains it, `received=100/100`.

## Architecture in brief

* **Self-describing memory** — every pointer carries its own `type_id` +
  `length` header. A raw address *is* a typed value.
* **Zero steady-state allocation** — the arena doctrine carves one region from
  the OS and never allocates again; pools and rings live inside it.
* **Lockless subsystems** — producers and consumers talk through ABA-tagged
  slots and CAS, never mutexes.
* **Everything is a pointer** — symbols resolve to addresses, addresses decode
  themselves, and the relational engine joins them.

## Requirements

* A C11 compiler (Clang recommended) on macOS (arm64).
* CMake ≥ 4.3 and a macOS SDK for the `anti_window` target.

## Building & running

```bash
mkdir build-debug && cd build-debug
/Applications/CLion.app/Contents/bin/cmake/mac/aarch64/bin/cmake .. -DCMAKE_BUILD_TYPE=Debug
cmake --build .
./anti            # the headless demo — expect `received=100/100 ticks=N`
./anti_window     # the AppKit windowed demo
```

Flags enforced by `CMakeLists.txt`: `-Wall -Wextra -Werror -mcpu=apple-m1`.

## Engine runtime notes

* **Thread 0 owns AppKit** in the windowed target; the engine loop runs at a
  fixed timestep on its own thread and drains the shared ring.
* The `Loop` is the heart: `while (running) { tick(); }` at `frame_ms`
  resolution, stopping when the workload is done.

## Status

Foundation verified: `Type`, `Memory`, `BitPool`, `SpinLock`, `RingBuffer`,
`Loop`, `Variable`, and the window backend. `./anti` reliably delivers
100/100 jobs. The relational layer (typed accessors over `Variable`, per-type
primitive classes, structs over pools) is the active build-out.
