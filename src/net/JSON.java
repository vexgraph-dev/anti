package net;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
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
            assert valStr != null;
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
            assert valStr != null;
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
            assert valStr != null;
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
            assert valStr != null;
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
        assert valStr != null;
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

    public static long getArrayElement(long jsonArrayPtr, int targetIndex) {
        if (jsonArrayPtr == 0L || targetIndex < 0) return 0L;
        int len = string.length(jsonArrayPtr);
        if (len < 2) return 0L;

        int start = 0;
        while (start < len && isWhitespace(ForeignMemory.getByte(jsonArrayPtr + start))) start++;
        if (start >= len || ForeignMemory.getByte(jsonArrayPtr + start) != '[') return 0L;
        start++; // skip '['

        int currentIndex = 0;
        int depth = 0;
        boolean inString = false;
        int elemStart = -1;

        while (start < len) {
            byte b = ForeignMemory.getByte(jsonArrayPtr + start);
            if (isWhitespace(b) && depth == 0 && elemStart == -1) {
                start++;
                continue;
            }

            if (b == ']' && depth == 0 && !inString) {
                if (elemStart != -1 && currentIndex == targetIndex) {
                    return extractValueAt(jsonArrayPtr, elemStart, start);
                }
                break;
            }

            if (elemStart == -1) {
                elemStart = start;
            }

            if (b == '"' && (start == 0 || ForeignMemory.getByte(jsonArrayPtr + start - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (b == '{' || b == '[') depth++;
                else if (b == '}' || b == ']') depth--;
                else if (b == ',' && depth == 0) {
                    if (currentIndex == targetIndex) {
                        return extractValueAt(jsonArrayPtr, elemStart, start);
                    }
                    currentIndex++;
                    elemStart = -1;
                }
            }
            start++;
        }

        if (elemStart != -1 && currentIndex == targetIndex) {
            return extractValueAt(jsonArrayPtr, elemStart, len);
        }

        return 0L;
    }

    public static long getKeys(long jsonObjPtr) {
        if (jsonObjPtr == 0L) return 0L;
        int len = string.length(jsonObjPtr);
        if (len < 2) return 0L;

        long resultPtr = 0L;
        int idx = 0;
        boolean inString = false;
        int depth = 0;
        boolean expectKey = true;

        while (idx < len) {
            byte b = ForeignMemory.getByte(jsonObjPtr + idx);
            if (b == '\\' && inString) {
                idx += 2;
                continue;
            }

            if (!inString) {
                if (b == '{' || b == '[') {
                    depth++;
                } else if (b == '}' || b == ']') {
                    depth--;
                } else if (b == ',' && depth == 1) {
                    expectKey = true;
                } else if (b == '"' && depth == 1 && expectKey) {
                    int keyStart = idx + 1;
                    int keyEnd = keyStart;
                    while (keyEnd < len) {
                        byte kb = ForeignMemory.getByte(jsonObjPtr + keyEnd);
                        if (kb == '\\') {
                            keyEnd += 2;
                            continue;
                        }
                        if (kb == '"') {
                            break;
                        }
                        keyEnd++;
                    }
                    if (keyEnd < len) {
                        int keyLen = keyEnd - keyStart;
                        if (keyLen > 0) {
                            if (resultPtr == 0L) {
                                resultPtr = string.allocateUninitialized(keyLen);
                                ForeignMemory.copy(jsonObjPtr + keyStart, resultPtr, keyLen);
                                ForeignMemory.putByte(resultPtr + keyLen, (byte) 0);
                            } else {
                                resultPtr = string.append(resultPtr, ",");
                                long tempKey = string.allocateUninitialized(keyLen);
                                ForeignMemory.copy(jsonObjPtr + keyStart, tempKey, keyLen);
                                ForeignMemory.putByte(tempKey + keyLen, (byte) 0);
                                resultPtr = string.appendPop(resultPtr, tempKey);
                            }
                        }
                    }
                    idx = keyEnd + 1;
                    expectKey = false;
                    continue;
                } else if (b == '"') {
                    inString = true;
                }
            } else if (b == '"') {
                inString = false;
            }
            idx++;
        }

        return resultPtr;
    }

    public static long escape(String unescapedStr) {
        if (unescapedStr == null) return 0L;
        long strPtr = string.allocate(unescapedStr);
        long res = escape(strPtr);
        string.free(strPtr);
        return res;
    }

    public static long escape(long strPtr) {
        if (strPtr == 0L) return 0L;
        String s = string.get(strPtr);
        if (s == null) return 0L;

        java.lang.StringBuilder sb = new java.lang.StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return string.allocate(sb.toString());
    }

    public static long unescape(String escapedStr) {
        if (escapedStr == null) return 0L;
        long strPtr = string.allocate(escapedStr);
        long res = unescape(strPtr);
        string.free(strPtr);
        return res;
    }

    public static long unescape(long strPtr) {
        if (strPtr == 0L) return 0L;
        String s = string.get(strPtr);
        if (s == null) return 0L;

        java.lang.StringBuilder sb = new java.lang.StringBuilder();
        int i = 0;
        int len = s.length();
        while (i < len) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < len) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"':  sb.append('"');  i += 2; break;
                    case '\\': sb.append('\\'); i += 2; break;
                    case '/':  sb.append('/');  i += 2; break;
                    case 'b':  sb.append('\b'); i += 2; break;
                    case 'f':  sb.append('\f'); i += 2; break;
                    case 'n':  sb.append('\n'); i += 2; break;
                    case 'r':  sb.append('\r'); i += 2; break;
                    case 't':  sb.append('\t'); i += 2; break;
                    case 'u':
                        if (i + 5 < len) {
                            try {
                                int hexVal = Integer.parseInt(s.substring(i + 2, i + 6), 16);
                                sb.append((char) hexVal);
                                i += 6;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                                i++;
                            }
                        } else {
                            sb.append(c);
                            i++;
                        }
                        break;
                    default:
                        sb.append(c);
                        i++;
                        break;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return string.allocate(sb.toString());
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
