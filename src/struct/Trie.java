package struct;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import nio.MemoryRegistry;
import oop.TypeRegister;

import java.lang.foreign.Arena;

/**
 * Off-heap Trie (Prefix Tree) implementation for zero-GC autocomplete and symbol prefix searches.
 */
@Draft
@Intention("Zero-GC off-heap Trie node tree mapping lowercase English symbols to registered Variable IDs using a 216-byte fixed node layout.")
public final class Trie {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_TRIE;

    public static final int TYPE_TRIE = TypeRegister.FORM_POINTER | CLASS_ID; // 0xCC00001C

    private static final long NODE_SIZE = 216L; // 26 child pointers (208B) + variableId (4B) + padding (4B)
    private static final int CHAR_COUNT = 26;

    private static Arena poolArena;
    private static volatile boolean active;

    static {
        poolArena = Arena.ofShared();
        active = true;
        MemoryRegistry.register(Trie::freeAll);
    }

    private Trie() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Trie subsystem is not active!");
    }

    public static void freeAll() {
        if (active) {
            active = false;
            if (poolArena != null && poolArena.scope().isAlive()) {
                poolArena.close();
            }
        }
    }

    private static long allocateNode() {
        long node = ForeignMemory.allocateNative(NODE_SIZE);
        ForeignMemory.setMemory(node, NODE_SIZE, (byte) 0);
        ForeignMemory.putInt(node + 208L, -1); // initial variable ID = -1 (not a terminal node)
        return node;
    }

    // create a new off-heap trie root node
    public static long instant() {
        checkActive();
        long headerBlock = ForeignMemory.allocateNative(8L + NODE_SIZE);
        long rootPtr = headerBlock + 8L;

        ForeignMemory.putInt(headerBlock, TYPE_TRIE);
        ForeignMemory.putInt(headerBlock + 4L, 0); // node count or size

        ForeignMemory.setMemory(rootPtr, NODE_SIZE, (byte) 0);
        ForeignMemory.putInt(rootPtr + 208L, -1);

        return rootPtr;
    }

    private static int charIndex(byte b) {
        int val = b & 0xFF;
        if (val >= 65 && val <= 90) { // A-Z
            val += 32;
        }
        if (val >= 97 && val <= 122) { // a-z
            return val - 97;
        }
        return -1;
    }

    // insert name symbol into trie mapping to variable ID
    public static synchronized void insert(long rootPtr, byte[] nameBytes, int variableId) {
        if (rootPtr == 0L || nameBytes == null || nameBytes.length == 0) return;
        checkActive();

        long current = rootPtr;
        for (byte b : nameBytes) {
            int idx = charIndex(b);
            if (idx == -1) continue; // skip non-alphabetic characters

            long childOffset = current + ((long) idx * 8L);
            long child = ForeignMemory.getLong(childOffset);
            if (child == 0L) {
                child = allocateNode();
                ForeignMemory.putLong(childOffset, child);
            }
            current = child;
        }

        // set terminal node payload
        ForeignMemory.putInt(current + 208L, variableId);
    }

    // search exact symbol match in trie
    public static int search(long rootPtr, byte[] nameBytes) {
        if (rootPtr == 0L || nameBytes == null || nameBytes.length == 0) return -1;

        long current = rootPtr;
        for (byte b : nameBytes) {
            int idx = charIndex(b);
            if (idx == -1) return -1;

            long child = ForeignMemory.getLong(current + ((long) idx * 8L));
            if (child == 0L) return -1;
            current = child;
        }

        return ForeignMemory.getInt(current + 208L);
    }

    // search all variable IDs starting with prefix, appending them to output list pointer
    public static synchronized void searchPrefix(long rootPtr, byte[] prefixBytes, long outListPtr) {
        if (rootPtr == 0L || outListPtr == 0L) return;

        long current = rootPtr;
        if (prefixBytes != null && prefixBytes.length > 0) {
            for (byte b : prefixBytes) {
                int idx = charIndex(b);
                if (idx == -1) return;

                long child = ForeignMemory.getLong(current + ((long) idx * 8L));
                if (child == 0L) return;
                current = child;
            }
        }

        // collect all matching terminal IDs starting from current node
        collect(current, outListPtr);
    }

    private static void collect(long node, long outListPtr) {
        if (node == 0L) return;
        int varId = ForeignMemory.getInt(node + 208L);
        if (varId != -1) {
            List.add(outListPtr, varId);
        }
        for (int i = 0; i < CHAR_COUNT; i++) {
            long child = ForeignMemory.getLong(node + ((long) i * 8L));
            if (child != 0L) {
                collect(child, outListPtr);
            }
        }
    }

    // free recursively all nodes
    public static synchronized void free(long rootPtr) {
        if (rootPtr == 0L) return;
        freeNode(rootPtr);
        long headerBlock = rootPtr - 8L;
        ForeignMemory.freeNative(headerBlock);
    }

    private static void freeNode(long node) {
        for (int i = 0; i < CHAR_COUNT; i++) {
            long child = ForeignMemory.getLong(node + ((long) i * 8L));
            if (child != 0L) {
                freeNode(child);
            }
        }
        ForeignMemory.freeNative(node);
    }

    public static int classId() {
        return CLASS_ID;
    }
}
