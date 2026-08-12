#version 450

// Textured image quad. Push constants carry the panel's resolved screen rect
// (in offscreen pixel space, top-left origin) plus the viewport size, and the
// vertex shader expands a full-screen-style triangle pair from gl_VertexIndex
// so no vertex buffer is needed — the picture is drawn where the darling.Panel
// actually resolves.
layout(push_constant) uniform PushConstants {
    vec4 rect;      // (x, y, w, h) in framebuffer pixels, y down
    vec2 viewport;  // (framebuffer width, framebuffer height)
    float z;        // z-order placeholder (unused for now)
    float pad;
} pc;

layout(location = 0) out vec2 vUv;

const vec2 corners[6] = vec2[](
    vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(0.0, 1.0),
    vec2(1.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0)
);

void main() {
    vec2 corner = corners[gl_VertexIndex];
    vec2 px = pc.rect.xy + corner * pc.rect.zw;
    vec2 ndc = vec2(
        px.x / pc.viewport.x * 2.0 - 1.0,
        1.0 - px.y / pc.viewport.y * 2.0
    );
    gl_Position = vec4(ndc, 0.0, 1.0);
    vUv = corner;
}