#ifndef SRC_SECURITY_TOUCHID_H
#define SRC_SECURITY_TOUCHID_H

#include <stdint.h>
#include <stdbool.h>

/*
 * TouchID token — opaque to the C core.
 *
 * Design rationale (why a bare "if (TouchID()) ..." is unsafe):
 *   - The auth result is a single-use, time-bound token, not a boolean.
 *   - The C core never trusts a bare "yes"/"no" from the biometric.
 *   - Every call starts a fresh LAContext; the token is invalidated
 *     immediately after use or on cancel/timeout.
 *   - Paired with an explicit UI confirmation step ("Verify with TouchID?").
 *   - If the operation involves keys, SecAccessControl + TouchIDCurrentSet
 *     ensures the key never leaves the Secure Enclave.
 *
 * API pattern (challenge–response):
 *   1. Token  = TouchID_authenticate("reason string");
 *   2. if (TouchID_verify(token)) { perform the operation; }
 *   3. TouchID_discard(token);   // explicit cleanup, or auto via scope
 *
 * The C core should never write:   if (TouchID_authenticate(...)) { ... }
 * instead it must write:
 *   TouchIDToken tok = TouchID_authenticate("reason");
 *   if (TouchID_verify(tok)) { perform the operation; }
 *   TouchID_discard(tok);
 */

typedef struct {
    uint64_t magic[2];   // randomness, never written by C core... oo00oo00ohh *magic*
    bool     consumed;   // set true by verify / discard
} TouchIDToken;

/* Start a fresh authentication session.
 * reason is a human-readable string shown to the user.
 * Returns a token that the C core must verify exactly once.
 * The underlying ObjC bridge (touchid_cocoa.m) presents an LAContext;
 * in a stub build the auth succeeds immediately. */
TouchIDToken TouchID_authenticate(const char *reason);

/* Consume a token granted by TouchID_authenticate.
 * Returns true iff the token has not been consumed yet and is still valid.
 * After this returns true the token is consumed and must not be re-used. */
bool TouchID_verify(TouchIDToken tok);

/* Explicitly discard a token without verifying it.
 * Use this if the user cancels the dialog or you decide not to proceed
 * after auth. After calling this the token is consumed. */
void TouchID_discard(TouchIDToken tok);

/* Convenience 1-line prompt wrapper.
 * Initiates an authentication session with reason, verifies the resulting
 * single-use token, and automatically discards/cleans up the session.
 *
 * Usage:
 *   if (TouchID_prompt("Authorize sensitive action")) {
 *       // Operation permitted
 *   }
 *
 * Returns true iff authentication succeeded and the single-use token verified. */
static inline bool TouchID_prompt(const char *reason) {
    TouchIDToken tok = TouchID_authenticate(reason);
    bool ok = TouchID_verify(tok);
    TouchID_discard(tok);
    return ok;
}

#endif