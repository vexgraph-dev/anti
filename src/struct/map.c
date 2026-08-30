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

static Collection *asCollection(Map *map) {
    return (Collection *)map;
}

static int isReferenceClass(uint32_t classId) {
    return classId == ID_STRING || classId >= ID_LIST;
}

static uint64_t computeHash(uint32_t keyClass, uint64_t key) {
    if (key == 0) return 0;
    if (isReferenceClass(keyClass) && key >= 4096u) {
        uint32_t inspected = Memory_type((void *)(uintptr_t)key);
        if (inspected != 0) {
            size_t len = Memory_length((void *)(uintptr_t)key);
            if (len > 0)
                return Hash_fnv1a64((const uint8_t *)(uintptr_t)key, len);
        }
    }
    return Hash_murmur3Mix64(key);
}

static int keysEqual(uint32_t keyClass, uint64_t k1, uint64_t k2) {
    if (k1 == k2) return 1;
    if (k1 == 0 || k2 == 0) return 0;
    if (isReferenceClass(keyClass) && k1 >= 4096u && k2 >= 4096u) {
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

static size_t highestOneBit(size_t n) {
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

static uint8_t *slotAt(Collection *c, size_t index) {
    return (*c).data + index * SLOT_SIZE;
}

static uint64_t slotState(const uint8_t *slot) {
    return *(uint64_t *)(slot + 24);
}

static void rehash(Collection *c, size_t newCap) {
    size_t oldCap = (*c).capacity;
    uint8_t *oldData = (*c).data;
    size_t bytes = newCap * SLOT_SIZE;
    uint32_t bufType = Type_make(FORM_ARRAY, ID_MAP);
    uint8_t *newData = (uint8_t *)Memory_alloc(bufType, bytes);
    if (!newData) return;
    memset(newData, 0, bytes);

    size_t mask = newCap - 1;
    for (size_t i = 0; i < oldCap; i++) {
        uint8_t *slot = slotAt(c, i);
        if (slotState(slot) == STATE_OCCUPIED) {
            uint64_t key = *(uint64_t *)slot;
            uint64_t val = *(uint64_t *)(slot + 8);
            uint64_t hash = *(uint64_t *)(slot + 16);

            size_t idx = (size_t)(hash & mask);
            while (slotState(newData + idx * SLOT_SIZE) == STATE_OCCUPIED)
                idx = (idx + 1) & mask;
            uint8_t *target = newData + idx * SLOT_SIZE;
            *(uint64_t *)target = key;
            *(uint64_t *)(target + 8) = val;
            *(uint64_t *)(target + 16) = hash;
            *(uint64_t *)(target + 24) = STATE_OCCUPIED;
        }
    }

    Memory_free(oldData);
    (*c).data = newData;
    (*c).capacity = (uint32_t)newCap;
}

Map *Map_3(uint32_t keyClass, uint32_t valClass, size_t capacity) {
    size_t cap = capacity == 0 ? DEFAULT_CAPACITY : highestOneBit(capacity);
    if (cap < 4) cap = 4;

    Map *map = (Map *)Memory_alloc(TYPE_MAP, sizeof(Map));
    if (!map) return nullptr;

    Collection *c = asCollection(map);
    (*c).typeId = TYPE_MAP;
    (*c).activeCount = 0;
    (*c).elementClass = keyClass;
    (*c).stride = valClass;
    (*c).capacity = (uint32_t)cap;
    (*c).head = 0;

    size_t bytes = cap * SLOT_SIZE;
    uint32_t bufType = Type_make(FORM_ARRAY, ID_MAP);
    (*c).data = (uint8_t *)Memory_alloc(bufType, bytes);
    if (!(*c).data) {
        Memory_free(map);
        return nullptr;
    }
    memset((*c).data, 0, bytes);
    return map;
}

void Map_free(Map *map) {
    if (!map) return;
    Collection *c = asCollection(map);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(map);
}

void Map_put(Map *map, uint64_t key, uint64_t value) {
    if (!map) return;
    Collection *c = asCollection(map);

    size_t load = (*c).capacity - (*c).capacity / LOAD_DIVISOR;
    if ((*c).activeCount >= load)
        rehash(c, (*c).capacity * 2);

    uint64_t hash = computeHash((*c).elementClass, key);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);
    size_t firstDeleted = SIZE_MAX;

    while (true) {
        uint8_t *slot = slotAt(c, idx);
        uint64_t st = slotState(slot);

        if (st == STATE_EMPTY) {
            size_t target = firstDeleted != SIZE_MAX ? firstDeleted : idx;
            uint8_t *tslot = slotAt(c, target);
            *(uint64_t *)tslot = key;
            *(uint64_t *)(tslot + 8) = value;
            *(uint64_t *)(tslot + 16) = hash;
            *(uint64_t *)(tslot + 24) = STATE_OCCUPIED;
            (*c).activeCount++;
            return;
        } else if (st == STATE_DELETED) {
            if (firstDeleted == SIZE_MAX)
                firstDeleted = idx;
        } else if (st == STATE_OCCUPIED) {
            if (*(uint64_t *)(slot + 16) == hash
                && keysEqual((*c).elementClass, *(uint64_t *)slot, key)) {
                *(uint64_t *)(slot + 8) = value;
                return;
            }
        }
        idx = (idx + 1) & mask;
    }
}

uint64_t Map_get(Map *map, uint64_t key) {
    if (!map) return 0;
    Collection *c = asCollection(map);
    if ((*c).capacity == 0) return 0;

    uint64_t hash = computeHash((*c).elementClass, key);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);

    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slotAt(c, idx);
        uint64_t st = slotState(slot);
        if (st == STATE_EMPTY) return 0;
        if (st == STATE_OCCUPIED
            && *(uint64_t *)(slot + 16) == hash
            && keysEqual((*c).elementClass, *(uint64_t *)slot, key))
            return *(uint64_t *)(slot + 8);
        idx = (idx + 1) & mask;
    }
    return 0;
}

bool Map_containsKey(Map *map, uint64_t key) {
    if (!map) return false;
    Collection *c = asCollection(map);
    if ((*c).capacity == 0) return false;

    uint64_t hash = computeHash((*c).elementClass, key);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);

    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slotAt(c, idx);
        uint64_t st = slotState(slot);
        if (st == STATE_EMPTY) return false;
        if (st == STATE_OCCUPIED
            && *(uint64_t *)(slot + 16) == hash
            && keysEqual((*c).elementClass, *(uint64_t *)slot, key))
            return true;
        idx = (idx + 1) & mask;
    }
    return false;
}

uint64_t Map_remove(Map *map, uint64_t key) {
    if (!map) return 0;
    Collection *c = asCollection(map);
    if ((*c).capacity == 0) return 0;

    uint64_t hash = computeHash((*c).elementClass, key);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);

    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slotAt(c, idx);
        uint64_t st = slotState(slot);
        if (st == STATE_EMPTY) return 0;
        if (st == STATE_OCCUPIED
            && *(uint64_t *)(slot + 16) == hash
            && keysEqual((*c).elementClass, *(uint64_t *)slot, key)) {
            uint64_t oldVal = *(uint64_t *)(slot + 8);
            *(uint64_t *)(slot + 24) = STATE_DELETED;
            (*c).activeCount--;
            return oldVal;
        }
        idx = (idx + 1) & mask;
    }
    return 0;
}

Array *Map_keys(Map *map) {
    if (!map) return nullptr;
    Collection *c = asCollection(map);
    Array *keys = Array((*c).elementClass, (*c).activeCount);
    if (!keys) return nullptr;

    size_t out = 0;
    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slotAt(c, i);
        if (slotState(slot) == STATE_OCCUPIED)
            Array_set(keys, out++, *(uint64_t *)slot);
    }
    return keys;
}

bool Map_isEmpty(Map *map) {
    return Collection_isEmpty(asCollection(map));
}

size_t Map_size(Map *map) {
    return Collection_size(asCollection(map));
}

size_t Map_capacity(Map *map) {
    return Collection_capacity(asCollection(map));
}

uint32_t Map_keyClassId(Map *map) {
    return Collection_keyClassId(asCollection(map));
}

uint32_t Map_valClassId(Map *map) {
    return Collection_valClassId(asCollection(map));
}

uint8_t *Map_dataBuffer(Map *map) {
    return Collection_dataBuffer(asCollection(map));
}
Map *Map_2(uint32_t key_class, uint32_t val_class) {
    return Map_3(key_class, val_class, 16);
}
