// src/security/touchid_cocoa.m — macOS LocalAuthentication bridge for TouchID.
//
// Bridges the C11 token-based TouchID API to Apple's LocalAuthentication framework
// (LAContext). Evaluates biometric policy (TouchID) with passcode fallback if configured.

#import <Foundation/Foundation.h>
#import <LocalAuthentication/LocalAuthentication.h>

#include "security/touchid.h"
#include "annotation/platform_exclusive.h"

;;PLATFORM_EXCLUSIVE("Mac")

// Active single-use token tracking
static TouchIDToken s_active_token = { .magic = {0, 0}, .consumed = true };
static bool s_token_valid = false;

TouchIDToken TouchID_authenticate(const char *reason) {
    TouchIDToken null_token = { .magic = {0, 0}, .consumed = true };

    @autoreleasepool {
        LAContext *context = [[LAContext alloc] init];
        NSError *error = nil;

        // LAPolicyDeviceOwnerAuthentication presents TouchID (biometrics) first
        // and seamlessly flips to the system password sheet if the user chooses
        // "Use Password...", or if TouchID is not enrolled/locked _out.
        LAPolicy policy = LAPolicyDeviceOwnerAuthentication;
        if (![context canEvaluatePolicy:policy error:&error]) {
            if (error) {
                fprintf(stderr, "[TouchID] Authentication not supported: %s\n",
                        error.localizedDescription.UTF8String);
            }
            return null_token;
        }

        NSString *localizedReason = reason ? [NSString stringWithUTF8String:reason]
                                           : @"Authorize security operation";

        dispatch_semaphore_t sema = dispatch_semaphore_create(0);
        __block BOOL authSuccess = NO;
        __block NSInteger authErrorCode = 0;

        [context evaluatePolicy:policy
                localizedReason:localizedReason
                          reply:^(BOOL success, NSError * _Nullable err) {
            authSuccess = success;
            if (!success && err) {
                authErrorCode = err.code;
            }
            dispatch_semaphore_signal(sema);
        }];

        // Wait synchronously for biometric scan, password verification, or dismissal
        dispatch_semaphore_wait(sema, DISPATCH_TIME_FOREVER);

        if (!authSuccess) {
            // Normal user actions (Cancel, Fallback, System sleep/switch) return clean null token.
            // Only log fatal/unexpected system errors.
            if (authErrorCode != 0 &&
                authErrorCode != LAErrorUserCancel &&
                authErrorCode != LAErrorUserFallback &&
                authErrorCode != LAErrorAppCancel &&
                authErrorCode != LAErrorSystemCancel) {
                fprintf(stderr, "[TouchID] Authentication rejected (code: %ld)\n", (long)authErrorCode);
            }
            return null_token;
        }

        // Generate cryptographically secure single-use token
        TouchIDToken tok;
        arc4random_buf(tok.magic, sizeof(tok.magic));
        tok.consumed = false;

        s_active_token = tok;
        s_token_valid = true;

        return tok;
    }
}

bool TouchID_verify(TouchIDToken tok) {
    if (tok.consumed || !s_token_valid) {
        return false;
    }

    if (tok.magic[0] == s_active_token.magic[0] &&
        tok.magic[1] == s_active_token.magic[1] &&
        !s_active_token.consumed) {
        // Token is valid: consume immediately (single-use)
        s_active_token.consumed = true;
        s_token_valid = false;
        return true;
    }

    return false;
}

void TouchID_discard(TouchIDToken tok) {
    (void)tok;
    s_token_valid = false;
    s_active_token.consumed = true;
    s_active_token.magic[0] = 0;
    s_active_token.magic[1] = 0;
}
