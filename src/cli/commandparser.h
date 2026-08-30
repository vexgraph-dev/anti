#ifndef CLI_COMMANDPARSER_H
#define CLI_COMMANDPARSER_H

#include "cli/command.h"

// cli/commandparser.h — the CommandParser class, ported from
// cli/CommandParser.java. Turns a raw input line into a parsed Command by
// trimming, splitting on whitespace, and boxing every token as a string.

// Parse a whitespace-separated command line. Returns a new Command (name +
// arguments as owned string blocks) or nullptr for blank/empty input.
Command *CommandParser_parse(const char *line);

#endif