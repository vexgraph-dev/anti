package lang;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;
import primitive.string;

@Draft
@Intention("Pure static utility class for off-heap string manipulation, appending, appendPop memory recycling, and substring operations over primitive.string")
public final class StringBuilder {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_STRING_BUILDER;

    private StringBuilder() {}

    // --- ALLOCATION & FREE MANAGEMENT ---

    public static long allocate(String value) {
        return string.allocate(value);
    }

    public static long allocate(char[] value) {
        return string.allocate(value);
    }

    public static long allocate(byte[] value) {
        return string.allocate(value);
    }

    public static void free(long pointer) {
        string.free(pointer);
    }

    public static String get(long pointer) {
        return string.get(pointer);
    }

    public static int length(long pointer) {
        return string.length(pointer);
    }

    public static int capacity(long pointer) {
        return string.capacity(pointer);
    }

    public static long copy(long srcPtr) {
        return string.copy(srcPtr);
    }

    // --- STATIC APPEND OPERATIONS ---

    public static long append(long destPtr, long srcPtr) {
        return string.append(destPtr, srcPtr);
    }

    public static long append(long destPtr, String value) {
        return string.append(destPtr, value);
    }

    public static long append(long destPtr, char[] value) {
        return string.append(destPtr, value);
    }

    public static long append(long destPtr, byte[] value) {
        return string.append(destPtr, value);
    }

    public static long append(long destPtr, int value) {
        return string.append(destPtr, String.valueOf(value));
    }

    public static long append(long destPtr, float value) {
        return string.append(destPtr, String.valueOf(value));
    }

    public static long append(long destPtr, double value) {
        return string.append(destPtr, String.valueOf(value));
    }

    public static long append(long destPtr, boolean value) {
        return string.append(destPtr, String.valueOf(value));
    }

    public static long append(long destPtr, Object value) {
        return string.append(destPtr, String.valueOf(value));
    }

    public static long append(long destPtr, long... srcPointers) {
        return string.append(destPtr, srcPointers);
    }

    // --- STATIC APPENDPOP OPERATIONS (APPEND & FREE SOURCE POINTER) ---

    public static long appendPop(long destPtr, long srcPtr) {
        return string.appendPop(destPtr, srcPtr);
    }

    public static long appendPop(long destPtr, long... srcPointers) {
        return string.appendPop(destPtr, srcPointers);
    }

    public static long appendPop(long destPtr, String value) {
        return string.append(destPtr, value);
    }

    public static long appendPop(long destPtr, char[] value) {
        return string.append(destPtr, value);
    }

    public static long appendPop(long destPtr, byte[] value) {
        return string.append(destPtr, value);
    }

    public static long appendPop(long destPtr, int value) {
        return string.append(destPtr, String.valueOf(value));
    }

    public static long appendPop(long destPtr, float value) {
        return string.append(destPtr, String.valueOf(value));
    }

    public static long appendPop(long destPtr, double value) {
        return string.append(destPtr, String.valueOf(value));
    }

    public static long appendPop(long destPtr, boolean value) {
        return string.append(destPtr, String.valueOf(value));
    }

    public static long appendPop(long destPtr, Object value) {
        return string.append(destPtr, String.valueOf(value));
    }

    // --- STATIC SUBSTRING OPERATIONS ---

    public static long substring(long srcPtr, int start) {
        return string.substring(srcPtr, start);
    }

    public static long substring(long srcPtr, int start, int end) {
        return string.substring(srcPtr, start, end);
    }

    public static long substringPop(long srcPtr, int start) {
        return string.substringPop(srcPtr, start);
    }

    public static long substringPop(long srcPtr, int start, int end) {
        return string.substringPop(srcPtr, start, end);
    }
}
