#ifndef STRUCT_COLLECTION_H
#define STRUCT_COLLECTION_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// struct/collection.h — the Collection metadata struct, ported from
// struct/Collection.java.
//
// Every collection (List, Array, Stack, Deque, Queue, Map, Set) embeds a
// Collection as its first member, so a pointer to any of them is also a
// Collection pointer and the accessors below read the shared header. The type
// itself lives in the block header (Memory_type), so Collection_type() walks
// back to it — one subtract, nothing more.

typedef struct Collection {
    uint32_t type_id;       // mirror of the block-header type (for debug)
    uint32_t active_count;  // number of live elements
    uint32_t element_class; // class of elements (Map: key class)
    uint32_t stride;        // bytes per element (Map: val class)
    uint32_t capacity;      // element capacity (or slot capacity)
    uint32_t head;          // circular head index (Deque/Queue); else 0
    uint8_t *data;          // element / slot buffer
} Collection;

uint32_t Collection_type(Collection *c);
uint32_t Collection_size(Collection *c);
uint32_t Collection_length(Collection *c);
bool Collection_isEmpty(Collection *c);
uint32_t Collection_elementClassId(Collection *c);
uint32_t Collection_keyClassId(Collection *c);
uint32_t Collection_stride(Collection *c);
uint32_t Collection_valClassId(Collection *c);
uint32_t Collection_capacity(Collection *c);
uint32_t Collection_head(Collection *c);
uint8_t *Collection_dataBuffer(Collection *c);

// Generic slot read/write for the scalar/pointer collections. Reads the slot at
// index with the collection's stride, widening small ints (legacy readSlot).
uint64_t Collection_readSlot(Collection *c, size_t index);
void Collection_writeSlot(Collection *c, size_t index, uint64_t value);

// Slot read/write without bounds checks (legacy unsafeGet/setUnsafe).
uint64_t Collection_readSlotUnsafe(Collection *c, size_t index);
void Collection_writeSlotUnsafe(Collection *c, size_t index, uint64_t value);

#endif