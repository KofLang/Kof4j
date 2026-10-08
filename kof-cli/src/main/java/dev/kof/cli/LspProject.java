package dev.kof.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Varredura da árvore de arquivos do projeto para o LSP (X10 fatias 4–5,
 * extraída de LspServer no split ≤600 de 18/09): irmãos `.kf` e leitura
 * tolerante a falha. Estrutura-only; comportamento idêntico.
 */
final class LspProject {

    private LspProject() {}

    static Path toPath(String uri) {
        try {
            if (uri == null || !uri.startsWith("file:")) return null;
            return Path.of(java.net.URI.create(uri));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Nome do documento a partir do URI: o basename real das fontes canônicas
     * Kof (`.kf`/`.ks`/`.kof`/`.ptkf`) — a EXTENSÃO é a autoridade do perfil
     * (`LanguageProfile.forFileName`), então `.ptkf` precisa sobreviver intacto
     * para o `analyze` lexear PortuKof (F7). Senão (`LspMain.kf`, comportamento
     * histórico do arquivo-único KOF) para URIs não-Kof (ex.: `.md` roteado antes).
     */
    static String fileNameOf(String uri) {
        String path = uri.startsWith("file:") ? uri.substring("file:".length()) : uri;
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (slash >= 0) path = path.substring(slash + 1);
        String n = path.toLowerCase();
        if (n.endsWith(".kf") || n.endsWith(".ks") || n.endsWith(".kof") || n.endsWith(".ptkf"))
            return path;
        return "LspMain.kf";
    }

    /**
     * #636 (residual — o proprio issue: "the LSP never calls it"): as fontes
     * de deps INSTALADAS no cache (#566 opção b) entram na analise pela mesma
     * porta do `kof run --deps` (`DepsSources`: kofdeps da raiz; sem kofdeps
     * = lista vazia, nunca silencio inventado).
     */
    static List<Path> dependencySourceRoots(Path root) throws java.io.IOException {
        return DepsSources.roots(root);
    }

    /**
     * (#636) Raiz de projeto do arquivo: o ancestral com `kof.toml` (MESMO
     * criterio do `kof check` via ProjectLocator); sem manifesto, a raiz do
     * workspace do `initialize` que contenha o arquivo; nada disso = null e o
     * chamador mantem o modo arquivo-unico.
     */
    static Path projectRootOf(Path file, Path workspaceRoot) {
        Path abs = file.toAbsolutePath().normalize();
        Path dir = abs.getParent();
        for (int i = 0; i < 64 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve("kof.toml"))) return dir;
            dir = dir.getParent();
        }
        if (workspaceRoot != null) {
            Path root = workspaceRoot.toAbsolutePath().normalize();
            if (abs.startsWith(root)) return root;
        }
        return null;
    }

    /**
     * (#636) Espelha a arvore do projeto num diretorio temporario (estrutura
     * preservada; so os arquivos que o build ve — `kof.toml`, `.kf`, `.ks`)
     * com o BUFFER por cima do disco: a compilacao roda com a mesma raiz que
     * o CLI sem tocar em arquivo do usuario. Arvore ilegivel = espelho sem
     * irmaos (nunca crash, nunca diagnostico inventado — R6).
     */
    static Path mirror(Path real, String bufferText, Path root, Path mirrorDir)
            throws java.io.IOException {
        Path abs = real.toAbsolutePath().normalize();
        Path rel = root.relativize(abs);
        Path target = mirrorDir.resolve(rel.toString());
        String name = target.getFileName().toString();
        if (name.endsWith(".ks")) {
            target = target.resolveSibling(name.replace(".ks", ".kf"));
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, bufferText);
        try (var walk = Files.walk(root, 12)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String n = p.getFileName().toString().toLowerCase();
                if (!(n.endsWith(".kf") || n.endsWith(".ks") || n.endsWith(".kof")
                        || n.endsWith(".ptkf") || n.equals("kof.toml"))) return;
                if (p.toAbsolutePath().normalize().equals(abs)) return; // o buffer vence o disco
                Path dst = mirrorDir.resolve(
                        root.relativize(p.toAbsolutePath().normalize()).toString());
                try {
                    Files.createDirectories(dst.getParent());
                    Files.copy(p, dst);
                } catch (java.io.IOException unreadable) {
                    // irmao ilegivel fora do espelho — nao e erro do documento aberto
                }
            });
        } catch (java.io.IOException unreadableTree) {
            // raiz sem irmaos acessiveis: degrada para o comportamento antigo
        }
        return target;
    }

    /** Arquivos `.kf` irmãos na árvore do projeto (profundidade ≤6, ordenados). */
    static List<Path> siblings(Path self) {
        return siblings(self, null);
    }

    /**
     * Irmãos da árvore do arquivo + (8.3-B) irmãos sob a raiz do workspace do
     * `initialize` — deps fora do pai imediato (multi-módulo) entram na mesma
     * busca, ordenadas e deduplicadas; root nulo = comportamento antigo exato.
     */
    static List<Path> siblings(Path self, Path root) {
        java.util.LinkedHashSet<Path> out = new java.util.LinkedHashSet<>();
        walkInto(out, self);
        if (root != null) walkInto(out, root.resolve("__workspace-root__.kf"));
        out.removeIf(p -> p.toAbsolutePath().equals(self.toAbsolutePath()));
        return out.stream().sorted().toList();
    }

    private static void walkInto(java.util.Set<Path> out, Path base) {
        Path dir = base.getParent();
        if (dir == null) return;
        // Arvore de projeto NUNCA e o diretorio temporario nem a raiz do FS:
        // sob /tmp vivem scraps de outros jobs (medido 19/09: 485 .kf de
        // playgrounds alheios em /tmp) e um arquivo .kf solto la nao define
        // projeto. Nesses casos só o nivel imediato conta (profundidade 1).
        int depth = isScratchDir(dir) ? 1 : 6;
        try (var stream = Files.walk(dir, depth)) {
            stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".kf") || n.endsWith(".kof") || n.endsWith(".ptkf");
            }).forEach(out::add);
        } catch (Exception e) {
            // arvore ilegivel = nao contribui (nunca chute - R6)
        }
    }

    private static boolean isScratchDir(Path dir) {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        if (dir.equals(tmp)) return true;
        try {
            return dir.toRealPath().equals(tmp.toRealPath());
        } catch (Exception e) {
            return false;
        }
    }

    static String readOrNull(Path f) {
        try {
            return Files.readString(f, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * workspace/symbol (X10 fatia 6): símbolos dos buffers abertos + arquivos
     * .kf não-abertos da árvore (mesmo walker das fatias 4–5). Filtro
     * case-insensitive por substring; prefixo antes de substring (convenção
     * LSP); desempate por nome e uri.
     */
    @SuppressWarnings("unchecked")
    static java.util.List<Object> workspaceSymbols(
            java.util.Map<String, String> buffers, String query) {
        return workspaceSymbols(buffers, query, null);
    }

    static java.util.List<Object> workspaceSymbols(
            java.util.Map<String, String> buffers, String query, Path root) {
        String q = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        java.util.List<Object> out = new java.util.ArrayList<>();
        java.util.Set<Path> seen = new java.util.HashSet<>();
        for (java.util.Map.Entry<String, String> e : buffers.entrySet()) {
            Path openPath = toPath(e.getKey());
            if (openPath != null) seen.add(openPath.toAbsolutePath());
            collect(out, e.getKey(), e.getValue(), q);
        }
        for (Path open : new java.util.ArrayList<>(seen)) {
            for (Path f : siblings(open, root)) {
                if (!seen.add(f.toAbsolutePath())) continue;
                String txt = readOrNull(f);
                if (txt == null) continue;
                collect(out, f.toAbsolutePath().toUri().toString(), txt, q);
            }
        }
        out.sort(java.util.Comparator
                .comparingInt((Object o) -> rank((java.util.Map<String, Object>) o, q))
                .thenComparing(o -> String.valueOf(((java.util.Map<String, Object>) o).get("name")))
                .thenComparing(o -> {
                    java.util.Map<String, Object> loc =
                            (java.util.Map<String, Object>) ((java.util.Map<String, Object>) o).get("location");
                    return String.valueOf(loc.get("uri"));
                }));
        return out;
    }

    private static int rank(java.util.Map<String, Object> sym, String q) {
        String name = String.valueOf(sym.get("name")).toLowerCase(java.util.Locale.ROOT);
        return name.startsWith(q) ? 0 : 1;
    }

    private static void collect(java.util.List<Object> out, String uri, String text, String q) {
        dev.kof.compiler.lang.LanguageProfile p = dev.kof.compiler.lang.LanguageProfile
                .forFileName(fileNameOf(uri));
        for (LspSymbols.DocSymbol s : LspSymbols.documentSymbols(p, text)) {
            if (!s.name().toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
            java.util.Map<String, Object> sym = new java.util.LinkedHashMap<>();
            sym.put("name", s.name());
            sym.put("kind", (long) s.kind());
            java.util.Map<String, Object> loc = new java.util.LinkedHashMap<>();
            loc.put("uri", uri);
            loc.put("range", LspServer.rangeOf(text, s.start(), s.end()));
            sym.put("location", loc);
            out.add(sym);
        }
    }

    /**
     * Linha de declaração do nome (X10 fatia 7 — hover): buffer primeiro
     * (fonte da verdade), depois irmãos `.kf`. Retorna {linha, arquivo} ou
     * null (nunca chute — R6).
     */
    static String[] declarationLine(String uri, String bufferText, String word) {
        return declarationLine(uri, bufferText, word, null);
    }

    static String[] declarationLine(String uri, String bufferText, String word, Path root) {
        if (word == null || word.isEmpty()) return null;
        if (bufferText != null) {
            int[] r = LspSymbols.declarationRange(
                    dev.kof.compiler.lang.LanguageProfile.forFileName(fileNameOf(uri)), bufferText, word);
            if (r != null) return new String[]{ lineAt(bufferText, r[0]), nameOf(uri) };
        }
        Path self = toPath(uri);
        if (self == null) return null;
        for (Path f : siblings(self, root)) {
            String txt = readOrNull(f);
            if (txt == null) continue;
            int[] r = LspSymbols.declarationRange(
                    dev.kof.compiler.lang.LanguageProfile.forFileName(f.getFileName().toString()),
                    txt, word);
            if (r != null) return new String[]{ lineAt(txt, r[0]), f.getFileName().toString() };
        }
        return null;
    }

    private static String lineAt(String text, int offset) {
        int ls = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int e = text.indexOf('\n', offset);
        return text.substring(ls, e < 0 ? text.length() : e).strip();
    }

    private static String nameOf(String uri) {
        Path p = toPath(uri);
        return p == null ? "?" : p.getFileName().toString();
    }
}
