#ifndef OOP_STRUCT_H
#define OOP_STRUCT_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "c23/constructor.h"
#include "oop/Class.h"

// oop/struct.h — Dynamic Struct Instance & Allocation Engine.
//
// Fields *x = Fields(sizeof(Vec3), sizeof(Vec3), sizeof(Vec3), sizeof(int), sizeof(char*));
// void *y   = Struct(x);         // allocates singleton
// void *y   = Struct(x, amount); // allocates array

// Aliases
typedef Fields Struct;
typedef Fields StructDescriptor;

// =========================================================================
// ALLOCATIONS: Struct(x), Struct(x, amount), allocate, allocateArray
// =========================================================================

void *Struct_allocate(const Fields *fields);
void *Struct_allocateSingletonRaw(uint32_t generic);
void *Struct_allocateArrayFrom(const Fields *fields, size_t amount);
void *Struct_allocateArrayRaw(uint32_t generic, size_t amount);
void *Struct_allocateCoexistentFrom(const Fields *fields, size_t amount);
void *Struct_allocateCoexistentRaw(uint32_t generic, size_t amount);
void *Struct_allocateSOAFrom(const Fields *fields, size_t amount);
void *Struct_allocateSOARaw(uint32_t generic, size_t amount);

// Polymorphic Allocator (arity-dispatched via c23/constructor.h):
//   Struct(x)         -> allocates singleton
//   Struct(x, amount) -> allocates array of amount elements
#define Struct_1(...) Struct_allocateSingleton(__VA_ARGS__)
#define Struct_2(...) Struct_allocateArray(__VA_ARGS__)
#define Struct(...)   CONSTRUCTOR_DISPATCH(Struct, ##__VA_ARGS__)

#define allocate(fields) Struct_allocate(fields)
#define allocateArray(fields, amount) Struct_allocateArray(fields, amount)

// C11 Generic Selection helpers for Struct_allocate*
#define Struct_allocateSingleton(target) \
    _Generic((target), \
        const Fields*: Struct_allocate, \
        Fields*: Struct_allocate, \
        default: Struct_allocateSingletonRaw \
    )(target)

#define Struct_allocateArray(target, amount) \
    _Generic((target), \
        const Fields*: Struct_allocateArrayFrom, \
        Fields*: Struct_allocateArrayFrom, \
        default: Struct_allocateArrayRaw \
    )((target), (amount))

#define Struct_allocateCoexistent(target, amount) \
    _Generic((target), \
        const Fields*: Struct_allocateCoexistentFrom, \
        Fields*: Struct_allocateCoexistentFrom, \
        default: Struct_allocateCoexistentRaw \
    )((target), (amount))

#define Struct_allocateSOA(target, amount) \
    _Generic((target), \
        const Fields*: Struct_allocateSOAFrom, \
        Fields*: Struct_allocateSOAFrom, \
        default: Struct_allocateSOARaw \
    )((target), (amount))

// Legacy constructor and inspection bridges
Fields *Struct_constructArray(const uint32_t *fieldClasses, size_t fieldCount);
uint32_t Struct_construct(const uint32_t *fieldClasses, size_t fieldCount);
const Fields *Struct_get(uint32_t generic);
size_t   Struct_stride(uint32_t generic);
uint32_t Struct_fieldsCount(uint32_t generic);
uint32_t Struct_fieldClass(uint32_t generic, size_t fieldIndex);
bool     Struct_isFieldStruct(uint32_t generic, size_t fieldIndex);

// Free struct memory
void Struct_free(void *userPtr);

// =========================================================================
// FIELD ACCESSORS: getField, setField, getElement, setElement
// =========================================================================

// Direct pointer to a field inside a struct instance
void *Struct_field(void *ptr, size_t fieldIndex);
void *Struct_elementField(void *ptr, size_t elementIndex, size_t fieldIndex);

// Generic slot value get/set
uint64_t Struct_getField(void *ptr, size_t fieldIndex);
void Struct_setField(void *ptr, size_t fieldIndex, uint64_t value);
uint64_t Struct_getElement(void *ptr, size_t elementIndex, size_t fieldIndex);
void Struct_setElement(void *ptr, size_t elementIndex, size_t fieldIndex, uint64_t value);

#define getField(ptr, fieldIndex) Struct_getField((ptr), (fieldIndex))
#define setField(ptr, fieldIndex, val) Struct_setField((ptr), (fieldIndex), (val))

// Direct typed accessors for singletons
void Struct_setInt(void *ptr, size_t fieldIndex, int32_t value);
int32_t Struct_getInt(void *ptr, size_t fieldIndex);
void Struct_setLong(void *ptr, size_t fieldIndex, int64_t value);
int64_t Struct_getLong(void *ptr, size_t fieldIndex);
void Struct_setFloat(void *ptr, size_t fieldIndex, float value);
float Struct_getFloat(void *ptr, size_t fieldIndex);
void Struct_setDouble(void *ptr, size_t fieldIndex, double value);
double Struct_getDouble(void *ptr, size_t fieldIndex);
void Struct_setByte(void *ptr, size_t fieldIndex, int8_t value);
int8_t Struct_getByte(void *ptr, size_t fieldIndex);
void Struct_setShort(void *ptr, size_t fieldIndex, int16_t value);
int16_t Struct_getShort(void *ptr, size_t fieldIndex);
void Struct_setPointerField(void *ptr, size_t fieldIndex, uintptr_t value);
uintptr_t Struct_getPointerField(void *ptr, size_t fieldIndex);

// Array element accessors
void Struct_setIntElement(void *ptr, size_t elementIndex, size_t fieldIndex, int32_t value);
int32_t Struct_getIntElement(void *ptr, size_t elementIndex, size_t fieldIndex);
void Struct_setLongElement(void *ptr, size_t elementIndex, size_t fieldIndex, int64_t value);
int64_t Struct_getLongElement(void *ptr, size_t elementIndex, size_t fieldIndex);
void Struct_setFloatElement(void *ptr, size_t elementIndex, size_t fieldIndex, float value);
float Struct_getFloatElement(void *ptr, size_t elementIndex, size_t fieldIndex);
void Struct_setDoubleElement(void *ptr, size_t elementIndex, size_t fieldIndex, double value);
double Struct_getDoubleElement(void *ptr, size_t elementIndex, size_t fieldIndex);
void Struct_setByteElement(void *ptr, size_t elementIndex, size_t fieldIndex, int8_t value);
int8_t Struct_getByteElement(void *ptr, size_t elementIndex, size_t fieldIndex);
void Struct_setShortElement(void *ptr, size_t elementIndex, size_t fieldIndex, int16_t value);
int16_t Struct_getShortElement(void *ptr, size_t elementIndex, size_t fieldIndex);

// Access nested sub-struct instance pointer inside a Coexistent Array or Singleton
void *Struct_getNested(void *ptr, size_t elementIndex, size_t fieldIndex);

// Generic-explicit accessors
void Struct_setIntG(uint32_t generic, void *ptr, size_t fieldIndex, int32_t value);
int32_t Struct_getIntG(uint32_t generic, void *ptr, size_t fieldIndex);
void Struct_setLongG(uint32_t generic, void *ptr, size_t fieldIndex, int64_t value);
int64_t Struct_getLongG(uint32_t generic, void *ptr, size_t fieldIndex);
void Struct_setFloatG(uint32_t generic, void *ptr, size_t fieldIndex, float value);
float Struct_getFloatG(uint32_t generic, void *ptr, size_t fieldIndex);
void Struct_setDoubleG(uint32_t generic, void *ptr, size_t fieldIndex, double value);
double Struct_getDoubleG(uint32_t generic, void *ptr, size_t fieldIndex);
void Struct_setByteG(uint32_t generic, void *ptr, size_t fieldIndex, int8_t value);
int8_t Struct_getByteG(uint32_t generic, void *ptr, size_t fieldIndex);
void Struct_setShortG(uint32_t generic, void *ptr, size_t fieldIndex, int16_t value);
int16_t Struct_getShortG(uint32_t generic, void *ptr, size_t fieldIndex);

// Direct nested subfield accessors
void Struct_setNestedInt(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex, int32_t value);
int32_t Struct_getNestedInt(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex);
void Struct_setNestedFloat(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex, float value);
float Struct_getNestedFloat(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex);
void Struct_setNestedLong(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex, int64_t value);
int64_t Struct_getNestedLong(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex);
void Struct_setNestedDouble(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex, double value);
double Struct_getNestedDouble(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex);

#endif