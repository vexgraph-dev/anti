<div align="center">
  <h1>🧱 Anti: Off-Heap Primitives</h1>
  <p><i>The foundational memory architecture for a Zero-GC Java Engine</i></p>
</div>

---

## 🚫 The Philosophy: Rejecting the Heap

Modern Java is fast, but the Garbage Collector (GC) is the enemy of real-time simulations and high-performance game engines. Every standard Java object comes with hidden costs:
- **Object Headers:** Every `new Object()` carries 12-16 bytes of invisible header overhead.
- **Pointer Chasing:** Arrays of objects (`Vector3[]`) are not contiguous in memory; they are arrays of pointers pointing to scattered memory locations, completely destroying CPU cache coherency.
- **GC Pauses:** Allocating and dereferencing thousands of objects per frame inevitably triggers "stop-the-world" pauses.

**Anti is built on an absolute rejection of the traditional Java heap.** 
We do not instantiate objects. We manage memory manually using Java's new Foreign Function & Memory (FFM) API. 

## ⚙️ What are Primitives?

In the Anti framework, a Primitive (`Int`, `Long`, `IntFloat`, `Bool`) is **not** a Java object. 

When you allocate a primitive, the engine does not return an object reference; it returns a raw 64-bit `long` **memory address pointer**. All read and write operations are performed statically by passing this pointer back to the subsystem.

```java
// ❌ Traditional Java (Creates Garbage, poor cache locality)
Integer myInt = new Integer(42); 

// ✅ Anti Framework (Zero-GC, Direct native memory)
long ptr = Int.allocateSingleton();
Int.set(ptr, 0, 42); 
Int.free(ptr);
```

## 🧠 Core Concepts

### 1. Compound Primitives (`IntFloat`, `LongDouble`)
In game development, data is often paired (e.g., an Entity ID `int` paired with a Priority Score `float`). Instead of creating a class to hold these, Anti provides Compound Primitives. An `IntFloat` allocates exactly 8 contiguous bytes of native memory (4 bytes for the int, 4 bytes for the float), ensuring maximum data density and perfect cache locality.

### 2. Self-Describing Memory (The 8-Byte Header)
Every dynamically allocated primitive array or singleton in Anti is prepended with an invisible 8-byte header:
* `[ptr - 8 bytes]`: **Type ID** (Bitwise packed `FORM` and `CLASS_ID`)
* `[ptr - 4 bytes]`: **Length** (The capacity of the allocation)

Because of this, systems like `Hash` or `Struct` can mathematically deduce the byte stride and size of *any* pointer in the entire engine without needing reflection or object metadata.

### 3. Bit-Packed Booleans (`Bool.java`)
Standard Java `boolean[]` arrays waste entire bytes (or more) for a single true/false value. Anti's `Bool` primitive tightly packs 64 boolean flags into a single 64-bit `long`, utilizing bitwise shifts for atomic, zero-waste flag querying (crucial for ECS Entity Masks).

### 4. Unaligned Access
To pack massive amounts of data into custom `Struct` layouts without wasting bytes on padding, Anti primitives utilize `ForeignMemory.getLongUnaligned()`. This guarantees that we don't trigger JVM hardware alignment crashes when reading densely packed off-heap arrays.

---

> [!WARNING]  
> **Manual Memory Management Required**
> Because these primitives exist outside the JVM Heap, they are completely invisible to the Garbage Collector. You **must** call `.free(ptr)` when you are done with a pointer, otherwise you will cause a native memory leak.
