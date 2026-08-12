#version 450

// Textured image quad. Push constants carry the darling.Canvas orthographic
// projection (maps canvas units to NDC, y-down, so setLocation means stable
// canvas coordinates), the panel's resolved rect in canvas units, and the crop
// region as normalized UVs (uvMin..uvMax from darling.Picture.getCrop). The
// vertex shader expands a full-screen-style triangle pair from gl_VertexIndex
// so no vertex buffer is needed — the picture is drawn where the darling.Picture
// actually resolves, sampling only the cropped region of its image.
layout(push_constant) uniform PushConstants {
    mat4 proj;      // canvas -> NDC orthographic projection (column-major)
    vec4 rect;      // (x, y, w, h) in canvas units, y down
    vec2 uvMin;     // crop top-left, normalized
    vec2 uvMax;     // crop bottom-right, normalized
} pc;

layout(location = 0) out vec2 vUv;

const vec2 corners[6] = vec2[](
    vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(0.0, 1.0),
    vec2(1.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0)
);

void main() {
    vec2 corner = corners[gl_VertexIndex];
    vec2 px = pc.rect.xy + corner * pc.rect.zw;
    // Vulkan NDC is y-down (0,0 framebuffer = top-left), and the ortho proj
    // keeps +y going straight down. The old manual px/viewport math inverted
    // the quad's winding, which turned it into a back face and culled it; doing
    // the transform in the mat4 keeps winding intact.
    gl_Position = pc.proj * vec4(px, 0.0, 1.0);
    vUv = pc.uvMin + corner * (pc.uvMax - pc.uvMin);
}