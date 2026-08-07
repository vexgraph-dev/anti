#version 450

layout(push_constant) uniform PushConstants {
    float u_time;
} pc;

layout(location = 0) out vec3 color;

void main() {
    float t = pc.u_time;

    vec2 positions[3] = vec2[](
        vec2( 0.0, -0.6),
        vec2( 0.6,  0.6),
        vec2(-0.6,  0.6)
    );

    vec3 colors[3] = vec3[](
        vec3(1.0, 0.1, 0.1),
        vec3(0.1, 1.0, 0.1),
        vec3(0.1, 0.3, 1.0)
    );

    float angle = t * 1.5;
    float c = cos(angle);
    float s = sin(angle);
    vec2 p = positions[gl_VertexIndex];

    // Spin the triangle and pulse its scale so the animation is obvious.
    float scale = 0.7 + 0.3 * sin(t * 3.0);
    vec2 rotated = vec2(p.x * c - p.y * s, p.x * s + p.y * c) * scale;

    gl_Position = vec4(rotated, 0.0, 1.0);
    color = colors[gl_VertexIndex];
}
