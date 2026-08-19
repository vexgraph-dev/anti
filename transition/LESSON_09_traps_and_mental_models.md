# Lesson 9 — Red Herrings, Traps & Mental Models (What to Watch Out For)

*Teacher's voice: The last lesson before the decision. You need to fear the right
things and stop fearing the wrong ones. Java lied to you in one specific way: it made
unsafe operations *impossible to express*. C makes them *trivially expressible and
quietly fatal*. That's not a reason to run from C — it's a reason to learn precisely
which six or seven mistakes cause 99% of the pain, and build your debug routine around
them.*

---

## 9.1 Undefined Behavior (UB) — the taxonomy you must know

Undefined Behavior isn't "a crash." It's "the compiler and CPU are allowed to do
*anything* — including the thing that looks like it works, until it doesn't." The
optimizer assumes UB never happens and transforms accordingly. The classic ones in
engine code:

**1. Use-after-free / dangling pointers**
```c
struct Entity* e = entity_create();
free(e);            // e is now a "dangling" pointer
e->hp = 0;          // UB: reads/writes freed memory
                    // might work... until the allocator reuses that block for something else
```
Java has a GC precisely to make this *unthinkable*. `anti` already freed
`ForeignMemory` manually (`freeNative`), so you've danced near this — but C gives you no
safety net if your arena releases memory while another thread still has a pointer.

**2. Buffer overflows (out-of-bounds)**
```c
int32_t arr[8];
arr[12] = 42;       // UB: writes 4 bytes past the array into whatever's adjacent
```
Java bounds-checks *every* array access (that's part of why it's slower; the JIT tries
to elide it). C never checks. Your whole `oop.Struct` + `Bit32` world is "one big byte
array" — an off-by-one in a stride calculation is an instant silent-corruption bug.

**3. Signed integer overflow**
```c
int32_t x = INT32_MAX;
x + 1;              // UB in C (wraps to negative in practice, but the optimizer may
                    // assume it never happens and "prove" things that are false)
```
Use `uint32_t`/`int64_t` or `-fwrapv` if you want defined wrap-around (the same way
Java's `int` arithmetic wraps).

**4. Unaligned access** (covered in Lesson 3) — tolerated slowly by plain loads, *fatal
for atomics and NEON* on Apple Silicon.

**5. Strict aliasing violations** — casting `float*` to `int*` and writing through it.
Use `memcpy` or `union` (or `-fno-strict-aliasing`) to move bits between types.

**6. Race conditions** — Lesson 7's entire subject. A non-atomic read while another
thread writes = UB (data race) even if "it usually works."

> **The single most useful reframe:** In Java, "safe" was the *default* and you fought
> the runtime to get performance. In C, "fast and unsafe" is the default and you fight
> *yourself* to stay correct. Everything else in this lesson is damage control for
> that shift.

---

## 9.2 Your safety nets (use them, not the debugger, first)

### Compiler warnings as errors — the free static analyzer
```
clang -std=c11 -Wall -Wextra -Wpedantic -Werror
```
`-Werror` makes warnings build-breaking. Treat a warning as a bug report. `clang`'s
warnings catch the "obvious" UB; `clang --analyze` catches more; `scan-build` runs the
whole static analyzer over your build.

### AddressSanitizer + UndefinedBehaviorSanitizer — the runtime detective
```
clang -fsanitize=address,undefined -g -o anti src/*.c   # debug build
./anti                                                    # ASan instruments every access
```
- **ASan** (AddressSanitizer) instruments every load/store to catch: use-after-free,
  heap/stack/buffer overflows, double-free, leaks. It works *extremely well* and is the
  single highest-leverage tool for a Java→C port. It slows your debug build ~2x — that
  is the tax for having no GC to babysit you.
- **UBSan** catches signed overflow, misaligned accesses, null deref, out-of-range
  shifts. Combine with ASan; compile debug with both, run your test scenes, and the
  sanitizer names the exact line.

This is the C replacement for "Java would have thrown an exception here" — except it
catches the *memory* bugs Java silently papered over.

### Leak detection (Apple ecosystem)
- **Xcode Instruments → Leaks** template: works on C/ObjC, finds unfreed
  allocations and where they were created.
- `leaks <pid>` / `leaks ./anti` from the terminal for a quick check.
- `valgrind` is effectively unsupported on macOS/Apple Silicon — don't fight it, use
  Instruments + ASan's leak detector (`-fsanitize=address` reports leaks at exit).
- For `anti` specifically: because you control all arenas/freelists, a leak is almost
  always "a slot never returned to `Bit32`'s freelist" or "an arena never closed" —
  your `freeAll()` points are the audit trail.

### Debugging with LLDB
```
lldb ./anti
(lldb) breakpoint set -n anti_engine_frame
(lldb) run
(lldb) bt                 # backtrace, like a JVM stack trace but real registers
(lldb) frame variable e   # inspect struct fields (the C equivalent of a debugger watch)
(lldb) x/8gx 0x100000000  # hex dump 8 words of memory
```
LLDB understands your types because the binary carries DWARF debug info (that's the
debug build you pay for with `-g`). You inspect *structs and pointers*, not objects —
which, given `anti`'s design, is literally what you already inspect when debugging FFM
offsets.

---

## 9.3 Common rookie traps when moving from Java to C

Ranked by how often they burn people:

1. **"`malloc` gives me zeroed memory."** No. `malloc` returns *uninitialized* bytes
   (whatever was there). Use `calloc` (zeroed) or `memset`. Your `Bit32` code already
   does `ForeignMemory.setMemory(base, size, (byte) 0)` after `allocateNative` —
   that's the manual `calloc`. Easy to forget the first 50 times.

2. **"I'll just free it later."** You won't. Without a GC, "later" is a lie.
   Decide ownership at allocation: arena (whole block freed once) or explicit
   free/cache-return (like `Bit32.free`). Prefer arenas for anything per-frame.

3. **"Pointers and arrays are the same thing."** They decay but aren't identical —
   `sizeof` differs, and you lose the length. `anti`'s `[typeId][length]` header is the
   *correct* instinct; keep passing `(ptr, length)` pairs or your header's length.

4. **"`volatile` makes it thread-safe."** No. `volatile` means "don't cache this in a
   register" — it orders nothing. You need `_Atomic`/atomics for correctness (Lesson 7).
   (Java's `volatile` does more; C's does less. Do not transfer the meaning.)

5. **"That cast looks harmless."** `(struct Entity*)rawBytes` where `rawBytes` came
   from an unaligned offset is how you get unaligned-access crashes and strict-aliasing
   UB. Align and use `memcpy`/`offsetof`.

6. **"Strings are easy now."** C strings are `char*` + NUL terminator — no length,
   no bounds. A Java `String` is length + UTF-16 + immutability. Your `primitive.string`
   (off-heap, explicit length) is already the C model. Keep using your own string
   struct (`{ char* data; size_t len; }`) and never use "C strings" directly.

7. **"The stack is infinite."** Each thread's stack is ~64KB–8MB, and it's *not* for
   your frame-sized VkPresentInfo plus 10MB of mesh data. Big transient data → arena.

8. **"My enum is an int that I can compare."** In C, `enum` is basically `int` — fine.
   But "type tags" like your `TypeRegister` values must be `int32_t` constants, not
   C strings — you already do this, which means you're ahead of the curve.

---

## 9.4 Mental models that make native code feel safe

- **The stack is your `new` of choice.** Locals on the stack = free allocation,
  free destruction, no leak possible. Reach for stack structs before heap.
- **Arena = your `try` for memory.** Allocate a scratch arena per frame; reset at
  frame end. Nothing leaks, nothing needs freeing, and cache behavior is excellent.
- **The compiler is your friend *and* your adversary.** It removes UB code; write
  well-defined code and it rewards you. Never "test" UB — undefined is undefined even
  if it passed a million runs.
- **`anti` already taught you most of this.** Your entire codebase *is* native-memory
  programming. The traps above are the Java-flavored misreadings; you're 80% immune by
  design. The remaining 20% (bounds, alignment, ownership, atomics-ordering) is exactly
  what Lessons 3, 6, and 7 covered.

### Actionable takeaway

Before you port a single file, get the **sanitizer workflow** working on a hello-world
C build: `-fsanitize=address,undefined -Wall -Wextra -Werror`, run, see the tool
complain correctly about a deliberate buffer overflow. Then make the same "mistake" in
your `Bit32`-style code and watch ASan catch *exactly* the bug you would have spent an
afternoon hunting in Java. Buy the tool first; it's the difference between a scary
migration and a boring one.

---
*Next: Lesson 10 — The Ultimate Decision Matrix & Migration Blueprint.*