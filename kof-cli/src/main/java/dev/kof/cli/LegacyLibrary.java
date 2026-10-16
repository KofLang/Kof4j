package dev.kof.cli;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Fonte de verdade unica da lib Kof `legacy` (KofBOL) para o CLI (rule 12: a
 * politica vive na lib Kof pura; aqui so mecanismo — o mesmo padrao do
 * KofmdLibrary). Ordem: (1) distribuicao instalada
 * `$kof.install.dir/lib/kof-libs/legacy`; (2) arvore do repositorio
 * `libs/legacy` andando ate a raiz (modo dev/teste). Sem fonte = null → o
 * chamador emite o gap honesto (R6).
 */
final class LegacyLibrary {

    private static final List<String> PARTS = List.of("Legacy", "Copybook");

    private LegacyLibrary() {}

    /** Copia o pacote para `destRoot/legacy/*.kf`; retorna false se nao ha fonte. */
    static boolean installTo(Path destRoot) throws IOException {
        Path dir = resolveDirectory();
        Path target = destRoot.resolve("legacy");
        Files.createDirectories(target);
        if (dir != null) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path kf : files.filter(f -> f.toString().endsWith(".kf")).toList()) {
                    Files.copy(kf, target.resolve(kf.getFileName().toString()),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return true;
        }
        return false;
    }

    /** Compila o main gerado com a lib `legacy` instalada (ou null). */
    static URLClassLoader compile(String sourceName, String mainSource, Path srcRoot, Path outRoot)
            throws Exception {
        Path installRoot = Files.createTempDirectory("kof-legacy-");
        String previous = System.getProperty("kof.install.dir");
        try {
            if (!installTo(installRoot.resolve("lib").resolve("kof-libs"))) {
                return null;
            }
            Path main = srcRoot.resolve(sourceName);
            Files.writeString(main, mainSource);
            System.setProperty("kof.install.dir", installRoot.toString());
            var result = new dev.kof.compiler.CompilerDriver().compile(
                    main, outRoot, dev.kof.compiler.Target.JVM);
            if (!result.success()) {
                return null;
            }
            return new URLClassLoader(new URL[]{outRoot.toUri().toURL()},
                    LegacyLibrary.class.getClassLoader());
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
            deleteTree(installRoot);
        }
    }

    private static Path resolveDirectory() {
        Path install = Path.of(System.getProperty("kof.install.dir", ""),
                "lib", "kof-libs", "legacy");
        if (hasParts(install)) {
            return install;
        }
        Path working = Path.of("").toAbsolutePath().normalize();
        for (Path base : List.of(working, working.getParent(),
                working.getParent() == null ? working : working.getParent().getParent())) {
            Path candidate = base.resolve("libs").resolve("legacy");
            if (hasParts(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasParts(Path dir) {
        return Files.isRegularFile(dir.resolve("Legacy.kf"));
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort temp cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort temp cleanup
        }
    }
}
