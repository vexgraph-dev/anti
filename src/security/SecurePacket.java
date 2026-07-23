package security;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import net.HTTPServer;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Off-heap encrypted and authenticated network packet framing engine.
 */
@Draft
@Intention("Zero-GC off-heap AES-GCM encrypted packet layout with anti-replay timestamps and sequence IDs.")
@Volatile
public final class SecurePacket {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SECURE_PACKET;
    public static final int TYPE_SECURE_PACKET = TypeRegister.FORM_SINGLETON | CLASS_ID;

    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

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
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(key.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            byte[] iv = SecureRandom.nextBytes(IV_LENGTH_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] cipherBytes = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] packetBytes = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, packetBytes, 0, iv.length);
            System.arraycopy(cipherBytes, 0, packetBytes, iv.length, cipherBytes.length);

            String encodedPacket = Base64.getEncoder().encodeToString(packetBytes);
            long cipherTextPtr = string.allocate(encodedPacket);

            long block = ForeignMemory.allocateNative(40);
            long userPtr = block + 8L;

            ForeignMemory.putInt(block, TYPE_SECURE_PACKET);
            ForeignMemory.putInt(block + 4L, 1);

            ForeignMemory.putLong(userPtr, sequenceId);
            ForeignMemory.putLong(userPtr + 8L, System.currentTimeMillis());
            ForeignMemory.putLong(userPtr + 16L, cipherTextPtr);

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
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(key.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            byte[] packetBytes = Base64.getDecoder().decode(encodedPacket);
            if (packetBytes.length < IV_LENGTH_BYTES) return 0L;

            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] cipherBytes = new byte[packetBytes.length - IV_LENGTH_BYTES];
            System.arraycopy(packetBytes, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(packetBytes, IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] plainBytes = cipher.doFinal(cipherBytes);
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
}
