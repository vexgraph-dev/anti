package thread;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;

/**
 * Off-heap Atomic Spinlock based on VarHandle CAS memory updates.
 */
@Draft
@Intention("Ultra-low-latency spinlock acting directly on memory address flags to coordinate multi-threaded access without thread parking.")
public final class SpinLock {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SPIN_LOCK;

    private SpinLock() {}

    public static int classId() {
        return CLASS_ID;
    }
}
