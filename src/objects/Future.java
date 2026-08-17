package objects;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import bit.Bit64;
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

    private static final long STRUCT_SIZE = 16L; // 8B struct: [0]=given (1B), [8]=value (8B)

    // this is for things... where the data will be null, but will eventually return smth.
    // layout: boolean::given (which means that the value is already presented)
    // setDesiredValue()

    /**
     * Allocates off-heap memory for a Future object.
     * 
     * Layout (24 bytes):
     * - [block + 0L] (4 bytes): TYPE_HEADER (TYPE_SINGLETON)
     * - [block + 4L] (4 bytes): Active Flag (1)
     * - [userPtr + 0L] (1 byte): given (boolean flag indicating if value is present)
     * - [userPtr + 8L] (8 bytes): value (long pointer/value returned by the future)
     */
    public static long allocate()
    {
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        long struct = ForeignMemory.allocateNative(STRUCT_SIZE);
        ForeignMemory.setLong(enginePtr, struct);

        ForeignMemory.setByte(struct, (byte) 0); // given
        ForeignMemory.setLong(struct + 8L, 0L); // value

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

    public static boolean isGiven(long ptr)
    {
        if (ptr == 0L) return false;
        return (byte) BYTE_VH.getVolatile(GLOBAL_MEMORY, struct(ptr)) != (byte) 0;
    }

    public static long get(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getLong(struct(ptr) + 8L);
    }

    public static boolean setDesiredValue(long ptr, long value)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        if (ForeignMemory.compareAndSetByte(struct(ptr), (byte) 0, (byte) 1)) {
            ForeignMemory.setLong(struct(ptr) + 8L, value);
            return true;
        }
        return false;
    }

    private Future() {}
}
