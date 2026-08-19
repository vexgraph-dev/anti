# Lesson 11 — The C Foundations Cheat Sheet (Everything You Need, One Page Deep)

*Teacher's voice: This is the lesson you keep open while you type. C is a small language;
the whole grammar fits on a page. Everything here is what your Java->C port will actually
touch. Read it twice, then keep it pinned as the reference while the AI writes code.*

---

## 11.1 The model in 30 seconds

C is three things:
1. A flat memory machine — you see bytes, you manipulate bytes.
2. A type system that is **mostly just a suggestion for layout** — `float` and `uint32_t`
   occupy the same 4 bytes; casts just reinterpret them.
3. A compiler you must *not* let silently invent things — every warning is a bug waiting.

There is no runtime. No VM. No GC. What you write is (essentially) what runs.

---

## 11.2 Primitive types on arm64 macOS

| C type | bytes | range / notes |
|---|---|---|
| `char` | 1 | signed or unsigned (implementation-defined). **Assume signed; use `signed char`/`unsigned char` to be sure** |
| `short` / `int` / `long` | 2 / 4 / 8 | `long` is 8 on arm64 macOS (64-bit). `int` is 4, always |
| `long long` | 8 | guaranteed ≥ 64-bit |
| `float` | 4 | IEEE-754 single |
| `double` | 8 | IEEE-754 double. On arm64 there is *no* x87 legacy — `double` math is native and fast |
| `_Bool` / `bool` | 1 | include `<stdbool.h>` for `bool`, `true`, `false` |
| `size_t` | 8 | unsigned, result of `sizeof`, index into arrays |
| `ptrdiff_t` | 8 | signed, result of pointer subtraction |
| `uintptr_t` / `intptr_t` | 8 | pointer as integer (your Java `long userPtr` lands here) |
| `uint8_t..uint64_t` | 1..8 | include `<stdint.h>` — **always use these for engine data** |

**The rule for `anti`:** never write bare `int` where you mean a fixed width. Bit layouts,
headers, offsets → `uint8_t/uint16_t/uint32_t/uint64_t`. Loop counters → `size_t` or
`int32_t` deliberately.

---

## 11.3 Storage classes

| Keyword | Meaning |
|---|---|
| (none) | local: stack, gone when scope exits. file-scope: global with external linkage |
| `static` | file-scope: hidden to other files (this is your `private`). local: persists across calls |
| `extern` | "this is defined elsewhere" — usually implicit, write it when declaring globals in headers |
| `const` | read-only through this view. **Does not mean the memory is immutable** — you may cast it away (don't) or the byte may be `__DATA_CONST` (then it *is* read-only and writes crash) |
| `volatile` | "re-read from memory every time, do not cache" — for MMIO and for variables shared with signals. **Not a substitute for atomics** |
| `_Atomic` | C11 atomics (your CAS spinlock and freelist). Include `<stdatomic.h>` |
| `_Thread_local` | per-thread storage (include `<threads.h>`; clang also accepts `__thread`) |

---

## 11.4 Pointers — the entire deal

```c
int   x  = 42;
int  *p  = &x;        // & = "address of"
int   y  = *p;        // * = "dereference"
*p = 43;              // write through the pointer
```

- A pointer is just a `uintptr_t` with a type attached for the compiler's benefit.
  That is *literally* what your Java `long userPtr` was. `*(int*)userPtr` in C is exactly
  `Unsafe.getInt(userPtr)`.
- `void *` is the "any address" pointer. Cast it explicitly to use it.
- `NULL` is the null pointer. In C `NULL` is `(void*)0`; comparing against `0` is fine.
- Declarations bind to the variable, not the type: `int* a, b;` declares `a` a pointer and
  `b` an int. Write `int *a, *b;` or one per line.
- Pointer arithmetic is in **units of the pointed-to type**: `p + 1` advances `sizeof(*p)` bytes.

Const combinations:
```c
const int  *a;   // pointer to const int   (can't write *a)
int *const  b;   // const pointer to int   (can't rebind b)
const int *const c; // neither
```

---

## 11.5 Arrays and strings

```c
int  arr[4] = {1,2,3,4};
int  i = arr[2];          // 3 — arr[i] is sugar for *(arr + i)
int *p = arr;             // arrays decay to pointer-to-first-element
size_t n = sizeof(arr);   // 16 — sizeof(arr) is the WHOLE array (only works on the
                          // real array, not on a decayed parameter)
```

- A function parameter `int arr[]` is a **lie** — it is `int *arr`, and `sizeof` inside
  gives 8. If you need a length, pass it.
- String literals: `"hi"` is an array of 3 chars (`'h','i','\0'`) in read-only memory.
  Never write to it. `char *s = "hi";` — do not `s[0] = 'H';`.
- Mutable strings you build yourself: `char buf[64]` and copy in.

---

## 11.6 Structs, unions, enums

```c
typedef struct {           // typedef lets you omit "struct"
    uint32_t type_id;
    uint32_t length;
    uint8_t  payload[];    // flexible array member (C99) — the header grows data
} Header;

Header h = {.type_id = 0xAA, .length = 0};   // designated initializers: named fields

h.type_id = 0xBB;          // access with .  on a value
Header *ph = &h;
ph->length = 8;            // access with -> on a pointer — identical to (*ph).length

union {                    // all members share the same memory
    uint32_t u32;
    float    f;
} pun = {.u32 = 0x3F800000};  // now pun.f == 1.0f on IEEE-754
```

- Structs are copied whole with `=` or `memcpy`. Copies are shallow.
- Alignment/padding: members are laid out in order, each aligned to its own size; padding
  inserted; the struct is padded to the alignment of its largest member. See Lesson 14.
- `enum` is just named `int` constants. Use `typedef enum { ... } Kind;`.

---

## 11.7 Control flow

```c
if (a < b) { ... } else if (a > b) { ... } else { ... }

switch (kind) {            // switch only works on integers/enums/char
case KIND_A:
    ...;                   // FALLTHROUGH is real: execution continues into KIND_B
case KIND_B:
    break;
default:
    break;
}
```

- **Switch falls through** unless you `break`. This is the #1 new-C bug. clang's
  `-Wimplicit-fallthrough` catches it.

```c
for (size_t i = 0; i < n; ++i) { ... }
while (cond) { ... }
do { ... } while (cond);     // runs body at least once
break;   // exits innermost loop/switch
continue;// next iteration
```

- `goto` is legal and occasionally the *right* tool: a single `cleanup:` label for error
  unwinding is idiomatic. Multi-level `break` has no other C answer.

---

## 11.8 Functions

```c
// declaration (prototype) — lets you call before defining
static float dot3(const float *a, const float *b);

// definition
static float dot3(const float *a, const float *b)
{
    return a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
}
```

- Parameters are **passed by value**. To mutate a caller's variable, pass its address:
  `void fill(float *out, size_t n)`.
- `static` on a function = file-private. `inline` is a *hint*; for functions used across
  files use `static inline` in a header (safe), or `inline` + one external definition.
- Variadic functions (`...`) require `<stdarg.h>` and exist mostly for `printf`-family.
  `anti` bans them in hot paths.
- Function pointers:
```c
typedef int (*OpHandler)(VM *vm, uint32_t op);
OpHandler table[256] = {0};
OpHandler h = table[OP_ADD];
int rc = h(vm, OP_ADD);   // call through the pointer
```

- Calling convention: on arm64, args pass in `x0–x7` + SIMD `v0–v7`, remainder on stack.
  You don't think about this day to day — except for ABI-sensitive calls like `objc_msgSend`
  (Lesson 5).

---

## 11.9 `main` and the process boundary

```c
int main(int argc, const char *argv[]);        // app entry
_Noreturn void exit(int status);               // ends process, runs atexit handlers
```

For `anti`: the real work happens in your platform layer's loop. `main` should: init
platform → enter loop → on exit, tear down in **reverse order**. "Instant exit" is Lesson 19.

---

## 11.10 The preprocessor — the 4 you need

```c
#include "anti/pool.h"     // local header
#include <stdint.h>        // system header
#define MAX_POOLS 64       // object-like macro
#define NEXT_POW2(n) (...) // function-like macro — parenthesize every arg and the body!
#ifdef ANTI_DEBUG
    ...
#endif
```

- **Macro gotcha:** `#define MUL(a,b) a*b` then `MUL(1+2,3)` = `1+2*3`. Always wrap:
  `#define MUL(a,b) ((a)*(b))`.
- `#pragma once` in every header is the simplest include guard.
- `#ifdef`/`#ifndef`/`#endif` gate debug code. Compile with `-DANTI_DEBUG` to enable.
- The preprocessor runs *before* the compiler and knows nothing about types. Prefer
  `static inline` functions over function-macros when you can.

---

## 11.11 Headers vs sources — the one true layout

```
anti/
  pool.h    // declarations, types, static inline helpers
  pool.c    // definitions of non-inline functions
```

- Header = contract: types, function prototypes, `static inline` implementations, `#pragma once`.
- Source = implementation: non-inline function bodies, file-scope `static` state.
- A `static` global in a `.c` is your "private field". If it must be shared, expose an
  accessor; never expose the storage.

---

## 11.12 The build (one line, everything you need)

```sh
clang -std=c11 -O2 -g -Wall -Wextra -Wpedantic -Werror \
      -mcpu=apple-m1 -march=armv8.5-a \
      -fsanitize=address,undefined \
      -framework AppKit -framework Metal -framework Vulkan \
      -o anti src/*.c src/window/ns_main.m
```

- `-std=c11` locks the language level. `-Werror` turns warnings into failures — non-negotiable.
- `-mcpu=apple-m1` lets clang use NEON + newer arm64 features.
- Sanitizers only in debug builds (`-DANTI_DEBUG`). Release: `-O3` + no sanitizer.
- The `.m` file is compiled by the same clang; it's C + a little ObjC for AppKit (Lesson 5).

---

## 11.13 Java → C translation table (your own words, your own engine)

| Your Java | C |
|---|---|
| `long userPtr` | `void *` or `uintptr_t` |
| `Unsafe.getInt(p)` | `*(int32_t *)p` |
| `Unsafe.putInt(p, v)` | `*(int32_t *)p = v;` |
| `new StructHeader(0xAA, len)` | `(Header){.type_id = 0xAA, .length = len}` |
| `someArray[i]` (ref array) | `arr + i` / `arr[i]` over a real buffer |
| class field | struct member |
| static field | file-scope `static` in the `.c` |
| `Bit32.acquire()` | `uint32_t pool_acquire(Pool *p)` |
| method call | function call, `p` as first arg (C has no `this`) |
| `SpinLock.lock()` | `while (atomic_flag_test_and_set(&lock)) {}` |
| `RuntimeException` | error code / `longjmp` (Lesson 19) |
| GC | nothing — Lesson 13 |

---

## 11.14 The 10 things that will bite you

1. **Array decay** — `sizeof(arr)` inside a function parameter gives 8, not the array size.
2. **Switch fallthrough** — `break` or it's a bug.
3. **Macro arguments** — wrap everything in parens.
4. **Signed overflow** is UB. `INT_MAX + 1` can do anything. Use unsigned for wraparound
   semantics (hash, tags, ABA counters).
5. **Pointer arithmetic is in elements, not bytes** — `p+1` moves `sizeof(*p)`.
6. **Integer promotion** — `uint8_t a=200, b=200; a+b` is computed as `int` → 400, then
   truncated. Cast before narrowing.
7. **Reading uninitialized memory** — `malloc` gives you garbage. `calloc`/`{0}` gives zero.
   Use `= {0}` on every struct you initialize.
8. **String literal writes** — crash or silent corruption. Copy if you must mutate.
9. **Struct padding** — `sizeof(struct)` is NOT the sum of its fields (Lesson 14).
10. **`const` is a promise, not armor** — nothing stops an `int*` from pointing at a
    `const int`; only `restrict` + discipline stop aliasing (Lesson 12).

---

*Next: Lesson 12 — naming, braces, annotations, and the banned list. The house rules
your AI will be held to.*