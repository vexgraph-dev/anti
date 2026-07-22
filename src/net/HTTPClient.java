package net;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;
import primitive.string;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Draft
@Intention("Off-heap zero-GC cross-platform HTTP client utilizing FFM direct memory buffers and primitive.string handles")
public final class HTTPClient {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_HTTP_CLIENT;

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HTTPClient() {}

    // --- SIMPLE GET & POST (RETURNS OFF-HEAP STRING POINTER) ---

    public static long get(long urlPtr) {
        if (urlPtr == 0L) return 0L;
        String urlStr = string.get(urlPtr);
        return get(urlStr);
    }

    public static long get(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return 0L;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return string.allocate(resp.body());
        } catch (Exception e) {
            return 0L;
        }
    }

    public static long post(long urlPtr, long bodyPtr) {
        if (urlPtr == 0L) return 0L;
        String urlStr = string.get(urlPtr);
        byte[] bodyBytes = bodyPtr != 0L ? string.get(bodyPtr).getBytes(StandardCharsets.UTF_8) : new byte[0];
        return post(urlStr, bodyBytes);
    }

    public static long post(String urlStr, byte[] bodyBytes) {
        if (urlStr == null || urlStr.isEmpty()) return 0L;
        try {
            byte[] payload = bodyBytes != null ? bodyBytes : new byte[0];
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return string.allocate(resp.body());
        } catch (Exception e) {
            return 0L;
        }
    }

    public static long post(String urlStr, String bodyStr) {
        byte[] bytes = bodyStr != null ? bodyStr.getBytes(StandardCharsets.UTF_8) : new byte[0];
        return post(urlStr, bytes);
    }

    // --- FULL OFF-HEAP HTTP REQUEST EXECUTION ---

    public static long request(long methodPtr, long urlPtr, long headersPtr, long bodyPtr) {
        String method = methodPtr != 0L ? string.get(methodPtr) : "GET";
        String urlStr = urlPtr != 0L ? string.get(urlPtr) : "";
        String headersStr = headersPtr != 0L ? string.get(headersPtr) : null;
        String bodyStr = bodyPtr != 0L ? string.get(bodyPtr) : null;
        return request(method, urlStr, headersStr, bodyStr);
    }

    public static long request(String method, String urlStr, String headersStr, String bodyStr) {
        if (urlStr == null || urlStr.isEmpty()) return 0L;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr));

            if (headersStr != null && !headersStr.isEmpty()) {
                String[] lines = headersStr.split("\n");
                for (String line : lines) {
                    int idx = line.indexOf(':');
                    if (idx > 0) {
                        String name = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        builder.header(name, value);
                    }
                }
            }

            byte[] bodyBytes = bodyStr != null ? bodyStr.getBytes(StandardCharsets.UTF_8) : new byte[0];
            HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArray(bodyBytes);

            switch (method.toUpperCase()) {
                case "POST" -> builder.POST(publisher);
                case "PUT" -> builder.PUT(publisher);
                case "DELETE" -> builder.DELETE();
                case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                default -> builder.GET();
            }

            HttpResponse<byte[]> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return string.allocate(resp.body());
        } catch (Exception e) {
            return 0L;
        }
    }

    // --- FETCH WITH STATUS CODE ---

    public static int getStatus(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return 500;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .GET()
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode();
        } catch (Exception e) {
            return 500;
        }
    }

    public static int getStatus(long urlPtr) {
        if (urlPtr == 0L) return 500;
        return getStatus(string.get(urlPtr));
    }
}
