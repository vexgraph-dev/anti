#include "cli/command.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "primitive/string.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Command (cli/command.c)
 * ============================================================================
 * the Command class, ported from cli/Command.java.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Command_3(name_ptr, arg_ptrs, argc)
 *
 * Core Functions:
 *   - Command_type(command)
 *   - Command_name(command)
 *   - Command_argumentCount(command)
 *   - Command_argument(command, index)
 *   - Command_free(command)
 * ============================================================================
 */


// command.c — the Command class, ported from cli/Command.java.

static const size_t COMMAND_HEADER_BYTES = 16; // namePtr + argc + pad
static const size_t ARG_POINTER_BYTES = 8;

Command *Command_3(uint8_t *name_ptr, uint8_t **arg_ptrs, size_t argc) {
    size_t total = COMMAND_HEADER_BYTES + ARG_POINTER_BYTES * argc;
    Command *cmd = (Command*) Memory_alloc(TYPE_COMMAND_SINGLETON, total);
    if (!cmd)
        return nullptr;

    uint8_t *p = (uint8_t*) cmd;
    *(uint8_t**) (p + 0) = name_ptr;
    *(uint32_t*) (p + 8) = (uint32_t)argc;
    *(uint32_t*) (p + 12) = 0; // padding
    for (size_t i = 0; i < argc; i++)
        *(uint8_t**) (p + COMMAND_HEADER_BYTES + ARG_POINTER_BYTES * i) = arg_ptrs[i];
    return cmd;
}

uint32_t Command_type(const Command *command) {
    if (!command)
        return 0;
    return Memory_type((void*) (uintptr_t)command);
}

uint8_t *Command_name(const Command *command) {
    if (!command)
        return nullptr;
    return *(uint8_t**) ((uint8_t*) command + 0);
}

size_t Command_argumentCount(const Command *command) {
    if (!command)
        return 0;
    return *(uint32_t*) ((uint8_t*) command + 8);
}

uint8_t *Command_argument(const Command *command, size_t index) {
    if (!command)
        return nullptr;
    if (index >= Command_argumentCount(command))
        return nullptr;
    return *(uint8_t**) ((uint8_t*) command + COMMAND_HEADER_BYTES + ARG_POINTER_BYTES * index);
}

void Command_free(Command *command) {
    if (!command)
        return;
    string_free(Command_name(command));
    size_t argc = Command_argumentCount(command);
    for (size_t i = 0; i < argc; i++)
        string_free(Command_argument(command, i));
    Memory_free(command);
}
