#ifndef PRIMITIVE_PACK_H
#define PRIMITIVE_PACK_H

#include <stdint.h>

// primitive/pack.h — bit-packing utilities (Legacy: primitive/Pack.java).

static inline int16_t Pack_packByte(int8_t b1, int8_t b2) { return (int16_t)((b1 << 8) | (b2 & 0xFF)); }
static inline int32_t Pack_packShort(int16_t s1, int16_t s2) { return (int32_t)((s1 << 16) | (s2 & 0xFFFF)); }
static inline int64_t Pack_packInt(int32_t i1, int32_t i2) { return ((int64_t)i1 << 32) | (uint32_t)i2; }
static inline int8_t Pack_unpackByte1(int16_t p) { return (int8_t)(p >> 8); }
static inline int8_t Pack_unpackByte2(int16_t p) { return (int8_t)(p & 0xFF); }
static inline int16_t Pack_unpackShort1(int32_t p) { return (int16_t)(p >> 16); }
static inline int16_t Pack_unpackShort2(int32_t p) { return (int16_t)(p & 0xFFFF); }
static inline int32_t Pack_unpackInt1(int64_t p) { return (int32_t)(p >> 32); }
static inline int32_t Pack_unpackInt2(int64_t p) { return (int32_t)(p & 0xFFFFFFFF); }

#endif
