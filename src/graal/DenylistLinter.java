package graal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Build-time denylist linter for the Anti Engine.
 *
 * Detects patterns banned by the engine's GraalVM-native coding rules (see ENGINE_PREFS §13).
 * Runs on plain javac output (no GraalVM SDK, no engine deps) so it can gate both the JVM
 * dev build and the native-image build.
 *
 * This tooling intentionally uses the heap freely; it never ships in the native binary.
 */
public final class DenylistLinter {

    public enum Severity { ERROR, WARNING, INFO }

    public static final class Finding {
        public final Severity severity;
        public final String file;
        public final int line;
        public final String rule;
        public final String message;

        Finding(Severity severity, String file, int line, String rule, String message) {
            this.severity = severity;
            this.file = file;
            this.line = line;
            this.rule = rule;
            this.message = message;
        }

        @Override
        public String toString() {
            return String.format("%-7s %s:%d [%s] %s", severity, file, line, rule, message);
        }
    }

    private record Rule(String name, Severity sev, Pattern pattern, String message) {}

    private static final List<Rule> RULES = List.of(
        // §13.1 reflection — banned outright (native-image cannot reflect without config).
        new Rule("REFLECT_FORNAME", Severity.ERROR,
            Pattern.compile("\\bClass\\.forName\\s*\\("),
            "Class.forName reflection is banned (§13.1). Use MethodHandles or generated code."),
        new Rule("REFLECT_DECLARED", Severity.ERROR,
            Pattern.compile("\\.getDeclared(Field|Fields|Method|Methods|Constructor|Constructors)\\s*\\("),
            "Reflection getDeclared* is banned (§13.1)."),
        new Rule("REFLECT_LOOKUP", Severity.ERROR,
            Pattern.compile("\\.get(Field|Fields|Method|Methods|Constructor|Constructors)\\s*\\("),
            "Reflection lookup is banned (§13.1)."),
        new Rule("REFLECT_NEWINSTANCE", Severity.ERROR,
            Pattern.compile("\\.newInstance\\s*\\("),
            "Reflection newInstance is banned (§13.1)."),
        new Rule("REFLECT_PACKAGE", Severity.ERROR,
            Pattern.compile("\\bjava\\.lang\\.reflect\\.[A-Za-z]"),
            "java.lang.reflect package is banned (§13.1)."),
        // §13.4 internal/sun access — banned.
        new Rule("SUN_UNSAFE", Severity.ERROR,
            Pattern.compile("\\bsun\\.misc\\.Unsafe\\b"),
            "sun.misc.Unsafe is banned (§13.4)."),
        new Rule("JDK_INTERNAL", Severity.ERROR,
            Pattern.compile("\\bjdk\\.internal\\.[A-Za-z]"),
            "jdk.internal package is banned (§13.4)."),
        // §13.1 AWT/Swing — banned (no Java GUI stack in native image).
        new Rule("AWT_SWING", Severity.ERROR,
            Pattern.compile("\\bjava\\.awt\\.[A-Za-z]|\\bjavax\\.swing\\.[A-Za-z]"),
            "AWT/Swing is banned (§13.1)."),
        // §13.1 serialization / dynamic classloading — banned.
        new Rule("SERIALIZATION", Severity.ERROR,
            Pattern.compile("\\bObject(Input|Output)Stream\\b"),
            "Java serialization is banned (§13.1)."),
        new Rule("SERVICE_LOADER", Severity.ERROR,
            Pattern.compile("\\bServiceLoader\\b"),
            "ServiceLoader dynamic loading is banned (§13.1)."),
        new Rule("URL_CLASSLOADER", Severity.ERROR,
            Pattern.compile("\\bURLClassLoader\\b"),
            "URLClassLoader is banned (§13.1)."),
        new Rule("DYNAMIC_PROXY", Severity.ERROR,
            Pattern.compile("\\bProxy\\.newProxyInstance\\b"),
            "Dynamic Proxy is banned (§13.1)."),
        // §13.1 process spawning — flagged, gated behind dev-only classes.
        new Rule("PROCESS_SPAWN", Severity.WARNING,
            Pattern.compile("\\bProcessBuilder\\b|Runtime\\.getRuntime\\(\\)\\.exec"),
            "Process spawning — confine to dev-only/gated classes (§13.1)."),
        // §11.2 static-final FFM handles — flagged, verified against macOSWindow.
        new Rule("STATIC_FINAL_FFM", Severity.WARNING,
            Pattern.compile("static\\s+final\\s+(MethodHandle|SymbolLookup|VarHandle|MemorySegment|Linker)\\b"),
            "static final FFM handle — verify --initialize-at-run-time + runtime init (§11.2)."),
        // §14.3 heap locks on hot paths — known debt, flagged.
        new Rule("HEAP_LOCK_HOTPATH", Severity.WARNING,
            Pattern.compile("\\bsynchronized\\b|\\bReentrantLock\\b|\\bConcurrentHashMap\\b"),
            "Heap lock / concurrent structure on hot path — known debt (§14.3)."),
        // §1/§13 allocation on hot paths — informational.
        new Rule("STRING_FORMAT_HOTPATH", Severity.INFO,
            Pattern.compile("String\\.format|\\bnew\\s+StringBuilder\\b"),
            "String allocation in hot path — review (§1/§13).")
    );

    private DenylistLinter() {}

    /** Lints every .java file under srcRoot (excluding api/ and the analyzer's own dir). */
    public static List<Finding> lint(Path srcRoot) {
        List<Finding> findings = new ArrayList<>();
        List<Path> files = javaFiles(srcRoot);
        for (Path file : files) {
            String rel = srcRoot.relativize(file).toString();
            if (rel.startsWith("api/") || rel.startsWith("graal/")) continue;
            List<String> lines;
            try {
                lines = Files.readAllLines(file);
            } catch (IOException e) {
                continue;
            }
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (Rule rule : RULES) {
                    if (rule.pattern().matcher(line).find()) {
                        findings.add(new Finding(rule.sev(), rel, i + 1, rule.name(), rule.message()));
                    }
                }
            }
        }
        return findings;
    }

    private static List<Path> javaFiles(Path srcRoot) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(srcRoot)) return out;
        try (Stream<Path> stream = Files.walk(srcRoot)) {
            stream.filter(p -> p.toString().endsWith(".java")).sorted().forEach(out::add);
        } catch (IOException e) {
            // ignore
        }
        return out;
    }
}
