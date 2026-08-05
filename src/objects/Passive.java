package objects;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
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

    // [definition]
    // the passive object acts as a lazy object. instead of setting and getting objects itself,
    // the setting of the object is a function, returning a value, and then the get is that exact
    // same function being ran. thats about it. its a different interpretation.
    //
    // though the scripting system shall work first before this because it is quite hard to implement
    // without a proper scripting purpose. but who knows. (check reactive object),

    public static long allocate(long getFuncAddress, long setFuncAddress)
    {
        long block = ForeignMemory.allocateNative(32);
        long userPtr = block + 8L;

        ForeignMemory.putInt(block, TYPE_SINGLETON);
        ForeignMemory.putInt(block + 4L, 1);

        ForeignMemory.putLong(userPtr, 0L); // cached value
        ForeignMemory.putLong(userPtr + 8L, getFuncAddress);
        ForeignMemory.putLong(userPtr + 16L, setFuncAddress);

        return userPtr;
    }

    public static void free(long ptr)
    {
        if (ptr == 0L) return;
        ForeignMemory.freeNative(ptr - 8L);
    }

    public static long getValue(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        long getFunc = ForeignMemory.getLong(ptr + 8L);
        if (getFunc != 0L) {
            try {
                return (long) GET_INVOKER.bindTo(MemorySegment.ofAddress(getFunc)).invokeExact();
            } catch (Throwable t) {
                throw new RuntimeException("Failed to invoke getFunction: " + t.getMessage(), t);
            }
        }
        return ForeignMemory.getLong(ptr);
    }

    public static void setValue(long ptr, long value)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        long setFunc = ForeignMemory.getLong(ptr + 16L);
        if (setFunc != 0L) {
            try {
                SET_INVOKER.bindTo(MemorySegment.ofAddress(setFunc)).invokeExact(value);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to invoke setFunction: " + t.getMessage(), t);
            }
        } else {
            ForeignMemory.putLong(ptr, value);
        }
    }

    private Passive() {}
}
