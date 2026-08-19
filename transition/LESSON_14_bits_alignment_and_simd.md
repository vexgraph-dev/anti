# Lesson 14 — Bits, Alignment, and SIMD on Apple Silicon

*Teacher's voice: `anti` is a binary engine — packed headers, bit-packed type IDs, tagged
freelists. In Java those were clever bits inside a managed heap. In C they become the
actual, physical layout of memory you own. This lesson is the toolbox for that layout:
widths, masks, alignment, and the NEON vector unit that makes SIMD math fast on M-series.*

---

## 14.1 Widths, always explicit

| Use | Type |
|---|---|
| counts, sizes, indices | `size_t` (64-bit) |
| signed arithmetic, loop counters | `int32_t` |
| bit fields, masks, headers, tags | `uint32_t` / `uint64_t` |
| 8-bit payloads | `uint8_t` |
| pointers-as-integers | `uintptr_t` |
| pointer difference | `ptrdiff_t` |

Two rules: (1) no bare `int` for anything that lives in a header; (2) when you shift a
tag into the high bits of a 64-bit word, use `uint64_t`, or the shift will be done in
32 bits and silently truncate.

---

## 14.2 The bit toolbox (your vocabulary for headers and freelists)

```c
// isolate / test
bit = (w >> shift) & MASK;         // extract a field
set   = (w & BIT) != 0;            // test one bit

// set / clear / toggle
w |=  (1u << k);
w &= ~(1u << k);
w ^=  (1u << k);

// round up n to a multiple of 16 (alignment) — the classic
n_aligned = (n + 15u) & ~15u;

// next power of two ≥ n
static uint32_t next_pow2(uint32_t n) {
    n--; n |= n >> 1; n |= n >> 2; n |= n >> 4; n |= n >> 8; n |= n >> 16;
    return n + 1;
}

// count bits
// __builtin_popcount / __builtin_clz / __builtin_ctz on clang = one instruction
```

**Bitfields in structs: use them, but verify.** `uint8_t tag : 8; uint32_t idx : 24;`
is compiler-dependent layout. If the layout must be *exact* (a wire/disk format, a
dylib header), use explicit masks + shifts and `_Static_assert` the result. For the
ABA freelist head, use `uint64_t` with mask constants — not bitfields:

```c
#define HEAD_TAG_SHIFT 32
#define HEAD_IDX_MASK  ((1ull << 32) - 1)
#define HEAD_TAG(head) ((uint32_t)((head) >> HEAD_TAG_SHIFT))
#define HEAD_IDX(head) ((uint32_t)((head) & HEAD_IDX_MASK))
```

---

## 14.3 Type IDs and headers — your `[typeId][length]` byte

```c
typedef struct {
    uint8_t  type_id;   // 0xAA form, 0xBB trait, 0xCC object...
    uint8_t  flags;     // bits: ALLOCATED, LOCKED, ...
    uint16_t length;    // element count
    uint32_t pool_id;   // which pool owns the payload
} ObjHeader;            // 8 bytes, matches your Java header

_Static_assert(sizeof(ObjHeader) == 8, "header must stay 8 bytes");
_Static_assert(_Alignof(ObjHeader) == 4, "header alignment");
```

- `_Static_assert` makes the layout a **compile-time promise**. If a refactor sneaks
  padding in, the build fails instead of the game.
- Pad-order the struct by alignment: biggest members first (Lesson 14.5) so the 8 bytes
  stay 8 bytes with no internal holes.

---

## 14.4 Endianness

- Apple Silicon is little-endian. Almost everything you touch is LE.
- Rule: **never assume on disk / wire formats.** When serializing a header to a file
  (save games, baked assets), write bytes explicitly or document "LE, always". For
  in-memory only (your pools, the VM chunks), endianness is irrelevant.

---

## 14.5 Alignment — why, and how to use it

**Why:** the CPU reads 8 bytes at a time. An `int64_t` at address `p` where
`p % 8 != 0` either splits across two loads (slow) or faults (on some archs; arm64
allows unaligned access for most ops but it costs). NEON loads *require* 16-byte
alignment or they fault.

- Every type has a natural alignment: its own size (up to the arch's max).
- Struct members are laid out in declaration order, each aligned to its own alignment,
  with padding inserted. The struct's alignment = its most-aligned member; its size is
  rounded up to that multiple.
- `_Alignof(T)` = alignment. `offsetof(T, member)` = member offset — your hand-rolled
  `Struct.java` `offsetof`, now a keyword.
- **Pad-order your structs** (biggest first) to kill wasted padding:
  `uint64_t` / `uint32_t` / `uint32_t` → `uint16_t` ×4 — not scattered.

**The cache-line factor:** Apple Silicon cache lines are **128 bytes** (x86 is 64). The
practical unit for false-sharing is the hardware line — so hot structs that different
threads hammer should be `_Alignas(128)` to guarantee two of them never share a line:

```c
typedef struct _Alignas(64) {
    _Atomic uint32_t head;
    uint32_t        pad[15];   // keep a per-thread pool slot off the same line
} LinePaddedHead;
```

**`container_of` — the reverse `offsetof`** (get the enclosing struct from a member
pointer). Used by your pool when a callback receives a `&slot->data` pointer:

```c
#define container_of(ptr, type, member) \
    ((type *)((uint8_t *)(ptr) - offsetof(type, member)))
```

---

## 14.6 Packed structs: the trap

`__attribute__((packed))` removes padding → sizeof is exact, but every unaligned field
access compiles to a slower byte-by-byte path (or faults on NEON loads). Bans:
- **Do not** `packed` your hot math types to save 8 bytes.
- **Do not** take addresses of packed fields into SIMD ops.
- **OK** for on-disk/wire headers where exact layout matters and access is rare.

Prefer: pad-order + `_Static_assert` over `packed`.

---

## 14.7 SIMD — NEON on Apple Silicon

NEON is the 128-bit vector unit: you get 32 × 128-bit registers (plus the SVE scalar
registers on M3+). Include `<arm_neon.h>` and the compiler maps intrinsics to real
`add.4s`/`mul.4s` instructions — no runtime, no overhead.

The pattern for a Vec3/Vec4 math core:

```c
#include <arm_neon.h>

typedef float32x4_t F32x4;   // 4 floats in one 16-byte register

static inline F32x4 f4(f32 x, f32 y, f32 z, f32 w) { return (F32x4){x,y,z,w}; }

// dot product of two 4-vectors
static inline float f4_dot(F32x4 a, F32x4 b) {
    return vaddvq_f32(vmulq_f32(a, b));   // multiply lanes, add across the vector
}

// add two 4-vectors
static inline F32x4 f4_add(F32x4 a, F32x4 b) { return vaddq_f32(a, b); }

// load/store — REQUIRES 16-byte alignment
_Alignas(16) float data[16];
F32x4 row0 = vld1q_f32(&data[0]);        // load
vst1q_f32(&data[0], row0);               // store
```

Rules for SIMD to actually be fast:
1. **Data `_Alignas(16)`** — misaligned `vld1q` faults. Every float array that feeds a
   SIMD op must be aligned, and so must the arena be (Lesson 13.2 rounds to 16).
2. Prefer `static inline` intrinsics in headers — the compiler interleaves them into
   the loop.
3. Keep arrays dense (AoS **for SoA-critical paths**: NEON wants 4/8 floats in a row,
   so a "struct of arrays" layout for hot transform math is often the win).
4. `-mcpu=apple-m1` (or newer) lets clang auto-vectorize too — but the *intrinsics*
   are deterministic; autovectorization is a hint. **Profile, then pick.**
5. Integer SIMD exists too (`int32x4_t`) for tag/hash work — same discipline.

---

## 14.8 A minimal anti-style example: an aligned transform pool

```c
typedef struct _Alignas(16) {
    F32x4 rows[4];          // 4×4 matrix, 16 floats, one cache-friendly block
} Mat4;                     // sizeof == 64, aligned to 16

static inline F32x4 mat4_mul_vec(const Mat4 *restrict m, F32x4 v) {
    F32x4 r = vmulq_laneq_f32(m->rows[0], v, 0);
    r = vfmaq_laneq_f32(r, m->rows[1], v, 1);
    r = vfmaq_laneq_f32(r, m->rows[2], v, 2);
    r = vfmaq_laneq_f32(r, m->rows[3], v, 3);
    return r;
}
```

---

## 14.9 Atomics recap for the freelist (your Lesson 7 bridge)

- ARM64 is weakly ordered. The compiler does NOT reorder atomics; the CPU may.
- `atomic_load_explicit(&x, memory_order_acquire)` → `ldar`; release store → `stlr`.
- Your ABA freelist: the `uint64_t {tag, idx}` head, `atomic_compare_exchange_strong`
  in a loop, tag bumped on every release. Same C, same trick, now with `_Atomic`.

---

## 14.10 The alignment/SIMD checklist

1. Every struct with a `float` member that feeds a hot loop is `_Alignas(16)`.
2. Every arena bump rounds to 16 (Lesson 13.2).
3. Headers are `_Static_assert`-ed for exact size.
4. Pad-order big-to-small; no `packed` in hot paths.
5. Hot per-thread data is `_Alignas(64)` to dodge false sharing.
6. NEON intrinsics used deliberately where the profile says math is the cost.

---

*Next: Lesson 15 — the AI agent's contract: the operating manual that keeps the code
clean, and the prompt template that enforces it.*