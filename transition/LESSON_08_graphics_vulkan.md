# Lesson 8 — Graphics Pipelines (Vulkan & GPU Buffers)

*Teacher's voice: Graphics is the one subsystem where your Java path is *already*
almost-native. LWJGL's Vulkan bindings are about as thin as a binding layer gets. So
this lesson is honest about what you gain and what you don't. The headline: moving to
C removes the JNI/FFM call tax and the wrapper indirection, but your architecture —
staging buffers, command buffers, descriptor sets, the draw/present split — transfers
unchanged.*

---

## 8.1 LWJGL bindings vs `<vulkan/vulkan.h>`

### What LWJGL actually is

LWJGL 3's Vulkan bindings are **generated from the Vulkan registry XML** — a thin,
hand-tuned JNI layer that passes primitive pointers directly (no boxing, no
object-wrapping, no `GetDirectBufferAddress` dance). The LWJGL maintainer's own
statement: Vulkan calls go through near-minimal JNI; the bindings are designed so hot
paths can run allocation-free with escape analysis + scalar replacement.

But "thin JNI" still means every `vk*` call you make crosses from Java managed code
into the native stub. And there's a subtlety: **Vulkan is the most verbose graphics
API in existence.** A frame issues hundreds of API calls (`vkAcquireNextImageKHR`,
`vkCmdBeginRenderPass`, `vkCmdDrawIndexed`, `vkCmdEndRenderPass`,
`vkQueueSubmit`, `vkQueuePresentKHR`...). Each is a foreign call.

### What direct Vulkan gives you

```c
// C — the same call, direct. vulkan.h is just structs + function pointers.
VkResult r = vkQueuePresentKHR(queue, &present_info);   // one indirect call
```

- No JVM transition. No stub. On arm64 it's a `ldr` for the function pointer plus a
  `blr` (branch-link-register) into the driver/loader.
- **Full control of dispatch.** Vulkan functions go through a *loader* by default;
  production native engines use `vkGetDeviceProcAddr` to grab direct driver entry
  points and cache them (volk is the standard library for this), shaving 1–5% of CPU
  in typical apps. In Java you cannot take that fast path — LWJGL hides the loader.
- No `MemorySegment` marshalling for struct-by-value (`VkPresentInfoKHR` etc. — the
  exact path that cost 46ns→8ns in Lesson 5; in C it's a struct in memory, zero
  adaptation).

### The honest accounting

| Cost | Java + LWJGL | Native C |
|---|---|---|
| Per-call overhead | ~3–9ns FFI/JNI + segment checks | ~1ns + loader hop (or ~0.5ns w/ volk) |
| Struct passing | FFM adapts layouts, maybe allocs | memory already in the right shape |
| Struct layout | hand-written `MemoryLayout` (must match C!) | the compiler knows `vulkan.h` |
| Device dispatch | fixed by LWJGL | `vkGetDeviceProcAddr` + volk (1–5%) |
| Error/debug layer | validation layers (same both ways) | same |

**The real win is not the nanoseconds per call.** It's that the *entire* engine — memory
pools, mesh buffers, scene graph, command building — lives natively with zero boundary.
Your `anti` architecture already treats Java as "C with a runtime"; graphics is just the
subsystem where the runtime tax is most visible (every frame, hundreds of times).

---

## 8.2 GPU memory allocation, staging buffers, command buffers, descriptor sets

Your existing pipeline concepts — they port 1:1, only the spelling changes. Side by
side for the canonical "upload vertex data" flow:

**Java (LWJGL):**
```java
// 1. Create buffer (VkBufferCreateInfo struct in native memory)
VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack);
createInfo.set(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
          .setSize(size).setUsage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
          .setSharingMode(VK_SHARING_MODE_EXCLUSIVE);
vkCreateBuffer(device, createInfo, null, buffer);

// 2. Allocate memory, bind (VkMemoryAllocateInfo)
vkAllocateMemory(device, allocInfo, null, bufferMemory);
vkBindBufferMemory(device, buffer, bufferMemory, 0);

// 3. Stage: map, memcpy, flush (staging buffer → vkCmdCopyBuffer in a command buffer)
PointerBuffer data = stack.malloc(4 * vertexCount);
vkMapMemory(device, stagingMemory, 0, size, 0, data);
data.put(...); vkUnmapMemory(device, stagingMemory);
```

**C (vulkan.h):**
```c
// 1.
VkBufferCreateInfo ci = { .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
                          .size = size,
                          .usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                          .sharingMode = VK_SHARING_MODE_EXCLUSIVE };
vkCreateBuffer(dev, &ci, NULL, &buffer);
// 2.
vkAllocateMemory(dev, &ai, NULL, &bufferMem);
vkBindBufferMemory(dev, buffer, bufferMem, 0);
// 3. memcpy into the mapped staging region — plain C pointer, no wrapper
memcpy(staging, vertices, size);
```

Structural differences you must internalize:
1. **Structs live on your stack/heap in C** (`VkBufferCreateInfo ci = {...}`) — no
   `MemoryStack.calloc`, no `.set()`, no lifetime juggling with a `stack` arena. The
   struct *is* a struct. This eliminates an entire class of FFM bookkeeping.
2. **Handles are pointers/uint64 in C** (`VkBuffer`, `VkCommandBuffer`) — in Java they
   are objects with a native handle inside, or bare `long`s you pass around. Same bits,
   less ceremony.
3. **Descriptor sets** are the same: create layout (compile-time-ish), allocate pool,
   write sets with `vkUpdateDescriptorSets` pointing at your buffers/views. In C the
   `VkDescriptorBufferInfo` is a struct you fill; in Java a MemorySegment you fill. No
   design change.

### GPU memory management

You already use (or should use) a GPU allocator concept — subdividing a few large
`VkDeviceMemory` blocks into suballocations. In C you link the well-known
**VMA (Vulkan Memory Allocator)** — a single `.h`/`.c` you drop in, or write your own
buddy/pool on top of `vkAllocateMemory`. In Java you have LWJGL's `vma` module. Same
algorithm; the C one is faster because it's in-process and you control allocation of
the *host* side too.

> **Key architecture note:** `anti`'s staging-buffer pattern (CPU write → staging →
> `vkCmdCopyBuffer` → VRAM) exists because *host-visible memory* on Apple Silicon
> (via MoltenVK) is not the fastest. This is a GPU-API fact, identical in C. Nothing
> about the draw/present split, the swapchain modes you toggle (FIFO/IMMEDIATE), or the
> decoupled draw thread changes.

---

## 8.3 SPIR-V shader loading & pipeline state

The flow is identical; only the file-read and struct code changes:

```c
// C — read the compiled SPIR-V, hand it to the driver
size_t n; char* spirv = read_whole_file("shader.vert.spv", &n);
VkShaderModuleCreateInfo smc = {
    .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
    .codeSize = n,
    .pCode = (const uint32_t*)spirv,
};
VkShaderModule module;
vkCreateShaderModule(dev, &smc, NULL, &module);
```

- Java path: `Files.readAllBytes` → LWJGL `ByteBuffer` → same `vkCreateShaderModule`.
- Pipeline creation (`VkGraphicsPipelineCreateInfo`): the giant struct of structs
  (shader stages, vertex input, rasterization, blend, depth, render pass, layout).
  In C it's one big `=`-initialized struct; in Java it's hundreds of `.set()` calls
  against `MemoryStack`. **This is the single most tedious LWJGL-to-C conversion** —
  but it is *mechanical*: every `.set()` maps to a struct field assignment.
- Compile SPIR-V from GLSL/HLSL at build time with `glslangValidator`/`glslc`
  (or at runtime). Same tools, same `.spv` files, no difference at all.

---

## 8.4 MoltenVK on Apple Silicon (the thing that doesn't change)

On macOS you do **not** talk to a real Vulkan driver. You talk to **MoltenVK**, a
Vulkan-to-Metal translation layer (`libMoltenVK.dylib`). Your `CAMetalLayer` hosting in
`macOSWindow.java` exists because MoltenVK needs a Metal layer as the swapchain
surface.

This is the same in native code:

- You still create a `CAMetalLayer`, still hand it to `vkCreateSwapchainKHR` via
  `VkMetalSurfaceCreateInfoEXT`, still get `VkImage`s that are really Metal textures.
- You still must run `-XstartOnFirstThread`-equivalent care: AppKit wants the *main
  thread* for the window/event pump; your engine's render thread is a worker. In C this
  is natural (main thread = your pump thread).
- MoltenVK's quirks (drawable sizing during live-resize, `allowsNextDrawableTimeout`,
  the CAMetalLayer gravity/autoresizing masks you set in `macOSWindow.java`) are
  **identical in C** — they're MoltenVK/AppKit behavior, not Java behavior.

### The `.m` shim does the real integration work

Your `macOSWindow.java` `createSurface()` — `CAMetalLayer`, gravity `kCAGravityTopLeft`,
backingScaleFactor, autoresizing masks, `NSViewLayerContentsPlacementTopLeft` — becomes
a ~40-line Objective-C function `macos_create_metal_layer(NSWindow*)` (Lesson 5), and
the Vulkan side consumes its returned `CAMetalLayer*` through `VkMetalSurfaceCreateInfoEXT`.

---

## 8.5 What you actually gain, ranked

1. **The draw loop runs native.** No `vkQueuePresentKHR` downcall at 60–120fps, no
   FFI check on `vkAcquireNextImageKHR` — but far more importantly, all the *engine*
   code feeding it (mesh batching, light culling, descriptor writes) is native too.
2. **Struct handling collapses.** `VkPresentInfoKHR` and friends go from
   `MemoryStack.calloc + .set` rituals to plain struct literals.
3. **Dispatch control** via `vkGetDeviceProcAddr`/volk (the 1–5% CPU win native
   engines take for free).
4. **No layout mismatch risk.** Your hand-written `MemoryLayout`s against
   `vulkan.h` structs can silently disagree; native code can't — the compiler reads
   the same header.

### What you do NOT gain (be honest about it)

- LWJGL is already excellent at removing JNI overhead. Going native is not "un-breaking"
  a broken binding — it's removing the last ~2–4ns/call and the FFI wall around the
  whole engine.
- You still use MoltenVK + CAMetalLayer. The GPU work is identical.
- SPIR-V, validation layers, swapchain modes, fences, semaphores — all unchanged
  concepts.

### Actionable takeaway

Pick one Vulkan struct you create per frame (e.g. `VkPresentInfoKHR`) and write both
versions side by side: the LWJGL `MemoryStack.calloc(...).set(...)` ritual and the C
`VkPresentInfoKHR pi = { .sType = ..., .swapchainCount = 1, ... }`. The C version is
shorter, zero-alloc, and impossible to mismatch. Multiply by ~200 structs in the
Vulkan API and you'll see exactly where the migration effort (and the payoff) lives:
it's all boilerplate removal.

---
*Next: Lesson 9 — Red Herrings, Traps & Mental Models.*