package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.IRStatistics;
import dev.kof.compiler.KofVersion;
import dev.kof.compiler.Target;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * `kof inspect` — IR and code statistics (docs/performance.md §34).
 *
 * Compiles the program and reports, per method, the IR operation count
 * before and after optimization (showing what the optimizer eliminated),
 * plus module totals and emitted class sizes.
 * 
 * Also supports inspecting compiled .class files (docs/future/LEGACY_IR.md).
 */
public final class Inspect {

    private Inspect() {
    }

    public static int run(String[] args) {
        if (args.length > 0 && "inspect".equals(args[0])) {
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length == 0) {
            System.err.println("usage: kof inspect <file.kf|file.class> [--json]");
            return 1;
        }
        Path file = Path.of(args[0]);
        boolean jsonOut = java.util.Arrays.asList(args).contains("--json");
        if (!Files.isRegularFile(file)) {
            System.err.println("file not found: " + file);
            return 1;
        }

        // Check if we're inspecting a .class file (legacy IR)
        if (file.toString().endsWith(".class")) {
            return inspectClassFile(file, jsonOut);
        }

        final IRStatistics[] stats = new IRStatistics[1];
        CompilerDriver driver = new CompilerDriver();
        driver.setIRObserver(statistics -> stats[0] = statistics);

        Path outDir;
        try {
            outDir = Files.createTempDirectory("kof-inspect-");
        } catch (IOException e) {
            System.err.println("kof inspect: " + e.getMessage());
            return 1;
        }
        try {
            CompilationResult result = driver.compile(file, outDir, Target.JVM);
            if (!result.success()) {
                for (Diagnostic d : result.diagnostics().getDiagnostics()) {
                    System.err.println(d.format());
                }
                return 1;
            }
            if (stats[0] == null) {
                System.err.println("kof inspect: IR not available");
                return 1;
            }

            IRStatistics statistics = stats[0];
            List<IRStatistics.MethodStat> methods = statistics.methods();
            long totalBefore = statistics.opsBefore();
            long totalAfter = statistics.opsAfter();
            long removed = statistics.opsRemoved();
            long classBytes = emittedClassBytes(outDir);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("file", file.getFileName().toString());
            summary.put("version", KofVersion.version());
            summary.put("classes", statistics.classes());
            summary.put("ops_before", totalBefore);
            summary.put("ops_after", totalAfter);
            summary.put("ops_removed", removed);
            summary.put("reduction_pct", statistics.reductionPct());
            summary.put("class_bytes", classBytes);

            if (jsonOut) {
                List<Map<String, Object>> rows = new java.util.ArrayList<>();
                for (IRStatistics.MethodStat m : methods) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("class", m.className());
                    row.put("method", m.methodName());
                    row.put("ops_before", m.opsBefore());
                    row.put("ops_after", m.opsAfter());
                    row.put("reduction_pct", m.reductionPct());
                    rows.add(row);
                }
                summary.put("methods", rows);
                System.out.println(Json.stringify(summary));
            } else {
                System.out.println();
                System.out.println("IR after optimization (" + KofVersion.version() + "):");
                System.out.printf("  %-28s %12s %12s %10s%n", "class.method", "ops before", "ops after", "reduction");
                System.out.println("  " + "-".repeat(68));
                methods.stream().sorted(Comparator.comparing(IRStatistics.MethodStat::className)
                                .thenComparing(IRStatistics.MethodStat::methodName))
                        .forEach(m -> System.out.printf("  %-28s %12d %12d %9d%%%n",
                                m.className() + "." + m.methodName(), m.opsBefore(), m.opsAfter(), m.reductionPct()));
                System.out.println("  " + "-".repeat(68));
                System.out.printf("  %-28s %12d %12d %9d%%%n", "TOTAL", totalBefore, totalAfter,
                        statistics.reductionPct());
                System.out.println();
                System.out.println("classes: " + statistics.classes()
                        + " | bytecode: " + classBytes + " bytes"
                        + " | ops removed by optimizer: " + removed);
            }
            return 0;
        } catch (Exception e) {
            System.err.println("kof inspect: " + e);
            return 1;
        } finally {
            cleanup(outDir);
        }
    }

    private static long emittedClassBytes(Path outDir) {
        long total = 0;
        try (var s = Files.walk(outDir)) {
            for (Path p : s.filter(p -> p.toString().endsWith(".class")
                    && !p.toString().contains("dev/kof/runtime")).toList()) {
                total += Files.size(p);
            }
        } catch (IOException ignored) {
        }
        return total;
    }

    private static int inspectClassFile(Path classFile, boolean jsonOut) {
        try {
            var ir = dev.kof.compiler.ClassFileParser.parse(Files.newInputStream(classFile));
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("file", classFile.getFileName().toString());
            result.put("magic", "0x" + String.format("%08X", ir.magic));
            result.put("version", ir.minorVersion + "." + ir.majorVersion);
            result.put("this_class", ir.thisClass);
            result.put("super_class", ir.superClass);
            result.put("interfaces", java.util.Arrays.asList(ir.interfaces));
            
            List<Map<String, Object>> fields = new java.util.ArrayList<>();
            for (var f : ir.fields) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", f.name);
                row.put("type", f.descriptor);
                fields.add(row);
            }
            result.put("fields", fields);
            
            List<Map<String, Object>> methods = new java.util.ArrayList<>();
            for (var m : ir.methods) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", m.name);
                row.put("descriptor", m.descriptor);
                row.put("code_size", m.code != null ? m.code.bytecode.length : 0);
                methods.add(row);
            }
            result.put("methods", methods);
            
            if (jsonOut) {
                System.out.println(Json.stringify(result));
            } else {
                System.out.println("\nClass file: " + classFile.getFileName());
                System.out.println("  Magic: 0x" + String.format("%08X", ir.magic));
                System.out.println("  Version: " + ir.minorVersion + "." + ir.majorVersion);
                System.out.println("  ThisClass: " + ir.thisClass);
                System.out.println("  SuperClass: " + ir.superClass);
                System.out.println("  Interfaces: " + java.util.Arrays.toString(ir.interfaces));
                System.out.println("  Fields (" + ir.fields.size() + "):");
                for (var f : ir.fields) {
                    System.out.println("    - " + f.name + " : " + f.descriptor);
                }
                System.out.println("  Methods (" + ir.methods.size() + "):");
                for (var m : ir.methods) {
                    System.out.println("    - " + m.name + m.descriptor);
                    if (m.code != null) {
                        System.out.println("        bytecode: " + m.code.bytecode.length + " bytes");
                    }
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("kof inspect class: " + e.getMessage());
            return 1;
        }
    }

    private static void cleanup(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}