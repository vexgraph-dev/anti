#include "cli/commandregistry.h"

#include <stdint.h>
#include <stdio.h>

#include "oop/type.h"
#include "primitive/string.h"
#include "struct/array.h"
#include "struct/map.h"

// commandregistry.c — the CommandRegistry class, ported from cli/CommandRegistry.java.

static Map *registry_map = nullptr;

static Map *ensure_map(void) {
    if (!registry_map)
        registry_map = Map_allocate(ID_STRING, ID_LONG, 0);
    return registry_map;
}

void CommandRegistry_register(const char *name, CommandFn target) {
    if (!name || !target)
        return;
    uint8_t *name_block = string_allocate(name);
    if (!name_block)
        return;
    Map *map = ensure_map();
    if (!map) {
        string_free(name_block);
        return;
    }
    Map_put(map, (uint64_t)(uintptr_t)name_block, (uint64_t)(uintptr_t)(void *)target);
}

void CommandRegistry_execute(Command *command) {
    if (!command)
        return;
    uint8_t *name_block = Command_name(command);
    if (!name_block)
        return;
    Map *map = ensure_map();
    uint64_t target = map ? Map_get(map, (uint64_t)(uintptr_t)name_block) : 0;
    if (target != 0) {
        CommandFn fn = (CommandFn)(uintptr_t)target;
        (*fn)(command);
    } else {
        printf("Unknown command: %s\n", string_get(name_block));
    }
}

void CommandRegistry_free(void) {
    if (!registry_map)
        return;
    Array *keys = Map_keys(registry_map);
    if (keys) {
        for (size_t i = 0; i < Array_length(keys); i++) {
            uint64_t key = Array_get(keys, i);
            string_free((uint8_t *)(uintptr_t)key);
        }
        Array_free(keys);
    }
    Map_free(registry_map);
    registry_map = nullptr;
}