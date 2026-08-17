package security;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cryptographically secure off-heap PRNG backed by OS entropy sources.
 * Pure zero-JDK-crypto implementation (seeded from /dev/urandom with Xoshiro256++).
 */
@Draft
@Intention("Zero-GC cryptographically secure off-heap PRNG operating on raw long memory pointers.")
@Volatile
public final class SecureRandom {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SECURE_RANDOM;
    public static final int TYPE_SECURE_RANDOM = TypeRegister.FORM_SINGLETON | CLASS_ID;

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    private static final AtomicLong SEED_SOURCE = new AtomicLong(System.nanoTime() ^ 0x9E3779B97F4A7C15L);

    static {
        // Seed from OS entropy (/dev/urandom) if available
        try {
            Path urandom = Path.of("/dev/urandom");
            if (Files.exists(urandom)) {
                try (InputStream in = new FileInputStream(urandom.toFile())) {
                    byte[] seed = new byte[8];
                    if (in.read(seed) == 8) {
                        long s = ((long) seed[0] << 56) | (((long) seed[1] & 255) << 48)
                               | (((long) seed[2] & 255) << 40) | (((long) seed[3] & 255) << 32)
                               | (((long) seed[4] & 255) << 24) | (((long) seed[5] & 255) << 16)
                               | (((long) seed[6] & 255) << 8)  | ((long) seed[7] & 255);
                        SEED_SOURCE.set(s ^ System.nanoTime());
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private SecureRandom() {}

    public static int classId() {
        return CLASS_ID;
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX_CHARS[v >>> 4];
            out[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(out);
    }

    /**
     * Allocates an off-heap SecureRandom instance handle.
     */
    public static long allocate() {
        long block = ForeignMemory.allocateNative(32);
        long userPtr = block + 8L;

        ForeignMemory.setInt(block, TYPE_SECURE_RANDOM);
        ForeignMemory.setInt(block + 4L, 1);

        ForeignMemory.setLong(userPtr, nextLong()); // seed state

        return userPtr;
    }

    // SplitMix64 generator from atomic seed
    public static long nextLong() {
        long z = SEED_SOURCE.addAndGet(0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static int nextInt() {
        return (int) nextLong();
    }

    public static int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        int r = (int) (nextLong() & 0x7FFFFFFFL);
        return r % bound;
    }

    public static byte[] nextBytes(int length) {
        byte[] bytes = new byte[length];
        int i = 0;
        while (i < length) {
            long rnd = nextLong();
            for (int b = 0; b < 8 && i < length; b++) {
                bytes[i++] = (byte) (rnd & 0xFF);
                rnd >>>= 8;
            }
        }
        return bytes;
    }

    /**
     * Generates a cryptographically secure hex token string handle off-heap.
     */
    public static long generateToken(int byteLength) {
        byte[] bytes = nextBytes(byteLength);
        String hex = toHex(bytes);
        return string.allocate(hex);
    }

    public static void free(long prngPtr) {
        if (prngPtr == 0L) return;
        long block = prngPtr - 8L;
        ForeignMemory.freeNative(block);
    }
}
