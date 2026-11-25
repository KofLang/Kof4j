package dev.kof.compiler.nat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * #1191: a resolução dos dirs de libs DISTRIBUÍDAS do módulo — a peça própria
 * do gate ≤500. Um `extern "libX.so"` por NOME SIMPLES (sem `/`) procura o
 * arquivo AQUI (a raiz do módulo, o irmão `libs/` dela, o dir do fonte —
 * extraído de {@link NativeBackend} antes de a classe cruzar 600); nada achado
 * mantém o `-l:` soname do sistema (o padrão SQLite).
 */
public final class ModuleLibResolver {

    public ModuleLibResolver() {}

    /** the dirs the pipeline populated (the module root, its libs/, the source dir). */
    private final List<Path> moduleLibraryDirs = new ArrayList<>();

    public void setDirs(List<Path> dirs) {
        moduleLibraryDirs.clear();
        moduleLibraryDirs.addAll(dirs);
    }

    List<Path> dirs() {
        return moduleLibraryDirs;
    }

    /** Resolves the bare-name libs against the module dirs (in place on {@code ffiLibs}). */
    void resolve(Collection<String> ffiLibs) {
        if (moduleLibraryDirs.isEmpty()) return;
        List<String> resolved = new ArrayList<>();
        for (String lib : ffiLibs) {
            String hit = null;
            if (lib.indexOf('/') < 0) {
                for (Path dir : moduleLibraryDirs) {
                    Path candidate = dir.resolve(lib);
                    if (Files.isRegularFile(candidate)) { hit = candidate.toString(); break; }
                }
            }
            resolved.add(hit != null ? hit : lib);
        }
        ffiLibs.clear();
        ffiLibs.addAll(resolved);
    }
}
