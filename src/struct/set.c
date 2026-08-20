#include "struct/set.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "util/arrays.h"
#include "util/hash.h"

// set.c — Set port (Legacy: struct/Set.java).

static const size_t SLOT_SIZE = 24;
static const size_t DEFAULT_CAPACITY = 16;
static const size_t LOAD_DIVISOR = 4; // 0.75 load factor
static const uint64_t STATE_EMPTY = 0;
static const uint64_t STATE_OCCUPIED = 1;
static const uint64_t STATE_DELETED = 2;

static Collection *as_collection(Set *set) {
    return (Collection *)set;
}

static int is_reference_class(uint32_t class_id) {
    return class_id == ID_STRING || class_id >= ID_LIST;
}

static uint64_t compute_hash(uint32_t element_class, uint64_t element) {
    if (element == 0) return 0;
    if (is_reference_class(element_class) && element >= 4096u) {
        uint32_t inspected = Memory_type((void *)(uintptr_t)element);
        if (inspected != 0) {
            size_t len = Memory_length((void *)(uintptr_t)element);
            if (len > 0)
                return Hash_fnv1a64((const uint8_t *)(uintptr_t)element, len);
        }
    }
    return Hash_murmur3Mix64(element);
}

static int elements_equal(uint32_t element_class, uint64_t e1, uint64_t e2) {
    if (e1 == e2) return 1;
    if (e1 == 0 || e2 == 0) return 0;
    if (is_reference_class(element_class) && e1 >= 4096u && e2 >= 4096u) {
        void *p1 = (void *)(uintptr_t)e1;
        void *p2 = (void *)(uintptr_t)e2;
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
    return *(uint64_t *)(slot + 16);
}

static void rehash(Collection *c, size_t new_cap) {
    size_t old_cap = (*c).capacity;
    uint8_t *old_data = (*c).data;
    size_t bytes = new_cap * SLOT_SIZE;
    uint32_t buf_type = Type_make(FORM_ARRAY, ID_SET);
    uint8_t *new_data = (uint8_t *)Memory_alloc(buf_type, bytes);
    if (!new_data) return;
    memset(new_data, 0, bytes);

    size_t mask = new_cap - 1;
    for (size_t i = 0; i < old_cap; i++) {
        uint8_t *slot = slot_at(c, i);
        if (slot_state(slot) == STATE_OCCUPIED) {
            uint64_t elem = *(uint64_t *)slot;
            uint64_t hash = *(uint64_t *)(slot + 8);

            size_t idx = (size_t)(hash & mask);
            while (slot_state(new_data + idx * SLOT_SIZE) == STATE_OCCUPIED)
                idx = (idx + 1) & mask;
            uint8_t *target = new_data + idx * SLOT_SIZE;
            *(uint64_t *)target = elem;
            *(uint64_t *)(target + 8) = hash;
            *(uint64_t *)(target + 16) = STATE_OCCUPIED;
        }
    }

    Memory_free(old_data);
    (*c).data = new_data;
    (*c).capacity = (uint32_t)new_cap;
}

Set *Set_allocate(uint32_t element_class, size_t capacity) {
    size_t cap = capacity == 0 ? DEFAULT_CAPACITY : highest_one_bit(capacity);
    if (cap < 4) cap = 4;

    Set *set = (Set *)Memory_alloc(TYPE_SET, sizeof(Set));
    if (!set) return NULL;

    Collection *c = as_collection(set);
    (*c).type_id = TYPE_SET;
    (*c).active_count = 0;
    (*c).element_class = element_class;
    (*c).stride = 0;
    (*c).capacity = (uint32_t)cap;
    (*c).head = 0;

    size_t bytes = cap * SLOT_SIZE;
    uint32_t buf_type = Type_make(FORM_ARRAY, ID_SET);
    (*c).data = (uint8_t *)Memory_alloc(buf_type, bytes);
    if (!(*c).data) {
        Memory_free(set);
        return NULL;
    }
    memset((*c).data, 0, bytes);
    return set;
}

void Set_free(Set *set) {
    if (!set) return;
    Collection *c = as_collection(set);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(set);
}

int Set_add(Set *set, uint64_t element) {
    if (!set) return 0;
    Collection *c = as_collection(set);

    size_t load = (*c).capacity - (*c).capacity / LOAD_DIVISOR;
    if ((*c).active_count >= load)
        rehash(c, (*c).capacity * 2);

    uint64_t hash = compute_hash((*c).element_class, element);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);
    size_t first_deleted = SIZE_MAX;

    while (true) {
        uint8_t *slot = slot_at(c, idx);
        uint64_t st = slot_state(slot);

        if (st == STATE_EMPTY) {
            size_t target = first_deleted != SIZE_MAX ? first_deleted : idx;
            uint8_t *tslot = slot_at(c, target);
            *(uint64_t *)tslot = element;
            *(uint64_t *)(tslot + 8) = hash;
            *(uint64_t *)(tslot + 16) = STATE_OCCUPIED;
            (*c).active_count++;
            return 1;
        } else if (st == STATE_DELETED) {
            if (first_deleted == SIZE_MAX)
                first_deleted = idx;
        } else if (st == STATE_OCCUPIED) {
            if (*(uint64_t *)(slot + 8) == hash
                && elements_equal((*c).element_class, *(uint64_t *)slot, element))
                return 0;
        }
        idx = (idx + 1) & mask;
    }
}

bool Set_contains(Set *set, uint64_t element) {
    if (!set) return false;
    Collection *c = as_collection(set);
    if ((*c).capacity == 0) return false;

    uint64_t hash = compute_hash((*c).element_class, element);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);

    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slot_at(c, idx);
        uint64_t st = slot_state(slot);
        if (st == STATE_EMPTY) return false;
        if (st == STATE_OCCUPIED
            && *(uint64_t *)(slot + 8) == hash
            && elements_equal((*c).element_class, *(uint64_t *)slot, element))
            return true;
        idx = (idx + 1) & mask;
    }
    return false;
}

int Set_remove(Set *set, uint64_t element) {
    if (!set) return 0;
    Collection *c = as_collection(set);
    if ((*c).capacity == 0) return 0;

    uint64_t hash = compute_hash((*c).element_class, element);
    size_t mask = (*c).capacity - 1;
    size_t idx = (size_t)(hash & mask);

    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slot_at(c, idx);
        uint64_t st = slot_state(slot);
        if (st == STATE_EMPTY) return 0;
        if (st == STATE_OCCUPIED
            && *(uint64_t *)(slot + 8) == hash
            && elements_equal((*c).element_class, *(uint64_t *)slot, element)) {
            *(uint64_t *)(slot + 16) = STATE_DELETED;
            (*c).active_count--;
            return 1;
        }
        idx = (idx + 1) & mask;
    }
    return 0;
}

List *Set_toList(Set *set) {
    if (!set) return NULL;
    Collection *c = as_collection(set);
    List *list = List_allocate((*c).element_class, (*c).active_count);
    if (!list) return NULL;

    for (size_t i = 0; i < (*c).capacity; i++) {
        uint8_t *slot = slot_at(c, i);
        if (slot_state(slot) == STATE_OCCUPIED)
            List_add(list, *(uint64_t *)slot);
    }
    return list;
}

List *Set_toSortedList(Set *set) {
    List *list = Set_toList(set);
    if (!list) return NULL;
    if (List_size(list) > 1) {
        size_t stride = List_stride(list);
        uint8_t *buf = List_dataBuffer(list);
        size_t n = List_size(list);
        if (stride == 4)
            Arrays_sortInt((int32_t *)buf, n);
        else if (stride == 8)
            Arrays_sortLong((int64_t *)buf, n);
    }
    return list;
}

bool Set_isEmpty(Set *set) {
    return Collection_isEmpty(as_collection(set));
}

size_t Set_size(Set *set) {
    return Collection_size(as_collection(set));
}

size_t Set_capacity(Set *set) {
    return Collection_capacity(as_collection(set));
}

uint32_t Set_elementClassId(Set *set) {
    return Collection_elementClassId(as_collection(set));
}

uint8_t *Set_dataBuffer(Set *set) {
    return Collection_dataBuffer(as_collection(set));
}