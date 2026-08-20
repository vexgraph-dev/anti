// relational/variable.c — Variable registry port (Legacy: variable/Variable.java).
//
// Open-addressing hash map keyed on the 4-word lowercase name; each 40-byte map
// slot holds the name words + a var id (-1 = empty). Slot 0..active_count-1 of
// the arena are the live rows.

#include "relational/variable.h"

#include <stdlib.h>
#include <string.h>

static void lowercase_pack(const char *name, size_t len, uint64_t words[4]) {
    for (int w = 0; w < 4; w++) {
        uint64_t word = 0;
        for (int i = 0; i < 8; i++) {
            size_t index = (size_t)w * 8 + (size_t)i;
            unsigned char b = 0;
            if (index < len) {
                b = (unsigned char)name[index];
                if (b >= 'A' && b <= 'Z')
                    b += 32;
            }
            word |= (uint64_t)b << (i * 8);
        }
        words[w] = word;
    }
}

static uint32_t hash_name(uint64_t l0, uint64_t l1, uint64_t l2, uint64_t l3) {
    uint64_t mix = l0 ^ (l1 >> 7) ^ (l2 << 9) ^ (l3 >> 13);
    mix ^= mix >> 32;
    return (uint32_t)mix;
}

static int32_t map_get(Variable *v, const uint64_t words[4]) {
    uint64_t *map = (uint64_t *)v->map;
    size_t cap = v->map_capacity;
    uint32_t index = hash_name(words[0], words[1], words[2], words[3]) % (uint32_t)cap;

    for (size_t i = 0; i < cap; i++) {
        uint64_t *slot = map + index * (VARIABLE_MAP_SLOT_SIZE / 8);
        int32_t stored_id = (int32_t)(slot[4] & 0xFFFFFFFFu);
        if (stored_id == -1)
            return -1;
        if (slot[0] == words[0] && slot[1] == words[1] && slot[2] == words[2] && slot[3] == words[3])
            return stored_id;
        index = (index + 1) % (uint32_t)cap;
    }
    return -1;
}

static void map_insert(Variable *v, const uint64_t words[4], int32_t var_id) {
    uint64_t *map = (uint64_t *)v->map;
    size_t cap = v->map_capacity;
    uint32_t index = hash_name(words[0], words[1], words[2], words[3]) % (uint32_t)cap;

    while (true) {
        uint64_t *slot = map + index * (VARIABLE_MAP_SLOT_SIZE / 8);
        int32_t stored_id = (int32_t)(slot[4] & 0xFFFFFFFFu);
        if (stored_id == -1 || stored_id == var_id) {
            slot[0] = words[0];
            slot[1] = words[1];
            slot[2] = words[2];
            slot[3] = words[3];
            slot[4] = (uint64_t)(uint32_t)var_id;
            return;
        }
        index = (index + 1) % (uint32_t)cap;
    }
}

static void map_rebuild(Variable *v) {
    memset(v->map, 0xFF, v->map_capacity * VARIABLE_MAP_SLOT_SIZE);
    for (size_t i = 0; i < v->active_count; i++) {
        uint64_t *slot = (uint64_t *)(v->arena + i * VARIABLE_SLOT_SIZE);
        uint64_t words[4] = {slot[0], slot[1], slot[2], slot[3]};
        map_insert(v, words, (int32_t)i);
    }
}

static bool map_resize(Variable *v) {
    size_t new_capacity = v->map_capacity * 2;
    uint8_t *new_map = malloc(new_capacity * VARIABLE_MAP_SLOT_SIZE);
    if (new_map == NULL)
        return false;
    memset(new_map, 0xFF, new_capacity * VARIABLE_MAP_SLOT_SIZE);

    uint64_t *old_map = (uint64_t *)v->map;
    size_t old_capacity = v->map_capacity;
    v->map = new_map;
    v->map_capacity = new_capacity;
    for (size_t i = 0; i < old_capacity; i++) {
        uint64_t *slot = old_map + i * (VARIABLE_MAP_SLOT_SIZE / 8);
        int32_t stored_id = (int32_t)(slot[4] & 0xFFFFFFFFu);
        if (stored_id != -1) {
            uint64_t words[4] = {slot[0], slot[1], slot[2], slot[3]};
            map_insert(v, words, stored_id);
        }
    }
    free(old_map);
    return true;
}

static uint8_t *slot_ptr(Variable *v, int32_t var_id) {
    return v->arena + (size_t)var_id * VARIABLE_SLOT_SIZE;
}

bool Variable_init(Variable *v) {
    memset(v, 0, sizeof(*v));
    v->capacity = VARIABLE_DEFAULT_CAPACITY;
    v->map_capacity = VARIABLE_DEFAULT_CAPACITY * 2;

    v->arena = malloc(v->capacity * VARIABLE_SLOT_SIZE);
    if (v->arena == NULL)
        return false;

    v->map = malloc(v->map_capacity * VARIABLE_MAP_SLOT_SIZE);
    if (v->map == NULL) {
        free(v->arena);
        v->arena = NULL;
        return false;
    }

    memset(v->map, 0xFF, v->map_capacity * VARIABLE_MAP_SLOT_SIZE);
    v->active = true;
    return true;
}

void Variable_shutdown(Variable *v) {
    if (!v->active)
        return;
    free(v->map);
    free(v->arena);
    v->map = NULL;
    v->arena = NULL;
    v->map_capacity = 0;
    v->capacity = 0;
    v->active_count = 0;
    v->active = false;
}

int32_t Variable_instant(Variable *v, const char *name, uint32_t class_id, uintptr_t target_pointer) {
    if (!v->active || name == NULL)
        return -1;

    size_t len = strlen(name);
    if (len > VARIABLE_NAME_SIZE)
        return -1;

    uint64_t words[4];
    lowercase_pack(name, len, words);

    int32_t existing = map_get(v, words);
    if (existing != -1) {
        uint64_t *slot = (uint64_t *)slot_ptr(v, existing);
        slot[4] = ((uint64_t)class_id << 32) | (slot[4] & 0xFFFFFFFFu);
        slot[5] = (uint64_t)target_pointer;
        return existing;
    }

    if (v->active_count >= v->capacity) {
        size_t new_capacity = v->capacity + VARIABLE_DEFAULT_CAPACITY;
        uint8_t *new_arena = malloc(new_capacity * VARIABLE_SLOT_SIZE);
        if (new_arena == NULL)
            return -1;
        memcpy(new_arena, v->arena, v->active_count * VARIABLE_SLOT_SIZE);
        free(v->arena);
        v->arena = new_arena;
        v->capacity = new_capacity;
    }

    int32_t assigned = (int32_t)v->active_count;
    uint64_t *slot = (uint64_t *)slot_ptr(v, assigned);
    slot[0] = words[0];
    slot[1] = words[1];
    slot[2] = words[2];
    slot[3] = words[3];
    slot[4] = (uint64_t)class_id << 32;
    slot[5] = (uint64_t)target_pointer;
    v->active_count++;

    if (v->active_count >= v->map_capacity * 6 / 10) {
        if (!map_resize(v)) {
            v->active_count--;
            return -1;
        }
    } else {
        map_insert(v, words, assigned);
    }
    return assigned;
}

int32_t Variable_getId(Variable *v, const char *name) {
    if (!v->active || name == NULL)
        return -1;

    size_t len = strlen(name);
    if (len > VARIABLE_NAME_SIZE)
        return -1;

    uint64_t words[4];
    lowercase_pack(name, len, words);
    return map_get(v, words);
}

bool Variable_rename(Variable *v, const char *old_name, const char *new_name) {
    if (!v->active || old_name == NULL || new_name == NULL)
        return false;

    size_t old_len = strlen(old_name);
    size_t new_len = strlen(new_name);
    if (old_len > VARIABLE_NAME_SIZE || new_len > VARIABLE_NAME_SIZE)
        return false;

    uint64_t old_words[4];
    uint64_t new_words[4];
    lowercase_pack(old_name, old_len, old_words);
    lowercase_pack(new_name, new_len, new_words);

    if (map_get(v, new_words) != -1)
        return false;

    int32_t target = map_get(v, old_words);
    if (target == -1)
        return false;

    uint64_t *slot = (uint64_t *)slot_ptr(v, target);
    slot[0] = new_words[0];
    slot[1] = new_words[1];
    slot[2] = new_words[2];
    slot[3] = new_words[3];

    map_rebuild(v);
    return true;
}

uintptr_t Variable_getPointer(Variable *v, int32_t var_id) {
    uint64_t *slot = (uint64_t *)slot_ptr(v, var_id);
    return (uintptr_t)slot[5];
}

void Variable_setPointer(Variable *v, int32_t var_id, uintptr_t target_pointer) {
    uint64_t *slot = (uint64_t *)slot_ptr(v, var_id);
    slot[5] = (uint64_t)target_pointer;
}

bool Variable_compareAndSetPointer(Variable *v, int32_t var_id, uintptr_t expected, uintptr_t new_pointer) {
    uint64_t *slot = (uint64_t *)slot_ptr(v, var_id);
    if (slot[5] != (uint64_t)expected)
        return false;
    slot[5] = (uint64_t)new_pointer;
    return true;
}

uint32_t Variable_getClassId(Variable *v, int32_t var_id) {
    uint64_t *slot = (uint64_t *)slot_ptr(v, var_id);
    return (uint32_t)(slot[4] >> 32);
}

int Variable_getName(Variable *v, int32_t var_id, char *out, size_t out_cap) {
    uint64_t *slot = (uint64_t *)slot_ptr(v, var_id);
    char buf[VARIABLE_NAME_SIZE + 1];
    size_t len = 0;
    for (int i = 0; i < 4 && len < VARIABLE_NAME_SIZE; i++) {
        uint64_t word = slot[i];
        for (int j = 0; j < 8 && len < VARIABLE_NAME_SIZE; j++) {
            unsigned char b = (unsigned char)(word >> (j * 8));
            if (b == 0)
                break;
            buf[len++] = (char)b;
        }
    }
    buf[len] = '\0';
    if (out_cap < len + 1)
        return -1;
    memcpy(out, buf, len + 1);
    return (int)len;
}

size_t Variable_getActiveCount(Variable *v) {
    return v->active_count;
}