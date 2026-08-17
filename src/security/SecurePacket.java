package security;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import net.HTTPServer;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Off-heap encrypted and authenticated network packet framing engine.
 * Pure zero-JDK-crypto implementation (ChaCha20 stream cipher + HMAC-SHA256 framing).
 */
@Draft
@Intention("Zero-GC off-heap ChaCha20 encrypted packet layout with anti-replay timestamps and sequence IDs.")
@Volatile
public final class SecurePacket {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SECURE_PACKET;
    public static final int TYPE_SECURE_PACKET = TypeRegister.FORM_SINGLETON | CLASS_ID;

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 32; // HMAC-SHA256 authentication tag

    private SecurePacket() {}

    public static int classId() {
        return CLASS_ID;
    }

    /**
     * Encrypts an off-heap string payload into a SecurePacket handle.
     * Memory Layout:
     *   [userPtr - 8]: 32-bit packed Type ID
     *   [userPtr - 4]: 32-bit length (1)
     *   [userPtr + 0]: 64-bit sequenceId
     *   [userPtr + 8]: 64-bit timestampMillis
     *   [userPtr + 16]: 64-bit cipherTextPtr (primitive.string handle)
     */
    public static long encrypt(long secretKeyPtr, long payloadPtr, long sequenceId) {
        if (secretKeyPtr == 0L || payloadPtr == 0L) return 0L;

        String key = string.get(secretKeyPtr);
        String payload = string.get(payloadPtr);
        if (key == null || payload == null) return 0L;

        try {
            byte[] keyBytes = HashVariable.computeSha256(key.getBytes(StandardCharsets.UTF_8));
            byte[] nonce = SecureRandom.nextBytes(NONCE_BYTES);
            byte[] plainBytes = payload.getBytes(StandardCharsets.UTF_8);

            byte[] cipherBytes = processChaCha20(keyBytes, nonce, 1, plainBytes);

            // Auth tag: HMAC-SHA256 over (nonce || cipherBytes)
            byte[] toAuth = new byte[NONCE_BYTES + cipherBytes.length];
            System.arraycopy(nonce, 0, toAuth, 0, NONCE_BYTES);
            System.arraycopy(cipherBytes, 0, toAuth, NONCE_BYTES, cipherBytes.length);
            byte[] tag = computeHmac(keyBytes, toAuth);

            // Packet format: [12B nonce][32B tag][cipherBytes]
            byte[] packetBytes = new byte[NONCE_BYTES + TAG_BYTES + cipherBytes.length];
            System.arraycopy(nonce, 0, packetBytes, 0, NONCE_BYTES);
            System.arraycopy(tag, 0, packetBytes, NONCE_BYTES, TAG_BYTES);
            System.arraycopy(cipherBytes, 0, packetBytes, NONCE_BYTES + TAG_BYTES, cipherBytes.length);

            String encodedPacket = Base64.getEncoder().encodeToString(packetBytes);
            long cipherTextPtr = string.allocate(encodedPacket);

            long block = ForeignMemory.allocateNative(40);
            long userPtr = block + 8L;

            ForeignMemory.setInt(block, TYPE_SECURE_PACKET);
            ForeignMemory.setInt(block + 4L, 1);

            ForeignMemory.setLong(userPtr, sequenceId);
            ForeignMemory.setLong(userPtr + 8L, System.currentTimeMillis());
            ForeignMemory.setLong(userPtr + 16L, cipherTextPtr);

            return userPtr;
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * Decrypts a SecurePacket handle back to an off-heap payload string handle.
     */
    public static long decrypt(long secretKeyPtr, long packetPtr) {
        if (secretKeyPtr == 0L || packetPtr == 0L) return 0L;

        String key = string.get(secretKeyPtr);
        long cipherTextPtr = ForeignMemory.getLong(packetPtr + 16L);
        String encodedPacket = string.get(cipherTextPtr);
        if (key == null || encodedPacket == null) return 0L;

        try {
            byte[] keyBytes = HashVariable.computeSha256(key.getBytes(StandardCharsets.UTF_8));
            byte[] packetBytes = Base64.getDecoder().decode(encodedPacket);
            if (packetBytes.length < NONCE_BYTES + TAG_BYTES) return 0L;

            byte[] nonce = new byte[NONCE_BYTES];
            byte[] tag = new byte[TAG_BYTES];
            int cipherLen = packetBytes.length - NONCE_BYTES - TAG_BYTES;
            byte[] cipherBytes = new byte[cipherLen];

            System.arraycopy(packetBytes, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(packetBytes, NONCE_BYTES, tag, 0, TAG_BYTES);
            System.arraycopy(packetBytes, NONCE_BYTES + TAG_BYTES, cipherBytes, 0, cipherLen);

            // Verify auth tag
            byte[] toAuth = new byte[NONCE_BYTES + cipherBytes.length];
            System.arraycopy(nonce, 0, toAuth, 0, NONCE_BYTES);
            System.arraycopy(cipherBytes, 0, toAuth, NONCE_BYTES, cipherBytes.length);
            byte[] expectedTag = computeHmac(keyBytes, toAuth);

            int diff = 0;
            for (int i = 0; i < TAG_BYTES; i++) {
                diff |= (tag[i] ^ expectedTag[i]);
            }
            if (diff != 0) return 0L; // Authentication failed

            byte[] plainBytes = processChaCha20(keyBytes, nonce, 1, cipherBytes);
            String plainText = new String(plainBytes, StandardCharsets.UTF_8);

            return string.allocate(plainText);
        } catch (Exception e) {
            return 0L;
        }
    }

    public static long getSequenceId(long packetPtr) {
        return HTTPServer.getRequestMethod(packetPtr);
    }

    public static long getTimestamp(long packetPtr) {
        return HTTPServer.getRequestUri(packetPtr);
    }

    public static void free(long packetPtr) {
        if (packetPtr == 0L) return;
        long cipherTextPtr = ForeignMemory.getLong(packetPtr + 16L);
        if (cipherTextPtr != 0L) string.free(cipherTextPtr);

        long block = packetPtr - 8L;
        ForeignMemory.freeNative(block);
    }

    // --- PURE OFF-HEAP CRYPTO PRIMITIVES ---

    private static byte[] computeHmac(byte[] key, byte[] data) {
        byte[] k = new byte[64];
        if (key.length > 64) {
            byte[] hashed = HashVariable.computeSha256(key);
            System.arraycopy(hashed, 0, k, 0, hashed.length);
        } else {
            System.arraycopy(key, 0, k, 0, key.length);
        }
        byte[] ipad = new byte[64 + data.length];
        byte[] opad = new byte[64 + 32];
        for (int i = 0; i < 64; i++) {
            ipad[i] = (byte) (k[i] ^ 0x36);
            opad[i] = (byte) (k[i] ^ 0x5C);
        }
        System.arraycopy(data, 0, ipad, 64, data.length);
        byte[] inner = HashVariable.computeSha256(ipad);
        System.arraycopy(inner, 0, opad, 64, 32);
        return HashVariable.computeSha256(opad);
    }

    // RFC 8439 ChaCha20 stream cipher block generator
    private static byte[] processChaCha20(byte[] key32, byte[] nonce12, int counter, byte[] input) {
        byte[] out = new byte[input.length];
        int[] state = new int[16];
        int[] keyInts = new int[8];
        for (int i = 0; i < 8; i++) {
            keyInts[i] = ((key32[i * 4] & 0xFF)) | ((key32[i * 4 + 1] & 0xFF) << 8)
                       | ((key32[i * 4 + 2] & 0xFF) << 16) | ((key32[i * 4 + 3] & 0xFF) << 24);
        }
        int[] nonceInts = new int[3];
        for (int i = 0; i < 3; i++) {
            nonceInts[i] = ((nonce12[i * 4] & 0xFF)) | ((nonce12[i * 4 + 1] & 0xFF) << 8)
                         | ((nonce12[i * 4 + 2] & 0xFF) << 16) | ((nonce12[i * 4 + 3] & 0xFF) << 24);
        }

        int blockCount = 0;
        while (blockCount * 64 < input.length) {
            // Constants "expand 32-byte k"
            state[0] = 0x61707865; state[1] = 0x3320646e; state[2] = 0x79622d32; state[3] = 0x6b206574;
            System.arraycopy(keyInts, 0, state, 4, 8);
            state[12] = counter + blockCount;
            System.arraycopy(nonceInts, 0, state, 13, 3);

            int[] working = state.clone();
            for (int r = 0; r < 10; r++) {
                // Column round
                quarterRound(working, 0, 4, 8, 12);
                quarterRound(working, 1, 5, 9, 13);
                quarterRound(working, 2, 6, 10, 14);
                quarterRound(working, 3, 7, 11, 15);
                // Diagonal round
                quarterRound(working, 0, 5, 10, 15);
                quarterRound(working, 1, 6, 11, 12);
                quarterRound(working, 2, 7, 8, 13);
                quarterRound(working, 3, 4, 9, 14);
            }

            int off = blockCount * 64;
            int len = Math.min(64, input.length - off);
            for (int i = 0; i < len; i++) {
                int word = working[i / 4] + state[i / 4];
                byte keyByte = (byte) (word >>> ((i % 4) * 8));
                out[off + i] = (byte) (input[off + i] ^ keyByte);
            }
            blockCount++;
        }
        return out;
    }

    private static void quarterRound(int[] x, int a, int b, int c, int d) {
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] ^ x[a], 16);
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] ^ x[c], 12);
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] ^ x[a], 8);
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] ^ x[c], 7);
    }
}
