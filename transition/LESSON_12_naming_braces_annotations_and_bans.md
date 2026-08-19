# Lesson 12 — Naming, Braces, Annotations, and the Banned List

*Teacher's voice: The compiler doesn't care about any of this. You and your AI do. A solo
engine becomes a swamp exactly when style is inconsistent — because then neither of you can
tell what a line is allowed to do. These rules are the house rules. Enforce them with
clang warnings and with the AI contract in Lesson 15.*

---

## 12.1 Naming conventions (anti's house rules)

The point of a convention is that the **name tells you the lifetime and scope** without
reading the code.

| Kind | Style | Example |
|---|---|---|
| Types (struct/enum/typedef) | `PascalCase` or `snake_t` — pick one, use `_t` suffix | `EntityPool`, `Vector3`, `pool_t` |
| Functions | `module_verb_noun()` | `pool_acquire()`, `vec3_dot()`, `bit32_claim()` |
| File-local (static) globals | `s_` prefix | `s_active_pool` |
| Module-level globals (rare) | `g_` prefix | `g_main_memory` |
| Constants / macros | `SCREAMING_SNAKE` | `MAX_POOLS`, `FORM_MASK` |
| Local variables | `snake_case`, short | `n`, `idx`, `len`, `head` |
| Struct fields | `snake_case` | `type_id`, `ref_count` |

Rules:
- **Prefix by subsystem**: `pool_`, `bit32_`, `spin_`, `vec3_`, `vm_`, `script_`, `hr_`
  (hot reload). This is C's namespace — a function is `bit32_acquire`, never `acquire`.
- Every integer local carries an **implicit width suffix in your head**: `i32 len`,
  `u64 tag`, `size_t n`. The name should make the width obvious (`n` for count,
  `idx` for index, `ptr` for address).
- Booleans read as questions: `is_valid`, `has_payload`, `did_reload`.

---

## 12.2 Braces (pick one and never flinch)

C tradition is **K&R / 1TBS** (opening brace on the same line). It's what clang-format,
Lua, JS, and most C engines do. `anti` uses K&R:

```c
static void pool_free(Pool *p, uint32_t slot) {
    Slot *s = &p->slots[slot];
    s->next = p->free_head;      // push onto freelist
    p->free_head = slot;
}
```

### Member access style — the house preference

`->` is sugar for `(*p).` — identical semantics. The house prefers the **fully spelled
form** `(*p).field` over the arrow. It reads closer to "dereference this pointer, then
get the field," which is what's actually happening, and it's what `anti`'s Java
(`Unsafe`-style `ptr.field`) already felt like.

```c
// preferred: (*p).field — explicit dereference, then field access
(*s).next = (*p).free_head;
(*p).free_head = slot;

// acceptable when it's clearer, but not the default: p->field
```

Both are legal and identical bytes. `->` is not banned — it's the *secondary* style.
When a line reads better as `(*p).field`, use it without guilt; the rule exists so the
codebase reads consistently, not to police you.

Rules:
- Braces on **every** compound statement, even one-liners. `if (x) y();` invites the
  dangling-else trap.
- `else` on the same line as the closing brace: `} else {`.
- `switch` cases: indent the `case` at the brace level, indent the body once more.
- Multi-line conditions: operator goes at the **end** of the line, not the start —
  clang's `-Wmisleading-indentation` and your own eyes will thank you.
- Empty body: write `{}` with a comment, or `while (n--) {}` — never a bare `;`.

---

## 12.3 Annotations (the vocabulary of "what I promise")

These are how a C function *declares its contracts* at the type level. Your AI must use
them; they're how clang can *verify* claims instead of you eyeballing them.

| Annotation | Meaning | anti use |
|---|---|---|
| `const` param | I won't mutate what you point at | all read-only APIs |
| `restrict` param | this pointer doesn't alias any other param | `vec3_add(const float *restrict a, const float *restrict b, float *restrict out)` — enables vectorization |
| `static` fn | private to this file | everything not in a header |
| `static inline` | copy me into each TU, no call overhead | math in headers |
| `_Noreturn` / `[[noreturn]]` | I never return | `anti_panic()`, `longjmp` wrappers, `exit` |
| `__attribute__((pure))` | no side effects, result depends only on args | `hash32(u32)` |
| `__attribute__((const))` | like pure + no pointer deref | `next_pow2(u32)` |
| `__attribute__((unused))` | silence unused warnings (debug-only fns) | asserts |
| `_Alignas(16)` | force alignment | SIMD buffers, pool slots |
| `_Static_assert(expr, msg)` | compile-time check | header layout, `sizeof(Header) == 8` |

Example — a pool acquire with full contract:

```c
// Returns an index into p->slots, or POOL_NULL if exhausted.
// The slot is exclusively owned by the caller until pool_release().
static uint32_t pool_acquire(Pool *p) {
    uint32_t slot = p->free_head;
    if (slot == POOL_NULL) return POOL_NULL;
    p->free_head = p->slots[slot].next;
    return slot;
}
```

**The `restrict` rule:** if two pointer params may overlap, the compiler must assume the
worst — no vectorization, no caching in registers. Mark non-overlapping inputs
`restrict`; it's the single biggest free performance win in C.

---

## 12.4 Commenting doctrine (say *why*, never *what*)

```c
// WRONG — describes the code, adds nothing:
i++; // increment i

// RIGHT — explains a non-obvious invariant:
// free_head is an ABA-tagged index (tag in high 16 bits). The tag must be
// re-read AFTER the CAS, not before, or a concurrent release breaks the pool.
```

Rules:
- One block comment per file at top: what this module owns, what memory it may touch,
  and who calls whom.
- An **invariant comment above every pool/arena/freelist**: allocation rules, ownership,
  thread-safety. This is the contract your AI reads to avoid soup.
- `// rationale:` comments when the code looks wrong on purpose (e.g. a specific
  instruction sequence, a byte layout).
- `// FIXME:`, `// TODO:`, `// HACK:` are allowed and searchable.
- **No lies.** If behavior changes, the comment changes in the same commit. A wrong
  comment is worse than none — it actively teaches the AI the wrong invariant.

---

## 12.5 The banned list (things `anti` does not do)

These are bans your AI *must* enforce. The Lesson 15 prompt template carries them
verbatim.

**Libraries / calls banned:**
- `malloc`/`calloc`/`realloc`/`free` — **except** inside `memory.c`'s arena/pool creation
  at init time. Nowhere in the frame loop, nowhere in the hot path.
- `printf`/`fprintf`/`sprintf` family in engine core — replaced by `anti_log()` that
  writes to a ring buffer (never allocates, never blocks).
- `strcpy`/`strcat`/`strlen` — use bounded helpers or your own length-carrying strings.
  `anti` strings are `{char *ptr; size_t len;}` — no NUL scan needed.
- `rand()`/`random()` — write a fixed PRNG (Lesson 14) for determinism.
- `<setjmp.h>` — *allowed only* in the script/VM error boundary (Lesson 19), never in
  the engine core.
- `<threads.h>` `thrd_*` and pthread's malloc-laden paths — you have your own spinlocks
  and atomic primitives (Lesson 7).
- VLAs (`int a[n];`) — banned outright. `-Werror=vla` in the build.
- ObjC `@try/@catch` — you're C; the `.m` shim must not throw.
- `errno` — return error codes instead (Lesson 19).

**Constructs banned:**
- Allocation inside a loop of any kind.
- Pointer casts to unrelated struct types without a comment (type-punning is allowed only
  via `union` or explicit casts with a stated reason).
- Mixing `signed`/`unsigned` in comparisons without deliberate intent (clang
  `-Wsign-compare`).
- Implicit narrowing — `uint64_t` into `uint32_t`. `-Wconversion` catches these.
- Function pointers stored across a hot reload boundary *unless* they go through the
  resolve-by-name table (Lesson 16).
- Recursion on unbounded data (a deep scene graph) — stack overflow is a crash, not an
  error. Iterate, or bound the depth.
- Magic numbers in hot paths — name them. Bit masks get `_MASK`/`_SHIFT` constants.

---

## 12.6 The "annotation audit" (5-minute check before you call a function done)

1. Every pointer parameter is `const`, `restrict`, or neither *on purpose*?
2. Every non-inlined function is `static` unless it needs to be exported?
3. Every loop condition is a bounded `size_t`/`i32` count, and the bounds are checked
   against the actual array length?
4. Every struct initialization uses `{0}` or explicit fields (no garbage bits)?
5. Every public API has a one-line contract comment (who owns what)?

If any answer is "no", stop and fix before moving on. This is the loop the AI runs in
Lesson 15.

---

*Next: Lesson 13 — memory. The zero-allocation doctrine and how `anti` never leaks.*