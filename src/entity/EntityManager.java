package entity;

import annotation.HotCode;
import primitive.Int;
import nio.ForeignMemory;

/**
 * Zero-GC Off-Heap Entity Manager.
 * Manages the generation, recycling, and lifecycle of Entity IDs.
 */
@HotCode
public final class EntityManager {
    
    private static int nextEntityId = 0;
    
    private static long recycledQueuePtr = 0L; // Int array pointer
    private static int recycleCount = 0;
    private static int recycleCapacity = 1024;

    static {
        // Initialize the recycle queue using your existing Int primitive pool!
        recycledQueuePtr = Int.allocateArray(recycleCapacity);
    }

    private EntityManager() {}

    /**
     * Generates a new unique Entity ID, recycling old ones if possible.
     */
    @HotCode
    public static int create() {
        if (recycleCount > 0) {
            recycleCount--;
            return Int.get(recycledQueuePtr, recycleCount);
        }
        return nextEntityId++;
    }

    /**
     * Destroys an entity and pushes its ID back to the recycle queue.
     * Note: You will also need to manually remove this entity ID from all SparseSets!
     */
    @HotCode
    public static void destroy(int entityId) {
        if (recycleCount >= recycleCapacity) {
            // Resize recycle queue
            int newCap = recycleCapacity * 2;
            long newQueue = Int.allocateArray(newCap);
            // Copy data over
            ForeignMemory.copy(recycledQueuePtr, newQueue, (long) recycleCapacity * 4L);
            Int.free(recycledQueuePtr);
            recycledQueuePtr = newQueue;
            recycleCapacity = newCap;
        }
        Int.set(recycledQueuePtr, recycleCount++, entityId);
    }

    /**
     * Gets the absolute maximum number of unique entities that have been created.
     */
    @HotCode
    public static int getMaxEntities() {
        return nextEntityId;
    }
}
