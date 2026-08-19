# Lesson 16 — Hot Swapping Code and Objects (Reload Without Restarting)

*Teacher's voice: You don't just want fast iteration — you want to change game logic and
see it live, new objects, new functions, without losing the running state. This is hot
reloading, and C is uniquely good at it: code is just a dylib you can drop out from under
a running process. The rules are few and unforgiving. Get them right once and you've
built the thing every engine wishes it had.*

---

## 16.1 The architecture: the engine is a host, the game is a dylib

The whole trick is a hard split:

```
anti host (executable, never reloaded)
  ├─ owns ALL memory (arenas + pools, Lesson 13)
  ├─ owns windows, GPU, input, the frame loop
  └─ calls INTO the game via a table of function pointers

game.dylib (rebuilt + reloaded constantly)
  └─ game logic only: update(), render(), init(), shutdown()
```

This is the Handmade Hero / Exile / raylib-example shape, and it's why `anti`'s
"engine vs gameplay" separation matters. On macOS the loader is `dlopen`/`dlsym`/
`dlclose` (`<dlfcn.h>`). Windows uses `LoadLibrary`/`GetProcAddress`; the design is
identical.

---

## 16.2 The reload loop (the canonical shape)

```c
// The game's interface — every function you can hot-swap lives here.
typedef struct {
    void  (*init)(GameMem *mem);
    bool  (*update)(GameMem *mem, const Input *in, f32 dt);
    void  (*shutdown)(GameMem *mem);
    void *dylib;            // dlopen handle
    uint64_t mod_time;      // mtime of the dylib when it was loaded
    uint32_t version;       // bumped on every successful reload
} GameAPI;

static GameAPI g_game;

// resolve every exported symbol, by name, into the API struct
static bool gameapi_load(const char *path, GameAPI *api) {
    api->dylib = dlopen(path, RTLD_LOCAL | RTLD_NOW);   // see 16.4
    if (!api->dylib) { anti_log("dlopen: %s", dlerror()); return false; }
    *(void **)(&api->init)     = dlsym(api->dylib, "game_init");
    *(void **)(&api->update)   = dlsym(api->dylib, "game_update");
    *(void **)(&api->shutdown) = dlsym(api->dylib, "game_shutdown");
    if (!api->init || !api->update || !api->shutdown) {
        anti_log("missing symbols"); return false;
    }
    return true;
}
```

Each frame: run `update`, then check the dylib's mtime; if it changed, reload:

```c
// copy the new build to a UNIQUE temp path, then dlopen the copy (16.4)
if (modtime(path) != g_game.mod_time && gameapi_load(tmp_path, &fresh)) {
    GameAPI old = g_game;
    g_game = fresh;
    g_game.version = old.version + 1;
    g_game.init(g_mem);              // "hot_reloaded" hook: rebind state
    gameapi_unload(&old);
    g_game.mod_time = modtime(path);
}
```

**Order matters:** load the new, copy any state pointers into it, *then* unload the old.
Both dylibs coexisting briefly is how state survives (16.3).

---

## 16.3 The three rules that make state survive

1. **The game owns zero memory.** All memory comes from host-owned arenas/pools
   (Lesson 13). `dlclose` wipes everything the dylib *owns* — its statics, its globals,
   its `.rodata` string literals. If game state lives in the host's pools, unloading the
   dylib changes nothing.
2. **No game static data.** A global counter inside the dylib resets on reload. The fix:
   globals are *pointers into host-owned state*, and a `game_hot_reloaded(GameMem*)`
   hook re-points them after every load. (The Zig/Odin engines call exactly this
   function.)
3. **Layout changes are a hard limit.** If a reload changes `sizeof(GameMem)` or any
   struct layout, old bytes + new code = garbage. Accept it: hot reload is for *logic*
   tweaks, not schema changes. Exile's answer: "reloading is for online tweaking, not
   altering data structures." If you must change layout, serialize state out and back,
   or just restart — Lesson 10's lesson. Compare `sizeof` before/after and force a
   reset on mismatch.

---

## 16.4 The macOS traps (this is where it actually breaks)

Apple's dyld is *not* Linux's ld.so, and reload gets weird unless you obey these:

- **`dlclose` may not unload.** If the dylib touches the ObjC runtime, Swift, or C++
  ODR symbols, the loader keeps it resident forever — then `dlopen` returns the *old*
  image. `anti`'s game dylib must be **pure C, no ObjC, no `thread_local`**, nothing that
  registers with a runtime.
- **Copy then open a unique path.** The kernel caches the code signature in the file's
  vnode. Rewriting the same file confuses it; opening a *renamed copy* gets a fresh
  vnode. Pattern: compiler writes `game.dylib`, host copies to
  `game_<pid>_<n>.dylib`, `dlopen`s the copy, `dlclose`s after (or leaks the handle —
  reloading is a dev feature; a small leak per reload is fine).
- **Use `RTLD_LOCAL | RTLD_NOW`.** `RTLD_GLOBAL` (macOS's default!) caused the
  stale-library bug above. `RTLD_NOW` binds immediately so missing symbols fail at load,
  not on first call.
- **Do not mutate the on-disk Mach-O** — write new files, then `rename`. Renaming gives
  a new vnode; rewriting corrupts the signature cache.
- Debugging reloaded code with breakpoints is painful; keep the reload path itself dead
  simple, because "bugs in the hot-reload harness" are the worst class of bug (Exile).

---

## 16.5 Safe calling — never call stale code

The #1 crash after a reload is a **stale function pointer**: the old dylib is unloaded,
something still holds `&old_update`, and calling it jumps into unmapped memory.

The rules:

1. **All callable game functions live in ONE `GameAPI` struct**, resolved by name at
   load time (16.2). After a reload you swap the struct wholesale — nothing else in the
   host holds a game pointer.
2. **Game-internal callbacks are the enemy** (the Odin/Zig engine's "Big Bad"): if game
   code registered a callback with the engine, that pointer is stale after reload. Fix:
   centralize callbacks in `setup_callbacks()` / `tear_down_callbacks()` and call them
   from the `hot_reloaded` hook. Never hand out game function addresses that outlive a
   reload.
3. **Version stamp everything.** `GameAPI.version` is checked before any call from
   script/VM (Lesson 17) into game code. If a VM closure captured an old API version,
   it refuses and re-resolves.
4. **Function pointers by name** (Exile's trick): if you must persist a game callback,
   store `{name, ptr}` and re-`dlsym` it on every reload — never store the raw address
   alone. The name is the stable thing; the address is disposable.
5. **Enums over pointers.** Most "I need a callback" cases are really a small set of
   behaviours — switch on an enum (Lesson 12, zylinski's advice) and the problem
   disappears entirely.

**The safe-call wrapper** (host side, around every game entry):

```c
static bool game_call_update(const Input *in, f32 dt) {
    uint32_t v = g_game.version;              // snapshot
    if (!g_game.update) return false;
    g_game.update(g_mem, in, dt);             // call
    if (g_game.version != v) anti_log("reload during update — re-run"); // detect
    return true;
}
```

---

## 16.6 Threads and hot reload

- A dylib that spawns threads leaves them **running after `dlclose`**, pointing into
  freed code — crash on next wakeup. Rule: **the game never creates threads.** The host
  owns all threads and job workers (Lesson 18); game code only submits jobs.
- If reloading with a job system: schedule the reload *as a job that waits for all
  in-flight jobs to drain*, then swaps the API. (The slembcke engine does exactly this.)
- GPU work: the game submits render commands into host-owned arenas; a reload between
  submission and presentation is fine because the commands are data, not code. Never
  let the game hand the GPU a function pointer.

---

## 16.7 Release builds

The reload path is **dev-only**. Ship builds statically link the game and skip
`dlopen` entirely:

```
game_logic.c ──static──▶ anti release binary (no dylib, no dlopen, no reload)
```

Same source, one `#ifdef ANTI_DEBUG` around the load/unload code. This also means the
release binary can't be the victim of any reload-harness bug.

---

## 16.8 What hot reload gets you (and what it doesn't)

| Yes | No |
|---|---|
| edit logic, see it live in ~1s | change struct layout / schema (forces restart) |
| tweak constants, tuning, AI behaviour | serialize arbitrary state automatically |
| iterate gameplay without losing scene state | fix bugs in the reload harness itself |
| new functions in the dylib appear on reload | reload ObjC/Swift/C++-rich dylibs reliably |

The payoff is the debug loop: ms-scale, not min-scale. Build it once, guard it with the
rules, and it becomes the single best productivity tool in the engine.

---

*Next: Lesson 17 — opcodes and scripting: your own bytecode VM, register-based, zero-alloc,
sandboxed by design.*