package oop;

import annotation.Intention;
import annotation.HotCode;

@HotCode
@Intention("Central type registry using bit-packed type IDs: Upper 8 bits = Form (AA=Singleton, BB=Array, CC=Pointer), Lower 24 bits = Class ID")
public class TypeRegister {

    // --- FORM / KIND BIT MASKS (Upper 8 bits) ---
    public static final int MASK_FORM        = 0xFF000000; // -16777216
    public static final int MASK_CLASS       = 0x00FFFFFF; // 16777215


    public static final int FORM_SINGLETON   = 0xAA000000; // -1442840576
    public static final int FORM_ARRAY       = 0xBB000000; // -1157627904
    public static final int FORM_POINTER     = 0xCC000000; // -872415232

    public static final int CUSTOM_STRUCT     = 0x00110000; // custom struct

    // --- RAW CLASS IDS (Lower 24 bits) ---
    public static final int ID_INT      = 0x000001; // Int class // 1
    public static final int ID_LONG      = 0x000002; // Long class // 2
    public static final int ID_FLOAT    = 0x000003; // Float class // 3
    public static final int ID_DOUBLE    = 0x000004; // Double class // 4
    public static final int ID_BYTE       = 0x000005; // Byte class // 5
    public static final int ID_SHORT      = 0x000006; // Short class // 6
    public static final int ID_STRING     = 0x000007; // string class // 7
    public static final int ID_INT_FLOAT = 0x000008; // IntFloat class // 8
    public static final int ID_INT_DOUBLE = 0x000009; // IntDouble class // 9
    public static final int ID_LONG_FLOAT = 0x00000A; // LongFloat class // 10
    public static final int ID_LONG_DOUBLE = 0x00000B; // LongDouble class // 11
    public static final int ID_VARIABLE   = 0x00000C; // Variable class // 12
    public static final int ID_PACK       = 0x00000D; // Pack class // 13
    public static final int ID_ARRAYS     = 0x00000E; // Arrays class // 14
    public static final int ID_HASH       = 0x00000F; // Hash class // 15
    public static final int ID_CLASS      = 0x000010; // Class class // 16
    public static final int ID_STRIDE     = 0x000011; // Stride class // 17
    public static final int ID_LIST       = 0x000012; // List class // 18
    public static final int ID_MAP        = 0x000013; // Map class // 19
    public static final int ID_SET        = 0x000014; // Set class // 20
    public static final int ID_STACK          = 0x000015; // Stack class // 21
    public static final int ID_DEQUE          = 0x000016; // Deque class // 22
    public static final int ID_SLAB_ALLOCATOR = 0x000017; // SlabAllocator class // 23
    public static final int ID_STRING_ENGINE  = 0x000018; // StringEngine class // 24
    public static final int ID_SPIN_LOCK       = 0x000019; // SpinLock class // 25
    public static final int ID_RING_BUFFER     = 0x00001A; // RingBuffer class // 26
    public static final int ID_MEMORY_MAP_MANAGER = 0x00001B; // MemoryMapManager class // 27
    public static final int ID_TRIE            = 0x00001C; // Trie class // 28
    public static final int ID_SEARCH_VARIABLE  = 0x00001D; // SearchVariable class // 29
    public static final int ID_RANDOM           = 0x00001E; // Random class // 30
    public static final int ID_INDEX_RANDOM     = 0x00001F; // IndexRandom class // 31
    public static final int ID_CALENDAR         = 0x000020; // Calendar class // 32
    public static final int ID_CLOCK            = 0x000021; // Clock class // 33
    public static final int ID_DATETIME         = 0x000022; // DateTime class // 34
    public static final int ID_NANOTIME         = 0x000023; // NanoTime class // 35
    public static final int ID_GRID_ARRAY       = 0x000024; // GridArray class // 36
    public static final int ID_OCTREE           = 0x000025; // Octree class // 37
    public static final int ID_CUBE_ARRAY       = 0x000026; // CubeArray class // 38
    public static final int ID_SPHERE_ARRAY     = 0x000027; // SphereArray class // 39
    public static final int ID_CIRCULAR_ARRAY   = 0x000028; // CircularArray class // 40
    public static final int ID_BRAIN            = 0x000029; // Brain class // 41
    public static final int ID_FIXED32          = 0x00002A; // Fixed32 class // 42
    public static final int ID_FIXED64          = 0x00002B; // Fixed64 class // 43
    public static final int ID_STRING_BUILDER   = 0x00002C; // StringBuilder class // 44
    public static final int ID_HTTP_CLIENT      = 0x00002D; // HTTPClient class // 45
    public static final int ID_JSON             = 0x00002E; // JSON class // 46





    // --- COMBINED BIT-PACKED TYPE CONSTANTS ---

    // Int class
    public static final int INT_SINGLETON = FORM_SINGLETON | ID_INT; // 0xAA000001 // -1442840575
    public static final int INT_ARRAY     = FORM_ARRAY     | ID_INT; // 0xBB000001 // -1157627903
    public static final int INT_POINTER   = FORM_POINTER   | ID_INT; // 0xCC000001 // -872415231

    // Long class
    public static final int LONG_SINGLETON = FORM_SINGLETON | ID_LONG; // 0xAA000002 // -1442840574
    public static final int LONG_ARRAY     = FORM_ARRAY     | ID_LONG; // 0xBB000002 // -1157627902
    public static final int LONG_POINTER   = FORM_POINTER   | ID_LONG; // 0xCC000002 // -872415230

    // Float class
    public static final int FLOAT_SINGLETON = FORM_SINGLETON | ID_FLOAT; // 0xAA000003 // -1442840573
    public static final int FLOAT_ARRAY     = FORM_ARRAY     | ID_FLOAT; // 0xBB000003 // -1157627901
    public static final int FLOAT_POINTER   = FORM_POINTER   | ID_FLOAT; // 0xCC000003 // -872415229

    // Double class
    public static final int DOUBLE_SINGLETON = FORM_SINGLETON | ID_DOUBLE; // 0xAA000004 // -1442840572
    public static final int DOUBLE_ARRAY     = FORM_ARRAY     | ID_DOUBLE; // 0xBB000004 // -1157627900
    public static final int DOUBLE_POINTER   = FORM_POINTER   | ID_DOUBLE; // 0xCC000004 // -872415228

    // Byte class
    public static final int BYTE_SINGLETON = FORM_SINGLETON | ID_BYTE; // 0xAA000005 // -1442840571
    public static final int BYTE_ARRAY     = FORM_ARRAY     | ID_BYTE; // 0xBB000005 // -1157627899
    public static final int BYTE_POINTER   = FORM_POINTER   | ID_BYTE; // 0xCC000005 // -872415227

    // Short class
    public static final int SHORT_SINGLETON = FORM_SINGLETON | ID_SHORT; // 0xAA000006 // -1442840570
    public static final int SHORT_ARRAY     = FORM_ARRAY     | ID_SHORT; // 0xBB000006 // -1157627898
    public static final int SHORT_POINTER   = FORM_POINTER   | ID_SHORT; // 0xCC000006 // -872415226

    // string class
    public static final int STRING_SINGLETON = FORM_SINGLETON | ID_STRING; // 0xAA000007 // -1442840569
    public static final int STRING_ARRAY     = FORM_ARRAY     | ID_STRING; // 0xBB000007 // -1157627897
    public static final int STRING_POINTER   = FORM_POINTER   | ID_STRING; // 0xCC000007 // -872415225

    // IntFloat class
    public static final int INT_FLOAT_SINGLETON = FORM_SINGLETON | ID_INT_FLOAT; // 0xAA000008 // -1442840568
    public static final int INT_FLOAT_ARRAY     = FORM_ARRAY     | ID_INT_FLOAT; // 0xBB000008 // -1157627896
    public static final int INT_FLOAT_POINTER   = FORM_POINTER   | ID_INT_FLOAT; // 0xCC000008 // -872415224

    // IntDouble class
    public static final int INT_DOUBLE_SINGLETON = FORM_SINGLETON | ID_INT_DOUBLE; // 0xAA000009 // -1442840567
    public static final int INT_DOUBLE_ARRAY     = FORM_ARRAY     | ID_INT_DOUBLE; // 0xBB000009 // -1157627895
    public static final int INT_DOUBLE_POINTER   = FORM_POINTER   | ID_INT_DOUBLE; // 0xCC000009 // -872415223

    // LongFloat class
    public static final int LONG_FLOAT_SINGLETON = FORM_SINGLETON | ID_LONG_FLOAT; // 0xAA00000A // -1442840566
    public static final int LONG_FLOAT_ARRAY     = FORM_ARRAY     | ID_LONG_FLOAT; // 0xBB00000A // -1157627894
    public static final int LONG_FLOAT_POINTER   = FORM_POINTER   | ID_LONG_FLOAT; // 0xCC00000A // -872415222

    // LongDouble class
    public static final int LONG_DOUBLE_SINGLETON = FORM_SINGLETON | ID_LONG_DOUBLE; // 0xAA00000B // -1442840565
    public static final int LONG_DOUBLE_ARRAY     = FORM_ARRAY     | ID_LONG_DOUBLE; // 0xBB00000B // -1157627893
    public static final int LONG_DOUBLE_POINTER   = FORM_POINTER   | ID_LONG_DOUBLE; // 0xCC00000B // -872415221

    // Variable class
    public static final int VARIABLE_SINGLETON = FORM_SINGLETON | ID_VARIABLE; // 0xAA00000C // -1442840564
    public static final int VARIABLE_ARRAY     = FORM_ARRAY     | ID_VARIABLE; // 0xBB00000C // -1157627892
    public static final int VARIABLE_POINTER   = FORM_POINTER   | ID_VARIABLE; // 0xCC00000C // -872415220

    // Random class
    public static final int RANDOM_SINGLETON = FORM_SINGLETON | ID_RANDOM; // 0xAA00001E
    public static final int INDEX_RANDOM_SINGLETON = FORM_SINGLETON | ID_INDEX_RANDOM; // 0xAA00001F

    // Time classes
    public static final int CLOCK_SINGLETON = FORM_SINGLETON | ID_CLOCK; // 0xAA000021
    public static final int DATETIME_SINGLETON = FORM_SINGLETON | ID_DATETIME; // 0xAA000022
    public static final int NANOTIME_SINGLETON = FORM_SINGLETON | ID_NANOTIME; // 0xAA000023

    // Brain bfloat16 class
    public static final int BRAIN_SINGLETON = FORM_SINGLETON | ID_BRAIN; // 0xAA000029
    public static final int BRAIN_ARRAY     = FORM_ARRAY     | ID_BRAIN; // 0xBB000029
    public static final int BRAIN_POINTER   = FORM_POINTER   | ID_BRAIN; // 0xCC000029

    // Fixed32 q16.16 class
    public static final int FIXED32_SINGLETON = FORM_SINGLETON | ID_FIXED32; // 0xAA00002A
    public static final int FIXED32_ARRAY     = FORM_ARRAY     | ID_FIXED32; // 0xBB00002A
    public static final int FIXED32_POINTER   = FORM_POINTER   | ID_FIXED32; // 0xCC00002A

    // Fixed64 q32.32 class
    public static final int FIXED64_SINGLETON = FORM_SINGLETON | ID_FIXED64; // 0xAA00002B
    public static final int FIXED64_ARRAY     = FORM_ARRAY     | ID_FIXED64; // 0xBB00002B
    public static final int FIXED64_POINTER   = FORM_POINTER   | ID_FIXED64; // 0xCC00002B

    // StringBuilder class
    public static final int STRING_BUILDER_SINGLETON = FORM_SINGLETON | ID_STRING_BUILDER; // 0xAA00002C
    public static final int STRING_BUILDER_ARRAY     = FORM_ARRAY     | ID_STRING_BUILDER; // 0xBB00002C
    public static final int STRING_BUILDER_POINTER   = FORM_POINTER   | ID_STRING_BUILDER; // 0xCC00002C

    // HTTPClient class
    public static final int HTTP_CLIENT_SINGLETON = FORM_SINGLETON | ID_HTTP_CLIENT; // 0xAA00002D
    public static final int HTTP_CLIENT_ARRAY     = FORM_ARRAY     | ID_HTTP_CLIENT; // 0xBB00002D
    public static final int HTTP_CLIENT_POINTER   = FORM_POINTER   | ID_HTTP_CLIENT; // 0xCC00002D

    // JSON class
    public static final int JSON_SINGLETON = FORM_SINGLETON | ID_JSON; // 0xAA00002E
    public static final int JSON_ARRAY     = FORM_ARRAY     | ID_JSON; // 0xBB00002E
    public static final int JSON_POINTER   = FORM_POINTER   | ID_JSON; // 0xCC00002E


    // --- HELPER BITWISE METHODS ---
    public static boolean isSingleton(int typeId) {
        return (typeId & MASK_FORM) == FORM_SINGLETON;
    }

    public static boolean isArray(int typeId) {
        return (typeId & MASK_FORM) == FORM_ARRAY;
    }

    public static boolean isPointer(int typeId) {
        return (typeId & MASK_FORM) == FORM_POINTER;
    }

    public static int getClassId(int typeId) {
        return typeId & MASK_CLASS;
    }
}
