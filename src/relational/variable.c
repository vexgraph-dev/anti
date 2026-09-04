// relational/variable.c — Variable registry port (Legacy: variable/Variable.java).
//
// Open-addressing hash map keyed on the 4-word lowercase name; each 40-byte map
// slot holds the name words + a var id (-1 = empty). Slot 0..activeCount-1 of
// the arena are the live rows.

#include "relational/variable.h"

#include <stdlib.h>
#include <string.h>

static void lowercasePack(const char *name, size_t len, uint64_t words[4]) {
    for (int w = 0; w < 4; w++) {
        uint64_t word = 0;
        for (int i = 0; i < 8; i++) {
            size_t index = (size_t) w * 8 + (size_t) i;
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

static uint32_t hashName(uint64_t l0, uint64_t l1, uint64_t l2, uint64_t l3) {
    uint64_t mix = l0 ^ (l1 >> 7) ^ (l2 << 9) ^ (l3 >> 13);
    mix ^= mix >> 32;
    return (uint32_t)mix;
}

static int32_t mapGet(Variable *v, const uint64_t words[4]) {
    uint64_t *map = (uint64_t*) (*v).map;
    size_t cap = (*v).mapCapacity;
    uint32_t index = hashName(words[0], words[1], words[2], words[3]) % (uint32_t)cap;

    for (size_t i = 0; i < cap; i++) {
        uint64_t *slot = map + index * (VARIABLE_MAP_SLOT_SIZE / 8);
        int32_t storedId = (int32_t)(slot[4] & 0xFFFFFFFFu);
        if (storedId == -1)
            return -1;
        if (slot[0] == words[0] && slot[1] == words[1] && slot[2] == words[2] && slot[3] == words[3])
            return storedId;
        index = (index + 1) % (uint32_t)cap;
    }
    return -1;
}

static void mapInsert(Variable *v, const uint64_t words[4], int32_t varId) {
    uint64_t *map = (uint64_t*) (*v).map;
    size_t cap = (*v).mapCapacity;
    uint32_t index = hashName(words[0], words[1], words[2], words[3]) % (uint32_t)cap;

    while (true) {
        uint64_t *slot = map + index * (VARIABLE_MAP_SLOT_SIZE / 8);
        int32_t storedId = (int32_t)(slot[4] & 0xFFFFFFFFu);
        if (storedId == -1 || storedId == varId) {
            slot[0] = words[0];
            slot[1] = words[1];
            slot[2] = words[2];
            slot[3] = words[3];
            slot[4] = (uint64_t)(uint32_t)varId;
            return;
        }
        index = (index + 1) % (uint32_t)cap;
    }
}

static void mapRebuild(Variable *v) {
    memset((*v).map, 0xFF, (*v).mapCapacity * VARIABLE_MAP_SLOT_SIZE);
    for (size_t i = 0; i < (*v).activeCount; i++) {
        uint64_t *slot = (uint64_t*) ((*v).arena + i * VARIABLE_SLOT_SIZE);
        uint64_t words[4] = {slot[0], slot[1], slot[2], slot[3]};
        mapInsert(v, words, (int32_t)i);
    }
}

static bool mapResize(Variable *v) {
    size_t newCapacity = (*v).mapCapacity * 2;
    uint8_t *newMap = malloc(newCapacity * VARIABLE_MAP_SLOT_SIZE);
    if (newMap == nullptr)
        return false;
    memset(newMap, 0xFF, newCapacity * VARIABLE_MAP_SLOT_SIZE);

    uint64_t *oldMap = (uint64_t*) (*v).map;
    size_t oldCapacity = (*v).mapCapacity;
    (*v).map = newMap;
    (*v).mapCapacity = newCapacity;
    for (size_t i = 0; i < oldCapacity; i++) {
        uint64_t *slot = oldMap + i * (VARIABLE_MAP_SLOT_SIZE / 8);
        int32_t storedId = (int32_t)(slot[4] & 0xFFFFFFFFu);
        if (storedId != -1) {
            uint64_t words[4] = {slot[0], slot[1], slot[2], slot[3]};
            mapInsert(v, words, storedId);
        }
    }
    free(oldMap);
    return true;
}

static uint8_t *slotPtr(Variable *v, int32_t varId) {
    return (*v).arena + (size_t) varId * VARIABLE_SLOT_SIZE;
}

static bool validId(Variable *v, int32_t varId) {
    return v && (*v).active && (*v).arena && varId >= 0 &&
           (size_t)varId < (*v).activeCount;
}

bool Variable_init(Variable *v) {
    memset(v, 0, sizeof(*v));
    (*v).capacity = VARIABLE_DEFAULT_CAPACITY;
    (*v).mapCapacity = VARIABLE_DEFAULT_CAPACITY * 2;

    (*v).arena = malloc((*v).capacity * VARIABLE_SLOT_SIZE);
    if ((*v).arena == nullptr)
        return false;

    (*v).map = malloc((*v).mapCapacity * VARIABLE_MAP_SLOT_SIZE);
    if ((*v).map == nullptr) {
        free((*v).arena);
        (*v).arena = nullptr;
        return false;
    }

    memset((*v).map, 0xFF, (*v).mapCapacity * VARIABLE_MAP_SLOT_SIZE);
    (*v).active = true;
    return true;
}

void Variable_shutdown(Variable *v) {
    if (!(*v).active)
        return;
    free((*v).map);
    free((*v).arena);
    (*v).map = nullptr;
    (*v).arena = nullptr;
    (*v).mapCapacity = 0;
    (*v).capacity = 0;
    (*v).activeCount = 0;
    (*v).active = false;
}

int32_t Variable_instant(Variable *v, const char *name, uint32_t classId, uintptr_t targetPointer) {
    if (!(*v).active || name == nullptr)
        return -1;

    size_t len = strlen(name);
    if (len > VARIABLE_NAME_SIZE)
        return -1;

    uint64_t words[4];
    lowercasePack(name, len, words);

    int32_t existing = mapGet(v, words);
    if (existing != -1) {
        uint64_t *slot = (uint64_t*) slotPtr(v, existing);
        slot[4] = ((uint64_t)classId << 32) | (slot[4] & 0xFFFFFFFFu);
        slot[5] = (uint64_t)targetPointer;
        return existing;
    }

    if ((*v).activeCount >= (*v).capacity) {
        size_t newCapacity = (*v).capacity + VARIABLE_DEFAULT_CAPACITY;
        uint8_t *newArena = malloc(newCapacity * VARIABLE_SLOT_SIZE);
        if (newArena == nullptr)
            return -1;
        memcpy(newArena, (*v).arena, (*v).activeCount * VARIABLE_SLOT_SIZE);
        free((*v).arena);
        (*v).arena = newArena;
        (*v).capacity = newCapacity;
    }

    int32_t assigned = (int32_t)(*v).activeCount;
    uint64_t *slot = (uint64_t*) slotPtr(v, assigned);
    slot[0] = words[0];
    slot[1] = words[1];
    slot[2] = words[2];
    slot[3] = words[3];
    slot[4] = (uint64_t)classId << 32;
    slot[5] = (uint64_t)targetPointer;
    (*v).activeCount++;

    if ((*v).activeCount >= (*v).mapCapacity * 6 / 10) {
        if (!mapResize(v)) {
            (*v).activeCount--;
            return -1;
        }
    } else
        mapInsert(v, words, assigned);
    return assigned;
}

int32_t Variable_getId(Variable *v, const char *name) {
    if (!(*v).active || name == nullptr)
        return -1;

    size_t len = strlen(name);
    if (len > VARIABLE_NAME_SIZE)
        return -1;

    uint64_t words[4];
    lowercasePack(name, len, words);
    return mapGet(v, words);
}

bool Variable_rename(Variable *v, const char *oldName, const char *newName) {
    if (!(*v).active || oldName == nullptr || newName == nullptr)
        return false;

    size_t oldLen = strlen(oldName);
    size_t newLen = strlen(newName);
    if (oldLen > VARIABLE_NAME_SIZE || newLen > VARIABLE_NAME_SIZE)
        return false;

    uint64_t oldWords[4];
    uint64_t newWords[4];
    lowercasePack(oldName, oldLen, oldWords);
    lowercasePack(newName, newLen, newWords);

    if (mapGet(v, newWords) != -1)
        return false;

    int32_t target = mapGet(v, oldWords);
    if (target == -1)
        return false;

    uint64_t *slot = (uint64_t*) slotPtr(v, target);
    slot[0] = newWords[0];
    slot[1] = newWords[1];
    slot[2] = newWords[2];
    slot[3] = newWords[3];

    mapRebuild(v);
    return true;
}

uintptr_t Variable_getPointer(Variable *v, int32_t varId) {
    if (!validId(v, varId))
        return 0;
    uint64_t *slot = (uint64_t*) slotPtr(v, varId);
    return (uintptr_t) slot[5];
}

void Variable_setPointer(Variable *v, int32_t varId, uintptr_t targetPointer) {
    if (!validId(v, varId))
        return;
    uint64_t *slot = (uint64_t*) slotPtr(v, varId);
    slot[5] = (uint64_t) targetPointer;
}

bool Variable_compareAndSetPointer(Variable *v, int32_t varId, uintptr_t expected, uintptr_t newPointer) {
    if (!validId(v, varId))
        return false;
    uint64_t *slot = (uint64_t*) slotPtr(v, varId);
    if (slot[5] != (uint64_t) expected)
        return false;
    slot[5] = (uint64_t) newPointer;
    return true;
}

uint32_t Variable_getClassId(Variable *v, int32_t varId) {
    if (!validId(v, varId))
        return 0;
    uint64_t *slot = (uint64_t*) slotPtr(v, varId);
    return (uint32_t) (slot[4] >> 32);
}

int Variable_getName(Variable *v, int32_t varId, char *out, size_t outCap) {
    if (!validId(v, varId) || !out)
        return -1;
    uint64_t *slot = (uint64_t*) slotPtr(v, varId);
    char buf[VARIABLE_NAME_SIZE + 1];
    size_t len = 0;
    for (int i = 0; i < 4 && len < VARIABLE_NAME_SIZE; i++) {
        uint64_t word = slot[i];
        for (int j = 0; j < 8 && len < VARIABLE_NAME_SIZE; j++) {
            unsigned char b = (unsigned char)(word >> (j * 8));
            if (b == 0)
                break;
            buf[len++] = (char) b;
        }
    }
    buf[len] = '\0';
    if (outCap < len + 1)
        return -1;
    memcpy(out, buf, len + 1);
    return (int)len;
}

size_t Variable_getActiveCount(Variable *v) {
    return (*v).activeCount;
}