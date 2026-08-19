# Lesson 7 — Concurrency & Lockless Primitives on Modern CPUs

*Teacher's voice: This is the lesson where `anti`'s design philosophy and native reality
finally line up 1:1. Your SpinLock, your ABA-tagged freelists, your RingBuffer — these
are already textbook native concurrency primitives. The only thing FFM is doing for you
is handing you the atomic instructions through a thick straw. Let's see the native
versions and the ARM64 memory model they're built on.*

---

## 7.1 Threads: pthread vs Java Virtual Threads

**Java Virtual Threads** are a user-space scheduling abstraction: millions of
lightweight tasks multiplexed onto carrier platform threads by the JVM. They are
brilliant for I/O-heavy workloads where threads block on sockets/files — the whole
point is that blocking is cheap because you have thousands of tasks sharing dozens of
OS threads.

**pthread** is the OS's real thread: a kernel-scheduled execution context, ~64KB
minimum stack, context switches that cost microseconds of kernel time, actual
concurrency on multicore. You don't "schedule" them — the kernel does.

`anti`'s engine threads (`DrawThread`, `NetworkingThread`, `ScriptingThread`,
`EventThread`, `UIThread`, `ConsoleThread`) are dedicated, long-lived, OS-pinned
workers — the exact opposite of the virtual-thread use case. They are platform threads
today (in Java, `Thread.ofPlatform()`), and they map 1:1 to pthreads:

```
Java:  new Thread(() -> { ... }).start()          // ThreadRegistry.getThreadIndex()
C:     pthread_create(&t, NULL, thread_main, arg) // then get your core index
```

The one thing to keep: `Thread.onSpinWait()` maps to `__builtin_yield()` / the arm64
`YIELD` hint instruction — it tells the CPU "I'm spinning, don't waste the whole
pipeline." Same intent, same hot-loop placement.

---

## 7.2 Atomic primitives: VarHandle vs `<stdatomic.h>` vs `<atomic>`

Your Java (the canonical ABA-tagged freelist CAS from `bit/Bit32.java`):

```java
// anti/bit/Bit32.java — allocateSingleton(), the core CAS loop
long oldTagged  = singletonFreeHead;                 // volatile read of head
long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;   // strip the 16-bit gen tag
ForeignMemory.setUnsafe(userPtr, oldRawHead);        // link our slot to old head
long nextGen   = ((oldTagged >>> 48) + 1L) & 0xFFFFL;// bump the generation
long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);
if (SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break; // CAS
```

C11 `<stdatomic.h>` — the same thing, and the names line up almost word-for-word:

```c
// C11 — identical ABA-tagged freelist pop
_Atomic uint64_t head;                       // top 16 bits = gen tag, low 48 = ptr
uint64_t oldTagged, oldRaw, newTagged, next;

do {
    oldTagged  = atomic_load_explicit(&head, memory_order_relaxed);
    oldRaw     = oldTagged & 0x0000FFFFFFFFFFFFULL;
    *userPtr   = oldRaw;                      // store "next" link into the slot
    next       = ((oldTagged >> 48) + 1) & 0xFFFFULL;
    newTagged  = (next << 48) | (*userPtr & 0x0000FFFFFFFFFFFFULL);
} while (!atomic_compare_exchange_weak(&head, &oldTagged, newTagged));
// strong/weak CAS; weak may spuriously fail → loop (fine, it's a spin)
```

Translation table you can pin to your monitor:

| `anti` (Java) | C11 `<stdatomic.h>` |
|---|---|
| `VarHandle.compareAndSet` | `atomic_compare_exchange_strong/weak` |
| `getVolatile()` | `atomic_load_explicit(..., memory_order_acquire)` |
| `setVolatile()` | `atomic_store_explicit(..., memory_order_release)` |
| `Thread.onSpinWait()` | `__builtin_yield()` (arm64 `YIELD`) |
| `AtomicIntegerFieldUpdater`-style | `_Atomic int32_t` |

Your `SpinLock.java` translated to C11 (this is the entire file, ~15 lines):

```c
// anti/thread/SpinLock.java → C11
static _Atomic int32_t lock_word;

static inline void spin_lock(_Atomic int32_t* w, int32_t my_ticket) {
    int32_t expect = 0;
    while (!atomic_compare_exchange_weak_explicit(w, &expect, my_ticket,
                                                  memory_order_acquire,
                                                  memory_order_relaxed)) {
        __builtin_yield();               // Thread.onSpinWait()
        expect = 0;                      // retry: expect unlocked again
    }
}

static inline void spin_unlock(_Atomic int32_t* w) {
    atomic_store_explicit(w, 0, memory_order_release);   // setVolatileInt(ptr, 0)
}
```

Your owner-encoding trick (`(threadId & 0x3FFFFFFF) << 1 | 1`) and the
`unlock` ownership check port verbatim — that's pure logic, no Java in it.

---

## 7.3 The ARM64 memory model (why your locks work — or silently don't)

This is the part Java abstracts away and native code must understand. Apple Silicon is
an **ARMv8.x** chip with a **weak memory model** — unlike x86's strong Total Store
Ordering (TSO), ARM allows the CPU to reorder loads and stores across *different*
memory locations aggressively, in the name of speed.

The dangerous scenario (why "volatile" is not enough):

```
Thread A:                          Thread B:
  data = 42;        // plain store      if (flag == 1) {   // plain load
  flag = 1;         // plain store         use(data);      // might see 0 or garbage!
```

On ARM, Thread B can observe `flag == 1` **before** `data == 42` is visible. No
ordering is guaranteed unless you use the synchronization instructions.

### The synchronization instructions you'll actually emit

- **`LDAR`** (load-acquire) — a load that cannot be reordered with any *later* memory
  operation. Reading a lock word with acquire = everything after the read is ordered.
- **`STLR`** (store-release) — a store that cannot be reordered with any *earlier*
  memory operation. Writing a lock word with release = everything before is ordered.
- Combined (as in a lock/unlock pair), they form **RCsc**: a release-store on one core
  and an acquire-load on another core synchronize — the classic mutex pairing.
- ARMv8.3 added **`LDAPR`** (load-acquire-PC) which is cheaper and gives RCpc; clang 16+
  and GCC 13+ emit it automatically for C acquire loads.

Compilers translate `memory_order_acquire`/`release` into `ldar`/`stlr` (and the
load-exclusive/store-exclusive variants `ldaxr`/`stlxr` for CAS loops). You write the
ordering in the source; the compiler picks the instruction.

### Measured reality on Apple Silicon (important for your tuning)

Academic measurements on the M1 (Wrenger, 2024; ARM-weak-memory work):
- **Acquire/release atomics are measurably slower than relaxed ops** on Apple Silicon.
  Plain `str`/`ldr` are cheapest; `stlr`/`ldar` cost extra ordering; the old LDAR (pre-
  LDAPR) behaved like SC.
- On x86, acquire/release are free (TSO makes every op strong). **Do not assume x86
  tuning transfers to Apple Silicon.** `anti` is Apple-only; tune for the weak model.
- The "weak" model's freedom also means you *must* be explicit: Java's `VarHandle`
  default is acquire/release for `compareAndSet`; C11's default is the same if you
  write `memory_order_acq_rel`. Default is correct; relaxed is an explicit, justified
  optimization.

Your `Bit32` freelist CAS uses the top 16 bits as a **generation tag** — that's the
classic **ABA-prevention** trick: a slot popped and re-pushed can look identical to a
stale reader; the tag makes the head value unique across cycles. That's a *data-race*
design, not a bug — it's the correct lockless pattern, and it ports byte-for-byte.

---

## 7.4 Cache lines, false sharing, and where `anti` already got it right

Caches move data in **64-byte cache lines** (Apple Silicon L1d = 64B). Two threads
hammering *different* variables that happen to share one line will ping-pong that line
between cores — every write invalidates the other core's copy. That's **false sharing**:
no shared *data*, but shared *hardware state*, and it can tank throughput to single-
digit percentages.

```
Cache line (64 bytes) — shared by two cores:
┌────────────────────────────────────────────┐
│ [x1 = Thread A's counter] [x2 = Thread B's] │  ← one line, two writers
└────────────────────────────────────────────┘
A writes x1 → line invalidated on B's core → B's next x2 access fetches again... forever
```

Fix: **pad** so each thread's hot data lives on its own line:

```c
struct alignas(64) PerThreadCache {   // alignas(64) → never shares a line
    int64_t count;                     // only this thread writes it
    int64_t _pad[7];                   // fill the rest of the 64-byte line
};
```

Where `anti` already nailed this: your `CACHE_ARENA_BASE` in `Bit32.java` gives each
thread a **1024-byte slot** (`threadIdx * 1024L`) — 16 cache lines per thread, so
per-thread caches never false-share. That's exactly right, and it's the same reasoning
you'd use in C. The lesson for the native port: **keep every per-thread hot field on a
64-byte-aligned 64-byte chunk, and never let two producers share a line.**

---

## 7.5 Translating `thread.SpinLock` and `thread.RingBuffer` to C11 atomics

**SpinLock** — done above (Section 7.2). The `unlock` owner check and ticket encoding
port verbatim.

**RingBuffer** (SPSC or MPSC) — the classic lockless queue: `head` (writer) and `tail`
(reader) indices, published with release/acquire:

```c
// Single-producer single-consumer ring buffer — C11
typedef struct {
    _Atomic int32_t head;    // writer index, release on store
    _Atomic int32_t tail;    // reader index, acquire on load
    int32_t        data[N];  // payload; the index publication orders the data
} RingBuffer;

static int32_t ring_try_push(RingBuffer* rb, int32_t v) {
    int32_t h = atomic_load_explicit(&rb->head, memory_order_relaxed);
    int32_t t = atomic_load_explicit(&rb->tail, memory_order_acquire); // see oldest free
    if (h - t == N) return -1;                       // full
    rb->data[h & (N-1)] = v;                         // plain store — ordered by the release
    atomic_store_explicit(&rb->head, h + 1, memory_order_release);   // publish
    return 0;
}
static int32_t ring_try_pop(RingBuffer* rb, int32_t* out) {
    int32_t t = atomic_load_explicit(&rb->tail, memory_order_relaxed);
    int32_t h = atomic_load_explicit(&rb->head, memory_order_acquire); // see newest data
    if (h == t) return -1;                           // empty
    *out = rb->data[t & (N-1)];                      // plain load — ordered by acquire
    atomic_store_explicit(&rb->tail, t + 1, memory_order_release);
    return 0;
}
```

The acquire on `head` before reading data, and the release on `head` after writing
data, are precisely the `VarHandle` semantics Java gave you — just spelled out with the
ordering you're choosing explicitly instead of a default.

### Actionable takeaway

Port `thread.SpinLock.java` to C11 (it's the 15-line file above). Then port your
`Bit32` freelist CAS. You will discover the total "port" of `anti`'s concurrency layer
is a handful of small files — because the concurrency layer was already written in
"native" style. The Java-isms (`VarHandle`, `Thread.onSpinWait`) are a thin translation
layer with a one-line mapping.

---
*Next: Lesson 8 — Graphics Pipelines (Vulkan & GPU Buffers).*