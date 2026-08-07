package io;

import annotation.Draft;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Buffered binary file writer used by {@link Log} on the writer thread.
 * Backed by a 64 KB BufferedOutputStream so records coalesce into large writes.
 */
@Draft
public class FileWriter {
    private BufferedOutputStream out;
    private boolean open;
    private long bytesWritten;

    public FileWriter() {
    }

    /** Creates/truncates the file at path. Returns true on success. */
    public boolean open(String path) {
        try {
            out = new BufferedOutputStream(
                new FileOutputStream(path, false), 1 << 16);
            open = true;
            bytesWritten = 0L;
            return true;
        } catch (IOException e) {
            open = false;
            return false;
        }
    }

    public void write(byte[] data, int off, int len) {
        if (!open || len <= 0) {
            return;
        }
        try {
            out.write(data, off, len);
            bytesWritten += len;
        } catch (IOException e) {
        }
    }

    public void flush() {
        if (!open) {
            return;
        }
        try {
            out.flush();
        } catch (IOException e) {
        }
    }

    public void close() {
        if (!open) {
            return;
        }
        try {
            out.flush();
            out.close();
        } catch (IOException e) {
        }
        open = false;
    }

    public long bytesWritten() {
        return bytesWritten;
    }
}
