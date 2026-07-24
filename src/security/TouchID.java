package security;

import annotation.Draft;
import annotation.Intention;
import annotation.PlatformExclusive;
import annotation.Required;
import annotation.Volatile;
import oop.TypeRegister;
import primitive.string;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * macOS LocalAuthentication TouchID / FaceID biometric verification downcall handle.
 */
@Draft
@Intention("Native macOS LocalAuthentication TouchID / FaceID biometric authentication via swift/AppleScript with fallback.")
@Volatile
@PlatformExclusive("Mac")
public final class TouchID {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_TOUCH_ID;
    public static final int TYPE_TOUCH_ID = TypeRegister.FORM_SINGLETON | CLASS_ID;

    private static final boolean isMac;

    static {
        String os = System.getProperty("os.name", "").toLowerCase();
        isMac = os.contains("mac") || os.contains("darwin");
    }

    private TouchID() {}

    public static int classId() {
        return CLASS_ID;
    }

    public static boolean isMac() {
        return isMac;
    }

    public static boolean isSupported() {
        return isMac;
    }

    /**
     * Triggers hardware TouchID biometric verification prompt on macOS.
     * Returns true if TouchID fingerprint authentication succeeded, or false if canceled/failed/unsupported.
     */
    public static boolean authenticate(String reasonPrompt) {
        if (!isMac) return false;
        long reasonPtr = string.allocate(reasonPrompt != null ? reasonPrompt : "Authenticate Anti Security Subsystem");
        boolean res = authenticate(reasonPtr);
        string.free(reasonPtr);
        return res;
    }

    public static boolean authenticate(long reasonPtr) {
        if (!isMac || reasonPtr == 0L) return false;

        String reason = string.get(reasonPtr);
        if (reason == null || reason.isEmpty()) {
            reason = "Authenticate with TouchID for Anti Security Subsystem";
        }

        String swiftScript = """
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
                """.formatted(reason.replace("\"", "\\\""));

        try {
            ProcessBuilder pb = new ProcessBuilder("swift", "-");
            Process p = pb.start();
            p.getOutputStream().write(swiftScript.getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().flush();
            p.getOutputStream().close();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            boolean success = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains("TOUCHID_SUCCESS")) {
                    success = true;
                }
            }
            int exitCode = p.waitFor();
            if (exitCode == 0 && success) return true;
            
            // exitCode 1 = User cancelled/failed. exitCode 2 = TouchID unavailable on this Mac.
            if (exitCode == 1) return false;
            if (exitCode == 2) return false;
            
        } catch (Exception e) {
            throw new RuntimeException("Swift is not installed or available on this Mac! The Anti Security Subsystem requires the Swift CLI to execute biometric authentication.", e);
        }
        return false;
    }
}
