#ifndef NET_TLS_H
#define NET_TLS_H

#include <stdbool.h>
#include <stddef.h>

// net/tls.h — TLS backend seam (Legacy: net/TransportProtocol.java).
//
// http:// stays on plain TCP. https:// requires a backend:
//   - APPLE: src/net/tls_apple.m (Network.framework / native roots)
//   - other: src/net/tls_curl.c (libcurl)
// Until a backend reports ready, Tls_connect fails closed (false).

typedef struct TlsConn {
    int backend;
    void *opaque;
} TlsConn;

// Connect with TLS to host:port. connOut is filled on success.
// Returns false when scheme is unsupported or handshake fails.
bool Tls_connect(const char *host, int port, TlsConn **connOut);

// Send bytes over an established TLS connection.
bool Tls_send(TlsConn *conn, const char *buf, size_t len);

// Receive up to cap bytes. gotOut receives the count.
bool Tls_recv(TlsConn *conn, char *buf, size_t cap, size_t *gotOut);

// Close and release. Safe with nullptr.
void Tls_close(TlsConn *conn);

#endif
