package compiler;

import annotation.Draft;
import annotation.Intention;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * On-the-fly programmatic compiler and macOS App packager for the Anti Engine.
 * Supports on-the-fly JVM compilation, jpackage JVM .app bundling, and GraalVM Native Image AOT compilation.
 */
@Draft
@Intention("Programmatic Java compilation engine and automated macOS .app packager for JVM and GraalVM AOT targets.")
public final class AOTCompiler {

    private static final String DEFAULT_PROJECT_DIR = "/Users/vexgraph/IdeaProjects/anti";

    private AOTCompiler() {}

    /**
     * Programmatically compiles all Java sources in 'src' and a target test file in 'scratch' on the fly.
     *
     * @param targetTestClass Relative class name or main file in scratch (e.g. "EngineTest")
     * @return true if compilation succeeded cleanly
     */
    public static boolean compileOnTheFly(String targetTestClass) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("[AOTCompiler] JDK SystemJavaCompiler is unavailable!");
            return false;
        }

        try {
            Path outPath = Paths.get(DEFAULT_PROJECT_DIR, "out");
            Files.createDirectories(outPath);

            Path scratchFile = Paths.get(DEFAULT_PROJECT_DIR, "scratch", targetTestClass + ".java");

            // Gather all src .java files
            Path srcPath = Paths.get(DEFAULT_PROJECT_DIR, "src");
            var srcFiles = Files.walk(srcPath)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toString)
                    .toList();

            // Dynamically scan and build classpath string for javax.tools compiler
            StringBuilder cpBuilder = new StringBuilder();
            cpBuilder.append(outPath.toAbsolutePath());
            Path libPath = Paths.get(DEFAULT_PROJECT_DIR, "lib");
            if (Files.exists(libPath)) {
                try (var jars = Files.walk(libPath)) {
                    jars.filter(p -> p.toString().endsWith(".jar"))
                        .forEach(p -> cpBuilder.append(File.pathSeparator).append(p.toAbsolutePath()));
                }
            }

            String currentRelease = System.getProperty("java.specification.version");

            var arguments = new java.util.ArrayList<String>();
            arguments.add("--enable-preview");
            arguments.add("-source");
            arguments.add(currentRelease);
            arguments.add("-cp");
            arguments.add(cpBuilder.toString());
            arguments.add("-d");
            arguments.add(outPath.toAbsolutePath().toString());

            arguments.addAll(srcFiles);
            if (Files.exists(scratchFile)) {
                arguments.add(scratchFile.toAbsolutePath().toString());
            }

            int exitCode = compiler.run(null, null, null, arguments.toArray(new String[0]));
            if (exitCode == 0) {
                System.out.println("[AOTCompiler] On-the-fly compilation of " + targetTestClass + " succeeded!");
                return true;
            } else {
                System.err.println("[AOTCompiler] On-the-fly compilation failed with exit code: " + exitCode);
                return false;
            }
        } catch (IOException e) {
            System.err.println("[AOTCompiler] Compilation IO Exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * Builds a JVM-bundled macOS .app using jpackage.
     * Fast build time (~3s), bundled JVM runtime.
     *
     * @param mainClass Main entry point class (e.g. "EngineTest")
     * @param appName Output app name (e.g. "AntiEngine-JVM")
     */
    public static boolean buildJPackageApp(String mainClass, String appName) {
        System.out.println("[AOTCompiler] Starting jpackage .app bundle build for " + appName + "...");
        if (!compileOnTheFly(mainClass)) {
            return false;
        }

        try {
            Path distDir = Paths.get(DEFAULT_PROJECT_DIR, "out", "dist");
            Files.createDirectories(distDir);

            // Step 1: Create JAR
            ProcessBuilder jarPb = new ProcessBuilder(
                "jar", "--create",
                "--file", "out/engine.jar",
                "--main-class", mainClass,
                "-C", "out", "."
            );
            jarPb.directory(new File(DEFAULT_PROJECT_DIR));
            if (jarPb.start().waitFor() != 0) {
                System.err.println("[AOTCompiler] Failed to package engine.jar");
                return false;
            }

            // Step 2: Run jpackage
            ProcessBuilder pb = new ProcessBuilder(
                "jpackage",
                "--name", appName,
                "--input", "out",
                "--main-jar", "engine.jar",
                "--main-class", mainClass,
                "--dest", "out/dist",
                "--type", "app-image",
                "--java-options", "--enable-preview",
                "--java-options", "--enable-native-access=ALL-UNNAMED",
                "--java-options", "-XstartOnFirstThread",
                "--java-options", "-Dmac.firstThread=true"
            );
            pb.directory(new File(DEFAULT_PROJECT_DIR));
            pb.inheritIO();

            int exitCode = pb.start().waitFor();
            if (exitCode == 0) {
                // Ad-hoc local code sign
                ProcessBuilder signPb = new ProcessBuilder("codesign", "--force", "--deep", "--sign", "-", "out/dist/" + appName + ".app");
                signPb.directory(new File(DEFAULT_PROJECT_DIR));
                signPb.start().waitFor();

                System.out.println("[AOTCompiler] SUCCESS! Built " + appName + ".app at out/dist/" + appName + ".app");
                return true;
            }
        } catch (Exception e) {
            System.err.println("[AOTCompiler] Error building jpackage .app: " + e.getMessage());
        }
        return false;
    }

    /**
     * Builds a GraalVM Native Image compiled macOS .app.
     * Instant startup (<10ms), tiny size (~20MB), zero JVM overhead.
     *
     * @param mainClass Main entry point class (e.g. "EngineTest")
     * @param appName Output app name (e.g. "AntiEngine-Native")
     */
    public static boolean buildNativeImageApp(String mainClass, String appName) {
        System.out.println("[AOTCompiler] Starting GraalVM Native Image compilation for " + appName + "...");
        if (!compileOnTheFly(mainClass)) {
            return false;
        }

        try {
            String binaryName = appName.toLowerCase();
            Path outPath = Paths.get(DEFAULT_PROJECT_DIR, "out");
            Path distDir = Paths.get(DEFAULT_PROJECT_DIR, "out", "dist");
            Files.createDirectories(distDir);

            // Step 1: Execute native-image AOT compiler
            ProcessBuilder nativePb = new ProcessBuilder(
                "native-image",
                "--enable-preview",
                "--enable-native-access=ALL-UNNAMED",
                "-H:+ForeignAPISupport",
                "-cp", "out:lib/lwjgl-release-3.4.2-custom/*:lib/oshi/*",
                mainClass,
                "-o", "out/" + binaryName
            );
            nativePb.directory(new File(DEFAULT_PROJECT_DIR));
            nativePb.inheritIO();

            int exitCode = nativePb.start().waitFor();
            if (exitCode != 0) {
                System.err.println("[AOTCompiler] native-image compilation failed!");
                return false;
            }

            // Step 2: Construct .app bundle directory
            Path appPath = distDir.resolve(appName + ".app");
            Path macOsDir = appPath.resolve("Contents/MacOS");
            Path resourcesDir = appPath.resolve("Contents/Resources");
            Files.createDirectories(macOsDir);
            Files.createDirectories(resourcesDir);

            Files.copy(outPath.resolve(binaryName), macOsDir.resolve(binaryName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Step 3: Write Info.plist
            String plistContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>CFBundleExecutable</key>
                    <string>%s</string>
                    <key>CFBundleIdentifier</key>
                    <string>com.vexgraph.anti</string>
                    <key>CFBundleName</key>
                    <string>%s</string>
                    <key>CFBundlePackageType</key>
                    <string>APPL</string>
                    <key>CFBundleShortVersionString</key>
                    <string>1.0</string>
                    <key>LSMinimumSystemVersion</key>
                    <string>12.0</string>
                    <key>NSHighResolutionCapable</key>
                    <true/>
                </dict>
                </plist>
                """.formatted(binaryName, appName);

            Files.writeString(appPath.resolve("Contents/Info.plist"), plistContent);

            // Step 4: Ad-hoc code sign
            ProcessBuilder signPb = new ProcessBuilder("codesign", "--force", "--deep", "--sign", "-", appPath.toString());
            signPb.directory(new File(DEFAULT_PROJECT_DIR));
            signPb.start().waitFor();

            System.out.println("[AOTCompiler] SUCCESS! Built GraalVM Native .app at " + appPath);
            return true;
        } catch (Exception e) {
            System.err.println("[AOTCompiler] Error building Native Image .app: " + e.getMessage());
        }
        return false;
    }
}
