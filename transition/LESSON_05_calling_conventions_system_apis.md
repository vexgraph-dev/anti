# Lesson 5 — Functions, Calling Conventions & System APIs

*Teacher's voice: This lesson is the one that makes your `macOSWindow.java` make sense.
You wrote 25+ `MethodHandle` stubs to call AppKit. A C programmer writes one line and
walks away. Let's see exactly why — and what the gap costs you.*

---

## 5.1 Why `anti`'s `macOSWindow.java` needs 25+ `MethodHandle` stubs

Look at your own code (`src/window/macOSWindow.java`). The static block is a *catalog*
of downcall handles:

```java
private static MethodHandle MSG_SEND_PTR;          // (id,sel) -> id  (1 arg)
private static MethodHandle MSG_SEND_PTR_PTR;      // (id,sel,id) -> id  (2 args)
private static MethodHandle MSG_SEND_PTR_LONG;     // (id,sel,long) -> id
private static MethodHandle MSG_SEND_PTR_LONG_PTR; // (id,sel,long,id) -> id
private static MethodHandle MSG_SEND_PTR_SIZE;     // (id,sel,CGSize) -> void
private static MethodHandle MSG_SEND_VOID;         // (id,sel) -> void
// ... and 20 more, each a distinct signature
```

Why so many? Two reasons, and they're both real:

1. **`objc_msgSend` is variadic.** `[window setTitle:x]` compiles to
   `objc_msgSend(window, @selector(setTitle:), x)`. The C function `objc_msgSend`
   takes *any* number of arguments — its actual C type is
   `id objc_msgSend(id, SEL, ...)`. The FFM linker's `downcallHandle` needs an
   **exact `FunctionDescriptor`** describing every parameter and return type so it can
   generate the correct calling-convention adapter. A variadic function has no single
   signature — so you must define *one handle per signature you'll use*. That's why the
   static block is a zoo of `MSG_SEND_*` variants.
2. **Every call is a foreign transition.** Each `.invoke()` crosses from Java
   managed code into the FFI stub, which marshals args per the ABI, calls
   `objc_msgSend`, then marshals the result back.

### What a native Objective-C program does instead

```objc
// window.m — one line per operation, the compiler generates the call
NSWindow* win = [[NSWindow alloc]
    initWithContentRect:rect
    styleMask:NSWindowStyleMaskTitled | NSWindowStyleMaskClosable
    backing:NSBackingStoreBuffered
    defer:NO];
[win setCollectionBehavior:NSWindowCollectionBehaviorFullScreenPrimary];
[win makeKeyAndOrderFront:nil];
```

Each `[receiver selector:arg]` is compiled by clang directly into:

```
bl  objc_msgSend        ; ONE branch instruction
```

There's no "descriptor," no handle table, no marshalling. The compiler knows the
selector's signature from the AppKit header and emits a direct call. **The 25-handle
catalog is a Java-only problem**: the JVM must be told, at runtime, what native
signatures look like, because it refuses to believe any signature until you've
specified it byte-for-byte. Clang already believes AppKit's headers.

---

## 5.2 The actual cost of an FFM downcall (measured, current numbers)

This is not theory; this is the state of the art as of 2025's JDK 25 work, measured on
an M3:

| Call kind | Cost (approx.) | Notes |
|---|---|---|
| Native C function called directly | ~1–2 ns | one `bl` + argument registers |
| FFM downcall, pointer-only args | ~3.3 ns/op | after the JDK 25 intermediate-buffer fix |
| FFM downcall, struct *returned by value* | ~34–46 ns/op (older) → **~6–12 ns** (JDK 25, fixed) | the fix reuses a per-thread scratch buffer instead of `malloc`/`free` per call |
| JNI (old LWJGL path) | ~5–9 ns | primitive-only calls |
| FFM/JNI generic managed-bindings overhead (older JVM, full checks) | ~50–57 ns | bound/unbound segment checks still on |

Critical detail from the JDK 25 patch mail: **returning a struct by value used to
`malloc`+`free` a 32-byte buffer on *every* call** — roughly 80% of the call's overhead
(46ns→8ns when fixed). And the advice it gives applies directly to you:

> *"Now there's an easy way around this for the user by using a different native
> signature: `void g(Vector2D *out) { *out = f(); }` — this eliminates the
> intermediate buffer altogether."*

Translation for `anti`: **write your FFM-bound functions to take/return pointers, never
by-value structs** — `CG_RECT`-returning calls like `MSG_SEND_RECT_RET` are the exact
slow path. And this is a *fundamental* insight: Java's `MemorySegment`-as-value struct
passing is a marshalling problem that doesn't exist in native code, where a struct is
just bytes in registers or memory.

### Multiply by your real workload

A Vulkan frame in `anti` issues *hundreds* of `vk*` downcalls per frame (this is true
in C too — Vulkan is verbose). At 60fps that's tens of thousands of cross-boundary
calls per second. Every call is a stub + check + maybe a thread-state transition. The
measured difference between a 3ns FFM call and a 1ns direct call is small *per call* —
but the difference between "every engine operation crosses a boundary" and "the engine
runs entirely inside native code" is the difference between paying a toll on every
street vs. buying the whole town. LWJGL's own author has said their bindings are
optimized to the bone; the real win of going native is **not** the last 2ns per call —
it's removing the boundary from 100% of your engine, not 3%.

---

## 5.3 Upcalls: the direction that hurts (your CGEventTap)

`macOSWindow.java` also contains an **upcall**: `LINKER.upcallStub(callbackHandle, ...)`
for the `CGEventTap` callback. A native callback (`CFRunLoop` → your code) has to jump
from C into the JVM, wrap a frame, and dispatch to your Java method — historically the
most expensive FFI direction (the LWJGL/JNR folks describe upcalls as "horrible").

In native code, the callback is *already a function you wrote*:

```c
// C: the OS calls THIS function directly. No stub, no trampoline, no JVM.
static CGEventRef on_event_tap(CFMachPortRef port, CGEventType type,
                               CGEventRef event, void* userInfo) {
    // decode keycode, write into your off-heap KeyLog — zero transition
    return event;
}
```

Same for any input callback, Vulkan debug messenger, or Metal drawable handler: in
native code it's a plain function pointer; in Java it's a generated stub with a runtime
frame. **Upcalls are where managed languages pay the steepest toll.**

---

## 5.4 Calling conventions on arm64 (the 60-second version)

The AArch64 ABI says how functions talk:

- Arguments go in registers: `x0..x7` (integers/pointers), `v0..v7` (floats/doubles).
- Return value in `x0`/`v0`. Spilled args go on the stack.
- `x0` doubles as the `self`/`this` for Objective-C methods (that's why `objc_msgSend`
  takes `self` then `SEL` — those are just `x0` and `x1`).
- Callee-saved registers (`x19..x28`) must be preserved across a call; caller-saved
  (`x0..x18`) may be clobbered.

A direct C call compiles to:

```
ldr  x0, [x21]         ; load pointer arg
bl   _vkQueuePresentKHR ; one branch — x0 = device, x1 = queue, x2 = present_info
```

That `bl` is *the entire cost* of a native call (plus whatever the function does).
There is no environment, no safepoint, no descriptor. This is the floor all three
languages aim at; C hits it by default.

---

## 5.5 Bridging C, Objective-C, and Metal cleanly on macOS

You will not write your whole engine in Objective-C — that's a masochism tax. The
industry-standard pattern (GLFW, sokol, wgpu, every serious macOS native engine):

```
 engine/        ← 100% C — portable, no Apple APIs
   ├─ anti_app.h        your portable window/app abstraction
   └─ anti_*.c
 platform/macos/
   ├─ macos_window.m    ← Objective-C: ONLY the AppKit shim
   │                      (create NSWindow, CAMetalLayer, event pump)
   └─ macos_window.c    ← C adapter: exposes anti_app.h surface,
                          calls into the .m via objc_msgSend or a tiny @interface
```

- The `.m` file wraps AppKit behind a C API: `macos_window_create(w,h)`,
  `macos_window_metal_layer(win)`, `macos_window_pump()`. Everything AppKit-specific
  lives *there* and nowhere else.
- The rest of the engine never sees an `NSWindow*` — it sees your C struct.
- Metal presentation: you already present through Vulkan+MoltenVK (it's your current
  pipeline, and MoltenVK translates Vulkan to Metal). That stays exactly as is —
  MoltenVK is a `.dylib` you link, same as the JVM path.
- If you later want pure-Metal (skip Vulkan) for Apple-only, you'd add a
  `macos_metal.c` backend behind the same `anti_app.h` surface. The abstraction
  boundary is the same one `anti` already has between `window.*` and `vulkan.*`.

> **The bridge rule:** portable core in C, platform shims in `.m`, and every engine↔OS
> boundary expressed as plain C functions with a `subsystem_verb_noun()` name. Your
> `macOSWindow.java` becomes `macos_window.m` (~200 lines instead of 800), and the
> 25-handle zoo becomes 25 *lines* of Objective-C.

### Actionable takeaway

Grep `macOSWindow.java` for `MSG_SEND_` and count the distinct signatures. For each
one, write the Objective-C equivalent (`[win center]`, `[win setTitle:s]`, ...).
You'll find roughly one ObjC line per Java handle — and the remaining ~600 lines are
*FFM bookkeeping* (SymbolLookup, FunctionDescriptor, Arena, downcallHandle). That
bookkeeping is 100% Java-specific; it evaporates in native code.

---
*Next: Lesson 6 — The "Template Trap" & Demystifying Binary Bloat.*