package process;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import nio.StringLookup;
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
        if (System.getProperty(StringLookup.getJavaString(1206)) == null) {
            if (System.getProperty(StringLookup.getJavaString(1207)) != null) {
                String command = ProcessHandle.current().info().command().orElse(null);
                if (command != null) {
                    File exeFile = new File(command).getAbsoluteFile();
                    File exeDir = exeFile.getParentFile();
                    if (exeDir != null && exeDir.exists()) {
                        System.setProperty(StringLookup.getJavaString(1206), exeDir.getAbsolutePath());
                    }
                }
            } else {
                File localNatives = new File(StringLookup.getJavaString(1208));
                if (localNatives.exists()) {
                    System.setProperty(StringLookup.getJavaString(1206), localNatives.getAbsolutePath());
                } else {
                    File scratchNatives = new File(StringLookup.getJavaString(1209));
                    if (scratchNatives.exists()) {
                        System.setProperty(StringLookup.getJavaString(1206), scratchNatives.getAbsolutePath());
                    }
                }
            }
        }

        // 2. macOS Trampoline: Auto-relaunch with -XstartOnFirstThread if missing (JVM mode only)
        if (System.getProperty(StringLookup.getJavaString(143)).toLowerCase().contains(StringLookup.getJavaString(144))
                && System.getProperty(StringLookup.getJavaString(1210)) != null
                && !Boolean.getBoolean(StringLookup.getJavaString(1211))) {

            System.out.println(StringLookup.getJavaString(1212));

            List<String> childArgs = new ArrayList<>();
            childArgs.add(System.getProperty(StringLookup.getJavaString(1210)) + StringLookup.getJavaString(1213));
            childArgs.add(StringLookup.getJavaString(1143));
            childArgs.add(StringLookup.getJavaString(1171));
            childArgs.add(StringLookup.getJavaString(1172));
            childArgs.add(StringLookup.getJavaString(1214)); // Heap kept small for off-heap focus

            Properties props = System.getProperties();
            for (Map.Entry<Object, Object> e : props.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (key.startsWith(StringLookup.getJavaString(1215))) {
                    childArgs.add(StringLookup.getJavaString(1216) + key + StringLookup.getJavaString(462) + String.valueOf(e.getValue()));
                }
            }
            childArgs.add(StringLookup.getJavaString(1173)); // Mark that we've relaunched
            childArgs.add(StringLookup.getJavaString(1145));
            childArgs.add(System.getProperty(StringLookup.getJavaString(1217)));

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
                throw new RuntimeException(StringLookup.getJavaString(1218), e);
            }
        }
        
        System.out.println(StringLookup.getJavaString(1219));
    }
}
