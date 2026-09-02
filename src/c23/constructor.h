#ifndef C23_CONSTRUCTOR_H
#define C23_CONSTRUCTOR_H

// c23/constructor.h — Java-style constructor overloading for C23.
//
// C has no overloading; this header fakes the arity half of it with pure
// preprocessor dispatch. A class exports one function per accepted arity,
// named Name_0, Name_1, ... Name_8, plus a one-line shim:
//
//     Window *Window_0(void);
//     Window *Window_1(const char *title);
//     Window *Window_3(const char *title, int width, int height);
//     #define Window(...) CONSTRUCTOR_DISPATCH(Window, ##__VA_ARGS__)
//
// Call sites read like Java:
//
//     Window *a = Window();                 // -> Window_0()
//     Window *b = Window("main");           // -> Window_1("main")
//     Window *c = Window("main", 800, 600); // -> Window_3(...)
//
// Dispatch is by ARG COUNT only. Type-based overloading (add(x, y) over
// int/Vec3/Mat4) is a different problem and deliberately out of scope here.
// Arity gaps are legal: declaring Window_0/_1/_3 without Window_2 means a
// 2-arg call fails to compile, exactly like Java missing that overload.
//
// Pure preprocessor: nothing here compiles to code, allocates, or costs a
// cycle at runtime. Extending past 8 args: grow CONSTRUCTOR_PICK and the
// NAME##_k ladder in step.
//
// Requires the GNU `, ##__VA_ARGS__` comma-elision extension (CMake builds
// this tree as gnu23; strict -std=c23 breaks zero-arg dispatch). window.h
// already leans on the same extension.

#define CONSTRUCTOR_PICK(_0, _1, _2, _3, _4, _5, _6, _7, _8, NAME, ...) NAME

#define CONSTRUCTOR_DISPATCH(NAME, ...) \
    CONSTRUCTOR_PICK(, ##__VA_ARGS__,   \
        NAME##_8, NAME##_7, NAME##_6, NAME##_5, \
        NAME##_4, NAME##_3, NAME##_2, NAME##_1, NAME##_0)(__VA_ARGS__)

#endif
