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
import java.nio.file.Files;
import java.nio.file.Path;

import nio.StringLookup;
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
        String os = System.getProperty(StringLookup.getJavaString(143), StringLookup.getJavaString(0)).toLowerCase();
        isMac = os.contains(StringLookup.getJavaString(144)) || os.contains(StringLookup.getJavaString(145));
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
        long reasonPtr = string.allocate(reasonPrompt != null ? reasonPrompt : StringLookup.getJavaString(146));
        boolean res = authenticate(reasonPtr);
        string.free(reasonPtr);
        return res;
    }

    public static boolean authenticate(long reasonPtr) {
        if (!isMac || reasonPtr == 0L) return false;

        String reason = string.get(reasonPtr);
        if (reason == null || reason.isEmpty()) {
            reason = StringLookup.getJavaString(147);
        }

        String swiftScript;
        try {
            swiftScript = Files.readString(Path.of(StringLookup.getJavaString(148))).formatted(reason.replace(StringLookup.getJavaString(63), StringLookup.getJavaString(69)));
        } catch (Exception e) {
            throw new RuntimeException(StringLookup.getJavaString(1238), e);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(StringLookup.getJavaString(149), StringLookup.getJavaString(150));
            Process p = pb.start();
            p.getOutputStream().write(swiftScript.getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().flush();
            p.getOutputStream().close();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            boolean success = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains(StringLookup.getJavaString(151))) {
                    success = true;
                }
            }
            int exitCode = p.waitFor();
            if (exitCode == 0 && success) return true;
            
            // exitCode 1 = User cancelled/failed. exitCode 2 = TouchID unavailable on this Mac.
            if (exitCode == 1) return false;
            if (exitCode == 2) return false;
            
        } catch (Exception e) {
            throw new RuntimeException(StringLookup.getJavaString(152), e);
        }
        return false;
    }
}
