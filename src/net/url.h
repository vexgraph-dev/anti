#ifndef NET_URL_H
#define NET_URL_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// net/url.h — URI synthesis & authentication headers (Legacy:
// net/TransportProtocol.java). Everything writes into caller buffers and
// reports what fit; nothing allocates.

// Default ports per scheme ("https" -> 443, else 80).
int NetUrl_defaultPort(const char *scheme);

// Build "scheme://host[:port]/path" into out. NULL scheme/host/path fall
// back to "http", "", "/". Returns length written or -1 when out is small.
int64_t NetUrl_build(const char *scheme, const char *host, int port,
                     const char *path, char *out, size_t cap);

// RFC 4648 base64. out needs 4*ceil(len/3)+1 bytes. Returns length or -1.
int64_t NetUrl_base64(const uint8_t *in, size_t len, char *out, size_t cap);

// "Basic base64(user:pass)" into out. Returns length or -1.
int64_t NetUrl_basicAuth(const char *user, const char *pass, char *out, size_t cap);

// "Bearer token" into out. Returns length or -1.
int64_t NetUrl_bearerAuth(const char *token, char *out, size_t cap);

#endif
