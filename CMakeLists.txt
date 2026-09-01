cmake_minimum_required(VERSION 4.3)
project(anti C)

set(CMAKE_C_STANDARD 23)
set(CMAKE_C_STANDARD_REQUIRED ON)

if(APPLE)
    enable_language(OBJC)
endif()

add_executable(anti
    src/main/main.c
    src/bit/bit.c
    src/nio/mem.c
    src/c23/free.c
    src/oop/type.c
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
    src/cli/logcommands.c
    src/cli/scanner.c
    src/engine/loop.c
    src/lang/vec4.c
    src/lang/vec2.c
    src/lang/mat4.c
    src/lang/fastmath.c
    src/struct/list.c
    src/struct/collection.c
    src/nio/mem.c
    src/c23/free.c
    src/darling/container.c
    src/darling/panel.c
    src/darling/picture.c
    src/darling/scene.c
    src/darling/picture.c
    src/darling/canvas.c
    src/io/antihome.c
    src/io/file.c
    src/io/filewriter.c
    src/io/log.c
    src/io/logparser.c
    src/input/focus.c
    src/input/key.c
    src/input/mouse.c
    src/input/touch.c
    src/cli/console.c
    src/primitive/string.c
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
    src/c23/free.c
    src/objects/choice.c
    src/objects/future.c
    src/objects/global.c
    src/objects/local.c
    src/objects/passive.c
    src/objects/probable.c
    src/objects/probable_objects.c
    src/objects/reactive.c
        src/oop/Class.c
        src/oop/field.c
    src/oop/stride.c
    src/oop/struct.c
    src/oop/type.c
    src/primitive/string.c
        src/relational/variable.c
        src/relational/relational.c
        src/render/raster.c
        src/render/surface.c
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
        src/c23/overload.h
)
target_include_directories(anti PRIVATE src)
target_compile_options(anti PRIVATE -Wall -Wextra -Werror -mcpu=native)
target_link_libraries(anti PRIVATE pthread)

if(APPLE)
    target_sources(anti PRIVATE src/objc/touchid_cocoa.m)
    target_link_libraries(anti PRIVATE
        "-framework Foundation"
        "-framework LocalAuthentication"
    )
else()
    target_sources(anti PRIVATE src/security/touchid.c)
endif()

# Audio: AVFoundation backend on macOS, silence elsewhere.
if(APPLE)
    target_sources(anti PRIVATE src/objc/audio_cocoa.m)
    set_source_files_properties(src/objc/audio_cocoa.m PROPERTIES
        COMPILE_OPTIONS "-fobjc-arc")
    target_link_libraries(anti PRIVATE "-framework AVFoundation")
else()
    target_sources(anti PRIVATE src/audio/audio_stub.c)
endif()

# Darling-tree live demo: scene panels + software raster + present.
add_executable(darlingtest
    src/vulkan/vulkan.c
    src/vulkan/vulkan_mac.c
    src/vulkan/vk_view.c
    src/vulkan/vk_scene.c
    src/vulkan/vk_iosurface.c
    src/vulkan/texture/texture.c
        src/buffer/buffer.c
        src/buffer/color_buffer.c
    src/system/image_mac.m
    src/main/darlingtest.c
        src/objc/window_cocoa.m
        src/objc/panel_cocoa.m
    src/render/raster.c
    src/render/surface.c
    src/darling/container.c
    src/darling/panel.c
    src/darling/picture.c
    src/darling/scene.c
    src/lang/mat4.c
    src/lang/vec2.c
    src/lang/vec4.c
    src/lang/fastmath.c
    src/struct/list.c
    src/struct/collection.c
    src/struct/array.c
    src/struct/map.c
    src/util/hash.c
    src/thread/thread.c
    src/thread/draw.c
    src/oop/stride.c
    src/oop/struct.c
        src/oop/Class.c
        src/oop/field.c
    src/engine/loop.c
    src/input/focus.c
    src/input/key.c
    src/input/mouse.c
    src/input/touch.c
    src/cli/console.c
    src/primitive/string.c
    src/atomic/ring.c
    src/atomic/spin.c
    src/time/clock.c
    src/time/nanotime.c
    src/system/display_monitor.c
    src/system/display_info.c
    src/system/hardware_info.c
    src/system/graphics_info.c
    src/system/system.c
    src/io/hot_file.c
    src/io/mmap.c
    src/io/bake.c
    src/io/vfs.c
        src/objc/discovery.m
    src/nio/mem.c
    src/c23/free.c
    src/oop/type.c
)
set_source_files_properties(src/objc/window_cocoa.m PROPERTIES COMPILE_OPTIONS "-fobjc-arc")
set_source_files_properties(src/objc/discovery.m PROPERTIES COMPILE_OPTIONS "-fobjc-arc")
set_source_files_properties(src/objc/panel_cocoa.m PROPERTIES COMPILE_OPTIONS "-fobjc-arc")
target_include_directories(darlingtest PRIVATE src src/objc /opt/homebrew/include)
target_compile_options(darlingtest PRIVATE -Wall -Wextra -Werror -mcpu=native)
target_compile_definitions(darlingtest PRIVATE ANTI_SPV_DIR="${CMAKE_CURRENT_SOURCE_DIR}/src/vulkan/spv")
target_link_libraries(darlingtest PRIVATE
    pthread
    "-framework Cocoa"
    "-framework AppKit"
    "-framework QuartzCore"
    "-framework CoreGraphics"
    "-framework Metal"
    "-framework IOKit"
    "-framework IOSurface"
)

# Vulkan chain probe: loader -> instance -> surface -> device -> swapchain.
add_executable(vk_test
    src/input/focus.c
    src/input/key.c
    src/input/mouse.c
    src/input/touch.c
    src/cli/console.c
    src/primitive/string.c
    src/atomic/ring.c
    src/atomic/spin.c
    src/time/clock.c
    src/time/nanotime.c
    src/thread/thread.c
    src/thread/draw.c
    src/struct/array.c
    src/struct/map.c
    src/struct/list.c
    src/struct/collection.c
    src/util/hash.c
    src/main/vk_test.c
    src/vulkan/vulkan.c
    src/vulkan/vulkan_mac.c
    src/vulkan/vk_view.c
    src/vulkan/vk_scene.c
    src/vulkan/vk_iosurface.c
    src/vulkan/texture/texture.c
        src/buffer/buffer.c
        src/buffer/color_buffer.c
    src/system/image_mac.m
        src/objc/window_cocoa.m
        src/objc/panel_cocoa.m
    src/window/panel_bridge.c
    src/darling/container.c
    src/darling/panel.c
    src/darling/picture.c
    src/darling/scene.c
    src/lang/mat4.c
    src/lang/vec2.c
    src/lang/vec4.c
    src/lang/fastmath.c
    src/system/display_monitor.c
    src/system/display_info.c
    src/system/hardware_info.c
    src/system/graphics_info.c
    src/system/system.c
    src/io/hot_file.c
    src/io/mmap.c
    src/io/bake.c
    src/io/vfs.c
        src/objc/discovery.m
    src/nio/mem.c
    src/c23/free.c
    src/oop/stride.c
    src/oop/struct.c
        src/oop/Class.c
        src/oop/field.c
    src/oop/type.c
)
set_source_files_properties(src/objc/window_cocoa.m PROPERTIES COMPILE_OPTIONS "-fobjc-arc")
set_source_files_properties(src/objc/discovery.m PROPERTIES COMPILE_OPTIONS "-fobjc-arc")
target_include_directories(vk_test PRIVATE src /opt/homebrew/include)
target_compile_options(vk_test PRIVATE -Wall -Wextra -Werror -mcpu=native)
target_compile_definitions(vk_test PRIVATE ANTI_SPV_DIR="${CMAKE_CURRENT_SOURCE_DIR}/src/vulkan/spv")
target_link_libraries(vk_test PRIVATE
    pthread
    "-framework Cocoa"
    "-framework AppKit"
    "-framework QuartzCore"
    "-framework CoreGraphics"
    "-framework Metal"
    "-framework IOKit"
    "-framework IOSurface"
)

# System Discovery & Hardware / Display probe target
add_executable(system_test
    tests/system_test.c
    src/system/display_monitor.c
    src/system/display_info.c
    src/system/hardware_info.c
    src/system/graphics_info.c
    src/system/system.c
    src/io/hot_file.c
    src/io/mmap.c
    src/io/bake.c
    src/io/vfs.c
        src/objc/discovery.m
    src/nio/mem.c
    src/c23/free.c
    src/oop/stride.c
    src/oop/struct.c
        src/oop/Class.c
        src/oop/field.c
    src/oop/type.c
)
set_source_files_properties(src/objc/discovery.m PROPERTIES COMPILE_OPTIONS "-fobjc-arc")
target_include_directories(system_test PRIVATE src)
target_compile_options(system_test PRIVATE -Wall -Wextra -Werror -mcpu=native)
target_link_libraries(system_test PRIVATE
    pthread
    "-framework Cocoa"
    "-framework AppKit"
    "-framework CoreGraphics"
    "-framework Metal"
    "-framework IOKit"
)

# TouchID Demo Target
if(APPLE)
    add_executable(touchid_demo
        tests/touchid_demo.c
            src/objc/touchid_cocoa.m
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
        src/objc/window_cocoa.m
    src/nio/mem.c
    src/c23/free.c
    src/oop/type.c
        src/buffer/buffer.c
        src/buffer/color_buffer.c
    src/engine/loop.c
    src/input/focus.c
    src/input/key.c
    src/input/mouse.c
    src/input/touch.c
    src/cli/console.c
    src/primitive/string.c
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
    src/cli/console.c
    src/primitive/string.c
        src/atomic/ring.c
        src/atomic/spin.c
    src/time/clock.c
    src/time/nanotime.c
)
target_include_directories(input_test PRIVATE src)
target_compile_options(input_test PRIVATE -Wall -Wextra -Werror -mcpu=native)

add_executable(class_relational_test
    tests/class_relational_test.c
    src/oop/Class.c
    src/oop/field.c
    src/oop/type.c
    src/oop/stride.c
    src/oop/struct.c
    src/relational/variable.c
    src/relational/relational.c
    src/primitive/string.c
    src/nio/mem.c
    src/c23/free.c
    src/atomic/spin.c
    src/util/hash.c
)
target_include_directories(class_relational_test PRIVATE src)
target_compile_options(class_relational_test PRIVATE -Wall -Wextra -Werror -mcpu=native)
target_link_libraries(class_relational_test PRIVATE pthread)

# Cocoa shim/s
add_executable(anti_window
        src/objc/window_cocoa.m
    src/nio/mem.c
    src/c23/free.c
    src/oop/type.c
        src/buffer/buffer.c
        src/buffer/color_buffer.c
    tests/window_test.c
    src/engine/loop.c
    src/input/focus.c
    src/input/key.c
    src/input/mouse.c
    src/input/touch.c
    src/cli/console.c
    src/primitive/string.c
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