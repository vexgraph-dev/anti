# Lesson 1 — The Anatomy of a Program (From Source Code to Machine Code)

*Teacher's voice: You are not learning three languages. You are learning three different
ways to build one thing: a sequence of instructions that a CPU executes.*
That insight — *the CPU only ever runs machine code* — is the whole key. Everything else
is machinery that produces it, or machinery that surrounds it. Let's tear all three down
to that one common substrate.

---

## 1.1 The common end point: what the CPU actually runs

Every program you've ever shipped from `anti` — the JVM bytecode, the GraalVM `.app`, a
C binary — eventually becomes the exact same kind of thing on an Apple Silicon CPU:

```
Memory layout of a running process (macOS, arm64)

Low addresses
  ┌──────────────────────────────────────┐
  │ __TEXT     read-only code            │  instructions (ldr/str/bl/ret...)
  │           e.g. the compiled draw loop│
  ├──────────────────────────────────────┤
  │ __DATA     read-write globals        │  mutable state, static fields
  │           e.g. your free-list heads  │
  ├──────────────────────────────────────┤
  │ __DATA_CONST  read-only constants    │  string literals, vtable-ish tables
  ├──────────────────────────────────────┤
  │ __LINKEDIT  symbol table, dyld info  │  not executed, only for the loader
  ├──────────────────────────────────────┤
  │ HEAP  (grows up)                     │  malloc/arena/mmap memory
  ├──────────────────────────────────────┤
  │ STACK (grows down)                   │  function calls, locals, return addr
  │           per-thread                 │
  └──────────────────────────────────────┘
```

The CPU does not know what "Java" is. It does not know what "C" is. It knows
`ADD X0, X1, X2`, `LDR X3, [X0, #8]`, `BL <function>` and a few hundred cousins.
Both a JIT-compiled Java method and a clang-compiled C function end up as those
instructions in `__TEXT`.

That's the entire game: **every language decision you make is really a decision about
how much machinery sits between your source text and these instructions, and how much of
that machinery has to ship inside your binary.**

---

## 1.2 Java's pipeline (your current reality)

Java compiles in two stages, and that's already unusual.

```
Hello.java ──javac──▶ Hello.class (bytecode) ──JVM launch──▶ interpret + JIT ──▶ machine code
                            ▲                                              ▲
                            │                                    JIT compiles "hot" methods
                            │                                     at runtime, in the process
                      bytecode is a *portable* intermediate
                      that is NOT native to any CPU
```

- `javac` compiles to **bytecode**: a compact, CPU-independent instruction set that no
  CPU runs directly. This is Java's portability trick — *compile once, run anywhere* —
  and its performance tax simultaneously.
- At launch, the **JVM** (HotSpot) reads bytecode, interprets it, and watches which
  methods run hot. Hot methods get JIT-compiled by C2/Graal into real arm64 machine
  code **at runtime, inside your process**.
- Because compilation happens at runtime, the JIT knows the *actual* runtime types and
  can do aggressive optimization: escape analysis, scalar replacement, devirtualization.
  This is why a well-written Java hot path can be genuinely fast.

### The GraalVM Native Image branch (the one `anti` ships)

Your `build_native.sh` path short-circuits this. Native Image is itself written in Java;
it takes your `.class` files, does a **whole-program static analysis** starting at
`main()`, and ahead-of-time compiles everything reachable into one standalone arm64
Mach-O binary. This is called AOT (ahead-of-time).

But here is the detail that explains your ~22MB `.app`:

1. **Closed-world assumption.** Native Image must know *all* classes and methods at
   build time. No dynamic classloading at runtime. Everything reachable from `main()`
   gets compiled in. Unreachable code is dropped.
2. **SubstrateVM runtime is embedded.** Your binary has no JVM next to it — so Native
   Image bundles its own mini-runtime: a garbage collector (SerialGC by default),
   thread scheduler, exception/demultiplexing tables, deoptimization machinery, class
   metadata, and the reflection/string/class infrastructure. **That runtime ships
   inside every single binary you produce.** There is no shared "system JVM" on macOS
   it can lean on — unlike C, which leans on `libSystem` that every macOS process
   already loads.
3. **The Image Heap.** During the build, Native Image actually *executes* your code.
   Classes get initialized at build time; the objects and static fields they create
   (your `TypeRegister` constants, `StringLookup` strings, static `VarHandle`s, the
   reachable `java.lang.Class` objects) are **serialized into the binary** and copied
   into memory verbatim at process startup. It's not just code — it's *pre-built heap
   state embedded in the file*.

```
GraalVM native-image build
  *.class ──▶ static analysis (reachability from main)
         ──▶ AOT compile reachable code
         ──▶ EXECUTE your class initializers (build time!)
         ──▶ serialize resulting objects into Image Heap
         ──▶ link SubstrateVM runtime (GC, threads, metadata)
         ──▶ single Mach-O binary: __TEXT + __DATA(image heap) + runtime
```

### Why your binary is ~22MB and a C one is ~200KB

This is not a mystery — it's a floor. Measured, real numbers:

| Artifact | Size | Notes |
|---|---|---|
| `Hello.java` compiled to class | ~0.4 KB | just bytecode, no runtime |
| Same program via GraalVM 22.2 native-image (Linux x64) | ~12.4 MB | SubstrateVM + JDK reachable code |
| GraalVM's own docs: HelloWorld floor | ~7 MB | what you can't go under |
| Same program compiled with `gcc hello.c` | ~16 KB | leans on macOS libSystem |

GraalVM maintainers were asked directly whether the SubstrateVM could ship as a shared
library like libc. Answer: **no plans**. The JDK standard-libraries-as-native-dylib idea?
Also no. So that 7–12MB floor is permanent for *any* GraalVM native binary.

When you open GraalVM's Build Report, you'll find the space isn't your engine — it's
`java.util`, `java.text`, `java.time`, `java.util.regex`, string/class metadata, and the
image heap. A `System.out.printf` alone drags the entire `java.text.format` subsystem
into reachability. Your engine code is often under 1% of the binary.

> **Why C is small:** A C program doesn't embed a runtime. It assumes the OS. On macOS,
> `libSystem` (a superset of libc + pthread + dyld glue) is already mapped into every
> process by the kernel. Your binary only contains *your* code, plus whatever you
> statically linked. Your `__TEXT` section is literally your instructions. A 200KB
> binary is not "a small C program" — it's "a program that owns its whole runtime."

---

## 1.3 C's pipeline

```
hello.c ──clang -O2──▶ hello.s (assembly) ──as──▶ hello.o (object, relocatable)
hello.c ──clang -O2──▶ hello.o                        │
                                                     │
   hello.o + other.o + libSystem.dylib ──ld (linker)──▶ hello (Mach-O executable)
```

1. **Preprocessor** (before compilation proper): resolves `#include`, `#define`,
   `#ifdef`. Pure text substitution. (This is C's "classloading" story — see Lesson 4.)
2. **Compiler** (`clang`): the *entire* program transformation happens here, at build
   time, once. Type checking, optimization (`-O2`, inlining, constant folding), and
   codegen. Output: assembly → machine code in a relocatable object file.
3. **Linker** (`ld`): stitches `.o` files together, resolves symbols against each other
   and against dynamic libraries (`libSystem`, `libMoltenVK.dylib`), assigns final
   addresses, and emits the Mach-O executable with its `__TEXT`/`__DATA`/`__LINKEDIT`
   sections.

**There is no runtime phase.** No interpreter, no JIT, no image heap, no GC built in.
The binary *is* the final artifact. When `main()` runs, the first instruction is the
first instruction of your `main()`.

```
C binary layout: everything is explicit
  __TEXT  = your compiled functions
  __DATA  = your globals (your free-list heads, your pools)
  __LINKEDIT = symbol names for the debugger
  HEAP    = what YOU allocate with mmap/malloc/arenas
  STACK   = call frames
No hidden runtime. No image heap. No GC embedded. The OS provides libSystem only.
```

Nothing else is injected by the language itself. Whatever is in the final binary is
code you wrote, headers you included, or libraries you linked. That is the entire
point of the C choice: **no hidden machinery, no runtime you didn't opt into.** (The
bloat traps that *would* appear in other toolchains — and why C skips them — are
Lesson 6.)

---

## 1.4 The real `main()` entrypoint (and why Java hides it)

- In Java, execution starts in the JVM, which locates your `main` method through
  reflection and invokes it. `Thread 0` is the AppKit pump in `anti`'s `run.sh`
  because the JVM's main thread is *created by the JVM*, not by the OS as the process
  entry.
- In C on macOS, the Mach-O entry point is `_main` (after dyld's `start` runs). The OS
  kernel hands the CPU to dyld; dyld loads your dylibs, runs constructors, then calls
  your `main(int argc, char** argv)`. Your `main` *is* the process — no one invented a
  thread for you. When `main` returns, the process exits.

```c
// C: you are the entry point. Nothing before you except dyld.
int main(int argc, char** argv) {
    // Thread 0 = this thread. AppKit wants main-thread ownership.
    // That's why anti needs -XstartOnFirstThread on the JVM — the JVM
    // normally gives you a *new* thread for main(); on macOS, AppKit
    // requires the OS-created main thread. In C this "problem" doesn't exist:
    // your process's thread 0 IS the main thread, always.
    return 0;
}
```

This one fact explains a whole class of macOS weirdness you've felt: `-XstartOnFirstThread`,
the `macOSWindow.java` event pump living on "Thread 0", the CF/AppKit main-thread
constraints. In C, you *are* the main thread from the first instruction. That AppKit
compatibility requirement becomes a non-issue instead of a launch flag.

---

## 1.5 What this means for `anti` — concretely

- Your GraalVM `.app` ships: your code (a fraction) + the SubstrateVM GC/threading/
  metadata runtime (a chunk) + the reachable JDK (a chunk) + a serialized Image Heap
  (a chunk) — all *per-binary, per-machine-arch*.
- Your C binary would ship: your code + whatever you statically link (MoltenVK/Vulkan
  loader stays a `.dylib` like it already is for the JVM path) — and then *stop*. The
  OS already loaded `libSystem`.
- Every memory-model subtlety of `anti` (off-heap, manual headers, lockless lists) is
  *native memory code* already. The hardware-reality framing you think in — headers,
  offsets, tags, CAS — is the C memory model. Java is the layer *surrounding* it that
  you've chosen to strip down.

### Actionable takeaway

Run GraalVM's build report on your current `anti` build (`-H:+DashboardAll`), and
inspect how much of the binary is `java.*` and image-heap vs. `anti.*`. You'll likely
find your own code is <5% of the file. That single report is the entire justification
for this masterclass: **you are paying ~20MB and a VM harness to run code that is
already written like C.**

---
*Next: Lesson 2 — The Truth About Memory & The "Heap".*
