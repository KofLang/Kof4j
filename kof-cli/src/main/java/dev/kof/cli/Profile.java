package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.KofVersion;
import dev.kof.compiler.Target;
import dev.kof.runtime.KofJsRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * `kof profile` — run a program and report execution metrics
 * (docs/architecture/performance.md §34).
 *
 * JVM: wall time, user/sys CPU, peak RSS, GC pauses (via -Xlog:gc).
 * Native: wall time, user/sys CPU, peak RSS; `perf stat` when available.
 * JS: in-process wall time.
 *
 * The goal is to discover WHY the program is slow, not just that it is.
 */
public final class Profile {

    private Profile() {
    }

    public static int run(String[] args) {
        if (args.length > 0 && "profile".equals(args[0])) {
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length < 1) {
            System.err.println("usage: kof profile <file.kf> [--target jvm|native|js] [args...]");
            return 1;
        }
        Path file = Path.of(args[0]);
        if (!Files.isRegularFile(file)) {
            System.err.println("file not found: " + file);
            return 1;
        }
        Target target = Target.JVM;
        int argStart = 1;
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--target=")) {
                target = parseTarget(args[i].substring("--target=".length()));
                argStart = i + 1;
            } else if (args[i].equals("--target") && i + 1 < args.length) {
                target = parseTarget(args[i + 1]);
                argStart = i + 2;
                i++;
            }
        }

        Path outDir;
        try {
            outDir = Files.createTempDirectory("kof-profile-");
        } catch (IOException e) {
            System.err.println("kof profile: " + e.getMessage());
            return 1;
        }
        try {
            CompilerDriver driver = new CompilerDriver();
            driver.setDebugInfoEnabled(false);
            CompilationResult result = driver.compile(file, outDir, target);
            for (Diagnostic d : result.diagnostics().getDiagnostics()) System.err.println(d.format());
            if (!result.success()) return 1;

            Map<String, Object> report = profile(target, outDir, args, argStart);
            if (report == null) return 1;
            report.put("file", file.getFileName().toString());
            report.put("target", target.name().toLowerCase());
            report.put("version", KofVersion.version());
            printReport(report);
            return 0;
        } catch (Exception e) {
            System.err.println("kof profile: " + e);
            return 1;
        } finally {
            cleanup(outDir);
        }
    }

    private static Map<String, Object> profile(Target target, Path outDir, String[] args, int argStart)
            throws IOException, InterruptedException {
        Map<String, Object> report = new LinkedHashMap<>();
        long start = System.nanoTime();

        if (target == Target.JS) {
            Path entry = findJsEntry(outDir);
            if (entry == null) {
                System.err.println("kof profile: no JS entry point");
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int ec = KofJsRunner.run(entry, out, InputStream.nullInputStream(), err);
            long wallMs = (System.nanoTime() - start) / 1_000_000;
            if (ec != 0) {
                System.err.print(err.toString(StandardCharsets.UTF_8));
                return null;
            }
            report.put("wall_ms", wallMs);
            return report;
        }

        List<String> command = new ArrayList<>();
        Path timeBin = Path.of("/usr/bin/time");
        boolean canMeasure = Files.isExecutable(timeBin)
                && System.getProperty("os.name", "").toLowerCase().contains("linux");
        if (canMeasure) {
            command.add("/usr/bin/time");
            command.add("-v");
        }
        if (target == Target.JVM) {
            Path gcLog = outDir.resolve("gc.log");
            command.add(System.getProperty("java.home") + "/bin/java");
            command.add("-Xlog:gc:" + gcLog);
            command.add("-cp");
            command.add(outDir.toString());
            String mainClass = findMainClass(outDir);
            if (mainClass == null) {
                System.err.println("kof profile: no main class found");
                return null;
            }
            command.add(mainClass);
        } else {
            Path bin = outDir.resolve("Default/Main");
            if (!Files.isExecutable(bin)) {
                System.err.println("kof profile: binary not found");
                return null;
            }
            command.add(bin.toString());
        }
        for (int i = argStart; i < args.length; i++) command.add(args[i]);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        byte[] buf = p.getInputStream().readAllBytes();
        int ec = p.waitFor();
        long wallMs = (System.nanoTime() - start) / 1_000_000;
        String text = new String(buf, StandardCharsets.UTF_8);
        if (ec != 0) {
            System.err.println("kof profile: exited with " + ec);
            System.err.print(text);
            return null;
        }
        if (canMeasure) {
            report.put("wall_ms", wallMs);
            parseTimeVerbose(text, report);
        }
        if (target == Target.JVM) {
            parseGcLog(outDir.resolve("gc.log"), report);
        }
        return report;
    }

    private static void parseTimeVerbose(String text, Map<String, Object> report) {
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.startsWith("User time")) report.put("user_s", parseDoubleAfterColon(t));
            else if (t.startsWith("System time")) report.put("system_s", parseDoubleAfterColon(t));
            else if (t.startsWith("Percent of CPU")) report.put("cpu_pct", parseDoubleAfterColon(t));
            else if (t.startsWith("Maximum resident set size")) {
                String v = t.substring(t.indexOf(':') + 1).trim();
                try {
                    report.put("rss_kb", Long.parseLong(v.split(" ")[0]));
                } catch (NumberFormatException ignored) {
                }
            } else if (t.startsWith("Minor page faults")) report.put("minor_faults", parseDoubleAfterColon(t));
            else if (t.startsWith("Major page faults")) report.put("major_faults", parseDoubleAfterColon(t));
            else if (t.startsWith("Voluntary context switches")) report.put("ctx_switches", parseDoubleAfterColon(t));
        }
    }

    private static double parseDoubleAfterColon(String line) {
        String v = line.substring(line.indexOf(':') + 1).trim().split(" ")[0];
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void parseGcLog(Path gcLog, Map<String, Object> report) {
        if (!Files.isRegularFile(gcLog)) return;
        try {
            long pauses = 0;
            double totalPauseMs = 0;
            double maxPauseMs = 0;
            for (String line : Files.readAllLines(gcLog)) {
                if (line.contains("Pause")) {
                    pauses++;
                    int idx = line.indexOf("ms");
                    if (idx > 0) {
                        String num = line.substring(line.lastIndexOf(',', idx - 1) + 1, idx).trim();
                        try {
                            double pause = Double.parseDouble(num);
                            totalPauseMs += pause;
                            maxPauseMs = Math.max(maxPauseMs, pause);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            if (pauses > 0) {
                report.put("gc_pauses", pauses);
                report.put("gc_pause_total_ms", Math.round(totalPauseMs));
                report.put("gc_pause_max_ms", maxPauseMs);
            }
        } catch (IOException ignored) {
        }
    }

    private static void printReport(Map<String, Object> report) {
        System.out.println();
        System.out.println("kof profile — " + report.get("file") + " (" + report.get("target")
                + ", " + report.get("version") + ")");
        System.out.println("  wall:           " + report.get("wall_ms") + " ms");
        if (report.containsKey("user_s")) {
            double user = (Double) report.get("user_s");
            double sys = (Double) report.get("system_s");
            System.out.printf("  cpu (user/sys): %.2f / %.2f s (%.0f%% of wall)%n",
                    user, sys, report.get("cpu_pct"));
        }
        if (report.containsKey("rss_kb")) {
            System.out.println("  peak rss:       " + report.get("rss_kb") + " kB");
        }
        if (report.containsKey("gc_pauses")) {
            System.out.println("  gc:             " + report.get("gc_pauses") + " pauses, "
                    + report.get("gc_pause_total_ms") + " ms total, max "
                    + String.format("%.1f", report.get("gc_pause_max_ms")) + " ms");
        }
        if (report.containsKey("minor_faults")) {
            System.out.println("  faults:         " + Math.round((Double) report.get("minor_faults"))
                    + " minor / " + Math.round((Double) report.get("major_faults")) + " major");
        }
        if (report.containsKey("ctx_switches")) {
            System.out.println("  ctx switches:   " + Math.round((Double) report.get("ctx_switches")));
        }
        System.out.println();
        System.out.println("jvm:      profile with JFR/async-profiler for method-level data");
        System.out.println("native:   run under perf stat for cycle/instruction counts");
        System.out.println("js:       profile with Node/V8 DevTools when running the emitted module");
    }

    private static String findMainClass(Path dir) {
        try (var s = Files.walk(dir)) {
            List<String> candidates = s.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> dir.relativize(p).toString()
                            .replace(".class", "").replace("/", ".").replace("\\", "."))
                    .toList();
            for (String c : candidates) {
                if (c.endsWith(".Main") || c.equals("Main")) return c;
            }
            return candidates.isEmpty() ? null : candidates.get(candidates.size() - 1);
        } catch (IOException e) {
            return null;
        }
    }

    private static Path findJsEntry(Path dir) {
        Path defaultEntry = dir.resolve("Default.mjs");
        if (Files.exists(defaultEntry)) return defaultEntry;
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .filter(p -> !p.toString().contains("kof-runtime"))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static Target parseTarget(String value) {
        return switch (value) {
            case "jvm" -> Target.JVM;
            case "native" -> Target.NATIVE;
            case "js" -> Target.JS;
            default -> {
                System.err.println("unknown target: " + value);
                System.exit(1);
                yield Target.JVM;
            }
        };
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