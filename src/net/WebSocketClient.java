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
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;

import nio.StringLookup;
/**
 * Off-heap zero-GC WebSocket client operating on raw long memory pointer handles.
 */
@Draft
@Intention("Data-Oriented Design (DOD) off-heap WebSocket client dogfooding struct.Map for channel mapping.")
@Volatile
public final class WebSocketClient {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_WEBSOCKET_CLIENT;
    public static final int TYPE_WEBSOCKET_CLIENT = TypeRegister.WEBSOCKET_CLIENT_SINGLETON;

    private static final long CHANNEL_MAP_PTR = Map.instant(TypeRegister.ID_LONG, TypeRegister.ID_VARIABLE, 64);
    private static final Random RNG = new Random();

    private WebSocketClient() {}

    public static int classId() {
        return CLASS_ID;
    }

    /**
     * Allocates a new off-heap WebSocket client handle for given URI.
     */
    public static long invoke(String uriStr) {
        if (uriStr == null || uriStr.isEmpty()) return 0L;

        long block = ForeignMemory.allocateNative(56);
        long userPtr = block + 8L;

        ForeignMemory.setInt(block, TYPE_WEBSOCKET_CLIENT);
        ForeignMemory.setInt(block + 4L, 1);

        long uriPtr = string.allocate(uriStr);

        ForeignMemory.setInt(userPtr, 0);          // state: 0 = DISCONNECTED, 1 = CONNECTED
        ForeignMemory.setLong(userPtr + 8L, uriPtr); // uriPtr

        return userPtr;
    }

    public static boolean isConnected(long wsPtr) {
        if (wsPtr == 0L) return false;
        return ForeignMemory.getInt(wsPtr) == 1;
    }

    public static long getUri(long wsPtr) {
        if (wsPtr == 0L) return 0L;
        return ForeignMemory.getLong(wsPtr + 8L);
    }

    /**
     * Performs WebSocket handshake upgrade (101 Switching Protocols).
     */
    public static synchronized boolean connect(long wsPtr) {
        if (wsPtr == 0L) return false;
        if (isConnected(wsPtr)) return true;

        long uriPtr = getUri(wsPtr);
        String uriStr = string.get(uriPtr);
        if (uriStr == null) return false;

        try {
            URI uri = URI.create(uriStr);
            String host = uri.getHost() != null ? uri.getHost() : StringLookup.getJavaString(39);
            int port = uri.getPort() > 0 ? uri.getPort() : (StringLookup.getJavaString(46).equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
            String path = (uri.getPath() != null && !uri.getPath().isEmpty()) ? uri.getPath() : StringLookup.getJavaString(40);

            SocketChannel sc = SocketChannel.open();
            sc.connect(new InetSocketAddress(host, port));
            sc.configureBlocking(true);

            byte[] keyBytes = new byte[16];
            RNG.nextBytes(keyBytes);
            String secKey = Base64.getEncoder().encodeToString(keyBytes);

            String handshakeReq = StringLookup.getJavaString(47) + path + StringLookup.getJavaString(48) +
                    StringLookup.getJavaString(49) + host + StringLookup.getJavaString(43) + port + StringLookup.getJavaString(50) +
                    StringLookup.getJavaString(51) +
                    StringLookup.getJavaString(52) +
                    StringLookup.getJavaString(53) + secKey + StringLookup.getJavaString(50) +
                    StringLookup.getJavaString(54);

            sc.write(ByteBuffer.wrap(handshakeReq.getBytes(StandardCharsets.UTF_8)));

            ByteBuffer respBuf = ByteBuffer.allocateDirect(1024);
            int bytesRead = sc.read(respBuf);
            if (bytesRead <= 0) {
                sc.close();
                return false;
            }

            respBuf.flip();
            byte[] respBytes = new byte[respBuf.remaining()];
            respBuf.get(respBytes);
            String handshakeResp = new String(respBytes, StandardCharsets.UTF_8);

            if (handshakeResp.contains(StringLookup.getJavaString(55))) {
                sc.configureBlocking(false);
                Map.putObject(CHANNEL_MAP_PTR, wsPtr, sc);
                ForeignMemory.setInt(wsPtr, 1); // State = CONNECTED
                return true;
            } else {
                sc.close();
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Encodes and sends a WebSocket text frame.
     */
    public static boolean send(long wsPtr, String textPayload) {
        if (textPayload == null) return false;
        long textPtr = string.allocate(textPayload);
        boolean res = send(wsPtr, textPtr);
        string.free(textPtr);
        return res;
    }

    public static boolean send(long wsPtr, long textPtr) {
        if (!isConnected(wsPtr) || textPtr == 0L) return false;

        SocketChannel sc = (SocketChannel) Map.getObject(CHANNEL_MAP_PTR, wsPtr);
        if (sc == null) return false;

        try {
            byte[] payload = string.get(textPtr).getBytes(StandardCharsets.UTF_8);
            int len = payload.length;

            byte[] mask = new byte[4];
            RNG.nextBytes(mask);

            int headerLen = 2 + (len > 65535 ? 8 : (len > 125 ? 2 : 0)) + 4;
            ByteBuffer frame = ByteBuffer.allocateDirect(headerLen + len);

            frame.put((byte) 0x81); // FIN + Text opcode

            if (len <= 125) {
                frame.put((byte) (0x80 | len));
            } else if (len <= 65535) {
                frame.put((byte) (0x80 | 126));
                frame.putShort((short) len);
            } else {
                frame.put((byte) (0x80 | 127));
                frame.putLong(len);
            }

            frame.put(mask);

            for (int i = 0; i < len; i++) {
                frame.put((byte) (payload[i] ^ mask[i % 4]));
            }

            frame.flip();
            while (frame.hasRemaining()) {
                sc.write(frame);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Non-blocking poll for incoming WebSocket text frame.
     * Returns an off-heap string handle (long), or 0L if no message frame available.
     */
    public static long poll(long wsPtr) {
        if (!isConnected(wsPtr)) return 0L;

        SocketChannel sc = (SocketChannel) Map.getObject(CHANNEL_MAP_PTR, wsPtr);
        if (sc == null) return 0L;

        try {
            ByteBuffer headerBuf = ByteBuffer.allocateDirect(2);
            int read = sc.read(headerBuf);
            if (read < 2) return 0L;

            headerBuf.flip();
            byte b0 = headerBuf.get();
            byte b1 = headerBuf.get();

            int payloadLen = b1 & 0x7F;
            if (payloadLen == 126) {
                ByteBuffer lenBuf = ByteBuffer.allocateDirect(2);
                if (sc.read(lenBuf) < 2) return 0L;
                lenBuf.flip();
                payloadLen = lenBuf.getShort() & 0xFFFF;
            }

            boolean isMasked = (b1 & 0x80) != 0;
            byte[] mask = new byte[4];
            if (isMasked) {
                ByteBuffer maskBuf = ByteBuffer.allocateDirect(4);
                if (sc.read(maskBuf) < 4) return 0L;
                maskBuf.flip();
                maskBuf.get(mask);
            }

            ByteBuffer dataBuf = ByteBuffer.allocateDirect(payloadLen);
            int totalRead = 0;
            while (totalRead < payloadLen) {
                int r = sc.read(dataBuf);
                if (r < 0) break;
                totalRead += r;
            }

            dataBuf.flip();
            byte[] rawData = new byte[payloadLen];
            dataBuf.get(rawData);

            if (isMasked) {
                for (int i = 0; i < payloadLen; i++) {
                    rawData[i] ^= mask[i % 4];
                }
            }

            String msg = new String(rawData, StandardCharsets.UTF_8);
            return string.allocate(msg);
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * Closes the WebSocket connection.
     */
    public static synchronized void close(long wsPtr) {
        if (wsPtr == 0L) return;
        ForeignMemory.setInt(wsPtr, 0); // State = DISCONNECTED

        SocketChannel sc = (SocketChannel) Map.removeObject(CHANNEL_MAP_PTR, wsPtr);
        if (sc != null) {
            try {
                sc.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Closes connection and frees off-heap native memory block.
     */
    public static void free(long wsPtr) {
        if (wsPtr == 0L) return;
        close(wsPtr);

        long uriPtr = getUri(wsPtr);
        if (uriPtr != 0L) string.free(uriPtr);

        long block = wsPtr - 8L;
        ForeignMemory.freeNative(block);
    }
}
