#version 450

layout(location = 0) in vec2 fragCoord;
layout(location = 1) in float time;

layout(location = 0) out vec4 outColor;

void main() {
    // A highly detailed, classic "seahorse valley" coordinate on the Mandelbrot boundary
    vec2 target = vec2(-0.743643887, 0.131825904);
    
    // Zoom in exponentially and infinitely based purely on time
    float zoom = exp(-time * 0.35); 
    
    // Map our screen coordinates to the zooming fractal space
    vec2 c = target + fragCoord * zoom * 2.5;
    
    vec2 z = vec2(0.0);
    // Dynamically scale up the iteration count as we zoom in for sharper details
    int max_iter = 100 + int(300.0 * (1.0 - zoom)); 
    int iter = 0;
    
    for(int i = 0; i < max_iter; i++) {
        // Complex squaring: z = z^2 + c
        float x = (z.x * z.x - z.y * z.y) + c.x;
        float y = (2.0 * z.x * z.y) + c.y;
        
        if((x * x + y * y) > 16.0) break; // Escape radius 4.0 (16.0 squared)
        z.x = x;
        z.y = y;
        iter++;
    }
    
    vec3 col = vec3(0.0); // Inside the fractal is void black
    
    if (iter < max_iter) {
        // Standard continuous smooth coloring algorithm to fix "banding" 
        // as we zoom in deeply
        float log_z = log(z.x*z.x + z.y*z.y) / 2.0;
        float nu = log(log_z / log(2.0)) / log(2.0);
        float f = float(iter) + 1.0 - nu;
        
        // Trippy, fast-shifting RGB TikTok-style palette
        float tColor = f * 0.05 - time * 1.5;
        col = 0.5 + 0.5 * cos(6.28318 * (tColor + vec3(0.0, 0.33, 0.67)));
        
        // Add a subtle bloom glow to the bands
        col *= 1.0 - exp(-f * 0.08);
    }
    
    outColor = vec4(col, 1.0);
}
