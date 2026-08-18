package struct;

import annotation.HotCode;
import primitive.IntFloat;

import nio.StringLookup;
/**
 * Zero-GC Off-Heap Min-Heap implementation.
 * Wraps an IntFloat array pointer.
 * Int = item (e.g. entity ID), Float = priority/cost.
 * Uses 1-based indexing for optimized heap math.
 * Index 0 stores the current size of the heap in the Int part.
 */
@HotCode
public final class MinHeap {

    private MinHeap() {}

    @HotCode
    public static long allocate(int capacity) {
        // allocate capacity + 1 (for 1-based indexing and storing size at index 0)
        long ptr = IntFloat.allocateArray(capacity + 1);
        IntFloat.setUnsafe(ptr, 0, 0, 0f); // size = 0
        return ptr;
    }

    @HotCode
    public static void free(long ptr) {
        IntFloat.free(ptr);
    }

    @HotCode
    public static int size(long ptr) {
        return IntFloat.unsafeGetIntPart(ptr, 0);
    }

    @HotCode
    public static int capacity(long ptr) {
        return IntFloat.length(ptr) - 1;
    }

    @HotCode
    public static boolean isEmpty(long ptr) {
        return size(ptr) == 0;
    }

    @HotCode
    public static void push(long ptr, int item, float priority) {
        int sz = size(ptr);
        sz++;
        
        int cap = capacity(ptr);
        if (sz > cap) {
            throw new IllegalStateException(StringLookup.getJavaString(389) + cap);
        }
        
        IntFloat.setUnsafe(ptr, 0, sz, 0f); // update size
        IntFloat.setUnsafe(ptr, sz, item, priority); // place at end
        siftUp(ptr, sz); // sift up to correct position
    }

    @HotCode
    public static int popItem(long ptr) {
        int sz = size(ptr);
        if (sz == 0) throw new IllegalStateException(StringLookup.getJavaString(390));
        
        // Root is always at index 1
        int result = IntFloat.unsafeGetIntPart(ptr, 1);
        
        // Move last element to root
        int lastItem = IntFloat.unsafeGetIntPart(ptr, sz);
        float lastPrio = IntFloat.unsafeGetFloatPart(ptr, sz);
        IntFloat.setUnsafe(ptr, 1, lastItem, lastPrio);
        
        sz--;
        IntFloat.setUnsafe(ptr, 0, sz, 0f);
        
        if (sz > 0) {
            siftDown(ptr, 1);
        }
        
        return result;
    }

    @HotCode
    private static void siftUp(long ptr, int index) {
        int item = IntFloat.unsafeGetIntPart(ptr, index);
        float prio = IntFloat.unsafeGetFloatPart(ptr, index);
        
        while (index > 1) {
            int parent = index / 2;
            float parentPrio = IntFloat.unsafeGetFloatPart(ptr, parent);
            
            if (prio >= parentPrio) {
                break;
            }
            
            // move parent down
            int parentItem = IntFloat.unsafeGetIntPart(ptr, parent);
            IntFloat.setUnsafe(ptr, index, parentItem, parentPrio);
            index = parent;
        }
        IntFloat.setUnsafe(ptr, index, item, prio);
    }

    @HotCode
    private static void siftDown(long ptr, int index) {
        int sz = size(ptr);
        int item = IntFloat.unsafeGetIntPart(ptr, index);
        float prio = IntFloat.unsafeGetFloatPart(ptr, index);
        
        int half = sz / 2;
        while (index <= half) {
            int left = index * 2;
            int right = left + 1;
            
            int bestChild = left;
            float bestPrio = IntFloat.unsafeGetFloatPart(ptr, left);
            
            if (right <= sz) {
                float rightPrio = IntFloat.unsafeGetFloatPart(ptr, right);
                if (rightPrio < bestPrio) {
                    bestChild = right;
                    bestPrio = rightPrio;
                }
            }
            
            if (prio <= bestPrio) {
                break;
            }
            
            // move child up
            int childItem = IntFloat.unsafeGetIntPart(ptr, bestChild);
            IntFloat.setUnsafe(ptr, index, childItem, bestPrio);
            index = bestChild;
        }
        IntFloat.setUnsafe(ptr, index, item, prio);
    }
}
