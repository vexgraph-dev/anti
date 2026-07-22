package net;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;
import primitive.string;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

@Draft
@Intention("Transport protocol framing, URI synthesis, and zero-GC authentication header generator over primitive.string handles")
public final class TransportProtocol {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_TRANSPORT_PROTOCOL;

    public static final int PROTOCOL_HTTP      = 1;
    public static final int PROTOCOL_HTTPS     = 2;
    public static final int PROTOCOL_WEBSOCKET = 3;
    public static final int PROTOCOL_RAW_TCP   = 4;
    public static final int PROTOCOL_RAW_UDP   = 5;

    private TransportProtocol() {}

    // --- URI SYNTHESIS (OFF-HEAP ZERO-GC) ---

    public static long buildUri(long schemePtr, long hostPtr, int port, long pathPtr) {
        String scheme = schemePtr != 0L ? string.get(schemePtr) : "http";
        String host = hostPtr != 0L ? string.get(hostPtr) : "localhost";
        String path = pathPtr != 0L ? string.get(pathPtr) : "/";
        return buildUri(scheme, host, port, path);
    }

    public static long buildUri(String scheme, String host, int port, String path) {
        String s = scheme != null ? scheme : "http";
        String h = host != null ? host : "localhost";
        String p = path != null ? (path.startsWith("/") ? path : "/" + path) : "/";
        int defaultPort = "https".equalsIgnoreCase(s) ? 443 : 80;

        String uriStr;
        if (port > 0 && port != defaultPort) {
            uriStr = s + "://" + h + ":" + port + p;
        } else {
            uriStr = s + "://" + h + p;
        }
        return string.allocate(uriStr);
    }

    // --- AUTHENTICATION HEADER GENERATION ---

    public static long createBasicAuth(long userPtr, long passPtr) {
        String user = userPtr != 0L ? string.get(userPtr) : "";
        String pass = passPtr != 0L ? string.get(passPtr) : "";
        return createBasicAuth(user, pass);
    }

    public static long createBasicAuth(String username, String password) {
        String user = username != null ? username : "";
        String pass = password != null ? password : "";
        String credentials = user + ":" + pass;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return string.allocate("Authorization: Basic " + encoded);
    }

    public static long createBearerAuth(long tokenPtr) {
        String token = tokenPtr != 0L ? string.get(tokenPtr) : "";
        return createBearerAuth(token);
    }

    public static long createBearerAuth(String token) {
        String t = token != null ? token : "";
        return string.allocate("Authorization: Bearer " + t);
    }
}
