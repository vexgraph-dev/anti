#include "struct/map.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "util/hash.h"

// map.c — Map port (Legacy: struct/Map.java).

static const size_t SLOT_SIZE = 32;
static const size_t DEFAULT_CAPACITY = 16;
static const size_t LOAD_DIVISOR = 4; // 0.75 load factor = capacity - capacity/4
static const uint64_t STATE_EMPTY = 0;
static const uint64_t STATE_OCCUPIED = 1;
static const uint64_t STATE_DELETED = 2;

static Collection *as_collection(Map *map) {
    return (Collection *)map;
}

static int is_reference_class(uint32_t class_id) {
    return class_id == ID_STRING || class_id >= ID_LIST;
}

static uint64_t compute_hash(uint32_t key_class, uint64_t key) {
    if (key == 0) return 0;
    if (is_reference_class(key_class) && key >= 4096u) {
        uint32_t inspected = Memory_type((void *)(uintptr_t)key);
        if (inspected != 0) {
            size_t len = Memory_length((void *)(uintptr_t)key);
            if (len > 0)
                return Hash_fnv1a64((const uint8_t *)(uintptr_t)key, len);
        }
    }
    return Hash_murmur3Mix64(key);
}

static int keys_equal(uint32_t key_class, uint64_t k1, uint64_t k2) {
    if (k1 == k2) return 1;
    if (k1 == 0 || k2 == 0) return 0;
    if (is_reference_class(key_class) && k1 >= 4096u && k2 >= 4096u) {
        void *p1 = (void *)(uintptr_t)k1;
        void *p2 = (void *)(uintptr_t)k2;
        uint32_t t1 = Memory_type(p1);
        uint32_t t2 = Memory_type(p2);
        if (t1 != 0 && t1 == t2) {
            size_t len1 = Memory_length(p1);
            size_t len2 = Memory_length(p2);
            if (len1 != len2) return 0;
            return memcmp(p1, p2, len1) == 0;
        }
    }
    return 0;
}

static size_t highest_one_bit(size_t n) {
    if (n <= 1) return 1;
    size_t v = n - 1;
    v |= v >> 1;
    v |= v >> 2;
    v |= v >> 4;
    v |= v >> 8;
    v |= v >> 16;
    v |= v >> 32;
    return v + 1;
}

static uint8_t *slot_at(Collection *c, size_t index) {
    return (*c).data + index * SLOT_SIZE;
}

static uint64_t slot_state(uint8_t *slot) {
    return *(uint64_t *)(slot + 24);
}

static void rehash(Collection *c, size_t new_cap) {
    size_t old_cap = (*c).capacity;
    uint8_t *old_data = (*c).data;
    size_t bytes = new_cap * SLOT_SIZE;
    uint32_t buf_type = Type_make(FORM_ARRAY, ID_MAP);
    uint8_t *new_data = (uint8_t *)Memory_alloc(buf_type, bytes);
    if (!new_data) return;
    memset(new_data, 0, bytes);

    size_t mask = new_cap - 1;
    for (size_t i = 0; i < old_cap; i++) {
        uint8_t *slot = slot_at(c, i);
        if (slot_state(slot) == STATE_OCCUPIED) {
            uint64_t key = *(uint64_t *)slot;
            uint64_t val = *(uint64_t *)(slot + 8);
            uint64_t hash = *(uint64_t *)(slot + 16);

            size_t idx = (size_t)(hash & mask);
            while (slot_state(new_data + idx * SLOT_SIZE) == STATE_OCCUPIED)
                idx = (idx + 1) & mask;
            uint8_t *target = new_data + idx * SLOT_SIZE;
            *(uint64_t *)target = key;
            *(uint64_t *)(target + 8) = val;
            *(uint64_t *)(target + 16) = hash;
            *(uint64_t *)(target + 24) = STATE_OCCUPIED;
        }
    }

    Memory_free(old_data);
    (*c).data = new_data;
    (*c).capacity = (uint32_t)new_cap;
}

Map *Map_allocate(uint32_t key_class, uint32_t val_class, size_t capacity) {
    size_t cap = capacity == 0 ? DEFAULT_CAPACITY : highest_one_bit(capacity);
    if (cap < 4) cap = 4;

    Map *map = (Map *)Memory_alloc(TYPE_MAP, sizeof(Map));
    if (!map) return NULL;

    Collection *c = as_collection(map);
    (*c).type_id = TYPE_MAP;
    (*c).active_count = 0;
    (*c).element_class = key_class;
    (*c).stride = val_class;
    (*c).capacity = (uint32_t)cap;
    (*c).head = 0;

    size_t bytes = cap * SLOT_SIZE;
    uint32_t buf_type = Type_make(FORM_ARRAY, ID_MAP);
    (*c).data = (uint8_t *)Memory_alloc(buf_type, bytes);
    if (!(*c).data) {
        Memory_free(map);
        return NULL;
    }
    memset((*c).data, 0, bytes);
    return map;
}

void Map_free(Map *map) {
    if (!map) return;
    Collection *c = as_collection(map);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(map);
}

void Map_put(Map *map, uint64_t key, uint64_t value) {
    if (!map) return;
    Collection *c = as_collection(map);

    size_t load = (*c).capacity - (*c).capacity / LOAD_DIVISOR;
    if ((*c).active_count >= load)
        rehash(c, (*c).capacity * 2);

    uint64_t hash = compute_hash((*c).element_class, key);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);
    size_t first_deleted = SIZE_MAX;

    while (true) {
        uint8_t *slot = slot_at(c, idx);
        uint64_t st = slot_state(slot);

        if (st == STATE_EMPTY) {
            size_t target = first_deleted != SIZE_MAX ? first_deleted : idx;
            uint8_t *tslot = slot_at(c, target);
            *(uint64_t *)tslot = key;
            *(uint64_t *)(tslot + 8) = value;
            *(uint64_t *)(tslot + 16) = hash;
            *(uint64_t *)(tslot + 24) = STATE_OCCUPIED;
            (*c).active_count++;
            return;
        } else if (st == STATE_DELETED) {
            if (first_deleted == SIZE_MAX)
                first_deleted = idx;
        } else if (st == STATE_OCCUPIED) {
            if (*(uint64_t *)(slot + 16) == hash
                && keys_equal((*c).element_class, *(uint64_t *)slot, key)) {
                *(uint64_t *)(slot + 8) = value;
                return;
            }
        }
        idx = (idx + 1) & mask;
    }
}

uint64_t Map_get(Map *map, uint64_t key) {
    if (!map) return 0;
    Collection *c = as_collection(map);
    if ((*c).capacity == 0) return 0;

    uint64_t hash = compute_hash((*c).element_class, key);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);

    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slot_at(c, idx);
        uint64_t st = slot_state(slot);
        if (st == STATE_EMPTY) return 0;
        if (st == STATE_OCCUPIED
            && *(uint64_t *)(slot + 16) == hash
            && keys_equal((*c).element_class, *(uint64_t *)slot, key))
            return *(uint64_t *)(slot + 8);
        idx = (idx + 1) & mask;
    }
    return 0;
}

bool Map_containsKey(Map *map, uint64_t key) {
    if (!map) return false;
    Collection *c = as_collection(map);
    if ((*c).capacity == 0) return false;

    uint64_t hash = compute_hash((*c).element_class, key);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);

    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slot_at(c, idx);
        uint64_t st = slot_state(slot);
        if (st == STATE_EMPTY) return false;
        if (st == STATE_OCCUPIED
            && *(uint64_t *)(slot + 16) == hash
            && keys_equal((*c).element_class, *(uint64_t *)slot, key))
            return true;
        idx = (idx + 1) & mask;
    }
    return false;
}

uint64_t Map_remove(Map *map, uint64_t key) {
    if (!map) return 0;
    Collection *c = as_collection(map);
    if ((*c).capacity == 0) return 0;

    uint64_t hash = compute_hash((*c).element_class, key);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);

    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slot_at(c, idx);
        uint64_t st = slot_state(slot);
        if (st == STATE_EMPTY) return 0;
        if (st == STATE_OCCUPIED
            && *(uint64_t *)(slot + 16) == hash
            && keys_equal((*c).element_class, *(uint64_t *)slot, key)) {
            uint64_t old_val = *(uint64_t *)(slot + 8);
            *(uint64_t *)(slot + 24) = STATE_DELETED;
            (*c).active_count--;
            return old_val;
        }
        idx = (idx + 1) & mask;
    }
    return 0;
}

Array *Map_keys(Map *map) {
    if (!map) return NULL;
    Collection *c = as_collection(map);
    Array *keys = Array_allocate((*c).element_class, (*c).active_count);
    if (!keys) return NULL;

    size_t out = 0;
    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slot_at(c, i);
        if (slot_state(slot) == STATE_OCCUPIED)
            Array_set(keys, out++, *(uint64_t *)slot);
    }
    return keys;
}

bool Map_isEmpty(Map *map) {
    return Collection_isEmpty(as_collection(map));
}

size_t Map_size(Map *map) {
    return Collection_size(as_collection(map));
}

size_t Map_capacity(Map *map) {
    return Collection_capacity(as_collection(map));
}

uint32_t Map_keyClassId(Map *map) {
    return Collection_keyClassId(as_collection(map));
}

uint32_t Map_valClassId(Map *map) {
    return Collection_valClassId(as_collection(map));
}

uint8_t *Map_dataBuffer(Map *map) {
    return Collection_dataBuffer(as_collection(map));
}