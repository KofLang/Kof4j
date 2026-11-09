package dev.kof.compiler;

import java.nio.file.Path;
import java.util.List;

/**
 * Helpers de Path para resolução de módulo/package (puros, sem estado).
 */
public final class ModuleRoots {

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
        Path rel = rootAbs.relativize(abs);
        // Fase 1: kof run src/Main.kf em projeto com kof.toml — a fonte
        // entrada fica na RAIZ e o pacote dela é "" (o main da aplicação).
        // Subdiretórios (src/controllers/...) continuam pacote = caminho.
        if (rel.getNameCount() <= 1) return "";
        Path parent = rel.getParent();
        if (parent == null || parent.toString().isEmpty()) return "";
        // GitHub #999: um diretório cujo nome NÃO é um identificador Kof
        // (`.agent`, `.hidden-x`) não pode ser um pacote. Sem esta guarda o
        // pacote derivado vira `.agent` e o nome interno JVM `/agent/Box`
        // (absoluto → outputDir.resolve descarta o diretório de saída →
        // EACCES mascarado de COMP001) ou `sub//agent/Box` (barra dupla →
        // ClassFormatError no load, um falso-verde do `kof check`). O
        // contrato (modules.md §1/§2) manda: sem pacote válido → pacote
        // default "". Qualquer segmento inválido torna o caminho inteiro
        // não-representável como pacote, então o arquivo cai no default.
        for (Path seg : parent) {
            if (!isPackageSegment(seg.toString())) return "";
        }
        return parent.toString().replace(java.io.File.separatorChar, '.');
    }

    /**
     * Um segmento de diretório é um pacote válido quando é um identificador
     * Kof (mesma regra do lexer: letra/`_`/`$` no início, depois
     * letra/dígito/`_`/`$`). Vazio, iniciado por ponto ou com hífen não é.
     */
    static boolean isPackageSegment(String seg) {
        if (seg == null || seg.isEmpty()) return false;
        char first = seg.charAt(0);
        if (!(Character.isLetter(first) || first == '_' || first == '$')) return false;
        for (int i = 1; i < seg.length(); i++) {
            char c = seg.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '$')) return false;
        }
        return true;
    }
}