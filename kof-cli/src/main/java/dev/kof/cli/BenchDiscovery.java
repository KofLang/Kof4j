package dev.kof.cli;

import dev.kof.compiler.Target;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * `kof bench` — descoberta de benchmarks (docs/performance.md §19-§25).
 *
 * Cada benchmark é um diretório com `Main.kf`, `expected.txt` e opcional
 * `meta.json` ({ "targets": [...], "iterations": N }). Extraído de Bench
 * (REFACTOR-500 Fase 8) — SRP: só descoberta + spec.
 */
final class BenchDiscovery {

    BenchDiscovery() {
    }

    static final class BenchmarkSpec {
        final String name;
        final Path dir;
        final int iterations;
        final List<String> targets;

        BenchmarkSpec(String name, Path dir, int iterations, List<String> targets) {
            this.name = name;
            this.dir = dir;
            this.iterations = iterations;
            this.targets = targets;
        }

        boolean supports(Target t) {
            return targets.isEmpty() || targets.contains(t.name().toLowerCase());
        }
    }

    static List<BenchmarkSpec> discover(List<Path> roots) {
        List<BenchmarkSpec> specs = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                if (Files.isRegularFile(root) && root.getFileName().toString().equals("Main.kf")) {
                    specs.add(specFromDir(root.getParent()));
                }
                continue;
            }
            try (var stream = Files.walk(root)) {
                stream.filter(p -> p.getFileName() != null && p.getFileName().toString().equals("Main.kf"))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(p -> specs.add(specFromDir(p.getParent())));
            } catch (IOException e) {
                System.err.println("kof bench: cannot read " + root + ": " + e.getMessage());
            }
        }
        return specs;
    }

    private static BenchmarkSpec specFromDir(Path dir) {
        String name = dir.getFileName().toString();
        Path parent = dir.getParent();
        if (parent != null && parent.getFileName() != null) {
            name = parent.getFileName() + "/" + name;
        }
        int iterations = 0;
        List<String> targets = List.of();
        Path meta = dir.resolve("meta.json");
        if (Files.isRegularFile(meta)) {
            try {
                Object parsed = Json.parse(Files.readString(meta));
                if (parsed instanceof Map<?, ?> m) {
                    if (m.get("iterations") instanceof Number n) iterations = n.intValue();
                    if (m.get("targets") instanceof List<?> list) {
                        targets = list.stream().map(String::valueOf).map(String::toLowerCase).toList();
                    }
                }
            } catch (Exception e) {
                System.err.println("kof bench: bad meta.json in " + dir + ": " + e.getMessage());
            }
        }
        return new BenchmarkSpec(name, dir, iterations, targets);
    }

    static Target parseTarget(String value) {
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
}
