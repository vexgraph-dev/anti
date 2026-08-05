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
@Intention("just an object that cannot be put in a file for serialized work")
public class Transient
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_TRANSIENT;
    public static final int TYPE_SINGLETON = TypeRegister.FORM_SINGLETON | TypeRegister.MOD_TRANSIENT | CLASS_ID;

    private static final VarHandle LONG_VH = ValueLayout.JAVA_LONG.varHandle();
    private static final MemorySegment GLOBAL_MEMORY = MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE);

    // just an object that cannot be put in a file for
    // serialized work, or a variable to be put in a persistence file.
    // thats about it.

    public static long allocate(long val)
    {
        long block = ForeignMemory.allocateNative(16);
        long userPtr = block + 8L;

        ForeignMemory.putInt(block, TYPE_SINGLETON);
        ForeignMemory.putInt(block + 4L, 1);

        ForeignMemory.putLong(userPtr, val);

        return userPtr;
    }

    public static void free(long ptr)
    {
        if (ptr == 0L) return;
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

    public Transient()
    {

    }
}

