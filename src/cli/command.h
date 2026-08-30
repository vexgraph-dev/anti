#ifndef CLI_COMMAND_H
#define CLI_COMMAND_H

#include <stddef.h>
#include <stdint.h>

// cli/command.h — the Command class, ported from cli/Command.java.
//
// A parsed command is a single Memory block of type TYPE_COMMAND_SINGLETON.
// Layout (all offsets relative to the block's payload pointer):
//
//   +0   namePtr        (uint8_t *  string block, owned)
//   +8   argumentCount  (uint32_t)
//   +12  padding        (uint32_t)
//   +16  arguments      (argumentCount * uint8_t *, owned)
//
// The whole block — name, arguments, and the command itself — is reclaimed by
// Command_free. Argument i reads slot +16 + i*8.

typedef struct Command Command;

// Allocate a command block owning name_ptr and arg_ptrs (all string blocks).
// Returns nullptr on failure.
Command *Command_allocate(uint8_t *name_ptr, uint8_t **arg_ptrs, size_t argc);

// Block-header type id of a command (0 if nullptr).
uint32_t Command_type(const Command *command);

// Owned command name string block (Java getName).
uint8_t *Command_name(const Command *command);

// Number of arguments (Java getArgumentCount).
size_t Command_argumentCount(const Command *command);

// Argument index as a string block (Java getArgument).
uint8_t *Command_argument(const Command *command, size_t index);

// Free name, every argument, then the command block itself (Java free).
void Command_free(Command *command);

#endif