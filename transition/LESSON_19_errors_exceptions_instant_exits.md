# Lesson 19 — Errors, Exceptions, Safe Calling, and Instant Exits

*Teacher's voice: In Java you threw `RuntimeException` and the GC unwound for you. In C
there is no unwind — so you build your own error story from three tools: return codes
(90% of cases), `setjmp`/`longjmp` (your "exception" for deep, non-recoverable paths),
and an exit discipline (your finally, run in the right order). Plus the safe-calling
rules that keep a failure from becoming a crash.*

---

## 19.1 The error pyramid (choose the right tool per layer)

| Layer | Tool | Why |
|---|---|---|
| Ordinary function calls | **return codes** | cheap, explicit, locally checkable |
| Deep call chain, no recovery | **`setjmp`/`longjmp`** (your try/throw/catch) | one handler for a whole subsystem |
| Script/VM boundary | **VM error + resetStack** (Lesson 17.6) | sandbox: abort the script, keep the engine |
| Unrecoverable engine bug | **`anti_panic` → instant exit** | fail loud, fail fast |

`anti` bans `errno` — errors are values, not global state. Every function's header
comment states its failure contract (Lesson 12.4, 15.3): "returns `-1` on invalid
handle" or "panics."

---

## 19.2 Return codes — the default

```c
static int32_t world_spawn(World *w, uint32_t proto_id, uint32_t *out) {
    if (proto_id >= w->protos.count) return ERR_NO_PROTO;
    uint32_t h = pool_acquire(&w->entities);
    if (h == POOL_NULL) return ERR_POOL_FULL;
    Entity *e = entity_from_handle(w, h);       // bounds-checked cast (13.5)
    entity_init(e, proto_id);
    *out = h;
    return OK;
}
```

- Check every return. The AI contract (15.4) makes unchecked errors a review failure.
- Propagate with the `goto cleanup` single-exit pattern so acquired resources are
  released on *every* path (15.4).

---

## 19.3 Your own exceptions: `setjmp`/`longjmp`

This is what libpng does, what Symbian's `Leave` did, and what Go's
`defer/panic/recover` is *made of*. It is a **non-local jump**: `longjmp` rewinds the
call stack to a saved point. Perfect for "deep parser hit a syntax error, abandon the
whole parse" or "shader compile failed, bail out of the pipeline" — cases where
propagating an error code through 8 layers is noise.

```c
typedef struct {
    jmp_buf    env;
    int        code;            // error id
    char const *msg;            // points into a static/arena buffer, never allocated
} Exception;

// one handler per subsystem — set up at the subsystem boundary
static Exception g_asset_ex;

#define TRY(e)   ((e)->code = 0, setjmp((e)->env) == 0)
#define THROW(e, c, m) ((e)->code = (c), (e)->msg = (m), longjmp((e)->env, 1))

int asset_load(const char *path) {
    Exception *e = &g_asset_ex;
    if (TRY(e)) {
        parse_header(path);     // may THROW deep inside
        parse_payload(path);
        return OK;
    }
    anti_log("asset error %d: %s", e->code, e->msg);   // the catch
    return ERR_ASSET;
}
```

**The non-negotiable rules** (from the standard and 20 years of pain):

1. `setjmp` must be called **directly in a condition/assignment** as above — never
   wrapped in another function.
2. **The `setjmp` function must still be on the call stack** when `longjmp` runs.
   No jumping across a returned frame.
3. Any local modified between `setjmp` and `longjmp` that you need after must be
   `volatile`.
4. **No VLAs** anywhere in the jumping region (they leak on unwinding).
5. **No resources that must be freed between the jump** — `longjmp` skips all cleanup.
   In `anti` this is *fine*: everything in the region lives in arenas that reset at the
   frame boundary (Lesson 13). That is exactly why the zero-alloc doctrine makes
   longjmp safe where it would be poison in a malloc-heavy C++ program.
6. Threads: `setjmp`/`longjmp` only within the **same thread**. Your asset/scene loads
   happen on the main thread; workers never throw across a job boundary (18.5).

For *nested* handlers (try inside try), chain a linked list of `Exception` nodes and
pop on exit — the `cx_impl` design does exactly this with macros. Start with the single
per-subsystem handler; you will rarely need more.

---

## 19.4 When NOT to use longjmp

- In the **frame hot path** (it costs a `jmp_buf` save on every `TRY` even when nothing
  throws). Hot functions return codes.
- Across threads. Across a job boundary.
- Where "which resources leaked" is ambiguous. The arena reset is what licenses this at
  all; if a subsystem doesn't own an arena, use return codes there.

**`anti`'s rule of thumb:** longjmp at *boundaries* (scene load, script compile, asset
batch), return codes *inside* (per-object ops). Never both in one call path.

---

## 19.5 Safe calling — the full doctrine

Three kinds of "safe" you need:

**1. Callback safety (reload):** never call a stale function pointer. The `GameAPI`
version-checked table + resolve-by-name (16.5). VM natives via registered tables
(17.7). Summary: **the only callable foreign functions are the ones you re-resolve
every load.**

**2. Boundary safety (memory):** every call that receives a handle/pointer re-validates
it against real bounds *before* dereferencing (13.5's `entity_from_handle`). IDs, not
raw pointers, cross the script/engine boundary (17.7). A `uint64_t {tag,idx}` handle is
double-checked: tag mismatch = stale, refuse.

**3. ABI safety (interop):** calling `objc_msgSend`, Vulkan, MoltenVK — the signature
must match exactly (Lesson 5). Use typed `MethodHandle`-style wrappers, `_Static_assert`
arg sizes, and never varargs-ify a fixed ABI. On arm64, a mismatch is silent memory
corruption, not a loud fault.

The safe-call wrapper pattern (16.5) — snapshot a version, call, detect a reload that
happened mid-call — is the pattern to repeat everywhere a *long-lived* callback could
race a reload.

---

## 19.6 Instant exits (the panic path)

Two exit speeds, both controlled:

**1. Clean shutdown (frame loop exit):** the loop's `while (!quit)` condition. Set
`quit = true`, drain jobs, `vkDeviceWaitIdle`, tear down in reverse init order, `exit(0)`.
This is the *normal* path and must always be reachable — a game that can't quit cleanly
fails its OS integration.

**2. Instant exit (fatal):** `anti_panic()`:

```c
_Noreturn void anti_panic(const char *msg) {
    anti_log("%s", msg);        // ring buffer, never blocks (12.5)
    if (g_crash_on_panic) abort();          // debug: get a core/backtrace
    _Exit(EXIT_FAILURE);                    // release: leave NOW
}
```

- `_Exit` skips atexit handlers, flush, everything — it is the "no further damage"
  exit. Use it for corruption detected mid-frame (bad header, CAS failed twice).
- `exit()` runs atexit handlers — use it for the *clean* shutdown path only, and make
  sure your atexit list is empty or harmless (or tear down manually instead, 19.5).
- **Instant exit is a last resort, and that's a feature.** In a zero-alloc engine a
  detected invariant violation means your state is already inconsistent — continuing is
  worse than dying. Fail loud, log the reason, let the crash reporter or restart bring
  you back. `anti`'s "absolute rejection of the traditional heap" gets its other half
  here: *absolute rejection of undefined behavior.*

Debug builds: `abort()` (LLDB catches it, backtrace in hand). Release builds: `_Exit`
+ the log line. The log is your only artifact — make it say exactly what invariant
broke.

---

## 19.7 The unified error flow (how it all composes)

```
frame loop:
  ├─ job batch … if a job detects a bad handle → return code → job fails → batch aborts
  ├─ game_update() … may THROW (script boundary longjmp) → caught at subsystem → logged
  ├─ asset load … THROW caught at scene load → scene stays on previous state
  └─ any invariant violation → anti_panic → instant exit (debug: abort for the trace)

quit: clean path → drain → wait GPU → reverse teardown → exit(0)
```

Every failure is either (a) handled and logged, (b) caught by a boundary and rolled
back, or (c) a fatal that exits instantly with a reason. There is no silent "continue
with garbage" — that's the soup-maker's habit, and it's banned.

---

## 19.8 Checklist before shipping an error path

- [ ] Every failing function returns a documented error code (or throws via a boundary
      `Exception`, or panics — never silently).
- [ ] Every acquired resource is released on every path (`goto cleanup`, 15.4).
- [ ] All `longjmp` uses obey 19.3's six rules (volatile, on-stack, no VLAs, no
      manual frees in the region, same thread).
- [ ] Handles are validated (bounds + tag) before every deref across a boundary.
- [ ] Reload-aware callbacks go through the version-checked table (16.5).
- [ ] `anti_panic` is the only exit a fatal uses; the clean `quit` path always exists.

---

*This is the last lesson. You now have the full `anti`-in-C curriculum: foundations
(11–12), memory (13), bits/SIMD (14), the AI contract (15), hot reload (16), scripting
(17), threads (18), and errors/exits (19). The lessons bridge straight onto the new
`anti` codebase — Lesson 15's prompt template is the contract you paste before every
feature. Go build the thing.*