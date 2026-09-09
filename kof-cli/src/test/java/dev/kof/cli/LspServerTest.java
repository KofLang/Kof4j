package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LSP references/rename — mock (didOpen + request) sem processo.
 * Verifica capability anunciada e que as ocorrências de um identificador
 * são encontradas em fronteiras de palavra (sem confundir prefixos/sufixos).
 */
class LspServerTest {

    private static final String URI = "file:///tmp/main.kf";

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
        List<Map<String, Object>> out = new ArrayList<>();
        int pos = 0;
        while (true) {
            int h = raw.indexOf("Content-Length:", pos);
            if (h < 0) break;
            int end = raw.indexOf("\r\n\r\n", h);
            int len = Integer.parseInt(raw.substring(h + "Content-Length:".length(), end).trim());
            int body = end + 4;
            Object parsed = Json.parse(raw.substring(body, body + len));
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
}
