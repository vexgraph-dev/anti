package nio;

import annotation.HotCode;
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
import relational.Variable;
import relational.SearchVariable;
import struct.List;
import struct.Array;
import struct.Deque;
import struct.Stack;
import struct.Map;
import struct.Set;
import struct.Trie;
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

    public static byte getByte(long address)
    {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_BYTE, address);
    }

    public static void putByte(long address, byte value)
    {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_BYTE, address, value);
    }

    public static short getShort(long address)
    {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_SHORT, address);
    }

    public static void putShort(long address, short value)
    {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_SHORT, address, value);
    }

    public static int getInt(long address)
    {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_INT, address);
    }

    public static void putInt(long address, int value)
    {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_INT, address, value);
    }

    public static long getLong(long address)
    {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_LONG, address);
    }

    public static void putLong(long address, long value)
    {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_LONG, address, value);
    }

    public static float getFloat(long address)
    {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_FLOAT, address);
    }

    public static void putFloat(long address, float value)
    {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_FLOAT, address, value);
    }

    public static double getDouble(long address)
    {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_DOUBLE, address);
    }

    public static void putDouble(long address, double value)
    {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_DOUBLE, address, value);
    }

    public static short getShortUnaligned(long address)
    {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_SHORT_UNALIGNED, address);
    }

    public static void putShortUnaligned(long address, short value)
    {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_SHORT_UNALIGNED, address, value);
    }

    public static int getIntUnaligned(long address)
    {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_INT_UNALIGNED, address);
    }

    public static void putIntUnaligned(long address, int value)
    {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_INT_UNALIGNED, address, value);
    }

    public static long getLongUnaligned(long address)
    {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_LONG_UNALIGNED, address);
    }

    public static void putLongUnaligned(long address, long value)
    {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_LONG_UNALIGNED, address, value);
    }

    public static float getFloatUnaligned(long address)
    {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_FLOAT_UNALIGNED, address);
    }

    public static void putFloatUnaligned(long address, float value)
    {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_FLOAT_UNALIGNED, address, value);
    }

    public static double getDoubleUnaligned(long address)
    {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, address);
    }

    public static void putDoubleUnaligned(long address, double value)
    {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_DOUBLE_UNALIGNED, address, value);
    }

    public static long getAddress(long address)
    {
        return GLOBAL_MEMORY.get(ValueLayout.ADDRESS, address).address();
    }

    public static void putAddress(long address, long targetAddress)
    {
        GLOBAL_MEMORY.set(ValueLayout.ADDRESS, address, MemorySegment.ofAddress(targetAddress));
    }

    public static void copy(long srcAddress, long destAddress, long bytes)
    {
        MemorySegment.copy(GLOBAL_MEMORY, srcAddress, GLOBAL_MEMORY, destAddress, bytes);
    }

    public static void copyToHeap(long srcAddress, byte[] destArray, int destOffset, int length)
    {
        MemorySegment.copy(GLOBAL_MEMORY, ValueLayout.JAVA_BYTE, srcAddress, destArray, destOffset, length);
    }

    public static void copyFromHeap(byte[] srcArray, int srcOffset, long destAddress, int length)
    {
        MemorySegment.copy(srcArray, srcOffset, GLOBAL_MEMORY, ValueLayout.JAVA_BYTE, destAddress, length);
    }

    public static String getString(long address)
    {
        if (address == 0) return null;
        return GLOBAL_MEMORY.getString(address);
    }

    public static MemorySegment wrap(long address, long byteSize)
    {
        return MemorySegment.ofAddress(address).reinterpret(byteSize);
    }

    public static void setMemory(long address, long byteSize, byte value)
    {
        if (address == 0L || byteSize <= 0) return;
        wrap(address, byteSize).fill(value);
    }


    public static MemorySegment wrap(long address, long byteSize, Arena arena)
    {
        return MemorySegment.ofAddress(address).reinterpret(byteSize, arena, null);
    }

    // =========================================================================
    // ATOMIC & VOLATILE DEREFERENCING METHODS (VarHandles)
    // =========================================================================

    public static byte getByteVolatile(long address) {
        return (byte) BYTE_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    public static void putByteVolatile(long address, byte value) {
        BYTE_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetByte(long address, byte expected, byte value) {
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

    public static short getShortVolatile(long address) {
        return (short) SHORT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    public static void putShortVolatile(long address, short value) {
        SHORT_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetShort(long address, short expected, short value) {
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

    public static int getIntVolatile(long address) {
        return (int) INT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    public static void putIntVolatile(long address, int value) {
        INT_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetInt(long address, int expected, int value) {
        return (boolean) INT_VH.compareAndSet(GLOBAL_MEMORY, address, expected, value);
    }

    public static int getAndSetInt(long address, int value) {
        return (int) INT_VH.getAndSet(GLOBAL_MEMORY, address, value);
    }

    public static long getLongVolatile(long address) {
        return (long) LONG_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    public static void putLongVolatile(long address, long value) {
        LONG_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetLong(long address, long expected, long value) {
        return (boolean) LONG_VH.compareAndSet(GLOBAL_MEMORY, address, expected, value);
    }

    public static long getAndSetLong(long address, long value) {
        return (long) LONG_VH.getAndSet(GLOBAL_MEMORY, address, value);
    }

    public static float getFloatVolatile(long address) {
        return (float) FLOAT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    public static void putFloatVolatile(long address, float value) {
        FLOAT_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetFloat(long address, float expected, float value) {
        return (boolean) FLOAT_VH.compareAndSet(GLOBAL_MEMORY, address, expected, value);
    }

    public static double getDoubleVolatile(long address) {
        return (double) DOUBLE_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    public static void putDoubleVolatile(long address, double value) {
        DOUBLE_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetDouble(long address, double expected, double value) {
        return (boolean) DOUBLE_VH.compareAndSet(GLOBAL_MEMORY, address, expected, value);
    }

    // nuclear, dangerous
    @HotCode
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