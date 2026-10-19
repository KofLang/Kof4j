package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #894 — o `kof lsp` guardava um buffer VAZIO no `didChange`: a notificação traz
 * um {@code VersionedTextDocumentIdentifier} (`uri` + `version`, SEM `text`), e o
 * `publishDiagnostics` escrevia no cache ANTES de ler o texto novo de
 * {@code contentChanges}. Resultado: os diagnostics ficavam certos (eles leem a
 * variável local) e TODOS os handlers que leem o cache — hover, completion,
 * signature help, definition, formatting, code action, symbols, references e
 * rename — passavam a ver um documento vazio. Os diagnostics continuarem certos
 * é o que ESCONDE o defeito.
 *
 * <p>Este teste olha as duas faces que o reporter mediu: {@code documentSymbol}
 * (que lista os símbolos do documento) e {@code formatting} (que responde um
 * edit com `newText` — com o buffer vazio o formatter devolve só {@code "\n"}).
 */
class LspDidChangeBufferE2ETest {

    private static final String URI = "file:///kof-lsp-selftest/main.kf";

    @Test
    void afterDidChangeEveryRequestSeesTheNewText() throws Exception {
        String before = "Int alpha(Int x) { return x }\nmain() { println(alpha(1)) }\n";
        String after = "Int beta(Int y) { return y + 1 }\nmain() { println(beta(2)) }\n";
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\"" + Json.escape(before) + "\"}}}";
        // didChange: VersionedTextDocumentIdentifier (SEM text) + contentChanges
        String didChange = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didChange\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\",\"version\":2},"
                + "\"contentChanges\":[{\"text\":\"" + Json.escape(after) + "\"}]}}";
        String symbols = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/documentSymbol\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"}}}";
        String formatting = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/formatting\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},"
                + "\"options\":{\"tabSize\":4,\"insertSpaces\":true}}}";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(didChange),
                frame(symbols), frame(formatting))), out).run();
        String wire = out.toString(StandardCharsets.UTF_8);

        Map<String, Object> sym = byId(wire, 1);
        assertNotNull(sym, "documentSymbol tem de responder: " + wire);
        String symJson = sym.toString();
        assertTrue(symJson.contains("beta"), "o símbolo do texto NOVO tem de aparecer: " + symJson);
        assertFalse(symJson.contains("alpha"), "o símbolo do texto ANTIGO não pode aparecer: " + symJson);

        Map<String, Object> fmt = byId(wire, 2);
        assertNotNull(fmt, "formatting tem de responder: " + wire);
        String fmtJson = fmt.toString();
        assertTrue(fmtJson.contains("beta"), "o formatter tem de formatar o texto NOVO: " + fmtJson);
        assertFalse(fmtJson.contains("newText=\n") || fmtJson.contains("newText=\r\n"),
                "com o buffer vazio o formatter devolve só um \\n — o defeito do #894: " + fmtJson);
    }

    // --- harness (mesmo enquadramento dos outros testes LSP) -----------------

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
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> byId(String raw, long id) {
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        String ascii = new String(bytes, StandardCharsets.ISO_8859_1);
        int pos = 0;
        while (true) {
            int header = ascii.indexOf("Content-Length:", pos);
            if (header < 0) return null;
            int eol = ascii.indexOf('\n', header);
            int length = Integer.parseInt(ascii.substring(header + "Content-Length:".length(), eol).trim());
            int body = eol + 1;
            while (body < ascii.length() && (ascii.charAt(body) == '\r' || ascii.charAt(body) == '\n')) body++;
            byte[] json = new byte[length];
            System.arraycopy(bytes, body, json, 0, length);
            Map<String, Object> m = (Map<String, Object>) Json.parse(new String(json, StandardCharsets.UTF_8));
            if (m.get("id") instanceof Number n && n.longValue() == id) return m;
            pos = body + length;
        }
    }
}
