cmake_minimum_required(VERSION 4.3)
project(anti C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

add_executable(anti
    src/main/main.c
    src/bit/bit.c
    src/engine/loop.c
    src/nio/mem.c
    src/relational/variable.c
    src/thread/ring.c
    src/thread/spin.c
)
target_include_directories(anti PRIVATE src)
target_compile_options(anti PRIVATE -Wall -Wextra -Werror -mcpu=apple-m1)
target_link_libraries(anti PRIVATE pthread)

# Cocoa shim
enable_language(OBJC)
add_executable(anti_window
    src/window/window_cocoa.m
    src/window/window_demo.c
    src/engine/loop.c
)
target_include_directories(anti_window PRIVATE src)
target_compile_options(anti_window PRIVATE -Wall -Wextra -mcpu=apple-m1)
target_link_libraries(anti_window PRIVATE
    "-framework Cocoa"
    "-framework AppKit"
)