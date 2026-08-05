package struct;

import annotation.HotCode;
import nio.ForeignMemory;
import primitive.Int;

/**
 * Zero-GC Off-Heap Sparse Set for ECS.
 * Maps sparse Entity IDs (int) to a tightly packed dense index.
 * It optionally manages a contiguous block of data (Component array).
 */
@HotCode
public final class SparseSet {

    // Header layout (40 bytes total)
    private static final long OFFSET_CAPACITY   = 0L;  // int
    private static final long OFFSET_MAX_ENTITY = 4L;  // int
    private static final long OFFSET_COUNT      = 8L;  // int
    private static final long OFFSET_STRIDE     = 12L; // int
    private static final long OFFSET_DENSE_PTR  = 16L; // long (Int array)
    private static final long OFFSET_SPARSE_PTR = 24L; // long (Int array)
    private static final long OFFSET_DATA_PTR   = 32L; // long (Raw memory)

    private SparseSet() {}

    @HotCode
    public static long allocate(int capacity, int maxEntities, int byteStride) {
        long ptr = ForeignMemory.allocateNative(40L);
        
        ForeignMemory.setInt(ptr + OFFSET_CAPACITY, capacity);
        ForeignMemory.setInt(ptr + OFFSET_MAX_ENTITY, maxEntities);
        ForeignMemory.setInt(ptr + OFFSET_COUNT, 0);
        ForeignMemory.setInt(ptr + OFFSET_STRIDE, byteStride);

        // Dense array: stores the Entity ID at the dense index
        long densePtr = Int.allocateArray(capacity);
        ForeignMemory.setLong(ptr + OFFSET_DENSE_PTR, densePtr);

        // Sparse array: stores the dense index for a given Entity ID
        long sparsePtr = Int.allocateArray(maxEntities);
        // Initialize sparse array with -1 to indicate missing
        for (int i = 0; i < maxEntities; i++) {
            Int.set(sparsePtr, i, -1);
        }
        ForeignMemory.setLong(ptr + OFFSET_SPARSE_PTR, sparsePtr);

        // Data array: contiguous buffer for the actual struct data
        if (byteStride > 0) {
            long dataPtr = ForeignMemory.allocateNative((long) capacity * byteStride);
            ForeignMemory.setLong(ptr + OFFSET_DATA_PTR, dataPtr);
        } else {
            ForeignMemory.setLong(ptr + OFFSET_DATA_PTR, 0L);
        }

        return ptr;
    }

    @HotCode
    public static void free(long ptr) {
        long densePtr = ForeignMemory.getLong(ptr + OFFSET_DENSE_PTR);
        long sparsePtr = ForeignMemory.getLong(ptr + OFFSET_SPARSE_PTR);
        long dataPtr = ForeignMemory.getLong(ptr + OFFSET_DATA_PTR);

        if (densePtr != 0L) Int.free(densePtr);
        if (sparsePtr != 0L) Int.free(sparsePtr);
        if (dataPtr != 0L) ForeignMemory.freeNative(dataPtr);

        ForeignMemory.freeNative(ptr);
    }

    @HotCode
    public static int count(long ptr) {
        return ForeignMemory.getInt(ptr + OFFSET_COUNT);
    }

    @HotCode
    public static boolean contains(long ptr, int entityId) {
        int max = ForeignMemory.getInt(ptr + OFFSET_MAX_ENTITY);
        if (entityId < 0 || entityId >= max) return false;

        long sparsePtr = ForeignMemory.getLong(ptr + OFFSET_SPARSE_PTR);
        return Int.get(sparsePtr, entityId) != -1;
    }

    /**
     * Adds an entity to the set and returns the memory pointer to its component data.
     * If it already exists, returns the existing data pointer.
     */
    @HotCode
    public static long add(long ptr, int entityId) {
        int max = ForeignMemory.getInt(ptr + OFFSET_MAX_ENTITY);
        if (entityId < 0 || entityId >= max) {
            throw new IndexOutOfBoundsException("Entity ID " + entityId + " exceeds max " + max);
        }

        long sparsePtr = ForeignMemory.getLong(ptr + OFFSET_SPARSE_PTR);
        int denseIndex = Int.get(sparsePtr, entityId);

        int stride = ForeignMemory.getInt(ptr + OFFSET_STRIDE);
        long dataPtr = ForeignMemory.getLong(ptr + OFFSET_DATA_PTR);

        if (denseIndex != -1) {
            // Already exists, just return the data pointer
            return stride > 0 ? dataPtr + ((long) denseIndex * stride) : 0L;
        }

        int count = ForeignMemory.getInt(ptr + OFFSET_COUNT);
        int cap = ForeignMemory.getInt(ptr + OFFSET_CAPACITY);
        if (count >= cap) {
            throw new IllegalStateException("SparseSet is full!");
        }

        long densePtr = ForeignMemory.getLong(ptr + OFFSET_DENSE_PTR);

        // dense[count] = entityId
        Int.set(densePtr, count, entityId);
        // sparse[entityId] = count
        Int.set(sparsePtr, entityId, count);
        
        ForeignMemory.setInt(ptr + OFFSET_COUNT, count + 1);

        return stride > 0 ? dataPtr + ((long) count * stride) : 0L;
    }

    @HotCode
    public static void remove(long ptr, int entityId) {
        if (!contains(ptr, entityId)) return;

        long sparsePtr = ForeignMemory.getLong(ptr + OFFSET_SPARSE_PTR);
        int denseIndexToRemove = Int.get(sparsePtr, entityId);

        int count = ForeignMemory.getInt(ptr + OFFSET_COUNT) - 1;
        long densePtr = ForeignMemory.getLong(ptr + OFFSET_DENSE_PTR);
        
        int lastEntityId = Int.get(densePtr, count);

        // Move the last element to the removed spot to keep the dense array contiguous
        if (denseIndexToRemove != count) {
            Int.set(densePtr, denseIndexToRemove, lastEntityId);
            Int.set(sparsePtr, lastEntityId, denseIndexToRemove);

            // Also swap the raw data bytes
            int stride = ForeignMemory.getInt(ptr + OFFSET_STRIDE);
            if (stride > 0) {
                long dataPtr = ForeignMemory.getLong(ptr + OFFSET_DATA_PTR);
                long destAddr = dataPtr + ((long) denseIndexToRemove * stride);
                long srcAddr = dataPtr + ((long) count * stride);
                ForeignMemory.copy(srcAddr, destAddr, stride);
            }
        }

        Int.set(sparsePtr, entityId, -1);
        ForeignMemory.setInt(ptr + OFFSET_COUNT, count);
    }

    /**
     * Gets the data pointer for the given entity.
     * Returns 0 if the entity does not have this component.
     */
    @HotCode
    public static long get(long ptr, int entityId) {
        long sparsePtr = ForeignMemory.getLong(ptr + OFFSET_SPARSE_PTR);
        int denseIndex = Int.get(sparsePtr, entityId);
        if (denseIndex == -1) return 0L;

        int stride = ForeignMemory.getInt(ptr + OFFSET_STRIDE);
        if (stride == 0) return ptr; // Just return a non-zero to indicate truth
        
        long dataPtr = ForeignMemory.getLong(ptr + OFFSET_DATA_PTR);
        return dataPtr + ((long) denseIndex * stride);
    }
    
    /**
     * Returns the memory pointer to the tightly packed dense array of Entity IDs.
     * Length of this array is count(ptr).
     */
    @HotCode
    public static long getDenseEntities(long ptr) {
        return ForeignMemory.getLong(ptr + OFFSET_DENSE_PTR);
    }
    
    /**
     * Returns the memory pointer to the tightly packed data array.
     */
    @HotCode
    public static long getDenseData(long ptr) {
        return ForeignMemory.getLong(ptr + OFFSET_DATA_PTR);
    }
}
