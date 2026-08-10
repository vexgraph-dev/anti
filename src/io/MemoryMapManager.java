package io;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

/**
 * Zero-copy memory-mapped file manager: the storage paging system.
 *
 * A file (or a region of it) is mapped into native address space via the
 * ForeignMemory bridge (FileChannel.map + Arena). The map is then addressed in
 * fixed-size pages, so callers read/write "on disk" simply by touching memory:
 *
 *   long map = MemoryMapManager.map("~/anti/projects/save.bin", 0, 4096 * 4);
 *   long page0 = MemoryMapManager.pageAddr(map, 0);   // == base address
 *   long page3 = MemoryMapManager.pageAddr(map, 3);   // base + 3*PAGE_SIZE
 *   MemoryMapManager.unmap(map);
 *
 * Pages are handed out as raw addresses that ForeignMemory accessors can use
 * directly. The OS pages the file in/out — that IS the paging system; no heap
 * buffer is ever involved.
 */
@Draft
@Intention("Storage paging system over memory-mapped files: MemorySegment/FileChannel.map into native address space, addressed in fixed-size pages handed out as raw pointers. Zero-copy, zero-heap data path (see MemoryMapManager @Intention).")
public final class MemoryMapManager {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_MEMORY_MAP_MANAGER;

    /** Default page size for pageAddr(). 4 KiB, the OS page size on macOS/Linux. */
    public static final long PAGE_SIZE = 4096L;

    private MemoryMapManager() {}

    public static int classId() {
        return CLASS_ID;
    }

    /** Maps [offset, offset+size) of the file read/write. Returns the map base address, or 0 on failure. */
    public static long map(String path, long offset, long size) {
        return map(path, offset, size, false);
    }

    /** Maps [offset, offset+size) of the file. Returns the map base address, or 0 on failure. */
    public static long map(String path, long offset, long size, boolean readOnly) {
        return ForeignMemory.mapFile(path, offset, size, readOnly);
    }

    /** Unmaps a previously mapped region, flushing dirty pages back to the file. */
    public static boolean unmap(long baseAddress) {
        return ForeignMemory.unmapFile(baseAddress);
    }

    /** Returns the address of page index i (base + i*PAGE_SIZE). Caller must keep i within the mapped size. */
    public static long pageAddr(long baseAddress, long pageIndex) {
        return baseAddress + pageIndex * PAGE_SIZE;
    }

    /** Number of pages of PAGE_SIZE that cover `bytes`. */
    public static long pageCount(long bytes) {
        return (bytes + PAGE_SIZE - 1L) / PAGE_SIZE;
    }

    public static long alignUp(long value, long alignment) {
        return (value + alignment - 1L) & ~(alignment - 1L);
    }
}
