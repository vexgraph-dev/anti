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

import nio.StringLookup;
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

    private static final long STRUCT_SIZE = 32L; // [0]=value, [8]=setValueEvent, [16]=getValueEvent, [24]=changedValueEvent

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

    /**
     * Allocates off-heap memory for a Reactive object.
     * 
     * Layout (40 bytes):
     * - [block + 0L] (4 bytes): TYPE_HEADER (TYPE_SINGLETON)
     * - [block + 4L] (4 bytes): Active Flag (1)
     * - [userPtr + 0L] (8 bytes): value (underlying long value of the reactive object)
     * - [userPtr + 8L] (8 bytes): setValueEvent (callback function address executed on write)
     * - [userPtr + 16L] (8 bytes): getValueEvent (callback function address executed on read)
     * - [userPtr + 24L] (8 bytes): changedValueEvent (callback function address executed when value is changed)
     */
    public static long allocate(long initialValue)
    {
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        long struct = ForeignMemory.allocateNative(STRUCT_SIZE);
        ForeignMemory.setLong(enginePtr, struct);

        ForeignMemory.setLong(struct, initialValue); // value
        ForeignMemory.setLong(struct + 8L, 0L); // setValueEvent
        ForeignMemory.setLong(struct + 16L, 0L); // getValueEvent
        ForeignMemory.setLong(struct + 24L, 0L); // changedValueEvent

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
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        return ForeignMemory.getLong(ptr);
    }

    public static void setValueEvent(long ptr, long eventCallbackAddr)
    {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        ForeignMemory.setLong(struct(ptr) + 8L, eventCallbackAddr);
    }

    public static void getValueEvent(long ptr, long eventCallbackAddr)
    {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        ForeignMemory.setLong(struct(ptr) + 16L, eventCallbackAddr);
    }

    public static void changedValueEvent(long ptr, long eventCallbackAddr)
    {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        ForeignMemory.setLong(struct(ptr) + 24L, eventCallbackAddr);
    }

    public static long getValue(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        long getEv = ForeignMemory.getLong(struct(ptr) + 16L);
        if (getEv != 0L) {
            try {
                GET_INVOKER.bindTo(MemorySegment.ofAddress(getEv)).invokeExact();
            } catch (Throwable t) {
                throw new RuntimeException(StringLookup.getJavaString(358) + t.getMessage(), t);
            }
        }
        return ForeignMemory.getLong(struct(ptr));
    }

    public static void setValue(long ptr, long value)
    {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        long setEv = ForeignMemory.getLong(struct(ptr) + 8L);
        if (setEv != 0L) {
            try {
                SET_INVOKER.bindTo(MemorySegment.ofAddress(setEv)).invokeExact(value);
            } catch (Throwable t) {
                throw new RuntimeException(StringLookup.getJavaString(359) + t.getMessage(), t);
            }
        }

        long oldVal = ForeignMemory.getLong(struct(ptr));
        ForeignMemory.setLong(struct(ptr), value);

        if (oldVal != value) {
            long changedEv = ForeignMemory.getLong(struct(ptr) + 24L);
            if (changedEv != 0L) {
                try {
                    SET_INVOKER.bindTo(MemorySegment.ofAddress(changedEv)).invokeExact(value);
                } catch (Throwable t) {
                    throw new RuntimeException(StringLookup.getJavaString(360) + t.getMessage(), t);
                }
            }
        }
    }

    public Reactive()
    {

    }
}

