package dev.kof.cli;

import dev.kof.compiler.parser.ClassFileParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * `kof migrate` — full migration with a traceable report (docs/development/
 * LEGACY_MIGRATION.md, Fase H).
 *
 * Inputs: a {@code .class} (decompiled to a structural Kof skeleton), a
 * {@code .java} (translated to Kof), or a {@code .jar} (every class extracted
 * and decompiled to the output directory, with the §8.1 aggregate report).
 * The report is honest about what was recovered vs. what requires manual
 * review — never inventing behavior.
 */
public final class Migrate {

    /** The decompiler's marker line for a body it did NOT recover (Fase E). */
    static final String STUB_MARKER = "throw \"body not recovered\"";

    private Migrate() {
    }

    record MigrationReport(
            String input,
            String format,
            int classes,
            int fields,
            int methodSignatures,
            int methodBodiesRecovered,
            int methodBodiesNotRecovered,
            List<String> manualReview,
            double recoveredPct,
            int behavioralPassed,
            int behavioralDivergent,
            int behavioralNotComparable) {

        // two-arg convenience for the pre-behavioral flows (class/java)
        MigrationReport(String input, String format, int classes, int fields,
                        int methodSignatures, int methodBodiesRecovered,
                        int methodBodiesNotRecovered, List<String> manualReview,
                        double recoveredPct) {
            this(input, format, classes, fields, methodSignatures, methodBodiesRecovered,
                    methodBodiesNotRecovered, manualReview, recoveredPct, 0, 0, 0);
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("input", input);
            m.put("format", format);
            m.put("classes", classes);
            m.put("fields", fields);
            m.put("method_signatures", methodSignatures);
            m.put("method_bodies_recovered", methodBodiesRecovered);
            m.put("method_bodies_not_recovered", methodBodiesNotRecovered);
            m.put("manual_review", manualReview);
            m.put("recovered_pct", Math.round(recoveredPct * 1000) / 10.0);
            if (behavioralPassed + behavioralDivergent + behavioralNotComparable > 0) {
                Map<String, Object> beh = new LinkedHashMap<>();
                beh.put("passed", behavioralPassed);
                beh.put("divergent", behavioralDivergent);
                beh.put("not_comparable", behavioralNotComparable);
                m.put("behavioral_tests", beh);
            }
            return m;
        }
    }

    public static int run(String[] args) {
        if (args.length > 0 && "migrate".equals(args[0])) {
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            System.out.println("usage: kof migrate <file.class|file.java|app.jar> [--output <file.kf>|<dir>] [--compare] [--json]");
            System.out.println("  migrates a legacy artifact to Kof and emits a traceable report");
            return 0;
        }
        Path input = Path.of(args[0]);
        String outArg = optionValue(args, "--output");
        Path outFile = outArg != null ? Path.of(outArg) : null;
        Path outDir = outArg != null ? Path.of(outArg) : null;
        boolean json = contains(args, "--json");
        boolean compare = contains(args, "--compare");
        List<String> compareArgs = optionValues(args, "--arg");
        String compareStdin = optionValue(args, "--stdin");
        if (!Files.isRegularFile(input)) {
            System.err.println("file not found: " + input);
            return 1;
        }
        try {
            String kof;
            MigrationReport report;
            if (input.toString().endsWith(".class")) {
                kof = Decompile.decompile(input);
                report = reportClass(input, kof);
            } else if (input.toString().endsWith(".java")) {
                kof = Translate.translateJava(Files.readString(input));
                report = reportJava(input, kof);
            } else if (input.toString().endsWith(".jar")) {
                if (outDir == null) {
                    System.err.println("kof migrate <app.jar> exige --output <dir>");
                    return 1;
                }
                report = jarReport(input, outDir, compare, compareArgs, compareStdin);
                if (json) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("report", report.toMap());
                    out.put("output_dir", outDir.toString());
                    System.out.println(Json.stringify(out));
                } else {
                    print(report, outDir);
                }
                return 0;
            } else {
                System.err.println("kof migrate expects a .class, .java or .jar file");
                return 1;
            }

            if (outFile != null) {
                Files.writeString(outFile, kof);
            }

            if (json) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("report", report.toMap());
                out.put("output_file", outFile != null ? outFile.toString() : null);
                System.out.println(Json.stringify(out));
            } else {
                print(report, outFile);
            }
            return 0;
        } catch (IOException e) {
            System.err.println("kof migrate: " + e.getMessage());
            return 1;
        }
    }

    static MigrationReport reportClass(Path input, String kof) throws IOException {
        ClassFileParser.ClassFile ir = ClassFileParser.parse(Files.newInputStream(input));
        int fields = (int) ir.fields.stream().filter(f -> (f.accessFlags & 0x0008) == 0).count();
        int methods = (int) ir.methods.stream().filter(m -> !"<clinit>".equals(m.name)).count();
        int bodies = (int) ir.methods.stream()
                .filter(m -> m.code != null && !"<clinit>".equals(m.name) && !"<init>".equals(m.name))
                .count();
        List<String> review = ir.methods.stream()
                .filter(m -> m.code != null && !"<clinit>".equals(m.name) && !"<init>".equals(m.name))
                .map(m -> "method " + m.name + ": body not recovered")
                .toList();
        int signatures = methods;
        int unrecoverable = bodies;
        double pct = signatures + fields == 0
                ? 100.0
                : 100.0 * (signatures + fields) / (signatures + fields + unrecoverable);
        return new MigrationReport(input.getFileName().toString(), "class",
                1, fields, signatures, 0, unrecoverable, review, pct);
    }

    static MigrationReport reportJava(Path input, String kof) {
        int methods = countOccurrences(kof, "(") > 0 ? approximateMethods(kof) : 0;
        return new MigrationReport(input.getFileName().toString(), "java",
                1, 0, methods, methods, 0, List.of(), 100.0);
    }

    /**
     * Fase H slice 1 (§8.1): migrate a whole JAR. Every {@code .class} entry
     * is decompiled to a {@code .kf} file in the output directory (package
     * structure preserved) and the aggregate report is built from the
     * decompiler's ACTUAL output — a unit counts as not-recovered only when
     * its decompiled source contains the {@link #STUB_MARKER} line, never by
     * assumption.
     */
    static MigrationReport jarReport(Path jarPath, Path outDir, boolean compare) throws IOException {
        return jarReport(jarPath, outDir, compare, List.of(), null);
    }

    /** Fase H slice 3: the §8 differential test runs over the SAME INPUT VECTOR. */
    static MigrationReport jarReport(Path jarPath, Path outDir, boolean compare,
                                     List<String> compareArgs, String compareStdin) throws IOException {
        java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath.toFile());
        int classes = 0;
        int fields = 0;
        int signatures = 0;
        int notRecovered = 0;
        int behPassed = 0;
        int behDivergent = 0;
        int behNotComparable = 0;
        List<String> review = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            List<java.util.jar.JarEntry> classEntries = new ArrayList<>();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry e = entries.nextElement();
                if (!e.isDirectory() && e.getName().endsWith(".class")
                        && !e.getName().contains("module-info")
                        && !e.getName().contains("package-info")) {
                    classEntries.add(e);
                }
            }
            Files.createDirectories(outDir);
            Path outRoot = outDir.toAbsolutePath().normalize();
            for (java.util.jar.JarEntry e : classEntries) {
                String internalName = e.getName().replaceFirst("\\.class$", "");
                // CodeQL java/zipslip (#1131): the entry name comes from the jar (untrusted) —
                // "../x.class" would write outside --output. Honest skip, never a silent write.
                Path kfPath = outRoot.resolve(internalName + ".kf").normalize();
                if (!kfPath.startsWith(outRoot) || internalName.contains("\\")) {
                    warnings.add(e.getName() + ": entry escapes the output directory — skipped");
                    continue;
                }
                Path tmp = Files.createTempFile("migrate-", ".class");
                try (var in = jar.getInputStream(e)) {
                    Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                    String kof = Decompile.decompile(tmp);
                    // package structure preserved in the output dir
                    if (kfPath.getParent() != null) {
                        Files.createDirectories(kfPath.getParent());
                    }
                    Files.writeString(kfPath, kof);
                    classes++;
                    ClassFileParser.ClassFile ir = ClassFileParser.parse(Files.newInputStream(tmp));
                    fields += (int) ir.fields.stream().filter(f -> (f.accessFlags & 0x0008) == 0).count();
                    int methods = (int) ir.methods.stream()
                            .filter(m -> m.code != null && !"<clinit>".equals(m.name))
                            .count();
                    signatures += methods;
                    int stubs = countOccurrences(kof, STUB_MARKER);
                    notRecovered += stubs;
                    if (stubs > 0) {
                        review.add(internalName + ": " + stubs + " body(ies) not recovered");
                    }
                    if (compare) {
                        // §8.1 behavioral wiring: a class with a static main is
                        // behaviorally comparable (legacy bytecode vs compiled
                        // Kof, identical inputs); without a main there is no
                        // entry point to compare (honest not-comparable)
                        boolean hasMain = ir.methods.stream().anyMatch(m ->
                                "main".equals(m.name) && (m.accessFlags & 0x0008) != 0);
                        boolean defaultPackage = !internalName.contains("/");
                        if (hasMain && defaultPackage) {
                            // the legacy side needs the class file under its OWN
                            // name for `java -cp dir Name` to find it
                            Path legacyDir = Files.createTempDirectory("migrate-legacy-");
                            Path legacyClass = legacyDir.resolve(kfPath.getFileName().toString()
                                    .replaceFirst("\\.kf$", ".class"));
                            Files.copy(tmp, legacyClass, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            try {
                                String simple = internalName.substring(internalName.lastIndexOf('/') + 1);
                                Compare.RunResult legacyRes = Compare.runLegacy(legacyClass, compareArgs, compareStdin);
                                // the migrated class compiles into the DEFAULT
                                // package (the .kf has no package line) — the
                                // entry is the plain simple name
                                Compare.RunResult kofRes = Compare.runKof(kfPath, simple, compareArgs, compareStdin);
                                Compare.Verdict verdict = Compare.compare(legacyRes, kofRes);
                                if (verdict.equivalent()) {
                                    behPassed++;
                                } else {
                                    behDivergent++;
                                    review.add(internalName + ": BEHAVIORAL DIVERGENCE (legacy vs migrated)");
                                }
                            } finally {
                                Compare.cleanup(legacyDir);
                            }
                        } else {
                            behNotComparable++;
                        }
                    }
                } catch (RuntimeException ex) {
                    // a class the decompiler cannot parse at all is an honest
                    // warning in the report, never a silent drop
                    warnings.add(internalName + ": " + ex.getMessage());
                    classes++;
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
        } finally {
            jar.close();
        }
        int recovered = signatures - notRecovered;
        if (recovered < 0) recovered = 0;
        int units = signatures + fields;
        double pct = units == 0 ? 100.0 : 100.0 * (units - notRecovered) / units;
        MigrationReport report = new MigrationReport(jarPath.getFileName().toString(), "jar",
                classes, fields, signatures, recovered, notRecovered, review, pct,
                behPassed, behDivergent, behNotComparable);
        for (String w : warnings) {
            System.err.println("kof migrate warning: " + w);
        }
        return report;
    }

    private static int approximateMethods(String kof) {
        int count = 0;
        for (String line : kof.split("\n")) {
            String t = line.trim();
            if (t.startsWith("main(") || t.startsWith("constructor(") || t.matches("^[A-Z][a-zA-Z0-9]* [a-zA-Z_]+\\(")) {
                count++;
            }
        }
        return count;
    }

    private static int countOccurrences(String s, String sub) {
        int c = 0;
        for (int i = 0; (i = s.indexOf(sub, i)) != -1; i += sub.length()) c++;
        return c;
    }

    private static void print(MigrationReport r, Path out) {
        System.out.println("Kof Migration Report");
        System.out.println();
        System.out.println("Input:     " + r.input + "  (" + r.format + ")");
        System.out.println("Output:    " + (out != null ? out : "(stdout)"));
        System.out.println();
        System.out.println("Classes:                " + r.classes);
        System.out.println("Fields:                 " + r.fields);
        System.out.println("Method signatures:      " + r.methodSignatures);
        System.out.println("Bodies recovered:       " + r.methodBodiesRecovered);
        System.out.println("Bodies NOT recovered:   " + r.methodBodiesNotRecovered);
        System.out.println("Manual review:          " + r.manualReview.size());
        for (String item : r.manualReview) {
            System.out.println("  - " + item);
        }
        System.out.println();
        if (r.behavioralPassed() + r.behavioralDivergent() + r.behavioralNotComparable() > 0) {
            System.out.println("Behavioral tests:");
            System.out.println("    " + r.behavioralPassed() + " passed");
            System.out.println("    " + r.behavioralDivergent() + " divergent");
            if (r.behavioralNotComparable() > 0) {
                System.out.println("    " + r.behavioralNotComparable() + " not comparable (no main or packaged class)");
            }
            System.out.println();
        }
        System.out.println("Recovered (structural): " + Math.round(r.recoveredPct * 10) / 10.0 + "%");
    }

    private static boolean contains(String[] args, String s) {
        for (String a : args) if (s.equals(a)) return true;
        return false;
    }

    private static String optionValue(String[] args, String opt) {
        for (int i = 0; i < args.length - 1; i++) {
            if (opt.equals(args[i])) return args[i + 1];
        }
        return null;
    }

    /** Repeatable option: every `--arg <v>` occurrence contributes one value. */
    private static List<String> optionValues(String[] args, String opt) {
        List<String> vals = new ArrayList<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (opt.equals(args[i])) vals.add(args[i + 1]);
        }
        return vals;
    }
}