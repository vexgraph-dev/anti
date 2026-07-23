# anti

A radically unconventional framework for high-performance Java game engine development. 

## The Philosophy

"anti" is an absolute rejection of the traditional Java heap. It is a strict, zero-allocation, zero-GC architecture designed for maximum throughput and predictable microsecond-level latency. 

We do not use the `new` keyword for data. We do not wrap primitives in objects. We do not box or unbox. We do not allow the Garbage Collector to pause the simulation.

Everything—from our primitives and matrices to our circular spatial grids and network buffers—is packed into off-heap virtual memory using raw 64-bit pointers and the Foreign Function & Memory (FFM) API. The framework manages its own arenas, implements custom lockless free-lists, and manually dictates the memory lifecycle in a C-style paradigm, all while remaining within the JVM.

## Architecture

*   **Zero-GC Off-Heap Collections**: Structs, Lists, Arrays, and Spatial Partitioning Arrays (GridArray, CircularArray) are all allocated natively via `java.lang.foreign`.
*   **Bit-Packed Type System**: Types are globally tracked and accessed through a custom 32-bit integer masking system, allowing for blazing fast polymorphism without object headers.
*   **Raw Memory Dereferencing**: Fields are accessed directly via raw pointer offsets and `VarHandle` atomic operations.
*   **Multi-Threading**: Framework updates happen concurrently via dedicated lockless sub-systems (e.g., DrawThread, ScriptingThread, NetworkingThread) interacting with ABA-tagged volatile memory.

This is Java stripped of its safety nets and rebuilt for raw performance.
