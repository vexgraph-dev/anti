package net;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;
import primitive.string;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

import nio.StringLookup;
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
        String scheme = schemePtr != 0L ? string.get(schemePtr) : StringLookup.getJavaString(38);
        String host = hostPtr != 0L ? string.get(hostPtr) : StringLookup.getJavaString(39);
        String path = pathPtr != 0L ? string.get(pathPtr) : StringLookup.getJavaString(40);
        return buildUri(scheme, host, port, path);
    }

    public static long buildUri(String scheme, String host, int port, String path) {
        String s = scheme != null ? scheme : StringLookup.getJavaString(38);
        String h = host != null ? host : StringLookup.getJavaString(39);
        String p = path != null ? (path.startsWith(StringLookup.getJavaString(40)) ? path : StringLookup.getJavaString(40) + path) : StringLookup.getJavaString(40);
        int defaultPort = StringLookup.getJavaString(41).equalsIgnoreCase(s) ? 443 : 80;

        String uriStr;
        if (port > 0 && port != defaultPort) {
            uriStr = s + StringLookup.getJavaString(42) + h + StringLookup.getJavaString(43) + port + p;
        } else {
            uriStr = s + StringLookup.getJavaString(42) + h + p;
        }
        return string.allocate(uriStr);
    }

    // --- AUTHENTICATION HEADER GENERATION ---

    public static long createBasicAuth(long userPtr, long passPtr) {
        String user = userPtr != 0L ? string.get(userPtr) : StringLookup.getJavaString(0);
        String pass = passPtr != 0L ? string.get(passPtr) : StringLookup.getJavaString(0);
        return createBasicAuth(user, pass);
    }

    public static long createBasicAuth(String username, String password) {
        String user = username != null ? username : StringLookup.getJavaString(0);
        String pass = password != null ? password : StringLookup.getJavaString(0);
        String credentials = user + StringLookup.getJavaString(43) + pass;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return string.allocate(StringLookup.getJavaString(44) + encoded);
    }

    public static long createBearerAuth(long tokenPtr) {
        String token = tokenPtr != 0L ? string.get(tokenPtr) : StringLookup.getJavaString(0);
        return createBearerAuth(token);
    }

    public static long createBearerAuth(String token) {
        String t = token != null ? token : StringLookup.getJavaString(0);
        return string.allocate(StringLookup.getJavaString(45) + t);
    }
}
