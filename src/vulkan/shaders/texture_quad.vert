#version 450

layout(push_constant) uniform Push {
    layout(offset = 0) vec4 u_rectNdc; // x, y, width, height in NDC
} push;

layout(location = 0) out vec2 v_uv;

const vec2 CORNERS[6] = vec2[6](
    vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0),
    vec2(0.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0)
);

void main() {
    vec2 localPos = CORNERS[gl_VertexIndex];
    v_uv = localPos; // UV goes from 0.0 to 1.0

    vec2 p = push.u_rectNdc.xy + localPos * push.u_rectNdc.zw;
    gl_Position = vec4(p, 0.0, 1.0);
}
