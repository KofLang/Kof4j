package dev.kof.cli;

/**
 * Escopo de resolução da árvore (`kof decompile <dir>`, §7 degrau 3).
 *
 * <p>Um por ARQUIVO decompilado (compartilhado pelos frames dos métodos —
 * {@code BytecodeFrame} é por-método; sem estático global, sem vazamento
 * entre arquivos/testes). Modo 1-arquivo: frames nascem com escopo null =
 * comportamento idêntico ao anterior.
 *
 * <p>Regra única (R6): um nome de domínio só é emitido quando resolve sem
 * ambiguidade — MESMO pacote (sem import) ou OUTRO pacote com
 * {@code import} emitido (degrau 3) e simples GLOBALMENTE único no índice.
 * Simples duplicado em 2+ pacotes (ex.: dois `Util`) → recusa (stub), mesmo
 * que o pacote atual tivesse um — conservador, nunca errado. Imports
 * registrados mas não usados (corpo que virou stub depois) são inofensivos:
 * import resolvido e não-usado compila limpo (probe PKG006: só import
 * INEXISTENTE é erro).
 */
final class TreeScope {

    private final java.util.Map<String, String> pkgOf;       // internalName → pacote
    private final java.util.Map<String, Integer> simpleCount; // simples → nº de pacotes distintos
    private final String currentPkg;
    private final java.util.Set<String> usedImports = new java.util.TreeSet<>(); // dotted, p/ emissão

    TreeScope(java.util.Map<String, String> pkgOf, String currentPkg) {
        this.pkgOf = pkgOf;
        this.currentPkg = currentPkg;
        var counts = new java.util.TreeMap<String, java.util.Set<String>>();
        for (var e : pkgOf.entrySet()) {
            String s = e.getKey();
            int slash = s.lastIndexOf('/');
            String simple = slash >= 0 ? s.substring(slash + 1) : s;
            counts.computeIfAbsent(simple, k -> new java.util.TreeSet<>()).add(e.getValue());
        }
        var n = new java.util.TreeMap<String, Integer>();
        for (var e : counts.entrySet()) n.put(e.getKey(), e.getValue().size());
        this.simpleCount = n;
    }

    /**
     * Resolve internalName → nome simples Kof, ou null (recusar → stub).
     * Emite o uso p/ import quando cross-package (só nomes que passam aqui
     * são emitidos, então todo `import` gerado foi usado de verdade — e
     * import não-usado compila limpo de todo jeito).
     */
    String resolve(String internal) {
        if (internal == null) return null;
        String pkg = pkgOf.get(internal);
        if (pkg == null) return null;
        int slash = internal.lastIndexOf('/');
        String simple = slash >= 0 ? internal.substring(slash + 1) : internal;
        if (!simple.matches("[A-Za-z_][A-Za-z0-9_]*")) return null;   // `Outer$Inner`, arrays
        if (simpleCount.getOrDefault(simple, 0) != 1) return null;     // ambíguo global: stub
        if (!pkg.equals(currentPkg)) {
            // import de/para default package não tem forma `a.b.C` — recusar
            // (só mesmo-pacote-"" resolve, sem import, como no degrau 1).
            if (pkg.isEmpty() || currentPkg.isEmpty()) return null;
            usedImports.add(pkg + "." + simple);
        }
        return simple;
    }

    /** Imports dotted (`p.B`) usados, ordenados, p/ emissão após o package. */
    java.util.Set<String> usedImports() {
        return java.util.Collections.unmodifiableSet(usedImports);
    }
}
