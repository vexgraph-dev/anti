#include "image_mac.h"
#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>
#import <ImageIO/ImageIO.h>
#include <stdlib.h>
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Image_mac (system/image_mac.m)
 * ============================================================================
 * detect format (PNG, JPEG, etc)
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - ImageMac_decode(fileData, fileSize, outW, outH)
 * ============================================================================
 */


void *ImageMac_decode(const void *fileData, size_t fileSize, size_t *outW, size_t *outH) {
    if (!fileData || fileSize == 0) return nullptr;

    CGDataProviderRef provider = CGDataProviderCreateWithData(nullptr, fileData, fileSize, nullptr);
    if (!provider) return nullptr;

    // Create image source to auto-detect format (PNG, JPEG, etc)
    CGImageSourceRef source = CGImageSourceCreateWithDataProvider(provider, nullptr);
    CGDataProviderRelease(provider);
    
    if (!source) return nullptr;

    CGImageRef image = CGImageSourceCreateImageAtIndex(source, 0, nullptr);
    CFRelease(source);

    if (!image) return nullptr;

    size_t width = CGImageGetWidth(image);
    size_t height = CGImageGetHeight(image);
    
    size_t bytesPerPixel = 4;
    size_t bytesPerRow = bytesPerPixel * width;
    void *rgbaData = malloc(height * bytesPerRow);
    
    if (rgbaData) {
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        CGContextRef context = CGBitmapContextCreate(rgbaData, width, height, 8, bytesPerRow, colorSpace,
                                                     kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
        
        if (context) {
            CGContextDrawImage(context, CGRectMake(0, 0, width, height), image);
            CGContextRelease(context);
        } else {
            free(rgbaData);
            rgbaData = nullptr;
        }
        CGColorSpaceRelease(colorSpace);
    }
    
    CGImageRelease(image);

    if (rgbaData) {
        if (outW) *outW = width;
        if (outH) *outH = height;
    }
    
    return rgbaData;
}
