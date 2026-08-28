#include "texture.h"
#include "../../io/vfs.h"
#include "../vk.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <CoreGraphics/CoreGraphics.h>

#define MAX_BINDLESS_TEXTURES 1024

// Bindless Registry Arrays
static void *s_images[MAX_BINDLESS_TEXTURES];
static void *s_memories[MAX_BINDLESS_TEXTURES];
static void *s_views[MAX_BINDLESS_TEXTURES];
static int s_textureCount = 0;

bool Texture_init(void) {
    // TODO: Create Global Descriptor Pool, Layout, and Bindless Set
    return true;
}

void Texture_shutdown(void) {
    // TODO: Free vulkan resources for all textures
    s_textureCount = 0;
}

int32_t Texture_load(const char *vfsPath) {
    if (s_textureCount >= MAX_BINDLESS_TEXTURES) {
        printf("Texture limit reached!\n");
        return -1;
    }

    size_t fileSize = 0;
    void *fileData = VFS_readAll(vfsPath, &fileSize);
    if (!fileData) {
        printf("Failed to read texture file: %s\n", vfsPath);
        return -1;
    }

    // Decode with CoreGraphics
    CGDataProviderRef provider = CGDataProviderCreateWithData(NULL, fileData, fileSize, NULL);
    CGImageRef image = NULL;
    
    // Auto-detect PNG or JPG (simple extension check)
    if (strstr(vfsPath, ".png") || strstr(vfsPath, ".PNG")) {
        image = CGImageCreateWithPNGDataProvider(provider, NULL, true, kCGRenderingIntentDefault);
    } else {
        image = CGImageCreateWithJPEGDataProvider(provider, NULL, true, kCGRenderingIntentDefault);
    }
    CGDataProviderRelease(provider);

    if (!image) {
        printf("Failed to decode image: %s\n", vfsPath);
        free(fileData);
        return -1;
    }

    size_t width = CGImageGetWidth(image);
    size_t height = CGImageGetHeight(image);

    // Render to raw RGBA buffer
    size_t bytesPerPixel = 4;
    size_t bytesPerRow = bytesPerPixel * width;
    void *rgbaData = malloc(height * bytesPerRow);
    
    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
    CGContextRef context = CGBitmapContextCreate(rgbaData, width, height, 8, bytesPerRow, colorSpace,
                                                 kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
    
    CGContextDrawImage(context, CGRectMake(0, 0, width, height), image);
    
    CGContextRelease(context);
    CGColorSpaceRelease(colorSpace);
    CGImageRelease(image);
    free(fileData);

    printf("Decoded texture: %s (%zux%zu)\n", vfsPath, width, height);

    // TODO: Upload rgbaData to Vulkan Staging Buffer -> VkImage
    
    free(rgbaData);

    int32_t id = s_textureCount++;
    return id;
}

void *Texture_getDescriptorSet(void) {
    return NULL; // TODO: Return the bindless set
}
