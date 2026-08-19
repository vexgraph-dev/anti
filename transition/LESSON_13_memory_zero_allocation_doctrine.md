# Lesson 13 — Memory: The Zero-Allocation Doctrine

*Teacher's voice: The GC is gone. You are the GC. But here is the relief: `anti` already
behaves like it has no GC — raw `long` pointers, manual 8-byte headers, hand-rolled
freelists. Porting that discipline to C is not learning something new. It is learning to
stop *pretending* the runtime protects you.*

---

## 13.1 The doctrine, in one sentence

**After init, `anti` performs zero heap allocation.** Every byte the engine touches comes
from a fixed set of memory blocks created once at startup and recycled forever.

Three mechanisms cover everything:

| Mechanism | Use for | Recycled when |
|---|---|---|
| **Static/global arrays** | constants, tables, fixed lookup | never — they're compile-time bytes |
| **Arenas** | per-frame scratch (transient) | end of frame: bump pointer resets to base |
| **Pools** | long-lived objects (entities, messages, scripts) | returned by caller to the freelist |

If your AI ever writes `malloc` in the frame loop, that's a bug by definition.

---

## 13.2 The arena (bump allocator) — 20 lines

```c
typedef struct {
    uint8_t *base;
    uint8_t *cursor;
    size_t   cap;
} Arena;

static void *arena_alloc(Arena *a, size_t n) {
    n = (n + 15u) & ~15u;                    // 16-byte align (SIMD-safe)
    if ((size_t)(a->cursor - a->base) + n > a->cap) {
        anti_panic("arena exhausted");       // fixed cap = no silent growth
    }
    void *p = a->cursor;
    a->cursor += n;
    return p;
}

static void arena_reset(Arena *a) {
    a->cursor = a->base;                     // free everything: set the clock back
}
```

- Allocation = bumping a pointer. **Free is not per-object; it is per-frame.**
- Perfect for: command buffers, collision query results, UI layout lists, any data that
  lives for exactly one frame.
- The cap is a compile-time constant sized to the worst frame. If you hit the cap,
  grow the arena at init — never in the loop.
- `anti_panic` on exhaustion is correct: out-of-memory in a zero-alloc engine is a
  *configuration* error (too-small arena), not a runtime condition to survive.

---

## 13.3 The pool (fixed-size slot freelist) — your `Bit32` pattern, in C

This is literally your Java `Bit32` freelist wearing C clothes:

```c
#define POOL_NULL UINT32_MAX

typedef struct {
    uint32_t next;    // freelist chain — free slots only
    uint8_t  data[];  // fixed-size payload follows the link word
} Slot;

typedef struct {
    Slot    *slots;   // one contiguous block, allocated ONCE at init
    uint32_t free_head;
    size_t   slot_bytes;
    size_t   count;
} Pool;

static void pool_init(Pool *p, void *mem, size_t count, size_t slot_bytes) {
    p->slots = mem;
    p->slot_bytes = slot_bytes;
    p->count = count;
    p->free_head = 0;
    for (size_t i = 0; i + 1 < count; ++i)          // build the chain once
        p->slots[i].next = (uint32_t)(i + 1);
    p->slots[count - 1].next = POOL_NULL;
}

static uint32_t pool_acquire(Pool *p) {
    uint32_t s = p->free_head;
    if (s == POOL_NULL) return POOL_NULL;
    p->free_head = p->slots[s].next;
    return s;
}

static void pool_release(Pool *p, uint32_t s) {
    p->slots[s].next = p->free_head;
    p->free_head = s;
}
```

- **Allocation is O(1), free is O(1), no system calls, no fragmentation, no GC.**
- Lockless version (your ABA-tagged `Bit32`): the head becomes
  `uint64_t { tag:32, index:32 }`, and release/acquire are CAS loops (Lesson 7). The
  template is identical; only the head word and the CAS change.
- Pool slots are the "objects" of `anti`. A slot index IS the handle — never store
  pointers to slots across a frame boundary; store the `uint32_t` and re-resolve.

---

## 13.4 The five ownership rules (print these)

1. **Every allocation has exactly one owner** at a time. Ownership transfers are explicit
   (`pool_acquire` returns to caller; caller returns via `pool_release`).
2. **Borrowers never free.** If a function only reads, it takes `const` and promises
   (via the header comment) to hold nothing past its return.
3. **Never free twice.** `pool_release` on an already-released index is a freelist
   corruption (and with tags, a detectable one — the ABA tag mismatches).
4. **Never use after free.** The moment you release, the slot may be re-issued to anyone.
   Re-resolve the handle *after* any call that can release.
5. **The arena is free-on-reset, wholesale.** No partial frees in an arena. Ever.

---

## 13.5 The four bugs — and their `anti` flavors

| Bug | Classic version | Your version |
|---|---|---|
| **Leak** | malloc without free | pool slots acquired but never released; arena never reset |
| **Double-free** | free() twice | `pool_release` twice — tag check catches it |
| **Use-after-free** | dangling pointer | holding a slot pointer across a `pool_release`, then dereferencing |
| **Out-of-bounds** | arr[n] beyond len | trusting a type header's `length` without re-checking the pool bounds |

The type-header (`[typeId][length]`) discipline that protected you in Java must now be
**verified** in C, because nothing verifies it for you:

```c
// safe cast: never trust a raw pointer, always range-check first
static Entity *entity_from_handle(World *w, uint32_t h) {
    if (h >= w->pool.count) { anti_log("entity %u out of range", h); return NULL; }
    return (Entity *)&w->pool.slots[h].data[0];
}
```

---

## 13.6 The safety net (run every debug build under this)

```sh
clang -fsanitize=address,undefined -fno-omit-frame-pointer -g ...
ASAN_OPTIONS=detect_leaks=1:abort_on_error=1 ./anti_debug
```

- **ASan** finds use-after-free, double-free, and leaks — in C, this is your GC.
- **UBSan** catches signed overflow, misaligned access, bad casts — the silent UB class.
- **`-fstack-protector-all`** catches stack smashing.
- Release builds drop all of it (`-O3 -fomit-frame-pointer`).
- Your CI / your dev loop: every commit runs the suite under ASan+UBSan. Soup cannot
  survive this for long.

---

## 13.7 The leak-soup prevention checklist (the AI runs this after every feature)

1. Grep the new code for `malloc|calloc|realloc|free|new` — allowed only in `memory.c`.
2. Grep for `pool_acquire` — is **every** acquire paired with a `pool_release` on every
   return path? (A function with a `goto cleanup` error path: yes. One that returns from
   the middle: bug.)
3. Run the pool stress test: acquire/release in a loop with random interleaving under
   ASan. The ABA tag should never trip. If it trips, the freelist math is wrong.
4. Verify every arena user calls `arena_reset` exactly once per frame, in order.
5. Run one full level/scene transition and one hot reload (Lesson 16) under ASan —
   these are where leaks hide.

---

## 13.8 Porting notes — your Java instincts, corrected

| Java instinct | C correction |
|---|---|
| "the GC handles cleanup" | nothing handles cleanup. The contract comment does. |
| "I'll allocate when I need it" | allocate once at init, recycle forever |
| "`long userPtr` is opaque" | `void *` is real. Dereferencing it is the *point*. |
| "objects have headers" | keep the 8-byte header — now it's just a struct prefix with `_Static_assert(sizeof == 8)` |
| "exceptions abort safely" | abort only via your own panic/exit path (Lesson 19) |

The good news, restated: **you've been doing this already.** `anti` in Java is C with
training wheels still attached. Lesson 13 is the moment the wheels come off — and the
sanitizers are the spotters.

---

*Next: Lesson 14 — bits, alignment, and SIMD on Apple Silicon.*