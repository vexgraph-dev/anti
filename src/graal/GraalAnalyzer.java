package graal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GraalAnalyzer — build-time gate for the Anti Engine's native-image rules.
 *
 * Two checks run against src/ (excluding the nested api/ sub-repository):
 *
 *  1. Denylist linter — flags banned patterns from the engine's §13 rules
 *     (reflection, sun.misc.Unsafe, jdk.internal, AWT/Swing, serialization,
 *     dynamic classloading, process spawning, static-final FFM handles, ...).
 *
 *  2. FFM cross-reference — every {@code FunctionDescriptor.of(...)} used by a
 *     downcall/upcall is canonicalized and checked against the descriptors
 *     registered in src/config/FFMRegistrationFeature.java. Used-but-unregistered
 *     descriptors are reported; these are the exact cases that crash a native
 *     image on first invoke.
 *
 * Usage:
 *   java graal.GraalAnalyzer [--root DIR] [--strict]
 *
 * Exit codes: 0 = clean, 1 = findings above the severity threshold, 2 = usage error.
 */
public final class GraalAnalyzer {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";

    public static void main(String[] args) {
        String root = "src";
        boolean strict = false;

        // -- parse args manually (no deps)
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--root": case "-r":
                    if (i + 1 < args.length) root = args[++i];
                    break;
                case "--strict":
                    strict = true;
                    break;
                case "--help": case "-h":
                    usage();
                    System.exit(0);
                    break;
                default:
                    System.err.println("unknown arg: " + args[i]);
                    usage();
                    System.exit(2);
            }
        }

        Path rootPath = Path.of(root);
        Path feature = rootPath.resolve("config").resolve("FFMRegistrationFeature.java");
        if (!Files.exists(feature)) {
            // allow running from repo root with src/ layout
            feature = Path.of(root, "config", "FFMRegistrationFeature.java");
        }

        int exit = 0;
        exit |= runDenylist(rootPath, strict);
        exit |= runFFM(rootPath, feature, strict);
        System.exit(exit);
    }

    private static void usage() {
        System.out.println("Usage: java graal.GraalAnalyzer [--root DIR] [--strict]");
        System.out.println("  --root DIR   source root to analyze (default: src)");
        System.out.println("  --strict     treat WARNING/INFO findings as errors too");
    }

    // ------------------------------------------------------------------ denylist

    private static int runDenylist(Path root, boolean strict) {
        List<DenylistLinter.Finding> findings = DenylistLinter.lint(root);
        Map<DenylistLinter.Severity, Integer> counts = new EnumMap<>(DenylistLinter.Severity.class);
        for (DenylistLinter.Severity s : DenylistLinter.Severity.values()) counts.put(s, 0);

        System.out.println();
        System.out.println("=== Denylist linter ===");
        if (findings.isEmpty()) {
            System.out.println("  clean");
            return 0;
        }
        for (DenylistLinter.Finding f : findings) {
            counts.put(f.severity, counts.get(f.severity) + 1);
            System.out.println("  " + color(f.severity) + f + ANSI_RESET);
        }
        System.out.println("  -- summary: "
            + counts.get(DenylistLinter.Severity.ERROR) + " error(s), "
            + counts.get(DenylistLinter.Severity.WARNING) + " warning(s), "
            + counts.get(DenylistLinter.Severity.INFO) + " info");

        int threshold = strict ? 2 : 1; // 2 = INFO+WARNING+ERROR, 1 = ERROR only
        boolean bad = counts.get(DenylistLinter.Severity.ERROR) > 0
            || (strict && counts.get(DenylistLinter.Severity.WARNING) > 0)
            || (strict && counts.get(DenylistLinter.Severity.INFO) > 0);
        return bad ? 1 : 0;
    }

    // ------------------------------------------------------------------ FFM

    private static int runFFM(Path root, Path feature, boolean strict) {
        List<FFMScanner.DescriptorUse> used = FFMScanner.scanSource(root);
        List<FFMScanner.DescriptorUse> registered = FFMScanner.scanRegistrations(feature);

        Set<String> regDown = new HashSet<>();
        Set<String> regUp = new HashSet<>();
        for (FFMScanner.DescriptorUse r : registered) {
            (r.kind == FFMScanner.Kind.DOWNCALL ? regDown : regUp).add(r.signature);
        }

        List<FFMScanner.DescriptorUse> missingDown = new ArrayList<>();
        List<FFMScanner.DescriptorUse> missingUp = new ArrayList<>();
        for (FFMScanner.DescriptorUse u : used) {
            Set<String> reg = u.kind == FFMScanner.Kind.DOWNCALL ? regDown : regUp;
            if (!reg.contains(u.signature)) {
                (u.kind == FFMScanner.Kind.DOWNCALL ? missingDown : missingUp).add(u);
            }
        }

        System.out.println();
        System.out.println("=== FFM descriptor cross-reference ===");
        System.out.println("  registered: " + regDown.size() + " downcall(s), " + regUp.size() + " upcall(s)");
        System.out.println("  used in src: " + used.size() + " call site(s)");
        if (missingDown.isEmpty() && missingUp.isEmpty()) {
            System.out.println("  all used descriptors are registered");
            return 0;
        }

        boolean bad = false;
        if (!missingDown.isEmpty()) {
            System.out.println("  " + ANSI_YELLOW + "unregistered downcalls:" + ANSI_RESET);
            for (FFMScanner.DescriptorUse u : dedupe(missingDown)) {
                System.out.println("    " + u.signature + "  @" + u.file + ":" + u.line);
            }
            bad = true;
        }
        if (!missingUp.isEmpty()) {
            System.out.println("  " + ANSI_YELLOW + "unregistered upcalls:" + ANSI_RESET);
            for (FFMScanner.DescriptorUse u : dedupe(missingUp)) {
                System.out.println("    " + u.signature + "  @" + u.file + ":" + u.line);
            }
            bad = true;
        }
        System.out.println("  -> register these in src/config/FFMRegistrationFeature.java "
            + "(see nio.ForeignMemory and window.macOSWindow patterns)");
        return bad ? 1 : 0;
    }

    private static List<FFMScanner.DescriptorUse> dedupe(List<FFMScanner.DescriptorUse> in) {
        Map<String, FFMScanner.DescriptorUse> bySig = new LinkedHashMap<>();
        for (FFMScanner.DescriptorUse u : in) {
            bySig.putIfAbsent(u.signature, u);
        }
        return new ArrayList<>(bySig.values());
    }

    private static String color(DenylistLinter.Severity s) {
        return switch (s) {
            case ERROR -> ANSI_RED;
            case WARNING, INFO -> ANSI_YELLOW;
        };
    }
}
