package dev.kof.cli;

import dev.kof.compiler.lang.LanguageProfile;
import dev.kof.compiler.lang.SurfaceNames;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LSP-A (DECISIONS.md §D-POLL-19, 19/09): rename <b>cross-file</b> do projeto
 * — o WorkspaceEdit toca o buffer aberto e os {@code .kf} irmãos na MESMA
 * convenção textual dos {@code references} (varredura por fronteiras de
 * palavra; sem índice tipado não desambigua homônimos entre arquivos —
 * limitação documentada em {@code docs/tooling/LSP.md}, honestidade
 * "primeiro hit", nunca posição que não foi lida).
 *
 * <p>Guardas honestas (R6): palavra-vazia, nome inválido, palavra-chave Kof
 * e namespace da stdlib devolvem {@code null} — rename de keyword/namespace
 * nunca edita nada.
 *
 * <p>F7.2 (07/10): o RENAME é do SÍMBOLO DO USUÁRIO, nunca do alias. Uma
 * palavra da superfície com alias oficial (keyword/builtin/namespace) é
 * recusada — renomeá-la seria renomear a ponte canônica, não o programa. O
 * identificador do usuário (`minhaConta`) renomeia como no Kof. Sem índice
 * tipado, um alias de método (`tamanho`) não é "renomeado" textualmente — a
 * recusa é a resposta honesta (§18). Kof = guarda histórica byte-a-byte.
 */
final class LspRename {

    private LspRename() {}

    /** WorkspaceEdit (documentChanges) ou null se o rename não é aplicável. */
    static Map<String, Object> workspaceEdit(String uri, String text, String word,
                                             String newName, Map<String, String> openBuffers,
                                             Path self, Path root) {
        return workspaceEdit(LanguageProfile.KOF, uri, text, word, newName, openBuffers, self, root);
    }

    static Map<String, Object> workspaceEdit(LanguageProfile p, String uri, String text, String word,
                                             String newName, Map<String, String> openBuffers,
                                             Path self, Path root) {
        if (word.isEmpty() || !isValidIdentifier(newName)) return null;
        if (isReserved(p, word)) return null;
        List<Object> docChanges = new ArrayList<>();
        List<Object> own = edits(text, word, newName);
        if (!own.isEmpty()) {
            docChanges.add(docEdit(uri, own));
        }
        if (self != null) {
            for (Path f : LspProject.siblings(self, root)) {
                String furi = f.toAbsolutePath().toUri().toString();
                String txt = openBuffers.getOrDefault(furi, LspProject.readOrNull(f));
                if (txt == null) continue;
                List<Object> e = edits(txt, word, newName);
                if (!e.isEmpty()) docChanges.add(docEdit(furi, e));
            }
        }
        if (docChanges.isEmpty()) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentChanges", docChanges);
        return result;
    }

    private static boolean isReserved(LanguageProfile p, String word) {
        if (dev.kof.compiler.StdCatalog.isNamespace(SurfaceNames.canonicalNamespace(p, word))) return true;
        for (String[] k : LspHover.KEYWORDS) {
            if (k[0].equals(word)) return true;
        }
        // F7.2: uma PALAVRA da superfície (keyword/builtin/namespace/entrada
        // PT) tem alias oficial → renomeá-la quebraria a ponte; recusa honesta.
        if (p != LanguageProfile.KOF && p.symbolAliases().containsKey(word)) return true;
        return false;
    }

    private static List<Object> edits(String text, String word, String newName) {
        List<Object> edits = new ArrayList<>();
        for (int[] r : LspServer.wordOccurrences(text, word)) {
            Map<String, Object> edit = new LinkedHashMap<>();
            edit.put("range", LspServer.rangeOf(text, r[0], r[1]));
            edit.put("newText", newName);
            edits.add(edit);
        }
        return edits;
    }

    private static Map<String, Object> docEdit(String uri, List<Object> edits) {
        Map<String, Object> docEdit = new LinkedHashMap<>();
        docEdit.put("textDocument", Map.of("uri", uri));
        docEdit.put("edits", edits);
        return docEdit;
    }

    static boolean isValidIdentifier(String s) {
        return s != null && s.matches("[A-Za-z_][A-Za-z0-9_]*");
    }
}
