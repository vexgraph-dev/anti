package objects;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

@Draft
@Intention("[purpose]")
public class Global
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_GLOBAL;
    public static final int TYPE_SINGLETON = TypeRegister.FORM_SINGLETON | TypeRegister.MOD_GLOBAL | CLASS_ID;

    private static final VarHandle LONG_VH = ValueLayout.JAVA_LONG.varHandle();
    private static final MemorySegment GLOBAL_MEMORY = MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE);

    // [purpose]
    // the purpose of the global variable is that the gloabel variable will be used to make
    // a pointer that is dedicated for a single variable (whether it be a struct, a primitive,
    // an array of objects, etc. it can act as just a pointer, it doesnt allocate a array for
    // each thread. shall be allocate as global when allocated for games.

    /**
     * Allocates off-heap memory for a Global pointer object.
     * 
     * Layout (16 bytes):
     * - [block + 0L] (4 bytes): TYPE_HEADER (TYPE_SINGLETON)
     * - [block + 4L] (4 bytes): Active Flag (1)
     * - [userPtr + 0L] (8 bytes): value (underlying global long value/pointer)
     */
    public static long allocate(long initialValue)
    {
        long block = ForeignMemory.allocateNative(16);
        long userPtr = block + 8L;

        ForeignMemory.setInt(block, TYPE_SINGLETON); // class type header
        ForeignMemory.setInt(block + 4L, 1); // active flag

        ForeignMemory.setLong(userPtr, initialValue); // value

        return userPtr;
    }

    public static void free(long ptr)
    {
        ForeignMemory.freeNative(ptr - 8L);
    }

    public static long get(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return (long) LONG_VH.getVolatile(GLOBAL_MEMORY, ptr);
    }

    public static void set(long ptr, long value)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        LONG_VH.setVolatile(GLOBAL_MEMORY, ptr, value);
    }

    public static boolean compareAndSet(long ptr, long expected, long value)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return (boolean) LONG_VH.compareAndSet(GLOBAL_MEMORY, ptr, expected, value);
    }

    @Intention("[purpose] line [n]")
    private Global() {}
}

