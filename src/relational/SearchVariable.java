package relational;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;
import primitive.Long;
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

    private static boolean isValidChar(byte b) {
        int val = b & 0xFF;
        if (val >= 97 && val <= 122) return true; // a-z
        if (val >= 65 && val <= 90) return true;  // A-Z
        if (val >= 48 && val <= 57) return true;  // 0-9
        if (val == 95) return true;               // _
        if (val == 36) return true;               // $
        return false;
    }

    private static boolean validateName(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 32) {
            return false;
        }
        for (byte b : bytes) {
            if (!isValidChar(b)) {
                return false;
            }
        }
        return true;
    }

    // register a variable name mapping to its ID in the search trie
    public static synchronized void insert(String name, int variableId) {
        if (name == null) return;
        insert(name.getBytes(StandardCharsets.UTF_8), variableId);
    }

    // register a variable name bytes mapping to its ID in the search trie
    public static synchronized void insert(byte[] nameBytes, int variableId) {
        checkActive();
        if (!validateName(nameBytes)) {
            throw new IllegalArgumentException("Invalid variable name. Only abcdefghijklmnopqrstuvwxyz1234567890_$ and length <= 32 allowed.");
        }
        Trie.insert(trieRoot, nameBytes, variableId);
    }

    private static boolean isExactMatch(String name, byte[] expectedBytes) {
        if (name == null) return false;
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length != expectedBytes.length) return false;
        for (int i = 0; i < expectedBytes.length; i++) {
            byte n = nameBytes[i];
            byte e = expectedBytes[i];
            if (e >= 65 && e <= 90) e += 32; // normalize prefix/expected to lowercase
            if (n != e) return false;
        }
        return true;
    }

    private static boolean startsWithPrefix(String name, byte[] prefixBytes) {
        if (name == null) return false;
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length < prefixBytes.length) return false;
        for (int i = 0; i < prefixBytes.length; i++) {
            byte n = nameBytes[i];
            byte p = prefixBytes[i];
            if (p >= 65 && p <= 90) p += 32; // normalize prefix to lowercase
            if (n != p) return false;
        }
        return true;
    }

    // search exact variable ID by symbol name
    public static int search(String name) {
        if (name == null) return -1;
        return search(name.getBytes(StandardCharsets.UTF_8));
    }

    // search exact variable ID by symbol name bytes
    public static int search(byte[] nameBytes) {
        checkActive();
        int varId = Trie.search(trieRoot, nameBytes);
        if (varId != -1) {
            String varName = relational.Variable.getName(varId);
            if (isExactMatch(varName, nameBytes)) {
                return varId;
            }
        }
        return -1;
    }

    // query a prefix and return an off-heap struct.List pointer containing matching variable IDs
    public static long autocomplete(String prefix) {
        if (prefix == null) return 0L;
        return autocomplete(prefix.getBytes(StandardCharsets.UTF_8));
    }

    // query a prefix bytes and return an off-heap struct.List pointer containing matching variable IDs
    public static long autocomplete(byte[] prefixBytes) {
        checkActive();
        if (prefixBytes == null || prefixBytes.length > 32) {
            return 0L;
        }
        for (byte b : prefixBytes) {
            if (!isValidChar(b)) return 0L;
        }
        long listPtr = struct.List.instant(TypeRegister.ID_INT);
        Trie.searchPrefix(trieRoot, prefixBytes, listPtr);

        // Filter out false positives caused by Trie index mapping collisions (e.g. digit folding)
        long filteredList = struct.List.instant(TypeRegister.ID_INT);
        int count = struct.List.size(listPtr);
        for (int i = 0; i < count; i++) {
            int varId = (int) struct.List.get(listPtr, i);
            String varName = relational.Variable.getName(varId);
            if (startsWithPrefix(varName, prefixBytes)) {
                struct.List.add(filteredList, varId);
            }
        }
        struct.List.free(listPtr);
        return filteredList;
    }

    // search matching variable pointers by prefix String returning on-heap long[]
    public static long[] searchPointers(String prefix) {
        if (prefix == null) return new long[0];
        return searchPointers(prefix.getBytes(StandardCharsets.UTF_8));
    }

    // search matching variable pointers by prefix char[] returning on-heap long[]
    public static long[] searchPointers(char[] prefix) {
        if (prefix == null) return new long[0];
        return searchPointers(new String(prefix).getBytes(StandardCharsets.UTF_8));
    }

    // search matching variable pointers by prefix bytes returning on-heap long[]
    public static long[] searchPointers(byte[] prefixBytes) {
        checkActive();
        if (prefixBytes == null || prefixBytes.length == 0 || prefixBytes.length > 32) {
            return new long[0];
        }
        for (byte b : prefixBytes) {
            if (!isValidChar(b)) {
                return new long[0];
            }
        }

        long listPtr = autocomplete(prefixBytes);
        int count = struct.List.size(listPtr);
        if (count == 0) {
            struct.List.free(listPtr);
            return new long[0];
        }

        // Allocate off-heap Long array for pointer buffering
        long tempArrayPtr = Long.allocateArray(count);
        for (int i = 0; i < count; i++) {
            int varId = (int) struct.List.get(listPtr, i);
            long pointer = relational.Variable.getPointer(varId);
            Long.set(tempArrayPtr, i, pointer);
        }

        // Copy to JVM on-heap array
        long[] result = new long[count];
        for (int i = 0; i < count; i++) {
            result[i] = Long.get(tempArrayPtr, i);
        }

        // Free off-heap temporary arrays and lists immediately
        Long.free(tempArrayPtr);
        struct.List.free(listPtr);

        return result;
    }

    public static int classId() {
        return CLASS_ID;
    }
}
