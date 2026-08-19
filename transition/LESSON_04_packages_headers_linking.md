# Lesson 4 — Packages & Classloading vs. Headers & Linking

*Teacher's voice: Java and C answer a deceptively similar question completely
differently: "how do I find the code I'm calling, and how do I keep two files from
colliding?" Java says: packages + classpath + a loader at runtime. C says: headers +
the preprocessor + a linker. This is where your build system's soul lives.*

---

## 4.1 Java's world: packages, classpath, dynamic loading

- Every `.java` file declares a `package`. The package name (`.`-joined) is baked into
  the bytecode as part of the class identity (`oop.TypeRegister`, `thread.SpinLock`).
- `import` is a compile-time convenience: it lets you write `TypeRegister` instead of
  `oop.TypeRegister`. It is purely source-level.
- At runtime, the **classloader** finds classes by name on the *classpath* (directories,
  JARs) — *dynamically, lazily, on first use*. The JVM loads a class when you first
  touch it.
- Consequences: you can swap implementations at runtime, add a JAR without rebuilding,
  and classes can even be loaded over the network. But: "what is actually in this
  process" is only fully known after it starts running.

This is why GraalVM needs the **closed-world assumption** (Lesson 1): a classloader that
can load anything breaks AOT compilation. Native Image restricts loading to what was
reachable at build time, and every class that *might* be reflected-on must be
explicitly registered in `reflect-config.json`. Your `native-image-config/` directory
exists for exactly this reason.

---

## 4.2 C's world: translation units, headers, the preprocessor

C has no packages and no classloader. It has **translation units** (TUs):

```
One .c file (after #include expansion) == one translation unit == one .o object file.

  entity.c ──▶ entity.o
  window.c ──▶ window.o
  main.c   ──▶ main.o
                 │
        entity.o + window.o + main.o + libSystem.dylib + libMoltenVK.dylib
                 │
                 └──▶ ld ──▶ anti (executable)
```

- A `.h` file is a **contract**: declarations (function signatures, struct layouts,
  constants) shared between TUs. A `.c` file has the **implementations**.
- `#include "foo.h"` literally *textually pastes* the header into the file. That's why
  header guards matter:

```c
// window.h
#pragma once                    // "only include me once" — the modern guard
struct Window;                  // forward declaration: "struct exists, you'll get its
                                // full definition from whoever includes the .c"
void window_open(int w, int h);
uintptr_t window_metal_layer(void);
```

```c
// window.c
#include "window.h"             // paste the contract in
#include <AppKit/AppKit.h>      // Apple's headers (also guarded with #pragma once)

struct Window { NSWindow* nswin; CAMetalLayer* layer; };

void window_open(int w, int h) { /* implementation */ }
```

- `#define` is raw text substitution at compile time — C's constant system and its
  sharpest knife. Your `TypeRegister` constants (Lesson 3) map to plain `enum`s or
  `#define`s in C:

```c
enum TypeForm {
    FORM_SINGLETON       = 0x10000000,
    FORM_ARRAY           = 0x20000000,
    FORM_POINTER         = 0x30000000,
    FORM_STRUCT_SINGLETON= 0x40000000,
    FORM_STRUCT_ARRAY    = 0x50000000,
    FORM_STRUCT_POINTER  = 0x60000000,
    FORM_ARRAY_SOA       = 0x70000000,
    FORM_ARRAY_AOS       = 0x80000000,
};
```

> **Java→C mental shift:** a Java package is a *namespace* enforced by the runtime.
> A C header is a *file you paste*. Java's classpath is a runtime lookup. C's linking
> is a build-time resolution. In Java you get errors at runtime ("ClassNotFoundException");
> in C you get errors at link time ("Undefined symbols") or, worse, silently resolved
> wrong.

### Static vs dynamic linking (the names that matter)

- **Static linking** (`.a` archives): the linker copies the object code *into* your
  executable. Your `__TEXT` grows. No runtime dependency — the code is yours.
- **Dynamic linking** (`.dylib` on macOS, `.so` on Linux): the executable records a
  *dependency on the library*; dyld loads it at process start and resolves the symbols.
  Code stays shared on disk (one copy in RAM for all processes using it).
- Your JVM path already does this: LWJGL's `liblwjgl.dylib`, `libMoltenVK.dylib`,
  `libglfw.dylib` are loaded dynamically. `run.sh` points at `natives/`.

`anti` packaging today: JVM path = dylibs + JVM + classpath. GraalVM path = everything
except dylibs folded into one Mach-O (the 22MB you see). Native C path = your code +
`-framework AppKit` + `-framework Metal` + Vulkan/MoltenVK dylibs, all of which macOS
and your SDK already provide.

---

## 4.3 Symbol naming: C's flat, stable namespace

A symbol is the linker's dictionary key for a function. The linker sees a *flat*
namespace — one name per function, and in C, **the function's source name IS its
symbol.** No decoration, no renaming, no namespace surgery.

```c
// C: the function's name IS its symbol. Beautiful, boring, stable.
int add(int a, int b);
// symbol: _add  (macOS prefixes everything with _)
uint32_t anti_engine_frame(uint64_t state);
// symbol: _anti_engine_frame
```

Why this matters for `anti` specifically:

1. **ABI stability.** A C function's symbol is its name, forever. Export it from a
   dylib once and it stays callable across rebuilds — which is exactly what hot
   reloading (Lesson 16) and the scripting VM (Lesson 17) depend on: you `dlsym` the
   name, not a fragile mangled key.
2. **No bridge ceremony.** When the engine exposes a function to the `.m` AppKit shim,
   to a script, or to a tool, the symbol and the source name are the same string.
   There is no `extern "C"` because there is no other "C" to convert from.
3. **Clean crash symbols.** In LLDB and crash logs you read `anti_pool_acquire`, not a
   compiler-generated soup of decorated identifiers.

---

## 4.4 How this reshapes `anti`'s source tree

Today:

```
src/oop/TypeRegister.java      package oop;  class TypeRegister
src/thread/SpinLock.java       package thread;
src/window/macOSWindow.java    package window;
```

A C port keeps the exact same *shape* but the package line becomes a header + prefix:

```
include/anti/oop.h             #pragma once + type masks (your TypeRegister ints)
include/anti/thread.h
include/anti/window.h
src/oop.c
src/thread/spinlock.c
src/window/macos.m            # .m = Objective-C (AppKit) — Lesson 5
```

Note `macOSWindow` becomes `.m`: AppKit is an Objective-C API, and the file you write
changes extension, not philosophy. The *naming prefix* replaces the package: in C you
use `anti_`/`anti_oop_`/`anti_thread_` prefixes because there are no namespaces.

> **Key realization:** your packages are already effectively "directories + prefixes".
> `oop.TypeRegister`, `thread.SpinLock`, `spatial.GridArray` port to
> `anti/oop.h`, `anti/thread.h`, `anti/spatial.h` with zero loss of meaning. The
> folder structure *is* the package system; C just makes you carry the prefix
> yourself instead of the runtime doing it for you.

### Actionable takeaway

Take `TypeRegister.java` and `Struct.java`. Rewrite the constants/classes as a single
`anti_oop.h` header (types, masks, `enum`, struct layout macros) and a `src/oop.c` with
the runtime layout engine. If the header is one page and the `.c` is a familiar loop,
you've discovered that *a third of your classloading/package machinery disappears when
there is no classloader* — the compiler and linker replace it.

---
*Next: Lesson 5 — Functions, Calling Conventions & System APIs.*