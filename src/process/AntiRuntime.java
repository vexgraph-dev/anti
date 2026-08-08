package process;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// this is where the runtime starts, gets the system information
// the graphics, the setup, everything, before the main class ever gets run
//
// on macos, if its detected as macos, it will trampoline to create an instance/s
// of window/s to ensure the CAMetalLayer is running at thread 0 while the others
// are running on their respective threads, whatever.
public class AntiRuntime {
    public AntiRuntime() {
    }

    public static void init(String[] args) {
        // 1. Resolve LWJGL native library paths for both JVM and GraalVM AOT execution
        if (System.getProperty("org.lwjgl.librarypath") == null) {
            if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
                String command = ProcessHandle.current().info().command().orElse(null);
                if (command != null) {
                    File exeFile = new File(command);
                    File exeDir = exeFile.getParentFile();
                    if (exeDir != null && exeDir.exists()) {
                        System.setProperty("org.lwjgl.librarypath", exeDir.getAbsolutePath());
                    }
                }
            } else {
                File localNatives = new File("natives");
                if (localNatives.exists()) {
                    System.setProperty("org.lwjgl.librarypath", localNatives.getAbsolutePath());
                } else {
                    File scratchNatives = new File("scratch/natives");
                    if (scratchNatives.exists()) {
                        System.setProperty("org.lwjgl.librarypath", scratchNatives.getAbsolutePath());
                    }
                }
            }
        }

        // 2. macOS Trampoline: Auto-relaunch with -XstartOnFirstThread if missing (JVM mode only)
        if (System.getProperty("os.name").toLowerCase().contains("mac")
                && System.getProperty("java.home") != null
                && !Boolean.getBoolean("mac.firstThread")) {

            System.out.println("[AntiRuntime] macOS Detected: Relaunching JVM with -XstartOnFirstThread...");

            List<String> childArgs = new ArrayList<>();
            childArgs.add(System.getProperty("java.home") + "/bin/java");
            childArgs.add("--enable-preview");
            childArgs.add("--enable-native-access=ALL-UNNAMED");
            childArgs.add("-XstartOnFirstThread");
            childArgs.add("-Xmx128m"); // Heap kept small for off-heap focus

            Properties props = System.getProperties();
            for (Map.Entry<Object, Object> e : props.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (key.startsWith("anti.")) {
                    childArgs.add("-D" + key + "=" + String.valueOf(e.getValue()));
                }
            }
            childArgs.add("-Dmac.firstThread=true"); // Mark that we've relaunched
            childArgs.add("-cp");
            childArgs.add(System.getProperty("java.class.path"));

            // Use StackWalker to dynamically find the class that called AntiRuntime.init()
            String callerClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .walk(s -> s.skip(1).findFirst().get().getDeclaringClass().getName());
            
            childArgs.add(callerClass);
            if (args != null) {
                childArgs.addAll(List.of(args));
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(childArgs);
                pb.inheritIO();
                Process p = pb.start();
                System.exit(p.waitFor());
            } catch (Exception e) {
                throw new RuntimeException("AntiRuntime trampoline failed", e);
            }
        }
        
        System.out.println("[AntiRuntime] Environment fully initialized.");
    }
}
