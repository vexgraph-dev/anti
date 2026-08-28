#ifndef ANTI_TEXTURE_H
#define ANTI_TEXTURE_H

#include <stdint.h>
#include <stdbool.h>

// Initialize the global texture registry (creates bindless descriptor sets)
bool Texture_initModule(void *instance, void *gpa, void *phys, void *device, void *queue, uint32_t queueFamily);

// Shut down the registry and free all VkImages and memory
void Texture_shutdown(void);

// Loads an image from the Virtual File System (VFS) via macOS CoreGraphics.
// Automatically transitions to SHADER_READ_ONLY_OPTIMAL and uploads to VRAM.
// Returns a bindless texture ID (>= 0) on success, or -1 on failure.
int32_t Texture_load(const char *vfsPath);

// Gets the global Vulkan Descriptor Set that contains the bindless texture array
void *Texture_getDescriptorSet(void);

// Gets the descriptor set layout used for the bindless array
void *Texture_getDescriptorSetLayout(void);

#endif // ANTI_TEXTURE_H
