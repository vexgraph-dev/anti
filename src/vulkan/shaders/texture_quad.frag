#version 450
#extension GL_EXT_nonuniform_qualifier : require

layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 fragColor;

// Fill param constants (must match PictureFillParam enum in vk.h)
#define FILL_CENTER       0u
#define FILL_TOP_LEFT     1u
#define FILL_TOP_RIGHT    2u
#define FILL_BOTTOM_LEFT  3u
#define FILL_BOTTOM_RIGHT 4u

// Scale mode constants (must match PictureScaleMode enum in vk.h)
#define SCALE_FIT       0u
#define SCALE_ZOOM_FILL 1u
#define SCALE_ZOOM_FIT  2u

layout(push_constant) uniform Push {
    layout(offset = 16) vec4  u_color;      // tint (rgba)
    layout(offset = 32) uint  u_textureId;  // Bindless texture ID
    layout(offset = 36) float u_imgAspect;  // image pixel width / height
    layout(offset = 40) float u_quadAspect; // quad display width / height
    layout(offset = 44) uint  u_mode;       // PictureScaleMode
    layout(offset = 48) uint  u_fillParam;  // PictureFillParam
} push;

layout(set = 0, binding = 0) uniform sampler2D u_textures[];

// Returns the x-axis offset for a horizontal fill param.
float horzOffset(uint param, float slack) {
    if (param == FILL_TOP_RIGHT || param == FILL_BOTTOM_RIGHT) return slack;
    if (param == FILL_CENTER)                                   return slack * 0.5;
    return 0.0; // TOP_LEFT, BOTTOM_LEFT
}

// Returns the y-axis offset for a vertical fill param.
float vertOffset(uint param, float slack) {
    if (param == FILL_BOTTOM_LEFT || param == FILL_BOTTOM_RIGHT) return slack;
    if (param == FILL_CENTER)                                     return slack * 0.5;
    return 0.0; // TOP_LEFT, TOP_RIGHT
}

void main() {
    vec2 uv = v_uv; // base 0→1 across the quad

    // r > 1 means quad is wider relative to the image (pillarbox territory in ZOOM_FIT)
    float r = push.u_quadAspect / push.u_imgAspect;

    if (push.u_mode == SCALE_FIT) {
        // Stretch and warp — UV unchanged, image fills the entire quad
    }
    else if (push.u_mode == SCALE_ZOOM_FILL) {
        // Cover: scale so the quad is completely filled. Crop the overflow.
        if (r >= 1.0) {
            // Quad wider than image → fill width exactly, crop top/bottom
            float visibleFrac = 1.0 / r;             // how much of image height is visible
            float offset = vertOffset(push.u_fillParam, 1.0 - visibleFrac);
            uv.y = uv.y * visibleFrac + offset;
        } else {
            // Quad taller than image → fill height exactly, crop left/right
            float visibleFrac = r;                    // how much of image width is visible
            float offset = horzOffset(push.u_fillParam, 1.0 - visibleFrac);
            uv.x = uv.x * visibleFrac + offset;
        }
    }
    else if (push.u_mode == SCALE_ZOOM_FIT) {
        // Contain: entire image is visible. Letterbox/pillarbox the dead space.
        if (r >= 1.0) {
            // Quad wider → pillarboxes on left/right
            float imgFrac = 1.0 / r;                 // fraction of quad width the image occupies
            float offset = horzOffset(push.u_fillParam, 1.0 - imgFrac);
            // remap pixel position to image UV: outside [0,1] → transparent
            uv.x = (uv.x - offset) / imgFrac;
        } else {
            // Quad taller → letterboxes top/bottom
            float imgFrac = r;                        // fraction of quad height the image occupies
            float offset = vertOffset(push.u_fillParam, 1.0 - imgFrac);
            uv.y = (uv.y - offset) / imgFrac;
        }
        // Discard pixels outside the image area (the letterbox / pillarbox region)
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            fragColor = vec4(0.0, 0.0, 0.0, 0.0);
            return;
        }
    }

    // CoreGraphics decodes image pixel data bottom-up in memory, but Vulkan expects top-down.
    // By flipping the Y coordinate right before sampling, our top-left cropping math works natively.
    vec4 texColor = texture(u_textures[nonuniformEXT(push.u_textureId)], vec2(uv.x, 1.0 - uv.y));
    fragColor = texColor * push.u_color;
}
