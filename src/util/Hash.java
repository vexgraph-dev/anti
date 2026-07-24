package util;

import annotation.Draft;
import annotation.Required;
import annotation.HotCode;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.nio.charset.StandardCharsets;

/**
 * Draft utility for fast 64-bit hashing (MurmurHash3 & FNV-1a).
 */
@Draft
public final class Hash {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_HASH;

    private Hash() {}

    public static int classId() {
        return CLASS_ID;
    }

    // fnv1a 64-bit hash for byte array
    @Draft
    public static long fnv1a64(byte[] data) {
        if (data == null) return 0L;
        long hash = 0xcbf29ce484222325L;
        for (byte b : data) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    // fnv1a 64-bit hash for string
    @Draft
    public static long fnv1a64(String str) {
        if (str == null) return 0L;
        return fnv1a64(str.getBytes(StandardCharsets.UTF_8));
    }

    // fnv1a 64-bit hash for off-heap memory block
    @HotCode
    public static long fnv1a64(long pointer, long length) {
        if (pointer == 0L || length <= 0) return 0L;
        long hash = 0xcbf29ce484222325L;
        for (long i = 0; i < length; i++) {
            hash ^= (ForeignMemory.getByte(pointer + i) & 0xff);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    // smart generic hashCode for any off-heap object/array pointer
    @HotCode
    public static long hashCode(long pointer) {
        if (pointer == 0L) return 0L;
        int type = ForeignMemory.getInt(pointer - 8L);
        int length = ForeignMemory.getInt(pointer - 4L);

        int form = type & TypeRegister.MASK_FORM;
        int classId = type & TypeRegister.MASK_CLASS;

        long totalBytes = 0;
        if (form == TypeRegister.FORM_SINGLETON) {
            totalBytes = oop.Stride.get(classId);
        } else if (form == TypeRegister.FORM_ARRAY) {
            totalBytes = (long) length * oop.Stride.get(classId);
        } else if (form == TypeRegister.FORM_POINTER) {
            totalBytes = (long) length * 8L;
        }

        return fnv1a64(pointer, totalBytes);
    }

    // murmurhash3 64-bit finalizer mix
    @Draft
    public static long murmur3Mix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

    // murmurhash3 32-bit finalizer mix
    @Draft
    public static int murmur3Mix32(int k) {
        k ^= k >>> 16;
        k *= 0x85ebca6b;
        k ^= k >>> 13;
        k *= 0xc2b2ae35;
        k ^= k >>> 16;
        return k;
    }
}
