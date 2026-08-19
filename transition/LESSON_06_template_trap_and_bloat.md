# Lesson 6 — The Bloat Traps (and Why C Skips All of Them)

*Teacher's voice: You'll hear "C is old" and "modern languages are better." This lesson
is the counterweight: the exact mechanisms that bloat other toolchains, and the reason
choosing C is choosing to inherit *none* of them. It's the research behind the decision,
so you never second-guess it again.*

---

## 6.1 The three bloat mechanisms (that exist everywhere except C)

When a "modern" language produces a fat binary, it's almost always one of three
mechanisms. C has none of them. Know them so you can spot them in any toolchain you
evaluate later:

1. **Monomorphization** — the compiler re-compiles the same function body once per
   *type* it's used with (generics/templates). N types → N copies in the binary.
2. **A pervasively-instantiated standard library** — containers and I/O that pull in
   locale machinery, iostreams, string bloat, per call site.
3. **Exception/RTTI machinery** — unwinding tables and runtime type info that ship even
   if you never throw a single exception.

Each one costs real bytes *before your code even runs* — the same way the JVM/GC cost
you real bytes in Lesson 1's 22MB `.app`.

---

## 6.2 Mechanism 1: Monomorphization (duplicated code per type)

A template/generic is a **code generator**: the compiler re-compiles the function body
for *each* distinct type you use it with. It's not shared code; it's N copies.

```c
// Other languages: max_of<int>, max_of<double>, max_of<float> → 3 compiled copies
// C: one function, one copy, one symbol. The type is erased.
static inline int32_t max_i32(int32_t a, int32_t b) { return a > b ? a : b; }
static inline double   max_f64(double   a, double   b) { return a > b ? a : b; }
// Two functions you wrote deliberately — not two copies the compiler made for you.
```

Real-world evidence: a format library's `write_padded` template dominated **59% of the
whole library's code**; extracting the type-independent part and mapping many types onto
a few "type-erased" backends shrank the library **4x** (368KB → 92KB) with no perf
loss. That's the price of generics — and C's answer is what that library did: **type
erasure on purpose.**

### How `anti` already does this (and why it teaches you the C way)

Your `bit.Bit32.java` is **type-erased by design**: it stores slots for "anything that
is 4 bytes wide" (int, float, Fixed32) in one shared pool, discriminated by the `typeId`
header. In C that design ports **verbatim** — the type-erased pool *is* the C idiom, not
a workaround. What would've been `Pool<int>, Pool<float>, Pool<Fixed32>` (3x code) in a
generic system is `Pool` with a `type_id` — one pool, one copy, one code path. That's
Lesson 13's `pool_t`, exactly.

---

## 6.3 Mechanism 2: The pervasive standard library

The single biggest binary-bloat contributor in generic-heavy toolchains is the
standard library — not because it's "bad," but because it's *pervasive and
per-instantiating*.

Measured, real:
- A 6KB hello world; **add one string object → +~100KB**, because the locale machinery
  gets pulled in as a global initializer that dead-code elimination can't remove.
- An engine's symbol dump can show **1,600+ copies of the same string functions** across
  translation units — the same code compiled, optimized, and written to object files
  1,600 times, then 1,599 copies thrown away.
- A function object costs ~12% more binary than a plain function pointer; templates cost
  ~nothing until you scale the number of types, then they scale linearly.

The modern answer for tiny binaries isn't "avoid string"; it's **"don't import the
pervasive library at all."** The author of the {fmt} formatting library stripped his
code down to bare minimalism and got the whole thing to **14KB** — versus the
standard I/O's half-kilobyte *per call site*.

**C's answer:** there is no pervasive container library to import. Your `{char*, size_t}`
string, your `Bit32` pool, your grid — you already wrote them (Lesson 13, and Phase 2
of Lesson 10's migration). The standard C library is opt-in per header:
`<stdint.h>` `<stdatomic.h>` `<stddef.h>` `<string.h>` `<dlfcn.h>` — each tiny, each
dead-strippable, none of them pulling in hidden globals.

---

## 6.4 Mechanism 3: Exceptions and RTTI (machinery that's on even if you never throw)

Exception-capable languages compile in, by default, for every function that *could*
participate in unwinding:

- **Unwinding tables** — maps from every program counter to its cleanup handlers.
  Real bytes in the binary even if nothing ever throws.
- **Personality routines** — the runtime functions that walk those tables.
- **RTTI** — runtime type info: every polymorphic type gets a `typeinfo` entry and a
  pointer to it. You pay whether or not you ever use it.

The lean-build crowd kills all three with three flags:
`-fno-exceptions -fno-rtti` and no standard library. That gets back to C size — by
*removing* what C never had.

**C's answer:** there are no exceptions and no RTTI. Your error handling is return codes
and your own `setjmp`/`longjmp` boundary mechanism (Lesson 19). Your type IDs are your
`TypeRegister`/`Form` enums (Lesson 14) — a compile-time constant, not a runtime object.
Zero hidden tables, zero hidden typeinfo, zero unwinding machinery, *in every build,
always.*

---

## 6.5 What C actually pays (and doesn't)

```
Bloat mechanisms:              generic langs      C
──────────────────────────────────────────────────
monomorphized copies           yes (N per type)  no (type-erased by design)
pervasive stdlib + globals     yes               no (opt-in per header)
exceptions + RTTI tables       yes               no (return codes + longjmp)
hidden runtime (JVM/GC)        yes               no (Lesson 1)

C's ONLY real bloat lever:     linking in libraries you don't use
```

That last one is fixable with two flags: `-ffunction-sections -fdata-sections` (one
section per function) + `-Wl,-dead_strip` (drop unused ones). A disciplined C build
with `-Os` is the smallest possible baseline there is. Your GraalVM 22MB floor
(Lesson 1) becomes, in C, "whatever your code actually does, plus the frameworks you
opt into."

---

## 6.6 The anti zero-bloat build (the one you'll actually use)

```
clang -std=c11 -O2 -ffunction-sections -fdata-sections \
      -Wall -Wextra -Wpedantic -Werror -Wconversion \
      -Wl,-dead_strip -o anti src/*.c -framework AppKit -framework Metal
```

Rules:
- `-O2` for the shipped engine; `-Os` when distribution size matters more than speed.
- No `<stdio.h>` in the engine — use your own off-heap ring-buffer logger (Lesson 12's
  ban list).
- No `<string.h>` for hot data — your `{char*, size_t}` strings don't need NUL scans.
- Containers are the ones you wrote (pool, arena, grid — Lessons 13/14). Nothing
  imported, nothing hidden, nothing duplicated per type.

### Actionable takeaway

Build a hello world and watch `size` (or `ls -la` + `otool -l`):

1. `cc hello.c` — **~12–16KB.**
2. `clang -std=c11 -Os -ffunction-sections -fdata-sections -Wl,-dead_strip hello.c`
   — smaller still.

That tiny number is *all* your code, plus libSystem's stub. No unwinding tables, no
locale globals, no typeinfo, no runtime you didn't ask for. It's the same reasoning
that separates your 22MB `.app` from a 200KB native binary — except now it's fully
under your control on day one.

---

*Next: Lesson 7 — Concurrency & Lockless Primitives on Modern CPUs.*