package dev.kof.cli;

import dev.kof.compiler.Target;
import dev.kof.runtime.KofJsRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * `kof bench` — execução de uma iteração: sobe o processo (ou o engine JS
 * embutido), captura wall time / RSS / CPU via /usr/bin/time quando
 * disponível. Extraído de Bench (REFACTOR-500 Fase 8) — SRP: só a run
 * individual + construção de comando por target.
 */
final class BenchRunners {

    BenchRunners() {
    }

    static final class RunResult {
        final long wallNanos;
        final long rssKb;
        final String output;
        final long userMicros;
        final long systemMicros;

        RunResult(long wallNanos, long rssKb, String output) {
            this(wallNanos, rssKb, output, 0, 0);
        }

        RunResult(long wallNanos, long rssKb, String output, long userMicros, long systemMicros) {
            this.wallNanos = wallNanos;
            this.rssKb = rssKb;
            this.output = output;
            this.userMicros = userMicros;
            this.systemMicros = systemMicros;
        }
    }

    static RunResult runOnce(Target target, Path outDir, BenchDiscovery.BenchmarkSpec spec, boolean verbose)
            throws IOException, InterruptedException {
        long start = System.nanoTime();
        String output;
        long rssKb = 0;
        long userMicros = 0;
        long systemMicros = 0;
        if (target == Target.JS) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            Path entry = findJsEntry(outDir);
            if (entry == null) {
                System.err.println("kof bench: " + spec.name + ": no JS entry point");
                return null;
            }
            int ec = KofJsRunner.run(entry, buf, InputStream.nullInputStream(), new ByteArrayOutputStream());
            output = buf.toString(StandardCharsets.UTF_8);
            if (ec != 0) {
                System.err.println("kof bench: " + spec.name + ": JS exited with " + ec);
                return null;
            }
        } else {
            List<String> command = commandFor(target, outDir);
            if (command == null) {
                System.err.println("kof bench: " + spec.name + ": cannot build command for " + target);
                return null;
            }
            List<String> effective = command;
            Path timeBin = Path.of("/usr/bin/time");
            boolean canMeasureRss = Files.isExecutable(timeBin)
                    && System.getProperty("os.name", "").toLowerCase().contains("linux");
            if (canMeasureRss) {
                effective = new ArrayList<>(List.of("/usr/bin/time", "-v"));
                effective.addAll(command);
            }
            ProcessBuilder pb = new ProcessBuilder(effective);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            byte[] buf = p.getInputStream().readAllBytes();
            int ec = p.waitFor();
            String text = new String(buf, StandardCharsets.UTF_8);
            if (canMeasureRss) {
                rssKb = BenchBaseline.parseRss(text);
                userMicros = BenchBaseline.parseTimeSeconds(text, "User time");
                systemMicros = BenchBaseline.parseTimeSeconds(text, "System time");
                output = BenchBaseline.stripTimeOutput(text);
            } else {
                output = text;
            }
            if (ec != 0) {
                System.err.println("kof bench: " + spec.name + ": exited with " + ec
                        + (verbose ? ": " + text : ""));
                return null;
            }
        }
        long wallNanos = System.nanoTime() - start;
        return new RunResult(wallNanos, rssKb, BenchBaseline.normalize(output), userMicros, systemMicros);
    }

    private static List<String> commandFor(Target target, Path outDir) {
        if (target == Target.JVM) {
            String className = findMainClass(outDir);
            if (className == null) return null;
            List<String> cmd = new ArrayList<>();
            cmd.add(System.getProperty("java.home") + "/bin/java");
            cmd.add("-cp");
            cmd.add(outDir.toString());
            cmd.add(className);
            return cmd;
        }
        if (target == Target.NATIVE) {
            Path bin = outDir.resolve("Default/Main");
            if (!Files.isExecutable(bin)) return null;
            return List.of(bin.toString());
        }
        return null;
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
}
