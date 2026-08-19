# Lesson 10 — The Ultimate Decision Matrix & Migration Blueprint

*Teacher's voice: Every lesson before this one was a lens. Now we stop looking through
lenses and look at the actual decision, scored honestly, then plan the move so it can't
set the project on fire.*

---

## 10.1 The honest head-to-head scorecard

Scoring: 1–5, where 5 = best. Weighted toward *an engine that is already written as
"C pretending to be Java"* — that's the whole premise of `anti`.

| Criterion | Java (FFM) | Pure C11 | Orthodox C++ | Why |
|---|---|---|---|---|
| **Binary size** | 1 (7–22MB floor) | 5 (~200KB) | 5 (`-fno-exceptions -fno-rtti -nostdinc++`) | GraalVM embeds SubstrateVM + image heap; native ships only your code |
| **Startup latency** | 3 (image-heap copy + runtime init) | 5 (dyld + your main) | 5 | C starts in microseconds; GraalVM in milliseconds |
| **Runtime memory overhead** | 3 (JVM/GC metadata + runtime objects) | 5 (only what you allocate) | 5 | native code owns every byte |
| **Hot-path latency** | 3 (FFM calls ~3–46ns, JIT warmup) | 5 (one `bl`) | 5 | the boundary tax disappears |
| **GC pressure** | 4 (you avoid the heap, but runtime exists) | 5 (no GC) | 5 | C has no GC to avoid |
| **Memory safety** | 4 (GC + bounds checks, FFM itself unsafe) | 2 (ASan/UBSan must be your net) | 2 | C's only real weakness |
| **Dev speed / iteration** | 4 (JIT, rich tooling) | 3 (compile+link, LLDB) | 4 (+ sugar) | Java's loop is slower but safer |
| **Profiling / debugging** | 3 (JFR/JMC, but you fight the VM) | 5 (Instruments, LLDB, ASan) | 5 | native tools see real symbols |
| **Interop w/ AppKit/Metal/Vulkan** | 2 (FFM shims, 25+ MethodHandles, layout mismatch risk) | 5 (direct headers + `.m`) | 5 | native code *is* the ecosystem |
| **Engine ecosystem** | 4 (LWJGL + your own libs) | 3 (you write everything) | 4 (headers; STL only if restrained) | `anti` already wrote its own stdlib |
| **Migration cost** | 5 (you're here) | 3 | 3 | honest: a port is a port |
| **Long-term macOS/ARM fit** | 3 (GraalVM Apple Silicon young) | 5 (first-class) | 5 | Apple Silicon native is the target |

**Totals (unweighted):** Java **39** · Pure C **52** · Orthodox C++ **55**

> Read the C++ score correctly: those +3 points are *ergonomics sugar* (operator
> overloading, namespaces, destructors) available only under permanent self-discipline
> flags (Lesson 6) — vigilance you'd carry forever. Pure C matches it on every
> *binary/memory/interop* criterion that actually ships, with zero feature-discipline
> tax. **The choice is C — that's locked in (10.2).** The matrix exists to show the
> deliberation, not to reopen it.

---

## 10.2 Why the answer is C (locked in)

The matrix says it plainly, and the earlier lessons back it up:

1. `anti` already rejects OOP and already speaks native memory — the code is *written
   like C* (Lesson 1). Porting to C is translation, not reinvention.
2. Your math, strings, and containers are already hand-rolled (`{char*, size_t}`,
   `Bit32` pools, grids). C requires nothing you don't already own (Lesson 6).
3. The ABI is clean, the symbols are your names (Lesson 4), and tooling sees your real
   code in every profiler and crash log.
4. The features a fancier language would offer (operator overloading, namespaces,
   automatic cleanup) are the same discipline burden you'd carry to stay lean (Lesson 6)
   — except C's discipline is *shaped by the language*, not self-imposed on top of it.
5. C and the rest of the stack coexist: the `.m` AppKit shim (Lesson 5), the Vulkan
   loader, your future scripting VM (Lesson 17) — all native, all C-compatible, all
   already speaking the same ABI.

**The decision: Pure C11 core + a thin Objective-C `.m` shim for AppKit**, with the
existing Vulkan + MoltenVK pipeline unchanged. Everything that follows in the migration
plan assumes exactly this.

---

## 10.3 When to keep Java instead

Stay on the JVM path when *any* of these is your dominant constraint:

- **Dev speed is worth more than the 20MB.** If you ship experiments weekly and the
  agentic tooling you built around Java (the `anti` dev loop, hot-reload, reflection,
  your whole `src/` HTTP/WS/JSON stack) is your actual product, don't migrate.
- **You value the GC as a safety net** more than you value its absence. `anti` already
  dodges it in hot paths, but the rest of the runtime still benefits.
- **You intend to reuse JDK libraries** (networking, JSON, security) heavily. In C you
  rewrite or vendor those (Lesson 4/10) — real cost.
- **You need cross-platform without a platform layer.** Java + LWJGL covers Win/Linux/
  mac with near-zero per-platform code; native C needs a platform shim per OS.

If migration is on the table at all, the honest trigger is: **the 20MB binary, the VM
harness in every `.app`, and the FFM toll on every system call are a cost you can
remove in ~6 phases below — and your architecture was built to survive it.**

---

## 10.4 The step-by-step, risk-free porting plan for `anti`

Six phases. Each phase ends with a **compilable, runnable milestone** and keeps the
Java build green the whole time (parallel tracks, property-test equivalence).

### Phase 0 — Toolchain & skeleton (a day)
- Install: `brew install llvm` (or use Xcode's clang), CMake or a plain Makefile.
- Create `transition/` layout:
```
anti/
  include/anti/     # public headers: oop.h, bit.h, thread.h, window.h, vulkan.h
  src/              # .c / .m implementations
  platform/macos/   # .m AppKit shims
  tests/            # C tests + equivalence tests vs the Java build
```
- Build flags from Lesson 6 (`-O2 -Wall -Wextra -Werror -ffunction-sections -Wl,-dead_strip`)
  and a `debug` variant with `-fsanitize=address,undefined -g`.
- **Milestone:** `main()` opens a window via the `.m` shim, creates a CAMetalLayer,
  clears it to dark, presents. (You already know this code in Java — Lesson 5's
  whole point.)

### Phase 1 — Primitives & memory (the foundation)
- Port `ForeignMemory` → `anti_memory.c`: `mmap`-backed arena (`anti_arena_alloc`,
  `anti_arena_reset`), `calloc`-based `anti_malloc`, a freelist pool.
- Port `bit.Bit8/16/32/64` → `anti_bit.c` with `_Atomic` freelist heads (Lesson 7's
  translation is verbatim). Your ABA tag + per-thread cache design ports unchanged.
- Port `oop.TypeRegister` → `anti_oop.h` (enum constants) and `oop.Struct` →
  `anti_oop.c` (runtime layout engine; Lesson 3).
- **Milestone:** a C test that allocates/frees/reads a struct through your pools and
  **matches the Java build byte-for-byte** for the same operations (write a small
  property test that feeds identical inputs to both and compares outputs).

### Phase 2 — Strings & containers (your stdlib, already written)
- Port `primitive.string`, `StringLookup`-style off-heap strings (`{char*, size_t}`).
- Port the container layer you use: `spatial.GridArray`, `RingBuffer`, `Trie`, hash
  tables — as C structs with explicit length (Lesson 3/9 rules).
- **Milestone:** C build can load a scene file, build a grid, run a spatial query and
  match Java's results.

### Phase 3 — Window & AppKit shim (the `.m` file)
- Port `macOSWindow.java` → `platform/macos/macos_window.m`: NSWindow + CAMetalLayer
  creation, event pump on the main thread, cursor lock/warp, CGEventTap (Lesson 5 —
  25 MethodHandles → ~25 ObjC lines).
- Expose it through `include/anti/window.h` as plain C functions.
- **Milestone:** the C binary runs the same window + input + resize behavior as the
  JVM build. Keep the JVM build running side-by-side and diff behavior.

### Phase 4 — Vulkan draw loop (mechanical, most tedious)
- Link MoltenVK + Vulkan headers (same `natives/` dylibs, same loader).
- Port `src/vulkan/*` — the draw/present split, swapchain FIFO/IMMEDIATE toggles,
  staging/command buffers, descriptor sets (Lesson 8). Expect ~200 struct-initialization
  conversions; every `.set()` → struct field.
- **Milestone:** C binary renders the same demo frame as the Java binary. Then delete
  LWJGL from the dependency graph entirely.

### Phase 5 — Spatial / ECS / rest of the engine
- Port `spatial.*`, `entity.*`, remaining subsystems in dependency order. Because
  every subsystem already speaks native memory, this is largely syntax translation.
- Keep your agentic dev workflow: have the agent port file-by-file, you audit the
  diffs (same as today's workflow per your README).

### Phase 6 — Ship & harden
- Wire `-fsanitize=address,undefined` into CI and run all tests + demos.
- Instruments leak runs; LLDB crash triage; set up `-Werror` so nothing slips.
- Package: a ~200KB–2MB `.app` (your code + MoltenVK + frameworks, no JVM). Compare
  startup, memory, and frame time against the GraalVM build. You'll have the numbers
  the masterclass predicted, measured on your own machine.

---

## 10.5 The final mental model

You are not migrating *to* C. **You are migrating `anti` back to the substrate it was
already written for.** Your type headers, your lockless pools, your off-heap strings,
your manual ownership — every one of them is native-memory code wearing a Java uniform.
C is the uniform that fits.

The three words that decide it:

1. **Trust** — do you trust ASan/UBSan + `-Werror` + your audit loop as much as you
   trusted the GC? (Your workflow already says yes.)
2. **Cost** — is ~20MB per `.app` and an FFM toll on every system call a cost you want
   to keep paying forever? (Your architecture says no.)
3. **Control** — do you want a language with zero hidden machinery, where the binary is
   exactly your code and nothing else? (Lesson 6 says that's the whole point.)

**Decision: C11 core + `.m` AppKit shim. Locked in.** The masterclass's lessons gave
you the vocabulary to defend it, and Phase 0 through 6 gives you the exit ramp to test
it without burning the ship.

*End of masterclass. Go build the thing.*
