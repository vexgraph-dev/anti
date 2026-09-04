#include "struct/set.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "util/arrays.h"
#include "util/hash.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Set (struct/set.c)
 * LEVEL: L2 — Behavior (container behavior API)
 * ============================================================================
 * the Set class, ported from struct/Set.java.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Set_2(element_class, capacity)
 *   - Set_1(element_class)
 *
 * Setters:
 *   - Set_free(set)
 *   - Set_add(set, element)
 *   - Set_contains(set, element)
 *   - Set_remove(set, element)
 *   - Set_toList(set)
 *   - Set_toSortedList(set)
 *   - Set_isEmpty(set)
 *   - Set_size(set)
 *   - Set_capacity(set)
 *   - Set_elementClassId(set)
 *   - Set_dataBuffer(set)
 * ============================================================================
 */


// set.c — Set port (Legacy: struct/Set.java).

static const size_t SLOT_SIZE = 24;
static const size_t DEFAULT_CAPACITY = 16;
static const size_t LOAD_DIVISOR = 4; // 0.75 load factor
static const uint64_t STATE_EMPTY = 0;
static const uint64_t STATE_OCCUPIED = 1;
static const uint64_t STATE_DELETED = 2;

static Collection *asCollection(Set *set) {
    return (Collection*) set;
}

static int isReferenceClass(uint32_t classId) {
    return classId == ID_STRING || classId >= ID_LIST;
}

static uint64_t computeHash(uint32_t elementClass, uint64_t element) {
    if (element == 0) return 0;
    if (isReferenceClass(elementClass) && element >= 4096u) {
        uint32_t inspected = Memory_type((void*) (uintptr_t)element);
        if (inspected != 0) {
            size_t len = Memory_length((void*) (uintptr_t)element);
            if (len > 0)
                return Hash_fnv1a64((const uint8_t*) (uintptr_t)element, len);
        }
    }
    return Hash_murmur3Mix64(element);
}

static int elementsEqual(uint32_t elementClass, uint64_t e1, uint64_t e2) {
    if (e1 == e2) return 1;
    if (e1 == 0 || e2 == 0) return 0;
    if (isReferenceClass(elementClass) && e1 >= 4096u && e2 >= 4096u) {
        void *p1 = (void*) (uintptr_t)e1;
        void *p2 = (void*) (uintptr_t)e2;
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

static uint64_t slotState(uint8_t *slot) {
    return *(uint64_t*) (slot + 16);
}

static void rehash(Collection *c, size_t newCap) {
    size_t oldCap = (*c).capacity;
    uint8_t *oldData = (*c).data;
    size_t bytes = newCap * SLOT_SIZE;
    uint32_t bufType = Type_make(FORM_ARRAY, ID_SET);
    uint8_t *newData = (uint8_t*) Memory_alloc(bufType, bytes);
    if (!newData) return;
    memset(newData, 0, bytes);

    size_t mask = newCap - 1;
    for (size_t i = 0; i < oldCap; i++) {
        uint8_t *slot = slotAt(c, i);
        if (slotState(slot) == STATE_OCCUPIED) {
            uint64_t elem = *(uint64_t*) slot;
            uint64_t hash = *(uint64_t*) (slot + 8);

            size_t idx = (size_t)(hash & mask);
            while (slotState(newData + idx * SLOT_SIZE) == STATE_OCCUPIED)
                idx = (idx + 1) & mask;
            uint8_t *target = newData + idx * SLOT_SIZE;
            *(uint64_t*) target = elem;
            *(uint64_t*) (target + 8) = hash;
            *(uint64_t*) (target + 16) = STATE_OCCUPIED;
        }
    }

    Memory_free(oldData);
    (*c).data = newData;
    (*c).capacity = (uint32_t)newCap;
}

Set *Set_2(uint32_t elementClass, size_t capacity) {
    size_t cap = capacity == 0 ? DEFAULT_CAPACITY : highestOneBit(capacity);
    if (cap < 4) cap = 4;

    Set *set = (Set*) Memory_alloc(TYPE_SET, sizeof(Set));
    if (!set) return nullptr;

    Collection *c = asCollection(set);
    (*c).typeId = TYPE_SET;
    (*c).activeCount = 0;
    (*c).elementClass = elementClass;
    (*c).stride = 0;
    (*c).capacity = (uint32_t)cap;
    (*c).head = 0;

    size_t bytes = cap * SLOT_SIZE;
    uint32_t bufType = Type_make(FORM_ARRAY, ID_SET);
    (*c).data = (uint8_t*) Memory_alloc(bufType, bytes);
    if (!(*c).data) {
        Memory_free(set);
        return nullptr;
    }
    memset((*c).data, 0, bytes);
    return set;
}

void Set_free(Set *set) {
    if (!set) return;
    Collection *c = asCollection(set);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(set);
}

int Set_add(Set *set, uint64_t element) {
    if (!set) return 0;
    Collection *c = asCollection(set);

    size_t load = (*c).capacity - (*c).capacity / LOAD_DIVISOR;
    if ((*c).activeCount >= load)
        rehash(c, (*c).capacity * 2);

    uint64_t hash = computeHash((*c).elementClass, element);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);
    size_t firstDeleted = SIZE_MAX;

    while (true) {
        uint8_t *slot = slotAt(c, idx);
        uint64_t st = slotState(slot);

        if (st == STATE_EMPTY) {
            size_t target = firstDeleted != SIZE_MAX ? firstDeleted : idx;
            uint8_t *tslot = slotAt(c, target);
            *(uint64_t*) tslot = element;
            *(uint64_t*) (tslot + 8) = hash;
            *(uint64_t*) (tslot + 16) = STATE_OCCUPIED;
            (*c).activeCount++;
            return 1;
        } else if (st == STATE_DELETED) {
            if (firstDeleted == SIZE_MAX)
                firstDeleted = idx;
        } else if (st == STATE_OCCUPIED) {
            if (*(uint64_t*) (slot + 8) == hash
                && elementsEqual((*c).elementClass, *(uint64_t*) slot, element))
                return 0;
        }
        idx = (idx + 1) & mask;
    }
}

bool Set_contains(Set *set, uint64_t element) {
    if (!set) return false;
    Collection *c = asCollection(set);
    if ((*c).capacity == 0) return false;

    uint64_t hash = computeHash((*c).elementClass, element);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);

    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slotAt(c, idx);
        uint64_t st = slotState(slot);
        if (st == STATE_EMPTY) return false;
        if (st == STATE_OCCUPIED
            && *(uint64_t*) (slot + 8) == hash
            && elementsEqual((*c).elementClass, *(uint64_t*) slot, element))
            return true;
        idx = (idx + 1) & mask;
    }
    return false;
}

int Set_remove(Set *set, uint64_t element) {
    if (!set) return 0;
    Collection *c = asCollection(set);
    if ((*c).capacity == 0) return 0;

    uint64_t hash = computeHash((*c).elementClass, element);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);

    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slotAt(c, idx);
        uint64_t st = slotState(slot);
        if (st == STATE_EMPTY) return 0;
        if (st == STATE_OCCUPIED
            && *(uint64_t*) (slot + 8) == hash
            && elementsEqual((*c).elementClass, *(uint64_t*) slot, element)) {
            *(uint64_t*) (slot + 16) = STATE_DELETED;
            (*c).activeCount--;
            return 1;
        }
        idx = (idx + 1) & mask;
    }
    return 0;
}

List *Set_toList(Set *set) {
    if (!set) return nullptr;
    Collection *c = asCollection(set);
    List *list = List((*c).elementClass, (*c).activeCount);
    if (!list) return nullptr;

    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slotAt(c, i);
        if (slotState(slot) == STATE_OCCUPIED)
            List_add(list, *(uint64_t*) slot);
    }
    return list;
}

List *Set_toSortedList(Set *set) {
    List *list = Set_toList(set);
    if (!list) return nullptr;
    if (List_size(list) > 1) {
        size_t stride = List_stride(list);
        uint8_t *buf = List_dataBuffer(list);
        size_t n = List_size(list);
        if (stride == 4)
            Arrays_sortInt((int32_t*) buf, n);
        else if (stride == 8)
            Arrays_sortLong((int64_t*) buf, n);
    }
    return list;
}

bool Set_isEmpty(Set *set) {
    return Collection_isEmpty(asCollection(set));
}

size_t Set_size(Set *set) {
    return Collection_size(asCollection(set));
}

size_t Set_capacity(Set *set) {
    return Collection_capacity(asCollection(set));
}

uint32_t Set_elementClassId(Set *set) {
    return Collection_elementClassId(asCollection(set));
}

uint8_t *Set_dataBuffer(Set *set) {
    return Collection_dataBuffer(asCollection(set));
}
Set *Set_1(uint32_t element_class) {
    return Set_2(element_class, 16);
}
