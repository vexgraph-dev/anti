package thread;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;

/**
 * Lock-free off-heap Ring Buffer queue for inter-thread message dispatching.
 */
@Draft
@Intention("High-throughput SPSC/MPMC ring buffer coordinating draw, physics, and networking threads with zero allocation.")
public final class RingBuffer {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_RING_BUFFER;

    private RingBuffer() {}

    public static int classId() {
        return CLASS_ID;
    }
}
