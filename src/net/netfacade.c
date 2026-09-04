// src/net/netfacade.c — the combined conveniences promised by net/net.h.
//
// Thin glue: each call is one HttpRequest + one Json_parse. All storage is
// the caller's; nothing here allocates.

#include "net/net.h"

#include <stdio.h>
#include <string.h>
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Netfacade (net/netfacade.c)
 * ============================================================================
 * the combined conveniences promised by net/net.h.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - Json_parse(doc, nodes, nodeCap, scratch, scratchCap, body)
 *   - Net_postJson(host, port, path, jsonBody, respBody, cap)
 *
 * Getters:
 *   - Net_get(host, port, path, bodyOut, cap)
 *   - Net_getJson(host, port, path, doc, nodes, nodeCap, scratch, scratchCap)
 * ============================================================================
 */


int64_t Net_get(const char *host, int port, const char *path,
                char *bodyOut, size_t cap) {
    HttpRequest req = {
        .method = "GET",
        .host = host,
        .port = port,
        .path = path,
    };
    HttpResponse resp = { .body = bodyOut, .bodyCap = cap };
    if (!Http_perform(&req, &resp))
        return -1;
    return resp.status;
}

bool Net_getJson(const char *host, int port, const char *path,
                 JsonDoc *doc, JsonNode *nodes, uint32_t nodeCap,
                 char *scratch, uint32_t scratchCap) {
    static _Thread_local char body[16384];
    HttpRequest req = { .method = "GET", .host = host, .port = port, .path = path };
    HttpResponse resp = { .body = body, .bodyCap = sizeof(body) };
    if (!Http_perform(&req, &resp))
        return false;
    if (resp.status != 200)
        return false;
    return Json_parse(doc, nodes, nodeCap, scratch, scratchCap, body);
}

int64_t Net_postJson(const char *host, int port, const char *path,
                     const char *jsonBody, char *respBody, size_t cap) {
    HttpHeader headers[1] = {
        { .name = "Content-Type", .value = "application/json" },
    };
    HttpRequest req = {
        .method = "POST",
        .host = host,
        .port = port,
        .path = path,
        .headers = headers,
        .headerCount = 1,
        .body = jsonBody,
        .bodyLen = strlen(jsonBody),
    };
    HttpResponse resp = { .body = respBody, .bodyCap = cap };
    if (!Http_perform(&req, &resp))
        return -1;
    return resp.status;
}
