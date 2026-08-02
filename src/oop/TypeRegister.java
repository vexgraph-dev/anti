package oop;

import annotation.Intention;
import annotation.HotCode;

@HotCode
@Intention("Central type registry using bit-packed type IDs: 32-bit hex code (Form, Modifiers, Wrappers, Class ID)")
public class TypeRegister
{

    // i want 0x0000_0000 hex code
    // 0x ::
    // 0 - form (singleton, array, pointer, struct singleton, struct array, struct pointer, abcdef)
    // 0 - object modifier (global, locale, transient) // volatile is already at the object level anyway, they can either shift to thread safe/unsafe at the same time
    // 0 - object wrapper (proactive, reactive)
    // 0 - object wrapper as well (probable, probableobjects, future, choice)
    // 0000 - the last four are the class ids

    // --- BIT MASKS ---
    public static final int MASK_FORM       = 0xF0000000;
    public static final int MASK_MODIFIER   = 0x0F000000;
    public static final int MASK_WRAPPER_1  = 0x00F00000;
    public static final int MASK_WRAPPER_2  = 0x000F0000;
    public static final int MASK_CLASS      = 0x0000FFFF;

    // --- FORMS (Digit 1) ---
    public static final int FORM_SINGLETON          = 0x10000000; // non-struct singleton
    public static final int FORM_ARRAY              = 0x20000000; // non-struct array
    public static final int FORM_POINTER            = 0x30000000; // non-struct pointer
    public static final int FORM_STRUCT_SINGLETON   = 0x40000000; // struct singleton
    public static final int FORM_STRUCT_ARRAY       = 0x50000000; // struct array
    public static final int FORM_STRUCT_POINTER     = 0x60000000; // struct pointer

    // --- MODIFIERS (Digit 2) ---
    public static final int MOD_GLOBAL              = 0x01000000;
    public static final int MOD_LOCALE              = 0x02000000;
    public static final int MOD_TRANSIENT           = 0x03000000;

    // --- WRAPPERS 1 (Digit 3) ---
    public static final int WRAP_PROACTIVE          = 0x00100000;
    public static final int WRAP_REACTIVE           = 0x00200000;

    // --- WRAPPERS 2 (Digit 4) ---
    public static final int WRAP_PROBABLE           = 0x00010000;
    public static final int WRAP_PROBABLE_OBJECTS   = 0x00020000;
    public static final int WRAP_FUTURE             = 0x00030000;
    public static final int WRAP_CHOICE             = 0x00040000;

    // --- RAW CLASS IDS (Lower 16 bits) ---
    public static final int ID_INT = 0x000001; // Int class // 1
    public static final int ID_LONG = 0x000002; // Long class // 2
    public static final int ID_FLOAT = 0x000003; // Float class // 3
    public static final int ID_DOUBLE = 0x000004; // Double class // 4
    public static final int ID_BYTE = 0x000005; // Byte class // 5
    public static final int ID_SHORT = 0x000006; // Short class // 6
    public static final int ID_STRING = 0x000007; // string class // 7
    public static final int ID_INT_FLOAT = 0x000008; // IntFloat class // 8
    public static final int ID_INT_DOUBLE = 0x000009; // IntDouble class // 9
    public static final int ID_LONG_FLOAT = 0x00000A; // LongFloat class // 10
    public static final int ID_LONG_DOUBLE = 0x00000B; // LongDouble class // 11
    public static final int ID_VARIABLE = 0x00000C; // Variable class // 12
    public static final int ID_PACK = 0x00000D; // Pack class // 13
    public static final int ID_ARRAYS = 0x00000E; // Arrays class // 14
    public static final int ID_HASH = 0x00000F; // Hash class // 15
    public static final int ID_CLASS = 0x000010; // Class class // 16
    public static final int ID_STRIDE = 0x000011; // Stride class // 17
    public static final int ID_LIST = 0x000012; // List class // 18
    public static final int ID_MAP = 0x000013; // Map class // 19
    public static final int ID_SET = 0x000014; // Set class // 20
    public static final int ID_STACK = 0x000015; // Stack class // 21
    public static final int ID_DEQUE = 0x000016; // Deque class // 22
    public static final int ID_SLAB_ALLOCATOR = 0x000017; // SlabAllocator class // 23
    public static final int ID_STRING_ENGINE = 0x000018; // StringEngine class // 24
    public static final int ID_SPIN_LOCK = 0x000019; // SpinLock class // 25
    public static final int ID_RING_BUFFER = 0x00001A; // RingBuffer class // 26
    public static final int ID_MEMORY_MAP_MANAGER = 0x00001B; // MemoryMapManager class // 27
    public static final int ID_TRIE = 0x00001C; // Trie class // 28
    public static final int ID_SEARCH_VARIABLE = 0x00001D; // SearchVariable class // 29
    public static final int ID_RANDOM = 0x00001E; // Random class // 30
    public static final int ID_INDEX_RANDOM = 0x00001F; // IndexRandom class // 31
    public static final int ID_CALENDAR = 0x000020; // Calendar class // 32
    public static final int ID_CLOCK = 0x000021; // Clock class // 33
    public static final int ID_DATETIME = 0x000022; // DateTime class // 34
    public static final int ID_NANOTIME = 0x000023; // NanoTime class // 35
    public static final int ID_GRID_ARRAY = 0x000024; // GridArray class // 36
    public static final int ID_OCTREE = 0x000025; // Octree class // 37
    public static final int ID_CUBE_ARRAY = 0x000026; // CubeArray class // 38
    public static final int ID_SPHERE_ARRAY = 0x000027; // SphereArray class // 39
    public static final int ID_CIRCULAR_ARRAY = 0x000028; // CircularArray class // 40
    public static final int ID_BRAIN = 0x000029; // Brain class // 41
    public static final int ID_FIXED32 = 0x00002A; // Fixed32 class // 42
    public static final int ID_FIXED64 = 0x00002B; // Fixed64 class // 43
    public static final int ID_STRING_BUILDER = 0x00002C; // StringBuilder class // 44
    public static final int ID_HTTP_CLIENT = 0x00002D; // HTTPClient class // 45
    public static final int ID_JSON = 0x00002E; // JSON class // 46
    public static final int ID_POLL_REQUEST = 0x00002F; // PollRequest class // 47
    public static final int ID_TRANSPORT_PROTOCOL = 0x000030; // TransportProtocol class // 48
    public static final int ID_NETWORKING_THREAD = 0x000031; // NetworkingThread class // 49
    public static final int ID_HTTP_SERVER = 0x000032; // HTTPServer class // 50
    public static final int ID_WEBSOCKET_CLIENT = 0x000033; // WebSocketClient class // 51
    public static final int ID_HASH_VARIABLE = 0x000034; // HashVariable class // 52
    public static final int ID_SECURE_PACKET = 0x000035; // SecurePacket class // 53
    public static final int ID_SECURE_RANDOM = 0x000036; // SecureRandom class // 54
    public static final int ID_SERVER_CLIENT_CHECKER = 0x000037; // ServerClientChecker class // 55
    public static final int ID_TOUCH_ID = 0x000038; // TouchID class // 56
    public static final int ID_SERVER_JAR_BUILDER = 0x000039; // ServerJarBuilder class // 57
    public static final int ID_SCRIPTING_THREAD = 0x00003A; // ScriptingThread class // 58
    public static final int ID_DRAW_THREAD = 0x00003B; // DrawThread class // 59
    public static final int ID_BOOL = 0x00003C; // Bool class // 60
    public static final int ID_SEMAPHORE = 0x00003D; // Semaphore class // 61
    public static final int ID_SWAPCHAIN = 0x00003E; // Swapchain class // 62
    public static final int ID_FENCE = 0x00003F; // Fence class // 63
    public static final int ID_COMMAND_POOL = 0x000040; // CommandPool class // 64
    public static final int ID_RENDER_PASS = 0x000041; // RenderPass class // 65
    public static final int ID_COMMAND_BUFFER = 0x000042; // CommandBuffer class // 66
    public static final int ID_VK_PIPELINE_LAYOUT = 0x000043; // VKPipelineLayout class // 67
    public static final int ID_VK_PIPELINE = 0x000044; // VKPipeline class // 68
    public static final int ID_VK_SHADER_MODULE = 0x000045; // VKShaderModule class // 69
    public static final int ID_VK_BUFFER = 0x000046; // VKBuffer class // 70
    public static final int ID_VK_DEVICE_MEMORY = 0x000047; // VKDeviceMemory class // 71
    public static final int ID_VK_IMAGE_VIEW = 0x000048; // VKImageView class // 72
    public static final int ID_VK_FRAMEBUFFER = 0x000049; // VKFramebuffer class // 73


    // --- BUFFER CLASSES ---
    public static final int ID_ACCUMULUATION_BUFFER = 0x000050; // AccumuluationBuffer class
    public static final int ID_AMBIENT_BUFFER = 0x000051; // AmbientBuffer class
    public static final int ID_COLOR_BUFFER = 0x000052; // ColorBuffer class
    public static final int ID_DEFAULT_PIXEL_BUFFER = 0x000053; // DefaultPixelBuffer class
    public static final int ID_DEPTH_BUFFER = 0x000054; // DepthBuffer class
    public static final int ID_FILTER_BUFFER = 0x000055; // FilterBuffer class
    public static final int ID_FRAME_BUFFER = 0x000056; // FrameBuffer class
    public static final int ID_HEIGHT_BUFFER = 0x000057; // HeightBuffer class
    public static final int ID_LIGHT_BUFFER = 0x000058; // LightBuffer class
    public static final int ID_MATERIAL_RESOLVE = 0x000059; // MaterialResolve class
    public static final int ID_MOTION_VECTOR_BUFFER = 0x00005A; // MotionVectorBuffer class
    public static final int ID_NORMAL_BUFFER = 0x00005B; // NormalBuffer class
    public static final int ID_PHYSICAL_BUFFER = 0x00005C; // PhysicalBuffer class
    public static final int ID_POST_PROCESSING_BUFFER = 0x00005D; // PostProcessingBuffer class
    public static final int ID_REFLECTIVITY_BUFFER = 0x00005E; // ReflectivityBuffer class
    public static final int ID_SHADOW_BUFFER = 0x00005F; // ShadowBuffer class
    public static final int ID_SPECULAR_BUFFER = 0x000060; // SpecularBuffer class
    public static final int ID_STENCIL_BUFFER = 0x000061; // StencilBuffer class
    public static final int ID_TRANSPARENCY_BUFFER = 0x000062; // TransparencyBuffer class
    public static final int ID_VISIBILITY_BUFFER = 0x000063; // VisibilityBuffer class
    public static final int ID_COMMAND = 0x000064; // Command class
    public static final int ID_CONSOLE_THREAD = 0x000065; // ConsoleThread class
    public static final int ID_DISPLAY_MONITOR = 0x000066; // DisplayMonitor class
    public static final int ID_AUDIO_SYSTEM = 0x000067; // AudioSystem class
    public static final int ID_AUDIO_SOURCE = 0x000068; // AudioSource class
    public static final int ID_AUDIO_BUFFER = 0x000069; // AudioBuffer class
    public static final int CUSTOM_STRUCT = 0x000100; // Base ID for custom structs
    // --- COMBINED BIT-PACKED TYPE CONSTANTS ---

    // Bool class
    public static final int BOOL_SINGLETON = FORM_SINGLETON | ID_BOOL;
    public static final int BOOL_ARRAY = FORM_ARRAY | ID_BOOL;
    public static final int BOOL_POINTER = FORM_POINTER | ID_BOOL;

    // Semaphore class
    public static final int SEMAPHORE_SINGLETON = FORM_SINGLETON | ID_SEMAPHORE;
    public static final int SEMAPHORE_ARRAY = FORM_ARRAY | ID_SEMAPHORE;
    public static final int SEMAPHORE_POINTER = FORM_POINTER | ID_SEMAPHORE;

    // Swapchain class
    public static final int SWAPCHAIN_SINGLETON = FORM_SINGLETON | ID_SWAPCHAIN;
    public static final int SWAPCHAIN_ARRAY = FORM_ARRAY | ID_SWAPCHAIN;
    public static final int SWAPCHAIN_POINTER = FORM_POINTER | ID_SWAPCHAIN;

    // Fence class
    public static final int FENCE_SINGLETON = FORM_SINGLETON | ID_FENCE;
    public static final int FENCE_ARRAY = FORM_ARRAY | ID_FENCE;
    public static final int FENCE_POINTER = FORM_POINTER | ID_FENCE;

    // CommandPool class
    public static final int COMMAND_POOL_SINGLETON = FORM_SINGLETON | ID_COMMAND_POOL;
    public static final int COMMAND_POOL_ARRAY = FORM_ARRAY | ID_COMMAND_POOL;
    public static final int COMMAND_POOL_POINTER = FORM_POINTER | ID_COMMAND_POOL;

    // RenderPass class
    public static final int RENDER_PASS_SINGLETON = FORM_SINGLETON | ID_RENDER_PASS;
    public static final int RENDER_PASS_ARRAY = FORM_ARRAY | ID_RENDER_PASS;
    public static final int RENDER_PASS_POINTER = FORM_POINTER | ID_RENDER_PASS;

    // CommandBuffer class
    public static final int COMMAND_BUFFER_SINGLETON = FORM_SINGLETON | ID_COMMAND_BUFFER;
    public static final int COMMAND_BUFFER_ARRAY = FORM_ARRAY | ID_COMMAND_BUFFER;
    public static final int COMMAND_BUFFER_POINTER = FORM_POINTER | ID_COMMAND_BUFFER;

    // VKPipelineLayout class
    public static final int VK_PIPELINE_LAYOUT_SINGLETON = FORM_SINGLETON | ID_VK_PIPELINE_LAYOUT;
    public static final int VK_PIPELINE_LAYOUT_ARRAY = FORM_ARRAY | ID_VK_PIPELINE_LAYOUT;
    public static final int VK_PIPELINE_LAYOUT_POINTER = FORM_POINTER | ID_VK_PIPELINE_LAYOUT;

    // VKPipeline class
    public static final int VK_PIPELINE_SINGLETON = FORM_SINGLETON | ID_VK_PIPELINE;
    public static final int VK_PIPELINE_ARRAY = FORM_ARRAY | ID_VK_PIPELINE;
    public static final int VK_PIPELINE_POINTER = FORM_POINTER | ID_VK_PIPELINE;

    // VKShaderModule class
    public static final int VK_SHADER_MODULE_SINGLETON = FORM_SINGLETON | ID_VK_SHADER_MODULE;
    public static final int VK_SHADER_MODULE_ARRAY = FORM_ARRAY | ID_VK_SHADER_MODULE;
    public static final int VK_SHADER_MODULE_POINTER = FORM_POINTER | ID_VK_SHADER_MODULE;

    // VKBuffer class
    public static final int VK_BUFFER_SINGLETON = FORM_SINGLETON | ID_VK_BUFFER;
    public static final int VK_BUFFER_ARRAY = FORM_ARRAY | ID_VK_BUFFER;
    public static final int VK_BUFFER_POINTER = FORM_POINTER | ID_VK_BUFFER;

    // VKDeviceMemory class
    public static final int VK_DEVICE_MEMORY_SINGLETON = FORM_SINGLETON | ID_VK_DEVICE_MEMORY;
    public static final int VK_DEVICE_MEMORY_ARRAY = FORM_ARRAY | ID_VK_DEVICE_MEMORY;
    public static final int VK_DEVICE_MEMORY_POINTER = FORM_POINTER | ID_VK_DEVICE_MEMORY;

    // VKImageView class
    public static final int VK_IMAGE_VIEW_SINGLETON = FORM_SINGLETON | ID_VK_IMAGE_VIEW;
    public static final int VK_IMAGE_VIEW_ARRAY = FORM_ARRAY | ID_VK_IMAGE_VIEW;
    public static final int VK_IMAGE_VIEW_POINTER = FORM_POINTER | ID_VK_IMAGE_VIEW;

    // VKFramebuffer class
    public static final int VK_FRAMEBUFFER_SINGLETON = FORM_SINGLETON | ID_VK_FRAMEBUFFER;
    public static final int VK_FRAMEBUFFER_ARRAY = FORM_ARRAY | ID_VK_FRAMEBUFFER;
    public static final int VK_FRAMEBUFFER_POINTER = FORM_POINTER | ID_VK_FRAMEBUFFER;

    // Int class
    public static final int INT_SINGLETON = FORM_SINGLETON | ID_INT;
    public static final int INT_ARRAY = FORM_ARRAY | ID_INT;
    public static final int INT_POINTER = FORM_POINTER | ID_INT;

    // Long class
    public static final int LONG_SINGLETON = FORM_SINGLETON | ID_LONG;
    public static final int LONG_ARRAY = FORM_ARRAY | ID_LONG;
    public static final int LONG_POINTER = FORM_POINTER | ID_LONG;

    // Float class
    public static final int FLOAT_SINGLETON = FORM_SINGLETON | ID_FLOAT;
    public static final int FLOAT_ARRAY = FORM_ARRAY | ID_FLOAT;
    public static final int FLOAT_POINTER = FORM_POINTER | ID_FLOAT;

    // Double class
    public static final int DOUBLE_SINGLETON = FORM_SINGLETON | ID_DOUBLE;
    public static final int DOUBLE_ARRAY = FORM_ARRAY | ID_DOUBLE;
    public static final int DOUBLE_POINTER = FORM_POINTER | ID_DOUBLE;

    // Byte class
    public static final int BYTE_SINGLETON = FORM_SINGLETON | ID_BYTE;
    public static final int BYTE_ARRAY = FORM_ARRAY | ID_BYTE;
    public static final int BYTE_POINTER = FORM_POINTER | ID_BYTE;

    // Short class
    public static final int SHORT_SINGLETON = FORM_SINGLETON | ID_SHORT;
    public static final int SHORT_ARRAY = FORM_ARRAY | ID_SHORT;
    public static final int SHORT_POINTER = FORM_POINTER | ID_SHORT;

    // string class
    public static final int STRING_SINGLETON = FORM_SINGLETON | ID_STRING;
    public static final int STRING_ARRAY = FORM_ARRAY | ID_STRING;
    public static final int STRING_POINTER = FORM_POINTER | ID_STRING;

    // IntFloat class
    public static final int INT_FLOAT_SINGLETON = FORM_SINGLETON | ID_INT_FLOAT;
    public static final int INT_FLOAT_ARRAY = FORM_ARRAY | ID_INT_FLOAT;
    public static final int INT_FLOAT_POINTER = FORM_POINTER | ID_INT_FLOAT;

    // IntDouble class
    public static final int INT_DOUBLE_SINGLETON = FORM_SINGLETON | ID_INT_DOUBLE;
    public static final int INT_DOUBLE_ARRAY = FORM_ARRAY | ID_INT_DOUBLE;
    public static final int INT_DOUBLE_POINTER = FORM_POINTER | ID_INT_DOUBLE;

    // LongFloat class
    public static final int LONG_FLOAT_SINGLETON = FORM_SINGLETON | ID_LONG_FLOAT;
    public static final int LONG_FLOAT_ARRAY = FORM_ARRAY | ID_LONG_FLOAT;
    public static final int LONG_FLOAT_POINTER = FORM_POINTER | ID_LONG_FLOAT;

    // LongDouble class
    public static final int LONG_DOUBLE_SINGLETON = FORM_SINGLETON | ID_LONG_DOUBLE;
    public static final int LONG_DOUBLE_ARRAY = FORM_ARRAY | ID_LONG_DOUBLE;
    public static final int LONG_DOUBLE_POINTER = FORM_POINTER | ID_LONG_DOUBLE;

    // Variable class
    public static final int VARIABLE_SINGLETON = FORM_SINGLETON | ID_VARIABLE;
    public static final int VARIABLE_ARRAY = FORM_ARRAY | ID_VARIABLE;
    public static final int VARIABLE_POINTER = FORM_POINTER | ID_VARIABLE;

    // Random class
    public static final int RANDOM_SINGLETON = FORM_SINGLETON | ID_RANDOM;
    public static final int INDEX_RANDOM_SINGLETON = FORM_SINGLETON | ID_INDEX_RANDOM;

    // Time classes
    public static final int CLOCK_SINGLETON = FORM_SINGLETON | ID_CLOCK;
    public static final int DATETIME_SINGLETON = FORM_SINGLETON | ID_DATETIME;
    public static final int NANOTIME_SINGLETON = FORM_SINGLETON | ID_NANOTIME;

    // Brain bfloat16 class
    public static final int BRAIN_SINGLETON = FORM_SINGLETON | ID_BRAIN;
    public static final int BRAIN_ARRAY = FORM_ARRAY | ID_BRAIN;
    public static final int BRAIN_POINTER = FORM_POINTER | ID_BRAIN;

    // Fixed32 q16.16 class
    public static final int FIXED32_SINGLETON = FORM_SINGLETON | ID_FIXED32;
    public static final int FIXED32_ARRAY = FORM_ARRAY | ID_FIXED32;
    public static final int FIXED32_POINTER = FORM_POINTER | ID_FIXED32;

    // Fixed64 q32.32 class
    public static final int FIXED64_SINGLETON = FORM_SINGLETON | ID_FIXED64;
    public static final int FIXED64_ARRAY = FORM_ARRAY | ID_FIXED64;
    public static final int FIXED64_POINTER = FORM_POINTER | ID_FIXED64;

    // StringBuilder class
    public static final int STRING_BUILDER_SINGLETON = FORM_SINGLETON | ID_STRING_BUILDER;
    public static final int STRING_BUILDER_ARRAY = FORM_ARRAY | ID_STRING_BUILDER;
    public static final int STRING_BUILDER_POINTER = FORM_POINTER | ID_STRING_BUILDER;

    // HTTPClient class
    public static final int HTTP_CLIENT_SINGLETON = FORM_SINGLETON | ID_HTTP_CLIENT;
    public static final int HTTP_CLIENT_ARRAY = FORM_ARRAY | ID_HTTP_CLIENT;
    public static final int HTTP_CLIENT_POINTER = FORM_POINTER | ID_HTTP_CLIENT;

    // JSON class
    public static final int JSON_SINGLETON = FORM_SINGLETON | ID_JSON;
    public static final int JSON_ARRAY = FORM_ARRAY | ID_JSON;
    public static final int JSON_POINTER = FORM_POINTER | ID_JSON;

    // PollRequest class
    public static final int POLL_REQUEST_SINGLETON = FORM_SINGLETON | ID_POLL_REQUEST;
    public static final int POLL_REQUEST_ARRAY = FORM_ARRAY | ID_POLL_REQUEST;
    public static final int POLL_REQUEST_POINTER = FORM_POINTER | ID_POLL_REQUEST;

    // TransportProtocol class
    public static final int TRANSPORT_PROTOCOL_SINGLETON = FORM_SINGLETON | ID_TRANSPORT_PROTOCOL;
    public static final int TRANSPORT_PROTOCOL_ARRAY = FORM_ARRAY | ID_TRANSPORT_PROTOCOL;
    public static final int TRANSPORT_PROTOCOL_POINTER = FORM_POINTER | ID_TRANSPORT_PROTOCOL;

    // NetworkingThread class
    public static final int NETWORKING_THREAD_SINGLETON = FORM_SINGLETON | ID_NETWORKING_THREAD;
    public static final int NETWORKING_THREAD_ARRAY = FORM_ARRAY | ID_NETWORKING_THREAD;
    public static final int NETWORKING_THREAD_POINTER = FORM_POINTER | ID_NETWORKING_THREAD;

    // HTTPServer class
    public static final int HTTP_SERVER_SINGLETON = FORM_SINGLETON | ID_HTTP_SERVER;
    public static final int HTTP_SERVER_ARRAY = FORM_ARRAY | ID_HTTP_SERVER;
    public static final int HTTP_SERVER_POINTER = FORM_POINTER | ID_HTTP_SERVER;

    // WebSocketClient class
    public static final int WEBSOCKET_CLIENT_SINGLETON = FORM_SINGLETON | ID_WEBSOCKET_CLIENT;
    public static final int WEBSOCKET_CLIENT_ARRAY = FORM_ARRAY | ID_WEBSOCKET_CLIENT;
    public static final int WEBSOCKET_CLIENT_POINTER = FORM_POINTER | ID_WEBSOCKET_CLIENT;

    // ScriptingThread class
    public static final int SCRIPTING_THREAD_SINGLETON = FORM_SINGLETON | ID_SCRIPTING_THREAD;
    public static final int SCRIPTING_THREAD_ARRAY = FORM_ARRAY | ID_SCRIPTING_THREAD;
    public static final int SCRIPTING_THREAD_POINTER = FORM_POINTER | ID_SCRIPTING_THREAD;

    // DrawThread class
    public static final int DRAW_THREAD_SINGLETON = FORM_SINGLETON | ID_DRAW_THREAD;
    public static final int DRAW_THREAD_ARRAY = FORM_ARRAY | ID_DRAW_THREAD;
    public static final int DRAW_THREAD_POINTER = FORM_POINTER | ID_DRAW_THREAD;


    // --- HELPER BITWISE METHODS ---
    public static boolean isSingleton(int typeId)
    {
        return (typeId & MASK_FORM) == FORM_SINGLETON;
    }

    public static boolean isArray(int typeId)
    {
        return (typeId & MASK_FORM) == FORM_ARRAY;
    }

    public static boolean isPointer(int typeId)
    {
        return (typeId & MASK_FORM) == FORM_POINTER;
    }

    public static boolean isStructSingleton(int typeId)
    {
        return (typeId & MASK_FORM) == FORM_STRUCT_SINGLETON;
    }

    public static boolean isStructArray(int typeId)
    {
        return (typeId & MASK_FORM) == FORM_STRUCT_ARRAY;
    }

    public static boolean isStructPointer(int typeId)
    {
        return (typeId & MASK_FORM) == FORM_STRUCT_POINTER;
    }

    public static boolean isStruct(int typeId)
    {
        int form = typeId & MASK_FORM;
        return form == FORM_STRUCT_SINGLETON || form == FORM_STRUCT_ARRAY || form == FORM_STRUCT_POINTER;
    }

    public static boolean isPrimitive(int typeId)
    {
        int form = typeId & MASK_FORM;
        return form == FORM_SINGLETON || form == FORM_ARRAY || form == FORM_POINTER;
    }

    public static boolean isGlobal(int typeId)
    {
        return (typeId & MASK_MODIFIER) == MOD_GLOBAL;
    }

    public static boolean isLocale(int typeId)
    {
        return (typeId & MASK_MODIFIER) == MOD_LOCALE;
    }

    public static boolean isTransient(int typeId)
    {
        return (typeId & MASK_MODIFIER) == MOD_TRANSIENT;
    }

    public static boolean isProactive(int typeId)
    {
        return (typeId & MASK_WRAPPER_1) == WRAP_PROACTIVE;
    }

    public static boolean isReactive(int typeId)
    {
        return (typeId & MASK_WRAPPER_1) == WRAP_REACTIVE;
    }

    public static boolean isProbable(int typeId)
    {
        return (typeId & MASK_WRAPPER_2) == WRAP_PROBABLE;
    }

    public static boolean isProbableObjects(int typeId)
    {
        return (typeId & MASK_WRAPPER_2) == WRAP_PROBABLE_OBJECTS;
    }

    public static boolean isFuture(int typeId)
    {
        return (typeId & MASK_WRAPPER_2) == WRAP_FUTURE;
    }

    public static boolean isChoice(int typeId)
    {
        return (typeId & MASK_WRAPPER_2) == WRAP_CHOICE;
    }

    public static int getClassId(int typeId)
    {
        return typeId & MASK_CLASS;
    }

    // AccumuluationBuffer class
    public static final int ACCUMULUATION_BUFFER_SINGLETON = FORM_SINGLETON | ID_ACCUMULUATION_BUFFER;
    public static final int ACCUMULUATION_BUFFER_ARRAY = FORM_ARRAY | ID_ACCUMULUATION_BUFFER;
    public static final int ACCUMULUATION_BUFFER_POINTER = FORM_POINTER | ID_ACCUMULUATION_BUFFER;

    // AmbientBuffer class
    public static final int AMBIENT_BUFFER_SINGLETON = FORM_SINGLETON | ID_AMBIENT_BUFFER;
    public static final int AMBIENT_BUFFER_ARRAY = FORM_ARRAY | ID_AMBIENT_BUFFER;
    public static final int AMBIENT_BUFFER_POINTER = FORM_POINTER | ID_AMBIENT_BUFFER;

    // ColorBuffer class
    public static final int COLOR_BUFFER_SINGLETON = FORM_SINGLETON | ID_COLOR_BUFFER;
    public static final int COLOR_BUFFER_ARRAY = FORM_ARRAY | ID_COLOR_BUFFER;
    public static final int COLOR_BUFFER_POINTER = FORM_POINTER | ID_COLOR_BUFFER;

    // DefaultPixelBuffer class
    public static final int DEFAULT_PIXEL_BUFFER_SINGLETON = FORM_SINGLETON | ID_DEFAULT_PIXEL_BUFFER;
    public static final int DEFAULT_PIXEL_BUFFER_ARRAY = FORM_ARRAY | ID_DEFAULT_PIXEL_BUFFER;
    public static final int DEFAULT_PIXEL_BUFFER_POINTER = FORM_POINTER | ID_DEFAULT_PIXEL_BUFFER;

    // DepthBuffer class
    public static final int DEPTH_BUFFER_SINGLETON = FORM_SINGLETON | ID_DEPTH_BUFFER;
    public static final int DEPTH_BUFFER_ARRAY = FORM_ARRAY | ID_DEPTH_BUFFER;
    public static final int DEPTH_BUFFER_POINTER = FORM_POINTER | ID_DEPTH_BUFFER;

    // FilterBuffer class
    public static final int FILTER_BUFFER_SINGLETON = FORM_SINGLETON | ID_FILTER_BUFFER;
    public static final int FILTER_BUFFER_ARRAY = FORM_ARRAY | ID_FILTER_BUFFER;
    public static final int FILTER_BUFFER_POINTER = FORM_POINTER | ID_FILTER_BUFFER;

    // FrameBuffer class
    public static final int FRAME_BUFFER_SINGLETON = FORM_SINGLETON | ID_FRAME_BUFFER;
    public static final int FRAME_BUFFER_ARRAY = FORM_ARRAY | ID_FRAME_BUFFER;
    public static final int FRAME_BUFFER_POINTER = FORM_POINTER | ID_FRAME_BUFFER;

    // HeightBuffer class
    public static final int HEIGHT_BUFFER_SINGLETON = FORM_SINGLETON | ID_HEIGHT_BUFFER;
    public static final int HEIGHT_BUFFER_ARRAY = FORM_ARRAY | ID_HEIGHT_BUFFER;
    public static final int HEIGHT_BUFFER_POINTER = FORM_POINTER | ID_HEIGHT_BUFFER;

    // LightBuffer class
    public static final int LIGHT_BUFFER_SINGLETON = FORM_SINGLETON | ID_LIGHT_BUFFER;
    public static final int LIGHT_BUFFER_ARRAY = FORM_ARRAY | ID_LIGHT_BUFFER;
    public static final int LIGHT_BUFFER_POINTER = FORM_POINTER | ID_LIGHT_BUFFER;

    // MaterialResolve class
    public static final int MATERIAL_RESOLVE_SINGLETON = FORM_SINGLETON | ID_MATERIAL_RESOLVE;
    public static final int MATERIAL_RESOLVE_ARRAY = FORM_ARRAY | ID_MATERIAL_RESOLVE;
    public static final int MATERIAL_RESOLVE_POINTER = FORM_POINTER | ID_MATERIAL_RESOLVE;

    // MotionVectorBuffer class
    public static final int MOTION_VECTOR_BUFFER_SINGLETON = FORM_SINGLETON | ID_MOTION_VECTOR_BUFFER;
    public static final int MOTION_VECTOR_BUFFER_ARRAY = FORM_ARRAY | ID_MOTION_VECTOR_BUFFER;
    public static final int MOTION_VECTOR_BUFFER_POINTER = FORM_POINTER | ID_MOTION_VECTOR_BUFFER;

    // NormalBuffer class
    public static final int NORMAL_BUFFER_SINGLETON = FORM_SINGLETON | ID_NORMAL_BUFFER;
    public static final int NORMAL_BUFFER_ARRAY = FORM_ARRAY | ID_NORMAL_BUFFER;
    public static final int NORMAL_BUFFER_POINTER = FORM_POINTER | ID_NORMAL_BUFFER;

    // PhysicalBuffer class
    public static final int PHYSICAL_BUFFER_SINGLETON = FORM_SINGLETON | ID_PHYSICAL_BUFFER;
    public static final int PHYSICAL_BUFFER_ARRAY = FORM_ARRAY | ID_PHYSICAL_BUFFER;
    public static final int PHYSICAL_BUFFER_POINTER = FORM_POINTER | ID_PHYSICAL_BUFFER;

    // PostProcessingBuffer class
    public static final int POST_PROCESSING_BUFFER_SINGLETON = FORM_SINGLETON | ID_POST_PROCESSING_BUFFER;
    public static final int POST_PROCESSING_BUFFER_ARRAY = FORM_ARRAY | ID_POST_PROCESSING_BUFFER;
    public static final int POST_PROCESSING_BUFFER_POINTER = FORM_POINTER | ID_POST_PROCESSING_BUFFER;

    // ReflectivityBuffer class
    public static final int REFLECTIVITY_BUFFER_SINGLETON = FORM_SINGLETON | ID_REFLECTIVITY_BUFFER;
    public static final int REFLECTIVITY_BUFFER_ARRAY = FORM_ARRAY | ID_REFLECTIVITY_BUFFER;
    public static final int REFLECTIVITY_BUFFER_POINTER = FORM_POINTER | ID_REFLECTIVITY_BUFFER;

    // ShadowBuffer class
    public static final int SHADOW_BUFFER_SINGLETON = FORM_SINGLETON | ID_SHADOW_BUFFER;
    public static final int SHADOW_BUFFER_ARRAY = FORM_ARRAY | ID_SHADOW_BUFFER;
    public static final int SHADOW_BUFFER_POINTER = FORM_POINTER | ID_SHADOW_BUFFER;

    // SpecularBuffer class
    public static final int SPECULAR_BUFFER_SINGLETON = FORM_SINGLETON | ID_SPECULAR_BUFFER;
    public static final int SPECULAR_BUFFER_ARRAY = FORM_ARRAY | ID_SPECULAR_BUFFER;
    public static final int SPECULAR_BUFFER_POINTER = FORM_POINTER | ID_SPECULAR_BUFFER;

    // StencilBuffer class
    public static final int STENCIL_BUFFER_SINGLETON = FORM_SINGLETON | ID_STENCIL_BUFFER;
    public static final int STENCIL_BUFFER_ARRAY = FORM_ARRAY | ID_STENCIL_BUFFER;
    public static final int STENCIL_BUFFER_POINTER = FORM_POINTER | ID_STENCIL_BUFFER;

    // TransparencyBuffer class
    public static final int TRANSPARENCY_BUFFER_SINGLETON = FORM_SINGLETON | ID_TRANSPARENCY_BUFFER;
    public static final int TRANSPARENCY_BUFFER_ARRAY = FORM_ARRAY | ID_TRANSPARENCY_BUFFER;
    public static final int TRANSPARENCY_BUFFER_POINTER = FORM_POINTER | ID_TRANSPARENCY_BUFFER;

    // VisibilityBuffer class
    public static final int VISIBILITY_BUFFER_SINGLETON = FORM_SINGLETON | ID_VISIBILITY_BUFFER;
    public static final int VISIBILITY_BUFFER_ARRAY = FORM_ARRAY | ID_VISIBILITY_BUFFER;
    public static final int VISIBILITY_BUFFER_POINTER = FORM_POINTER | ID_VISIBILITY_BUFFER;

    // AudioSystem class
    public static final int AUDIO_SYSTEM_SINGLETON = FORM_SINGLETON | ID_AUDIO_SYSTEM;
    public static final int AUDIO_SYSTEM_ARRAY = FORM_ARRAY | ID_AUDIO_SYSTEM;
    public static final int AUDIO_SYSTEM_POINTER = FORM_POINTER | ID_AUDIO_SYSTEM;

    // AudioSource class
    public static final int AUDIO_SOURCE_SINGLETON = FORM_SINGLETON | ID_AUDIO_SOURCE;
    public static final int AUDIO_SOURCE_ARRAY = FORM_ARRAY | ID_AUDIO_SOURCE;
    public static final int AUDIO_SOURCE_POINTER = FORM_POINTER | ID_AUDIO_SOURCE;

    // AudioBuffer class
    public static final int AUDIO_BUFFER_SINGLETON = FORM_SINGLETON | ID_AUDIO_BUFFER;
    public static final int AUDIO_BUFFER_ARRAY = FORM_ARRAY | ID_AUDIO_BUFFER;
    public static final int AUDIO_BUFFER_POINTER = FORM_POINTER | ID_AUDIO_BUFFER;

}
