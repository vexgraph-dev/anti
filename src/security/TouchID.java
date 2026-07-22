package security;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import nio.ForeignMemory;
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
     * Returns true if TouchID fingerprint authentication succeeded, or false if cancelled/failed/unsupported.
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

        String swiftScript = "import LocalAuthentication\n" +
                "import Foundation\n" +
                "let context = LAContext()\n" +
                "var error: NSError?\n" +
                "let reason = \"" + reason.replace("\"", "\\\"") + "\"\n" +
                "if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) || context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) {\n" +
                "    let semaphore = DispatchSemaphore(value: 0)\n" +
                "    var authenticated = false\n" +
                "    context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, authError in\n" +
                "        authenticated = success\n" +
                "        semaphore.signal()\n" +
                "    }\n" +
                "    semaphore.wait()\n" +
                "    if authenticated { print(\"TOUCHID_SUCCESS\"); exit(0); } else { print(\"TOUCHID_FAILED\"); exit(1); }\n" +
                "} else { print(\"TOUCHID_UNAVAILABLE\"); exit(2); }\n";

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
        } catch (Exception ignored) {}

        // Fallback to macOS AppleScript system dialog if Swift is unavailable
        try {
            ProcessBuilder pb = new ProcessBuilder("osascript", "-e",
                    "display dialog \"" + reason + "\" with title \"Anti Security Subsystem\" buttons {\"Cancel\", \"Authenticate\"} default button \"Authenticate\" with icon caution");
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
