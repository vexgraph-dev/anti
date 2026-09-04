// net/url.c — URI synthesis & authentication headers (Legacy:
// net/TransportProtocol.java port). Pure byte plumbing; every writer
// reserves room for the NUL and fails clean instead of truncating.

#include "net/url.h"

#include <stdio.h>
#include <strings.h>
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Url (net/url.c)
 * LEVEL: L2 — Behavior (net behavior API)
 * ============================================================================
 * URI synthesis & authentication headers (Legacy:
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - Url_defaultPort(scheme)
 *   - Url_build(scheme, host, port, path, out, cap)
 *   - Url_base64(in, len, out, cap)
 *   - Url_basicAuth(user, pass, out, cap)
 *   - Url_bearerAuth(token, out, cap)
 * ============================================================================
 */


int Url_defaultPort(const char *scheme) {
    if (scheme && strcasecmp(scheme, "https") == 0) return 443;
    return 80;
}

static const char HTTP[] = "http";

int64_t Url_build(const char *scheme, const char *host, int port,
                     const char *path, char *out, size_t cap) {
    const char *s = scheme ? scheme : HTTP;
    const char *h = host ? host : "";
    const char *pIn = path ? path : "/";
    // Path must start with a slash; prepend when the caller forgot.
    // joined must live beyond the if-block — p will alias it.
    char joined[1024];
    const char *p = pIn;
    if (*p != '/') {
        int j = snprintf(joined, sizeof(joined), "/%s", pIn);
        if (j < 0 || (size_t)j >= sizeof(joined)) return -1;
        p = joined;
    }

    int defaultPort = Url_defaultPort(s);
    int n;
    if (port > 0 && port != defaultPort)
        n = snprintf(out, cap, "%s://%s:%d%s", s, h, port, p);
    else
        n = snprintf(out, cap, "%s://%s%s", s, h, p);
    if (n < 0) return -1;
    if (cap == 0 || (size_t)n >= cap) return -1; // truncated — contract says -1
    return (int64_t)n;
}

// the characters of base64
static const char B64[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

int64_t Url_base64(const uint8_t *in, size_t len, char *out, size_t cap) {
    size_t needed = 4 * ((len + 2) / 3);
    if (cap < needed + 1) return -1;

    size_t o = 0;
    size_t i = 0;
    while (i + 3 <= len) {
        uint32_t v = ((uint32_t)in[i] << 16) | ((uint32_t)in[i + 1] << 8) | in[i + 2];
        out[o++] = B64[(v >> 18) & 0x3F];
        out[o++] = B64[(v >> 12) & 0x3F];
        out[o++] = B64[(v >> 6) & 0x3F];
        out[o++] = B64[v & 0x3F];
        i += 3;
    }
    size_t rem = len - i;
    if (rem == 1) {
        uint32_t v = (uint32_t)in[i] << 16;
        out[o++] = B64[(v >> 18) & 0x3F];
        out[o++] = B64[(v >> 12) & 0x3F];
        out[o++] = '=';
        out[o++] = '=';
    } else if (rem == 2) {
        uint32_t v = ((uint32_t)in[i] << 16) | ((uint32_t)in[i + 1] << 8);
        out[o++] = B64[(v >> 18) & 0x3F];
        out[o++] = B64[(v >> 12) & 0x3F];
        out[o++] = B64[(v >> 6) & 0x3F];
        out[o++] = '=';
    }
    out[o] = '\0';
    return (int64_t)o;
}

int64_t Url_basicAuth(const char *user, const char *pass, char *out, size_t cap) {
    char creds[512];
    int c = snprintf(creds, sizeof(creds), "%s:%s", user ? user : "", pass ? pass : "");
    if (c < 0 || (size_t)c >= sizeof(creds)) return -1;

    char encoded[768];
    int64_t e = Url_base64((const uint8_t*) creds, (size_t)c, encoded, sizeof(encoded));
    if (e < 0) return -1;

    int n = snprintf(out, cap, "Basic %s", encoded);
    if (n < 0) return -1;
    if (cap == 0 || (size_t)n >= cap) return -1;
    return (int64_t)n;
}

int64_t Url_bearerAuth(const char *token, char *out, size_t cap) {
    int n = snprintf(out, cap, "Bearer %s", token ? token : "");
    if (n < 0) return -1;
    if (cap == 0 || (size_t)n >= cap) return -1;
    return (int64_t)n;
}
