cmake_minimum_required(VERSION 4.3)
project(anti C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

add_executable(anti
    main.c
    src/anti_bit.c
    src/anti_loop.c
    src/anti_mem.c
    src/anti_ring.c
    src/anti_spin.c
)
target_include_directories(anti PRIVATE src)
target_compile_options(anti PRIVATE -Wall -Wextra -Werror -mcpu=apple-m1)
target_link_libraries(anti PRIVATE pthread)

# Cocoa shim
enable_language(OBJC)
add_executable(anti_window
    src/anti_window_cocoa.m
    window_demo.c
    src/anti_loop.c
)
target_include_directories(anti_window PRIVATE src)
target_compile_options(anti_window PRIVATE -Wall -Wextra -mcpu=apple-m1)
target_link_libraries(anti_window PRIVATE
    "-framework Cocoa"
    "-framework AppKit"
)

add_executable(pointer_practice pointer_practice.c)
target_compile_options(pointer_practice PRIVATE -Wall -Wextra -Werror)

add_executable(pointer_translation_lab lessons/pointer_translation_lab.c)
target_compile_options(pointer_translation_lab PRIVATE -Wall -Wextra -Werror -mcpu=apple-m1)

add_executable(pointer_exercises lessons/pointer_exercises.c)
target_compile_options(pointer_exercises PRIVATE -Wall -Wextra -Werror -mcpu=apple-m1)