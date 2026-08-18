import LocalAuthentication
import Foundation
let context = LAContext()
var error: NSError?
let reason = "%s"
if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) || context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) {
    let semaphore = DispatchSemaphore(value: 0)
    var authenticated = false
    context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { success, authError in
        authenticated = success
        semaphore.signal()
    }
    semaphore.wait()
    if authenticated { print("TOUCHID_SUCCESS"); exit(0); } else { print("TOUCHID_FAILED"); exit(1); }
} else { print("TOUCHID_UNAVAILABLE"); exit(2); }
