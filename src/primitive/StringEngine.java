package primitive;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;

/**
 * Zero-allocation off-heap String Engine for formatting, splitting, and parsing.
 */
@Draft
@Intention("Core string formatting, concatenation, and manipulation engine acting directly on off-heap string pointers to achieve zero-GC footprint.")
public final class StringEngine {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_STRING_ENGINE;

    private StringEngine() {}

    public static int classId() {
        return CLASS_ID;
    }
}
