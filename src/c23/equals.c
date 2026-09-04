#include "c23/equals.h"

#include <string.h>

#include "annotation/overview.h"
#include "nio/mem.h"
#include "oop/Class.h"
#include "relational/variable.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Equals (c23/equals.c)
 * ============================================================================
 * Relational equality: the C answer to Java `.equals()`. Identity first,
 * then Memory headers (type + length must match), then the Class schema
 * field-by-field so padding bytes never vote. Types with no registered
 * schema fall back to payload memcmp; foreign pointers prove identity only.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - isEqual(a, b)                    : Deep, schema-driven equality
 *   - isEquallyNamed(v, nameA, nameB) : Registry form via Variable symbols
 * ============================================================================
 */

// Max inline-struct nesting followed before giving up (finite data, but
// schemas are runtime-registered — never trust them blindly).
#define EQUALS_MAX_DEPTH 16

static bool equalsFields(const uint8_t *a, const uint8_t *b, const Fields *schema,
                         size_t payloadLen, int depth) {
    if (!a || !b || !schema)
        return false;
    if (depth > EQUALS_MAX_DEPTH)
        return false;
    Field *items = (*schema).items;
    if (!items)
        return false;
    size_t n = (*schema).count;
    for (size_t i = 0; i < n; i++) {
        uint32_t off = items[i].offset;
        if (items[i].isStruct) {
            // size holds the sub-schema classId here, not a byte count.
            const Fields *sub = Fields_get(items[i].size);
            if (!sub || (*sub).count == 0)
                return false;
            size_t span = Fields_stride(items[i].size);
            if (span == 0 || (size_t) off + span > payloadLen)
                return false;
            if (!equalsFields(a + off, b + off, sub, span, depth + 1))
                return false;
            continue;
        }
        uint32_t sz = items[i].size;
        if ((size_t) off + (size_t) sz > payloadLen)
            return false;
        if (memcmp(a + off, b + off, (size_t) sz) != 0)
            return false;
    }
    return true;
}

// CORE FUNCTIONS

bool isEqual(const void *a, const void *b) {
    if (a == b)
        return true;
    if (!a || !b)
        return false;

    uint32_t ta = Memory_type((void*) a);
    uint32_t tb = Memory_type((void*) b);
    if (ta == 0 || tb == 0)
        return false;
    if (ta != tb)
        return false;

    size_t la = Memory_length((void*) a);
    size_t lb = Memory_length((void*) b);
    if (la != lb)
        return false;

    const Fields *schema = Fields_get(ta);
    if (schema && (*schema).count > 0 && (*schema).items) {
        const uint8_t *pa = (const uint8_t*) a;
        const uint8_t *pb = (const uint8_t*) b;
        return equalsFields(pa, pb, schema, la, 0);
    }

    const uint8_t *pa = (const uint8_t*) a;
    const uint8_t *pb = (const uint8_t*) b;
    return memcmp(pa, pb, la) == 0;
}

bool isEquallyNamed(Variable *v, const char *nameA, const char *nameB) {
    if (!v || !nameA || !nameB)
        return false;
    int32_t idA = Variable_getId(v, nameA);
    int32_t idB = Variable_getId(v, nameB);
    if (idA < 0 || idB < 0)
        return false;
    if (Variable_getClassId(v, idA) != Variable_getClassId(v, idB))
        return false;
    const void *pa = (const void*) Variable_getPointer(v, idA);
    const void *pb = (const void*) Variable_getPointer(v, idB);
    return isEqual(pa, pb);
}
