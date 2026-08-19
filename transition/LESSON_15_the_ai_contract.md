# Lesson 15 — The AI Agent's Contract (How to Make C Without Making Soup)

*Teacher's voice: You type with an AI. That's the whole premise of `anti`. A C codebase
written by an agent — without rules — becomes memory-leak soup in a weekend, because an
agent is eager, verbose, and confident, and C rewards none of those. This lesson is the
operating manual that turns the agent into a reliable co-writer: the compile loop, the
pre-flight checklist, the audit, and the exact prompt template you paste in for every
new subsystem.*

---

## 15.1 The core principle: verifiable units

The agent must **write the smallest unit that can be compiled and verified, then stack.**
Never a 400-line blob that "looks right." The golden loop, every single time:

```
1. Write one header + one function (or one small set).
2. Compile with the full warning+sanitizer set. Zero warnings, zero errors.
3. Run it under ASan/UBSan with a tiny test.
4. Commit. Move on.
```

If the agent can't compile its own output (e.g. no build in scope), it must say so and
write only code it can prove by inspection — never code it "hopes" works.

---

## 15.2 The compile-first rule (non-negotiable flags)

```sh
clang -std=c11 -Wall -Wextra -Wpedantic -Werror \
      -Wconversion -Wshadow -Wsign-compare -Wimplicit-fallthrough \
      -Wvla -Werror=vla -fstrict-aliasing \
      -fsanitize=address,undefined -g -O2 -DANTI_DEBUG \
      -mcpu=apple-m1 -o anti_debug $(sources)
```

Rules the agent obeys:
- **Never ship a warning.** `-Werror` makes that structural. If a warning appears, the
  agent fixes the *cause* — it never silences with `#pragma` or a cast to shut up.
- `-Wconversion` forces explicit narrowing — every `uint64_t→uint32_t` must be an
  intentional, cast, explained assignment.
- `-Wshadow` forbids shadowing — the agent must rename, not nest.
- Sanitizers **in every debug run**. Release is `-O3 -fomit-frame-pointer` with
  sanitizers off — and nothing else changes.

---

## 15.3 The pre-flight checklist (agent writes this *before* any function)

For each function it is about to write, the agent must state — in the header comment:

1. **What memory does this touch?** (pool slots, arena, caller-owned buffer, static)
2. **Who owns it?** On entry? On every return path?
3. **What are the invariants?** (bounds, tags, alignment, single-thread vs atomic)
4. **What are the explicit non-goals / bans?** (no alloc, no printf, no recursion)
5. **What is the failure contract?** (error code returned, or `longjmp`, or panic)

If the comment can't be written, the function isn't understood, and it must not be
written yet. **The comment is not documentation; it is the plan.**

---

## 15.4 The hot-path rules the agent must obey

- **Zero allocation. Zero syscalls. Zero function pointers through reloaded code**
  (Lesson 16) — unless via the name table.
- Mark non-aliasing inputs `restrict` (Lesson 12.3). If the agent can't prove
  non-aliasing, it must document that the compiler will be conservative.
- Loop bounds: explicit `size_t`/`i32` counts, checked against real lengths. No
  `while(p)`, no "until NULL" scans, no unbounded recursion.
- Prefer `static inline` + value structs for math. Return small structs by value, not
  out-params, unless profiling says otherwise.
- Keep the loop body branch-light so the vectorizer (and NEON intrinsics, Lesson 14.7)
  can chew it.
- `goto cleanup` single-exit error paths, never mid-function `return`s after resources
  are claimed.

---

## 15.5 The leak-soup audit loop (run after every feature, before "done")

```
1. grep the diff for malloc/calloc/realloc/free → must only be in memory.c
2. grep for pool_acquire → pair with pool_release on EVERY path (incl. error paths)
3. compile + run under ASan/UBSan (detect_leaks=1)
4. run the pool stress test (random acquire/release interleave)
5. run one scene transition + one hot reload under ASan
6. read the diff aloud as an ownership audit: "who frees this? when? twice?"
```

If any step fails or is skipped, the feature is not done.

---

## 15.6 The no-clever rule

- Write the **boring version first**. C is read 20× more than it's written.
- Cleverness is allowed only where it *buys* something measured (Lesson 14: bit tricks,
  SIMD), and only with an invariant comment explaining why.
- No "clever" single-expression macros that hide control flow. No compound assignments
  buried in conditions. No `i = ++i + i++`-class nonsense — it's UB, the optimizer may
  do anything.
- When a clever hack is genuinely needed, it gets: a `// rationale:` comment, a
  `_Static_assert`, and a test. That's the tax on cleverness.

---

## 15.7 Self-review the way a reviewer would

Before declaring a function done, the agent reads its own diff and answers:

1. Does every `pool_acquire`/`pool_release` pair survive every return path?
2. Are all bounds checked against real array lengths, not trusted headers?
3. Are all casts explained? Is all narrowing intentional?
4. Is every pointer parameter `const`/`restrict` honestly annotated?
5. Is every invariant in the pre-flight comment actually enforced in code?

If "I don't know" is the honest answer to any — the agent must say "I don't know" out
loud, rather than asserting correctness. **Certainty with no proof is the soup-maker.**

---

## 15.8 The reusable task prompt template (paste for every subsystem)

```
Write the <SUBSYSTEM> for anti, following the house rules:

RULES:
- C11, compile with the flags in Lesson 15.2 (-Werror). No warnings, ever.
- ZERO allocation after init. No malloc/calloc/realloc/free outside memory.c.
- No printf-family in engine code. No rand(). No VLAs. No recursion.
- Fixed-width types (uint32_t etc.) for all engine data. Explicit casts for narrowing.
- Pad-order structs big-to-small. _Static_assert exact layouts.
- const/restrict on every pointer honestly. static on file-private functions.
- Naming: subsystem_verb_noun(), s_/g_ prefixes, SCREAMING_SNAKE constants.
- Member access: prefer `(*p).field` (explicit dereference); `->` only when it reads
  clearly (house style, Lesson 12).
- Boring code first. Invariant comments above pools/arenas. No cleverness without rationale.
- Every acquire pairs with a release on every path. Error paths via goto cleanup.

DEFINE BEFORE WRITING:
- What memory this touches, who owns it, the invariants, the failure contract.

PLAN (write this first, wait for approval):
1. Header + types + _Static_asserts.
2. Init (one-time allocations only).
3. Core API (each function compiled+tested before the next).

VERIFY (do all, report results):
- clang -Wall -Wextra -Werror ... && run under ASan/UBSan.
- Pool/arena pairing grep. Stress test. Scene-transition + hot-reload ASan run.

DO NOT: skip verification, silence warnings, or claim something works without a test run.
```

---

## 15.9 Definition of done (the agent cannot mark complete until)

- [ ] Compiles clean under the full flag set, `-Werror`.
- [ ] Runs under ASan+UBSan with zero reports.
- [ ] Ownership audit passes (every acquire released, no double free, no UAF).
- [ ] Invariant comments present and true.
- [ ] Naming/braces/bans conform to Lessons 11–12.
- [ ] One small test exercised the new code paths.

When the agent reports "done," these six boxes are the claim. If it can't tick one, the
correct answer is "not done, blocked on X," not "done."

---

*Now the engine lessons begin. Next: Lesson 16 — hot-swapping code and objects at
runtime, and how to call reloaded code without crashing.*