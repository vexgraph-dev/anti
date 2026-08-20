#ifndef OOP_STRUCT_H
#define OOP_STRUCT_H

#include <stddef.h>
#include <stdint.h>

// oop/struct.h — the Struct class, ported from oop/Struct.java.
//
// Runtime-defined dynamic struct layouts. Calling Struct_construct with a list
// of field class ids returns a fresh generic id (ID_CUSTOM_STRUCT + n) whose
// stride is the packed sum of the field strides. Layouts are stored in a fixed
// registry; Stride_get consults it so collections of custom structs work like
// any other element class. Allocation supports singletons, AoS/SOA arrays, and
// pointer matrices (Level 1/2/3 in legacy terms).

// Allocate a new custom struct id for the given field class ids. Returns 0 if
// the registry is full or the field list is empty.
uint32_t Struct_construct(const uint32_t *field_classes, size_t field_count);

// (Re)define the layout for an existing custom struct id.
void Struct_define(uint32_t generic, const uint32_t *field_classes, size_t field_count);

// Byte stride of a custom struct id (0 if undefined).
size_t Struct_stride(uint32_t generic);

// Number of fields in a custom struct id (0 if undefined).
uint32_t Struct_fieldsCount(uint32_t generic);

// Field class id at index (0 if undefined/out of range).
uint32_t Struct_fieldClass(uint32_t generic, size_t field_index);

// --- ALLOCATION (Levels) ---

// Level 1: a single zeroed struct.
void *Struct_allocateSingleton(uint32_t generic);

// Level 2: an array of structs, AoS layout (same as legacy allocateArray).
void *Struct_allocateArray(uint32_t generic, size_t length);

// Level 2: an array of structs, SoA layout.
void *Struct_allocateSOA(uint32_t generic, size_t length);

// Level 3: a matrix (pointer array) of structs.
void *Struct_allocateMatrix(uint32_t generic, size_t length);

// Pointer at index in a matrix, and setter.
uintptr_t Struct_getPointer(void *user_ptr, size_t index);
void Struct_setPointer(void *user_ptr, size_t index, uintptr_t target);

// Free a struct singleton, array, SOA, or matrix.
void Struct_free(void *user_ptr);

// --- SINGLETON FIELD ACCESSORS (pointer form) ---
void Struct_setInt(void *ptr, size_t field_index, int32_t value);
int32_t Struct_getInt(void *ptr, size_t field_index);
void Struct_setLong(void *ptr, size_t field_index, int64_t value);
int64_t Struct_getLong(void *ptr, size_t field_index);
void Struct_setFloat(void *ptr, size_t field_index, float value);
float Struct_getFloat(void *ptr, size_t field_index);
void Struct_setDouble(void *ptr, size_t field_index, double value);
double Struct_getDouble(void *ptr, size_t field_index);
void Struct_setByte(void *ptr, size_t field_index, int8_t value);
int8_t Struct_getByte(void *ptr, size_t field_index);
void Struct_setShort(void *ptr, size_t field_index, int16_t value);
int16_t Struct_getShort(void *ptr, size_t field_index);
void Struct_setPointerField(void *ptr, size_t field_index, uintptr_t value);
uintptr_t Struct_getPointerField(void *ptr, size_t field_index);

// --- ARRAY FIELD ACCESSORS (element + field) ---
void Struct_setIntElement(void *ptr, size_t element_index, size_t field_index, int32_t value);
int32_t Struct_getIntElement(void *ptr, size_t element_index, size_t field_index);
void Struct_setLongElement(void *ptr, size_t element_index, size_t field_index, int64_t value);
int64_t Struct_getLongElement(void *ptr, size_t element_index, size_t field_index);
void Struct_setFloatElement(void *ptr, size_t element_index, size_t field_index, float value);
float Struct_getFloatElement(void *ptr, size_t element_index, size_t field_index);
void Struct_setDoubleElement(void *ptr, size_t element_index, size_t field_index, double value);
double Struct_getDoubleElement(void *ptr, size_t element_index, size_t field_index);
void Struct_setByteElement(void *ptr, size_t element_index, size_t field_index, int8_t value);
int8_t Struct_getByteElement(void *ptr, size_t element_index, size_t field_index);
void Struct_setShortElement(void *ptr, size_t element_index, size_t field_index, int16_t value);
int16_t Struct_getShortElement(void *ptr, size_t element_index, size_t field_index);

#endif