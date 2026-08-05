package net;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;
import struct.Map;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * Off-heap zero-GC non-blocking HTTP server operating on raw long memory pointer handles.
 */
@Draft
@Intention("Data-Oriented Design (DOD) off-heap non-blocking HTTP server dogfooding struct.Map for socket channels.")
@Volatile
public final class HTTPServer {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_HTTP_SERVER;
    public static final int TYPE_HTTP_SERVER = TypeRegister.HTTP_SERVER_SINGLETON;

    private static final long CHANNEL_MAP_PTR = Map.instant(TypeRegister.ID_LONG, TypeRegister.ID_VARIABLE, 64);
    private static final long REQUEST_CHANNEL_MAP_PTR = Map.instant(TypeRegister.ID_LONG, TypeRegister.ID_VARIABLE, 128);

    private HTTPServer() {}

    public static int classId() {
        return CLASS_ID;
    }

    /**
     * Allocates a new off-heap HTTPServer instance handle for the target port.
     */
    public static long invoke(int port) {
        long block = ForeignMemory.allocateNative(56);
        long userPtr = block + 8L;

        ForeignMemory.setInt(block, TYPE_HTTP_SERVER);
        ForeignMemory.setInt(block + 4L, 1);

        ForeignMemory.setInt(userPtr, 0);       // state: 0 = STOPPED, 1 = RUNNING
        ForeignMemory.setInt(userPtr + 4L, port); // port

        return userPtr;
    }

    public static boolean isRunning(long serverPtr) {
        if (serverPtr == 0L) return false;
        return ForeignMemory.getInt(serverPtr) == 1;
    }

    public static int getPort(long serverPtr) {
        if (serverPtr == 0L) return 0;
        return ForeignMemory.getInt(serverPtr + 4L);
    }

    /**
     * Starts listening on the server's configured port.
     */
    public static synchronized boolean start(long serverPtr) {
        if (serverPtr == 0L) return false;
        if (isRunning(serverPtr)) return true;

        int port = getPort(serverPtr);
        try {
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.configureBlocking(false);
            ssc.bind(new InetSocketAddress(port));
            Map.putObject(CHANNEL_MAP_PTR, serverPtr, ssc);
            ForeignMemory.setInt(serverPtr, 1); // Set state to RUNNING
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Non-blocking poll for incoming HTTP request.
     * Returns an off-heap request handle (reqPtr), or 0L if no request is pending.
     */
    public static long pollRequest(long serverPtr) {
        if (!isRunning(serverPtr)) return 0L;

        ServerSocketChannel ssc = (ServerSocketChannel) Map.getObject(CHANNEL_MAP_PTR, serverPtr);
        if (ssc == null) return 0L;

        try {
            SocketChannel sc = ssc.accept();
            if (sc == null) return 0L;

            sc.configureBlocking(false);
            ByteBuffer buf = ByteBuffer.allocateDirect(4096);
            int bytesRead = sc.read(buf);
            if (bytesRead <= 0) {
                sc.close();
                return 0L;
            }

            buf.flip();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            String rawReq = new String(bytes, StandardCharsets.UTF_8);

            String[] lines = rawReq.split("\r\n");
            if (lines.length == 0) {
                sc.close();
                return 0L;
            }

            String[] reqParts = lines[0].split(" ");
            String method = reqParts.length > 0 ? reqParts[0] : "GET";
            String uri = reqParts.length > 1 ? reqParts[1] : "/";

            long reqBlock = ForeignMemory.allocateNative(48);
            long reqPtr = reqBlock + 8L;

            long methodPtr = string.allocate(method);
            long uriPtr = string.allocate(uri);

            ForeignMemory.setInt(reqBlock, TypeRegister.FORM_SINGLETON | TypeRegister.ID_HTTP_SERVER);
            ForeignMemory.setInt(reqBlock + 4L, 1);

            ForeignMemory.setLong(reqPtr, methodPtr);
            ForeignMemory.setLong(reqPtr + 8L, uriPtr);
            ForeignMemory.setLong(reqPtr + 16L, serverPtr);

            Map.putObject(REQUEST_CHANNEL_MAP_PTR, reqPtr, sc);
            return reqPtr;
        } catch (IOException e) {
            return 0L;
        }
    }

    public static long getRequestMethod(long reqPtr) {
        if (reqPtr == 0L) return 0L;
        return ForeignMemory.getLong(reqPtr);
    }

    public static long getRequestUri(long reqPtr) {
        if (reqPtr == 0L) return 0L;
        return ForeignMemory.getLong(reqPtr + 8L);
    }

    /**
     * Sends an HTTP response back to the client and closes the request channel.
     */
    public static boolean sendResponse(long reqPtr, int statusCode, String responseBody) {
        if (reqPtr == 0L) return false;

        SocketChannel sc = (SocketChannel) Map.removeObject(REQUEST_CHANNEL_MAP_PTR, reqPtr);
        if (sc == null) return false;

        try {
            String body = responseBody != null ? responseBody : "";
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

            String statusText = (statusCode == 200) ? "OK" : (statusCode == 404) ? "Not Found" : "Internal Server Error";
            String httpHeader = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: " + bodyBytes.length + "\r\n" +
                    "Connection: close\r\n\r\n";

            ByteBuffer buf = ByteBuffer.allocateDirect(httpHeader.length() + bodyBytes.length);
            buf.put(httpHeader.getBytes(StandardCharsets.UTF_8));
            buf.put(bodyBytes);
            buf.flip();

            while (buf.hasRemaining()) {
                sc.write(buf);
            }
            sc.close();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            freeRequest(reqPtr);
        }
    }

    public static void freeRequest(long reqPtr) {
        if (reqPtr == 0L) return;
        long methodPtr = ForeignMemory.getLong(reqPtr);
        long uriPtr = ForeignMemory.getLong(reqPtr + 8L);

        if (methodPtr != 0L) string.free(methodPtr);
        if (uriPtr != 0L) string.free(uriPtr);

        long block = reqPtr - 8L;
        ForeignMemory.freeNative(block);
    }

    /**
     * Stops the HTTP server.
     */
    public static synchronized void stop(long serverPtr) {
        if (serverPtr == 0L) return;
        ForeignMemory.setInt(serverPtr, 0); // State = STOPPED

        ServerSocketChannel ssc = (ServerSocketChannel) Map.removeObject(CHANNEL_MAP_PTR, serverPtr);
        if (ssc != null) {
            try {
                ssc.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Stops the server and frees the off-heap native memory block.
     */
    public static void free(long serverPtr) {
        if (serverPtr == 0L) return;
        stop(serverPtr);

        long block = serverPtr - 8L;
        ForeignMemory.freeNative(block);
    }
}
