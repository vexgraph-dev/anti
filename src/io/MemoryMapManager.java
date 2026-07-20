package io;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;

/**
 * Zero-copy Memory-Mapped File manager for direct virtual address block paging.
 */
@Draft
@Intention("File I/O mapping wrapper using MemorySegment.mapFile to allow direct, zero-copy structural loads from disk to native RAM.")
public final class MmapManager {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_MMAP_MANAGER;

    private MmapManager() {}

    public static int classId() {
        return CLASS_ID;
    }
}
