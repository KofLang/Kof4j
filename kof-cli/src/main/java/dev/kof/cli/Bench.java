package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.Target;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * `kof bench` — benchmark harness (docs/performance.md §19-§25, §34).
 *
 * Each benchmark is a directory containing `Main.kf`, `expected.txt` and an
 * optional `meta.json` ({ "targets": [...], "iterations": N }). The harness:
 *
 *   compile → run → validate output → collect metrics → compare baseline
 *
 * Metrics: median wall time over N runs; peak RSS (Linux, via /usr/bin/time).
 * A baseline file (JSON) can be compared against; regressions above the
 * threshold ratio are reported and can fail the run (CI gate, §26).
 *
 * Divisão (REFACTOR-500 Fase 8): descoberta/spec em {@link BenchDiscovery},
 * execução por iteração em {@link BenchRunners}, baseline/relatório/
 * time-parsing em {@link BenchBaseline}. Aqui fica só a orquestração.
 */
public final class Bench {

    private static final double DEFAULT_THRESHOLD = 1.20;

    private Bench() {
    }

    public static int run(String[] args) {
        if (args.length > 0 && "bench".equals(args[0])) {
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        List<Path> roots = new ArrayList<>();
        Target target = Target.JVM;
        int iterations = 3;
        int warmup = 1;
        Path baselineFile = null;
        Path updateBaseline = null;
        boolean jsonOut = false;
        boolean failOnRegression = false;
        boolean verbose = false;
        double threshold = DEFAULT_THRESHOLD;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--target" -> {
                    if (i + 1 < args.length) {
                        target = BenchDiscovery.parseTarget(args[++i]);
                    }
                }
                case "--iterations" -> {
                    if (i + 1 < args.length) {
                        iterations = Integer.parseInt(args[++i]);
                    }
                }
                case "--warmup" -> {
                    if (i + 1 < args.length) {
                        warmup = Integer.parseInt(args[++i]);
                    }
                }
                case "--quick" -> {
                    iterations = 1;
                    warmup = 0;
                }
                case "--baseline" -> {
                    if (i + 1 < args.length) {
                        baselineFile = Path.of(args[++i]);
                    }
                }
                case "--update-baseline" -> {
                    if (i + 1 < args.length) {
                        updateBaseline = Path.of(args[++i]);
                    }
                }
                case "--threshold" -> {
                    if (i + 1 < args.length) {
                        threshold = Double.parseDouble(args[++i]);
                    }
                }
                case "--json" -> jsonOut = true;
                case "--fail-on-regression" -> failOnRegression = true;
                case "--verbose" -> verbose = true;
                default -> {
                    if (arg.startsWith("--target=")) {
                        target = BenchDiscovery.parseTarget(arg.substring("--target=".length()));
                    } else if (arg.startsWith("--iterations=")) {
                        iterations = Integer.parseInt(arg.substring("--iterations=".length()));
                    } else if (arg.startsWith("--warmup=")) {
                        warmup = Integer.parseInt(arg.substring("--warmup=".length()));
                    } else if (arg.startsWith("--baseline=")) {
                        baselineFile = Path.of(arg.substring("--baseline=".length()));
                    } else if (arg.startsWith("--update-baseline=")) {
                        updateBaseline = Path.of(arg.substring("--update-baseline=".length()));
                    } else if (arg.startsWith("--threshold=")) {
                        threshold = Double.parseDouble(arg.substring("--threshold=".length()));
                    } else {
                        roots.add(Path.of(arg));
                    }
                }
            }
        }
        if (roots.isEmpty()) roots.add(Path.of("benchmarks"));
        if (iterations < 1) iterations = 1;

        List<BenchDiscovery.BenchmarkSpec> specs = BenchDiscovery.discover(roots);
        if (specs.isEmpty()) {
            System.err.println("kof bench: no benchmarks found");
            return 1;
        }

        Map<String, Object> baseline = baselineFile != null ? BenchBaseline.loadBaseline(baselineFile) : null;
        Map<String, Object> results = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean anyRegression = false;
        boolean anyFailure = false;

        for (BenchDiscovery.BenchmarkSpec spec : specs) {
            if (!spec.supports(target)) {
                if (verbose) System.out.println("skip   " + spec.name + " (not supported on " + target + ")");
                continue;
            }
            if (verbose) System.out.println("run    " + spec.name + " (" + target + ")");
            Map<String, Object> row = benchmark(spec, target, iterations, warmup, verbose);
            if (row == null) {
                anyFailure = true;
                continue;
            }
            if (baseline != null) {
                Object baseMs = ((Map<?, ?>) baseline.getOrDefault("results", Map.of())).get(spec.name);
                if (baseMs instanceof Number baseNum && baseNum.doubleValue() > 0) {
                    double ratio = ((Number) row.get("ms")).doubleValue() / baseNum.doubleValue();
                    row.put("ratio", ratio);
                    row.put("baseline_ms", baseNum.doubleValue());
                    // Relative + absolute guard: small benchmarks are noise-prone,
                    // so a regression must also exceed a 10ms absolute delta.
                    double delta = ((Number) row.get("ms")).doubleValue() - baseNum.doubleValue();
                    if (ratio > threshold && delta > 10.0) {
                        row.put("regression", true);
                        anyRegression = true;
                    }
                }
            }
            results.put(spec.name, row);
            row.put("name", spec.name);
            rows.add(row);
        }

        if (updateBaseline != null) {
            BenchBaseline.writeBaseline(updateBaseline, target, iterations, results);
        }

        if (jsonOut) {
            System.out.println(Json.stringify(BenchBaseline.report(target, iterations, results, baselineFile)));
        } else {
            BenchBaseline.printTable(target, rows);
        }

        if (anyRegression) {
            if (!jsonOut) {
                System.out.println("PERFORMANCE REGRESSION");
            }
            return failOnRegression ? 1 : 0;
        }
        return anyFailure ? 1 : 0;
    }

    // ── Single benchmark run ─────────────────────────────────────────

    private static Map<String, Object> benchmark(BenchDiscovery.BenchmarkSpec spec, Target target,
                                                 int defaultIterations, int warmup, boolean verbose) {
        int iterations = spec.iterations > 0 ? spec.iterations : defaultIterations;
        Path outDir;
        try {
            outDir = Files.createTempDirectory("kof-bench-");
        } catch (IOException e) {
            System.err.println("kof bench: " + spec.name + ": " + e.getMessage());
            return null;
        }
        try {
            CompilerDriver driver = new CompilerDriver();
            driver.setDebugInfoEnabled(false);
            Path source = spec.dir.resolve("Main.kf");
            long compileStart = System.nanoTime();
            CompilationResult result = driver.compile(source, outDir, target);
            long compileMs = (System.nanoTime() - compileStart) / 1_000_000;
            if (!result.success()) {
                for (Diagnostic d : result.diagnostics().getDiagnostics()) {
                    System.err.println("kof bench: " + spec.name + ": " + d.format());
                }
                return null;
            }

            String expected = expectedOutput(spec.dir);
            if (expected == null) {
                System.err.println("kof bench: " + spec.name + ": expected.txt missing");
                return null;
            }

            // Warmup run: discarded from metrics, but still validated.
            if (warmup > 0) {
                BenchRunners.RunResult w = BenchRunners.runOnce(target, outDir, spec, verbose);
                if (w == null) {
                    System.err.println("kof bench: " + spec.name + ": warmup run failed");
                    return null;
                }
                if (!w.output.equals(expected)) {
                    System.err.println("kof bench: " + spec.name + ": output mismatch"
                            + "\n  expected: " + quote(expected)
                            + "\n  actual:   " + quote(w.output));
                    return null;
                }
            }

            List<Long> times = new ArrayList<>();
            long rssKb = 0;
            long cpuMicros = 0;
            boolean validated = true;
            for (int i = 0; i < iterations; i++) {
                BenchRunners.RunResult rr = BenchRunners.runOnce(target, outDir, spec, verbose);
                if (rr == null) {
                    validated = false;
                    break;
                }
                times.add(rr.wallNanos);
                rssKb = Math.max(rssKb, rr.rssKb);
                cpuMicros += rr.userMicros + rr.systemMicros;
                if (!rr.output.equals(expected)) {
                    validated = false;
                    System.err.println("kof bench: " + spec.name + ": output mismatch"
                            + "\n  expected: " + quote(expected)
                            + "\n  actual:   " + quote(rr.output));
                    break;
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ms", median(times));
            if (rssKb > 0) row.put("rss_kb", rssKb);
            if (cpuMicros > 0) row.put("cpu_ms", cpuMicros / 1_000);
            row.put("compile_ms", compileMs);
            row.put("validated", validated);
            if (!validated) row.put("status", "FAILED");
            return row;
        } catch (Exception e) {
            System.err.println("kof bench: " + spec.name + ": " + e);
            return null;
        } finally {
            BenchBaseline.cleanup(outDir);
        }
    }

    // ── Output handling ──────────────────────────────────────────────

    private static String expectedOutput(Path dir) {
        Path expected = dir.resolve("expected.txt");
        if (!Files.isRegularFile(expected)) return null;
        try {
            return BenchBaseline.normalize(Files.readString(expected));
        } catch (IOException e) {
            return null;
        }
    }

    private static String quote(String s) {
        return s.isEmpty() ? "(empty)" : "\"" + s + "\"";
    }

    private static long median(List<Long> values) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(mid);
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2;
    }
}
