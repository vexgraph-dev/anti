#include "struct/sparseset.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Sparseset (struct/sparseset.c)
 * ============================================================================
 * the SparseSet class, ported from struct/SparseSet.java.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - SparseSet_3(capacity, maxEntities, stride)
 *
 * Core Functions:
 *   - SparseSet_free(set)
 *   - SparseSet_count(set)
 *   - SparseSet_capacity(set)
 *   - SparseSet_maxEntities(set)
 *   - SparseSet_contains(set, entityId)
 *   - SparseSet_add(set, entityId)
 *   - SparseSet_remove(set, entityId)
 *   - SparseSet_denseEntities(set)
 *   - SparseSet_denseData(set)
 *
 * Getters:
 *   - SparseSet_get(set, entityId)
 * ============================================================================
 */


// sparseset.c — SparseSet port (Legacy: struct/SparseSet.java).

static int32_t *allocateInts(size_t count) {
    return (int32_t*) Memory_alloc(TYPE_INT_ARRAY, count * sizeof(int32_t));
}

SparseSet *SparseSet_3(size_t capacity, size_t maxEntities, size_t stride) {
    SparseSet *set = (SparseSet*) Memory_alloc(Type_make(FORM_SINGLETON, ID_SPARSE_SET), sizeof(SparseSet));
    if (!set) return nullptr;

    (*set).capacity = (int32_t)capacity;
    (*set).maxEntities = (int32_t)maxEntities;
    (*set).count = 0;
    (*set).stride = (int32_t)stride;

    (*set).dense = allocateInts(capacity);
    if (!(*set).dense) {
        Memory_free(set);
        return nullptr;
    }

    (*set).sparse = allocateInts(maxEntities);
    if (!(*set).sparse) {
        Memory_free((*set).dense);
        Memory_free(set);
        return nullptr;
    }
    for (size_t i = 0; i < maxEntities; i++)
        (*set).sparse[i] = -1;

    if (stride > 0) {
        size_t bytes = capacity * stride;
        uint32_t bufType = Type_make(FORM_ARRAY, ID_SPARSE_SET);
        (*set).data = (uint8_t*) Memory_alloc(bufType, bytes);
        if (!(*set).data) {
            Memory_free((*set).dense);
            Memory_free((*set).sparse);
            Memory_free(set);
            return nullptr;
        }
    } else {
        (*set).data = nullptr;
    }
    return set;
}

void SparseSet_free(SparseSet *set) {
    if (!set) return;
    Memory_free((*set).dense);
    Memory_free((*set).sparse);
    Memory_free((*set).data);
    Memory_free(set);
}

size_t SparseSet_count(SparseSet *set) {
    if (!set) return 0;
    return (size_t)(*set).count;
}

size_t SparseSet_capacity(SparseSet *set) {
    if (!set) return 0;
    return (size_t)(*set).capacity;
}

size_t SparseSet_maxEntities(SparseSet *set) {
    if (!set) return 0;
    return (size_t)(*set).maxEntities;
}

bool SparseSet_contains(SparseSet *set, int32_t entityId) {
    if (!set) return false;
    if (entityId < 0 || entityId >= (*set).maxEntities)
        return false;
    return (*set).sparse[entityId] != -1;
}

uint8_t *SparseSet_add(SparseSet *set, int32_t entityId) {
    if (!set) return nullptr;
    if (entityId < 0 || entityId >= (*set).maxEntities)
        return nullptr;

    int32_t denseIndex = (*set).sparse[entityId];
    if (denseIndex != -1) {
        if ((*set).stride > 0)
            return (*set).data + (size_t)denseIndex * (size_t)(*set).stride;
        return (*set).data;
    }

    if ((*set).count >= (*set).capacity)
        return nullptr;

    int32_t count = (*set).count;
    (*set).dense[count] = entityId;
    (*set).sparse[entityId] = count;
    (*set).count = count + 1;

    if ((*set).stride > 0)
        return (*set).data + (size_t)count * (size_t)(*set).stride;
    return (*set).data;
}

void SparseSet_remove(SparseSet *set, int32_t entityId) {
    if (!set) return;
    if (entityId < 0 || entityId >= (*set).maxEntities)
        return;

    int32_t denseIndex = (*set).sparse[entityId];
    if (denseIndex == -1)
        return;

    int32_t count = (*set).count - 1;
    int32_t lastEntity = (*set).dense[count];

    if (denseIndex != count) {
        (*set).dense[denseIndex] = lastEntity;
        (*set).sparse[lastEntity] = denseIndex;
        if ((*set).stride > 0) {
            uint8_t *dest = (*set).data + (size_t)denseIndex * (size_t)(*set).stride;
            uint8_t *src = (*set).data + (size_t)count * (size_t)(*set).stride;
            memcpy(dest, src, (size_t)(*set).stride);
        }
    }

    (*set).sparse[entityId] = -1;
    (*set).count = count;
}

uint8_t *SparseSet_get(SparseSet *set, int32_t entityId) {
    if (!set) return nullptr;
    if (entityId < 0 || entityId >= (*set).maxEntities)
        return nullptr;

    int32_t denseIndex = (*set).sparse[entityId];
    if (denseIndex == -1)
        return nullptr;
    if ((*set).stride == 0)
        return (uint8_t*) set; // non-nullptr presence sentinel (legacy returns ptr)

    return (*set).data + (size_t)denseIndex * (size_t)(*set).stride;
}

const int32_t *SparseSet_denseEntities(SparseSet *set) {
    if (!set) return nullptr;
    return (*set).dense;
}

const uint8_t *SparseSet_denseData(SparseSet *set) {
    if (!set) return nullptr;
    return (*set).data;
}
