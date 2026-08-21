#ifndef CLI_COMMANDREGISTRY_H
#define CLI_COMMANDREGISTRY_H

#include "cli/command.h"

// cli/commandregistry.h — the CommandRegistry class, ported from
// cli/CommandRegistry.java.
//
// A single Map (key class ID_STRING, value class ID_LONG) binds command names
// to native function pointers. The Map hashes/compares string keys by content
// through their block headers, so a freshly parsed "quit" matches the
// registered "quit" no matter where each string lives.

// Native command target: receives the parsed command and returns void.
typedef void (*CommandFn)(Command *command);

// Bind name to target. The name string is copied into the registry map.
void CommandRegistry_register(const char *name, CommandFn target);

// Look up the parsed command's name and invoke its target. Prints
// "Unknown command: <name>" when nothing is registered under that name.
void CommandRegistry_execute(Command *command);

// Free the registry map and every registered name string.
void CommandRegistry_free(void);

#endif