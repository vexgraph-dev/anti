cmake_minimum_required(VERSION 4.3)
project(vexspoke C)

set(CMAKE_C_STANDARD 23)
set(CMAKE_C_STANDARD_REQUIRED ON)

if(APPLE)
    enable_language(OBJC)
    set(CMAKE_OBJC_STANDARD 23)
    set(CMAKE_OBJC_STANDARD_REQUIRED ON)
endif()

# Core VexSpoke (formerly Anti) Runtime Library
add_library(vexspoke STATIC
    src/bit/bit.c
    src/nio/mem.c
    src/c23/free.c
    src/oop/type.c
    src/oop/Class.c
    src/oop/field.c
    src/oop/stride.c
    src/oop/struct.c
    src/buffer/buffer.c
    src/buffer/color_buffer.c
    src/buffer/depth_buffer.c
    src/buffer/frame_buffer.c
    src/buffer/height_buffer.c
    src/buffer/normal_buffer.c
    src/buffer/shadow_buffer.c
    src/buffer/stencil_buffer.c
    src/cli/command.c
    src/cli/commandparser.c
    src/cli/commandregistry.c
    src/cli/console.c
    src/cli/logcommands.c
    src/cli/scanner.c
    src/primitive/string.c
    src/primitive/int.c
    src/primitive/long.c
    src/primitive/float.c
    src/primitive/double.c
    src/primitive/byte.c
    src/primitive/short.c
    src/primitive/bool.c
    src/primitive/brain.c
    src/primitive/fixed32.c
    src/primitive/fixed64.c
    src/primitive/int_float.c
    src/primitive/int_double.c
    src/primitive/long_float.c
    src/primitive/long_double.c
    src/primitive/pack.c
    src/engine/loop.c
    src/lang/vec2.c
    src/lang/vec3.c
    src/lang/vec4.c
    src/lang/mat4.c
    src/lang/fastmath.c
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
    src/io/vexhome.c
    src/io/file.c
    src/io/filewriter.c
    src/io/log.c
    src/io/logparser.c
    src/io/hot_file.c
    src/input/focus.c
    src/input/key.c
    src/input/mouse.c
    src/input/touch.c
    src/net/http.c
    src/net/json.c
    src/net/netfacade.c
    src/net/url.c
    src/objects/choice.c
    src/objects/future.c
    src/objects/global.c
    src/objects/local.c
    src/objects/passive.c
    src/objects/probable.c
    src/objects/probable_objects.c
    src/objects/reactive.c
    src/relational/variable.c
    src/relational/relational.c
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
    src/system/system.c
    src/system/display_monitor.c
    src/system/display_info.c
    src/system/hardware_info.c
    src/system/graphics_info.c
    src/vulkan/vulkan.c
    src/vulkan/vulkan_mac.c
)

target_include_directories(vexspoke PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)

if(EXISTS "/opt/homebrew/include/vulkan/vulkan.h")
    target_include_directories(vexspoke PUBLIC /opt/homebrew/include)
endif()

target_compile_options(vexspoke PRIVATE -Wall -Wextra -Werror -mcpu=native)
target_link_libraries(vexspoke PUBLIC pthread)

if(APPLE)
    target_sources(vexspoke PRIVATE
        src/objc/touchid_cocoa.m
        src/objc/tls_apple.m
        src/objc/audio_cocoa.m
        src/objc/discovery.m
        src/system/image_mac.m
    )
    set_source_files_properties(
        src/objc/touchid_cocoa.m
        src/objc/tls_apple.m
        src/objc/audio_cocoa.m
        src/objc/discovery.m
        src/system/image_mac.m
        PROPERTIES COMPILE_OPTIONS "-fobjc-arc"
    )
    target_link_libraries(vexspoke PUBLIC
        "-framework Foundation"
        "-framework LocalAuthentication"
        "-framework Network"
        "-framework Security"
        "-framework AVFoundation"
        "-framework Cocoa"
        "-framework AppKit"
        "-framework CoreGraphics"
        "-framework Metal"
        "-framework IOKit"
    )
else()
    target_sources(vexspoke PRIVATE
        src/security/touchid.c
        src/net/tls_curl.c
        src/audio/audio_stub.c
    )
    find_package(CURL REQUIRED)
    target_link_libraries(vexspoke PUBLIC CURL::libcurl)
endif()

# Headless demo target
add_executable(anti
    src/main/main.c
)
target_link_libraries(anti PRIVATE vexspoke)

# System Discovery & Hardware / Display probe target
add_executable(system_test
    tests/system_test.c
)
target_link_libraries(system_test PRIVATE vexspoke)

# TouchID Demo Target
add_executable(touchid_demo
    tests/touchid_demo.c
)
target_link_libraries(touchid_demo PRIVATE vexspoke)

# Headless net package test
add_executable(net_test
    tests/net_test.c
)
target_link_libraries(net_test PRIVATE vexspoke)

# Headless threading package test
add_executable(thread_test
    tests/thread_test.c
)
target_link_libraries(thread_test PRIVATE vexspoke)

# Headless time package test
add_executable(time_test
    tests/time_test.c
)
target_link_libraries(time_test PRIVATE vexspoke)

# Headless input pipeline test
add_executable(input_test
    tests/input_test.c
)
target_link_libraries(input_test PRIVATE vexspoke)

# Class relational test
add_executable(class_relational_test
    tests/class_relational_test.c
)
target_link_libraries(class_relational_test PRIVATE vexspoke)
