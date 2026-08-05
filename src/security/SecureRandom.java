package security;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;

import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Cryptographically secure off-heap PRNG backed by OS entropy sources.
 */
@Draft
@Intention("Zero-GC cryptographically secure off-heap PRNG operating on raw long memory pointers.")
@Volatile
public final class SecureRandom {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SECURE_RANDOM;
    public static final int TYPE_SECURE_RANDOM = TypeRegister.FORM_SINGLETON | CLASS_ID;

    private static final java.security.SecureRandom CS_RNG;

    static {
        java.security.SecureRandom rng;
        try {
            rng = java.security.SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            rng = new java.security.SecureRandom();
        }
        CS_RNG = rng;
    }

    private SecureRandom() {}

    public static int classId() {
        return CLASS_ID;
    }

    /**
     * Allocates an off-heap SecureRandom instance handle.
     */
    public static long allocate() {
        long block = ForeignMemory.allocateNative(32);
        long userPtr = block + 8L;

        ForeignMemory.setInt(block, TYPE_SECURE_RANDOM);
        ForeignMemory.setInt(block + 4L, 1);

        ForeignMemory.setLong(userPtr, CS_RNG.nextLong()); // seed state

        return userPtr;
    }

    public static long nextLong() {
        return CS_RNG.nextLong();
    }

    public static int nextInt() {
        return CS_RNG.nextInt();
    }

    public static int nextInt(int bound) {
        return CS_RNG.nextInt(bound);
    }

    public static byte[] nextBytes(int length) {
        byte[] bytes = new byte[length];
        CS_RNG.nextBytes(bytes);
        return bytes;
    }

    /**
     * Generates a cryptographically secure hex token string handle off-heap.
     */
    public static long generateToken(int byteLength) {
        byte[] bytes = nextBytes(byteLength);
        String hex = HexFormat.of().formatHex(bytes);
        return string.allocate(hex);
    }

    public static void free(long prngPtr) {
        if (prngPtr == 0L) return;
        long block = prngPtr - 8L;
        ForeignMemory.freeNative(block);
    }
}
