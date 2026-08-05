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
@Intention("[definition]")
public class Future
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_FUTURE;
    public static final int TYPE_SINGLETON = TypeRegister.FORM_SINGLETON | TypeRegister.WRAP_FUTURE | CLASS_ID;

    private static final VarHandle BYTE_VH = ValueLayout.JAVA_BYTE.varHandle();
    private static final MemorySegment GLOBAL_MEMORY = MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE);

    // this is for things... where the data will be null, but will eventually return smth.
    // layout: boolean::given (which means that the value is already presented)
    // setDesiredValue()

    public static long allocate()
    {
        long block = ForeignMemory.allocateNative(24);
        long userPtr = block + 8L;

        ForeignMemory.putInt(block, TYPE_SINGLETON);
        ForeignMemory.putInt(block + 4L, 1);

        ForeignMemory.putByte(userPtr, (byte) 0); // given = false
        ForeignMemory.putLong(userPtr + 8L, 0L); // value = null/0

        return userPtr;
    }

    public static void free(long ptr)
    {
        if (ptr == 0L) return;
        ForeignMemory.freeNative(ptr - 8L);
    }

    public static boolean isGiven(long ptr)
    {
        if (ptr == 0L) return false;
        return (byte) BYTE_VH.getVolatile(GLOBAL_MEMORY, ptr) != (byte) 0;
    }

    public static long get(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getLong(ptr + 8L);
    }

    public static boolean setDesiredValue(long ptr, long value)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        if ((boolean) BYTE_VH.compareAndSet(GLOBAL_MEMORY, ptr, (byte) 0, (byte) 1)) {
            ForeignMemory.putLong(ptr + 8L, value);
            return true;
        }
        return false;
    }

    private Future() {}
}
