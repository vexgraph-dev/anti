package objects;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;

@Draft
@Intention("just an object that cannot be put in a file for serialized work")
public class Transient
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_TRANSIENT;
    public static final int TYPE_SINGLETON = TypeRegister.FORM_SINGLETON | TypeRegister.MOD_TRANSIENT | CLASS_ID;

    // just an object that cannot be put in a file for
    // serialized work, or a variable to be put in a persistence file.
    // thats about it.

    /**
     * Allocates off-heap memory for a Transient pointer object.
     * 
     * Layout (16 bytes):
     * - [block + 0L] (4 bytes): TYPE_HEADER (TYPE_SINGLETON)
     * - [block + 4L] (4 bytes): Active Flag (1)
     * - [userPtr + 0L] (8 bytes): value (underlying transient long value/pointer)
     */
    public static long allocate(long val)
    {
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        ForeignMemory.setLong(enginePtr, val); // value

        return enginePtr;
    }

    public static void free(long ptr)
    {
        if (ptr == 0L) return;
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

    public Transient()
    {

    }
}
