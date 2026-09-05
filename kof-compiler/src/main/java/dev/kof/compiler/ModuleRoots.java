package dev.kof.compiler;

import java.nio.file.Path;
import java.util.List;

/**
 * Helpers de Path para resolução de módulo/package (puros, sem estado).
 */
final class ModuleRoots {

    private ModuleRoots() {}

    static Path moduleRootFor(List<Path> sources) {
        if (sources.isEmpty()) return null;
        java.util.List<Path> parents = new java.util.ArrayList<>();
        for (Path s : sources) {
            Path p = s.toAbsolutePath().normalize().getParent();
            if (p != null) parents.add(p);
        }
        if (parents.isEmpty()) return null;
        Path lca = parents.get(0);
        for (int i = 1; i < parents.size(); i++) lca = commonAncestor(lca, parents.get(i));
        return lca;
    }

    static Path commonAncestor(Path a, Path b) {
        int n = Math.min(a.getNameCount(), b.getNameCount());
        int i = 0;
        while (i < n && a.getName(i).toString().equals(b.getName(i).toString())) i++;
        if (i == 0) return a.getRoot() != null ? a.getRoot() : Path.of(".");
        Path sub = a.subpath(0, i);
        return a.getRoot() != null ? a.getRoot().resolve(sub) : sub;
    }

    static String derivedPackageOf(Path src, Path rootAbs) {
        Path abs = src.toAbsolutePath().normalize();
        if (rootAbs == null || !abs.startsWith(rootAbs)) return "";
        Path parent = rootAbs.relativize(abs).getParent();
        if (parent == null || parent.toString().isEmpty()) return "";
        return parent.toString().replace(java.io.File.separatorChar, '.');
    }
}