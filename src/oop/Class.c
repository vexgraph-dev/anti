#include "oop/Class.h"

#include <stdarg.h>
#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * CLASS: Class (oop/Class.c)
 * LEVEL: L1 — File Metadata (class schema registry)
 * ============================================================================
 * Dynamic Class Schema Engine (Legacy: oop/Fields, now Class).
 *
 * STRUCT FIELDS (Mirroring oop/Class.h):
 * ----------------------------------------------------------------------------
 *   Class {
 *     uint32_t genericId; // ID_CUSTOM_STRUCT + n
 *     size_t stride; // Total singleton stride
 *     size_t stream1Stride; // Hot primitive stream stride
 *     size_t stream2Stride; // Secondary struct stream stride
 *     uint32_t count; // Number of fields
 *     Field *items; // Array of field descriptors
 *   }
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Fields_create(size_t[])
 *
 * Core Functions:
 *   - Class_createNamed(count, ...)
 *   - Class_fieldName(generic, fieldIndex)
 *   - Class_fieldIndex(generic, fieldName)
 *   - Fields_stride(generic)
 *   - Fields_count(generic)
 *   - Fields_fieldSize(generic, fieldIndex)
 *   - Fields_resolveSize(val, outIsStruct)
 *
 * Getters:
 *   - Fields_get(generic)
 *   - Fields_isFieldStruct(generic, fieldIndex)
 * ============================================================================
 */


// fields.c — Dynamic Fields Schema Engine.
//
// Manages the global registry of Fields schemas, field sizing, and dual-stream
// offset calculations.

#define MAX_FIELDS 65000

static Fields schemas[MAX_FIELDS];
static uint32_t nextFieldsId = 1;

static int indexOf(uint32_t generic) {
    if (generic < ID_CUSTOM_STRUCT)
        return -1;
    int32_t index = (int32_t)(generic - ID_CUSTOM_STRUCT);
    if (index < 0 || index >= (int32_t)MAX_FIELDS)
        return -1;
    return index;
}

size_t Fields_resolveSize(size_t val, bool *outIsStruct) {
    *outIsStruct = false;
    if (val >= ID_CUSTOM_STRUCT) {
        *outIsStruct = true;
        return Fields_stride((uint32_t)val);
    }
    switch (val) {
        case ID_BYTE:    return 1;
        case ID_SHORT:   return 2;

        case ID_INT:
        case ID_FLOAT:   return 4;

        case ID_STRING:
        case ID_VEC2:
        case ID_LONG:
        case ID_DOUBLE:  return 8;

        case ID_VEC3:    *outIsStruct = true; return 12;
        case ID_VEC4:    *outIsStruct = true; return 16;
        case ID_MAT4:    *outIsStruct = true; return 64;
        default:
            if (val > 8) {
                *outIsStruct = true;
            }
            return val;
    }
}

// Natural alignment for a field of the given byte size: primitives align to
// their own width (capped at 8), compound fields to their widest component
// class. Every cursor in defineInto rounds up to this before laying a field
// down, and strides round to MAX_FIELD_ALIGN so array elements keep every
// 8-byte field aligned.
#define MAX_FIELD_ALIGN 8u

static size_t alignOf(size_t size) {
    if (size >= MAX_FIELD_ALIGN)
        return MAX_FIELD_ALIGN;

    if (size >= 4)
        return 4;

    if (size >= 2)
        return 2;

    return 1;
}

static size_t roundUp(size_t val, size_t align) {
    return (val + align - 1) & ~(align - 1);
}

static int defineInto(Fields *s, const size_t *sizesOrClasses, size_t count) {
    if (!sizesOrClasses || count == 0)
        return 0;
    if ((*s).items) {
        Memory_free((*s).items);
        (*s).items = nullptr;
    }

    Field *items = Memory_alloc(
        Type_make(FORM_ARRAY, ID_STRIDE), count * sizeof(Field));
    if (!items)
        return 0;

    for (size_t i = 0; i < count; i++)
        (*items[i].name) = '\0';

    size_t unifiedOffset = 0;
    size_t s1Offset = 0;
    size_t s2Offset = 0;

    for (size_t i = 0; i < count; i++) {
        size_t rawVal = sizesOrClasses[i];
        bool isS = false;
        size_t size = Fields_resolveSize(rawVal, &isS);
        size_t align = alignOf(size);

        items[i].size = (uint32_t)rawVal;
        items[i].isStruct = isS;

        unifiedOffset = roundUp(unifiedOffset, align);
        items[i].offset = (uint32_t)unifiedOffset;
        unifiedOffset += size;

        if (!isS) {
            s1Offset = roundUp(s1Offset, align);
            items[i].stream1Offset = (uint32_t)s1Offset;
            items[i].stream2Offset = 0;
            s1Offset += size;
        } else {
            s2Offset = roundUp(s2Offset, align);
            items[i].stream1Offset = 0;
            items[i].stream2Offset = (uint32_t)s2Offset;
            s2Offset += size;
        }
    }

    // Tail-pad so element N's aligned fields stay aligned at array stride.
    (*s).stride = roundUp(unifiedOffset, MAX_FIELD_ALIGN);
    (*s).stream1Stride = roundUp(s1Offset, MAX_FIELD_ALIGN);
    (*s).stream2Stride = roundUp(s2Offset, MAX_FIELD_ALIGN);
    (*s).count = (uint32_t)count;
    (*s).items = items;
    return 1;
}

Fields *Fields_create(const size_t *sizesOrClasses, size_t count) {
    if (nextFieldsId >= MAX_FIELDS)
        return nullptr;
    uint32_t generic = ID_CUSTOM_STRUCT + nextFieldsId;
    int index = indexOf(generic);
    if (index < 0)
        return nullptr;
    schemas[index].genericId = generic;
    if (!defineInto(&schemas[index], sizesOrClasses, count))
        return nullptr;
    nextFieldsId++;
    return &schemas[index];
}

// Named variant — mirrors defineInto but also stores field names for spotlight.
static int defineIntoNamed(Fields *s, const size_t *sizesOrClasses, const char **names, size_t count) {
    if (!sizesOrClasses || !names || count == 0)
        return 0;
    if ((*s).items) {
        Memory_free((*s).items);
        (*s).items = nullptr;
    }
    Field *items = Memory_alloc(
        Type_make(FORM_ARRAY, ID_STRIDE), count * sizeof(Field));
    if (!items)
        return 0;
    for (size_t i = 0; i < count; i++)
        (*items[i].name) = '\0';
    size_t unifiedOffset = 0;
    size_t s1Offset = 0;
    size_t s2Offset = 0;
    for (size_t i = 0; i < count; i++) {
        size_t rawVal = sizesOrClasses[i];
        const char *fieldName = names[i];
        if (!fieldName)
            fieldName = "";
        bool isS = false;
        size_t size = Fields_resolveSize(rawVal, &isS);
        size_t align = alignOf(size);
        items[i].size = (uint32_t)rawVal;
        items[i].isStruct = isS;
        // name — truncate to 31 chars, NUL-terminate
        size_t nLen = strlen(fieldName);
        if (nLen >= 32)
            nLen = 31;
        memcpy(items[i].name, fieldName, nLen);
        items[i].name[nLen] = '\0';
        unifiedOffset = roundUp(unifiedOffset, align);
        items[i].offset = (uint32_t)unifiedOffset;
        unifiedOffset += size;
        if (!isS) {
            s1Offset = roundUp(s1Offset, align);
            items[i].stream1Offset = (uint32_t)s1Offset;
            items[i].stream2Offset = 0;
            s1Offset += size;
        } else {
            s2Offset = roundUp(s2Offset, align);
            items[i].stream1Offset = 0;
            items[i].stream2Offset = (uint32_t)s2Offset;
            s2Offset += size;
        }
    }
    (*s).stride = roundUp(unifiedOffset, MAX_FIELD_ALIGN);
    (*s).stream1Stride = roundUp(s1Offset, MAX_FIELD_ALIGN);
    (*s).stream2Stride = roundUp(s2Offset, MAX_FIELD_ALIGN);
    (*s).count = (uint32_t)count;
    (*s).items = items;
    return 1;
}

Class *Class_createNamed(size_t count, ...) {
    if (count == 0 || count >= MAX_FIELDS)
        return nullptr;
    if (nextFieldsId >= MAX_FIELDS)
        return nullptr;
    size_t *classes = Memory_alloc(Type_make(FORM_ARRAY, ID_STRIDE), count * sizeof(size_t));
    const char **names = Memory_alloc(Type_make(FORM_ARRAY, ID_STRIDE), count * sizeof(char*));
    if (!classes || !names) {
        if (classes) Memory_free(classes);
        if (names) Memory_free(names);
        return nullptr;
    }
    va_list ap;
    va_start(ap, count);
    for (size_t i = 0; i < count; i++) {
        // first of pair: classId or Class* — passed as size_t (promoted)
        size_t klass = va_arg(ap, size_t);
        const char *name = va_arg(ap, const char*);
        // edge: null name → empty, duplicate check later
        if (!name)
            name = "";
        // store raw class value as-is; Fields_resolveSize handles Class* via genericId
        // if caller passed Class* as pointer, it was cast to size_t — reinterpret
        // Detect pointer vs small int: if klass looks like heap address (> 0x10000) and
        // points to a valid Class generic, use its genericId
        if (klass > 0x10000) {
            // heuristic: try to interpret as Class* — check if it falls in schemas range
            // We cannot dereference arbitrary int, so probe via indexOf if it looks like genericId already
            // If klass is a Class* address, its genericId field is at offset 0
            // We peek safely only if klass is within our schemas arena? Instead require caller
            // to pass (size_t)Class_ptr — we extract genericId if it looks like pointer
            Class *maybe = (Class*) (uintptr_t) klass;
            // two-layer cap: hoist
            Class *c = maybe;
            // check if c is one of our schemas by scanning — O(N) but count small and only at define time
            bool found = false;
            for (size_t s = 0; s < nextFieldsId; s++) {
                if (&schemas[s] == c) {
                    klass = (*c).genericId;
                    found = true;
                    break;
                }
            }
            if (!found) {
                // not a known Class*, treat as raw size (edge: large classId like 0x20000 would also hit)
                // keep klass as-is — Fields_resolveSize will treat as size if not ID_CUSTOM_STRUCT
            }
        }
        classes[i] = klass;
        names[i] = name;
    }
    va_end(ap);
    // duplicate name check — edge case: two fields named same
    for (size_t i = 0; i < count; i++) {
        for (size_t j = i + 1; j < count; j++) {
            if (strcmp(names[i], names[j]) == 0) {
                Memory_free(classes);
                Memory_free(names);
                return nullptr;
            }
        }
        if (strlen(names[i]) == 0 || strlen(names[i]) >= 32) {
            Memory_free(classes);
            Memory_free(names);
            return nullptr;
        }
    }
    uint32_t generic = ID_CUSTOM_STRUCT + nextFieldsId;
    int index = indexOf(generic);
    if (index < 0) {
        Memory_free(classes);
        Memory_free(names);
        return nullptr;
    }
    schemas[index].genericId = generic;
    int ok = defineIntoNamed(&schemas[index], classes, names, count);
    Memory_free(classes);
    Memory_free(names);
    if (!ok)
        return nullptr;
    nextFieldsId++;
    return &schemas[index];
}

const char *Class_fieldName(uint32_t generic, size_t fieldIndex) {
    int index = indexOf(generic);
    if (index < 0)
        return nullptr;
    const Fields *s = &schemas[index];
    if (fieldIndex >= (*s).count)
        return nullptr;
    return (*s).items[fieldIndex].name;
}

int32_t Class_fieldIndex(uint32_t generic, const char *fieldName) {
    if (!fieldName)
        return -1;
    int index = indexOf(generic);
    if (index < 0)
        return -1;
    const Fields *s = &schemas[index];
    for (size_t i = 0; i < (*s).count; i++) {
        if (strcmp((*s).items[i].name, fieldName) == 0)
            return (int32_t) i;
    }
    return -1;
}

const Fields *Fields_get(uint32_t generic) {
    int index = indexOf(generic);
    if (index < 0)
        return nullptr;
    return &schemas[index];
}

size_t Fields_stride(uint32_t generic) {
    int index = indexOf(generic);
    if (index < 0)
        return 0;
    return schemas[index].stride;
}

uint32_t Fields_count(uint32_t generic) {
    int index = indexOf(generic);
    if (index < 0)
        return 0;
    return schemas[index].count;
}

uint32_t Fields_fieldSize(uint32_t generic, size_t fieldIndex) {
    int index = indexOf(generic);
    if (index < 0)
        return 0;
    const Fields *s = &schemas[index];
    if (fieldIndex >= (*s).count)
        return 0;
    return (*s).items[fieldIndex].size;
}

bool Fields_isFieldStruct(uint32_t generic, size_t fieldIndex) {
    int index = indexOf(generic);
    if (index < 0)
        return false;
    const Fields *s = &schemas[index];
    if (fieldIndex >= (*s).count)
        return false;
    return (*s).items[fieldIndex].isStruct;
}
