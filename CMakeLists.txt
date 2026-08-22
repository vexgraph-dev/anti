cmake_minimum_required(VERSION 4.3)
project(anti C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

if(APPLE)
    enable_language(OBJC)
endif()

add_executable(anti
    src/main/main.c
    src/bit/bit.c
    src/buffers/buffer.c
    src/buffers/color_buffer.c
    src/buffers/depth_buffer.c
    src/buffers/frame_buffer.c
    src/buffers/height_buffer.c
    src/buffers/normal_buffer.c
    src/buffers/shadow_buffer.c
    src/buffers/stencil_buffer.c
    src/cli/command.c
    src/cli/commandparser.c
    src/cli/commandregistry.c
    src/cli/console.c
    src/cli/logcommands.c
    src/cli/scanner.c
    src/engine/loop.c
    src/io/antihome.c
    src/io/file.c
    src/io/filewriter.c
    src/io/log.c
    src/io/logparser.c
    src/input/focus.c
    src/input/key.c
    src/input/mouse.c
    src/input/touch.c
    src/lang/fastmath.c
    src/lang/mat4.c
    src/lang/vec2.c
    src/lang/vec3.c
    src/lang/vec4.c
    src/net/http.c
    src/net/json.c
    src/net/netfacade.c
    src/net/url.c
    src/nio/mem.c
    src/objects/choice.c
    src/objects/future.c
    src/objects/global.c
    src/objects/local.c
    src/objects/passive.c
    src/objects/probable.c
    src/objects/probable_objects.c
    src/objects/reactive.c
    src/oop/fields.c
    src/oop/stride.c
    src/oop/struct.c
    src/oop/type.c
    src/primitive/string.c
        src/relational/variable.c
        src/render/raster.c
        src/render/panel.c
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
        src/atomic/atomic.c
        src/atomic/ring.c
        src/atomic/registry.c
        src/atomic/spin.c
        src/thread/thread.c
        src/thread/networking.c
        src/thread/event.c
        src/thread/draw.c
        src/thread/scripting.c
        src/thread/ui.c
    src/time/calendar.c
    src/time/clock.c
    src/time/datetime.c
    src/time/nanotime.c
    src/util/arrays.c
    src/util/hash.c
    src/util/random.c
        src/c11/overload.h
)
target_include_directories(anti PRIVATE src)
target_compile_options(anti PRIVATE -Wall -Wextra -Werror -mcpu=native)
target_link_libraries(anti PRIVATE pthread)

if(APPLE)
    target_sources(anti PRIVATE src/security/touchid_cocoa.m)
    target_link_libraries(anti PRIVATE
        "-framework Foundation"
        "-framework LocalAuthentication"
    )
else()
    target_sources(anti PRIVATE src/security/touchid.c)
endif()

# Audio: AVFoundation backend on macOS, silence elsewhere.
if(APPLE)
    target_sources(anti PRIVATE src/audio/audio_cocoa.m)
    set_source_files_properties(src/audio/audio_cocoa.m PROPERTIES
        COMPILE_OPTIONS "-fobjc-arc")
    target_link_libraries(anti PRIVATE "-framework AVFoundation")
else()
    target_sources(anti PRIVATE src/audio/audio_stub.c)
endif()

# TouchID Demo Target
if(APPLE)
    add_executable(touchid_demo
        tests/touchid_demo.c
        src/security/touchid_cocoa.m
    )
    target_include_directories(touchid_demo PRIVATE src)
    target_compile_options(touchid_demo PRIVATE -Wall -Wextra -mcpu=native)
    target_link_libraries(touchid_demo PRIVATE
        "-framework Foundation"
        "-framework LocalAuthentication"
    )
else()
    add_executable(touchid_demo
        tests/touchid_demo.c
        src/security/touchid.c
    )
    target_include_directories(touchid_demo PRIVATE src)
    target_compile_options(touchid_demo PRIVATE -Wall -Wextra -mcpu=native)
endif()

# Windowed engine demo (Legacy: process/EngineTest.java)
add_executable(enginetest
    src/main/enginetest.c
    src/window/window_cocoa.m
    src/engine/loop.c
    src/input/focus.c
    src/input/key.c
    src/input/mouse.c
    src/input/touch.c
        src/atomic/ring.c
        src/atomic/spin.c
    src/time/clock.c
    src/time/nanotime.c
)
target_include_directories(enginetest PRIVATE src)
target_compile_options(enginetest PRIVATE -Wall -Wextra -Werror -mcpu=native)
target_link_libraries(enginetest PRIVATE
    "-framework QuartzCore"
    "-framework Cocoa"
    "-framework AppKit"
)

# Headless net package test
add_executable(net_test
    tests/net_test.c
    src/net/http.c
    src/net/json.c
    src/net/netfacade.c
    src/net/url.c
)
target_include_directories(net_test PRIVATE src)
target_compile_options(net_test PRIVATE -Wall -Wextra -Werror -mcpu=native)

# Headless threading package test
add_executable(thread_test
    tests/thread_test.c
        src/atomic/atomic.c
        src/atomic/registry.c
    src/time/clock.c
    src/time/nanotime.c
)
target_include_directories(thread_test PRIVATE src)
target_compile_options(thread_test PRIVATE -Wall -Wextra -Werror -mcpu=native)
target_link_libraries(thread_test PRIVATE pthread)

# Headless time package test
add_executable(time_test
    tests/time_test.c
    src/time/calendar.c
    src/time/clock.c
    src/time/datetime.c
    src/time/nanotime.c
)
target_include_directories(time_test PRIVATE src)
target_compile_options(time_test PRIVATE -Wall -Wextra -Werror -mcpu=native)

# Headless input pipeline test
add_executable(input_test
    tests/input_test.c
    src/input/focus.c
    src/input/key.c
    src/input/mouse.c
    src/input/touch.c
        src/atomic/ring.c
        src/atomic/spin.c
    src/time/clock.c
    src/time/nanotime.c
)
target_include_directories(input_test PRIVATE src)
target_compile_options(input_test PRIVATE -Wall -Wextra -Werror -mcpu=native)

# Cocoa shim/s
add_executable(anti_window
    src/window/window_cocoa.m
    tests/window_test.c
    src/engine/loop.c
    src/input/focus.c
    src/input/key.c
    src/input/mouse.c
    src/input/touch.c
        src/atomic/ring.c
        src/atomic/spin.c
    src/time/clock.c
    src/time/nanotime.c
)
target_include_directories(anti_window PRIVATE src)
target_compile_options(anti_window PRIVATE -Wall -Wextra -Werror -mcpu=native)
target_link_libraries(anti_window PRIVATE
    "-framework QuartzCore"
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