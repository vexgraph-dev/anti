package nio;

import annotation.HotCode;
import annotation.Intention;
import annotation.Unsafe;
import annotation.Volatile;
import oop.Struct;
import primitive.Byte;
import primitive.Double;
import primitive.Float;
import primitive.Brain;
import primitive.Fixed32;
import primitive.Fixed64;
import primitive.Int;
import primitive.IntDouble;
import primitive.IntFloat;
import primitive.Long;
import primitive.LongFloat;
import primitive.LongDouble;
import primitive.Short;
import primitive.string;
import variable.Variable;
import variable.SearchVariable;
import struct.List;
import struct.Array;
import struct.Deque;
import struct.Stack;
import struct.Map;
import struct.Set;
import search.Trie;
import spatial.GridArray;
import spatial.CircularArray;
import thread.RingBuffer;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

public class ForeignMemory
{
    // god, the lens of all things. might be dangerous to hold, low-key
    // literally c flavored hell java version
    // good luck myself
    private static final MemorySegment GLOBAL_MEMORY = MemorySegment.ofAddress(0).reinterpret(java.lang.Long.MAX_VALUE);

    private static final VarHandle BYTE_VH = ValueLayout.JAVA_BYTE.varHandle();
    private static final VarHandle SHORT_VH = ValueLayout.JAVA_SHORT.varHandle();
    private static final VarHandle INT_VH = ValueLayout.JAVA_INT.varHandle();
    private static final VarHandle LONG_VH = ValueLayout.JAVA_LONG.varHandle();
    private static final VarHandle FLOAT_VH = ValueLayout.JAVA_FLOAT.varHandle();
    private static final VarHandle DOUBLE_VH = ValueLayout.JAVA_DOUBLE.varHandle();

    private static final MethodHandle MALLOC_HANDLE;
    private static final MethodHandle FREE_HANDLE;

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup stdlib = linker.defaultLookup();

        try {
            MALLOC_HANDLE = linker.downcallHandle(
                stdlib.find("malloc").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            );

            FREE_HANDLE = linker.downcallHandle(
                stdlib.find("free").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    public static long allocateNative(long bytes)
    {
        if (bytes <= 0) return 0L;
        try {
            MemorySegment seg = (MemorySegment) MALLOC_HANDLE.invokeExact(bytes);
            return seg.address();
        } catch (Throwable t) {
            throw new OutOfMemoryError("Native malloc failed for size: " + bytes);
        }
    }

    public static void freeNative(long address)
    {
        if (address == 0L) return;
        try {
            FREE_HANDLE.invokeExact(MemorySegment.ofAddress(address));
        } catch (Throwable t) {
            throw new RuntimeException("Native free failed for address: " + address, t);
        }
    }

    public static byte getByte(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_BYTE, address);
    }

    @Unsafe
    public static byte unsafeGetByte(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_BYTE, address);
    }

    public static void putByte(long address, byte value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_BYTE, address, value);
    }

    public static short getShort(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_SHORT, address);
    }

    @Unsafe
    public static short unsafeGetShort(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_SHORT, address);
    }

    public static void putShort(long address, short value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_SHORT, address, value);
    }

    public static int getInt(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_INT, address);
    }

    @Unsafe
    public static int unsafeGetInt(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_INT, address);
    }

    public static void putInt(long address, int value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_INT, address, value);
    }

    public static long getLong(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_LONG, address);
    }

    @Unsafe
    public static long unsafeGetLong(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_LONG, address);
    }

    public static void putLong(long address, long value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_LONG, address, value);
    }

    public static float getFloat(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_FLOAT, address);
    }

    @Unsafe
    public static float unsafeGetFloat(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_FLOAT, address);
    }

    public static void putFloat(long address, float value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_FLOAT, address, value);
    }

    public static double getDouble(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_DOUBLE, address);
    }

    @Unsafe
    public static double unsafeGetDouble(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_DOUBLE, address);
    }

    public static void putDouble(long address, double value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_DOUBLE, address, value);
    }

    public static short getShortUnaligned(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_SHORT_UNALIGNED, address);
    }

    public static void putShortUnaligned(long address, short value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_SHORT_UNALIGNED, address, value);
    }

    public static int getIntUnaligned(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_INT_UNALIGNED, address);
    }

    public static void putIntUnaligned(long address, int value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_INT_UNALIGNED, address, value);
    }

    public static long getLongUnaligned(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_LONG_UNALIGNED, address);
    }

    public static void putLongUnaligned(long address, long value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_LONG_UNALIGNED, address, value);
    }

    public static float getFloatUnaligned(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_FLOAT_UNALIGNED, address);
    }

    public static void putFloatUnaligned(long address, float value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_FLOAT_UNALIGNED, address, value);
    }

    public static double getDoubleUnaligned(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, address);
    }

    public static void putDoubleUnaligned(long address, double value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_DOUBLE_UNALIGNED, address, value);
    }

    public static long getAddress(long address) {
        if (address == 0L) throw new NullPointerException("Reading address from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.ADDRESS, address).address();
    }

    public static void putAddress(long address, long targetAddress) {
        if (address == 0L) throw new NullPointerException("Writing address to NULL off-heap pointer!");
        GLOBAL_MEMORY.set(ValueLayout.ADDRESS, address, MemorySegment.ofAddress(targetAddress));
    }

    public static void copy(long srcAddress, long destAddress, long bytes) {
        MemorySegment.copy(GLOBAL_MEMORY, srcAddress, GLOBAL_MEMORY, destAddress, bytes);
    }

    public static void copy(long srcAddress, long destAddress, int bytes) {
        MemorySegment.copy(GLOBAL_MEMORY, srcAddress, GLOBAL_MEMORY, destAddress, bytes);
    }

    public static void copyToHeap(long srcAddress, byte[] destArray, int destOffset, int length) {
        MemorySegment.copy(GLOBAL_MEMORY, ValueLayout.JAVA_BYTE, srcAddress, destArray, destOffset, length);
    }

    public static void copyFromHeap(byte[] srcArray, int srcOffset, long destAddress, int length) {
        MemorySegment.copy(srcArray, srcOffset, GLOBAL_MEMORY, ValueLayout.JAVA_BYTE, destAddress, length);
    }

    public static String getString(long address) {
        if (address == 0) return null;
        return GLOBAL_MEMORY.getString(address);
    }

    public static MemorySegment wrap(long address, long byteSize) {
        return MemorySegment.ofAddress(address).reinterpret(byteSize);
    }

    public static void setMemory(long address, long byteSize, byte value) {
        if (address == 0L || byteSize <= 0) return;
        wrap(address, byteSize).fill(value);
    }

    public static void setMemory(long address, int byteSize, byte value) {
        if (address == 0L || byteSize <= 0) return;
        wrap(address, byteSize).fill(value);
    }

    public static MemorySegment wrap(long address, long byteSize, Arena arena) {
        return MemorySegment.ofAddress(address).reinterpret(byteSize, arena, null);
    }

    // =========================================================================
    // OVERLOADED SAFE & UNSAFE SETTERS
    // =========================================================================


    public static void set(long address, byte value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        putByte(address, value);
    }

    public static void set(long address, short value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        putShort(address, value);
    }

    public static void set(long address, int value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        putInt(address, value);
    }

    public static void set(long address, long value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        putLong(address, value);
    }

    public static void set(long address, float value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        putFloat(address, value);
    }

    public static void set(long address, double value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        putDouble(address, value);
    }

    @Unsafe
    public static void unsafeSet(long address, byte value) {
        putByte(address, value);
    }

    @Unsafe
    public static void unsafeSet(long address, short value) {
        putShort(address, value);
    }

    @Unsafe
    public static void unsafeSet(long address, int value) {
        putInt(address, value);
    }

    @Unsafe
    public static void unsafeSet(long address, long value) {
        putLong(address, value);
    }

    @Unsafe
    public static void unsafeSet(long address, float value) {
        putFloat(address, value);
    }

    @Unsafe
    public static void unsafeSet(long address, double value) {
        putDouble(address, value);
    }

    public static void setVolatile(long address, byte value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        putByteVolatile(address, value);
    }

    public static void setVolatile(long address, short value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        putShortVolatile(address, value);
    }

    public static void setVolatile(long address, int value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        putIntVolatile(address, value);
    }

    public static void setVolatile(long address, long value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        putLongVolatile(address, value);
    }

    public static void setVolatile(long address, float value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        putFloatVolatile(address, value);
    }

    public static void setVolatile(long address, double value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        putDoubleVolatile(address, value);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSet(long address, byte value) {
        putByteVolatile(address, value);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSet(long address, short value) {
        putShortVolatile(address, value);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSet(long address, int value) {
        putIntVolatile(address, value);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSet(long address, long value) {
        putLongVolatile(address, value);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSet(long address, float value) {
        putFloatVolatile(address, value);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSet(long address, double value) {
        putDoubleVolatile(address, value);
    }

    // =========================================================================
    // ATOMIC & VOLATILE DEREFERENCING METHODS (VarHandles)
    // =========================================================================

    @Volatile
    public static byte getByteVolatile(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return (byte) BYTE_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Unsafe
    @Volatile
    public static byte unsafeGetByteVolatile(long address) {
        return (byte) BYTE_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Volatile
    public static void putByteVolatile(long address, byte value) {
        BYTE_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetByte(long address, byte expected, byte value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        long alignedAddr = address & ~3L;
        int shift = (int) (address & 3L) * 8;
        int mask = 0xFF << shift;
        int expectedBits = (expected & 0xFF) << shift;
        int valueBits = (value & 0xFF) << shift;
        while (true) {
            int oldVal = getIntVolatile(alignedAddr);
            if (((oldVal >>> shift) & 0xFF) != (expected & 0xFF)) {
                return false;
            }
            int newVal = (oldVal & ~mask) | valueBits;
            if (compareAndSetInt(alignedAddr, oldVal, newVal)) {
                return true;
            }
        }
    }

    @Volatile
    public static short getShortVolatile(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return (short) SHORT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Unsafe
    @Volatile
    public static short unsafeGetShortVolatile(long address) {
        return (short) SHORT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Volatile
    public static void putShortVolatile(long address, short value) {
        SHORT_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetShort(long address, short expected, short value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        long alignedAddr = address & ~3L;
        int shift = (int) (address & 3L) * 8;
        int mask = 0xFFFF << shift;
        int expectedBits = (expected & 0xFFFF) << shift;
        int valueBits = (value & 0xFFFF) << shift;
        while (true) {
            int oldVal = getIntVolatile(alignedAddr);
            if (((oldVal >>> shift) & 0xFFFF) != (expected & 0xFFFF)) {
                return false;
            }
            int newVal = (oldVal & ~mask) | valueBits;
            if (compareAndSetInt(alignedAddr, oldVal, newVal)) {
                return true;
            }
        }
    }

    @Volatile
    public static int getIntVolatile(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return (int) INT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Unsafe
    @Volatile
    public static int unsafeGetIntVolatile(long address) {
        return (int) INT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Volatile
    public static void putIntVolatile(long address, int value) {
        INT_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetInt(long address, int expected, int value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (boolean) INT_VH.compareAndSet(GLOBAL_MEMORY, address, expected, value);
    }

    public static int getAndSetInt(long address, int value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (int) INT_VH.getAndSet(GLOBAL_MEMORY, address, value);
    }

    @Volatile
    public static long getLongVolatile(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return (long) LONG_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Unsafe
    @Volatile
    public static long unsafeGetLongVolatile(long address) {
        return (long) LONG_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Volatile
    public static void putLongVolatile(long address, long value) {
        LONG_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetLong(long address, long expected, long value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (boolean) LONG_VH.compareAndSet(GLOBAL_MEMORY, address, expected, value);
    }

    public static long getAndSetLong(long address, long value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (long) LONG_VH.getAndSet(GLOBAL_MEMORY, address, value);
    }

    public static long getAndBitwiseOrLong(long address, long mask) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (long) LONG_VH.getAndBitwiseOr(GLOBAL_MEMORY, address, mask);
    }

    public static long getAndBitwiseAndLong(long address, long mask) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (long) LONG_VH.getAndBitwiseAnd(GLOBAL_MEMORY, address, mask);
    }

    @Volatile
    public static float getFloatVolatile(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return (float) FLOAT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Unsafe
    @Volatile
    public static float unsafeGetFloatVolatile(long address) {
        return (float) FLOAT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Volatile
    public static void putFloatVolatile(long address, float value) {
        FLOAT_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetFloat(long address, float expected, float value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (boolean) FLOAT_VH.compareAndSet(GLOBAL_MEMORY, address, expected, value);
    }

    @Volatile
    public static double getDoubleVolatile(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return (double) DOUBLE_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Unsafe
    @Volatile
    public static double unsafeGetDoubleVolatile(long address) {
        return (double) DOUBLE_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Volatile
    public static void putDoubleVolatile(long address, double value) {
        DOUBLE_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetDouble(long address, double expected, double value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (boolean) DOUBLE_VH.compareAndSet(GLOBAL_MEMORY, address, expected, value);
    }

    // nuclear, dangerous
    @HotCode
    @Intention("the garbage collection to end the actual application")
    public static void freeAllClasses()
    {

        Byte.freeAll();
        Short.freeAll();
        Int.freeAll();
        Long.freeAll();
        Float.freeAll();
        Double.freeAll();
        IntFloat.freeAll();
        IntDouble.freeAll();
        LongFloat.freeAll();
        LongDouble.freeAll();
        Brain.freeAll();
        Fixed32.freeAll();
        Fixed64.freeAll();
        string.freeAll();
        Struct.freeAll();

        Variable.freeAllClasses();
        SearchVariable.freeAll();
        List.freeAll();
        Array.freeAll();
        Deque.freeAll();
        Stack.freeAll();
        Map.freeAll();
        Set.freeAll();
        Trie.freeAll();
        GridArray.freeAll();
        CircularArray.freeAll();
        RingBuffer.freeAll();
    }
}