#include "primitive/pack.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Pack (primitive/pack.c)
 * LEVEL: L2 — Behavior (primitive behavior API)
 * ============================================================================
 * bit-packing utilities (Legacy: primitive/Pack.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - Pack_packByte(b1, b2)
 *   - Pack_packShort(s1, s2)
 *   - Pack_packInt(i1, i2)
 *   - Pack_unpackByte1(p)
 *   - Pack_unpackByte2(p)
 *   - Pack_unpackShort1(p)
 *   - Pack_unpackShort2(p)
 *   - Pack_unpackInt1(p)
 *   - Pack_unpackInt2(p)
 * ============================================================================
 */

// pack.c — no state, header-only inline helpers.
