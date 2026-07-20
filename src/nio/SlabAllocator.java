package nio;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;

/**
 * Off-heap Slab Allocator for high-frequency sub-allocation without system malloc overhead.
 */
@Draft
@Intention("High-performance off-heap Slab Allocator providing custom user-space dynamic memory blocks to bypass OS context switch downcalls.")
public final class SlabAllocator {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SLAB_ALLOCATOR;

    private SlabAllocator() {}

    public static int classId() {
        return CLASS_ID;
    }
}
