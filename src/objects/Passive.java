package objects;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

@Draft
@Intention("[definition]")
public class Passive
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_PASSIVE;
    public static final int TYPE_SINGLETON = TypeRegister.FORM_SINGLETON | TypeRegister.WRAP_PROACTIVE | CLASS_ID;

    private static final MethodHandle GET_INVOKER;
    private static final MethodHandle SET_INVOKER;

    static {
        Linker linker = Linker.nativeLinker();
        GET_INVOKER = linker.downcallHandle(FunctionDescriptor.of(ValueLayout.JAVA_LONG));
        SET_INVOKER = linker.downcallHandle(FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    }

    private static final long STRUCT_SIZE = 24L; // [0]=cached value, [8]=getFunc, [16]=setFunc

    // [definition]
    // the passive object acts as a lazy object. instead of setting and getting objects itself,
    // the setting of the object is a function, returning a value, and then the get is that exact
    // same function being ran. thats about it. its a different interpretation.
    //
    // though the scripting system shall work first before this because it is quite hard to implement
    // without a proper scripting purpose. but who knows. (check reactive object),

    /**
     * Allocates off-heap memory for a Passive object.
     * <p>
     * Layout (32 bytes):
     * - [block + 0L] (4 bytes): TYPE_HEADER (TYPE_SINGLETON)
     * - [block + 4L] (4 bytes): Active Flag (1)
     * - [userPtr + 0L] (8 bytes): cached value (the cached long value of the object)
     * - [userPtr + 8L] (8 bytes): getFuncAddress (callback getter function address)
     * - [userPtr + 16L] (8 bytes): setFuncAddress (callback setter function address)
     */
    public static long allocate(long getFuncAddress, long setFuncAddress)
    {
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        long struct = ForeignMemory.allocateNative(STRUCT_SIZE);
        ForeignMemory.setLong(enginePtr, struct);

        ForeignMemory.setLong(struct, 0L); // cached value
        ForeignMemory.setLong(struct + 8L, getFuncAddress); // getFuncAddress
        ForeignMemory.setLong(struct + 16L, setFuncAddress); // setFuncAddress

        return enginePtr;
    }

    public static void free(long ptr)
    {
        Object.free(ptr);
    }

    private static long struct(long ptr)
    {
        return Object.struct(ptr);
    }

    public static long getValue(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        long getFunc = ForeignMemory.getLong(struct(ptr) + 8L);
        if (getFunc != 0L) {
            try {
                return (long) GET_INVOKER.bindTo(MemorySegment.ofAddress(getFunc)).invokeExact();
            } catch (Throwable t) {
                throw new RuntimeException("Failed to invoke getFunction: " + t.getMessage(), t);
            }
        }
        return ForeignMemory.getLong(struct(ptr));
    }

    public static void setValue(long ptr, long value)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        long setFunc = ForeignMemory.getLong(struct(ptr) + 16L);
        if (setFunc != 0L) {
            try {
                SET_INVOKER.bindTo(MemorySegment.ofAddress(setFunc)).invokeExact(value);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to invoke setFunction: " + t.getMessage(), t);
            }
        } else {
            ForeignMemory.setLong(struct(ptr), value);
        }
    }

    private Passive() {}
}
