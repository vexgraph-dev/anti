#version 450

layout(location = 0) in vec2 fragCoord;
layout(location = 1) in float time;

layout(location = 0) out vec4 outColor;

void main() {
    // A standard, lightweight "hello triangle" shader.
    // Creates a smooth color gradient based on position and time.
    vec3 col = 0.5 + 0.5 * cos(time + fragCoord.xyx + vec3(0.0, 2.0, 4.0));
    outColor = vec4(col, 1.0);
}
