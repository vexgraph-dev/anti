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

import nio.StringLookup;
/**
 * On-the-fly programmatic compiler and macOS App packager for the Anti Engine.
 * Supports on-the-fly JVM compilation, jpackage JVM .app bundling, and GraalVM Native Image AOT compilation.
 */
@Draft
@Intention("Programmatic Java compilation engine and automated macOS .app packager for JVM and GraalVM AOT targets.")
public final class AOTCompiler {

    private static final String DEFAULT_PROJECT_DIR = StringLookup.getJavaString(1136);

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
            System.err.println(StringLookup.getJavaString(1137));
            return false;
        }

        try {
            Path outPath = Paths.get(DEFAULT_PROJECT_DIR, StringLookup.getJavaString(133));
            Files.createDirectories(outPath);

            Path scratchFile = Paths.get(DEFAULT_PROJECT_DIR, StringLookup.getJavaString(1138), targetTestClass + StringLookup.getJavaString(1139));

            // Gather all src .java files
            Path srcPath = Paths.get(DEFAULT_PROJECT_DIR, StringLookup.getJavaString(880));
            var srcFiles = Files.walk(srcPath)
                    .filter(p -> p.toString().endsWith(StringLookup.getJavaString(1139)))
                    .map(Path::toString)
                    .toList();

            // Dynamically scan and build classpath string for javax.tools compiler
            StringBuilder cpBuilder = new StringBuilder();
            cpBuilder.append(outPath.toAbsolutePath());
            Path libPath = Paths.get(DEFAULT_PROJECT_DIR, StringLookup.getJavaString(1140));
            if (Files.exists(libPath)) {
                try (var jars = Files.walk(libPath)) {
                    jars.filter(p -> p.toString().endsWith(StringLookup.getJavaString(1141)))
                        .forEach(p -> cpBuilder.append(File.pathSeparator).append(p.toAbsolutePath()));
                }
            }

            String currentRelease = System.getProperty(StringLookup.getJavaString(1142));

            var arguments = new java.util.ArrayList<String>();
            arguments.add(StringLookup.getJavaString(1143));
            arguments.add(StringLookup.getJavaString(1144));
            arguments.add(currentRelease);
            arguments.add(StringLookup.getJavaString(1145));
            arguments.add(cpBuilder.toString());
            arguments.add(StringLookup.getJavaString(1146));
            arguments.add(outPath.toAbsolutePath().toString());

            arguments.addAll(srcFiles);
            if (Files.exists(scratchFile)) {
                arguments.add(scratchFile.toAbsolutePath().toString());
            }

            int exitCode = compiler.run(null, null, null, arguments.toArray(new String[0]));
            if (exitCode == 0) {
                System.out.println(StringLookup.getJavaString(1147) + targetTestClass + StringLookup.getJavaString(1148));
                return true;
            } else {
                System.err.println(StringLookup.getJavaString(1149) + exitCode);
                return false;
            }
        } catch (IOException e) {
            System.err.println(StringLookup.getJavaString(1150) + e.getMessage());
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
        System.out.println(StringLookup.getJavaString(1151) + appName + StringLookup.getJavaString(1152));
        if (!compileOnTheFly(mainClass)) {
            return false;
        }

        try {
            Path distDir = Paths.get(DEFAULT_PROJECT_DIR, StringLookup.getJavaString(133), StringLookup.getJavaString(1153));
            Files.createDirectories(distDir);

            // Step 1: Create JAR
            ProcessBuilder jarPb = new ProcessBuilder(
                StringLookup.getJavaString(1154), StringLookup.getJavaString(1155),
                StringLookup.getJavaString(1156), StringLookup.getJavaString(1157),
                StringLookup.getJavaString(1158), mainClass,
                StringLookup.getJavaString(1159), StringLookup.getJavaString(133), StringLookup.getJavaString(311)
            );
            jarPb.directory(new File(DEFAULT_PROJECT_DIR));
            if (jarPb.start().waitFor() != 0) {
                System.err.println(StringLookup.getJavaString(1160));
                return false;
            }

            // Step 2: Run jpackage
            ProcessBuilder pb = new ProcessBuilder(
                StringLookup.getJavaString(1161),
                StringLookup.getJavaString(1162), appName,
                StringLookup.getJavaString(1163), StringLookup.getJavaString(133),
                StringLookup.getJavaString(1164), StringLookup.getJavaString(1165),
                StringLookup.getJavaString(1158), mainClass,
                StringLookup.getJavaString(1166), StringLookup.getJavaString(1167),
                StringLookup.getJavaString(1168), StringLookup.getJavaString(1169),
                StringLookup.getJavaString(1170), StringLookup.getJavaString(1143),
                StringLookup.getJavaString(1170), StringLookup.getJavaString(1171),
                StringLookup.getJavaString(1170), StringLookup.getJavaString(1172),
                StringLookup.getJavaString(1170), StringLookup.getJavaString(1173)
            );
            pb.directory(new File(DEFAULT_PROJECT_DIR));
            pb.inheritIO();

            int exitCode = pb.start().waitFor();
            if (exitCode == 0) {
                // Ad-hoc local code sign
                ProcessBuilder signPb = new ProcessBuilder(StringLookup.getJavaString(1174), StringLookup.getJavaString(1175), StringLookup.getJavaString(1176), StringLookup.getJavaString(1177), StringLookup.getJavaString(150), StringLookup.getJavaString(1178) + appName + StringLookup.getJavaString(1179));
                signPb.directory(new File(DEFAULT_PROJECT_DIR));
                signPb.start().waitFor();

                System.out.println(StringLookup.getJavaString(1180) + appName + StringLookup.getJavaString(1181) + appName + StringLookup.getJavaString(1179));
                return true;
            }
        } catch (Exception e) {
            System.err.println(StringLookup.getJavaString(1182) + e.getMessage());
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
        System.out.println(StringLookup.getJavaString(1183) + appName + StringLookup.getJavaString(1152));
        if (!compileOnTheFly(mainClass)) {
            return false;
        }

        try {
            String binaryName = appName.toLowerCase();
            Path outPath = Paths.get(DEFAULT_PROJECT_DIR, StringLookup.getJavaString(133));
            Path distDir = Paths.get(DEFAULT_PROJECT_DIR, StringLookup.getJavaString(133), StringLookup.getJavaString(1153));
            Files.createDirectories(distDir);

            // Step 1: Execute native-image AOT compiler
            ProcessBuilder nativePb = new ProcessBuilder(
                StringLookup.getJavaString(1184),
                StringLookup.getJavaString(1143),
                StringLookup.getJavaString(1171),
                StringLookup.getJavaString(1185),
                StringLookup.getJavaString(1145), StringLookup.getJavaString(1186),
                mainClass,
                StringLookup.getJavaString(1187), StringLookup.getJavaString(1188) + binaryName
            );
            nativePb.directory(new File(DEFAULT_PROJECT_DIR));
            nativePb.inheritIO();

            int exitCode = nativePb.start().waitFor();
            if (exitCode != 0) {
                System.err.println(StringLookup.getJavaString(1189));
                return false;
            }

            // Step 2: Construct .app bundle directory
            Path appPath = distDir.resolve(appName + StringLookup.getJavaString(1179));
            Path macOsDir = appPath.resolve(StringLookup.getJavaString(1190));
            Path resourcesDir = appPath.resolve(StringLookup.getJavaString(1191));
            Files.createDirectories(macOsDir);
            Files.createDirectories(resourcesDir);

            Files.copy(outPath.resolve(binaryName), macOsDir.resolve(binaryName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Step 3: Write Info.plist
            String plistContent = StringLookup.getJavaString(1192).formatted(binaryName, appName);

            Files.writeString(appPath.resolve(StringLookup.getJavaString(1193)), plistContent);

            // Step 4: Ad-hoc code sign
            ProcessBuilder signPb = new ProcessBuilder(StringLookup.getJavaString(1174), StringLookup.getJavaString(1175), StringLookup.getJavaString(1176), StringLookup.getJavaString(1177), StringLookup.getJavaString(150), appPath.toString());
            signPb.directory(new File(DEFAULT_PROJECT_DIR));
            signPb.start().waitFor();

            System.out.println(StringLookup.getJavaString(1194) + appPath);
            return true;
        } catch (Exception e) {
            System.err.println(StringLookup.getJavaString(1195) + e.getMessage());
        }
        return false;
    }
}
