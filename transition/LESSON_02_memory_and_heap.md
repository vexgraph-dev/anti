# Lesson 2 — The Truth About Memory & The "Heap"

*Teacher's voice: "The heap" is one of the most overloaded words in programming. Java's
heap is not C's heap. C's heap is not an arena. An arena is not a stack. Understand
which one you mean, or you will compare apples to orbiting bananas.*

Let's un-tangle the words first, because `anti`'s whole identity is built on the
distinction.

---

## 2.1 The five different things people call "the heap"

1. **The Java heap** — a region of virtual address space managed by a Garbage Collector.
   You never see addresses; you see references. Allocation = `new`, freeing = the GC
   deciding it's unreachable. Object layout is a JVM implementation detail.
2. **The malloc heap** — managed by `malloc`/`free`. You get raw byte addresses. You
   are responsible for freeing. Best-effort: `malloc` uses a mix of `sbrk`/`mmap`
   depending on size (on macOS, large allocations come straight from `mmap`).
3. **mmap / OS virtual memory** — the OS-level primitive: ask the kernel for pages
   (4KB–2MB granularity on Apple Silicon), get a contiguous virtual range. `malloc`
   and Java's heap are *both* built on top of this.
4. **Arena / pool** — a flat block of memory you carve objects out of sequentially.
   Freeing an arena = advancing a cursor (or resetting a cursor to base). No per-object
   free. This is the one `anti` is actually built around.
5. **The stack** — per-thread, grows down, holds call frames. Fastest possible
   "allocation": one register adjustment. Freeing = returning from the function.

`anti`'s README says it right: *"an absolute rejection of the traditional Java heap."*
The architecture is: mmap once (or via `Arena.ofShared()`), then hand-manage pools.

---

## 2.2 Object overhead: 16 bytes of Java you don't pay in C

This is the single most concrete "why" for zero-allocation engines. HotSpot object
layout (64-bit JVM, compressed oops — the default on modern macOS JVMs):

```
Java object on the heap

  ┌──────────┬──────────┬────────────────────────────┐
  │ Mark Word│Klass ptr │   instance fields          │
  │ 8 bytes  │ 4 bytes  │   (whatever the class has) │
  └──────────┴──────────┴────────────────────────────┘
  ├──────── 16 bytes of header ────────┤
  (array adds a 4-byte length field → padded to 16)

  Total: header is 12 bytes (16 with array length), then padding to 8.
```

- **Mark word (8B):** hashCode, lock state, GC age bits. Your data has nothing to do
  with it — the *GC needs it*.
- **Klass pointer (4B, compressed):** points to the class metadata — so the JVM knows
  what type you are and which vtable to use.
- For an array: +4 bytes for length, padded to a 16-byte header.

So **every Java object you `new` pays 16 bytes of pure GC/type bookkeeping before your
first field**. An `int` field you box into `Integer` costs 16 bytes header + 4 bytes
value + padding = **24 bytes**, when the payload is 4.

### Now look at what `anti` already does

Your `oop.TypeRegister.java` and your allocators (e.g. `bit.Bit32.java`) already
implement a *tiny bespoke object header* — but you control it, it's smaller, and it
does what *you* need:

```
anti's off-heap slot (from Bit32.java)

  ┌──────────┬──────────┬────────────────────────────┐
  │ typeId   │ length   │   payload (userPtr points  │
  │ 4 bytes  │ 4 bytes  │   here, 8 bytes past base) │
  └──────────┴──────────┴────────────────────────────┘
  base = userPtr - 8L        userPtr (returned to caller)

  In C terms this is just a struct:
  struct Slot {
      int32_t type_id;   // 0x10000019 = SpinLock singleton, etc.
      int32_t length;    // element count / lifetime marker
      // payload begins at offset 8
  };
```

Your comment in `Bit32.java` (`SINGLETON_SLOT_SIZE = 8 + ...`) and the free-dispatch
in `free()` that reads `type` from `userPtr - 8L` is *literally* reading the type field
of a C struct's header. **You already write C memory layout in Java.** The header is 8
bytes, not 16, because there's no GC, no mark word, no compressed class pointer — you
decided what "type" means, and you made it an `int`.

The 16-byte Java header isn't evil — it's the price of GC + virtual dispatch + identity
hashing. But `anti` already declined to pay it for its data. The question the
masterclass keeps returning to: *why keep paying it for everything else the runtime
touches?*

### A C struct costs zero overhead

```c
// C: a struct's memory footprint is EXACTLY its fields (+ padding, Lesson 3).
// No mark word. No klass pointer. No length field unless you add one.
struct Slot {
    int32_t type_id;    // your TypeRegister form, e.g. FORM_SINGLETON | ID_SPIN_LOCK
    int32_t length;
    /* payload */
};

// sizeof(struct Slot) == 8 on arm64. That is it. The compiler adds nothing.
```

The difference: in Java you *manually* did `userPtr = base + 8L` everywhere. In C, the
compiler generates that `+8` for you, and `struct Slot *s = userPtr - 8; s->type_id` is
a `LDR W0, [X0, #-8]` — identical machine code to your `ForeignMemory.getUnsafeInt(userPtr - 8L)`.

---

## 2.3 Memory ownership: GC vs manual free vs RAII

### Java: the GC owns everything
- Pros: you can't leak, you can't use-after-free, `anti`'s engine threads never
  allocate so GC pressure is near zero.
- Cons: to guarantee *zero* GC pauses you must keep the GC out of your data entirely —
  which is why you went off-heap. The GC is still there, monitoring your *other* Java
  objects (strings, MethodHandles, VarHandles, LWJGL objects, the Image Heap in native
  builds).

### C: you own everything
```c
// C: ownership is explicit, always visible, always your job.
void* p = calloc(1, sizeof(struct Thing)); // you asked
// ... use it ...
free(p);                                    // you give it back — or you leak
```
- No GC at all. `malloc`/`free`/`mmap`/`munmap`. Leaks are a bug you must find
  (Instruments/leaks). Double-free and use-after-free are bugs that crash you.
- The *relief*: `anti` already does manual ownership in Java (`freeAll()`, `Arena.close()`,
  `ForeignMemory.freeNative`). The discipline transfers 1:1.

### C: no destructors — cleanup is a contract, not a mechanism
```c
// C has no destructors. When a scope ends, everything you allocated is still there.
// Your cleanup options, in order of preference for `anti`:
// 1. Arena/pool: you don't free individually — the arena resets, the pool recycles (L13).
// 2. goto cleanup: single exit point releases everything claimed on the way.
static int load_scene(Scene *s) {
    int err = 0;
    if (!chunk_open(&s->handle))  { err = ERR_OPEN;  goto cleanup; }
    if (!chunk_read(&s->handle))  { err = ERR_READ;  goto cleanup; }
    // ... success path ...
cleanup:
    chunk_close(&s->handle);   // runs on EVERY path — that's your destructor
    return err;
}
```
- The Java version of this discipline was `try/finally`. In C it's `goto cleanup` and
  return codes (Lesson 19). Same shape, no hidden machinery.
- The *relief*: `anti` already does manual ownership in Java (`freeAll()`, `Arena.close()`,
  `ForeignMemory.freeNative`). The discipline transfers 1:1 — and the arena/pool model
  (Lesson 13) means most objects *never need individual cleanup at all*.

### Where `anti` sits today
- `Bit32.java` — arena + per-thread caches + ABA freelists: this is a *pool allocator*.
  In C it's the same code, minus `ForeignMemory` (you'd use `mmap` + pointers).
- `Struct.java` — a runtime struct-layout registry: this is C's `sizeof`/`offsetof`
  computed by hand at runtime instead of by the compiler at build time (Lesson 3).
- `SpinLock.java` + `RingBuffer.java` — these are the exact primitives you'd write
  with C11 atomics (Lesson 7).

### Java heap vs C memory — side by side

| | Java heap | C (malloc/mmap/arena) |
|---|---|---|
| You see | references (opaque) | addresses (`uintptr_t`) |
| Header | 16B object header | 0B (yours to add) |
| Freeing | GC discovers | `free`/arena reset |
| Latency | GC pause risk (you avoid it) | none, but you own bugs |
| Leak safety | guaranteed | your discipline |
| Zero-alloc guarantee | opt-in (you did it) | it's the default state of affairs |

---

## 2.4 The mental model that will serve you forever

Think of memory like land.

- **Stack** = land you rent at a street market stall: you show up, use a slot, walk
  away; the stall owner hands it to the next person. Extremely fast, extremely
  temporary.
- **Arena** = buying a whole parking lot: you pay once, then hand out spaces by drawing
  a chalk line (advance a cursor). You never collect individual spaces; you re-chalk
  the whole lot to reset. Fast, no per-object bookkeeping. **This is `anti`'s model.**
- **malloc/free** = individual land parcels with a title office: every plot is
  registered, freed, and reused individually. Flexible, slower, more bookkeeping.
- **GC** = the city decides your land is abandoned and bulldozes it. Convenient, but
  the bulldozer occasionally visits when you're mid-computation. `anti` moved to the
  suburbs to avoid the bulldozer.

Every allocator in `anti` (Bit32/Bit64, Struct registry, GridArray) is either an arena
or a freelist-on-top-of-an-arena. Those are *native* patterns. Java's FFM was your
trowel for building them; C's memory model is the ground they were always standing on.

### Actionable takeaway

Open `bit.Bit32.java` and trace one allocation: `allocateSingleton(typeId)`.
1. It reads a per-thread cache (fast path), or
2. CAS-pops a slot from a freelist head (`SINGLETON_FREE_HEAD_VH.compareAndSet`), or
3. CAS-claims an expansion guard and calls `expandSingletonPool()`.

That is a textbook C pool allocator — the *only* Java-isms are `VarHandle`,
`ForeignMemory`, and `Arena`. In C, each of those maps to: `_Atomic struct Slot* head`,
`atomic_compare_exchange_strong`, and a `mmap` + `calloc` loop. Nothing about your
design changes. The runtime just stops being in the room.

---
*Next: Lesson 3 — Structs, Alignment & The Gotchas of Pointer Math.*
