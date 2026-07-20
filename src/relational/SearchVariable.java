package relational;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.MemoryRegistry;
import oop.TypeRegister;
import struct.Trie;

import java.nio.charset.StandardCharsets;

/**
 * Symbol Search and Autocomplete Index using an off-heap Trie structure.
 */
@Draft
@Intention("High-speed prefix lookup and variable autocomplete search index wrapper referencing an off-heap struct.Trie.")
public final class SearchVariable {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SEARCH_VARIABLE;

    private static final long trieRoot;
    private static volatile boolean active;

    static {
        trieRoot = Trie.instant();
        active = true;
        MemoryRegistry.register(SearchVariable::freeAll);
    }

    private SearchVariable() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("SearchVariable subsystem is not active!");
    }

    public static void freeAll() {
        if (active) {
            active = false;
            Trie.free(trieRoot);
        }
    }

    // register a variable name mapping to its ID in the search trie
    public static synchronized void insert(String name, int variableId) {
        if (name == null) return;
        insert(name.getBytes(StandardCharsets.UTF_8), variableId);
    }

    // register a variable name bytes mapping to its ID in the search trie
    public static synchronized void insert(byte[] nameBytes, int variableId) {
        checkActive();
        Trie.insert(trieRoot, nameBytes, variableId);
    }

    // search exact variable ID by symbol name
    public static int search(String name) {
        if (name == null) return -1;
        return search(name.getBytes(StandardCharsets.UTF_8));
    }

    // search exact variable ID by symbol name bytes
    public static int search(byte[] nameBytes) {
        checkActive();
        return Trie.search(trieRoot, nameBytes);
    }

    // query a prefix and return an off-heap struct.List pointer containing matching variable IDs
    public static long autocomplete(String prefix) {
        if (prefix == null) return 0L;
        return autocomplete(prefix.getBytes(StandardCharsets.UTF_8));
    }

    // query a prefix bytes and return an off-heap struct.List pointer containing matching variable IDs
    public static long autocomplete(byte[] prefixBytes) {
        checkActive();
        long listPtr = struct.List.instant(TypeRegister.ID_INT32);
        Trie.searchPrefix(trieRoot, prefixBytes, listPtr);
        return listPtr;
    }

    public static int classId() {
        return CLASS_ID;
    }
}
