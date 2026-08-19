# Lesson 18 — Threads and the Job System (Using Every Core, Sans Races)

*Teacher's voice: Lesson 7 gave you the atomics — the vocabulary. This lesson is the
*sentence*: the threading *architecture* of `anti`. A zero-alloc engine has a clean answer
to parallelism: a fixed pool of worker threads running a job queue, no locks in the hot
path, state owned by whoever writes it, and — critically — the game never owns threads,
so hot reload never breaks (Lesson 16.6).*

---

## 18.1 Why a job system, not "locks everywhere"

The M-series has performance cores and efficiency cores. A single-threaded loop leaves
most of them idle. But "just add a mutex around the state" is how you get priority
inversion, cache-line ping-pong, and deadlocks.

The job-system answer, in one line:
**Work is split into small jobs. A fixed pool of workers pulls jobs off a lock-free
queue and runs them. The main thread submits jobs and waits for the frame's batch.**

Benefits that matter for `anti`:
- No per-frame thread creation (threads are created once at init — zero-alloc rule).
- No global lock in the hot path — one lock-free queue (your atomics from Lesson 7).
- Deterministic frame structure: submit → drain → present.
- Reload-safe: workers run *jobs*, never game functions directly (16.6).

---

## 18.2 The core shapes

**Job** — a function pointer + a payload *owned by the caller's arena* (data, not
owned by the job):

```c
typedef void (*JobFn)(void *ctx, uint32_t worker_id);

typedef struct {
    JobFn  fn;
    void  *data;      // points into a caller arena — valid until the batch drains
    uint32_t payload; // small ids travel by value, no deref needed
} Job;
```

**Queue** — a bounded ring, lock-free (Lesson 7's CAS/acquire-release primitives):

```c
#define JOB_CAP 4096
typedef struct {
    _Atomic uint32_t head;        // claim index, release store
    _Atomic uint32_t tail;        // publish index, acquire load
    Job slot[JOB_CAP];
} JobQueue;
```

Single-producer / multi-consumer: the main thread *submits* (one producer), workers
*claim* (many consumers). That's the classic SPSC/MPMC split your `_Atomic` discipline
handles without locks.

---

## 18.3 The worker thread (the whole secret, ~30 lines)

```c
static _Atomic uint32_t g_exit;

static void *worker_main(void *arg) {
    uint32_t id = (uint32_t)(uintptr_t)arg;
    while (!atomic_load_explicit(&g_exit, memory_order_acquire)) {
        Job j;
        if (job_try_pop(&g_queue, &j)) {          // lock-free claim
            j.fn(j.data, id);
        } else if (job_wait_and_park(&g_queue)) {
            ;                                       // slept, was woken by a push
        }
    }
    return NULL;
}
```

- Workers **park** (block) when empty and get woken on push — no busy-spin burn on
  idle cores. `job_wait_and_park` uses a futex/condvar *only* for parking, never for
  queue correctness.
- **One worker per performance core**, created at init, owned by the host forever.
  Game code never spawns threads (16.6) — this is where that rule is enforced.
- Each worker has its own private pool/scratch arena so per-worker state never shares
  a cache line (14.5's `_Alignas(64)` false-sharing fix).

---

## 18.4 The frame structure (determinism you can rely on)

```
frame N:
  main: build job batch (tasks: physics broadphase, transform update, script ticks,
        asset load callbacks)      → push all jobs
  main: wait for batch complete     → drain flag via acquire/release
  main: game_update() (single-threaded, owns mutable world state)
  main: submit render commands to GPU arena
  present; arena_reset; next frame
```

- **Two layers:** parallel jobs (read-only over SoA data, produce results into
  per-worker arenas) then a single-threaded consolidation pass (the only thing that
  mutates shared state). This is the pattern that makes races *structurally rare* —
  not because you're careful, but because the design forbids them.
- Jobs that need shared output write to **per-worker slices** of a preallocated array
  (index = worker_id), and the consolidation pass merges. No locks, no contention.
- `restrict` every job that reads `const` input arrays and writes a disjoint output —
  the compiler vectorizes the parallel region (12.3, 14.7).

---

## 18.5 The rules that prevent the classic disasters

| Disaster | Prevention |
|---|---|
| Data race | jobs only write their own worker slice; shared state mutated by the single-threaded pass only |
| Deadlock | jobs never wait on other jobs (no nested dependencies) — if you need order, submit in dependency batches |
| Priority inversion | no locks held across job boundaries; the queue is lock-free |
| False sharing | per-worker structures `_Alignas(64)`; hot atomics on their own cache line |
| Torn reads | all cross-thread publishes are release stores, all consumes are acquire loads (Lesson 7) |
| Threads leak on reload | the game never creates threads; only the host's fixed pool exists (16.6) |

The "no nested job wait" rule is the big one: a job that blocks on another job can
deadlock when every worker is blocked. Solution: **breadth — split work so jobs are
independent; sequence via the drain point, not via waiting.**

---

## 18.6 Interop with the other systems

- **Hot reload:** reload is a job that drains the queue, swaps `GameAPI` (16.5), then
  resumes. Workers never hold game pointers between jobs.
- **Scripting (17):** script ticks are jobs; each VM's register array is owned by that
  job's worker arena. Scripts can't spawn threads, and their native calls are the
  single-threaded pass.
- **GPU:** render command emission is a job (fill an arena with `vkCmd*` calls); actual
  `vkQueueSubmit` happens on the main thread after drain. Never hand a GPU function
  pointer from a reloaded dylib (16.5).
- **Debug builds:** run with ThreadSanitizer (`-fsanitize=thread`) periodically — it
  finds races ASan can't. It's slower, so keep it in the "weekly" lane, not every run.

---

## 18.7 Building it (in the right order)

1. **The lock-free queue + workers that run one trivial job.** Prove the skeleton under
   TSan.
2. **Per-worker arenas** (Lesson 13.2) and worker_id slices. No shared writes yet.
3. **The batch/drain API**: `jobs_submit(n)`, `jobs_wait()`. Main thread owns the
   frame structure.
4. **Port one subsystem** (say, transform update) into a parallel job. Measure:
   M-series scaling should appear immediately; if it doesn't, the job is touching
   shared cache lines (14.5) or a hidden lock.
5. Add parking/wakeup so idle workers sleep. Then it's done — you have the whole system.

---

## 18.8 The one-paragraph summary

`anti`'s parallelism is: **fixed host-owned workers, a lock-free ring queue, jobs that
write only their own worker slice, a single-threaded consolidation pass, and a frame
drain point.** The atomics are Lesson 7. The memory is Lesson 13. The reload is
Lesson 16. Put those together and the M-series cores all earn their keep — deterministically,
without a single `malloc` in the loop.

---

*Next: Lesson 19 — the last system: errors, exceptions (your own, C-style), safe calling
in depth, and instant exits.*