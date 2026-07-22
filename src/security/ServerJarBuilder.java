package security;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import oop.TypeRegister;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Build utility compiling and packaging server-side modules into executable anti-server.jar.
 */
@Draft
@Intention("Build utility bundling HTTPServer, NetworkingThread, and security modules into anti-server.jar.")
@Volatile
public final class ServerJarBuilder {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SERVER_JAR_BUILDER;

    private ServerJarBuilder() {}

    public static int classId() {
        return CLASS_ID;
    }

    /**
     * Builds executable standalone server JAR at target output path.
     */
    public static boolean buildServerJar(String outputJarPath, String mainClassName) {
        Path outDir = Paths.get("out");
        if (!Files.exists(outDir)) {
            System.err.println("Compiled 'out' directory missing. Please compile classes first!");
            return false;
        }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClassName != null ? mainClassName : "net.HTTPServer");

        File jarFile = new File(outputJarPath != null ? outputJarPath : "anti-server.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile), manifest);
             Stream<Path> paths = Files.walk(outDir)) {

            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".class"))
                 .forEach(p -> {
                     String entryName = outDir.relativize(p).toString().replace("\\", "/");
                     try {
                         jos.putNextEntry(new JarEntry(entryName));
                         try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(p.toFile()))) {
                             in.transferTo(jos);
                         }
                         jos.closeEntry();
                     } catch (IOException e) {
                         System.err.println("Failed to add JAR entry: " + entryName);
                     }
                 });

            System.out.println("SUCCESSFULLY CREATED STANDALONE SERVER JAR -> " + jarFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            System.err.println("Failed to build server JAR: " + e.getMessage());
            return false;
        }
    }
}
