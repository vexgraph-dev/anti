#ifndef CLI_SCANNER_H
#define CLI_SCANNER_H

#include <stdbool.h>
#include <stdint.h>

// cli/scanner.h — the Scanner class, ported from cli/Scanner.java.
//
// Zero-allocation replacement for java.util.Scanner. Reads UTF-8 bytes from
// stdin into a fixed stack buffer and returns each line/word as a string
// block, so the console loop never touches the allocator while typing.

#define SCANNER_BUFFER_SIZE 4096

// True when stdin has pending input (Java hasNextLine).
bool Scanner_hasNextLine(void);

// Next line as a string block (without the newline), or NULL at EOF or on an
// empty line (Java nextLine).
uint8_t *Scanner_nextLine(void);

// Next whitespace-delimited word as a string block, or NULL at EOF (Java
// nextWord). Leading whitespace is skipped.
uint8_t *Scanner_nextWord(void);

#endif