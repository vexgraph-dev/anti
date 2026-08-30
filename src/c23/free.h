#ifndef C23_FREE_H
#define C23_FREE_H

// c23/free.h — The Relational Destructor Dispatcher.
// 
// In anti, everything is a pointer and every block carries its type in a header.
// This function overloads the standard free(void*), automatically routing to the 
// correct destructor (e.g. Probable_free) based on the runtime type ID, before 
// actually reclaiming the memory block via Memory_free.

void c23_free(void *ptr);

// Hijack the standard free call.
// Note: nio/mem.c must NOT include this file (or must #undef free), 
// because Memory_free needs to call the real libc free() to release the block.
#define free(ptr) c23_free(ptr)

#endif // C23_FREE_H
