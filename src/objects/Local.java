package objects;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;
import thread.ThreadRegistry;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

@Draft
@Intention("[purpose]")
public class Local
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_LOCAL;
    public static final int TYPE_SINGLETON = TypeRegister.FORM_SINGLETON | TypeRegister.MOD_LOCALE | CLASS_ID;

    private static final VarHandle LONG_VH = ValueLayout.JAVA_LONG.varHandle();
    private static final MemorySegment GLOBAL_MEMORY = MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE);

    // [purpose]
    // the purpose of the local variable is that the local variable will be used
    // to generate a bunch of singletons/pointers, regarding objects to act as a singleton
    // multipurpose variable. this is like when a user makes a custom variable in the script
    // and the same script is being run on multiple threads, that creates a mess. it shall be
    // managed accordingly based on their own. global on the other hand will create their own
    // race conditions over a SINGLE variable. if that makes even sense...

    public static long allocate()
    {
        // 256 threads * 8 bytes = 2048 bytes payload
        long block = ForeignMemory.allocateNative(8L + 2048L);
        long userPtr = block + 8L;

        ForeignMemory.setInt(block, TYPE_SINGLETON);
        ForeignMemory.setInt(block + 4L, 256);

        // Zero out the thread local slots
        ForeignMemory.setMemory(userPtr, 2048L, (byte) 0);

        return userPtr;
    }

    public static void free(long ptr)
    {
        Object.free(ptr);
    }

    public static long get(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        int tid = ThreadRegistry.getThreadIndex();
        return (long) LONG_VH.getVolatile(GLOBAL_MEMORY, ptr + (tid * 8L));
    }

    public static void set(long ptr, long value)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        int tid = ThreadRegistry.getThreadIndex();
        LONG_VH.setVolatile(GLOBAL_MEMORY, ptr + (tid * 8L), value);
    }

    public static boolean compareAndSet(long ptr, long expected, long value)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        int tid = ThreadRegistry.getThreadIndex();
        return (boolean) LONG_VH.compareAndSet(GLOBAL_MEMORY, ptr + (tid * 8L), expected, value);
    }

    @Intention("[purpose] line [n]")
    private Local() {}
}

