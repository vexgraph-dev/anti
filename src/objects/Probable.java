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

    private Probable() {}

    /**
     * Allocates off-heap memory for a Probable choice object.
     * <p>
     * Layout (24 bytes):
     * - [block + 0L] (4 bytes): TYPE_HEADER (TYPE_SINGLETON)
     * - [block + 4L] (4 bytes): Active Flag (1)
     * - [userPtr + 0L] (8 bytes): objectPtr (pointer to target object choice)
     * - [userPtr + 8L] (4 bytes): weight (relative weight/chance of this choice)
     * - [userPtr + 12L] (4 bytes): total (total weight cumulative value)
     */
    public static long allocate(long objectPtr, int weight, int total)
    {
        long block = ForeignMemory.allocateNative(24);
        long userPtr = block + 8L;

        ForeignMemory.setInt(block, TYPE_SINGLETON); // class type header
        ForeignMemory.setInt(block + 4L, 1); // active flag

        ForeignMemory.setLong(userPtr, objectPtr); // objectPtr
        ForeignMemory.setInt(userPtr + 8L, weight); // weight
        ForeignMemory.setInt(userPtr + 12L, total); // total

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

    public static int getWeight(long ptr)
    {
        return ForeignMemory.getInt(ptr + 8L);
    }

    public static int getTotal(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getInt(ptr + 12L);
    }

    public static void setObject(long ptr, long objectPtr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        ForeignMemory.setLong(ptr, objectPtr);
    }

    public static void setWeight(long ptr, int weight)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        ForeignMemory.setInt(ptr + 8L, weight);
    }

    public static void setTotal(long ptr, int total)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        ForeignMemory.setInt(ptr + 12L, total);
    }

    public static long get(long ptr)
    {
        return util.Random.sample(ptr);
    }
}

