#include "oop/fields.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

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
        case ID_INT:     return 4;
        case ID_LONG:    return 8;
        case ID_FLOAT:   return 4;
        case ID_DOUBLE:  return 8;
        case ID_STRING:  return 8;
        case ID_VEC2:    return 8;
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

static int defineInto(Fields *s, const size_t *sizesOrClasses, size_t count) {
    if (!sizesOrClasses || count == 0)
        return 0;
    if ((*s).items) {
        Memory_free((*s).items);
        (*s).items = NULL;
    }

    Field *items = Memory_alloc(
        Type_make(FORM_ARRAY, ID_STRIDE), count * sizeof(Field));
    if (!items)
        return 0;

    size_t unifiedOffset = 0;
    size_t s1Offset = 0;
    size_t s2Offset = 0;

    for (size_t i = 0; i < count; i++) {
        size_t rawVal = sizesOrClasses[i];
        bool isS = false;
        size_t size = Fields_resolveSize(rawVal, &isS);

        items[i].size = (uint32_t)rawVal;
        items[i].isStruct = isS;
        items[i].offset = (uint32_t)unifiedOffset;
        unifiedOffset += size;

        if (!isS) {
            items[i].stream1Offset = (uint32_t)s1Offset;
            items[i].stream2Offset = 0;
            s1Offset += size;
        } else {
            items[i].stream1Offset = 0;
            items[i].stream2Offset = (uint32_t)s2Offset;
            s2Offset += size;
        }
    }

    (*s).stride = unifiedOffset;
    (*s).stream1Stride = s1Offset;
    (*s).stream2Stride = s2Offset;
    (*s).count = (uint32_t)count;
    (*s).items = items;
    return 1;
}

Fields *Fields_create(const size_t *sizesOrClasses, size_t count) {
    if (nextFieldsId >= MAX_FIELDS)
        return NULL;
    uint32_t generic = ID_CUSTOM_STRUCT + nextFieldsId;
    int index = indexOf(generic);
    if (index < 0)
        return NULL;
    schemas[index].genericId = generic;
    if (!defineInto(&schemas[index], sizesOrClasses, count))
        return NULL;
    nextFieldsId++;
    return &schemas[index];
}

const Fields *Fields_get(uint32_t generic) {
    int index = indexOf(generic);
    if (index < 0)
        return NULL;
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
