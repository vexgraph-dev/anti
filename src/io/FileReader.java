package io;

import annotation.Draft;
import annotation.Intention;
import io.File;
import nio.ForeignMemory;

/**
 * Off-heap buffered file reader. Mirrors {@link java.io.FileInputStream} /
 * {@link java.io.BufferedReader} semantics as an anti-philosophy surface: the
 * whole file (or a page of it) is read straight into caller-owned native
 * memory via the ForeignMemory bridge — no heap byte[] ever exists on the data
 * path. The internal buffer is a native block owned by this reader.
 *
 *   FileReader r = FileReader.open("~/anti/logs/engine.bin");
 *   long buf = ...; // native buffer you own
 *   long n = r.read(buf, 4096);  // bytes land in native memory
 *   r.close();
 */
@Draft
@Intention("Off-heap file reader: reads land directly in native memory via the ForeignMemory bridge; a native internal buffer provides java.io-style buffered reads with zero heap allocation.")
public final class FileReader {

    private long filePtr;       // io.File handle
    private long buffer;        // native buffer block (owned)
    private long bufferLen;     // capacity of buffer
    private long bufFill;       // bytes currently buffered
    private long bufPos;        // read cursor within buffer

    private FileReader() {}

    public static FileReader open(String path, long bufferCapacity) {
        long filePtr = File.open(path, ForeignMemory.FILE_MODE_READ);
        if (filePtr == 0L) return null;
        long buffer = ForeignMemory.allocateNative(bufferCapacity);
        if (buffer == 0L) {
            File.close(filePtr);
            return null;
        }
        FileReader r = new FileReader();
        r.filePtr = filePtr;
        r.buffer = buffer;
        r.bufferLen = bufferCapacity;
        return r;
    }

    /** Reads the next chunk into native memory at dest. Returns bytes read, -1 on error, 0 at EOF. */
    public long read(long dest, long maxLen) {
        if (filePtr == 0L) return -1L;
        return File.read(filePtr, dest, maxLen);
    }

    /** Buffered read: refills the native buffer from the file and copies up to maxLen bytes to dest. */
    public long bufferedRead(long dest, long maxLen) {
        if (filePtr == 0L) return -1L;
        long available = bufFill - bufPos;
        if (available <= 0L) {
            long n = File.read(filePtr, buffer, bufferLen);
            if (n <= 0L) return n; // EOF (0) or error (-1)
            bufFill = n;
            bufPos = 0L;
            available = n;
        }
        long toCopy = Math.min(available, maxLen);
        ForeignMemory.copy(buffer + bufPos, dest, toCopy);
        bufPos += toCopy;
        return toCopy;
    }

    public boolean seek(long position) {
        if (filePtr == 0L) return false;
        bufFill = 0L;
        bufPos = 0L;
        return File.seek(filePtr, position);
    }

    public boolean skip(long bytes) {
        if (filePtr == 0L) return false;
        return File.seek(filePtr, File.pos(filePtr) + bytes);
    }

    public long position() {
        if (filePtr == 0L) return -1L;
        return File.pos(filePtr);
    }

    public long size() {
        if (filePtr == 0L) return -1L;
        return File.size(filePtr);
    }

    public boolean eof() {
        if (filePtr == 0L) return true;
        return File.eof(filePtr);
    }

    public void close() {
        if (buffer != 0L) {
            ForeignMemory.freeNative(buffer);
            buffer = 0L;
        }
        if (filePtr != 0L) {
            File.close(filePtr);
            filePtr = 0L;
        }
    }
}
