package security;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Off-heap cryptographic hashing and message authentication engine.
 */
@Draft
@Intention("Zero-GC off-heap cryptographic hashing engine operating over primitive.string handles and native memory addresses.")
public final class HashVariable {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_HASH_VARIABLE;

    private HashVariable() {}

    public static int classId() {
        return CLASS_ID;
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
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash);
            return string.allocate(hex);
        } catch (Exception e) {
            return 0L;
        }
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
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash);
            return string.allocate(hex);
        } catch (Exception e) {
            return 0L;
        }
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
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(keyStr.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(dataStr.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hmacBytes);
            return string.allocate(hex);
        } catch (Exception e) {
            return 0L;
        }
    }
}
