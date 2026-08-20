cmake_minimum_required(VERSION 4.3)
project(anti C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

add_executable(anti
    src/main/main.c
    src/bit/bit.c
    src/engine/loop.c
    src/nio/mem.c
    src/objects/probable.c
    src/objects/probable_objects.c
    src/oop/stride.c
    src/oop/struct.c
    src/oop/type.c
    src/relational/variable.c
    src/struct/array.c
    src/struct/collection.c
    src/struct/deque.c
    src/struct/list.c
    src/struct/map.c
    src/struct/minheap.c
    src/struct/queue.c
    src/struct/set.c
    src/struct/sparseset.c
    src/struct/stack.c
    src/thread/ring.c
    src/thread/spin.c
    src/util/arrays.c
    src/util/hash.c
    src/util/random.c
)
target_include_directories(anti PRIVATE src)
target_compile_options(anti PRIVATE -Wall -Wextra -Werror -mcpu=native)
target_link_libraries(anti PRIVATE pthread)

# Cocoa shim
enable_language(OBJC)
add_executable(anti_window
    src/window/window_cocoa.m
    tests/window_test.c
    src/engine/loop.c
)
target_include_directories(anti_window PRIVATE src)
target_compile_options(anti_window PRIVATE -Wall -Wextra -mcpu=native)
target_link_libraries(anti_window PRIVATE
    "-framework Cocoa"
    "-framework AppKit"
)

# Cross-platform backend seam: the Windows/Linux placeholders are draft
# stubs (see the @Draft annotations in src/window/). Each compiles only on
# its own platform; the anti_window target itself still needs a platform
# split before it can build outside macOS.
if(WIN32)
    target_sources(anti_window PRIVATE src/window/window.c)
elseif(UNIX AND NOT APPLE)
    target_sources(anti_window PRIVATE src/window/window_linux.c)
endif()