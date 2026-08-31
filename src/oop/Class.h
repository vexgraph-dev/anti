#ifndef OOP_CLASS_H
#define OOP_CLASS_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "oop/field.h"

// oop/Class.h — Dynamic Class Schema Engine (Legacy: oop/Fields, now Class).
//
// A Class is a runtime struct schema defining field sizes, offsets, names,
// and dual-stream partitioning (Stream 1 for hot flat primitives, Stream 2 for nested structs).
// Reserved constructor is Class() — anti style, struct-like.

typedef struct Class {
    uint32_t genericId;        // ID_CUSTOM_STRUCT + n
    size_t stride;             // Total singleton stride
    size_t stream1Stride;      // Hot primitive stream stride
    size_t stream2Stride;      // Secondary struct stream stride
    uint32_t count;            // Number of fields
    Field *items;              // Array of field descriptors
} Class;

// Compatibility aliases — Fields == Class
typedef Class Fields;
typedef Class Construct;
typedef Field ConstructField;
typedef Field StructField;

// =========================================================================
// CONSTRUCTORS
// =========================================================================

// Fields: Variadic constructor taking field sizes or class IDs.
// Example:
//   Fields *playerFields = Fields(sizeof(Vec3), sizeof(Vec3), sizeof(Vec3), sizeof(int), sizeof(char*));
#define Fields(...) Fields_build(__VA_ARGS__)

#define Fields_build(...) \
    Fields_create((const size_t[]){__VA_ARGS__}, sizeof((const size_t[]){__VA_ARGS__}) / sizeof(size_t))

#define construct(...) Fields(__VA_ARGS__)
#define Construct_build(...) Fields(__VA_ARGS__)

// Build a Fields schema from an array of sizes or class IDs
Fields *Fields_create(const size_t *sizesOrClasses, size_t count);

// Named Class constructor — anti reserved word Class()
// Usage:
//   Class *player = Class(TYPE_VEC3, "position", TYPE_INT, "health", TYPE_STRING, "name");
//   Class *mat = Class(TYPE_FLOAT, "albedo", TYPE_FLOAT, "roughness");
// Each pair is (classId or Class* , "fieldName"). Count is pairs, auto-counted by macro.
// Edge: odd args, null name, duplicate name → returns nullptr.
Class *Class_createNamed(size_t count, ...);
#define PP_NARG(...) PP_NARG_I(__VA_ARGS__, PP_RSEQ_N())
#define PP_NARG_I(...) PP_ARG_N(__VA_ARGS__)
#define PP_ARG_N(_1,_2,_3,_4,_5,_6,_7,_8,_9,_10,_11,_12,_13,_14,_15,_16,_17,_18,_19,_20, N, ...) N
#define PP_RSEQ_N() 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0
#define Class(...) Class_createNamed(PP_NARG(__VA_ARGS__) / 2, __VA_ARGS__)

// Named lookup — field name by index, or index by name (for spotlight)
const char *Class_fieldName(uint32_t generic, size_t fieldIndex);
int32_t Class_fieldIndex(uint32_t generic, const char *fieldName);

// Lookup and metadata inspection
const Fields *Fields_get(uint32_t generic);
size_t   Fields_stride(uint32_t generic);
uint32_t Fields_count(uint32_t generic);
uint32_t Fields_fieldSize(uint32_t generic, size_t fieldIndex);
bool     Fields_isFieldStruct(uint32_t generic, size_t fieldIndex);

// Size resolver helper
size_t Fields_resolveSize(size_t val, bool *outIsStruct);

#endif
