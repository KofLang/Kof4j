package dev.kof.cli;

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
 * `kof bench` — baseline (carregar/gravar/JSON), relatório em tabela e
 * parsing da saída de /usr/bin/time. Extraído de Bench (REFACTOR-500 Fase 8)
 * — SRP: só saída + baseline; a orquestração fica em Bench.
 */
final class BenchBaseline {

    BenchBaseline() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> loadBaseline(Path file) {
        try {
            Object parsed = Json.parse(Files.readString(file));
            if (parsed instanceof Map<?, ?> m) return (Map<String, Object>) m;
        } catch (Exception e) {
            System.err.println("kof bench: cannot read baseline " + file + ": " + e.getMessage());
        }
        return null;
    }

    static void writeBaseline(Path file, Target target, int iterations,
                              Map<String, Object> results) {
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("tool", "kof bench");
        baseline.put("version", KofVersion.version());
        baseline.put("target", target.name().toLowerCase());
        baseline.put("iterations", iterations);
        baseline.put("host", hostName());
        Map<String, Object> ms = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : results.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> row && row.get("validated") == Boolean.TRUE) {
                ms.put(e.getKey(), row.get("ms"));
            }
        }
        baseline.put("results", ms);
        try {
            Files.createDirectories(file.getParent() != null ? file.getParent() : Path.of("."));
            Files.writeString(file, Json.stringify(baseline) + "\n");
            System.out.println("baseline written to " + file);
        } catch (IOException e) {
            System.err.println("kof bench: cannot write baseline: " + e.getMessage());
        }
    }

    static Map<String, Object> report(Target target, int iterations,
                                      Map<String, Object> results, Path baselineFile) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("tool", "kof bench");
        report.put("version", KofVersion.version());
        report.put("target", target.name().toLowerCase());
        report.put("iterations", iterations);
        report.put("host", hostName());
        report.put("results", results);
        if (baselineFile != null) report.put("baseline", baselineFile.toString());
        return report;
    }

    static void printTable(Target target, List<Map<String, Object>> rows) {
        System.out.println();
        System.out.printf("%-34s %8s %10s %8s %9s %s%n", "benchmark", "ms", "rss_kb", "cpu_ms", "ratio", "status");
        System.out.println("--------------------------------------------------------------------");
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("name");
            Object ms = row.get("ms");
            Object rss = row.get("rss_kb");
            Object cpu = row.get("cpu_ms");
            Object ratio = row.get("ratio");
            String status = row.get("status") != null ? (String) row.get("status") : "ok";
            if (row.get("regression") == Boolean.TRUE) status = "REGRESSION";
            System.out.printf("%-34s %8d %10s %8s %9s %s%n",
                    name, ms instanceof Number n ? n.longValue() : 0,
                    rss instanceof Number n ? n.longValue() : "-",
                    cpu instanceof Number n ? n.longValue() : "-",
                    ratio instanceof Number n ? String.format("%.2f", n.doubleValue()) : "-",
                    status);
        }
        System.out.println("--------------------------------------------------------------------");
        System.out.println("target: " + target.name().toLowerCase() + " | version: " + KofVersion.version());
    }

    static String hostName() {
        String os = System.getProperty("os.name", "?");
        String arch = System.getProperty("os.arch", "?");
        return os + "/" + arch;
    }

    /** Normaliza quebras de linha e remove trailing whitespace (validação de output). */
    static String normalize(String text) {
        return text.replace("\r\n", "\n").stripTrailing();
    }

    // ── Parsing da saída de /usr/bin/time -v ─────────────────────────

    static long parseTimeSeconds(String timeOutput, String prefix) {
        for (String line : timeOutput.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                String[] parts = trimmed.split(":");
                if (parts.length == 2) {
                    try {
                        return Math.round(Double.parseDouble(parts[1].trim().split(" ")[0]) * 1_000_000);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return 0;
    }

    static long parseRss(String timeOutput) {
        for (String line : timeOutput.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Maximum resident set size")) {
                String[] parts = trimmed.split(":");
                if (parts.length == 2) {
                    try {
                        return Long.parseLong(parts[1].trim().split(" ")[0]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return 0;
    }

    static String stripTimeOutput(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.contains("Maximum resident set size") || line.contains("Command being timed")
                    || line.startsWith("\t")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    static void cleanup(Path dir) {
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
