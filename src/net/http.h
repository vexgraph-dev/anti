#ifndef NET_HTTP_H
#define NET_HTTP_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <pthread.h>

// net/http.h — HTTP/1.1 client & micro server over BSD sockets
// (Legacy: net/HTTPClient.java, net/HTTPServer.java, net/PollRequest.java).
//
// One transport, two directions. The client performs one request per call
// (Connection: close semantics) into caller-owned buffers — nothing inside
// this module allocates. TLS is not wired yet: http:// only until a curl
// backend lands behind the same surface (the legacy used libcurl for https).

#define HTTP_MAX_HEADERS 16

typedef struct HttpHeader {
    const char *name;
    const char *value;
} HttpHeader;

typedef struct HttpRequest {
    const char *scheme;   // "http" default, "https" requires TLS backend
    const char *method;   // "GET", "POST", ... default "GET"
    const char *host;     // required
    int port;             // 0 -> 80
    const char *path;     // default "/"
    const HttpHeader *headers;
    uint32_t headerCount;
    const char *body;     // nullptr = no body
    size_t bodyLen;
    uint32_t timeoutMs;   // connect+read budget, default 5000
} HttpRequest;

typedef struct HttpResponse {
    bool ok;              // transport-level success; status may still be 4xx
    int status;
    char contentType[128];
    char *body;           // caller-owned receive buffer
    size_t bodyCap;
    size_t bodyLen;
} HttpResponse;

// Execute one request. Fills resp; body is written into resp->body up to
// bodyCap and NUL-terminated when it fits.
bool Http_perform(const HttpRequest *req, HttpResponse *resp);

// --- Micro server ---

// What the handler sees of one exchange. body points into an internal
// buffer valid only during the callback.
typedef struct HttpExchange {
    char method[16];
    char path[1024];
    const char *body;
    size_t bodyLen;
} HttpExchange;

typedef void (*HttpHandler)(const HttpExchange *exchange, int clientFd, void *userdata);

typedef struct HttpServer {
    int listenFd;
    pthread_t thread;
    volatile bool running;
    HttpHandler handler;
    void *userdata;
} HttpServer;

// Bind + listen + spawn the accept loop on its own thread. Returns false on
// socket failure; portOut receives the bound port (use 0 to pick ephemeral).
bool HttpServer_start(HttpServer *server, int port, HttpHandler handler,
                      void *userdata, int *portOut);

// Stop accepting and close the listener. In-flight handler finishes.
void HttpServer_stop(HttpServer *server);

// Handler-side responder: writes a full response on the open connection.
void Http_respond(int clientFd, int status, const char *contentType,
                  const char *body, size_t bodyLen);

#endif
