package graal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Build-time FFM (Foreign Function & Memory) cross-reference scanner.
 *
 * Extracts every {@code FunctionDescriptor.of(...)} / {@code ofVoid(...)} expression used as a
 * downcall ({@code linker.downcallHandle(...)}) or upcall ({@code linker.upcallStub(...)}) across
 * src/, canonicalizes each descriptor to a structural signature, and diffs it against the
 * descriptors registered in {@code src/config/FFMRegistrationFeature.java}.
 *
 * Anything used-but-unregistered is exactly the crash-on-first-invoke class of bug the native
 * image hits, so this linter makes it a hard, greppable finding instead.
 *
 * This tooling intentionally uses the heap freely; it never ships in the native binary.
 */
public final class FFMScanner {

    public enum Kind { DOWNCALL, UPCALL }

    public static final class DescriptorUse {
        public final Kind kind;
        public final String signature;
        public final String file;
        public final int line;

        DescriptorUse(Kind kind, String signature, String file, int line) {
            this.kind = kind;
            this.signature = signature;
            this.file = file;
            this.line = line;
        }
    }

    private static final Pattern DESC_CALL = Pattern.compile("FunctionDescriptor\\.(of|ofVoid)\\s*\\(");
    private static final Pattern DOWNCALL_CALL = Pattern.compile("\\bdowncallHandle\\s*\\(");
    private static final Pattern UPCALL_CALL = Pattern.compile("\\bupcallStub\\s*\\(");
    private static final Pattern STRUCT_DEF =
        Pattern.compile("(?:MemoryLayout|StructLayout)\\s+(\\w+)\\s*=\\s*MemoryLayout\\.structLayout\\s*\\(");
    private static final Pattern DESC_DEF =
        Pattern.compile("FunctionDescriptor\\s+(\\w+)\\s*=\\s*FunctionDescriptor\\.(of|ofVoid)\\s*\\(");
    private static final Pattern LAYOUT_TYPE = Pattern.compile("ValueLayout\\.(JAVA_[A-Z_]+|ADDRESS)");
    private static final Pattern WITH_NAME = Pattern.compile("\\.withName\\s*\\(\\s*(?:\"[^\"]*\"|'[^']*')\\s*\\)");

    private FFMScanner() {}

    /** Scans all FFM usage in src/ (excluding api/ and the analyzer's own dir). */
    public static List<DescriptorUse> scanSource(Path srcRoot) {
        List<DescriptorUse> uses = new ArrayList<>();
        for (Path file : javaFiles(srcRoot)) {
            String rel = srcRoot.relativize(file).toString();
            if (rel.startsWith("api/") || rel.startsWith("graal/")) continue;
            String text;
            try {
                text = new String(Files.readAllBytes(file));
            } catch (IOException e) {
                continue;
            }
            uses.addAll(scanFile(text, rel));
        }
        return uses;
    }

    /** Reads the descriptors registered in FFMRegistrationFeature (downcall + upcall sets). */
    public static List<DescriptorUse> scanRegistrations(Path featureFile) {
        List<DescriptorUse> registered = new ArrayList<>();
        if (!Files.isReadable(featureFile)) return registered;
        try {
            String masked = maskComments(new String(Files.readAllBytes(featureFile)));
            addRegistrationCalls(masked, "registerForDowncall", Kind.DOWNCALL, registered);
            addRegistrationCalls(masked, "registerForUpcall", Kind.UPCALL, registered);
        } catch (IOException e) {
            // ignore
        }
        return registered;
    }

    private static void addRegistrationCalls(String masked, String method, Kind kind, List<DescriptorUse> out) {
        int idx = 0;
        while (idx < masked.length()) {
            int call = masked.indexOf(method, idx);
            if (call < 0) break;
            int open = masked.indexOf('(', call);
            if (open < 0) break;
            int close = matchParen(masked, open);
            if (close < 0) break;
            String descText = extractDescriptorExpr(masked.substring(open + 1, close));
            if (descText != null) {
                String sig = canonicalDescriptor(descText, Map.of());
                if (sig != null) {
                    out.add(new DescriptorUse(kind, sig, "", lineAt(masked, call)));
                }
            }
            idx = close + 1;
        }
    }

    /** Scans one file: struct defs, descriptor defs, then downcall/upcall call sites. */
    static List<DescriptorUse> scanFile(String raw, String rel) {
        String masked = maskComments(raw);
        Map<String, String> structs = new LinkedHashMap<>();
        Map<String, String> descVars = new LinkedHashMap<>();

        Matcher sm = STRUCT_DEF.matcher(masked);
        while (sm.find()) {
            int open = masked.indexOf('(', sm.end() - 1);
            if (open < 0) break;
            int close = matchParen(masked, open);
            if (close < 0) break;
            structs.put(sm.group(1), masked.substring(open + 1, close));
        }

        Matcher dm = DESC_DEF.matcher(masked);
        while (dm.find()) {
            int open = masked.indexOf('(', dm.end() - 1);
            if (open < 0) break;
            int close = matchParen(masked, open);
            if (close < 0) break;
            String descText = masked.substring(dm.start(), close + 1);
            String sig = canonicalDescriptor(descText, structs);
            if (sig != null) descVars.put(dm.group(1), sig);
        }

        List<DescriptorUse> out = new ArrayList<>();
        findCallSites(masked, rel, DOWNCALL_CALL, Kind.DOWNCALL, descVars, out);
        findCallSites(masked, rel, UPCALL_CALL, Kind.UPCALL, descVars, out);
        return out;
    }

    private static void findCallSites(String masked, String rel, Pattern callPattern, Kind kind,
            Map<String, String> descVars, List<DescriptorUse> out) {
        Matcher m = callPattern.matcher(masked);
        while (m.find()) {
            int open = m.start() + m.group().length() - 1;
            int close = matchParen(masked, open);
            if (close < 0) break;
            String descText = resolveDescriptorArg(masked.substring(open + 1, close), descVars);
            if (descText == null) continue;
            String sig = isRawDescriptor(descText) ? canonicalDescriptor(descText, Map.of()) : descText;
            if (sig == null) continue;
            out.add(new DescriptorUse(kind, sig, rel, lineAt(masked, m.start())));
        }
    }

    /** Finds the FunctionDescriptor argument inside a downcallHandle/upcallStub argument list. */
    private static String resolveDescriptorArg(String body, Map<String, String> descVars) {
        String inline = extractDescriptorExpr(body);
        if (inline != null) return inline;
        for (String var : descVars.keySet()) {
            if (body.matches("(?s).*\\b" + Pattern.quote(var) + "\\b.*")) {
                return descVars.get(var);
            }
        }
        return null;
    }

    /** True if the resolved arg is a raw FunctionDescriptor.of(...) expression (else it's canonical). */
    private static boolean isRawDescriptor(String resolved) {
        return resolved != null && resolved.startsWith("FunctionDescriptor.");
    }

    private static String extractDescriptorExpr(String body) {
        Matcher m = DESC_CALL.matcher(body);
        if (!m.find()) return null;
        int open = m.start() + m.group().length() - 1;
        int close = matchParen(body, open);
        if (close < 0) return null;
        return body.substring(m.start(), close + 1);
    }

    /** Canonicalizes a FunctionDescriptor.of(...) expression into "RET(a,b,c)" with void for ofVoid. */
    static String canonicalDescriptor(String descText, Map<String, String> structs) {
        Matcher m = DESC_CALL.matcher(descText);
        if (!m.find()) return null;
        boolean isVoid = "ofVoid".equals(m.group(1));
        int open = descText.indexOf('(', m.start());
        int close = matchParen(descText, open);
        if (close < 0) return null;
        List<String> args = splitArgs(descText.substring(open + 1, close));
        if (args.isEmpty()) return isVoid ? "void()" : "?()";

        StringBuilder sb = new StringBuilder();
        if (isVoid) {
            sb.append("void");
        } else {
            sb.append(canonicalLayout(args.get(0), structs));
        }
        sb.append('(');
        for (int i = isVoid ? 0 : 1; i < args.size(); i++) {
            if (i > (isVoid ? 0 : 1)) sb.append(',');
            sb.append(canonicalLayout(args.get(i), structs));
        }
        sb.append(')');
        return sb.toString();
    }

    /** Canonicalizes a single layout expression to its structural type name. */
    static String canonicalLayout(String expr, Map<String, String> structs) {
        String t = WITH_NAME.matcher(expr.trim()).replaceAll("").trim();
        Matcher lm = LAYOUT_TYPE.matcher(t);
        if (lm.find()) {
            switch (lm.group(1)) {
                case "ADDRESS":      return "ADDRESS";
                case "JAVA_BYTE":    return "BYTE";
                case "JAVA_SHORT":   return "SHORT";
                case "JAVA_CHAR":    return "CHAR";
                case "JAVA_INT":     return "INT";
                case "JAVA_LONG":    return "LONG";
                case "JAVA_FLOAT":   return "FLOAT";
                case "JAVA_DOUBLE":  return "DOUBLE";
                case "JAVA_BOOLEAN": return "BOOL";
                default:             return lm.group(1);
            }
        }
        if (t.startsWith("MemoryLayout.structLayout(") || t.startsWith("StructLayout.structLayout(")) {
            int open = t.indexOf('(');
            int close = matchParen(t, open);
            if (close >= 0) {
                StringBuilder sb = new StringBuilder("struct{");
                List<String> fields = splitArgs(t.substring(open + 1, close));
                for (int i = 0; i < fields.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(canonicalLayout(fields.get(i), structs));
                }
                sb.append('}');
                return sb.toString();
            }
        }
        if (structs != null && structs.containsKey(t)) {
            StringBuilder sb = new StringBuilder("struct{");
            List<String> fields = splitArgs(structs.get(t));
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(canonicalLayout(fields.get(i), structs));
            }
            sb.append('}');
            return sb.toString();
        }
        return "?";
    }

    /** Splits a parenthesized argument body on top-level commas (nested parens respected). */
    static List<String> splitArgs(String body) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                args.add(body.substring(start, i).trim());
                start = i + 1;
            }
        }
        args.add(body.substring(start).trim());
        return args;
    }

    /** Returns the index of the paren matching the one at open (assumes '(' at open). */
    static int matchParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** Replaces comments with spaces (preserving line structure) so regexes don't match inside them. */
    static String maskComments(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        boolean inLine = false, inBlock = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            char next = i + 1 < raw.length() ? raw.charAt(i + 1) : 0;
            if (inLine) {
                if (c == '\n') { inLine = false; sb.append(c); }
                else sb.append(' ');
                continue;
            }
            if (inBlock) {
                if (c == '*' && next == '/') { inBlock = false; sb.append(' '); i++; }
                else if (c == '\n') sb.append(c);
                else sb.append(' ');
                continue;
            }
            if (c == '/' && next == '/') { inLine = true; sb.append("  "); i++; continue; }
            if (c == '/' && next == '*') { inBlock = true; sb.append("  "); i++; continue; }
            sb.append(c);
        }
        return sb.toString();
    }

    /** 1-based line number of the given char offset. */
    static int lineAt(String s, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < s.length(); i++) {
            if (s.charAt(i) == '\n') line++;
        }
        return line;
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
