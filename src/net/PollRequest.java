package net;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;

@Draft
@Intention("Data-Oriented Design (DOD) off-heap network request poller layout manager over contiguous native strides")
public final class PollRequest {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_POLL_REQUEST;

    public static final int STRIDE_BYTES = 80;

    // Method Constants
    public static final int METHOD_GET    = 0;
    public static final int METHOD_POST   = 1;
    public static final int METHOD_PUT    = 2;
    public static final int METHOD_DELETE = 3;

    // Status Constants
    public static final int STATUS_IDLE      = 0;
    public static final int STATUS_PENDING   = 1;
    public static final int STATUS_IN_FLIGHT = 2;
    public static final int STATUS_SUCCESS   = 3;
    public static final int STATUS_FAILED    = 4;

    private PollRequest() {}

    // --- DOD BATCH ALLOCATION ---

    public static long allocateBatch(int count) {
        if (count <= 0) return 0L;
        long totalBytes = 8L + ((long) count * STRIDE_BYTES);
        long base = ForeignMemory.allocateNative(totalBytes);
        ForeignMemory.putInt(base, TypeRegister.POLL_REQUEST_ARRAY);
        ForeignMemory.putInt(base + 4L, count);

        long userPtr = base + 8L;
        ForeignMemory.setMemory(userPtr, (long) count * STRIDE_BYTES, (byte) 0);
        return userPtr;
    }

    public static int getCount(long batchPtr) {
        if (batchPtr == 0L) return 0;
        return ForeignMemory.getInt(batchPtr - 4L);
    }

    public static void freeBatch(long batchPtr) {
        if (batchPtr == 0L) return;
        int count = getCount(batchPtr);
        for (int i = 0; i < count; i++) {
            long slot = batchPtr + ((long) i * STRIDE_BYTES);
            long uriPtr = ForeignMemory.getLong(slot);
            long userPtr = ForeignMemory.getLong(slot + 8L);
            long passPtr = ForeignMemory.getLong(slot + 16L);
            long tokenPtr = ForeignMemory.getLong(slot + 24L);
            long payloadPtr = ForeignMemory.getLong(slot + 32L);
            long respPtr = ForeignMemory.getLong(slot + 40L);

            if (uriPtr != 0L) string.free(uriPtr);
            if (userPtr != 0L) string.free(userPtr);
            if (passPtr != 0L) string.free(passPtr);
            if (tokenPtr != 0L) string.free(tokenPtr);
            if (payloadPtr != 0L) string.free(payloadPtr);
            if (respPtr != 0L) string.free(respPtr);
        }

        ForeignMemory.freeNative(batchPtr - 8L);
    }

    // --- DOD STRIDE ATTRIBUTE SETTERS ---

    public static void set(long batchPtr, int index, String uri, int port, String username, String password) {
        if (batchPtr == 0L || index < 0 || index >= getCount(batchPtr)) return;
        long uriPtr = uri != null ? string.allocate(uri) : 0L;
        long userPtr = username != null ? string.allocate(username) : 0L;
        long passPtr = password != null ? string.allocate(password) : 0L;
        set(batchPtr, index, uriPtr, port, userPtr, passPtr);
    }

    public static void set(long batchPtr, int index, long uriPtr, int port, long userPtr, long passPtr) {
        if (batchPtr == 0L || index < 0 || index >= getCount(batchPtr)) return;
        long slot = batchPtr + ((long) index * STRIDE_BYTES);

        ForeignMemory.putLong(slot, uriPtr);
        ForeignMemory.putLong(slot + 8L, userPtr);
        ForeignMemory.putLong(slot + 16L, passPtr);
        ForeignMemory.putInt(slot + 48L, port);
        ForeignMemory.putInt(slot + 56L, STATUS_PENDING);
    }

    public static void setAuthToken(long batchPtr, int index, String token) {
        if (batchPtr == 0L || index < 0 || index >= getCount(batchPtr)) return;
        long tokenPtr = token != null ? string.allocate(token) : 0L;
        setAuthToken(batchPtr, index, tokenPtr);
    }

    public static void setAuthToken(long batchPtr, int index, long tokenPtr) {
        if (batchPtr == 0L || index < 0 || index >= getCount(batchPtr)) return;
        long slot = batchPtr + ((long) index * STRIDE_BYTES);
        ForeignMemory.putLong(slot + 24L, tokenPtr);
    }

    public static void setPayload(long batchPtr, int index, String payload) {
        if (batchPtr == 0L || index < 0 || index >= getCount(batchPtr)) return;
        long payloadPtr = payload != null ? string.allocate(payload) : 0L;
        setPayload(batchPtr, index, payloadPtr);
    }

    public static void setPayload(long batchPtr, int index, long payloadPtr) {
        if (batchPtr == 0L || index < 0 || index >= getCount(batchPtr)) return;
        long slot = batchPtr + ((long) index * STRIDE_BYTES);
        ForeignMemory.putLong(slot + 32L, payloadPtr);
        ForeignMemory.putInt(slot + 52L, METHOD_POST);
    }

    // --- DOD BATCH EXECUTION ENGINE ---

    public static void execute(long batchPtr, int index) {
        if (batchPtr == 0L || index < 0 || index >= getCount(batchPtr)) return;
        long slot = batchPtr + ((long) index * STRIDE_BYTES);

        long uriPtr = ForeignMemory.getLong(slot);
        long userPtr = ForeignMemory.getLong(slot + 8L);
        long passPtr = ForeignMemory.getLong(slot + 16L);
        long tokenPtr = ForeignMemory.getLong(slot + 24L);
        long payloadPtr = ForeignMemory.getLong(slot + 32L);
        int port = ForeignMemory.getInt(slot + 48L);
        int method = ForeignMemory.getInt(slot + 52L);

        if (uriPtr == 0L) {
            ForeignMemory.putInt(slot + 56L, STATUS_FAILED);
            return;
        }

        ForeignMemory.putInt(slot + 56L, STATUS_IN_FLIGHT);

        // Synthesize Auth Header if user/pass or token are set
        long authHeaderPtr = 0L;
        if (tokenPtr != 0L) {
            authHeaderPtr = TransportProtocol.createBearerAuth(tokenPtr);
        } else if (userPtr != 0L || passPtr != 0L) {
            authHeaderPtr = TransportProtocol.createBasicAuth(userPtr, passPtr);
        }

        String methodStr = (method == METHOD_POST) ? "POST" : (method == METHOD_PUT) ? "PUT" : (method == METHOD_DELETE) ? "DELETE" : "GET";
        String uriStr = string.get(uriPtr);
        String authHeaderStr = authHeaderPtr != 0L ? string.get(authHeaderPtr) : null;
        String payloadStr = payloadPtr != 0L ? string.get(payloadPtr) : null;

        long respBodyPtr = HTTPClient.request(methodStr, uriStr, authHeaderStr, payloadStr);
        int statusCode = HTTPClient.getStatus(uriPtr);

        if (authHeaderPtr != 0L) string.free(authHeaderPtr);

        // Store execution results directly back into off-heap stride
        long oldResp = ForeignMemory.getLong(slot + 40L);
        if (oldResp != 0L) string.free(oldResp);

        ForeignMemory.putLong(slot + 40L, respBodyPtr);
        ForeignMemory.putInt(slot + 60L, statusCode);

        if (respBodyPtr != 0L && statusCode < 400) {
            ForeignMemory.putInt(slot + 56L, STATUS_SUCCESS);
        } else {
            ForeignMemory.putInt(slot + 56L, STATUS_FAILED);
        }
    }

    public static void executeAll(long batchPtr) {
        if (batchPtr == 0L) return;
        int count = getCount(batchPtr);
        for (int i = 0; i < count; i++) {
            execute(batchPtr, i);
        }
    }

    // --- DOD STRIDE ATTRIBUTE GETTERS ---

    public static long getUri(long batchPtr, int index) {
        if (batchPtr == 0L || index < 0 || index >= getCount(batchPtr)) return 0L;
        return ForeignMemory.getLong(batchPtr + ((long) index * STRIDE_BYTES));
    }

    public static int getPort(long batchPtr, int index) {
        if (batchPtr == 0L || index < 0 || index >= getCount(batchPtr)) return 0;
        return ForeignMemory.getInt(batchPtr + ((long) index * STRIDE_BYTES) + 48L);
    }

    public static int getStatus(long batchPtr, int index) {
        if (batchPtr == 0L || index < 0 || index >= getCount(batchPtr)) return STATUS_IDLE;
        return ForeignMemory.getInt(batchPtr + ((long) index * STRIDE_BYTES) + 56L);
    }

    public static int getResponseCode(long batchPtr, int index) {
        if (batchPtr == 0L || index < 0 || index >= getCount(batchPtr)) return 0;
        return ForeignMemory.getInt(batchPtr + ((long) index * STRIDE_BYTES) + 60L);
    }

    public static long getResponse(long batchPtr, int index) {
        if (batchPtr == 0L || index < 0 || index >= getCount(batchPtr)) return 0L;
        return ForeignMemory.getLong(batchPtr + ((long) index * STRIDE_BYTES) + 40L);
    }
}
