package objects;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;

@Draft
@Intention("[purpose]")
public class Global
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_GLOBAL;
    public static final int TYPE_SINGLETON = TypeRegister.FORM_SINGLETON | TypeRegister.MOD_GLOBAL | CLASS_ID;

    /*
     [purpose]
     the purpose of the global variable is that the gloabel variable will be used to make
     a pointer that is dedicated for a single variable (whether it be a struct, a primitive,
     an array of objects, etc. it can act as just a pointer, it doesnt allocate a array for
     each thread. shall be allocate as global when allocated for games.
    */

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
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        ForeignMemory.setLong(enginePtr, initialValue); // value

        return enginePtr;
    }

    public static void free(long ptr)
    {
        Bit64.free(ptr);
    }

    public static long get(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getVolatileLong(ptr);
    }

    public static void set(long ptr, long value)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        ForeignMemory.setVolatileLong(ptr, value);
    }

    public static boolean compareAndSet(long ptr, long expected, long value)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.compareAndSetLong(ptr, expected, value);
    }

    @Intention("[purpose] line [n]")
    private Global() {}
}
