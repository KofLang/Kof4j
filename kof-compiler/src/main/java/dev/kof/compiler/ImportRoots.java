package dev.kof.compiler;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * §1203 (23/11, app do Énois): a raiz de import nunca é o próprio diretório de
 * uma fonte de ENTRADA. Num layout `r/app/Main.kf` + `r/enois/*.kf` o LCA dos
 * pais é `r/enois` (o diretório do irmão) e todo `import enois.X` morria em
 * PKG006; num layout `r/sub/Main.kf` + `r/enois/*.kf` (o harness que instala as
 * libs numa subpasta) é o mesmo caso via a busca nas bibliotecas. A regra sobe
 * a raiz enquanto um diretório cujo nome é um identificador Kof (pode ser
 * pacote) contiver uma fonte de entrada.
 */
final class ImportRoots {

    private ImportRoots() {}

    /**
     * §1203: a raiz é o diretório da entrada e ele é um PACOTE de verdade —
     * sem fontes próprias e sem subpasta com fontes (o `app/` só com o
     * `Main.kf`, ao lado do irmão `enois/`). O diretório do módulo
     * (`Main.kf` + `enois/`, `src/` + `app/`) fica como raiz.
     */
    static boolean structuralEntryDirectory(List<Path> entrySources, Path root) {
        if (!shadowsEntryPackage(entrySources, root)) return false;
        return !hasSubdirectoryWithSources(root, 2);
    }

    /** Alguma subpasta de {@code dir} (até {@code depth}) tem uma fonte Kof? */
    private static boolean hasSubdirectoryWithSources(Path dir, int depth) {
        if (depth <= 0) return false;
        try (var files = java.nio.file.Files.list(dir)) {
            for (Path f : files.toList()) {
                if (!java.nio.file.Files.isDirectory(f)) continue;
                try (var inner = java.nio.file.Files.list(f)) {
                    for (Path g : inner.toList()) {
                        if (java.nio.file.Files.isRegularFile(g)
                                && CompilerImports.isKofExtSource(g.getFileName().toString())) {
                            return true;
                        }
                    }
                }
                if (hasSubdirectoryWithSources(f, depth - 1)) return true;
            }
        } catch (java.io.IOException e) {
            return false;
        }
        return false;
    }

    /**
     * O diretório da entrada (pai das fontes) é um diretório de pacote e a raiz
     * o contém — a raiz de import sobe um nível. As fontes de entrada NÃO são
     * biblioteca: o caller passa as bibliotecas à parte.
     */
    static boolean shadowsEntryPackage(List<Path> entrySources, Path root) {
        if (root == null) return false;
        Path absRoot = root.toAbsolutePath().normalize();
        for (Path s : entrySources) {
            Path parent = s.toAbsolutePath().normalize().getParent();
            if (parent == null || !parent.startsWith(absRoot)) continue;
            Path name = parent.getFileName();
            if (name != null && ModuleRoots.isPackageSegment(name.toString())) return true;
        }
        return false;
    }
}
