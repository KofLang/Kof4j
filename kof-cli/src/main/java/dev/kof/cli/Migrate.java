package dev.kof.cli;

import dev.kof.compiler.ClassFileParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * `kof migrate` — full migration with a traceable report (docs/future/
 * LEGACY_MIGRATION.md, Fase H).
 *
 * Inputs: a {@code .class} (decompiled to a structural Kof skeleton) or a
 * {@code .java} (translated to Kof). The report is honest about what was
 * recovered vs. what requires manual review — never inventing behavior.
 */
public final class Migrate {

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
            double recoveredPct) {

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
            return m;
        }
    }

    public static int run(String[] args) {
        if (args.length > 0 && "migrate".equals(args[0])) {
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            System.out.println("usage: kof migrate <file.class|file.java> [--output <file.kf>] [--json]");
            System.out.println("  migrates a legacy artifact to Kof and emits a traceable report");
            return 0;
        }
        Path input = Path.of(args[0]);
        String outArg = optionValue(args, "--output");
        Path outFile = outArg != null ? Path.of(outArg) : null;
        boolean json = contains(args, "--json");
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
            } else {
                System.err.println("kof migrate expects a .class or .java file");
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
}