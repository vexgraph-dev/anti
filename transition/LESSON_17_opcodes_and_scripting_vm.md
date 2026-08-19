# Lesson 17 — Opcodes and Scripting: Your Own Bytecode VM

*Teacher's voice: Hot reload covers native logic tweaks. But you also want *content* that
can't crash the engine, tweaked by designers or shipped as data — that's a scripting
layer. You already speak the vocabulary: opcodes, dispatch, registers. `anti`'s type
headers are basically your first bytecode. This lesson builds the whole thing: a
register-based VM, zero-alloc, running inside your pools, safely calling back into
engine code.*

---

## 17.1 Why a VM, and why register-based

Two families exist (Crafting Interpreters covers both beautifully):

| | Stack VM | Register VM |
|---|---|---|
| instructions | tiny (`PUSH A`, `ADD`) | bigger (`ADD r0, r1, r2`) |
| dispatch count | 3–4 per operation | 1 per operation |
| compiler | easy | harder (register allocation) |
| speed | slower | ~1.5–2× faster (Lua 5.0 switched and measured it) |

`anti` wants deterministic, hot, zero-alloc scripting → **register VM**. A real CPU is a
register machine; your VM is just a very small, very safe CPU.

---

## 17.2 The instruction format: fixed 32-bit

Fixed width means the fetch is `*(uint32_t *)ip; ip++` — perfectly linear memory access,
hardware prefetcher loves it, decode is shift-and-mask.

```
┌──────────┬──────────┬──────────┬──────────┐
│ opcode   │ reg A    │ reg B    │ reg C    │    iABC: OP rA = rB op rC
│ 8 bits   │ 8 bits   │ 8 bits   │ 8 bits   │
└──────────┴──────────┴──────────┴──────────┘

┌──────────┬────────────────────────────────┐
│ opcode   │  signed jump offset / constant │   iSx: OP ±n
│ 8 bits   │        24 bits                 │
└──────────┴────────────────────────────────┘
```

```c
typedef uint32_t Instruction;         // the whole VM is one word per instruction

#define OPCODE(i)  ((i) & 0xFF)
#define ARG_A(i)   (((i) >>  8) & 0xFF)
#define ARG_B(i)   (((i) >> 16) & 0xFF)
#define ARG_C(i)   (((i) >> 24) & 0xFF)
#define MK_ABC(op,a,b,c) ((uint32_t)(op) | ((a)<<8) | ((b)<<16) | ((c)<<24))
#define ARG_S(i)   ((int32_t)(i) >> 8)      // sign-extended jump distance
```

8-bit opcode = 256 opcodes max — plenty. 8-bit registers = 256 registers per function —
plenty. Big constants (floats, strings, large numbers) don't fit in the word; they live
in a **constant pool** and the opcode references it by index (see 17.4).

---

## 17.3 The execution engine: registers, chunk, dispatch

```c
#define MAX_REGISTERS 256
#define MAX_STACK    1024

typedef struct {
    float      r[MAX_REGISTERS];   // the "registers" — one function frame
    const Instruction *ip;         // program counter
    const float       *k;          // constant pool
    size_t             kcount;
} Frame;

typedef struct {
    Frame  frame;
    // host-owned pools hold everything else (17.6)
} VM;
```

The dispatch loop. Two options:

**Option 1 — plain `switch`** (portable, clear; this is what you ship first):

```c
static int vm_run(VM *vm, uint32_t limit) {
    for (uint32_t n = 0; n < limit; ++n) {
        Instruction i = *vm->frame.ip++;
        switch (OPCODE(i)) {
        case OP_ADD: {
            uint32_t a = ARG_A(i), b = ARG_B(i), c = ARG_C(i);
            vm->frame.r[a] = vm->frame.r[b] + vm->frame.r[c];
            break;
        }
        case OP_RETURN: return 0;
        /* ... */
        }
    }
    anti_log("script limit exceeded");   // opcode budget = crash guard
    return VM_ERROR_STEP_LIMIT;
}
```

**Option 2 — computed goto** (the `0xCP` VM, LuaJIT, and the fast engines): map each
opcode to a C label address and `goto` straight there — no switch, the branch predictor
learns each opcode's successor independently:

```c
// clang extension: &&label is a label address; designated-initialize a jump table
static const void *const dispatch[256] = {
    [OP_ADD]    = &&DO_ADD,
    [OP_SUB]    = &&DO_SUB,
    [OP_RETURN] = &&DO_RETURN,
};
#define DISPATCH() do { goto *dispatch[OPCODE(*vm->frame.ip++)]; } while (0)

DO_ADD: {
    Instruction i = *(vm->frame.ip - 1);
    vm->frame.r[ARG_A(i)] = vm->frame.r[ARG_B(i)] + vm->frame.r[ARG_C(i)];
    DISPATCH();
}
DO_RETURN: { return 0; }
```

Use option 1 first (it's debuggable), switch to option 2 when profiling says dispatch is
the bottleneck. Both are C; both are zero-alloc.

---

## 17.4 Chunks: bytecode is data, not objects

A chunk is a flat array in host memory — exactly your Java `[typeId][length]` header
discipline:

```c
typedef struct {
    uint8_t      type_id;       // = CHUNK_TYPE, your header convention
    uint32_t     count;
    uint32_t     capacity;
    Instruction *code;          // owned by the chunk (host pool)
    float       *consts;        // constant pool
    uint32_t     const_count;
} Chunk;
```

- One chunk per function/script. Linear array → cache-friendly fetch (16.2's point:
  the prefetcher streams `ip++`).
- Constants (floats, hashed strings) are appended to `consts`; the opcode stores the
  *index* in its 24-bit field (`OP_LOADK rA, kIdx`).
- All chunks live in host pools/arenas → **the script system never allocates**.

---

## 17.5 Control flow is just arithmetic on the IP

`if`/`while`/`for` in bytecode are jump instructions that add a signed offset to `ip`:

```
0  OP_LT    r0, r1, r2        ; r0 = (r1 < r2)
1  OP_JMPF  r0, +4            ; if !r0 skip 4 instructions
2  ... loop body ...
6  OP_JMP   -6                ; back to the condition
```

The compiler emits a *dummy* offset (0), then **backpatches** it once it knows the
destination (the `0xCP` series shows this exactly). This is the only "clever" part of a
compiler, and it's ~20 lines.

**Your safety guard:** every `OP_JMP`/`OP_JMPF` is bounds-checked — `new_ip` must land
inside `[chunk_start, chunk_end)` or the VM aborts the script. Untrusted scripts never
get to jump off the map. That's your "no crash from script" promise.

---

## 17.6 The zero-alloc runtime

Everything the VM needs comes from `anti`'s pools and arenas:

```
host pool  →  VM objects (one per active script)
host arena →  per-frame scratch (stack, temp buffers) — reset every frame
chunks     →  loaded from disk at init (baked), or compiled in an arena
```

- The VM's register array and stack are *inside* the VM struct — no allocation at
  `OP_CALL`, no grow/realloc in the loop. `MAX_STACK`/`MAX_REGISTERS` are hard caps.
- On script crash/error: **reset the frame's `ip` to the chunk start** and re-enter —
  or, in a sandbox shell, zero the stack and start fresh (Lair's pattern, and Crafting
  Interpreters' `resetStack`). The error is reported; the engine keeps running.
- Instruction **step budget**: every `vm_run` takes a max-ops limit. A buggy script
  loops forever? It hits the budget, the VM aborts, and your frame loop survives.

---

## 17.7 Safe calling — the VM into the engine (and vice versa)

The VM must call engine functions (draw, spawn, query) and the engine must call back
into scripts. Both directions are where "safe" lives.

**Script → engine (the sandboxed call):**

```c
// table of native functions the script may call
typedef struct { uint32_t hash; int32_t (*fn)(VM*, Frame*); } NativeFn;

static int32_t n_spawn(VM *vm, Frame *f) {   // native bound to "spawn"
    Entity e = world_spawn(vm->ctx, (int32_t)f->r[ARG_A_of_caller]);
    f->r[0] = (float)(uint32_t)e.handle;
    return 0;
}
```

- The script can only reach engine functions **you registered**. No arbitrary
  `dlsym`, no raw pointers across the boundary — the script holds *indices and ids*,
  and the engine re-validates them (Lesson 13.5's `entity_from_handle`).
- Never pass a raw pointer into script-visible state. IDs, not addresses.

**Engine → script (callbacks / event handlers):**

- The engine stores a `{script_id, function_id}` handle, not a native address.
- When a callback fires, the engine looks up the *current* VM + current chunk for that
  function — which is reload-safe: if the script was replaced, the id resolves to the
  new bytecode (same principle as Lesson 16.5's resolve-by-name). Version stamps on the
  chunk make stale handles detectable and skippable.

---

## 17.8 Scripting + hot reload together

The two systems compose cleanly if you keep one rule: **the VM never holds a pointer
into the game dylib.** All game calls go through the `GameAPI` version-checked table
(Lesson 16.5). Consequences:

- Reload the dylib → scripts keep running, next `OP_CALL_GAME` uses the new API. If a
  function changed signature, the version bump makes the call fail loudly, not silently.
- Replace a script chunk → the engine re-resolves callbacks by id, same mechanism.
- Scripts are also a *fallback* when hot reload isn't viable (e.g. shipped content):
  logic that ships as bytecode never needs the native reload path at all.

---

## 17.9 The build order (start small, prove the loop)

1. **Chunk + disassembler.** Define 10 opcodes; dump them as text. Now you can *see*
   bytecode.
2. **The `switch` dispatch** with `OP_ADD/OP_SUB/OP_LOADK/OP_RETURN`. Run a 5-line
   program. This is the whole engine skeleton.
3. **Jumps + backpatching.** Add `if`/`while`. Now scripts have control flow.
4. **Native function table.** Register `spawn`, `log`, `query`. Now scripts can act.
5. **Sandbox hardening.** Step budget, bounds-checked jumps, ID-based handles, the
   `resetStack` error path. Ship it.

Each step compiles and runs under ASan+UBSan (Lesson 15.2). There is no step 6 — that's
a working scripting layer.

---

## 17.10 Design notes worth stealing

- **Fixed 32-bit instructions, linear chunk, register window per function** — every
  "fast interpreter" from Lua 5.0 to the `0xCP` series to Boa agrees on this shape.
- **Floats only in the initial value model** — `anti` is float/math-heavy; add integer
  ops (`i32`, `u64` for tags/handles) before strings. Strings are a can of worms; your
  `{ptr,len}` C strings + hashed constant pools keep them data, not objects.
- **The opcode table is your API surface.** Every new engine feature you want scriptable
  is a new opcode + a native function. That's the entire design tension, and it's the
  right one.

---

*Next: Lesson 18 — threads and the job system: how `anti` uses the M-series cores
without malloc, races, or dying on hot reload.*