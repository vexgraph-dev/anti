#ifndef OOP_STRIDE_H
#define OOP_STRIDE_H

#include <stddef.h>
#include <stdint.h>

// oop/stride.h — the Stride utility, ported from oop/Stride.java.
//
// Maps a class id to the byte width of one slot of that class. Collections ask
// Stride_get(element_class) when they allocate so they know how many bytes each
// element occupies. Custom structs (ID_CUSTOM_STRUCT + n) are answered by the
// Struct registry, which owns their layout.

// Byte width of one element of the given class. Returns 8 for anything it does
// not know (pointer-sized default, matching legacy).
size_t Stride_get(uint32_t class_id);

#endif