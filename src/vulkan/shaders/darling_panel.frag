#version 450

// Darling UI Panel Fragment Shader
// Renders uniform panel color with optional smooth rounded corners.
layout(location = 0) in vec4 vColor;
layout(location = 1) in vec2 vLocalPos;
layout(location = 2) in vec2 vDimensions;
layout(location = 3) in vec4 vStyle;

layout(location = 0) out vec4 outColor;

void main() {
    float radius = vStyle.x;
    if (radius > 0.0) {
        radius = min(radius, min(vDimensions.x, vDimensions.y) * 0.5);
        vec2 halfDim = vDimensions * 0.5;
        vec2 p = abs(vLocalPos - halfDim) - (halfDim - vec2(radius));
        float d = length(max(p, 0.0)) + min(max(p.x, p.y), 0.0) - radius;
        if (d > 0.0) {
            discard;
        }
    }
    outColor = vColor;
}
