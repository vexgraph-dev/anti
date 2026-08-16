package bindings;

import annotation.Draft;
import annotation.Intention;
import io.File;
import nio.ForeignMemory;
import primitive.Arguments;
import primitive.string;

/**
 * I/O language-pack adapters. Every method is a uniform bound method:
 *
 * <pre>
 *   long fn(long argsPtr, long argCount)
 * </pre>
 *
 * Strings cross the boundary as off-heap pointers (see {@link primitive.string});
 * no heap String is ever created on the calling path.
 */
@Draft
@Intention("Uniform-ABI language-pack adapters over existing I/O primitives: long fn(long argsPtr, long argCount).")
public final class BindingsIO {

    private BindingsIO() {}

    /** Returns 1 if the file at the given string pointer exists, else 0. */
    public static long fileExists(long argsPtr, long argCount) {
        long namePtr = Arguments.getPointer(argsPtr, 0);
        if (namePtr == 0L) return 0L;
        return File.exists(string.get(namePtr)) ? 1L : 0L;
    }

    /** Opens the file at the given string pointer with the given mode flags; returns the file pointer. */
    public static long fileOpen(long argsPtr, long argCount) {
        long namePtr = Arguments.getPointer(argsPtr, 0);
        int mode = (int) Arguments.get(argsPtr, 1);
        if (namePtr == 0L) return 0L;
        return File.open(namePtr, mode);
    }

    /** Closes the file; returns 1 on success. */
    public static long fileClose(long argsPtr, long argCount) {
        long filePtr = Arguments.getPointer(argsPtr, 0);
        if (filePtr == 0L) return 0L;
        return File.close(filePtr) ? 1L : 0L;
    }

    /** Writes len bytes from src to the file; returns bytes written (or 0). */
    public static long fileWrite(long argsPtr, long argCount) {
        long filePtr = Arguments.getPointer(argsPtr, 0);
        long src = Arguments.getPointer(argsPtr, 1);
        long len = Arguments.get(argsPtr, 2);
        if (filePtr == 0L || src == 0L) return 0L;
        return File.write(filePtr, src, len);
    }

    /** Allocates a primitive.String from a heap String (bind-time only, not hot). */
    public static long stringAllocate(long argsPtr, long argCount) {
        long sPtr = Arguments.getPointer(argsPtr, 0);
        return string.allocate(string.get(sPtr));
    }

    /** Frees a primitive.String. */
    public static long stringFree(long argsPtr, long argCount) {
        long sPtr = Arguments.getPointer(argsPtr, 0);
        if (sPtr == 0L) return 0L;
        string.free(sPtr);
        return 1L;
    }

    /** Returns the length of a primitive.String. */
    public static long stringLength(long argsPtr, long argCount) {
        long sPtr = Arguments.getPointer(argsPtr, 0);
        return sPtr == 0L ? 0L : string.length(sPtr);
    }
}