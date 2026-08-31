#ifndef OOP_FIELD_H
#define OOP_FIELD_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "nio/mem.h"

// oop/field.h — Field descriptor (one column in a Class).

typedef struct Field {
    char name[32];           // field name for spotlight search (VARIABLE_NAME_SIZE)
    uint32_t size;           // byte size or classId for isStruct
    uint32_t offset;         // unified singleton offset
    uint32_t stream1Offset;  // hot primitive stream offset
    uint32_t stream2Offset;  // nested struct stream offset
    bool isStruct;           // true if field is a compound sub-struct
} Field;

#endif
