#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>
#import <CoreText/CoreText.h>

// Finds the absolute file path to a system font by its PostScript or Family name.
// Returns a malloc'd string (must be freed by caller), or NULL if not found.
char* System_getFontPath(const char* familyName) {
    @autoreleasepool {
        NSString *name = [NSString stringWithUTF8String:familyName];
        
        // Try finding by exact name
        NSFont *font = [NSFont fontWithName:name size:12.0];
        
        if (!font) {
            // Try generic matching
            font = [NSFont systemFontOfSize:12.0];
        }
        
        if (!font) return NULL;
        
        CTFontRef ctFont = (__bridge CTFontRef)font;
        CFURLRef url = (CFURLRef)CTFontCopyAttribute(ctFont, kCTFontURLAttribute);
        if (url) {
            NSString *path = [(__bridge NSURL *)url path];
            char *result = strdup([path UTF8String]);
            CFRelease(url);
            return result;
        }
    }
    return NULL;
}
