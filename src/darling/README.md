# darling — UI Threading Contract

> -# generated — notes written for the lead developer (me), capturing the
> architecture as we settled it. Not user-facing documentation. Keep in sync
> with how the code actually behaves; correct it the moment it stops matching.

## The core rule (settled)

**Bounds and draw are fundamentally separate — from day one, on every UI part.**

- **Bounds** (placement, size, layout) are owned and written by **Thread 0**
  (the AppKit event pump / main thread). This is the `validate()` role: layout
  happens on the thread that owns window + layer + swapchain lifecycle.
- **Draw** (the paint override, the actual rendering of a node) is owned by the
  **Draw Thread**. Reactive updates, paint overrides, visual property changes
  land here.

They are **not** "waiting on each other". Bounds can change freely on Thread 0
while the draw override paints independently on the Draw Thread. Nothing blocks
between them. The only cross-thread signal is an explicit **dirty generation**.

## Why this shape

The OS window resize is delivered to Thread 0 (AppKit pumps there). The swapchain
is the OS-level surface, so it lives on Thread 0 too. But the *content* must keep
rendering smoothly at its own resolution while the window changes size. Putting
bounds/layout on Thread 0 and paint on the Draw Thread decouples the two:

- Resize never stalls the render loop — the Draw Thread keeps painting the
  latest snapshot every frame.
- The scene can live at a fixed virtual resolution (e.g. 800x600) and scale
  itself (see `Canvas` virtual size / `MODE_PIXEL | MODE_FIT | MODE_STRETCH`);
  the window's swapchain follows the real window, the content does not re-render.

## The handoff (explicit invalidation, not implicit coupling)

The one rule that keeps this from collapsing into stale visuals:

> A bounds change bumps a **dirty generation**. It does NOT implicitly trigger a
> repaint. The Draw Thread repaints only because it observed a generation bump
> and chose to re-paint from the current snapshot.

If bounds ever silently imply a redraw, bounds and draw are re-coupled and the
split is gone. Keep invalidation explicit.

Contract per node:

- **Bounds struct** — off-heap (x, y, w, h). Written by Thread 0.
- **Dirty generation** — bumped by Thread 0 on any bounds mutation. This is the
  only cross-thread signal.
- **paint() / draw override** — owned by the Draw Thread. At frame start, read
  the node's generation + a bounds *snapshot*; if changed, re-paint from the
  snapshot. Never blocks, never writes bounds.

## Text / vector nuance

Vector text is the special case that stays on **Thread 0**: because the glyph
geometry is produced from the font's own vector metadata (per ROADMAP Phase 3),
re-sizing / re-wrapping a multi-letter string regenerates glyph geometry, which
is layout — so it happens on Thread 0, and it is **not** stored in a per-text
FBO (RAM). The Draw Thread just consumes the regenerated geometry.

- Content *change* (new string) — layout side, Thread 0.
- Color / visual change (fill, opacity, stroke) — paint side, Draw Thread.
- The glyph data lives off-heap; the Draw Thread never re-tessellates it.

## Thread map

| Concern                    | Thread 0 (pump/main)            | Draw Thread                 |
|----------------------------|---------------------------------|-----------------------------|
| Window, layer, swapchain   | owns (create + rebuild + size)  | consumes surface           |
| UI bounds / layout         | owns (validate)                 | reads snapshot              |
| Dirty generation           | bumps                           | observes                    |
| paint() / draw override    | —                               | owns (reactive + overrides) |
| Visual properties (color…) | — (or enqueue)                  | owns (paint-side mutation)  |
| Vector text geometry       | owns (resize/re-wrap)           | consumes geometry           |

## Scene hierarchy (settled)

The scene is a node, not a static singleton. Chain (structural subclass = payload
starts at the parent's `USER_STRIDE`, per `TypeRegister.getParentClass`):

```
Container (48) > Panel (48..112, bg color + tree) > Scene (112.., mapping mode)
                                                   > Scene2D / Scene3D (empty payloads)
```

- The scene's **size IS Container w/h** (`setSize`) — the fixed virtual
  resolution that the offscreen render target is built at. `setBackgroundColor`
  (0xRRGGBBAA, Panel payload) is the render-pass clear color.
- The window's swapchain follows the real window; the present pass **blits the
  scene → swapchain** (source = scene size, dest = window extent), so resizing
  never re-creates the render target or re-renders the scene.
- Scene2D vs Scene3D is a *type* distinction for the renderer to dispatch on
  (2D ortho vs 3D perspective); payloads are empty until they diverge.
- `checkContainer`/`checkPanel` walk the parent chain via
  `TypeRegister.isA(...)` so any scene pointer passes as Panel/Container.