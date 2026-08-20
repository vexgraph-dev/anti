#ifndef ANNOTATION_PLATFORM_EXCLUSIVE_H
#define ANNOTATION_PLATFORM_EXCLUSIVE_H

// src/annotation/platform_exclusive.h — C mirror of
// legacy-java/src/annotation/PlatformExclusive.java.
//
// PlatformExclusive(platform): marks a backend as exclusive to the named
// platform (e.g. "Windows", "Linux", "Mac"). The platform is a string literal.
//
// See draft.h for the macro convention (C has no language-level annotations).

#define PLATFORM_EXCLUSIVE(platform) _Static_assert(1, "@PlatformExclusive(" platform ")");

#endif