package oop;

import annotation.Intention;
import annotation.Volatile;

@Volatile
@Intention("Central type registry using bit-packed type IDs: Upper 8 bits = Form (AA=Singleton, BB=Array, CC=Pointer), Lower 24 bits = Class ID")
public class TypeRegister {

    // --- FORM / KIND BIT MASKS (Upper 8 bits) ---
    public static final int MASK_FORM        = 0xFF000000; // -16777216
    public static final int MASK_CLASS       = 0x00FFFFFF; // 16777215

    public static final int FORM_SINGLETON   = 0xAA000000; // -1442840576
    public static final int FORM_ARRAY       = 0xBB000000; // -1157627904
    public static final int FORM_POINTER     = 0xCC000000; // -872415232

    // --- RAW CLASS IDS (Lower 24 bits) ---
    public static final int ID_INT32      = 0x000001; // Int32 class // 1
    public static final int ID_INT64      = 0x000002; // Int64 class // 2
    public static final int ID_FLOAT32    = 0x000003; // Float class // 3
    public static final int ID_FLOAT64    = 0x000004; // Double class // 4
    public static final int ID_BYTE       = 0x000005; // Byte class // 5
    public static final int ID_SHORT      = 0x000006; // Short class // 6
    public static final int ID_STRING     = 0x000007; // string class // 7
    public static final int ID_INT32_FP32 = 0x000008; // Int32Fp32 class // 8
    public static final int ID_INT32_FP64 = 0x000009; // Int32Fp64 class // 9
    public static final int ID_INT64_FP32 = 0x00000A; // Int64Fp32 class // 10
    public static final int ID_INT64_FP64 = 0x00000B; // Int64Fp64 class // 11
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




    // --- COMBINED BIT-PACKED TYPE CONSTANTS ---

    // Int32 class
    public static final int INT32_SINGLETON = FORM_SINGLETON | ID_INT32; // 0xAA000001 // -1442840575
    public static final int INT32_ARRAY     = FORM_ARRAY     | ID_INT32; // 0xBB000001 // -1157627903
    public static final int INT32_POINTER   = FORM_POINTER   | ID_INT32; // 0xCC000001 // -872415231

    // Int64 class
    public static final int INT64_SINGLETON = FORM_SINGLETON | ID_INT64; // 0xAA000002 // -1442840574
    public static final int INT64_ARRAY     = FORM_ARRAY     | ID_INT64; // 0xBB000002 // -1157627902
    public static final int INT64_POINTER   = FORM_POINTER   | ID_INT64; // 0xCC000002 // -872415230

    // Float class
    public static final int FLOAT32_SINGLETON = FORM_SINGLETON | ID_FLOAT32; // 0xAA000003 // -1442840573
    public static final int FLOAT32_ARRAY     = FORM_ARRAY     | ID_FLOAT32; // 0xBB000003 // -1157627901
    public static final int FLOAT32_POINTER   = FORM_POINTER   | ID_FLOAT32; // 0xCC000003 // -872415229

    // Double class
    public static final int FLOAT64_SINGLETON = FORM_SINGLETON | ID_FLOAT64; // 0xAA000004 // -1442840572
    public static final int FLOAT64_ARRAY     = FORM_ARRAY     | ID_FLOAT64; // 0xBB000004 // -1157627900
    public static final int FLOAT64_POINTER   = FORM_POINTER   | ID_FLOAT64; // 0xCC000004 // -872415228

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

    // Int32Fp32 class
    public static final int INT32_FP32_SINGLETON = FORM_SINGLETON | ID_INT32_FP32; // 0xAA000008 // -1442840568
    public static final int INT32_FP32_ARRAY     = FORM_ARRAY     | ID_INT32_FP32; // 0xBB000008 // -1157627896
    public static final int INT32_FP32_POINTER   = FORM_POINTER   | ID_INT32_FP32; // 0xCC000008 // -872415224

    // Int32Fp64 class
    public static final int INT32_FP64_SINGLETON = FORM_SINGLETON | ID_INT32_FP64; // 0xAA000009 // -1442840567
    public static final int INT32_FP64_ARRAY     = FORM_ARRAY     | ID_INT32_FP64; // 0xBB000009 // -1157627895
    public static final int INT32_FP64_POINTER   = FORM_POINTER   | ID_INT32_FP64; // 0xCC000009 // -872415223

    // Int64Fp32 class
    public static final int INT64_FP32_SINGLETON = FORM_SINGLETON | ID_INT64_FP32; // 0xAA00000A // -1442840566
    public static final int INT64_FP32_ARRAY     = FORM_ARRAY     | ID_INT64_FP32; // 0xBB00000A // -1157627894
    public static final int INT64_FP32_POINTER   = FORM_POINTER   | ID_INT64_FP32; // 0xCC00000A // -872415222

    // Int64Fp64 class
    public static final int INT64_FP64_SINGLETON = FORM_SINGLETON | ID_INT64_FP64; // 0xAA00000B // -1442840565
    public static final int INT64_FP64_ARRAY     = FORM_ARRAY     | ID_INT64_FP64; // 0xBB00000B // -1157627893
    public static final int INT64_FP64_POINTER   = FORM_POINTER   | ID_INT64_FP64; // 0xCC00000B // -872415221

    // Variable class
    public static final int VARIABLE_SINGLETON = FORM_SINGLETON | ID_VARIABLE; // 0xAA00000C // -1442840564
    public static final int VARIABLE_ARRAY     = FORM_ARRAY     | ID_VARIABLE; // 0xBB00000C // -1157627892
    public static final int VARIABLE_POINTER   = FORM_POINTER   | ID_VARIABLE; // 0xCC00000C // -872415220

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
