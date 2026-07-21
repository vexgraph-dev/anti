package struct;

import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

/**
 * Universal off-heap collection metadata accessor utility class.
 */
@Intention("Zero-GC off-heap collection metadata accessors utilizing compact ternary raw memory dereferencing.")
public final class Collection {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_CLASS;

    private Collection() {}

    public static int type(long ptr) {
        return ptr == 0L ? 0 : ForeignMemory.getInt(ptr - 8L);
    }

    public static int size(long ptr) {
        return ptr == 0L ? 0 : ForeignMemory.getInt(ptr - 4L);
    }

    public static int length(long ptr) {
        return size(ptr);
    }

    public static boolean isEmpty(long ptr) {
        return size(ptr) == 0;
    }

    public static int elementClassId(long ptr) {
        return ptr == 0L ? 0 : ForeignMemory.getInt(ptr);
    }

    public static int keyClassId(long ptr) {
        return elementClassId(ptr);
    }

    public static int stride(long ptr) {
        return ptr == 0L ? 0 : ForeignMemory.getInt(ptr + 4L);
    }

    public static int valClassId(long ptr) {
        return stride(ptr);
    }

    public static int capacity(long ptr) {
        return ptr == 0L ? 0 : ForeignMemory.getInt(ptr + 8L);
    }

    public static long dataBuffer(long ptr) {
        return ptr == 0L ? 0L : ForeignMemory.getLong(ptr + 16L);
    }

    public static int classId() {
        return CLASS_ID;
    }
}
