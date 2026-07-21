package nio;

import annotation.HotCode;
import oop.Struct;
import primitive.Byte;
import primitive.Double;
import primitive.Float;
import primitive.Int32;
import primitive.Int32Fp32;
import primitive.Int32Fp64;
import primitive.Int64;
import primitive.Int64Fp32;
import primitive.Int64Fp64;
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

public class ForeignMemory
{
    // god, the lens of all things. might be dangerous to hold, low-key
    // literally c flavored hell java version
    // good luck myself
    private static final MemorySegment GLOBAL_MEMORY = MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE);

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

    // nuclear, dangerous
    @HotCode
    public static void freeAllClasses()
    {

        Byte.freeAll();
        Short.freeAll();
        Int32.freeAll();
        Int64.freeAll();
        Float.freeAll();
        Double.freeAll();
        Int32Fp32.freeAll();
        Int32Fp64.freeAll();
        Int64Fp32.freeAll();
        Int64Fp64.freeAll();
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