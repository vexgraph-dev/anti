package security;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;
import struct.Map;

/**
 * Server-Client handshake verifier, HMAC challenge-response checker, and rate limiter guard.
 */
@Draft
@Intention("Server-side connection security guard verifying client tokens, HMAC challenges, and IP rate limits.")
@Volatile
public final class ServerClientChecker {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SERVER_CLIENT_CHECKER;
    public static final int TYPE_SERVER_CLIENT_CHECKER = TypeRegister.FORM_SINGLETON | CLASS_ID;

    private static final long IP_RATE_MAP_PTR = Map.instant(TypeRegister.ID_LONG, TypeRegister.ID_LONG, 128);
    private static final int MAX_REQUESTS_PER_WINDOW = 100;
    private static final long WINDOW_MS = 1000L;

    private ServerClientChecker() {}

    public static int classId() {
        return CLASS_ID;
    }

    /**
     * Generates a random HMAC challenge string handle for server-client handshake.
     */
    public static long generateChallenge() {
        return SecureRandom.generateToken(16);
    }

    /**
     * Verifies if client response matches HMAC-SHA256(secretKey, challenge).
     */
    public static boolean verifyResponse(long secretKeyPtr, long challengePtr, long clientResponsePtr) {
        if (secretKeyPtr == 0L || challengePtr == 0L || clientResponsePtr == 0L) return false;

        long expectedHmacPtr = HashVariable.hmacSha256(secretKeyPtr, challengePtr);
        if (expectedHmacPtr == 0L) return false;

        String expected = string.get(expectedHmacPtr);
        String actual = string.get(clientResponsePtr);

        string.free(expectedHmacPtr);
        if (expected == null || actual == null) return false;

        return expected.equalsIgnoreCase(actual);
    }

    /**
     * Off-heap rate limiting check for connecting client IP / identifier.
     * Returns true if client is allowed, or false if rate limit exceeded.
     */
    public static synchronized boolean checkRateLimit(long ipHash) {
        if (ipHash == 0L) return true;

        long now = System.currentTimeMillis();
        long record = Map.getVolatile(IP_RATE_MAP_PTR, ipHash);

        if (record == 0L) {
            long newRecord = (now << 16) | 1L; // upper 48 bits = window start, lower 16 bits = req count
            Map.putVolatile(IP_RATE_MAP_PTR, ipHash, newRecord);
            return true;
        }

        long windowStart = record >>> 16;
        int reqCount = (int) (record & 0xFFFF);

        if (now - windowStart > WINDOW_MS) {
            long newRecord = (now << 16) | 1L;
            Map.putVolatile(IP_RATE_MAP_PTR, ipHash, newRecord);
            return true;
        }

        if (reqCount >= MAX_REQUESTS_PER_WINDOW) {
            return false; // Rate limit exceeded!
        }

        long newRecord = (windowStart << 16) | (reqCount + 1);
        Map.putVolatile(IP_RATE_MAP_PTR, ipHash, newRecord);
        return true;
    }
}
