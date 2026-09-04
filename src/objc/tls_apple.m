#import "net/tls.h"

#include "annotation/draft.h"
#include "annotation/intention.h"

// net/tls_apple.m — native Apple TLS backend (Network.framework).
;;DRAFT
;;INTENTION("Apple native TLS via Network.framework, fail closed until wired")

bool Tls_connect(const char *host, int port, TlsConn **connOut) {
    if (!host || !connOut)
        return false;
    if (port <= 0)
        return false;
    // Fail closed: native handshake not wired yet. No bytes are sent.
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
