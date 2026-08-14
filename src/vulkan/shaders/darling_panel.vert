#version 450

// Darling UI Panel Vertex Shader
// Transforms virtual canvas coordinates to NDC via push constants.
// Expands a 6-vertex quad from gl_VertexIndex (zero vertex buffer overhead).
layout(push_constant) uniform PushConstants {
    mat4 proj;        // canvas -> NDC orthographic projection (column-major) (64B)
    vec4 rect;        // (x, y, w, h) in canvas units (16B)
    vec4 color;       // (r, g, b, a) normalized 0.0 - 1.0 (16B)
    vec4 style;       // (cornerRadius, borderWidth, 0.0, 0.0) (16B)
} pc;

layout(location = 0) out vec4 vColor;
layout(location = 1) out vec2 vLocalPos;
layout(location = 2) out vec2 vDimensions;
layout(location = 3) out vec4 vStyle;

const vec2 corners[6] = vec2[](
    vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(0.0, 1.0),
    vec2(1.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0)
);

void main() {
    vec2 corner = corners[gl_VertexIndex];
    vec2 px = pc.rect.xy + corner * pc.rect.zw;
    gl_Position = pc.proj * vec4(px, 0.0, 1.0);
    vColor = pc.color;
    vLocalPos = corner * pc.rect.zw;
    vDimensions = pc.rect.zw;
    vStyle = pc.style;
}
