package dev.kof.cli;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Fonte de verdade unica da lib Kof pura do Interop Core (`libs/interop`) para o
 * CLI (rule 12 / D-KOF-FIRST-IMPL: a politica — render do manifest (§8) e
 * validacao — vive na lib Kof pura; aqui so o mecanismo de terminal). Mesmo
 * padrao de resolucao do {@link KofmdLibrary}: (1) distribuicao instalada
 * `$kof.install.dir/lib/kof-libs/<lib>` (o `scripts/package.sh` copia a arvore
 * `libs/` inteira); (2) arvore do repositorio `libs/<lib>` andando ate a raiz
 * (modo dev/teste). Sem fonte = null -> o chamador emite CONNECTOR001 honesto
 * (R6).
 */
final class InteropLibrary {

    /** As libs que o Core do Interop precisa: o descritor + o leitor TOML que ele importa. */
    private static final List<String> LIBS = List.of("interop", "file");

    private InteropLibrary() {}

    /** Copia cada lib para `destRoot/<lib>/*.kf`; false se faltar alguma fonte. */
    static boolean installTo(Path destRoot) throws IOException {
        for (String lib : LIBS) {
            if (!installOne(lib, destRoot.resolve(lib))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Instala as libs, compila o `mainSource` e devolve o loader vivo (ou null
     * quando a fonte nao existe ou a compilacao falha). O `kof.install.dir` e
     * restaurado ao final.
     */
    static URLClassLoader compile(String sourceName, String mainSource, Path srcRoot, Path outRoot)
            throws Exception {
        Path installRoot = Files.createTempDirectory("interop-engine-");
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
                    InteropLibrary.class.getClassLoader());
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
            deleteTree(installRoot);
        }
    }

    private static boolean installOne(String lib, Path target) throws IOException {
        Path dir = resolveDirectory(lib);
        if (dir == null) {
            return false;
        }
        Files.createDirectories(target);
        try (Stream<Path> files = Files.list(dir)) {
            for (Path kf : files.filter(f -> f.toString().endsWith(".kf")).toList()) {
                Files.copy(kf, target.resolve(kf.getFileName().toString()),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return true;
    }

    private static Path resolveDirectory(String lib) {
        Path install = Path.of(System.getProperty("kof.install.dir", ""),
                "lib", "kof-libs", lib);
        if (Files.isDirectory(install)) {
            return install;
        }
        Path working = Path.of("").toAbsolutePath().normalize();
        for (Path base : List.of(working, working.getParent(),
                working.getParent() == null ? working : working.getParent().getParent())) {
            Path candidate = base.resolve("libs").resolve(lib);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> s = Files.walk(root)) {
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
