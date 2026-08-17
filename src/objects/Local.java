package objects;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;
import thread.ThreadRegistry;

@Draft
@Intention("[purpose]")
public class Local
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_LOCAL;
    public static final int TYPE_SINGLETON = TypeRegister.FORM_SINGLETON | TypeRegister.MOD_LOCALE | CLASS_ID;

    private static final long STRUCT_SIZE = 2048L; // 256 threads * 8 bytes = 2048 bytes payload

    // [purpose]
    // the purpose of the local variable is that the local variable will be used
    // to generate a bunch of singletons/pointers, regarding objects to act as a singleton
    // multipurpose variable. this is like when a user makes a custom variable in the script
    // and the same script is being run on multiple threads, that creates a mess. it shall be
    // managed accordingly based on their own. global on the other hand will create their own
    // race conditions over a SINGLE variable. if that makes even sense...

    /**
     * Allocates off-heap memory for a Local object.
     * 
     * Layout (8 + 2048 bytes):
     * - [block + 0L] (4 bytes): TYPE_HEADER (TYPE_SINGLETON)
     * - [block + 4L] (4 bytes): Thread capacity (256)
     * - [userPtr + (tid * 8L)] (8 bytes): Value slot for thread index 'tid'
     */
    public static long allocate()
    {
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        long struct = ForeignMemory.allocateNative(STRUCT_SIZE);
        ForeignMemory.setLong(enginePtr, struct);

        // Zero out the thread local slots
        ForeignMemory.setMemory(struct, STRUCT_SIZE, (byte) 0); // thread-local variable value slots

        return enginePtr;
    }

    public static void free(long ptr)
    {
        if (ptr == 0L) return;
        long struct = ForeignMemory.getLong(ptr);
        if (struct != 0L) ForeignMemory.freeNative(struct);
        Bit64.free(ptr);
    }

    private static long struct(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getLong(ptr);
    }

    public static long get(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        int tid = ThreadRegistry.getThreadIndex();
        return ForeignMemory.getVolatileLong(struct(ptr) + (tid * 8L));
    }

    public static void set(long ptr, long value)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        int tid = ThreadRegistry.getThreadIndex();
        ForeignMemory.setVolatileLong(struct(ptr) + (tid * 8L), value);
    }

    public static boolean compareAndSet(long ptr, long expected, long value)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        int tid = ThreadRegistry.getThreadIndex();
        return ForeignMemory.compareAndSetLong(struct(ptr) + (tid * 8L), expected, value);
    }

    @Intention("[purpose] line [n]")
    private Local() {}
}
