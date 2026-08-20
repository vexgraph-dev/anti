# Contributions & Engineering Manifest

This project is a strictly solo development process (for now, not announcing any time soon).

It serves as a personal architectural manifesto of my own philosophies for what it is to make a C-based application regarding low-level systems, and a highly specific workspace tailored exactly to my preferences, workflows, and standards, within my reach and my research.

Because of the rigid, self-describing and relational nature of the codebase — everything is a pointer, every block knows its own type, and the engine is written against my exact style rules — contributions introduce unnecessary friction.

Therefore, **I do not accept any Pull Requests, feature requests, or external contributions of any kind. As of now.. (so yet, yes, yet.)**

Feel free to fork the code however or use it as inspiration for your own zero-allocation experimentation, but this upstream repository will remain exclusively managed by its sole author.

---

## 1. Architectural Principles

| Principle | Specification |
| :--- |:---|
| **Everything is a pointer** | Symbols resolve to addresses, addresses decode themselves; no wrappers, no handles-to-handles. |
| **Self-describing memory** | Every block carries a bit-packed `type_id` + `length` header; `Memory_type()`/`Memory_length()` are free. |
| **Zero steady-state allocation** | One arena carved from the OS; no `malloc` in ticks, physics, audio, or networking paths. |
| **Lockless subsystems** | ABA-tagged freelists, CAS spinlocks, MPMC rings. No mutexes in the hot path. |
| **Atomic Commit Discipline** | Per-file atomic git commits with explicit scopes (`feat`, `refactor`, `perf`, `chore`, `style`). |

---

## 2. Banned Patterns & Permitted Replacements

| Banned Pattern | Reason for Ban | Permitted Replacement |
|:---|:---|:---|
| Arrow member access (`p->field`) | Hidden indirection, against the doctrine. | `(*p).field` — always explicit. |
| Casts with no space (`(int)p`) | Style rule. | `(int) p` — exactly one space after `)`. |
| `typedef struct _x { } x;` | Non-class naming. | `typedef struct Class { } Class;` — same tag and typedef. |
| Mixed-caps or snake-capital functions | Style rule. | Definition `functionName`, call site `Class_functionName(...)`. |
| Braced single-statement `if` | Style rule. | `if(foo)` newline `(*coo).doo(params);` — no braces. |
| External GUI/windowing libs (GLFW, SDL) | Pulls in C++/extra dependencies, contradicts the native backend. | `window.Window` — AppKit directly via Objective-C shim. |
| Annotation marker written bare (e.g. `DRAFT`) | Style rule. | `;;DRAFT` — two semicolons prefix, nothing after. |

---

## 3. Memory Tier Architecture

| Pool | Slot Size | Managed Types |
| :--- | :--- | :--- |
| **`BitPool`** | parameterized | any equal-stride slot: primitives, structs, ring cells. |
| **`Memory`** | 16B header + payload | self-describing typed blocks (`Memory_alloc`). |
| **`RingBuffer`** | N cells | MPMC inter-thread channel. |
| **`Variable`** | 48B rows | relational symbol registry: name → classId + pointer. |

---

## 4. Local Build & Verification Workflow

| Step | Command | Expected Result |
| :--- | :--- | :--- |
| **1. Configure** | `cmake .. -DCMAKE_BUILD_TYPE=Debug` | configure succeeds. |
| **2. Build (strict)** | `cmake --build .` | all targets, `-Werror`, zero warnings. |
| **3. Engine Demo** | `./anti` | `received=100/100 ticks=N`. |

Commit history is per-file and granular — "this is what I did" — even if
intermediate commits don't compile. Pushing happens only on my explicit say-so.
