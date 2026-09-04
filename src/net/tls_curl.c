#include "net/tls.h"

#include "annotation/draft.h"
#include "annotation/intention.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Tls_curl (net/tls_curl.c)
 * ============================================================================
 * libcurl TLS backend for non-Apple platforms.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - Tls_connect(host, port, connOut)
 *   - Tls_send(conn, buf, len)
 *   - Tls_recv(conn, buf, cap, gotOut)
 *   - Tls_close(conn)
 * ============================================================================
 */


// net/tls_curl.c — libcurl TLS backend for non-Apple platforms.
;;DRAFT
;;INTENTION("libcurl https backend, fail closed until wired")

bool Tls_connect(const char *host, int port, TlsConn **connOut) {
    if (!host || !connOut)
        return false;
    if (port <= 0)
        return false;
    // Fail closed: libcurl handshake not wired yet. No bytes are sent.
    (*connOut) = 0;
    return false;
}

bool Tls_send(TlsConn *conn, const char *buf, size_t len) {
    if (!conn || !buf)
        return false;
    if (len == 0)
        return true;
    return false;
}

bool Tls_recv(TlsConn *conn, char *buf, size_t cap, size_t *gotOut) {
    if (!conn || !buf || !gotOut)
        return false;
    if (cap == 0)
        return false;
    (*gotOut) = 0;
    return false;
}

void Tls_close(TlsConn *conn) {
    if (!conn)
        return;
    (*conn).opaque = 0;
    (*conn).backend = 0;
}
