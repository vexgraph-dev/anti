# Contributions & Engineering Manifest

This project is a strictly solo development process (for now, not announcing any time soon). 

It serves as a personal architectural manifesto of my own philosophies for what it is to make a Java-based application regarding low-level systems, and a highly specific workspace tailored exactly to my preferences, workflows, and standards, within my reach and my research.

Because of the rigid, boilerplate-heavy and unconventional nature of the codebase, relying heavily on raw memory manipulation and extreme zero-allocation constraints—outside, while allowing memory-safe functions, contributions introduce unnecessary friction.

Therefore, **I do not accept any Pull Requests, feature requests, or external contributions of any kind. as of now.. (so yet, yes, yet.)**

Feel free to fork the code however or use it as inspiration for your own zero-GC experimentation, but this upstream repository will remain exclusively managed by its sole author.

---

## 1. Architectural Principles

| Principle | Specification                                                                                     |
| :--- |:--------------------------------------------------------------------------------------------------|
| **Zero Java Heap in Runtime** | No `new Object()` inside render loops, tick updates, physics, audio, or networking.               |
| **Foreign Memory Bridge (FFM)** | Primitives, structs, arrays, matrices, strings (runtime), etc. live off-heap via raw pointers.    |
| **Epsilon (No-Op) GC Target** | Compiled with `--gc=epsilon`. The engine does not rely on garbage collector sweeps or finalizers. |
| **Atomic Commit Discipline** | Per-file atomic git commits with explicit scopes (`feat`, `refactor`, `perf`, `chore`, `style`).  |

---

## 2. Banned API Denylist & Permitted Replacements

| Banned API / Pattern                                                      | Reason for Ban                                                                                                                  | Permitted Replacement                                                                                                         |
|:--------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------|
| ANYTHING from `java.util.*` | It makes it import everything, just like `Calendar`, `Format`, etc. | Make its own utility.                                                                                                         |
| `String.format(...)`<br>`System.out.printf(...)`                          | Pulls `java.util.Formatter`, `java.text`, `Calendar`, and CLDR localization bundles into SubstrateVM image heap (+500KB bloat). | Fast zero-allocation formatters (e.g. `DrawThread.fmt1`/`fmt2`), direct bitwise math, or `Character.forDigit`.                |
| `System.out.println(...)`<br>`System.err.println(...)` *(in engine core)* | Allocates heap strings, triggers synchronized `PrintStream` lock contention.                                                    | `cli.Console.log(...)` (direct POSIX stdout fd 1 FFM downcall) or off-heap `thread.RingBuffer`.                               |
| `javax.crypto.*`<br>`java.security.*`                                     | Forces SunJCE providers, RSA, DES, and KeyStore tables into native binary.                                                      | Pure off-heap bitwise algorithms in `security.HashVariable` (SHA-256/512/HMAC) & `security.SecurePacket` (RFC 8439 ChaCha20). |
| Standard JVM Collections<br>(`ArrayList`, `HashMap`, `HashSet`)           | Heap-allocated nodes with unpredictable GC pauses.                                                                              | Off-heap lock-free collections: `struct.List`, `struct.Map`, `struct.Set`, `struct.Deque`, `struct.Stack`.                    |
| Scattered `VarHandle` lookups                                             | Creates individual reflection tokens and dynamic type hubs in GraalVM.                                                          | Unified static memory accessors via `nio.ForeignMemory` (`getVolatile*`, `setVolatile*`, `compareAndSet*`).                   |
| External hardware libs (`oshi.*`, SLF4J)                                  | Pulls logging frameworks and JNA into AOT image.                                                                                | Standard `OperatingSystemMXBean` and POSIX `sysctlbyname` FFM downcalls in `system.SystemDiscovery`.                          |
| Any sort of "" (quotations) in all of the codebase | That creates a single `java.lang.String` object per "quote" which is heap bloat | Use the string register (`StringLookup.ini`) to store the desire strings.

---

## 3. Off-Heap Memory Tier Architecture

| Allocator Pool | Slot Size | Managed Types & Structs |
| :--- | :--- | :--- |
| **`bit.Bit8`** | 1 byte | `primitive.Byte`, `primitive.Bool`, `buffers.Buffer` |
| **`bit.Bit16`** | 2 bytes | `primitive.Short`, `primitive.Brain` (bfloat16) |
| **`bit.Bit32`** | 4 bytes | `primitive.Int`, `primitive.Float`, `primitive.Fixed32` (q16.16) |
| **`bit.Bit64`** | 8 bytes | `primitive.Long`, `primitive.Double`, `primitive.Fixed64` (q32.32), Vulkan Handles, UI `darling.*`, `objects.*`, `audio.*` |
| **`bit.Bit128`** | 16 bytes | `primitive.IntFloat`, `primitive.IntDouble`, `primitive.LongFloat`, `primitive.LongDouble` |
| **`primitive.string`** | 64B / 256B / 1024B | Off-heap UTF-8 variable length string slots with lock-free ABA-tagged free-lists. |

---

## 4. Local Build & Verification Workflow

| Step | Command | Expected Result |
| :--- | :--- | :--- |
| **1. GraalVM Denylist Linter** | `bash scratch/scripts/graal_check.sh --root src` | `0 error(s)` |
| **2. Engine Regression Suite** | `java ... ScratchTest` | `ALL TESTS PASSED SUCCESSFULLY!` |
| **3. Native Mach-O Image Build** | `bash scratch/scripts/build_native.sh` | Production `.app` bundle at `out/dist/AntiEngine-Native.app` |
