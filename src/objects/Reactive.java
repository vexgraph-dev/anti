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
public class Reactive
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_REACTIVE;
    public static final int TYPE_SINGLETON = TypeRegister.FORM_SINGLETON | TypeRegister.WRAP_REACTIVE | CLASS_ID;

    private static final MethodHandle GET_INVOKER;
    private static final MethodHandle SET_INVOKER;

    static {
        Linker linker = Linker.nativeLinker();
        GET_INVOKER = linker.downcallHandle(FunctionDescriptor.of(ValueLayout.JAVA_LONG));
        SET_INVOKER = linker.downcallHandle(FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    }

    // [definition]
    // the reactive object acts as a reactive object that makes scripting easy because it is just THAT reactive.
    // when a item of this object changes, it will run an event, and run that certain event. many events happen.
    //
    // long in question in here is a REACTIVE OBJECT POINTER btw... must be...
    // setValueEvent(long e)
    // getValueEvent(long e)
    // changedValueEVent(long e) <- this is when the reactive object's underlying fields got changed. (its a lot to work on)
    //
    // the implementation:
    // e.g. if the health bar changed, the script runs (the effects happen, the death sound pops up, ui changes, etc...)
    //
    // though the scripting system shall work first before this because it is quite hard to implement
    // without a proper scripting purpose. but who knows. (check passive object),
    //
    // just so you know:
    // a reactive object CANNOT be passive at the same time and vice versa. thats gonna make a recursive action.

    public static long allocate(long initialValue)
    {
        long block = ForeignMemory.allocateNative(40);
        long userPtr = block + 8L;

        ForeignMemory.putInt(block, TYPE_SINGLETON);
        ForeignMemory.putInt(block + 4L, 1);

        ForeignMemory.putLong(userPtr, initialValue);
        ForeignMemory.putLong(userPtr + 8L, 0L); // setValueEvent
        ForeignMemory.putLong(userPtr + 16L, 0L); // getValueEvent
        ForeignMemory.putLong(userPtr + 24L, 0L); // changedValueEvent

        return userPtr;
    }

    public static void free(long ptr)
    {
        if (ptr == 0L) return;
        ForeignMemory.freeNative(ptr - 8L);
    }

    public static void setValueEvent(long ptr, long eventCallbackAddr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        ForeignMemory.putLong(ptr + 8L, eventCallbackAddr);
    }

    public static void getValueEvent(long ptr, long eventCallbackAddr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        ForeignMemory.putLong(ptr + 16L, eventCallbackAddr);
    }

    public static void changedValueEvent(long ptr, long eventCallbackAddr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        ForeignMemory.putLong(ptr + 24L, eventCallbackAddr);
    }

    public static long getValue(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        long getEv = ForeignMemory.getLong(ptr + 16L);
        if (getEv != 0L) {
            try {
                GET_INVOKER.bindTo(MemorySegment.ofAddress(getEv)).invokeExact();
            } catch (Throwable t) {
                throw new RuntimeException("Failed to invoke getValueEvent: " + t.getMessage(), t);
            }
        }
        return ForeignMemory.getLong(ptr);
    }

    public static void setValue(long ptr, long value)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        long setEv = ForeignMemory.getLong(ptr + 8L);
        if (setEv != 0L) {
            try {
                SET_INVOKER.bindTo(MemorySegment.ofAddress(setEv)).invokeExact(value);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to invoke setValueEvent: " + t.getMessage(), t);
            }
        }

        long oldVal = ForeignMemory.getLong(ptr);
        ForeignMemory.putLong(ptr, value);

        if (oldVal != value) {
            long changedEv = ForeignMemory.getLong(ptr + 24L);
            if (changedEv != 0L) {
                try {
                    SET_INVOKER.bindTo(MemorySegment.ofAddress(changedEv)).invokeExact(value);
                } catch (Throwable t) {
                    throw new RuntimeException("Failed to invoke changedValueEvent: " + t.getMessage(), t);
                }
            }
        }
    }

    public Reactive()
    {

    }
}

