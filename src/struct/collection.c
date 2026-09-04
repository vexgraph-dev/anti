#include "struct/collection.h"

#include <string.h>

#include "nio/mem.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Collection (struct/collection.c)
 * LEVEL: L2 — Behavior (container behavior API)
 * ============================================================================
 * the Collection metadata struct, ported from
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - Collection_type(c)
 *   - Collection_size(c)
 *   - Collection_length(c)
 *   - Collection_elementClassId(c)
 *   - Collection_keyClassId(c)
 *   - Collection_stride(c)
 *   - Collection_valClassId(c)
 *   - Collection_capacity(c)
 *   - Collection_head(c)
 *   - Collection_dataBuffer(c)
 *   - Collection_readSlot(c, index)
 *   - Collection_writeSlot(c, index, value)
 *   - Collection_readSlotUnsafe(c, index)
 *   - Collection_writeSlotUnsafe(c, index, value)
 *
 * Getters:
 *   - Collection_isEmpty(c)
 * ============================================================================
 */


// collection.c — Collection port (Legacy: struct/Collection.java).

static uint64_t readSlotAt(const uint8_t *data, size_t stride) {
    switch (stride) {
        case 1:  return (uint64_t)(*(const uint8_t*) data);
        case 2:  return (uint64_t)(*(const uint16_t*) data);
        case 4:  return (uint64_t)(*(const uint32_t*) data);
        default: return *(const uint64_t*) data;
    }
}

static void writeSlotAt(uint8_t *data, size_t stride, uint64_t value) {
    switch (stride) {
        case 1:  *(uint8_t*) data = (uint8_t)value;   break;
        case 2:  *(uint16_t*) data = (uint16_t)value; break;
        case 4:  *(uint32_t*) data = (uint32_t)value; break;
        default: *(uint64_t*) data = value;           break;
    }
}

uint32_t Collection_type(Collection *c) {
    if (!c) return 0;
    return Memory_type(c);
}

uint32_t Collection_size(Collection *c) {
    if (!c) return 0;
    return (*c).activeCount;
}

uint32_t Collection_length(Collection *c) {
    return Collection_size(c);
}

bool Collection_isEmpty(Collection *c) {
    return Collection_size(c) == 0;
}

uint32_t Collection_elementClassId(Collection *c) {
    if (!c) return 0;
    return (*c).elementClass;
}

uint32_t Collection_keyClassId(Collection *c) {
    return Collection_elementClassId(c);
}

uint32_t Collection_stride(Collection *c) {
    if (!c) return 0;
    return (*c).stride;
}

uint32_t Collection_valClassId(Collection *c) {
    return Collection_stride(c);
}

uint32_t Collection_capacity(Collection *c) {
    if (!c) return 0;
    return (*c).capacity;
}

uint32_t Collection_head(Collection *c) {
    if (!c) return 0;
    return (*c).head;
}

uint8_t *Collection_dataBuffer(Collection *c) {
    if (!c) return nullptr;
    return (*c).data;
}

uint64_t Collection_readSlot(Collection *c, size_t index) {
    if (!c) return 0;
    uint8_t *slot = (*c).data + index * (*c).stride;
    return readSlotAt(slot, (*c).stride);
}

void Collection_writeSlot(Collection *c, size_t index, uint64_t value) {
    if (!c) return;
    uint8_t *slot = (*c).data + index * (*c).stride;
    writeSlotAt(slot, (*c).stride, value);
}

uint64_t Collection_readSlotUnsafe(Collection *c, size_t index) {
    uint8_t *slot = (*c).data + index * (*c).stride;
    return readSlotAt(slot, (*c).stride);
}

void Collection_writeSlotUnsafe(Collection *c, size_t index, uint64_t value) {
    uint8_t *slot = (*c).data + index * (*c).stride;
    writeSlotAt(slot, (*c).stride, value);
}
