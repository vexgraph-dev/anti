#include "image_mac.h"
#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>
#import <ImageIO/ImageIO.h>
#include <stdlib.h>

void *ImageMac_decode(const void *fileData, size_t fileSize, size_t *outW, size_t *outH) {
    if (!fileData || fileSize == 0) return NULL;

    CGDataProviderRef provider = CGDataProviderCreateWithData(NULL, fileData, fileSize, NULL);
    if (!provider) return NULL;

    // Create image source to auto-detect format (PNG, JPEG, etc)
    CGImageSourceRef source = CGImageSourceCreateWithDataProvider(provider, NULL);
    CGDataProviderRelease(provider);
    
    if (!source) return NULL;

    CGImageRef image = CGImageSourceCreateImageAtIndex(source, 0, NULL);
    CFRelease(source);

    if (!image) return NULL;

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
            rgbaData = NULL;
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
