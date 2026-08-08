#version 450

layout(push_constant) uniform PushConstants {
    float u_time;
} pc;

layout(location = 0) out vec2 fragCoord;
layout(location = 1) out float time;

void main() {
    time = pc.u_time;

    // Full-screen triangle covering the -1 to 1 NDC space
    vec2 positions[3] = vec2[](
        vec2(-1.0, -1.0),
        vec2( 3.0, -1.0),
        vec2(-1.0,  3.0)
    );

    vec2 p = positions[gl_VertexIndex];
    gl_Position = vec4(p, 0.0, 1.0);
    fragCoord = p;
}
