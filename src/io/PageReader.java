package io;

import annotation.Draft;
import annotation.Intention;
import nio.ForeignMemory;

/**
 * Paged reader over a memory-mapped file.
 *
 * Mirrors java.nio's BufferedInputStream-as-a-paging-layer role: the file is
 * mapped once (zero copy), then reads are served directly out of native address
 * space page by page, with an explicit page window instead of OS read() calls.
 * No heap buffer exists anywhere on the data path.
 *
 *   PageReader r = PageReader.open("~/anti/projects/save.bin");
 *   long p = r.page(3);
 *   byte b = ForeignMemory.getByte(p + 12);
 *   r.close();
 */
@Draft
@Intention("Paged reader over a memory-mapped file: a map window addressed as fixed-size pages, served directly from native address space (zero-copy, zero-heap).")
public final class PageReader {

    private long baseAddr;      // mapped file base address
    private long mapSize;       // mapped bytes
    private String path;
    private boolean readOnly;

    private PageReader() {}

    public static PageReader open(String path) {
        return open(path, -1L, false);
    }

    /** Maps the whole file; pass size < 0 to read the file's real size first. */
    public static PageReader open(String path, long size, boolean readOnly) {
        long fileSize = size;
        if (fileSize < 0L) {
            long f = File.open(path, readOnly ? ForeignMemory.FILE_MODE_READ : (ForeignMemory.FILE_MODE_READ | ForeignMemory.FILE_MODE_WRITE));
            if (f == 0L) return null;
            fileSize = File.size(f);
            File.close(f);
        }
        long base = MemoryMapManager.map(path, 0L, fileSize, readOnly);
        if (base == 0L) return null;
        PageReader r = new PageReader();
        r.baseAddr = base;
        r.mapSize = fileSize;
        r.path = path;
        r.readOnly = readOnly;
        return r;
    }

    /** Returns the address of page i (base + i*PAGE_SIZE). Caller bounds-checks against pageCount(). */
    public long page(long pageIndex) {
        return MemoryMapManager.pageAddr(baseAddr, pageIndex);
    }

    /** Returns the address of byte `index` within the mapped file. */
    public long addr(long index) {
        return baseAddr + index;
    }

    public long base() {
        return baseAddr;
    }

    public long size() {
        return mapSize;
    }

    public long pageCount() {
        return MemoryMapManager.pageCount(mapSize);
    }

    public boolean eof(long index) {
        return index >= mapSize;
    }

    /** Reads `len` bytes starting at `index` into native memory at dest (may cross pages; data is contiguous in the map). */
    public long read(long index, long dest, long len) {
        if (index < 0L || index >= mapSize) return 0L;
        long available = mapSize - index;
        long n = Math.min(available, len);
        ForeignMemory.copy(baseAddr + index, dest, n);
        return n;
    }

    public void close() {
        if (baseAddr != 0L) {
            MemoryMapManager.unmap(baseAddr);
            baseAddr = 0L;
        }
    }
}
