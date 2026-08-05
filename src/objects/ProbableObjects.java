package objects;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

@Draft
@Intention("[definition]")
public class ProbableObjects
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_PROBABLE_OBJECTS;
    public static final int TYPE_SINGLETON = TypeRegister.FORM_SINGLETON | TypeRegister.WRAP_PROBABLE_OBJECTS | CLASS_ID;

    // [definition]
    // quite simple. gets a randomized object based on your choices being added to it
    // but the thing is that each object has its own weight of chance...
    //
    // e.g.
    // oranges - 200
    // apples - 40
    // banana - 1
    //
    // total of weights = 241
    // the chance of getting apples = 40/241
    // the chance of getting oranges = 200/241
    // the chance of getting apples = 1/241 <- waow so rare
    // .
    // good for gatcha, random getting, random script running, unpredictable paths, ai,
    // procedural-non-deterministic choice, etc. many such cases.
    //
    // multiple objects with just one chance with equal share at probable class.

    /**
     * Allocates off-heap memory for a ProbableObjects array.
     * 
     * Layout (16 + capacity * 16 bytes):
     * - [block + 0L] (4 bytes): TYPE_HEADER (TYPE_SINGLETON)
     * - [block + 4L] (4 bytes): capacity (maximum choices)
     * - [userPtr + 0L] (4 bytes): count (number of active choices)
     * - [userPtr + 4L] (4 bytes): totalWeight (sum of all choice weights)
     * - [userPtr + 8L + (index * 16L)] (16 bytes per slot):
     *     - slotBase + 0L (8 bytes): objectPtr (pointer to target object)
     *     - slotBase + 8L (4 bytes): cumulativeWeight (weight sum up to this element)
     *     - slotBase + 12L (4 bytes): weight (weight of this specific element)
     */
    public static long allocate(int capacity)
    {
        long block = ForeignMemory.allocateNative(16L + capacity * 16L);
        long userPtr = block + 8L;

        ForeignMemory.setInt(block, TYPE_SINGLETON); // class type header
        ForeignMemory.setInt(block + 4L, capacity); // capacity

        ForeignMemory.setInt(userPtr, 0); // count
        ForeignMemory.setInt(userPtr + 4L, 0); // totalWeight

        return userPtr;
    }

    public static void free(long ptr)
    {
        if (ptr == 0L) return;
        ForeignMemory.freeNative(ptr - 8L);
    }

    public static int size(long ptr)
    {
        if (ptr == 0L) return 0;
        return ForeignMemory.getInt(ptr);
    }

    public static int capacity(long ptr)
    {
        if (ptr == 0L) return 0;
        return ForeignMemory.getInt(ptr - 4L);
    }

    public static int getTotalWeight(long ptr)
    {
        if (ptr == 0L) return 0;
        return ForeignMemory.getInt(ptr + 4L);
    }

    public static void add(long ptr, long objectPtr, int weight)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        int count = ForeignMemory.getInt(ptr);
        int cap = capacity(ptr);
        if (count >= cap) {
            throw new IndexOutOfBoundsException("ProbableObjects pool full! Capacity: " + cap);
        }

        long slotBase = ptr + 8L + (count * 16L);
        ForeignMemory.setLong(slotBase, objectPtr);
        
        int totalWeight = ForeignMemory.getInt(ptr + 4L);
        totalWeight += weight;
        ForeignMemory.setInt(ptr + 4L, totalWeight);

        ForeignMemory.setInt(slotBase + 8L, totalWeight); // cumulative weight
        ForeignMemory.setInt(slotBase + 12L, weight);

        ForeignMemory.setInt(ptr, count + 1);
    }

    public static void addProbable(long ptr, long probablePtr)
    {
        add(ptr, Probable.getObject(probablePtr), Probable.getWeight(probablePtr));
    }

    public static long get(long ptr)
    {
        return util.Random.probablePool(ptr);
    }

    private ProbableObjects() {}
}


