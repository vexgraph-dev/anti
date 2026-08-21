#ifndef OOP_FIELDS_H
#define OOP_FIELDS_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// oop/fields.h — Dynamic Fields Schema Engine.
//
// A Fields object is a runtime schema defining struct field sizes, offsets,
// and dual-stream partitioning (Stream 1 for hot flat primitives, Stream 2 for nested structs).

typedef struct Field {
    uint32_t size;             // Byte size or class ID
    uint32_t offset;           // Unified singleton byte offset
    uint32_t stream1Offset;    // Hot primitive stream byte offset
    uint32_t stream2Offset;    // Nested struct stream byte offset
    bool     isStruct;         // True if field is a compound sub-struct
} Field;

typedef struct Fields {
    uint32_t genericId;        // ID_CUSTOM_STRUCT + n
    size_t   stride;           // Total singleton stride
    size_t   stream1Stride;    // Hot primitive stream stride
    size_t   stream2Stride;    // Secondary struct stream stride
    uint32_t count;            // Number of fields
    Field   *items;            // Array of field descriptors
} Fields;

// Compatibility aliases
typedef Fields Construct;
typedef Field  ConstructField;
typedef Field  StructField;

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

// Lookup and metadata inspection
const Fields *Fields_get(uint32_t generic);
size_t   Fields_stride(uint32_t generic);
uint32_t Fields_count(uint32_t generic);
uint32_t Fields_fieldSize(uint32_t generic, size_t fieldIndex);
bool     Fields_isFieldStruct(uint32_t generic, size_t fieldIndex);

// Size resolver helper
size_t Fields_resolveSize(size_t val, bool *outIsStruct);

#endif
