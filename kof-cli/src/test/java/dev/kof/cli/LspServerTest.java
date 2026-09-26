package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LSP references/rename — mock (didOpen + request) sem processo.
 * Verifica capability anunciada e que as ocorrências de um identificador
 * são encontradas em fronteiras de palavra (sem confundir prefixos/sufixos).
 */
class LspServerTest {

    private static final String URI = "file:///kof-lsp-selftest/a.kf";
    // (antes /tmp/main.kf: o walk-6 do irmao pegava os scraps .kf alheios
    // de /tmp — determinismo de teste = URI num caminho que nunca exists)

    private static byte[] frame(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] header = ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[header.length + body.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(body, 0, out, header.length, body.length);
        return out;
    }

    private static byte[] all(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, off, p.length); off += p.length; }
        return out;
    }

    /** Extrai todos os envelopes JSON de uma saída LSP (skip de headers). */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> messages(String raw) {
        // byte-safe: Content-Length do protocolo é em BYTES UTF-8 (o servidor
        // usa body.length de byte[]); fatiar por char quebraria em payloads
        // não-ASCII (ex.: hover com em-dash) — bug exposto pela fatia 7.
        byte[] all = raw.getBytes(StandardCharsets.UTF_8);
        String ascii = new String(all, StandardCharsets.ISO_8859_1); // 1 char == 1 byte
        List<Map<String, Object>> out = new ArrayList<>();
        int pos = 0;
        while (true) {
            int h = ascii.indexOf("Content-Length:", pos);
            if (h < 0) break;
            int end = ascii.indexOf("\r\n\r\n", h);
            int len = Integer.parseInt(ascii.substring(h + "Content-Length:".length(), end).trim());
            int body = end + 4;
            Object parsed = Json.parse(new String(all, body, len, StandardCharsets.UTF_8));
            if (parsed instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
            pos = body + len;
        }
        return out;
    }

    private static Map<String, Object> byId(List<Map<String, Object>> msgs, long id) {
        for (Map<String, Object> m : msgs) {
            if (m.get("id") != null && m.get("id").equals(id)) return m;
        }
        throw new AssertionError("sem resposta id=" + id + " em " + msgs);
    }

    @SuppressWarnings("unchecked")
    @Test
    void initializeAnnouncesReferencesAndRenameCapabilities() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LspServer server = new LspServer(new ByteArrayInputStream(frame(req)), out);
        server.run();
        Map<String, Object> resp = byId(messages(out.toString(StandardCharsets.UTF_8)), 0);
        Map<String, Object> res = (Map<String, Object>) resp.get("result");
        Map<String, Object> caps = (Map<String, Object>) res.get("capabilities");
        assertEquals(Boolean.TRUE, caps.get("referencesProvider"));
        assertEquals(Boolean.TRUE, caps.get("renameProvider"));
    }

    /** §429: an unknown REQUEST (has `id`) is answered -32601 MethodNotFound;
     *  an unknown NOTIFICATION (no `id`) stays silent. */
    @SuppressWarnings("unchecked")
    @Test
    void unknownRequestAnswersMethodNotFoundAndNotificationStaysSilent() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"textDocument/inlayHint\",\"params\":{}}";
        String notif = "{\"jsonrpc\":\"2.0\",\"method\":\"$/unknownNotification\",\"params\":{}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(req), frame(notif))), out).run();
        List<Map<String, Object>> msgs = messages(out.toString(StandardCharsets.UTF_8));
        assertEquals(1, msgs.size(), "só o request vira resposta; a notificação é silenciosa: " + msgs);
        Map<String, Object> resp = byId(msgs, 7);
        assertNull(resp.get("result"), "erro não carrega result: " + resp);
        Map<String, Object> err = (Map<String, Object>) resp.get("error");
        assertNotNull(err, "request desconhecido deve responder error: " + resp);
        assertEquals(-32601, ((Number) err.get("code")).intValue(), "código MethodNotFound: " + err);
    }

    @SuppressWarnings("unchecked")
    @Test
    void referencesReturnsAllWordBoundaries() throws Exception {
        String text = "var count := 0\nprintln(count)\nvar counter := 1\n";
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/references\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},"
                + "\"position\":{\"line\":0,\"character\":4},"
                + "\"context\":{\"includeDeclaration\":true}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LspServer server = new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out);
        server.run();
        List<Map<String, Object>> msgs = messages(out.toString(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        List<Object> locs = (List<Object>) byId(msgs, 1).get("result");
        assertEquals(2, locs.size());
        for (Object l : locs) {
            assertEquals(URI, ((Map<?, ?>) l).get("uri"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void renameProducesEditsForEveryOccurrence() throws Exception {
        String text = "var count := 0\nprintln(count)\nprintln(count)\n";
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/rename\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},"
                + "\"position\":{\"line\":0,\"character\":4},\"newName\":\"total\"}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LspServer server = new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out);
        server.run();
        Map<String, Object> resp = byId(messages(out.toString(StandardCharsets.UTF_8)), 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> res = (Map<String, Object>) resp.get("result");
        List<Object> docChanges = (List<Object>) res.get("documentChanges");
        assertEquals(1, docChanges.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> docEdit = (Map<String, Object>) docChanges.get(0);
        assertEquals(URI, ((Map<?, ?>) docEdit.get("textDocument")).get("uri"));
        @SuppressWarnings("unchecked")
        List<Object> edits = (List<Object>) docEdit.get("edits");
        assertEquals(3, edits.size());
        for (Object e : edits) {
            assertEquals("total", ((Map<?, ?>) e).get("newText"));
        }
    }

    /** LSP-A (D-POLL-19): rename cruza os arquivos do projeto (mesma convenção dos references). */
    @Test
    void renameCrossesProjectFiles(@TempDir Path dir) throws Exception {
        String lib = "Int helper(Int x) { return x * 2 }\n";
        String app = "main() { println(helper(21) + helper(1)) }\n";
        Files.writeString(dir.resolve("lib.kf"), lib);
        Path appFile = dir.resolve("app.kf");
        Files.writeString(appFile, app);
        String appUri = appFile.toAbsolutePath().toUri().toString();
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + appUri + "\",\"text\":\"" + Json.escape(app) + "\"}}}";
        int col = app.indexOf("helper") + 2;
        String req = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/rename\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + appUri + "\"},"
                + "\"position\":{\"line\":0,\"character\":" + col + "},\"newName\":\"calc\"}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Map<String, Object> res = (Map<String, Object>) byId(
                messages(out.toString(StandardCharsets.UTF_8)), 2).get("result");
        assertNotNull(res, "rename cross-file nao pode ser null");
        java.util.List<Map<String, Object>> dc =
                (java.util.List<Map<String, Object>>) res.get("documentChanges");
        assertEquals(2, dc.size(), "app (2 usos) + lib (1 declaracao)");
        java.util.Map<String, Integer> perDoc = new java.util.LinkedHashMap<>();
        for (Map<String, Object> de : dc) {
            String u = String.valueOf(((Map<?, ?>) de.get("textDocument")).get("uri"));
            int n = ((java.util.List<?>) de.get("edits")).size();
            perDoc.put(u.endsWith("lib.kf") ? "lib" : "app", n);
            for (Object e : (java.util.List<Object>) de.get("edits")) {
                assertEquals("calc", ((Map<?, ?>) e).get("newText"));
            }
        }
        assertEquals(2, perDoc.get("app"), "dois usos no buffer");
        assertEquals(1, perDoc.get("lib"), "uma ocorrencia no irmao em disco");
        // aplicando os edits manualmente: nenhum "helper" sobrevive nos dois arquivos
        String appliedApp = applyEdits(app, dc, appUri);
        String appliedLib = applyEdits(lib, dc, dir.resolve("lib.kf").toAbsolutePath().toUri().toString());
        assertFalse(appliedApp.contains("helper"), appliedApp);
        assertFalse(appliedLib.contains("helper"), appliedLib);
        assertTrue(appliedLib.contains("Int calc(Int x)"), "declaracao renomeada: " + appliedLib);
    }

    /** LSP-A: keyword e namespace da stdlib nunca sao renomeaveis (guarda honesta). */
    @Test
    void renameRefusesKeywordsAndStdlibNamespaces() throws Exception {
        String app = "main() { spawn go() }\nString db = \"x\"\n";
        String reqTpl = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/rename\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},"
                + "\"position\":{\"line\":0,\"character\":%d},\"newName\":\"z\"}}";
        for (int col : new int[]{app.indexOf("spawn") + 2}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new LspServer(new ByteArrayInputStream(all(
                    frame("{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                            + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\""
                            + Json.escape(app) + "\"}}}"),
                    frame(String.format(reqTpl, col)))), out).run();
            Map<String, Object> resp = byId(messages(out.toString(StandardCharsets.UTF_8)), 2);
            assertNull(resp.get("result"), "rename de keyword deve ser null honesto (col " + col + ")");
        }
        // 'go' e um nome comum: renomeia (2 ocorrencias no proprio buffer)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(
                frame("{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                        + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\"" + Json.escape(app) + "\"}}}"),
                frame(String.format(reqTpl, app.indexOf("go") + 1)))), out).run();
        Map<String, Object> res = (Map<String, Object>) byId(
                messages(out.toString(StandardCharsets.UTF_8)), 2).get("result");
        assertNotNull(res, "nome comum renomeia");
    }

    @SuppressWarnings("unchecked")
    private static String applyEdits(String text, java.util.List<Map<String, Object>> dc, String wantUri) {
        StringBuilder sb = new StringBuilder(text);
        for (Map<String, Object> de : dc) {
            String u = String.valueOf(((Map<?, ?>) de.get("textDocument")).get("uri"));
            if (!u.equals(wantUri)) continue;
            java.util.List<Map<String, Object>> edits = (java.util.List<Map<String, Object>>) de.get("edits");
            edits.sort((a, b) -> Integer.compare(start(a, text), start(b, text)));
            for (int k = edits.size() - 1; k >= 0; k--) {
                Map<String, Object> e = edits.get(k);
                int st = start(e, text);
                int en = end(e, text);
                sb.replace(st, en, String.valueOf(e.get("newText")));
            }
        }
        return sb.toString();
    }

    private static int start(Map<String, Object> edit, String text) {
        return offsetIn(text, (Map<?, ?>) edit.get("range"), true);
    }

    private static int end(Map<String, Object> edit, String text) {
        return offsetIn(text, (Map<?, ?>) edit.get("range"), false);
    }

    private static int offsetIn(String text, Map<?, ?> range, boolean start) {
        Map<?, ?> pt = (Map<?, ?>) range.get(start ? "start" : "end");
        int line = ((Number) pt.get("line")).intValue();
        int ch = ((Number) pt.get("character")).intValue();
        int off = 0;
        for (int i = 0; i < line; i++) off = text.indexOf('\n', off) + 1;
        return off + ch;
    }

    @Test
    void renameWithNoWordOrInvalidNameReturnsNullResult() throws Exception {
        String text = "var count := 0\n";
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        // char 10 = ':' (não-identificador) → palavra vazia → null
        String reqNoWord = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"textDocument/rename\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},"
                + "\"position\":{\"line\":0,\"character\":10},\"newName\":\"x\"}}";
        String reqBadName = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"textDocument/rename\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},"
                + "\"position\":{\"line\":0,\"character\":4},\"newName\":\"bad name\"}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LspServer server = new LspServer(
                new ByteArrayInputStream(all(frame(didOpen), frame(reqNoWord), frame(reqBadName))), out);
        server.run();
        List<Map<String, Object>> msgs = messages(out.toString(StandardCharsets.UTF_8));
        assertEquals(null, byId(msgs, 3).get("result"), "sem palavra no cursor → result nulo");
        assertEquals(null, byId(msgs, 4).get("result"), "newName inválido → result nulo");
    }

    // ---- EDI001 degrau 0: textDocument/definition ------------------------

    @SuppressWarnings("unchecked")
    @Test
    void initializeAnnouncesDefinitionCapability() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(frame(req)), out).run();
        Map<String, Object> res = (Map<String, Object>) byId(messages(out.toString(StandardCharsets.UTF_8)), 0).get("result");
        Map<String, Object> caps = (Map<String, Object>) res.get("capabilities");
        assertEquals(Boolean.TRUE, caps.get("definitionProvider"));
    }

    /** Abre um texto e pede definition na posição dada; retorna o range (linha,col) do start. */
    @SuppressWarnings("unchecked")
    private long[] definitionStart(String text, long line, long ch) throws Exception {
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"textDocument/definition\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},"
                + "\"position\":{\"line\":" + line + ",\"character\":" + ch + "}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Object result = byId(messages(out.toString(StandardCharsets.UTF_8)), 9).get("result");
        if (result == null) return null;
        List<Object> locs = (List<Object>) result;
        if (locs.isEmpty()) return null;
        Map<String, Object> range = (Map<String, Object>) ((Map<String, Object>) locs.get(0)).get("range");
        Map<String, Object> start = (Map<String, Object>) range.get("start");
        return new long[]{ ((Number) start.get("line")).longValue(), ((Number) start.get("character")).longValue() };
    }

    @Test
    void definitionFindsFunctionDeclaration() throws Exception {
        // cursor em 'compute' na linha do main → declaração na linha 0, no nome (após "Int ")
        String text = "Int compute(Int x) { return x * 2 }\nmain() { println(compute(3)) }\n";
        long[] r = definitionStart(text, 1, 20);
        assertNotNull(r, "deve achar a declaração da função");
        assertEquals(0, r[0], "linha da declaração");
        assertEquals(4, r[1], "coluna do nome 'compute'");
    }

    @Test
    void definitionFindsRecordDeclaration() throws Exception {
        String text = "record Point(Int x, Int y)\nmain() { var p = Point(1,2); println(p) }\n";
        long[] r = definitionStart(text, 1, 18); // cursor dentro de 'Point' (col 17-21)
        assertNotNull(r);
        assertEquals(0, r[0]);
        assertEquals(7, r[1], "coluna do nome 'Point'");
    }

    @Test
    void definitionFindsVarDeclaration() throws Exception {
        String text = "main() {\n    var total = 0\n    println(total)\n}\n";
        long[] r = definitionStart(text, 2, 13); // cursor dentro de 'total' (linha 2)
        assertNotNull(r);
        assertEquals(1, r[0]);
        assertEquals(8, r[1], "coluna do nome 'total'");
    }

    @Test
    void definitionIsConservativeOnParameter() throws Exception {
        // 'x' só aparece como parâmetro — não resolvemos parâmetro (null, não minto)
        String text = "Int compute(Int x) { return x * 2 }\n";
        assertNull(definitionStart(text, 0, 17), "parâmetro não é declarado por var/val/função — null honesto");
    }

    // ---- EDI001 §15: textDocument/formatting (delegado ao KofFormatter) ----

    @SuppressWarnings("unchecked")
    @Test
    void initializeAnnouncesFormattingCapability() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(frame(req)), out).run();
        Map<String, Object> res = (Map<String, Object>) byId(messages(out.toString(StandardCharsets.UTF_8)), 0).get("result");
        Map<String, Object> caps = (Map<String, Object>) res.get("capabilities");
        assertEquals(Boolean.TRUE, caps.get("documentFormattingProvider"));
    }

    @SuppressWarnings("unchecked")
    private List<Object> formattingEdits(String text) throws Exception {
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"textDocument/formatting\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},\"options\":{\"tabSize\":4,\"insertSpaces\":true}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        return (List<Object>) byId(messages(out.toString(StandardCharsets.UTF_8)), 8).get("result");
    }

    @Test
    void formattingReturnsWholeDocumentEdit() throws Exception {
        // texto desformatado → um edit de documento inteiro cujo newText é o formatado
        List<Object> edits = formattingEdits("main(){\nprintln(   1+2 )\n}\n");
        assertNotNull(edits);
        assertEquals(1, edits.size(), "um edit de substituição total");
        Map<String, Object> e = (Map<String, Object>) edits.get(0);
        String newText = (String) e.get("newText");
        assertTrue(newText.contains("println(1 + 2)"), "KofFormatter normaliza: " + newText);
    }

    @Test
    void formattingIsIdempotentNoEditWhenAlreadyFormatted() throws Exception {
        // formatar o que já está formatado → sem edits (lista vazia, não null)
        String formatted = dev.kof.compiler.KofFormatter.format("main(){\nprintln(1)\n}\n", "Main.kf");
        List<Object> edits = formattingEdits(formatted);
        assertNotNull(edits);
        assertTrue(edits.isEmpty(), "já formatado → nenhum edit, foi: " + edits);
    }

    // ---- §509 / issue #625: formatting nunca perde comentario, nunca NPE --

    private static final String REPRO_625 = "main() {\n    val a = 1\n    val b = 2\n"
            + "    // soma os valores\n    val c = a + b\n    println(c)\n}\n";

    @SuppressWarnings("unchecked")
    @Test
    void formattingPreservesCommentsIssue625() throws Exception {
        // antes: o edit de documento inteiro DELETAVA a linha do comentario
        // (AST vencia pelo heuristica de 50%) — o format-on-save do editor
        // destrua trabalho do usuario em silencio (R6).
        String unformatted = "main(){\nval a=1\n// soma os valores\nprintln( a )\n}\n";
        List<Object> edits = formattingEdits(unformatted);
        assertEquals(1, edits.size(), "um edit de substituição total");
        String newText = (String) ((Map<String, Object>) edits.get(0)).get("newText");
        assertTrue(newText.contains("// soma os valores"),
                "comentario preservado no newText: " + newText);
        assertTrue(newText.contains("println(a)"), "e o codigo formata: " + newText);
    }

    @Test
    void formattingCanonicalWithCommentsProposesNoEdit() throws Exception {
        // o fonte do issue ja esta na forma canonica do formatador de
        // comentarios (token-based) — o servidor NAO pode propor reescrita
        // lossy (era exatamente isso que o bug fazia: edit "limpando" a
        // linha do comentario)
        List<Object> edits = formattingEdits(REPRO_625);
        assertTrue(edits.isEmpty(), "ja canonico com comentario → nenhum edit, foi: " + edits);
    }

    @Test
    void formattingUnformattableSourceNeverCrashesServer() throws Exception {
        // antes: KofFormatter devolvia null (fonte nao-parseavel) e o
        // formatEdit fazia formatted.equals(text) → NPE matava o servidor
        // (LspServer.java:433). Agora: resposta valida, servidor vivo.
        List<Object> edits = formattingEdits("main( {\n// nota\n");
        assertNotNull(edits, "servidor responde (edits ou vazio), nao morre");
    }

    // ---- EDI001 §15: textDocument/documentSymbol (outline) ---------------

    @SuppressWarnings("unchecked")
    @Test
    void initializeAnnouncesDocumentSymbolCapability() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(frame(req)), out).run();
        Map<String, Object> res = (Map<String, Object>) byId(messages(out.toString(StandardCharsets.UTF_8)), 0).get("result");
        Map<String, Object> caps = (Map<String, Object>) res.get("capabilities");
        assertEquals(Boolean.TRUE, caps.get("documentSymbolProvider"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> documentSymbols(String text) throws Exception {
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"textDocument/documentSymbol\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Object result = byId(messages(out.toString(StandardCharsets.UTF_8)), 7).get("result");
        List<Object> raw = result == null ? List.of() : (List<Object>) result;
        List<Map<String, Object>> symbols = new java.util.ArrayList<>();
        for (Object o : raw) symbols.add((Map<String, Object>) o);
        return symbols;
    }

    @Test
    void documentSymbolListsFunctionsAndTypes() throws Exception {
        String text = "record Point(Int x, Int y)\nclass Box { Int n }\n"
                + "Int compute(Int x) { return x * 2 }\nmain() { println(compute(3)) }\n";
        List<Map<String, Object>> syms = documentSymbols(text);
        java.util.Map<String, Integer> byName = new java.util.LinkedHashMap<>();
        for (Map<String, Object> s : syms) byName.put((String) s.get("name"),
                ((Number) s.get("kind")).intValue());
        // tipos (kind 5) + funções (kind 12), em ordem de aparecimento
        assertEquals(java.util.Map.of("Point", 5, "Box", 5, "compute", 12, "main", 12), byName,
                "outline deve listar tipos e funções: " + syms);
    }

    @Test
    void documentSymbolSelectionRangePointsAtName() throws Exception {
        String text = "Int compute(Int x) { return x * 2 }\n";
        List<Map<String, Object>> syms = documentSymbols(text);
        assertEquals(1, syms.size());
        Map<String, Object> start = (Map<String, Object>)
                ((Map<String, Object>) syms.get(0).get("selectionRange")).get("start");
        assertEquals(0, ((Number) start.get("line")).intValue());
        assertEquals(4, ((Number) start.get("character")).intValue(), "seleção no nome 'compute' (col 4)");
    }

    @Test
    void documentSymbolSkipsControlKeywords() throws Exception {
        // if/while/for/switch com ( não são funções
        String text = "main() {\n if (true) { println(1) }\n while (false) { }\n for (var i in listOf(1)) { }\n}\n";
        List<Map<String, Object>> syms = documentSymbols(text);
        assertEquals(1, syms.size(), "só 'main' é função: " + syms);
        assertEquals("main", syms.get(0).get("name"));
    }

    // ---- EDI001 §15: textDocument/codeAction (source.format) --------------

    @SuppressWarnings("unchecked")
    private List<Object> codeActions(String text) throws Exception {
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"textDocument/codeAction\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},"
                + "\"range\":{\"start\":{\"line\":0,\"character\":0},\"end\":{\"line\":0,\"character\":0}},"
                + "\"context\":{\"diagnostics\":[]}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Object result = byId(messages(out.toString(StandardCharsets.UTF_8)), 6).get("result");
        return result == null ? List.of() : (List<Object>) result;
    }

    @Test
    void codeActionOffersFormatWhenUnformatted() throws Exception {
        List<Object> actions = codeActions("main(){\nprintln(   1+2 )\n}\n");
        assertEquals(1, actions.size(), "source.format oferecido: " + actions);
        Map<String, Object> a = (Map<String, Object>) actions.get(0);
        assertEquals("Format Document", a.get("title"));
        assertEquals("source", a.get("kind"));
        List<Object> changes = (List<Object>) ((Map<String, Object>) a.get("edit")).get("documentChanges");
        assertEquals(1, changes.size());
        List<Object> edits = (List<Object>) ((Map<String, Object>) changes.get(0)).get("edits");
        assertTrue(((String) ((Map<String, Object>) edits.get(0)).get("newText")).contains("println(1 + 2)"));
    }

    @Test
    void codeActionEmptyWhenAlreadyFormatted() throws Exception {
        String formatted = dev.kof.compiler.KofFormatter.format("main(){\nprintln(1)\n}\n", "Main.kf");
        assertTrue(codeActions(formatted).isEmpty(), "nada a formatar → nenhuma ação");
    }

    @Test
    void codeActionProviderAnnouncedWithKinds() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(frame(req)), out).run();
        Map<String, Object> res = (Map<String, Object>) byId(messages(out.toString(StandardCharsets.UTF_8)), 0).get("result");
        Map<String, Object> caps = (Map<String, Object>) res.get("capabilities");
        Object provider = caps.get("codeActionProvider");
        assertInstanceOf(Map.class, provider, "codeActionProvider com opções");
        assertEquals(List.of("source"), ((Map<String, Object>) provider).get("codeActionKinds"));
    }


    /** X10 fatia 4: definição em OUTRO arquivo do projeto (packages). */
    @Test
    void definitionJumpsAcrossProjectFiles(@TempDir Path dir) throws Exception {
        String lib = "Int helper(Int x) { return x * 2 }\n";
        String app = "main() { println(helper(21)) }\n";
        Files.writeString(dir.resolve("lib.kf"), lib);
        Path appFile = dir.resolve("app.kf");
        Files.writeString(appFile, app);
        String appUri = appFile.toAbsolutePath().toUri().toString();
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + appUri + "\",\"text\":\"" + Json.escape(app) + "\"}}}";
        // coluna de "helper" na linha 0: "main() { println(" = 18 chars? localizar real
        int col = app.indexOf("helper") + 2;
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/definition\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + appUri + "\"},"
                + "\"position\":{\"line\":0,\"character\":" + col + "}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Map<String, Object> resp = byId(messages(out.toString(StandardCharsets.UTF_8)), 1);
        @SuppressWarnings("unchecked")
        List<Object> locs = (List<Object>) resp.get("result");
        assertNotNull(locs, "esperava Location cross-file");
        assertEquals(1, locs.size());
        Map<?, ?> loc = (Map<?, ?>) locs.get(0);
        String libUri = dir.resolve("lib.kf").toAbsolutePath().toUri().toString();
        assertEquals(libUri, loc.get("uri"), "deve cair em lib.kf");
        @SuppressWarnings("unchecked")
        Map<String, Object> range = (Map<String, Object>) loc.get("range");
        @SuppressWarnings("unchecked")
        Map<String, Object> start = (Map<String, Object>) range.get("start");
        assertEquals(0L, ((Number) start.get("line")).longValue(), "helper declarado na linha 0");
    }

    /** Nome inexistente no projeto: null honesto (nunca chute). */
    @Test
    void definitionUnknownNameIsNull(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("a.kf");
        String app = "main() { println(naoExiste(1)) }\n";
        Files.writeString(f, app);
        String uriA = f.toAbsolutePath().toUri().toString();
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + uriA + "\",\"text\":\"" + Json.escape(app) + "\"}}}";
        int col = app.indexOf("naoExiste") + 3;
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/definition\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + uriA + "\"},"
                + "\"position\":{\"line\":0,\"character\":" + col + "}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Map<String, Object> resp = byId(messages(out.toString(StandardCharsets.UTF_8)), 1);
        assertTrue(resp.containsKey("result"));
        assertNull(resp.get("result"));
    }


    /** X10 fatia 5: referências também nos .kf irmãos (read-only). */
    @Test
    void referencesSpanProjectFiles(@TempDir Path dir) throws Exception {
        String lib = "Int helper(Int x) { return x * 2 }\n";
        String app = "main() { println(helper(21)) }\n";
        Files.writeString(dir.resolve("lib.kf"), lib);
        Path appFile = dir.resolve("app.kf");
        Files.writeString(appFile, app);
        String appUri = appFile.toAbsolutePath().toUri().toString();
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + appUri + "\",\"text\":\"" + Json.escape(app) + "\"}}}";
        int col = app.indexOf("helper") + 2;
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/references\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + appUri + "\"},"
                + "\"position\":{\"line\":0,\"character\":" + col + "}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Map<String, Object> resp = byId(messages(out.toString(StandardCharsets.UTF_8)), 1);
        @SuppressWarnings("unchecked")
        List<Object> locs = (List<Object>) resp.get("result");
        assertEquals(2, locs.size(), "1 no buffer + 1 no irmão lib.kf");
        java.util.Set<String> uris = new java.util.HashSet<>();
        for (Object o : locs) uris.add(String.valueOf(((Map<?, ?>) o).get("uri")));
        assertTrue(uris.contains(appUri));
        assertTrue(uris.contains(dir.resolve("lib.kf").toAbsolutePath().toUri().toString()));
    }


    /** X10 fatia 6: workspace/symbol une buffers + .kf irmãos não-abertos. */
    @Test
    void workspaceSymbolSpansProject(@TempDir Path dir) throws Exception {
        String lib = "Int helper(Int x) { return x * 2 }\n";
        String app = "record Box(Int w)\nmain() { println(helper(21)) }\n";
        Files.writeString(dir.resolve("lib.kf"), lib);
        Path appFile = dir.resolve("app.kf");
        Files.writeString(appFile, app);
        String appUri = appFile.toAbsolutePath().toUri().toString();
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + appUri + "\",\"text\":\"" + Json.escape(app) + "\"}}}";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"\"}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Map<String, Object> resp = byId(messages(out.toString(StandardCharsets.UTF_8)), 1);
        @SuppressWarnings("unchecked")
        List<Object> syms = (List<Object>) resp.get("result");
        assertEquals(3, syms.size(), "Box+main (buffer) e helper (irmão no disco)");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Object o : syms) names.add(String.valueOf(((Map<?, ?>) o).get("name")));
        assertEquals(java.util.Set.of("Box", "main", "helper"), names);
        String libUri = dir.resolve("lib.kf").toAbsolutePath().toUri().toString();
        for (Object o : syms) {
            Map<?, ?> m = (Map<?, ?>) o;
            Map<?, ?> loc = (Map<?, ?>) m.get("location");
            if ("helper".equals(m.get("name"))) assertEquals(libUri, loc.get("uri"));
        }
        // filtro substring case-insensitive: "BOx" acha Box (prefixo) e nada mais
        String reqF = req.replace("\"query\":\"\"", "\"query\":\"BOx\"");
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(reqF))), out2).run();
        @SuppressWarnings("unchecked")
        List<Object> filt = (List<Object>) byId(messages(out2.toString(StandardCharsets.UTF_8)), 1).get("result");
        assertEquals(1, filt.size());
        assertEquals("Box", ((Map<?, ?>) filt.get(0)).get("name"));
    }


    /** 8.3 (plano universal): hover POR DOMINIO — namespace e membro em contexto de ponto. */
    @Test
    void hoverCoversStdlibNamespacesAndMembers(@TempDir Path dir) throws Exception {
        String app = "main() { val s = json.encode(mapOf(\"a\", 1)); val d = db }\n";
        Path appFile = dir.resolve("app.kf");
        Files.writeString(appFile, app);
        String appUri = appFile.toAbsolutePath().toUri().toString();
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + appUri + "\",\"text\":\"" + Json.escape(app) + "\"}}}";
        // membro no contexto exato json. -> membro de kof.json
        int col = app.indexOf("encode") + 3;
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/hover\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + appUri + "\"},"
                + "\"position\":{\"line\":0,\"character\":" + col + "}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Map<String, Object> res = (Map<String, Object>) byId(
                messages(out.toString(StandardCharsets.UTF_8)), 1).get("result");
        assertNotNull(res, "hover de membro stdlib nao pode ser null");
        String v = String.valueOf(((Map<String, Object>) res.get("contents")).get("value"));
        assertTrue(v.contains("member of `kof.json`"), "hover de membro: " + v);
        // namespace sozinho -> lista membros reais do StdCatalog
        int colDb = app.indexOf("db") + 1;
        String reqDb = req.replace("\"character\":" + col, "\"character\":" + colDb);
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(reqDb))), out2).run();
        Map<String, Object> res2 = (Map<String, Object>) byId(
                messages(out2.toString(StandardCharsets.UTF_8)), 1).get("result");
        assertNotNull(res2, "hover de namespace nao pode ser null");
        String v2 = String.valueOf(((Map<String, Object>) res2.get("contents")).get("value"));
        assertTrue(v2.contains("namespace `kof.db`"), "hover de namespace: " + v2);
    }

    /** X10 fatia 4 (travada por teste): o LSP e generico sobre o StdCatalog —
     *  os 18 namespaces da fatia 3 aparecem em hover sem NENHUM codigo novo
     *  de tooling (fonte unica; se um dia alguém fixar lista no hover, isto
     *  aqui quebra e a duplicacao morre). */
    @Test
    void hoverCoversSliceThreeNamespacesFromSingleSource(@TempDir Path dir) throws Exception {
        String app = "main() { val a = orm.save(x); val v = config.get(\"k\"); val o = orm; val c = config }\n";
        Path appFile = dir.resolve("app.kf");
        Files.writeString(appFile, app);
        String uri = appFile.toAbsolutePath().toUri().toString();
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + uri + "\",\"text\":\"" + Json.escape(app) + "\"}}}";
        String tpl = "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"textDocument/hover\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + uri + "\"},"
                + "\"position\":{\"line\":0,\"character\":%d}}}";
        String[] cases = {"member of `kof.orm`", "member of `kof.config`",
                "namespace `kof.orm`", "namespace `kof.config`"};
        int[] cols = {app.indexOf("save") + 2, app.indexOf("get") + 1,
                app.indexOf("val o = orm") + 8, app.indexOf("val c = config") + 8};
        byte[] req0 = frame(didOpen);
        byte[] req1 = frame(String.format(tpl, 1, cols[0]));
        byte[] req2 = frame(String.format(tpl, 2, cols[1]));
        byte[] req3 = frame(String.format(tpl, 3, cols[2]));
        byte[] req4 = frame(String.format(tpl, 4, cols[3]));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(req0, req1, req2, req3, req4)), out).run();
        List<Map<String, Object>> m = messages(out.toString(StandardCharsets.UTF_8));
        for (int i = 0; i < cases.length; i++) {
            Map<String, Object> res = (Map<String, Object>) byId(m, i + 1).get("result");
            assertNotNull(res, "hover caso " + i + " nao pode ser null (col " + cols[i] + ")");
            String v = String.valueOf(((Map<String, Object>) res.get("contents")).get("value"));
            assertTrue(v.contains(cases[i]), "hover " + cases[i] + ": " + v);
        }
    }

    /** 8.3: local shadowing vence o dominio; membro solto sem '.' continua null honesto. */
    @Test
    void hoverStdlibDoesNotShadowLocalsOrGuessLooseNames(@TempDir Path dir) throws Exception {
        String app = "main() {\n    val db = 1\n    println(db)\n    println(encode)\n}\n";
        Path appFile = dir.resolve("a.kf");
        Files.writeString(appFile, app);
        String uri = appFile.toAbsolutePath().toUri().toString();
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + uri + "\",\"text\":\"" + Json.escape(app) + "\"}}}";
        String tpl = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/hover\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + uri + "\"},"
                + "\"position\":{\"line\":%d,\"character\":%d}}}";
        // println(db): 'db' na linha 2, coluna 12 (mesmo nome do namespace kof.db)
        String v = hoverValue(didOpen, String.format(tpl, 2, 13), dir);
        assertTrue(v.contains("local variable"), "shadow local nao pode virar namespace: " + v);
        // println(encode): membro solto, sem contexto de ponto -> null honesto (nunca chute)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen),
                frame(String.format(tpl, 3, 14)))), out).run();
        Map<String, Object> resp = byId(messages(out.toString(StandardCharsets.UTF_8)), 1);
        assertNull(resp.get("result"), "membro solto sem '.': null honesto");
    }

    private static String hoverValue(String didOpen, String req, Path dir) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Map<String, Object> res = (Map<String, Object>) byId(
                messages(out.toString(StandardCharsets.UTF_8)), 1).get("result");
        return String.valueOf(((Map<String, Object>) res.get("contents")).get("value"));
    }

    /** 8.3-B: workspace/symbol cobra deps fora do pai do arquivo aberto (rootUri do initialize). */
    @Test
    void workspaceSymbolCoversProjectRootDependency(@TempDir Path dir) throws Exception {
        Path src = Files.createDirectories(dir.resolve("proj/src"));
        Path lib = Files.createDirectories(dir.resolve("proj/lib"));
        String main = "main() { println(Helper(1).v()) }\n";
        String helper = "record Helper(Int v)\n";
        Files.writeString(src.resolve("main.kf"), main);
        Files.writeString(lib.resolve("Helper.kf"), helper);
        String srcUri = src.resolve("main.kf").toAbsolutePath().toUri().toString();
        String rootUri = dir.resolve("proj").toAbsolutePath().toUri().toString();
        String init = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{"
                + "\"rootUri\":\"" + rootUri + "\"}}";
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + srcUri + "\",\"text\":\"" + Json.escape(main) + "\"}}}";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"Helper\"}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(init), frame(didOpen), frame(req))), out).run();
        @SuppressWarnings("unchecked")
        List<Object> hits = (List<Object>) byId(messages(out.toString(StandardCharsets.UTF_8)), 1).get("result");
        assertTrue(hits.stream().anyMatch(h -> String.valueOf(((Map<?, ?>) h).get("name")).equals("Helper")),
                "8.3-B: simbolo de proj/lib/Helper.kf deve aparecer via rootUri; hits=" + hits);
    }

    /** X10 fatia 7: hover mostra declara\u00e7\u00e3o cross-file do projeto. */
    @Test
    void hoverShowsCrossFileDeclaration(@TempDir Path dir) throws Exception {
        String lib = "Int helper(Int x) { return x * 2 }\n";
        String app = "main() { println(helper(21) + mystery(2)) }\n";
        Files.writeString(dir.resolve("lib.kf"), lib);
        Path appFile = dir.resolve("app.kf");
        Files.writeString(appFile, app);
        String appUri = appFile.toAbsolutePath().toUri().toString();
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + appUri + "\",\"text\":\"" + Json.escape(app) + "\"}}}";
        int col = app.indexOf("helper") + 2;
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/hover\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + appUri + "\"},"
                + "\"position\":{\"line\":0,\"character\":" + col + "}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Map<String, Object> resp = byId(messages(out.toString(StandardCharsets.UTF_8)), 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> res = (Map<String, Object>) resp.get("result");
        assertNotNull(res, "esperava hover cross-file");
        @SuppressWarnings("unchecked")
        Map<String, Object> contents = (Map<String, Object>) res.get("contents");
        String value = String.valueOf(contents.get("value"));
        assertTrue(value.contains("lib.kf"), "deve apontar o arquivo de origem: " + value);
        assertTrue(value.contains("Int helper(Int x)"), "linha de declara\u00e7\u00e3o real: " + value);
        // nome sem declaração no projeto segue null honesto (R6, nunca chute)
        int col2 = app.indexOf("mystery") + 3;
        String req2 = req.replace("\"character\":" + col, "\"character\":" + col2);
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req2))), out2).run();
        Map<String, Object> resp2 = byId(messages(out2.toString(StandardCharsets.UTF_8)), 1);
        assertTrue(resp2.containsKey("result"));
        assertNull(resp2.get("result"), "mystery não é declarado no projeto: hover null honesto");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> completionAt(String text, long line, long ch) throws Exception {
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/completion\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},"
                + "\"position\":{\"line\":" + line + ",\"character\":" + ch + "}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Map<String, Object> res = (Map<String, Object>) byId(messages(out.toString(StandardCharsets.UTF_8)), 1)
                .get("result");
        return (List<Map<String, Object>>) res.get("items");
    }

    /** X10 fatia 1: depois de `rng.` o LSP oferece os membros REAIS do typer. */
    @Test
    void completionRngDotOffersStdlibMembers() throws Exception {
        List<Map<String, Object>> items = completionAt("main() {\n    rng.\n}", 1, 8);
        List<String> labels = items.stream().map(i -> (String) i.get("label")).toList();
        assertTrue(labels.containsAll(List.of("seed", "int", "boolean", "double", "string")),
                "esperava os 5 membros de kof.rng, veio " + labels);
        assertTrue(items.stream().allMatch(i -> "Function".equals(i.get("kind"))),
                "membros stdlib sao kind Function: " + items);
        assertEquals("kof.rng", items.get(0).get("detail"));
    }

    @Test
    void completionMathDotOffersFunctions() throws Exception {
        List<String> labels = completionAt("math.", 0, 5).stream()
                .map(i -> (String) i.get("label")).toList();
        assertTrue(labels.contains("sqrt"), "math.sqrt ausente: " + labels);
        assertTrue(labels.contains("clamp"), "math.clamp ausente: " + labels);
    }

    /** Prefixo que NAO e namespace stdlib: nenhuma oferta de membros (no-op honesto). */
    @Test
    void completionNonNamespaceDotStaysQuiet() throws Exception {
        List<Map<String, Object>> items = completionAt("foo.", 0, 4);
        assertTrue(items.stream().noneMatch(i -> "Function".equals(i.get("kind"))),
                "nao-inventar membros p/ prefixo estranho: " + items);
    }

    /** X10 fatia 2: dispatch próprio também completado (time/db/security). */
    @Test
    void completionSlice2Namespaces() throws Exception {
        List<String> t = completionAt("time.", 0, 5).stream()
                .map(i -> (String) i.get("label")).toList();
        assertTrue(t.containsAll(List.of("sleep", "now", "interval", "daysBetween")),
                "time.* faltando: " + t);
        List<String> d = completionAt("db.", 0, 3).stream()
                .map(i -> (String) i.get("label")).toList();
        assertEquals(List.of("connect", "query", "execute", "close", "transaction"), d);
        List<String> c = completionAt("crypto.", 0, 7).stream()
                .map(i -> (String) i.get("label")).toList();
        assertTrue(c.contains("sha256") && c.contains("hmacSha256"), "crypto: " + c);
    }

    /** X10 fatia 3: receiver-typed (json/log/mq/validation/media). */
    @Test
    void completionSlice3Namespaces() throws Exception {
        assertEquals(List.of("encode", "decode"),
                completionAt("json.", 0, 5).stream().map(i -> (String) i.get("label")).toList());
        List<String> lg = completionAt("log.", 0, 4).stream()
                .map(i -> (String) i.get("label")).toList();
        assertEquals(List.of("debug", "info", "warn", "error"), lg);
        List<String> mi = completionAt("Mic.", 0, 4).stream()
                .map(i -> (String) i.get("label")).toList();
        assertEquals(List.of("record", "list"), mi);
        List<String> img = completionAt("Image.", 0, 6).stream()
                .map(i -> (String) i.get("label")).toList();
        assertEquals(List.of("open"), img);
    }

    /** Fora do ponto, o completion de palavras/chaves existente nao regride. */
    @Test
    void completionStillOffersKeywordsAndVars() throws Exception {
        List<String> labels = completionAt("var total = 0\n    ", 1, 4).stream()
                .map(i -> (String) i.get("label")).toList();
        assertTrue(labels.contains("total"), "variable sumiu: " + labels);
        assertTrue(labels.contains("var"), "keywords sumiram: " + labels);
    }


    /** 8.3 (LSP-A, fila universal): o servidor anuncia signatureHelp e o
     *  request devolve as formas gravadas no MESMA tabela do hover. */
    @Test
    @SuppressWarnings("unchecked")
    void signatureHelpRoundTripUsesTableAndAnnouncesCapability(@TempDir Path dir) throws Exception {
        String init = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}";
        ByteArrayOutputStream out0 = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(init))), out0).run();
        Map<String, Object> caps = (Map<String, Object>) byId(
                messages(out0.toString(StandardCharsets.UTF_8)), 0).get("result");
        assertNotNull(((Map<String, Object>) caps.get("capabilities")).get("signatureHelpProvider"),
                "capability signatureHelp faltando: " + caps.get("capabilities"));
        String app = "main() { val d = db.connect(\"x\", y }\n";
        Path appFile = dir.resolve("app.kf");
        Files.writeString(appFile, app);
        String uri = appFile.toAbsolutePath().toUri().toString();
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\""
                + uri + "\",\"text\":\"" + Json.escape(app) + "\"}}}";
        int col = app.indexOf("y }") + 1;
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/signatureHelp\",\"params\":{\"textDocument\":{\"uri\":\""
                + uri + "\"},\"position\":{\"line\":0,\"character\":" + col + "}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Map<String, Object> res = (Map<String, Object>) byId(
                messages(out.toString(StandardCharsets.UTF_8)), 1).get("result");
        assertNotNull(res, "db.connect( com tabela nao pode responder null");
        assertEquals(1L, res.get("activeParameter"), res.toString());
        List<Object> sigs = (List<Object>) res.get("signatures");
        assertEquals(2, sigs.size(), "as duas formas de db.connect: " + sigs);
        String u = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/signatureHelp\",\"params\":{\"textDocument\":{\"uri\":\""
                + uri + "\"},\"position\":{\"line\":0,\"character\":" + (app.indexOf("db") + 1) + "}}}";
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(u))), out2).run();
        assertNull(byId(messages(out2.toString(StandardCharsets.UTF_8)), 2).get("result"),
                "fora de chamada com tabela => null, nunca chute (R6)");
    }
}