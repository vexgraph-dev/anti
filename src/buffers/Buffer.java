package buffers;

import annotation.Draft;
import annotation.Required;
import annotation.Unsafe;
import annotation.Volatile;
import bit.Bit8;
import nio.ForeignMemory;
import oop.TypeRegister;
import oop.Inheritance;

import nio.StringLookup;
@Draft
public final class Buffer {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_BUFFER;

    private static final long HEADER_SIZE = 24L; // padding(4) + width(4) + height(4) + channels(4) + type(4) + length(4)

    private Buffer() {}

    public static void freeAll() {
        // Bit8.freeAll() manages the shared array slot arenas.
    }

    public static boolean isBufferClass(int classId) {
        return classId >= 0x000050 && classId <= 0x000063;
    }

    /**
     * Allocates a buffer of {@code width * height * channels} long elements from the
     * shared bit.Bit8 array pool. The returned pointer carries a 24-byte metadata
     * header (width/height/channels/type/length) immediately below the data; the
     * bit.Bit8 block header sits 24 bytes further down and is owned by the pool.
     */
    public static long allocate(int classId, int width, int height, int channels) {
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException(StringLookup.getJavaString(23) + Integer.toHexString(classId) + StringLookup.getJavaString(24));

        int length = width * height * channels;
        int type = TypeRegister.FORM_ARRAY | classId;
        long base = Bit8.allocateArray(type, (int) (HEADER_SIZE + (length * 8L)));
        long pointer = base + HEADER_SIZE;
        ForeignMemory.setInt(pointer - 20L, width);
        ForeignMemory.setInt(pointer - 16L, height);
        ForeignMemory.setInt(pointer - 12L, channels);
        ForeignMemory.setInt(pointer - 8L, type);
        ForeignMemory.setInt(pointer - 4L, length);
        return pointer;
    }

    public static long expand(long oldPointer, int newWidth, int newHeight) {
        if (oldPointer == 0L)
            throw new NullPointerException(StringLookup.getJavaString(25));
        int type = type(oldPointer);
        int classId = TypeRegister.getClassId(type);
        int oldLength = length(oldPointer);
        int channels = channels(oldPointer);
        long newPointer = allocate(classId, newWidth, newHeight, channels);

        int newLength = newWidth * newHeight * channels;
        int elementsToCopy = Math.min(oldLength, newLength);
        ForeignMemory.copy(oldPointer, newPointer, elementsToCopy * 8L);
        free(oldPointer);
        return newPointer;
    }

    public static void free(long pointer) {
        if (pointer == 0L)
            return;

        int type = type(pointer);
        int classId = TypeRegister.getClassId(type);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalStateException(StringLookup.getJavaString(26) + java.lang.Long.toHexString(pointer).toUpperCase());

        Bit8.free(pointer - HEADER_SIZE);
    }

    public static long get(long pointer) {
        if (pointer == 0L)
            throw new NullPointerException(StringLookup.getJavaString(27));
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId + StringLookup.getJavaString(30));
        return ForeignMemory.getLong(pointer);
    }

    public static long get(long pointer, int index) {
        checkBounds(pointer, index);
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId + StringLookup.getJavaString(30));
        return ForeignMemory.getLong(pointer + (index * 8L));
    }

    public static void set(long pointer, long value) {
        if (pointer == 0L)
            throw new NullPointerException(StringLookup.getJavaString(31));
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId + StringLookup.getJavaString(30));
        ForeignMemory.setLong(pointer, value);
    }

    public static void set(long pointer, int index, long value) {
        checkBounds(pointer, index);
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId + StringLookup.getJavaString(30));
        ForeignMemory.setLong(pointer + (index * 8L), value);
    }

    @Volatile
    public static long getVolatile(long pointer) {
        if (pointer == 0L)
            throw new NullPointerException(StringLookup.getJavaString(32));
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId + StringLookup.getJavaString(30));
        return ForeignMemory.getVolatileLong(pointer);
    }

    @Volatile
    public static void setVolatile(long pointer, long value) {
        if (pointer == 0L)
            throw new NullPointerException(StringLookup.getJavaString(31));
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId + StringLookup.getJavaString(30));
        ForeignMemory.setVolatileLong(pointer, value);
    }

    public static boolean compareAndSet(long pointer, long expected, long value) {
        if (pointer == 0L)
            throw new NullPointerException(StringLookup.getJavaString(31));
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId + StringLookup.getJavaString(30));
        return ForeignMemory.compareAndSetLong(pointer, expected, value);
    }

    public static long getAndSet(long pointer, long value) {
        if (pointer == 0L)
            throw new NullPointerException(StringLookup.getJavaString(31));
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId + StringLookup.getJavaString(30));
        return ForeignMemory.getAndSetLong(pointer, value);
    }

    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L)
            throw new NullPointerException(StringLookup.getJavaString(33));
        int len = length(pointer);
        if (index < 0 || index >= len)
            throw new IndexOutOfBoundsException(StringLookup.getJavaString(34) + index + StringLookup.getJavaString(35) + len + StringLookup.getJavaString(36) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(37) + Integer.toHexString(type(pointer)).toUpperCase() + StringLookup.getJavaString(18));
    }

    public static int width(long pointer) {
        return ForeignMemory.getInt(pointer - 20L);
    }

    public static int height(long pointer) {
        return ForeignMemory.getInt(pointer - 16L);
    }

    public static int channels(long pointer) {
        return ForeignMemory.getInt(pointer - 12L);
    }

    public static int type(long pointer) {
        return ForeignMemory.getInt(pointer - 8L);
    }

    public static int length(long pointer) {
        return ForeignMemory.getInt(pointer - 4L);
    }

    public static int classId(long pointer) {
        return TypeRegister.getClassId(type(pointer));
    }

    public static boolean isSingleton(long pointer) {
        return TypeRegister.isSingleton(type(pointer));
    }

    public static boolean isArray(long pointer) {
        return TypeRegister.isArray(type(pointer));
    }

    public static boolean isPointer(long pointer) {
        return TypeRegister.isPointer(type(pointer));
    }

    @Unsafe
    public static long unsafeGet(long pointer) {
        return ForeignMemory.getLong(pointer);
    }

    @Unsafe
    public static long unsafeGet(long pointer, int index) {
        return ForeignMemory.getLong(pointer + (index * 8L));
    }

    @Unsafe
    public static void setUnsafe(long pointer, long value) {
        ForeignMemory.setLong(pointer, value);
    }

    @Unsafe
    public static void setUnsafe(long pointer, int index, long value) {
        ForeignMemory.setLong(pointer + (index * 8L), value);
    }

    @Volatile
    public static long getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getVolatileLong(pointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, int index, long value) {
        checkBounds(pointer, index);
        ForeignMemory.setVolatileLong(pointer + (index * 8L), value);
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatile(long pointer) {
        return ForeignMemory.getVolatileLong(pointer);
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatile(long pointer, int index) {
        return ForeignMemory.getVolatileLong(pointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, long value) {
        ForeignMemory.setVolatileLong(pointer, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, int index, long value) {
        ForeignMemory.setVolatileLong(pointer + (index * 8L), value);
    }
}