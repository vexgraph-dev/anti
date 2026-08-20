#include "struct/sparseset.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

// sparseset.c — SparseSet port (Legacy: struct/SparseSet.java).

static int32_t *allocate_ints(size_t count) {
    return (int32_t *)Memory_alloc(TYPE_INT_ARRAY, count * sizeof(int32_t));
}

SparseSet *SparseSet_allocate(size_t capacity, size_t max_entities, size_t stride) {
    SparseSet *set = (SparseSet *)Memory_alloc(Type_make(FORM_SINGLETON, ID_SPARSE_SET), sizeof(SparseSet));
    if (!set) return NULL;

    (*set).capacity = (int32_t)capacity;
    (*set).max_entities = (int32_t)max_entities;
    (*set).count = 0;
    (*set).stride = (int32_t)stride;

    (*set).dense = allocate_ints(capacity);
    if (!(*set).dense) {
        Memory_free(set);
        return NULL;
    }

    (*set).sparse = allocate_ints(max_entities);
    if (!(*set).sparse) {
        Memory_free((*set).dense);
        Memory_free(set);
        return NULL;
    }
    for (size_t i = 0; i < max_entities; i++)
        (*set).sparse[i] = -1;

    if (stride > 0) {
        size_t bytes = capacity * stride;
        uint32_t buf_type = Type_make(FORM_ARRAY, ID_SPARSE_SET);
        (*set).data = (uint8_t *)Memory_alloc(buf_type, bytes);
        if (!(*set).data) {
            Memory_free((*set).dense);
            Memory_free((*set).sparse);
            Memory_free(set);
            return NULL;
        }
    } else {
        (*set).data = NULL;
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
    return (size_t)(*set).max_entities;
}

bool SparseSet_contains(SparseSet *set, int32_t entity_id) {
    if (!set) return false;
    if (entity_id < 0 || entity_id >= (*set).max_entities)
        return false;
    return (*set).sparse[entity_id] != -1;
}

uint8_t *SparseSet_add(SparseSet *set, int32_t entity_id) {
    if (!set) return NULL;
    if (entity_id < 0 || entity_id >= (*set).max_entities)
        return NULL;

    int32_t dense_index = (*set).sparse[entity_id];
    if (dense_index != -1) {
        if ((*set).stride > 0)
            return (*set).data + (size_t)dense_index * (size_t)(*set).stride;
        return (*set).data;
    }

    if ((*set).count >= (*set).capacity)
        return NULL;

    int32_t count = (*set).count;
    (*set).dense[count] = entity_id;
    (*set).sparse[entity_id] = count;
    (*set).count = count + 1;

    if ((*set).stride > 0)
        return (*set).data + (size_t)count * (size_t)(*set).stride;
    return (*set).data;
}

void SparseSet_remove(SparseSet *set, int32_t entity_id) {
    if (!set) return;
    if (entity_id < 0 || entity_id >= (*set).max_entities)
        return;

    int32_t dense_index = (*set).sparse[entity_id];
    if (dense_index == -1)
        return;

    int32_t count = (*set).count - 1;
    int32_t last_entity = (*set).dense[count];

    if (dense_index != count) {
        (*set).dense[dense_index] = last_entity;
        (*set).sparse[last_entity] = dense_index;
        if ((*set).stride > 0) {
            uint8_t *dest = (*set).data + (size_t)dense_index * (size_t)(*set).stride;
            uint8_t *src = (*set).data + (size_t)count * (size_t)(*set).stride;
            memcpy(dest, src, (size_t)(*set).stride);
        }
    }

    (*set).sparse[entity_id] = -1;
    (*set).count = count;
}

uint8_t *SparseSet_get(SparseSet *set, int32_t entity_id) {
    if (!set) return NULL;
    if (entity_id < 0 || entity_id >= (*set).max_entities)
        return NULL;

    int32_t dense_index = (*set).sparse[entity_id];
    if (dense_index == -1)
        return NULL;
    if ((*set).stride == 0)
        return (uint8_t *)set; // non-NULL presence sentinel (legacy returns ptr)

    return (*set).data + (size_t)dense_index * (size_t)(*set).stride;
}

const int32_t *SparseSet_denseEntities(SparseSet *set) {
    if (!set) return NULL;
    return (*set).dense;
}

const uint8_t *SparseSet_denseData(SparseSet *set) {
    if (!set) return NULL;
    return (*set).data;
}