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
