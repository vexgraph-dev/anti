package net;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import lang.StringBuilder;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;

import java.nio.charset.StandardCharsets;

@Draft
@Intention("Off-heap zero-GC JSON parser and compiler operating on raw long memory pointers with zero Java heap allocations")
public final class JSON {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_JSON;

    private JSON() {}

    // --- OFF-HEAP JSON COMPILER / BUILDER ---

    public static long createObject() {
        long ptr = string.allocate("{");
        return ptr;
    }

    public static long createArray() {
        long ptr = string.allocate("[");
        return ptr;
    }

    public static long put(long objPtr, String key, String value) {
        if (objPtr == 0L || key == null) return objPtr;
        long keyPtr = string.allocate(key);
        long valPtr = value != null ? string.allocate(value) : 0L;
        long res = put(objPtr, keyPtr, valPtr);
        string.free(keyPtr);
        if (valPtr != 0L) string.free(valPtr);
        return res;
    }

    public static long put(long objPtr, long keyPtr, long valPtr) {
        if (objPtr == 0L || keyPtr == 0L) return objPtr;
        int len = string.length(objPtr);
        boolean hasEntries = len > 1;

        long resPtr = objPtr;
        if (hasEntries) {
            resPtr = string.append(resPtr, ",");
        }

        resPtr = string.append(resPtr, "\"");
        resPtr = string.append(resPtr, keyPtr);
        resPtr = string.append(resPtr, "\":");

        if (valPtr != 0L) {
            resPtr = string.append(resPtr, "\"");
            resPtr = string.append(resPtr, valPtr);
            resPtr = string.append(resPtr, "\"");
        } else {
            resPtr = string.append(resPtr, "null");
        }

        return resPtr;
    }

    public static long putInt(long objPtr, String key, int value) {
        return putRaw(objPtr, key, String.valueOf(value));
    }

    public static long putLong(long objPtr, String key, long value) {
        return putRaw(objPtr, key, String.valueOf(value));
    }

    public static long putFloat(long objPtr, String key, float value) {
        return putRaw(objPtr, key, String.valueOf(value));
    }

    public static long putDouble(long objPtr, String key, double value) {
        return putRaw(objPtr, key, String.valueOf(value));
    }

    public static long putBoolean(long objPtr, String key, boolean value) {
        return putRaw(objPtr, key, String.valueOf(value));
    }

    public static long putObject(long objPtr, String key, long childObjPtr) {
        if (childObjPtr == 0L) return putRaw(objPtr, key, "null");
        long finalizedChild = build(childObjPtr);
        long res = putRaw(objPtr, key, string.get(finalizedChild));
        string.free(finalizedChild);
        return res;
    }

    public static long putArray(long objPtr, String key, long arrayPtr) {
        if (arrayPtr == 0L) return putRaw(objPtr, key, "null");
        long finalizedArray = buildArray(arrayPtr);
        long res = putRaw(objPtr, key, string.get(finalizedArray));
        string.free(finalizedArray);
        return res;
    }

    private static long putRaw(long objPtr, String key, String rawValueStr) {
        if (objPtr == 0L || key == null) return objPtr;
        int len = string.length(objPtr);
        boolean hasEntries = len > 1;

        long resPtr = objPtr;
        if (hasEntries) {
            resPtr = string.append(resPtr, ",");
        }

        resPtr = string.append(resPtr, "\"");
        resPtr = string.append(resPtr, key);
        resPtr = string.append(resPtr, "\":");
        resPtr = string.append(resPtr, rawValueStr != null ? rawValueStr : "null");

        return resPtr;
    }

    public static long addArrayElement(long arrayPtr, String elementStr) {
        if (arrayPtr == 0L) return arrayPtr;
        int len = string.length(arrayPtr);
        boolean hasEntries = len > 1;

        long resPtr = arrayPtr;
        if (hasEntries) {
            resPtr = string.append(resPtr, ",");
        }

        resPtr = string.append(resPtr, "\"");
        resPtr = string.append(resPtr, elementStr);
        resPtr = string.append(resPtr, "\"");
        return resPtr;
    }

    public static long addArrayElement(long arrayPtr, long elementPtr) {
        if (arrayPtr == 0L || elementPtr == 0L) return arrayPtr;
        int len = string.length(arrayPtr);
        boolean hasEntries = len > 1;

        long resPtr = arrayPtr;
        if (hasEntries) {
            resPtr = string.append(resPtr, ",");
        }

        resPtr = string.append(resPtr, elementPtr);
        return resPtr;
    }

    public static long build(long builderPtr) {
        if (builderPtr == 0L) return 0L;
        return string.append(builderPtr, "}");
    }

    public static long buildArray(long builderPtr) {
        if (builderPtr == 0L) return 0L;
        return string.append(builderPtr, "]");
    }

    public static void free(long jsonPtr) {
        if (jsonPtr != 0L) {
            string.free(jsonPtr);
        }
    }

    // --- OFF-HEAP ZERO-GC JSON PARSER / SCANNER ---

    public static long get(long jsonPtr, String path) {
        if (jsonPtr == 0L || path == null || path.isEmpty()) return 0L;
        String[] keys = path.split("\\.");
        long currentPtr = jsonPtr;
        long extractedPtr = 0L;

        for (int i = 0; i < keys.length; i++) {
            extractedPtr = findKey(currentPtr, keys[i]);
            if (extractedPtr == 0L) {
                if (i > 0 && currentPtr != jsonPtr) string.free(currentPtr);
                return 0L;
            }
            if (i > 0 && currentPtr != jsonPtr) {
                string.free(currentPtr);
            }
            currentPtr = extractedPtr;
        }

        return extractedPtr;
    }

    public static int getInt(long jsonPtr, String path) {
        long valPtr = get(jsonPtr, path);
        if (valPtr == 0L) return 0;
        String valStr = string.get(valPtr);
        string.free(valPtr);
        try {
            return Integer.parseInt(valStr.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public static long getLong(long jsonPtr, String path) {
        long valPtr = get(jsonPtr, path);
        if (valPtr == 0L) return 0L;
        String valStr = string.get(valPtr);
        string.free(valPtr);
        try {
            return Long.parseLong(valStr.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    public static float getFloat(long jsonPtr, String path) {
        long valPtr = get(jsonPtr, path);
        if (valPtr == 0L) return 0.0f;
        String valStr = string.get(valPtr);
        string.free(valPtr);
        try {
            return Float.parseFloat(valStr.trim());
        } catch (Exception e) {
            return 0.0f;
        }
    }

    public static double getDouble(long jsonPtr, String path) {
        long valPtr = get(jsonPtr, path);
        if (valPtr == 0L) return 0.0;
        String valStr = string.get(valPtr);
        string.free(valPtr);
        try {
            return Double.parseDouble(valStr.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static boolean getBoolean(long jsonPtr, String path) {
        long valPtr = get(jsonPtr, path);
        if (valPtr == 0L) return false;
        String valStr = string.get(valPtr);
        string.free(valPtr);
        return "true".equalsIgnoreCase(valStr.trim());
    }

    public static int getArrayLength(long jsonArrayPtr) {
        if (jsonArrayPtr == 0L) return 0;
        int len = string.length(jsonArrayPtr);
        if (len < 2) return 0;

        int count = 0;
        boolean inString = false;
        int depth = 0;

        for (int i = 0; i < len; i++) {
            byte b = ForeignMemory.getByte(jsonArrayPtr + i);
            if (b == '"' && (i == 0 || ForeignMemory.getByte(jsonArrayPtr + i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (b == '{' || b == '[') depth++;
                else if (b == '}' || b == ']') depth--;
                else if (b == ',' && depth == 1) count++;
            }
        }

        return len > 2 ? count + 1 : 0;
    }

    // --- OFF-HEAP JSON TOKEN FINDER ---

    private static long findKey(long jsonPtr, String targetKey) {
        if (jsonPtr == 0L || targetKey == null || targetKey.isEmpty()) return 0L;
        int len = string.length(jsonPtr);
        byte[] targetBytes = targetKey.getBytes(StandardCharsets.UTF_8);
        int targetLen = targetBytes.length;

        int idx = 0;
        while (idx < len) {
            byte b = ForeignMemory.getByte(jsonPtr + idx);
            if (b == '"') {
                // Check if this quoted string matches targetKey
                boolean match = true;
                if (idx + 1 + targetLen < len && ForeignMemory.getByte(jsonPtr + idx + 1 + targetLen) == '"') {
                    for (int k = 0; k < targetLen; k++) {
                        if (ForeignMemory.getByte(jsonPtr + idx + 1 + k) != targetBytes[k]) {
                            match = false;
                            break;
                        }
                    }
                } else {
                    match = false;
                }

                if (match) {
                    // Check if followed by colon ':'
                    int colonIdx = idx + 1 + targetLen + 1;
                    while (colonIdx < len && isWhitespace(ForeignMemory.getByte(jsonPtr + colonIdx))) {
                        colonIdx++;
                    }

                    if (colonIdx < len && ForeignMemory.getByte(jsonPtr + colonIdx) == ':') {
                        int valStart = colonIdx + 1;
                        while (valStart < len && isWhitespace(ForeignMemory.getByte(jsonPtr + valStart))) {
                            valStart++;
                        }
                        if (valStart < len) {
                            return extractValueAt(jsonPtr, valStart, len);
                        }
                    }
                }
            }
            idx++;
        }

        return 0L;
    }

    private static long extractValueAt(long basePtr, int start, int totalLen) {
        byte first = ForeignMemory.getByte(basePtr + start);
        if (first == '"') {
            // String value
            int end = start + 1;
            while (end < totalLen) {
                byte b = ForeignMemory.getByte(basePtr + end);
                if (b == '"' && ForeignMemory.getByte(basePtr + end - 1) != '\\') {
                    break;
                }
                end++;
            }
            int valLen = end - (start + 1);
            long valPtr = string.allocateUninitialized(valLen);
            ForeignMemory.copy(basePtr + start + 1, valPtr, valLen);
            ForeignMemory.putByte(valPtr + valLen, (byte) 0);
            return valPtr;
        } else if (first == '{' || first == '[') {
            // Object or Array value
            byte openChar = first;
            byte closeChar = (first == '{') ? (byte) '}' : (byte) ']';
            int depth = 1;
            boolean inString = false;
            int end = start + 1;

            while (end < totalLen && depth > 0) {
                byte b = ForeignMemory.getByte(basePtr + end);
                if (b == '"' && ForeignMemory.getByte(basePtr + end - 1) != '\\') {
                    inString = !inString;
                } else if (!inString) {
                    if (b == openChar) depth++;
                    else if (b == closeChar) depth--;
                }
                end++;
            }

            int valLen = end - start;
            long valPtr = string.allocateUninitialized(valLen);
            ForeignMemory.copy(basePtr + start, valPtr, valLen);
            ForeignMemory.putByte(valPtr + valLen, (byte) 0);
            return valPtr;
        } else {
            // Primitive number, boolean, or null
            int end = start;
            while (end < totalLen) {
                byte b = ForeignMemory.getByte(basePtr + end);
                if (b == ',' || b == '}' || b == ']' || isWhitespace(b)) {
                    break;
                }
                end++;
            }
            int valLen = end - start;
            long valPtr = string.allocateUninitialized(valLen);
            ForeignMemory.copy(basePtr + start, valPtr, valLen);
            ForeignMemory.putByte(valPtr + valLen, (byte) 0);
            return valPtr;
        }
    }

    private static boolean isWhitespace(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r';
    }
}
