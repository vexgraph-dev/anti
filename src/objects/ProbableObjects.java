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

    public static long allocate(int capacity)
    {
        long block = ForeignMemory.allocateNative(24L + capacity * 24L);
        long userPtr = block + 8L;

        ForeignMemory.putInt(block, TYPE_SINGLETON);
        ForeignMemory.putInt(block + 4L, capacity);

        ForeignMemory.putInt(userPtr, 0); // count = 0
        ForeignMemory.putInt(userPtr + 4L, 0); // padding
        ForeignMemory.putDouble(userPtr + 8L, 0.0); // totalWeight = 0.0

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

    public static double getTotalWeight(long ptr)
    {
        if (ptr == 0L) return 0.0;
        return ForeignMemory.getDouble(ptr + 8L);
    }

    public static void add(long ptr, long objectPtr, double weight)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        int count = ForeignMemory.getInt(ptr);
        int cap = capacity(ptr);
        if (count >= cap) {
            throw new IndexOutOfBoundsException("ProbableObjects pool full! Capacity: " + cap);
        }

        long slotBase = ptr + 16L + (count * 24L);
        ForeignMemory.putLong(slotBase, objectPtr);
        ForeignMemory.putDouble(slotBase + 8L, weight);

        double totalWeight = ForeignMemory.getDouble(ptr + 8L);
        totalWeight += weight;
        ForeignMemory.putDouble(ptr + 8L, totalWeight);

        // Store cumulative weight
        ForeignMemory.putDouble(slotBase + 16L, totalWeight);

        ForeignMemory.putInt(ptr, count + 1);
    }

    public static void addProbable(long ptr, long probablePtr)
    {
        add(ptr, Probable.getObject(probablePtr), Probable.getWeight(probablePtr));
    }

    public static long sample(long ptr, long randomPtr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        int count = size(ptr);
        if (count == 0) return 0L;

        double totalWeight = getTotalWeight(ptr);
        if (totalWeight <= 0.0) {
            int idx = (int) (util.Random.nextDouble(randomPtr) * count);
            if (idx >= count) idx = count - 1;
            long slotBase = ptr + 16L + (idx * 24L);
            return ForeignMemory.getLong(slotBase);
        }

        double r = util.Random.nextDouble(randomPtr) * totalWeight;

        int low = 0;
        int high = count - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            long slotBase = ptr + 16L + (mid * 24L);
            double cumWeight = ForeignMemory.getDouble(slotBase + 16L);
            if (cumWeight < r) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }

        long slotBase = ptr + 16L + (low * 24L);
        return ForeignMemory.getLong(slotBase);
    }

    public static long sample(long ptr)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        int count = size(ptr);
        if (count == 0) return 0L;

        double totalWeight = getTotalWeight(ptr);
        if (totalWeight <= 0.0) {
            int idx = (int) (Math.random() * count);
            if (idx >= count) idx = count - 1;
            long slotBase = ptr + 16L + (idx * 24L);
            return ForeignMemory.getLong(slotBase);
        }

        double r = Math.random() * totalWeight;

        int low = 0;
        int high = count - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            long slotBase = ptr + 16L + (mid * 24L);
            double cumWeight = ForeignMemory.getDouble(slotBase + 16L);
            if (cumWeight < r) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }

        long slotBase = ptr + 16L + (low * 24L);
        return ForeignMemory.getLong(slotBase);
    }

    public ProbableObjects()
    {

    }
}

