#!/bin/sh
# build_shaders.sh — recompile GLSL sources into the SPIR-V blobs the engine
# loads at runtime (vulkan.c loadSpvAny -> src/vulkan/spv/*.spv).
#
# Why both exist: Vulkan consumes SPIR-V bytecode, never GLSL text; the .spv
# files are the deployable artifacts, these .vert/.frag files are their source.
# Edit the GLSL here, then run this script before rebuilding the engine.
#
# Requires: glslangValidator (brew install glslang).
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"

glslangValidator -V "$DIR/hello_triangle.vert" -o "$DIR/../spv/hello_triangle_vert.spv"
glslangValidator -V "$DIR/hello_triangle.frag" -o "$DIR/../spv/hello_triangle_frag.spv"
glslangValidator -V "$DIR/solid_quad.vert" -o "$DIR/../spv/solid_quad_vert.spv"
glslangValidator -V "$DIR/solid_quad.frag" -o "$DIR/../spv/solid_quad_frag.spv"
glslangValidator -V "$DIR/texture_quad.vert" -o "$DIR/../spv/texture_quad_vert.spv"
glslangValidator -V "$DIR/texture_quad.frag" -o "$DIR/../spv/texture_quad_frag.spv"

echo "shaders: spv refreshed"
glslangValidator -V "$DIR/text_sdf.vert" -o "$DIR/../spv/text_sdf_vert.spv"
glslangValidator -V "$DIR/text_sdf.frag" -o "$DIR/../spv/text_sdf_frag.spv"
