#ifndef STRUCT_MAP_H
#define STRUCT_MAP_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "c23/constructor.h"

#include "struct/array.h"
#include "struct/collection.h"

// struct/map.h — the Map class, ported from struct/Map.java.
//
// Open-addressing hash map. Each slot is 32 bytes: key(8) + val(8) + hash(8) +
// state(8); state is 0=empty, 1=occupied, 2=deleted. The embedded Collection
// stores key_class in element_class and val_class in stride. Reference keys
// (strings, collections, structs) are hashed/compared via their block headers.

typedef struct Map {
    Collection collection;
} Map;

// Empty map for key_class/val_class with initial slot capacity (min 4, power of two).
Map *Map_3(uint32_t key_class, uint32_t val_class, size_t capacity);

void Map_free(Map *map);

// Insert or update key => value. Grows (rehashes) past the 0.75 load factor.
void Map_put(Map *map, uint64_t key, uint64_t value);

// Value for key, or 0 if absent.
uint64_t Map_get(Map *map, uint64_t key);

bool Map_containsKey(Map *map, uint64_t key);

// Remove key; returns the old value, or 0 if absent.
uint64_t Map_remove(Map *map, uint64_t key);

// Array of all active keys (class key_class).
Array *Map_keys(Map *map);

bool Map_isEmpty(Map *map);
size_t Map_size(Map *map);
size_t Map_capacity(Map *map);
uint32_t Map_keyClassId(Map *map);
uint32_t Map_valClassId(Map *map);
uint8_t *Map_dataBuffer(Map *map);


Map *Map_2(uint32_t key_class, uint32_t val_class);

#define Map(...) CONSTRUCTOR_DISPATCH(Map, ##__VA_ARGS__)

#endif