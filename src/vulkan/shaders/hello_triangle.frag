#version 450

layout(location = 0) in vec2 fragCoord;
layout(location = 1) in float time;

layout(location = 0) out vec4 outColor;

// Edge function for point-in-triangle test (positive inside, negative outside).
float edge(vec2 a, vec2 b, vec2 p) {
    return (p.x - a.x) * (b.y - a.y) - (p.y - a.y) * (b.x - a.x);
}

// Signed distance of p to the segment ab (for a smooth triangle edge).
float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float t = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * t);
}

void main() {
    // A standard, lightweight "hello triangle" shader.
    // Creates a smooth color gradient based on position and time.
    vec3 col = 0.5 + 0.5 * cos(time + fragCoord.xyx + vec3(0.0, 2.0, 4.0));

    // --- Moving triangle (added on top, existing gradient untouched) ---
    // Vertices slide horizontally across NDC space with time; the triangle
    // bobbles and flips so you can see it running while the gradient animates.
    float bounce = 0.35 * sin(time * 1.4);
    vec2 v0 = vec2(-0.9 + bounce, -0.7);
    vec2 v1 = vec2( 0.5 + bounce, -0.1);
    vec2 v2 = vec2(-0.3 + bounce,  0.8);

    vec2 p = fragCoord;
    float e0 = edge(v0, v1, p);
    float e1 = edge(v1, v2, p);
    float e2 = edge(v2, v0, p);

    if (e0 >= 0.0 && e1 >= 0.0 && e2 >= 0.0) {
        // Inside the triangle: give it a hot color that also animates.
        vec3 triCol = 0.5 + 0.5 * cos(time + vec3(0.0, 2.0, 4.0) + 2.0);
        col = mix(col, triCol, 0.85);
    } else {
        // Outside: thin glowing outline so the triangle is visible on any gradient.
        float d = min(min(segDist(p, v0, v1), segDist(p, v1, v2)), segDist(p, v2, v0));
        float glow = exp(-d * 14.0) * 0.8;
        col += glow * vec3(1.0, 0.4, 0.4);
    }

    outColor = vec4(col, 1.0);
}
