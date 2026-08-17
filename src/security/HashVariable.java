package security;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Off-heap cryptographic hashing and message authentication engine.
 * Pure zero-dependency off-heap implementation (0 JDK crypto provider overhead).
 */
@Draft
@Intention("Zero-GC off-heap cryptographic hashing engine operating over primitive.string handles and native memory addresses.")
public final class HashVariable {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_HASH_VARIABLE;

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    // SHA-256 K constants
    private static final int[] K256 = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };

    // SHA-512 K constants
    private static final long[] K512 = {
        0x428a2f98d728ae22L, 0x7137449123ef65cdL, 0xb5c0fbcfec4d3b2fL, 0xe9b5dba58189dbbcL,
        0x3956c25bf348b538L, 0x59f111f1b605d019L, 0x923f82a4af194f9bL, 0xab1c5ed5da6d8118L,
        0xd807aa98a3030242L, 0x12835b0145706fbeL, 0x243185be4ee4b28cL, 0x550c7dc3d5ffb4e2L,
        0x72be5d74f27b896fL, 0x80deb1fe3b1696b1L, 0x9bdc06a725c71235L, 0xc19bf174cf692694L,
        0xe49b69c19ef14ad2L, 0xefbe4786384f25e3L, 0x0fc19dc68b8cd5b5L, 0x240ca1cc77ac9c65L,
        0x2de92c6f592b0275L, 0x4a7484aa6ea6e483L, 0x5cb0a9dcbd41fbd4L, 0x76f988da831153b5L,
        0x983e5152ee66dfabL, 0xa831c66d2db43210L, 0xb00327c898fb213fL, 0xbf597fc7beef0ee4L,
        0xc6e00bf33da88fc2L, 0xd5a79147930aa725L, 0x06ca6351e003826fL, 0x142929670a0e6e70L,
        0x27b70a8546d22ffcL, 0x2e1b21385c26c926L, 0x4d2c6dfc5ac42aedL, 0x53380d139d95b3dfL,
        0x650a73548baf63deL, 0x766a0abb3c77b2a8L, 0x81c2c92e47867871L, 0x92722c85129a73c9L,
        0xa2bfe8a14cf10364L, 0xa81a664bbc423001L, 0xc24b8b70d0f89791L, 0xc76c51a30654be30L,
        0xd192e819d6ef5218L, 0xd69906245565a910L, 0xf40e35855771202aL, 0x106aa07032bbd1b8L,
        0x19a4c116b8d2d0c8L, 0x1e376c085141ab53L, 0x2748774cdf8eeeb9L, 0x34b0bcb5e19b48a8L,
        0x391c0cb3c5c95a63L, 0x4ed8aa4ae3418acbL, 0x5b9cca4f7763e373L, 0x682e6ff3d6b2b8a3L,
        0x748f82ee5defb2fcL, 0x78a5636f43172f60L, 0x84c87814a1f0ab72L, 0x8cc702081a6439ecL,
        0x90befffa23631e28L, 0xa4506cebde82bde9L, 0xbef9a3f7b2c67915L, 0xc67178f2e372532bL,
        0xca273eceea26619cL, 0xd186b8c721c0c207L, 0xeada7dd6cde0eb1eL, 0xf57d4f7fee6ed178L,
        0x06f067aa72176fbaL, 0x0a637dc5a2c898a6L, 0x113f9804bef90daeL, 0x1b710b35131c471bL,
        0x28db77f523047d84L, 0x32caab7b40c72493L, 0x3c9ebe0a15c9bebcL, 0x431d67c49c100d4cL,
        0x4cc5d4becb3e42b6L, 0x597f299cfc657e2aL, 0x5fcb6fab3ad6faecL, 0x6c44198c4a475817L
    };

    private HashVariable() {}

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
     * Computes SHA-256 hash of off-heap string handle.
     * Returns off-heap primitive.string handle containing hex digest.
     */
    public static long sha256(long strPtr) {
        if (strPtr == 0L) return 0L;
        String val = string.get(strPtr);
        if (val == null) return 0L;
        return sha256(val);
    }

    public static long sha256(String data) {
        if (data == null) return 0L;
        byte[] hash = computeSha256(data.getBytes(StandardCharsets.UTF_8));
        return string.allocate(toHex(hash));
    }

    public static byte[] computeSha256(byte[] data) {
        int h0 = 0x6a09e667, h1 = 0xbb67ae85, h2 = 0x3c6ef372, h3 = 0xa54ff53a;
        int h4 = 0x510e527f, h5 = 0x9b05688c, h6 = 0x1f83d9ab, h7 = 0x5be0cd19;

        long bitLen = (long) data.length * 8L;
        int padLen = (data.length % 64 < 56) ? (56 - data.length % 64) : (120 - data.length % 64);
        byte[] padded = new byte[data.length + padLen + 8];
        System.arraycopy(data, 0, padded, 0, data.length);
        padded[data.length] = (byte) 0x80;
        for (int i = 0; i < 8; i++) {
            padded[padded.length - 1 - i] = (byte) (bitLen >>> (i * 8));
        }

        int[] w = new int[64];
        for (int chunk = 0; chunk < padded.length; chunk += 64) {
            for (int i = 0; i < 16; i++) {
                int off = chunk + i * 4;
                w[i] = ((padded[off] & 0xFF) << 24) | ((padded[off + 1] & 0xFF) << 16)
                     | ((padded[off + 2] & 0xFF) << 8) | (padded[off + 3] & 0xFF);
            }
            for (int i = 16; i < 64; i++) {
                int s0 = Integer.rotateRight(w[i - 15], 7) ^ Integer.rotateRight(w[i - 15], 18) ^ (w[i - 15] >>> 3);
                int s1 = Integer.rotateRight(w[i - 2], 17) ^ Integer.rotateRight(w[i - 2], 19) ^ (w[i - 2] >>> 10);
                w[i] = w[i - 16] + s0 + w[i - 7] + s1;
            }

            int a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, h = h7;
            for (int i = 0; i < 64; i++) {
                int s1 = Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25);
                int ch = (e & f) ^ ((~e) & g);
                int temp1 = h + s1 + ch + K256[i] + w[i];
                int s0 = Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22);
                int maj = (a & b) ^ (a & c) ^ (b & c);
                int temp2 = s0 + maj;

                h = g; g = f; f = e; e = d + temp1; d = c; c = b; b = a; a = temp1 + temp2;
            }

            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e; h5 += f; h6 += g; h7 += h;
        }

        byte[] out = new byte[32];
        for (int i = 0; i < 4; i++) {
            out[i]      = (byte) (h0 >>> (24 - i * 8));
            out[i + 4]  = (byte) (h1 >>> (24 - i * 8));
            out[i + 8]  = (byte) (h2 >>> (24 - i * 8));
            out[i + 12] = (byte) (h3 >>> (24 - i * 8));
            out[i + 16] = (byte) (h4 >>> (24 - i * 8));
            out[i + 20] = (byte) (h5 >>> (24 - i * 8));
            out[i + 24] = (byte) (h6 >>> (24 - i * 8));
            out[i + 28] = (byte) (h7 >>> (24 - i * 8));
        }
        return out;
    }

    /**
     * Computes SHA-512 hash of off-heap string handle.
     */
    public static long sha512(long strPtr) {
        if (strPtr == 0L) return 0L;
        String val = string.get(strPtr);
        if (val == null) return 0L;
        return sha512(val);
    }

    public static long sha512(String data) {
        if (data == null) return 0L;
        byte[] hash = computeSha512(data.getBytes(StandardCharsets.UTF_8));
        return string.allocate(toHex(hash));
    }

    public static byte[] computeSha512(byte[] data) {
        long h0 = 0x6a09e667f3bcc908L, h1 = 0xbb67ae8584caa73bL, h2 = 0x3c6ef372fe94f82bL, h3 = 0xa54ff53a5f1d36f1L;
        long h4 = 0x510e527fade682d1L, h5 = 0x9b05688c2b3e6c1fL, h6 = 0x1f83d9abfb41bd6bL, h7 = 0x5be0cd19137e2179L;

        long bitLen = (long) data.length * 8L;
        int padLen = (data.length % 128 < 112) ? (112 - data.length % 128) : (240 - data.length % 128);
        byte[] padded = new byte[data.length + padLen + 16];
        System.arraycopy(data, 0, padded, 0, data.length);
        padded[data.length] = (byte) 0x80;
        for (int i = 0; i < 8; i++) {
            padded[padded.length - 1 - i] = (byte) (bitLen >>> (i * 8));
        }

        long[] w = new long[80];
        for (int chunk = 0; chunk < padded.length; chunk += 128) {
            for (int i = 0; i < 16; i++) {
                int off = chunk + i * 8;
                w[i] = ((padded[off] & 0xFFL) << 56) | ((padded[off + 1] & 0xFFL) << 48)
                     | ((padded[off + 2] & 0xFFL) << 40) | ((padded[off + 3] & 0xFFL) << 32)
                     | ((padded[off + 4] & 0xFFL) << 24) | ((padded[off + 5] & 0xFFL) << 16)
                     | ((padded[off + 6] & 0xFFL) << 8) | (padded[off + 7] & 0xFFL);
            }
            for (int i = 16; i < 80; i++) {
                long s0 = Long.rotateRight(w[i - 15], 1) ^ Long.rotateRight(w[i - 15], 8) ^ (w[i - 15] >>> 7);
                long s1 = Long.rotateRight(w[i - 2], 19) ^ Long.rotateRight(w[i - 2], 61) ^ (w[i - 2] >>> 6);
                w[i] = w[i - 16] + s0 + w[i - 7] + s1;
            }

            long a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, h = h7;
            for (int i = 0; i < 80; i++) {
                long s1 = Long.rotateRight(e, 14) ^ Long.rotateRight(e, 18) ^ Long.rotateRight(e, 41);
                long ch = (e & f) ^ ((~e) & g);
                long temp1 = h + s1 + ch + K512[i] + w[i];
                long s0 = Long.rotateRight(a, 28) ^ Long.rotateRight(a, 34) ^ Long.rotateRight(a, 39);
                long maj = (a & b) ^ (a & c) ^ (b & c);
                long temp2 = s0 + maj;

                h = g; g = f; f = e; e = d + temp1; d = c; c = b; b = a; a = temp1 + temp2;
            }

            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e; h5 += f; h6 += g; h7 += h;
        }

        byte[] out = new byte[64];
        for (int i = 0; i < 8; i++) {
            out[i]      = (byte) (h0 >>> (56 - i * 8));
            out[i + 8]  = (byte) (h1 >>> (56 - i * 8));
            out[i + 16] = (byte) (h2 >>> (56 - i * 8));
            out[i + 24] = (byte) (h3 >>> (56 - i * 8));
            out[i + 32] = (byte) (h4 >>> (56 - i * 8));
            out[i + 40] = (byte) (h5 >>> (56 - i * 8));
            out[i + 48] = (byte) (h6 >>> (56 - i * 8));
            out[i + 56] = (byte) (h7 >>> (56 - i * 8));
        }
        return out;
    }

    /**
     * Computes HMAC-SHA256 signature of data handle using key handle.
     */
    public static long hmacSha256(long keyPtr, long dataPtr) {
        if (keyPtr == 0L || dataPtr == 0L) return 0L;
        String key = string.get(keyPtr);
        String data = string.get(dataPtr);
        if (key == null || data == null) return 0L;
        return hmacSha256(key, data);
    }

    public static long hmacSha256(String keyStr, String dataStr) {
        if (keyStr == null || dataStr == null) return 0L;
        byte[] keyBytes = keyStr.getBytes(StandardCharsets.UTF_8);
        byte[] dataBytes = dataStr.getBytes(StandardCharsets.UTF_8);

        byte[] k = new byte[64];
        if (keyBytes.length > 64) {
            byte[] hashedKey = computeSha256(keyBytes);
            System.arraycopy(hashedKey, 0, k, 0, hashedKey.length);
        } else {
            System.arraycopy(keyBytes, 0, k, 0, keyBytes.length);
        }

        byte[] ipad = new byte[64 + dataBytes.length];
        byte[] opad = new byte[64 + 32];
        for (int i = 0; i < 64; i++) {
            ipad[i] = (byte) (k[i] ^ 0x36);
            opad[i] = (byte) (k[i] ^ 0x5C);
        }
        System.arraycopy(dataBytes, 0, ipad, 64, dataBytes.length);
        byte[] innerHash = computeSha256(ipad);
        System.arraycopy(innerHash, 0, opad, 64, 32);
        byte[] hmac = computeSha256(opad);

        return string.allocate(toHex(hmac));
    }
}
