package io;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;

/**
 * Off-heap file handle. A `long` pointer to a native struct whose layout is:
 *
 *   [ptr - 8]  type header   (FORM_SINGLETON | ID_FILE)
 *   [ptr - 4]  length        (1)
 *   [ptr + 0]  namePtr       (primitive.string pointer to the path, or 0)
 *   [ptr + 8]  handle        (ForeignMemory file-handle token, or 0)
 *   [ptr + 16] size          (bytes, cached)
 *   [ptr + 24] position      (current read/write cursor)
 *   [ptr + 32] mode          (ForeignMemory.FILE_MODE_* flags)
 *
 * Reads and writes move bytes straight between the file and caller-owned
 * native memory — the heap is never touched (see the ForeignMemory bridge).
 * This mirrors java.io.File + java.nio.file semantics as an anti-philosophy
 * pointer-based surface: open/read/write/seek/size/flush/close/exists/delete.
 */
@Draft
@Intention("Off-heap file handle replicating the java.io.File / java.nio surface: a long pointer to a [name|handle|size|position|mode] struct whose I/O crosses the ForeignMemory bridge (zero heap on the data path).")
public final class File {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_FILE;

    private static final int OFF_NAME = 0;
    private static final int OFF_HANDLE = 8;
    private static final int OFF_SIZE = 16;
    private static final int OFF_POS = 24;
    private static final int OFF_MODE = 32;
    private static final int STRIDE = 40;

    private File() {}

    public static int classId() {
        return CLASS_ID;
    }

    // ------------------------------------------------------------------
    // open / close
    // ------------------------------------------------------------------

    /** Opens (creating parent dirs if CREATE is set) and returns a file-handle pointer, or 0 on failure. */
    public static long open(String path, int mode) {
        if (path == null) return 0L;
        long handle = ForeignMemory.fileOpen(path, mode);
        if (handle == 0L) return 0L;

        long namePtr = string.allocate(path);
        long filePtr = ForeignMemory.allocateNative(STRIDE);
        ForeignMemory.setInt(filePtr - 8L, TypeRegister.FORM_SINGLETON | TypeRegister.ID_FILE);
        ForeignMemory.setInt(filePtr - 4L, 1);
        ForeignMemory.setLong(filePtr + OFF_NAME, namePtr);
        ForeignMemory.setLong(filePtr + OFF_HANDLE, handle);
        ForeignMemory.setLong(filePtr + OFF_SIZE, ForeignMemory.fileSize(handle));
        ForeignMemory.setLong(filePtr + OFF_POS, 0L);
        ForeignMemory.setInt(filePtr + OFF_MODE, mode);
        return filePtr;
    }

    /** Opens a file whose path is already an off-heap string pointer. */
    public static long open(long namePtr, int mode) {
        if (namePtr == 0L) return 0L;
        return open(string.get(namePtr), mode);
    }

    public static boolean close(long filePtr) {
        if (filePtr == 0L) return false;
        long handle = handle(filePtr);
        long namePtr = namePtr(filePtr);
        if (handle != 0L) {
            ForeignMemory.fileClose(handle);
        }
        if (namePtr != 0L) {
            string.free(namePtr);
        }
        ForeignMemory.freeNative(filePtr);
        return true;
    }

    // ------------------------------------------------------------------
    // I/O (off-heap data path)
    // ------------------------------------------------------------------

    /** Reads up to maxLen bytes into native memory at dest. Returns bytes read, or -1 on error. */
    public static long read(long filePtr, long dest, long maxLen) {
        if (filePtr == 0L) return -1L;
        long n = ForeignMemory.fileRead(handle(filePtr), dest, maxLen);
        if (n > 0L) {
            ForeignMemory.setLong(filePtr + OFF_POS, pos(filePtr) + n);
        }
        return n;
    }

    /** Writes len bytes from native memory at src. Returns bytes written, or -1 on error. */
    public static long write(long filePtr, long src, long len) {
        if (filePtr == 0L) return -1L;
        long n = ForeignMemory.fileWrite(handle(filePtr), src, len);
        if (n > 0L) {
            ForeignMemory.setLong(filePtr + OFF_POS, pos(filePtr) + n);
            long size = size(filePtr);
            long newPos = pos(filePtr);
            if (newPos > size) {
                ForeignMemory.setLong(filePtr + OFF_SIZE, newPos);
            }
        }
        return n;
    }

    public static boolean seek(long filePtr, long position) {
        if (filePtr == 0L) return false;
        if (ForeignMemory.fileSeek(handle(filePtr), position)) {
            ForeignMemory.setLong(filePtr + OFF_POS, position);
            return true;
        }
        return false;
    }

    /** Refreshes the cached size from the underlying file. */
    public static long refreshSize(long filePtr) {
        if (filePtr == 0L) return -1L;
        long s = ForeignMemory.fileSize(handle(filePtr));
        if (s >= 0L) {
            ForeignMemory.setLong(filePtr + OFF_SIZE, s);
        }
        return s;
    }

    public static boolean flush(long filePtr) {
        if (filePtr == 0L) return false;
        return ForeignMemory.fileFlush(handle(filePtr));
    }

    // ------------------------------------------------------------------
    // accessors
    // ------------------------------------------------------------------

    public static long handle(long filePtr) {
        return ForeignMemory.getLong(filePtr + OFF_HANDLE);
    }

    public static long namePtr(long filePtr) {
        return ForeignMemory.getLong(filePtr + OFF_NAME);
    }

    public static String name(long filePtr) {
        long np = namePtr(filePtr);
        return np != 0L ? string.get(np) : null;
    }

    public static long size(long filePtr) {
        return ForeignMemory.getLong(filePtr + OFF_SIZE);
    }

    public static long pos(long filePtr) {
        return ForeignMemory.getLong(filePtr + OFF_POS);
    }

    public static int mode(long filePtr) {
        return ForeignMemory.getInt(filePtr + OFF_MODE);
    }

    public static boolean eof(long filePtr) {
        return pos(filePtr) >= size(filePtr);
    }

    // ------------------------------------------------------------------
    // path-level static ops (folder creation, existence, delete)
    // ------------------------------------------------------------------

    /** Returns true if the path exists as a file or directory. */
    public static boolean exists(String path) {
        if (path == null) return false;
        try {
            return java.nio.file.Files.exists(java.nio.file.Path.of(path));
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isDirectory(String path) {
        if (path == null) return false;
        try {
            return java.nio.file.Files.isDirectory(java.nio.file.Path.of(path));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Creates all missing parent directories. Returns true when the directory exists afterwards. */
    public static boolean mkdirs(String path) {
        if (path == null) return false;
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(path));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean delete(String path) {
        if (path == null) return false;
        try {
            return java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(path));
        } catch (Throwable t) {
            return false;
        }
    }
}
