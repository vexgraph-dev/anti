# Lesson 20 — Architecture Awareness: x86 vs ARM, Apple Silicon vs Discrete GPUs

*Teacher's voice: You code on Apple Silicon, but someday anti runs on an x86 box with a
big discrete GPU. If your code behaves differently on those two machines, you have a bug
you haven't found yet. This lesson is the "be mindful" list: where the two CPU worlds
differ (memory model!), where Apple Silicon's GPU is genuinely different from a discrete
one (unified memory + tile-based rendering), and what that does to Nanite/Lumen-style
GPU-driven rendering. Most of it costs you nothing — just writing portable C — but a few
decisions (atomics, cache-line padding, ring-buffer discipline) you must get right up
front.*

---

## 20.1 Two CPU worlds: x86 (strong memory) vs ARM (weak memory)

This is the #1 silent killer. It's about the **memory model** — what the hardware is
*allowed* to reorder — not about instruction names.

| | x86-64 (Intel/AMD) | ARM64 (Apple Silicon) |
|---|---|---|
| Memory model | **Strong (TSO, Total Store Order)** | **Weak** — loads and stores can be reordered by the hardware |
| Plain loads/stores | Nearly no reordering | Freely reordered around each other |
| Default `_Atomic` | usually happens to behave ordered | can silently reorder if you don't say otherwise |
| `volatile` | gives *some* ordering behavior (compiler quirks) | **orders nothing.** It is not an atomic, full stop |
| Acquire/release | rarely matters in practice | **required** — this is how you get ordering |

Why you care: your lockless pool and freelist (Lesson 7) are written with C11 atomics.
On x86, sloppy code often "just works" because TSO papers over the mistakes. On ARM, the
same code can produce corrupted linked lists that only show up under load.

**The canonical story (Takua renderer, ported to ARM):** code compiled for x86 with
`memory_order_relaxed` atomics rendered fine. On ARM64 the same code dropped tiles
because the relaxed ordering let the producer's writes become visible after the
consumer's reads. The fix wasn't more barriers — it was using correct acquire/release
semantics at the right points. Lesson: **treat x86 as the generous grader and ARM as the
real exam.** If it's right on ARM, it's right everywhere.

**What to do (all cheap):**
- Every load of a pointer you hand to another thread: `memory_order_acquire`. Every
  store that publishes it: `memory_order_release`. This is the "publish a pointer,
  hand off a lockless object" pattern — Lesson 7.5.
- Your refcounts (Lesson 7.6): `memory_order_acq_rel` (or relaxed for the fast-path
  increment + release for the decrement-that-frees). Never plain `relaxed` for the
  freeing decrement — that's the Takua bug.
- Never use `volatile` to synchronize. It does not order anything on ARM. `volatile`
  is for MMIO and signal-visible flags only.
- On ARM, `_Atomic` with acquire/release compiles to the `LDAR`/`STLR` instructions —
  cheap, not fences. On ARMv8.1+ (all Apple Silicon) the CAS/LSE atomics are a single
  instruction, not a load/store-exclusive loop — so correct atomics are *fast* here.
  clang emits them with `-mcpu=apple-m1`; your toolchain already does.
- `anti_panic`, logs, and the job scheduler do their ordering via atomics — grep for
  every `memory_order_relaxed` in the codebase and justify each one in a comment.

---

## 20.2 What changes on Apple Silicon specifically (CPU side)

Three physical facts shape the job system and data layout:

**1. P-cores and E-cores.** M-series has performance and efficiency cores in clusters
sharing L2. The OS schedules for you, but you can *help*: query core counts with
`nperflevels` (in `<sys/sysctl.h>` / via `sysctlbyname("hw.nperflevels")`) to size the
job pool; keep the main-thread render job on a P-core. Under thermal pressure the OS can
disable P-cores — your job system must degrade to fewer workers without deadlocking
(Lesson 18.3 already caps workers by `hw.ncpu`; subtract the P-core margin).

**2. Cache lines are 128 bytes.** Not the 64 of x86. False sharing (two threads
hammering adjacent fields) ping-pongs a 128-byte line between clusters. Padding that
was "obviously enough" at 64 bytes is *exactly half* the real line. Hot per-thread
structs: `_Alignas(128)`. See the corrected note in Lesson 14.

**3. Unified memory.** The CPU, GPU, and NPU share one physical DRAM with one address
space. No VRAM, no copy to upload. This is the biggest *win* for an engine like anti —
and it changes your buffer discipline (20.4).

---

## 20.3 GPU: Apple Silicon (TBDR + unified) vs discrete (IMR + VRAM)

The Apple GPU is a **tile-based deferred renderer**. It renders in small tiles, keeps
intermediate results in on-chip **tile memory** (fast SRAM, no DRAM round-trips), and
only writes a tile to DRAM once finished. A discrete GPU is an immediate-mode renderer
with dedicated VRAM. Same Metal/Vulkan, very different economics:

| Concern | Discrete GPU | Apple GPU (unified + TBDR) |
|---|---|---|
| Uploading data to GPU | must copy CPU→VRAM over PCIe | **free** — same memory, same address |
| Reading results back | stalling copy VRAM→CPU | fast, but flushes the pipeline |
| Bandwidth | huge, ~100s GB/s, dedicated | the **scarce resource** (shared DRAM) |
| Clears / MSAA resolves | cost real passes | nearly free — happen in the tile |
| Keeping a pass's output alive | cheap (it's in VRAM) | forces a tile flush → real cost |
| Load/store of framebuffer | cheap | **expensive** — you pay to dump the tile |

**The one sentence:** on Apple Silicon, **bandwidth is the currency and tile flushes are
the tax**. A pass that "does nothing" but round-trips the framebuffer can cost more than
the pass that did real work.

**Things that are weirdly cheap on Apple (use them):**
- Clears, full-screen constant writes, MSAA resolve — tile-local, near-free.
- Depth/pre-pass + a light deferred pass — merges well into the same tile.

**Things that are weirdly expensive on Apple (avoid):**
- Reading a framebuffer attachment in the middle of the frame (forces a flush).
- Chunky staged uploads "because that's what you do for discrete" — unnecessary here.
- Lots of tiny separate passes — each is a tile round-trip.

**Lossless compression gotcha (from Apple's own "Optimize high-end games" talk):**
Metal losslessly compresses textures automatically. The **`ShaderWrite`** and
**`PixelFormatView`** flags *disable* that compression. Only set them when you must
(e.g. compute writes a texture that is later sampled at non-pixel-aligned offsets).
If you port to Vulkan/MoltenVK and suddenly bandwidth halves, check what you're doing
to textures that Metal would otherwise compress.

---

## 20.4 What anti's Vulkan/MoltenVK path must do differently

anti targets Vulkan through MoltenVK on a CAMetalLayer. MoltenVK is a near-conformant
Vulkan 1.2 implementation — most of the API maps cleanly, but the *best practice*
shifts because the underlying hardware is Metal's:

1. **Zero-copy buffers.** Skip the staged-upload dance. Allocate your dynamic uniform /
   vertex / instance buffers in host-visible, device-accessible memory and write them
   straight from the job threads. No `vkCmdCopyBuffer`, no staging pools. This is
   strictly better on unified memory and still correct (just slower) on discrete —
   so it's portable, and it's the fast path on your machine.

2. **Ring buffers with N+1 buffers.** CPU and GPU race on the same memory (there's no
   VRAM to hide it). The proven pattern (BG3's engine team): a fixed-size ring of
   *separate* buffers, write into slot *i*, never reuse slot *i* until the frame using
   it has finished. Measure: on Apple it bought ~33% frame-time over a naive single
   dynamic buffer. (Alternative that is also fine: one big ring + a fence to know the
   oldest still-in-use frame.)

3. **Merge passes; minimize load/store.** Every `loadOp`/`storeOp` round-trip a render
   pass is a tile flush. Fuse your G-buffer + lighting into fewer passes, prefer
   `DONT_CARE` clears, and only store what the next frame needs. On a discrete GPU this
   is a mild win; on Apple it's the difference between 60 and 120 fps.

4. **Prefer parallel render command encoders over separate command buffers** where you
   can — it lets Metal parallelize across tiles instead of serializing GPU-side submits.

5. **Mesh shaders exist, but profile before you trust them.** Metal 3 exposes mesh +
   object stages, and MoltenVK exposes `VK_EXT_mesh_shader`. Tempting for
   GPU-driven-culling — but early macOS testing showed a task/mesh-shader cull being
   **~2× slower than brute-force** on some Apple GPUs. The naive draw path is often
   fine; only go GPU-driven where the profile says the CPU is the bottleneck.

---

## 20.5 Nanite-like (virtualized geometry / GPU-driven rendering) on Apple Silicon

What Nanite actually is, mechanically: a GPU-driven pipeline — the scene lives *in GPU
memory*, the CPU submits almost nothing, and the GPU does instance culling → cluster
culling → micro-polygon rasterization to a **visibility buffer** (instance ID + triangle
ID + depth), then shades materials per-pixel from that buffer, using **persistent
threads** for the hierarchy culling. It needs three things:

1. **Full GPU memory residency of the scene** (large buffer with all geometry).
2. **Mesh shaders / GPU compute for culling passes.**
3. **64-bit texture & buffer atomics.** This is the hard requirement — Nanite's
   rasterization is built on atomic counters/bitmasks in textures.

Reality check on your hardware:
- UE enables Nanite on macOS **only for M2 and newer, via Shader Model 6** — the M1
  line is explicitly excluded because it can't meet the quality bar (its GPUs lack some
  of the atomics/features). Even on M2+, the macOS/Nanite path came late and had to
  work around Metal's missing 64-bit texture atomics; platforms without them get Nanite
  **permanently disabled** (that's what Epic does). 
- Vulkan/MoltenVK on Apple Silicon exposes 64-bit *buffer* atomics fine, but
  *texture/image* atomics — Nanite's bread and butter — are not a given. **Do not bank
  anti's design on Nanite-style visibility-buffer rasterization until you've verified
  `VK_EXT_shader_atomic_float`/64-bit image-atomics behavior through MoltenVK on your
  actual chip.**

What this means for anti:
- Your version of "Nanite-like" should be the parts that are hardware-neutral: **GPU
  residency of static geometry + instance culling + simple LOD/hierarchy culling in
  compute, feeding ordinary draw calls.** That's portable everywhere and avoids the
  64-bit texture-atomic trap.
- Keep the fallback: when in doubt, brute-force draw with frustum culling on CPU. On
  many Apple GPUs the naive path is competitive (see 20.4.5).

---

## 20.6 Lumen-like (global illumination) on Apple Silicon

What Lumen does: real-time GI via a **Surface Cache** (lighting computed into a cache
of surfaces) + **screen-space traces** first, then **software ray tracing against Mesh
Distance Fields**, upgrading to **hardware ray tracing** where available for high
quality. The software path is the point — it runs *without* any RT hardware.

On Apple:
- **Software RT (distance fields) works on every Apple GPU** — this is your portable
  floor. Distance fields are cheap to march with no acceleration structure.
- **Hardware RT:** M1/M2 have **no RT cores** — ray tracing is emulated in compute
  (~25 ms per 1 ray/pixel in early M-series tests — not interactive). **M3 added
  hardware ray tracing** (a ray-box intersection hardware instruction + BVH traversal
  in the Metal RT API) roughly doubling RT throughput. Note the difference from NVIDIA:
  Apple's "RT" is a hardware assist, not a massive RTX core array.
- **Scene-update cost:** with hardware RT, rebuilding/updating the acceleration
  structure for >100k instances is a real per-frame cost; two-level BVHs get messy with
  overlapping meshes. Lumen exists partly to keep the *quality* high while scaling the
  cost down. 

What this means for anti:
- If you want Lumen-like lighting, target the **software path first**: screen-space
  tracing + SDF (distance-field) GI. It's portable, works on every Apple GPU, and
  doesn't commit you to hardware requirements.
- Treat M3+ hardware RT as a *quality upgrade*, not a baseline. Profile per device;
  on M1/M2 prefer SDF + screen-space and spend the frame budget elsewhere.
- The "ray tracing needs 100GB/s VRAM" fear is the discrete-GPU mindset; on Apple the
  cost is bandwidth and tile flushes, so keep your RT res low and upscale (MetalFX or
  your own temporal upscale) rather than running full-res RT traces.

---

## 20.7 The mindset (how to stay portable without losing your machine)

1. **Write to the intersection, optimize to your machine.** Plain C11, C99 portable
   code, `-mcpu=apple-m1` for tuning. The intersection is big; anti lives there.
2. **Correctness is decided on ARM.** Every atomic, every ordering decision, is
   validated by the weak memory model. If it runs on Apple Silicon under load, x86 will
   also behave.
3. **Performance is measured, never assumed.** Mesh shaders were slower than brute
   force on macOS until proven otherwise; RT is software until you profile M3. Ship the
   simple thing, profile, upgrade where the numbers justify it.
4. **Vulkan through MoltenVK is the portability floor.** When a Metal-only feature
   (tile shaders, memoryless attachments, 64-bit texture atomics) is the difference
   between 30 and 60 fps, you can dip into Metal behind a tiny abstraction in the
   `.m` shim — but that is a *last resort*, because it forks your renderer.
5. **The GPU is not a separate machine.** On Apple Silicon it shares your DRAM. Treat
   GPU memory as "more CPU memory" (zero-copy buffers), not as a foreign VRAM you must
   shuttle to. That discipline is also correct — just not optimal — on discrete GPUs.

---

## 20.8 Checklist (run this before calling a subsystem "done")

- [ ] Every hand-off atomic uses acquire/release (or a justified relaxed + comment).
      Nothing is ordered by `volatile`.
- [ ] Hot per-thread structs are `_Alignas(128)` (Apple line size), not 64.
- [ ] Dynamic buffers: zero-copy, host-visible; ring of N+1 with per-slot fences.
- [ ] Render passes merged; no mid-frame framebuffer reads; load/store minimized.
- [ ] No `ShaderWrite`/`PixelFormatView`-style flags set without a comment saying why.
- [ ] GPU-driven culling and ray tracing are *profile-gated* — the naive path exists and
      is a compile-time switch, so the fallback ships on any hardware.
- [ ] Scene geometry stays CPU-side-friendly (SDF/GPU-resident where proven), and the
      renderer does not hard-depend on 64-bit image atomics or mesh shaders.
- [ ] Thread pool sized for P/E cores; degrades gracefully under thermal P-core loss.

*Cross-references: memory model ↔ Lesson 7 (atomics) and Lesson 11.4/11.5; cache lines
↔ Lesson 14 (now 128-byte aware); job sizing ↔ Lesson 18; buffer/ring discipline and
MoltenVK quirks ↔ Lesson 8; AI contract checklist ↔ Lesson 15.*