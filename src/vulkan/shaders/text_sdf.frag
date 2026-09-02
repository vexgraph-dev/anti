#version 450
#extension GL_EXT_nonuniform_qualifier : require

layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform Push {
    layout(offset = 16) vec4  u_color;
    layout(offset = 32) uint  u_textureId;
    layout(offset = 36) float u_bold;
    layout(offset = 40) float u_smoothness;
    layout(offset = 48) vec4  u_uvBox;
} push;

layout(set = 0, binding = 0) uniform sampler2D u_textures[];

void main() {
    vec4 texColor = texture(u_textures[nonuniformEXT(push.u_textureId)], v_uv);
    float dist = texColor.r;

    // A negative u_bold (-0.5) is our secret signal from rich_text.c that this is a shadow quad.
    // Instead of drawing a sharp edge, we want a soft gradient bloom.
    if (push.u_bold < -0.1) {
        float shadowAlpha = smoothstep(0.1, 0.6, dist); 
        fragColor = vec4(push.u_color.rgb, push.u_color.a * shadowAlpha);
        return;
    }

    float threshold = 0.5 - push.u_bold;
    float baseWidth = fwidth(dist);
    float t = clamp(push.u_smoothness, 0.0, 1.0);
    float smoothing = mix(0.02, baseWidth * 1.8, t);
    float alpha = smoothstep(threshold - smoothing, threshold + smoothing, dist);

    if (dist == 0.0) {
        alpha = 0.0;
    }

    fragColor = vec4(push.u_color.rgb, push.u_color.a * alpha);
}
