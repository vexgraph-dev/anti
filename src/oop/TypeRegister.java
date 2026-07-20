package oop;

import annotation.Intention;
import annotation.Volatile;

@Volatile
@Intention("Central type registry using bit-packed type IDs: Upper 8 bits = Form (AA=Singleton, BB=Array, CC=Pointer), Lower 24 bits = Class ID")
public class TypeRegister {

    // --- FORM / KIND BIT MASKS (Upper 8 bits) ---
    public static final int MASK_FORM        = 0xFF000000;
    public static final int MASK_CLASS       = 0x00FFFFFF;

    public static final int FORM_SINGLETON   = 0xAA000000;
    public static final int FORM_ARRAY       = 0xBB000000;
    public static final int FORM_POINTER     = 0xCC000000;

    // --- RAW CLASS IDS (Lower 24 bits) ---
    public static final int ID_INT32   = 0x000001; // Int32 class
    public static final int ID_INT64   = 0x000002; // Int64 class
    public static final int ID_FLOAT32 = 0x000003; // Float class
    public static final int ID_FLOAT64 = 0x000004; // Double class
    public static final int ID_BYTE    = 0x000005; // Byte class
    public static final int ID_SHORT   = 0x000006; // Short class
    public static final int ID_STRING  = 0x000007; // string class

    // --- COMBINED BIT-PACKED TYPE CONSTANTS ---

    // Int32 class
    public static final int INT32_SINGLETON = FORM_SINGLETON | ID_INT32; // 0xAA000001
    public static final int INT32_ARRAY     = FORM_ARRAY     | ID_INT32; // 0xBB000001
    public static final int INT32_POINTER   = FORM_POINTER   | ID_INT32; // 0xCC000001

    // Int64 class
    public static final int INT64_SINGLETON = FORM_SINGLETON | ID_INT64; // 0xAA000002
    public static final int INT64_ARRAY     = FORM_ARRAY     | ID_INT64; // 0xBB000002
    public static final int INT64_POINTER   = FORM_POINTER   | ID_INT64; // 0xCC000002

    // Float class
    public static final int FLOAT32_SINGLETON = FORM_SINGLETON | ID_FLOAT32; // 0xAA000003
    public static final int FLOAT32_ARRAY     = FORM_ARRAY     | ID_FLOAT32; // 0xBB000003
    public static final int FLOAT32_POINTER   = FORM_POINTER   | ID_FLOAT32; // 0xCC000003

    // Double class
    public static final int FLOAT64_SINGLETON = FORM_SINGLETON | ID_FLOAT64; // 0xAA000004
    public static final int FLOAT64_ARRAY     = FORM_ARRAY     | ID_FLOAT64; // 0xBB000004
    public static final int FLOAT64_POINTER   = FORM_POINTER   | ID_FLOAT64; // 0xCC000004

    // Byte class
    public static final int BYTE_SINGLETON = FORM_SINGLETON | ID_BYTE; // 0xAA000005
    public static final int BYTE_ARRAY     = FORM_ARRAY     | ID_BYTE; // 0xBB000005
    public static final int BYTE_POINTER   = FORM_POINTER   | ID_BYTE; // 0xCC000005

    // Short class
    public static final int SHORT_SINGLETON = FORM_SINGLETON | ID_SHORT; // 0xAA000006
    public static final int SHORT_ARRAY     = FORM_ARRAY     | ID_SHORT; // 0xBB000006
    public static final int SHORT_POINTER   = FORM_POINTER   | ID_SHORT; // 0xCC000006

    // string class
    public static final int STRING_SINGLETON = FORM_SINGLETON | ID_STRING; // 0xAA000007
    public static final int STRING_ARRAY     = FORM_ARRAY     | ID_STRING; // 0xBB000007
    public static final int STRING_POINTER   = FORM_POINTER   | ID_STRING; // 0xCC000007

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
