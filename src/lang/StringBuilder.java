package lang;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;
import primitive.string;

@Draft
@Intention("Off-heap StringBuilder using raw pointer manipulation and appendPop memory recycling over primitive.string")
public final class StringBuilder {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_STRING_BUILDER;

    private long pointer;

    public StringBuilder() {
        this.pointer = 0L;
    }

    public StringBuilder(long initialStringPointer) {
        this.pointer = initialStringPointer;
    }

    public StringBuilder(String initialValue) {
        this.pointer = string.allocate(initialValue);
    }

    public StringBuilder(char[] initialValue) {
        this.pointer = string.allocate(initialValue);
    }

    public StringBuilder(byte[] initialValue) {
        this.pointer = string.allocate(initialValue);
    }

    public StringBuilder(int initialValue) {
        this.pointer = string.allocate(java.lang.String.valueOf(initialValue));
    }

    public StringBuilder(float initialValue) {
        this.pointer = string.allocate(java.lang.String.valueOf(initialValue));
    }

    public StringBuilder(double initialValue) {
        this.pointer = string.allocate(java.lang.String.valueOf(initialValue));
    }

    public StringBuilder(boolean initialValue) {
        this.pointer = string.allocate(java.lang.String.valueOf(initialValue));
    }

    // --- STATIC POINTER-BASED BUILDER OPERATIONS (ZERO-GC ALLOCATION) ---

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
        return string.append(destPtr, java.lang.String.valueOf(value));
    }

    public static long append(long destPtr, float value) {
        return string.append(destPtr, java.lang.String.valueOf(value));
    }

    public static long append(long destPtr, double value) {
        return string.append(destPtr, java.lang.String.valueOf(value));
    }

    public static long append(long destPtr, boolean value) {
        return string.append(destPtr, java.lang.String.valueOf(value));
    }

    public static long append(long destPtr, Object value) {
        return string.append(destPtr, java.lang.String.valueOf(value));
    }

    public static long append(long destPtr, long... srcPointers) {
        return string.append(destPtr, srcPointers);
    }

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
        return string.append(destPtr, java.lang.String.valueOf(value));
    }

    public static long appendPop(long destPtr, float value) {
        return string.append(destPtr, java.lang.String.valueOf(value));
    }

    public static long appendPop(long destPtr, double value) {
        return string.append(destPtr, java.lang.String.valueOf(value));
    }

    public static long appendPop(long destPtr, boolean value) {
        return string.append(destPtr, java.lang.String.valueOf(value));
    }

    public static long appendPop(long destPtr, Object value) {
        return string.append(destPtr, java.lang.String.valueOf(value));
    }

    // --- INSTANCE / HANDLE-BASED BUILDER OPERATIONS ---

    public StringBuilder append(long value) {
        this.pointer = string.append(this.pointer, java.lang.String.valueOf(value));
        return this;
    }

    public StringBuilder append(String value) {
        this.pointer = string.append(this.pointer, value);
        return this;
    }

    public StringBuilder append(char[] value) {
        this.pointer = string.append(this.pointer, value);
        return this;
    }

    public StringBuilder append(byte[] value) {
        this.pointer = string.append(this.pointer, value);
        return this;
    }

    public StringBuilder append(int value) {
        this.pointer = string.append(this.pointer, java.lang.String.valueOf(value));
        return this;
    }

    public StringBuilder append(float value) {
        this.pointer = string.append(this.pointer, java.lang.String.valueOf(value));
        return this;
    }

    public StringBuilder append(double value) {
        this.pointer = string.append(this.pointer, java.lang.String.valueOf(value));
        return this;
    }

    public StringBuilder append(boolean value) {
        this.pointer = string.append(this.pointer, java.lang.String.valueOf(value));
        return this;
    }

    public StringBuilder append(Object value) {
        this.pointer = string.append(this.pointer, java.lang.String.valueOf(value));
        return this;
    }

    public StringBuilder append(long... srcPointers) {
        this.pointer = string.append(this.pointer, srcPointers);
        return this;
    }

    public StringBuilder appendPointer(long srcPtr) {
        this.pointer = string.append(this.pointer, srcPtr);
        return this;
    }

    public StringBuilder appendPop(long srcPtr) {
        this.pointer = string.appendPop(this.pointer, srcPtr);
        return this;
    }

    public StringBuilder appendPopPointer(long srcPtr) {
        this.pointer = string.appendPop(this.pointer, srcPtr);
        return this;
    }

    public StringBuilder appendPop(long... srcPointers) {
        this.pointer = string.appendPop(this.pointer, srcPointers);
        return this;
    }

    public StringBuilder appendPop(String value) {
        this.pointer = string.append(this.pointer, value);
        return this;
    }

    public StringBuilder appendPop(char[] value) {
        this.pointer = string.append(this.pointer, value);
        return this;
    }

    public StringBuilder appendPop(byte[] value) {
        this.pointer = string.append(this.pointer, value);
        return this;
    }

    public StringBuilder appendPop(int value) {
        this.pointer = string.append(this.pointer, java.lang.String.valueOf(value));
        return this;
    }

    public StringBuilder appendPop(float value) {
        this.pointer = string.append(this.pointer, java.lang.String.valueOf(value));
        return this;
    }

    public StringBuilder appendPop(double value) {
        this.pointer = string.append(this.pointer, java.lang.String.valueOf(value));
        return this;
    }

    public StringBuilder appendPop(boolean value) {
        this.pointer = string.append(this.pointer, java.lang.String.valueOf(value));
        return this;
    }

    public StringBuilder appendPop(Object value) {
        this.pointer = string.append(this.pointer, java.lang.String.valueOf(value));
        return this;
    }

    public long pointer() {
        return this.pointer;
    }

    public long build() {
        return this.pointer;
    }

    public int length() {
        return string.length(this.pointer);
    }

    public int capacity() {
        return string.capacity(this.pointer);
    }

    public void free() {
        if (this.pointer != 0L) {
            string.free(this.pointer);
            this.pointer = 0L;
        }
    }

    public static void free(long pointer) {
        if (pointer != 0L) {
            string.free(pointer);
        }
    }

    @Override
    public String toString() {
        return string.get(this.pointer);
    }
}
