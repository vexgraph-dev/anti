package objects;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

@Draft
@Intention("[definition]")
public class Probable
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_PROBABLE;
    public static final int TYPE_SINGLETON = TypeRegister.FORM_SINGLETON | TypeRegister.WRAP_PROBABLE | CLASS_ID;

    // [definition]
    // quite simple. gets a randomized object based on your choices being added to it..
    // good for gatcha, random getting, random script running, unpredictable paths, ai,
    // procedural-non-deterministic choice, etc. many such cases.
    //
    // multiple objects with specific chance (being in one pool) at probableobjects class.

    public static long allocate(long objectPtr, double weight)
    {
        long block = ForeignMemory.allocateNative(24);
        long userPtr = block + 8L;

        ForeignMemory.putInt(block, TYPE_SINGLETON);
        ForeignMemory.putInt(block + 4L, 1);

        ForeignMemory.putLong(userPtr, objectPtr);
        ForeignMemory.putDouble(userPtr + 8L, weight);

        return userPtr;
    }

    public static void free(long ptr)
    {
        if (ptr == 0L) return;
        ForeignMemory.freeNative(ptr - 8L);
    }

    public static long getObject(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getLong(ptr);
    }

    public static double getWeight(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getDouble(ptr + 8L);
    }

    public static void setObject(long ptr, long objectPtr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        ForeignMemory.putLong(ptr, objectPtr);
    }

    public static void setWeight(long ptr, double weight)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        ForeignMemory.putDouble(ptr + 8L, weight);
    }

    public Probable()
    {

    }
}
