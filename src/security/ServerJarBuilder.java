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

import nio.StringLookup;
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
        Path outDir = Paths.get(StringLookup.getJavaString(133));
        if (!Files.exists(outDir)) {
            System.err.println(StringLookup.getJavaString(134));
            return false;
        }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, StringLookup.getJavaString(135));
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClassName != null ? mainClassName : StringLookup.getJavaString(136));

        File jarFile = new File(outputJarPath != null ? outputJarPath : StringLookup.getJavaString(137));
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile), manifest);
             Stream<Path> paths = Files.walk(outDir)) {

            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(StringLookup.getJavaString(138)))
                 .forEach(p -> {
                     String entryName = outDir.relativize(p).toString().replace(StringLookup.getJavaString(139), StringLookup.getJavaString(40));
                     try {
                         jos.putNextEntry(new JarEntry(entryName));
                         try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(p.toFile()))) {
                             in.transferTo(jos);
                         }
                         jos.closeEntry();
                     } catch (IOException e) {
                         System.err.println(StringLookup.getJavaString(140) + entryName);
                     }
                 });

            System.out.println(StringLookup.getJavaString(141) + jarFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            System.err.println(StringLookup.getJavaString(142) + e.getMessage());
            return false;
        }
    }
}
