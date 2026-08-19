# Lesson 3 — Structs, Alignment & The Gotchas of Pointer Math

*Teacher's voice: Here is where "I know C" and "I actually understand C" split. Java
gave you `long` as a universal address and `ForeignMemory.getInt(addr + offset)` as a
universal access. C gives you *typed* pointers — and with typing comes a set of rules
that either do the work for you or silently corrupt your memory. Learn the rules.*

---

## 3.1 The core gotcha: `ptr + 1` is NOT byte 1

In Java, addresses are `long`s, and pointer math is *byte-addressed* arithmetic:

```java
long p = allocateNative(...);
long next = p + 1;     // Java: literally one byte later. Because p is just a number.
```

In C, a pointer remembers what it points to. `ptr + 1` advances by
`sizeof(*ptr)` bytes — not one byte:

```c
uint8_t*  b = ...;   b + 1 == b + 1 byte         // sizeof(uint8_t)  == 1
int32_t*  i = ...;   i + 1 == b + 4 bytes        // sizeof(int32_t)  == 4
double*   d = ...;   d + 1 == b + 8 bytes        // sizeof(double)   == 8
struct Vec3* v = ...; v + 1 == b + sizeof(Vec3)  // usually 16 (see 3.2)
```

This is a feature, not a trap — it's exactly what `i++` over an array is *supposed* to
do. The "trap" is that you must be aware of the scaling when you port Java index math:

```java
// Java (your current code, byte addresses everywhere)
long base   = allocateNative(64);
long second = base + 4;              // you manually scale: 4 bytes per int
int  value  = ForeignMemory.getInt(second);
```

```c
// C: the type does the scaling for you
int32_t* arr  = malloc(64);
int32_t* second = arr + 1;           // same address, compiler adds 4
int32_t  value = *second;            // loads W0, [X0]
```

Both compile to `LDR W0, [X0, #4]` on arm64. Java made you write the `+4`; C hands it
to you for free — but only if you use the typed pointer. If you keep casting to
`void*`/`uint8_t*` (as Java habits will tempt you), you reintroduce manual scaling and
lose the benefit. **Pick the width-typed pointer and let the compiler do the math.**

### Indexing formula (memorize)

```
ptr + i  ≡  (uint8_t*)ptr + i * sizeof(*ptr)

The C compiler does the multiply. If you write the multiply yourself you are
doing Java's job and inviting a bug when sizeof changes (e.g. padding).
```

---

## 3.2 Alignment & padding: why your struct is bigger than the sum of its fields

ARM64 (Apple Silicon) likes things aligned to their size: an `int32_t` wants a 4-byte
address, an `int64_t`/`double` an 8-byte address, a pointer an 8-byte address.
Unaligned access is *allowed* for normal `ldr/str` on arm64 but costs extra cycles and
is *forbidden* for several instructions (SIMD/NEON loads, and `ldar`/`stlr` atomic
loads are not alignment-tolerant the way plain loads are). The AArch64 spec and Apple
hardware effectively punish unaligned atomics and vector ops.

The C compiler solves this by **inserting padding** between struct fields so every
field lands on its natural boundary:

```c
struct Naive {
    char    a;   // offset 0
    double  b;   // wants 8-byte alignment
    int32_t c;   // wants 4-byte alignment
};
// sizeof(struct Naive) == 24, not 13!
//
//   0       8              16        24
//  [a|pad×7|      b       | c|pad×4 ]
```

Layout rules (arm64, clang):
- Each field is placed at a multiple of its own size.
- The struct's alignment = the max alignment of its members (here 8).
- The struct's size is rounded up to a multiple of its alignment (so arrays of the
  struct keep every element aligned).

Reordering fields can shrink a struct:

```c
struct Sorted {
    double  b;   // offset 0
    int32_t c;   // offset 8
    char    a;   // offset 12
};
// sizeof(struct Sorted) == 16. Same data, 8 bytes saved.
```

### What this means for `anti`

Your `oop.Struct.java` is a **runtime struct-layout engine**: you store a registry of
`[fieldTypeIds]` and `[offsets]` arrays, and compute each field's offset by summing
`Stride.get(fieldClassId)` (`Struct.java:104-107`):

```java
// anti/oop/Struct.java — you literally compute layout by hand, at runtime
for (int i = 0; i < len; i++) {
    ForeignMemory.setInt(fieldTypesPtr + i * 4L, fieldClassIds[i]);
    ForeignMemory.setInt(offsetsPtr + i * 4L, currentOffset);
    currentOffset += Stride.get(fieldClassIds[i]);
}
```

That loop is a *reimplementation of the C compiler's layout algorithm*, executed at
runtime, in Java, over FFM pointers. In C, the compiler does all of it at build time:

```c
// The same thing, but the compiler did offsets for free:
struct Entity {
    int32_t  id;      // offset 0
    float    x, y, z; // 4, 8, 12
    uint64_t bits;    // 16 (padded to 8-alignment)
};
// offsets, padding, sizeof — all compile-time constants.
// offsetof(struct Entity, bits) == 16. sizeof == 24. Zero runtime cost.
```

If you need *dynamic* structs (structs defined at runtime, e.g. from scripts), you can
still write a layout engine in C — it's the same loop — but for the 95% of structs you
can know at compile time, C gives you correctness and performance for free, and the
compiler *tells you* the offsets instead of you debugging them by hand.

### Matching your Java layout to C layout (critical for interop)

When you define `MemoryLayout` for a struct you share with native code (your `CG_RECT`
in `macOSWindow.java`, for example), the Java layout and the C `struct` **must agree**.
`MemoryLayout.structLayout(JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE)` matches
`struct CGRect { double x,y,w,h; }` perfectly because everything is 8-byte aligned.
The moment you mix a `char` and a `double`, Java's layout must manually insert the 7
pad bytes that C's compiler inserts automatically. `jextract` exists precisely to
generate those FFM layouts *from* the headers so you don't have to get them wrong.

> **Golden rule:** layout belongs to the *compiler* in native land. If you hand-write
> a layout in Java and it differs from the native struct by one pad byte, you get
> silent garbage (or a crash) at the boundary — not an error message.

---

## 3.3 Code comparison: the same field read, three ways

```java
// anti today — byte address + offset, FFM
long entity = allocateStruct(...);
long bitsAddr = entity + Struct.getFieldOffset(layoutId, FIELD_BITS);
long bits = ForeignMemory.getUnsafeLong(bitsAddr);   // LDR X0, [X1, #16]
```

```c
// C — typed pointer, compiler resolves the offset
struct Entity* e = allocate_struct();
uint64_t bits = e->bits;                             // LDR X0, [X1, #16]
```

```c
// C alternative — explicit offsetof (what Java forced you to do)
uint64_t bits = *(uint64_t*)((uint8_t*)e + offsetof(struct Entity, bits));
```

All three emit the identical instruction. But note: only the middle one is
*checkable* — `e->bits` can't drift out of sync with the struct because it *is* the
struct. Your Java version and the `offsetof` version both hardcode/derive offsets that
the compiler could simply have generated for you.

---

## 3.4 The danger zones (memorize these or pay)

1. **Unaligned access on Apple Silicon.** Plain loads tolerate it (slowly). Atomic
   loads/stores (`ldar`/`stlr`) and NEON vector loads generally do **not** — this is a
   hard crash or undefined behavior territory. Keep atomics and SIMD on natural
   alignment. Your `Bit32` pool already aligns slots to 8 (`SINGLETON_SLOT_SIZE` and
   the `,8` alignment argument in `poolArena.allocate(...)` — `Bit32.java:156`), which
   is exactly right.
2. **`sizeof` vs. `stride` vs. `Stride.get()`.** If you've ever had two different
   calculations of an element width disagree (your `Stride.get` for a struct vs. the
   actual bytes), that's a padding bug. In C, `sizeof` and `offsetof` are the single
   source of truth and they're computed by the same compiler that lays out the memory.
3. **Pointer cast + arithmetic at once.** `(struct Thing*)ptr + 1` scales *after* the
   cast. If `ptr` is actually a byte pointer you'll walk off the end of an array.
4. **Array decay.** In C, an array name in most expressions *decays* to a pointer to
   its first element. `sizeof(arr)` (whole array) vs `sizeof(ptr)` (8 bytes) is a
   classic bug when porting "array of N" Java code. Keep the array length next to the
   pointer — which, amusingly, is exactly your `[typeId][length]` header design.

### Actionable takeaway

Take one struct from `oop.Struct.java` (say an entity with an `id`, three floats, and a
bitset) and write its C `struct` + `offsetof` table on paper. Compare the offsets your
`Stride.get` loop produces with what clang computes. If they match, you've just
confirmed that `anti`'s runtime struct engine is a slower, hand-rolled version of
something the C compiler does in microseconds at build time — and that porting it to C
is not a rewrite, it's a *deletion*.

---
*Next: Lesson 4 — Packages & Classloading vs. Headers & Linking.*