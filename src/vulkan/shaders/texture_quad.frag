#version 450
#extension GL_EXT_nonuniform_qualifier : require

layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform Push {
    layout(offset = 16) vec4 u_color;      // base tint
    layout(offset = 32) uint u_textureId;  // Bindless texture ID
} push;

layout(set = 0, binding = 0) uniform sampler2D u_textures[];

void main() {
    vec4 texColor = texture(u_textures[nonuniformEXT(push.u_textureId)], v_uv);
    fragColor = texColor * push.u_color;
}
