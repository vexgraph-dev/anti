// net/http.c — HTTP/1.1 client & micro server over BSD sockets
// (Legacy: net/HTTPClient.java / HTTPServer.java / PollRequest.java port).
//
// The legacy drove libcurl through FFM; here the transport is ours: one
// blocking TCP connection per request, header block parsed in place, body
// received per Content-Length or chunked framing into the caller's buffer.
// Zero allocation — every buffer is caller-owned.

#if !defined(_GNU_SOURCE) && !defined(__APPLE__)
#define _GNU_SOURCE // strcasestr
#endif

#include "net/http.h"

#include <arpa/inet.h>
#include <netdb.h>
#include <netinet/in.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

#include "net/url.h"

#define HEADER_BLOCK_CAP 8192
#define RECV_TIMEOUT_DEFAULT_MS 5000
#define SERVER_BODY_CAP 16384

// Handlers run inline on the single accept thread, so one shared receive
// buffer is safe.
static char s_serverBody[SERVER_BODY_CAP];

static void applyTimeout(int fd, uint32_t ms) {
    struct timeval tv = { .tv_sec = ms / 1000, .tv_usec = (suseconds_t)((ms % 1000) * 1000) };
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
}

// --- Client ---

static bool writeAll(int fd, const char *buf, size_t len) {
    size_t sent = 0;
    while (sent < len) {
        ssize_t n = send(fd, buf + sent, len - sent, 0);
        if (n <= 0) return false;
        sent += (size_t)n;
    }
    return true;
}

// Read one CRLF-terminated line (without CRLF) from fd.
static bool readLine(int fd, char *out, size_t cap, size_t *lenOut) {
    size_t n = 0;
    while (n + 1 < cap) {
        char c;
        ssize_t r = recv(fd, &c, 1, 0);
        if (r <= 0) return false;
        if (c == '\n') {
            if (n > 0 && out[n - 1] == '\r') n--;
            out[n] = '\0';
            *lenOut = n;
            return true;
        }
        out[n++] = c;
    }
    return false;
}

static bool recvSome(int fd, char *dst, size_t cap, size_t *gotOut) {
    ssize_t r = recv(fd, dst, cap, 0);
    if (r <= 0) return false;
    *gotOut = (size_t)r;
    return true;
}

bool Http_perform(const HttpRequest *req, HttpResponse *resp) {
    // Preserve the caller's receive buffer across the init wipe.
    char *bodyOut = resp ? (*resp).body : nullptr;
    size_t bodyCap = resp ? (*resp).bodyCap : 0;

    memset(resp, 0, sizeof(*resp));
    (*resp).status = -1;
    (*resp).body = bodyOut;
    (*resp).bodyCap = bodyCap;

    if (!req || !(*req).host || !(*resp).body) return false;

    const char *method = (*req).method ? (*req).method : "GET";
    const char *path = (*req).path ? (*req).path : "/";
    int port = (*req).port > 0 ? (*req).port : Url_defaultPort("http");
    uint32_t timeoutMs = (*req).timeoutMs ? (*req).timeoutMs : RECV_TIMEOUT_DEFAULT_MS;

    // --- Resolve ---
    char portStr[8];
    snprintf(portStr, sizeof(portStr), "%d", port);
    struct addrinfo hints = { .ai_family = AF_UNSPEC, .ai_socktype = SOCK_STREAM };
    struct addrinfo *res = nullptr;
    if (getaddrinfo((*req).host, portStr, &hints, &res) != 0 || !res) return false;

    int fd = socket((*res).ai_family, (*res).ai_socktype, (*res).ai_protocol);
    if (fd < 0) {
        freeaddrinfo(res);
        return false;
    }
    applyTimeout(fd, timeoutMs);

    bool ok = connect(fd, (*res).ai_addr, (*res).ai_addrlen) == 0;
    freeaddrinfo(res);
    if (!ok) {
        close(fd);
        return false;
    }

    // --- Send request head ---
    char head[HEADER_BLOCK_CAP];
    int h = snprintf(head, sizeof(head),
                     "%s %s HTTP/1.1\r\n"
                     "Host: %s\r\n"
                     "Connection: close\r\n"
                     "Content-Length: %zu\r\n",
                     method, path, (*req).host, (*req).bodyLen);
    if (h < 0 || (size_t)h >= sizeof(head)) {
        close(fd);
        return false;
    }

    for (uint32_t i = 0; i < (*req).headerCount && i < HTTP_MAX_HEADERS; i++) {
        int k = snprintf(head + h, sizeof(head) - (size_t)h, "%s: %s\r\n",
                         (*req).headers[i].name, (*req).headers[i].value);
        if (k < 0 || (size_t)k >= sizeof(head) - (size_t)h) {
            close(fd);
            return false;
        }
        h += k;
    }
    h += snprintf(head + h, sizeof(head) - (size_t)h, "\r\n");
    ok = writeAll(fd, head, (size_t)h);

    // --- Send body (skip for GET-style no-body verbs) ---
    if (ok && (*req).body && (*req).bodyLen)
        ok = writeAll(fd, (*req).body, (*req).bodyLen);

    // --- Status line + headers ---
    char line[2048];
    size_t lineLen;
    if (!ok || !readLine(fd, line, sizeof(line), &lineLen)) {
        close(fd);
        return false;
    }
    if (strncmp(line, "HTTP/", 5) != 0) {
        close(fd);
        return false;
    }
    (*resp).status = atoi(line + 9); // "HTTP/1.1 NNN ..."

    bool chunked = false;
    while (readLine(fd, line, sizeof(line), &lineLen) && lineLen > 0) {
        const char *ct = "Content-Type:";
        const char *te = "Transfer-Encoding:";
        if (strncasecmp(line, ct, strlen(ct)) == 0) {
            const char *v = line + strlen(ct);
            while (*v == ' ') v++;
            snprintf((*resp).contentType, sizeof((*resp).contentType), "%s", v);
        } else if (strncasecmp(line, te, strlen(te)) == 0) {
            if (strcasestr(line, "chunked")) chunked = true;
        }
    }

    // --- Body ---
    size_t total = 0;
    ok = true;
    if (chunked) {
        // Each chunk: hex-size line, payload, CRLF; zero chunk terminates.
        while (true) {
            if (!readLine(fd, line, sizeof(line), &lineLen)) { ok = false; break; }
            size_t chunkLen = (size_t)strtoul(line, nullptr, 16);
            if (chunkLen == 0) break;
            if (total + chunkLen > (*resp).bodyCap) { ok = false; break; }
            size_t got = 0;
            while (got < chunkLen && ok) {
                size_t step;
                if (!recvSome(fd, (*resp).body + total + got, chunkLen - got, &step)) ok = false;
                else got += step;
            }
            total += got;
            char crlf[2];
            if (ok) ok = recv(fd, crlf, 2, 0) == 2;
        }
    } else {
        ssize_t r;
        while (total < (*resp).bodyCap && (r = recv(fd, (*resp).body + total, (*resp).bodyCap - total, 0)) > 0)
            total += (size_t)r;
    }
    close(fd);

    (*resp).bodyLen = total;
    if (total < (*resp).bodyCap)
        (*resp).body[total] = '\0';
    (*resp).ok = ok;
    return ok;
}

void Http_respond(int clientFd, int status, const char *contentType,
                  const char *body, size_t bodyLen) {
    const char *reason =
        status == 200 ? "OK" :
        status == 201 ? "Created" :
        status == 400 ? "Bad Request" :
        status == 404 ? "Not Found" :
        status == 405 ? "Method Not Allowed" :
        status == 500 ? "Internal Server Error" : "Unknown";

    char head[1024];
    int h = snprintf(head, sizeof(head),
                     "HTTP/1.1 %d %s\r\n"
                     "Content-Type: %s\r\n"
                     "Content-Length: %zu\r\n"
                     "Connection: close\r\n"
                     "\r\n",
                     status, reason, contentType ? contentType : "text/plain", bodyLen);
    if (h < 0) return;
    if (!writeAll(clientFd, head, (size_t)h)) return;
    if (bodyLen) writeAll(clientFd, body, bodyLen);
}

// --- Micro server ---

static void *acceptLoop(void *arg) {
    HttpServer *server = arg;
    while ((*server).running) {
        // Poll with a short budget instead of blocking in accept forever:
        // stopping must stay cooperative, since closing the listener does
        // not reliably wake a parked accept on every platform.
        struct pollfd pfd = { .fd = (*server).listenFd, .events = POLLIN };
        if (poll(&pfd, 1, 100) <= 0)
            continue;

        int clientFd = accept((*server).listenFd, nullptr, nullptr);
        if (clientFd < 0) continue;

        applyTimeout(clientFd, RECV_TIMEOUT_DEFAULT_MS);

        HttpExchange ex;
        memset(&ex, 0, sizeof(ex));

        char line[2048];
        size_t lineLen = 0;
        size_t total = 0;
        bool haveHead = false;
        size_t contentLen = 0;

        // Slurp until end of headers, then Content-Length worth of body.
        while (total < SERVER_BODY_CAP &&
               readLine(clientFd, line, sizeof(line), &lineLen)) {
            if (lineLen == 0) break; // blank line ends headers
            if (!haveHead) {
                sscanf(line, "%15s %1023s", ex.method, ex.path);
                haveHead = true;
                continue;
            }
            if (strncasecmp(line, "Content-Length:", 15) == 0)
                contentLen = (size_t)strtoul(line + 15, nullptr, 10);
        }
        while (total < contentLen && total < SERVER_BODY_CAP) {
            ssize_t r = recv(clientFd, s_serverBody + total, contentLen - total, 0);
            if (r <= 0) break;
            total += (size_t)r;
        }

        ex.body = total ? s_serverBody : nullptr;
        ex.bodyLen = total;

        (*server).handler(&ex, clientFd, (*server).userdata);
        close(clientFd);
    }
    return nullptr;
}

bool HttpServer_start(HttpServer *server, int port, HttpHandler handler,
                      void *userdata, int *portOut) {
    memset(server, 0, sizeof(*server));
    (*server).handler = handler;
    (*server).userdata = userdata;

    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return false;

    int reuse = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse));

    struct sockaddr_in addr = { .sin_family = AF_INET,
                                .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
                                .sin_port = htons((uint16_t)(port > 0 ? port : 0)) };
    if (bind(fd, (struct sockaddr*) &addr, sizeof(addr)) != 0 || listen(fd, 4) != 0) {
        close(fd);
        return false;
    }

    socklen_t len = sizeof(addr);
    getsockname(fd, (struct sockaddr*) &addr, &len);
    if (portOut) *portOut = ntohs(addr.sin_port);

    (*server).listenFd = fd;
    (*server).running = true;
    return pthread_create(&(*server).thread, nullptr, acceptLoop, server) == 0;
}

void HttpServer_stop(HttpServer *server) {
    if (!(*server).listenFd) return;
    (*server).running = false;      // loop notices within one poll period
    pthread_join((*server).thread, nullptr);
    close((*server).listenFd);
    (*server).listenFd = 0;
}
