# anti, by Vex, truly.

A radically unconventional framework for high-performance Java game engine development. 

## The Philosophy

"anti" is an absolute rejection of the traditional Java heap. It is a strict, less-or-zero-allocation, less-or-zero-GC architecture designed for maximum throughput and predictable microsecond-level latency. 

I (some) do not use the `new` keyword for data. I do not wrap primitives in objects. I do not box or unbox. I do not allow the Garbage Collector to pause the simulation (yup).

Everything, from our primitives and matrices to our circular spatial grids and network buffers, is packed into off-heap virtual memory using raw 64-bit pointers and the Foreign Function & Memory (FFM) API. The framework manages its own arenas, implements custom lockless free-lists, and manually dictates the memory lifecycle in a C-style paradigm, all while remaining within the JVM.

## Architecture

*   **Zero-GC Off-Heap Collections**: Structs, Lists, Arrays, and Spatial Partitioning Arrays (GridArray, CircularArray) are all allocated natively via `java.lang.foreign`.
*   **Bit-Packed Type System**: Types are globally tracked and accessed through a custom 32-bit integer masking system, allowing for blazing fast polymorphism without object headers.
*   **Raw Memory Dereferencing**: Fields are accessed directly via raw pointer offsets and `VarHandle` atomic operations.
*   **Multi-Threading**: Framework updates happen concurrently via dedicated lockless subsystems, interacting with ABA-tagged volatile memory.

This is Java stripped of its safety nets and rebuilt for raw performance.

## Development & Orchestration

This framework is built using an unconventional AI-orchestrated development process.

I leverage Google **[Antigravity](https://antigravity.google)** as my primary coding agent. However, the architecture is entirely mine. I orchestrate the AI's operations, strictly dictate its boundaries, and rigorously audit all generated code to ensure it flawlessly complies with my zero-allocation standards. The AI acts as a high-speed implementer of my architectural manifesto.

## Building & Dependencies

If you are exploring this codebase, be aware that it relies on a very specific set of native libraries to interface with the operating system and hardware directly. 


### Core Dependencies

*   **[OSHI](https://github.com/oshi/oshi)**: Used for low-level operating system and hardware information retrieval without relying on standard Java wrappers.
*   **[LWJGL 3](https://www.lwjgl.org/)**: The Lightweight Java Game Library. Used for raw native bindings to Vulkan, OpenGL, GLFW, OpenAL, and other C-libraries.

### How to Compile and Test

To compile or test this project, you must include the required library directories in your classpath. 

For manual compilation:
```bash
javac -d out/production/anti -cp "out/production/anti:lib/*:lib/oshi/*:lib/lwjgl-release-3.4.2-custom/*" src/<PreferredClass>.java
```

All empirical verification and runtime testing is done strictly via `scratch/ScratchTest.java` (using Java 26 preview features):
```bash
java --enable-preview --source 26 -cp "out/production/anti:lib/*:lib/oshi/*:lib/lwjgl-release-3.4.2-custom/*" scratch/ScratchTest.java
```
Will be using GraalVM Native Image for native compilation anytime I can get around to it.
