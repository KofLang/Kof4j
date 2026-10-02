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
            System.err.println("usage: kof profile <file.kf> [--target jvm|native|js] [--methods] [args...]");
            return 1;
        }
        Path file = Path.of(args[0]);
        if (!Files.isRegularFile(file)) {
            System.err.println("file not found: " + file);
            return 1;
        }
        Target target = Target.JVM;
        boolean methods = false;
        int argStart = 1;
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--target=")) {
                target = parseTarget(args[i].substring("--target=".length()));
                argStart = i + 1;
            } else if (args[i].equals("--target") && i + 1 < args.length) {
                target = parseTarget(args[i + 1]);
                argStart = i + 2;
                i++;
            } else if (args[i].equals("--methods")) {
                methods = true;
                argStart = i + 1;
            }
        }
        if (methods && target == Target.NATIVE) {
            // R6/R7 honest gap: method-level sampling on native needs `perf record`, whose
            // kernel perf_event access is gated by perf_event_paranoid; on hosts where the
            // sysctl forbids it there is no in-house substitute — never a silent no-op.
            System.err.println("kof profile --methods: method-level sampling on native needs perf"
                    + " (perf record); /proc/sys/kernel/perf_event_paranoid must allow it"
                    + perfParanoidNote());
            return 1;
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
            // --methods needs the JVM LineNumberTable so the JFR sample maps back to the
            // .kf source line; without it the profile would show bytecode positions only.
            driver.setDebugInfoEnabled(methods);
            CompilationResult result = driver.compile(file, outDir, target);
            for (Diagnostic d : result.diagnostics().getDiagnostics()) System.err.println(d.format());
            if (!result.success()) return 1;

            Map<String, Object> report = profile(target, outDir, args, argStart, methods);
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

    private static Map<String, Object> profile(Target target, Path outDir, String[] args, int argStart,
            boolean methods) throws IOException, InterruptedException {
        Map<String, Object> report = new LinkedHashMap<>();
        long start = System.nanoTime();

        if (target == Target.JS) {
            Path entry = findJsEntry(outDir);
            if (entry == null) {
                System.err.println("kof profile: no JS entry point");
                return null;
            }
            if (methods) {
                // In-house method-level sampling for the JS face: the emitted module runs
                // under the Node CPU profiler (`--cpu-prof`, part of Node — no external
                // tool), and the `.mjs.map` source map maps the sampled JS line back to
                // the `.kf` source line, the JS counterpart of the JVM LineNumberTable.
                List<String> programArgs = new ArrayList<>();
                for (int i = argStart; i < args.length; i++) programArgs.add(args[i]);
                Map<String, Object> jsReport = JsMethodProfile.sample(entry, outDir, programArgs);
                if (jsReport == null) return null;
                report.putAll(jsReport);
                report.put("wall_ms", (System.nanoTime() - start) / 1_000_000);
                return report;
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
            command.addAll(KofStdio.capturedJvmFlags());
            command.add("-Xlog:gc:" + gcLog);
            if (methods) {
                // In-house method-level sampling: the JVM's own JFR records
                // `jdk.ExecutionSample` stack traces (settings=profile), dumped on exit.
                command.add("-XX:StartFlightRecording=filename=" + outDir.resolve("profile.jfr")
                        + ",settings=profile,dumponexit=true");
            }
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
        report.put("wall_ms", wallMs);
        if (canMeasure) {
            parseTimeVerbose(text, report);
        }
        if (target == Target.JVM) {
            parseGcLog(outDir.resolve("gc.log"), report);
            if (methods) {
                parseMethodSamples(outDir.resolve("profile.jfr"), report);
            }
        }
        return report;
    }

    /**
     * A hot method from a method-level sampling run: the reported symbol
     * (JVM class/method or JS function) and the Kof source line it maps to.
     */
    record MethodSample(String method, int samples, int line) {
    }

    /**
     * Method-level profile from the child JVM's JFR recording: aggregate
     * `jdk.ExecutionSample` by top frame. The line number is the `.kf` line —
     * the compiler's LineNumberTable maps the bytecode back to the Kof source,
     * so the user sees the hot Kof function, never raw bytecode.
     */
    private static void parseMethodSamples(Path jfrFile, Map<String, Object> report) {
        if (!Files.isRegularFile(jfrFile)) {
            report.put("methods_unavailable", "no JFR recording produced (is this a JVM with JFR?)");
            return;
        }
        Map<String, Integer> byMethod = new LinkedHashMap<>();
        Map<String, Integer> lineByMethod = new LinkedHashMap<>();
        int total = 0;
        try (jdk.jfr.consumer.RecordingFile rf = new jdk.jfr.consumer.RecordingFile(jfrFile)) {
            while (rf.hasMoreEvents()) {
                jdk.jfr.consumer.RecordedEvent event = rf.readEvent();
                if (!"jdk.ExecutionSample".equals(event.getEventType().getName())) continue;
                jdk.jfr.consumer.RecordedStackTrace stack = event.getStackTrace();
                if (stack == null) continue;
                List<jdk.jfr.consumer.RecordedFrame> frames = stack.getFrames();
                if (frames.isEmpty()) continue;
                jdk.jfr.consumer.RecordedFrame top = frames.get(0);
                String type = top.getMethod().getType().getName();
                if (type.startsWith("jdk.jfr.internal")) continue; // the sampler's own overhead
                String method = type + "." + top.getMethod().getName();
                byMethod.merge(method, 1, Integer::sum);
                // keep the first POSITIVE line: a safepoint sample can carry -1
                lineByMethod.merge(method, top.getLineNumber(), (old, now) -> old > 0 ? old : now);
                total++;
            }
        } catch (IOException e) {
            report.put("methods_unavailable", "JFR recording could not be read: " + e.getMessage());
            return;
        }
        if (total == 0) {
            report.put("methods_unavailable", "the program was too short for JFR to take a sample");
            return;
        }
        List<MethodSample> top = byMethod.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> new MethodSample(e.getKey(), e.getValue(),
                        lineByMethod.getOrDefault(e.getKey(), -1)))
                .toList();
        report.put("samples_total", total);
        report.put("methods", top);
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
        if (report.containsKey("methods")) {
            String kind = "js".equals(report.get("target")) ? "Node samples" : "JFR samples";
            System.out.println();
            System.out.println("  hot methods (" + report.get("samples_total")
                    + " " + kind + ", top " + ((List<?>) report.get("methods")).size() + "):");
            for (Object o : (List<?>) report.get("methods")) {
                MethodSample m = (MethodSample) o;
                String line = m.line() > 0 ? "  (line " + m.line() + ")" : "";
                System.out.printf("    %6d  %s%s%n", m.samples(), m.method(), line);
            }
        } else if (report.containsKey("methods_unavailable")) {
            System.out.println();
            System.out.println("  hot methods:    unavailable — " + report.get("methods_unavailable"));
        }
        System.out.println();
        if (report.containsKey("methods")) {
            String source = "js".equals(report.get("target"))
                    ? "the Node CPU profiler (--cpu-prof, part of Node; no external tool)"
                    : "the JVM's own JFR (in-house; no external tool)";
            System.out.println("method-level data from " + source);
        } else {
            System.out.println("jvm:      profile with --methods for JFR method-level data");
            System.out.println("native:   run under perf stat for cycle/instruction counts");
            System.out.println("js:       profile with --methods for Node CPU-profiler method-level data");
        }
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

    /**
     * The measured `perf_event_paranoid` level, when the host exposes it: an
     * actionable note instead of a generic "use perf" that the user cannot act on.
     */
    private static String perfParanoidNote() {
        Path paranoid = Path.of("/proc/sys/kernel/perf_event_paranoid");
        if (!Files.isRegularFile(paranoid)) return "";
        try {
            String level = Files.readString(paranoid).trim();
            return " (measured on this host: perf_event_paranoid=" + level
                    + "; levels above 2 forbid unprivileged sampling — lower it or run as root)";
        } catch (IOException e) {
            return "";
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