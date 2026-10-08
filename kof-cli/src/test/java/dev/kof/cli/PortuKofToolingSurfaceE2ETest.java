package dev.kof.cli;

import dev.kof.compiler.lang.LanguageProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-PORTUKOF F7.2 (07/10) — RENDERIZAÇÃO DE SUPERFÍCIE do tooling + imports
 * `.ptkf`.
 *
 * Regra central (nunca quebrada): o tooling opera sobre o SÍMBOLO CANÔNICO
 * e renderiza a superfície via a ponte ÚNICA ({@code LanguageProfile} +
 * catálogos gateados). SEM regex no tooling, SEM segundo engine, SEM
 * tradução de identificador de usuário, SEM contrato de máquina alterado.
 */
class PortuKofToolingSurfaceE2ETest {

    // ---------------- harness LSP (byte-safe) ----------------
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

    private static String run(byte[]... in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(in)), out).run();
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String uriOf(Path p) { return p.toUri().toString(); }

    private static String didOpen(Path file, String text) {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":"
                + "{\"textDocument\":{\"uri\":\"" + uriOf(file) + "\",\"text\":" + str(text) + "}}}";
    }

    private static String initReq(Path root) {
        String ru = root == null ? "{}" : "{\"rootUri\":\"" + root.toUri() + "\"}";
        return "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":" + ru + "}";
    }

    private static String req(int id, String method, Path file, long line, long ch) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":"
                + "{\"textDocument\":{\"uri\":\"" + uriOf(file) + "\"},\"position\":"
                + "{\"line\":" + line + ",\"character\":" + ch + "}}}";
    }

    private static String reqWithNewName(int id, Path file, long line, long ch, String newName) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"textDocument/rename\",\"params\":"
                + "{\"textDocument\":{\"uri\":\"" + uriOf(file) + "\"},\"position\":"
                + "{\"line\":" + line + ",\"character\":" + ch + "},\"newName\":" + str(newName) + "}}";
    }

    private static String str(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    /** Corpo da resposta com o `id` pedido (null se o servidor devolveu null). */
    private static String resultOf(String raw, int id) {
        String ascii = raw;
        int pos = 0;
        String key = "\"id\":" + id + ",";
        while (true) {
            int h = ascii.indexOf("Content-Length: ", pos);
            if (h < 0) break;
            int sep = ascii.indexOf("\r\n\r\n", h);
            if (sep < 0) break;
            int len = Integer.parseInt(ascii.substring(h + 16, sep).trim());
            String body = ascii.substring(sep + 4, Math.min(sep + 4 + len, ascii.length()));
            pos = sep + 4 + len;
            if (body.contains(key)) {
                int ri = body.indexOf("\"result\":");
                if (ri < 0) continue;
                if (body.regionMatches(ri + 9, "null", 0, 4)) return null;
                return body.substring(ri + 9);
            }
        }
        return null;
    }

    private static List<String> labels(String result) {
        List<String> out = new ArrayList<>();
        if (result == null) return out;
        Matcher m = Pattern.compile("\"(?:label|name)\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(result);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static String hoverValue(String result) {
        if (result == null) return null;
        Matcher m = Pattern.compile("\"value\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(result);
        return m.find() ? m.group(1) : null;
    }

    private static String first(String result, String field) {
        if (result == null) return null;
        Matcher m = Pattern.compile("\"" + field + "\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(result);
        return m.find() ? m.group(1) : null;
    }

    // ============ COMPLETION / MEMBROS ============

    private static String categorySurfaceLabel(LanguageProfile p, String category) {
        var byName = dev.kof.compiler.lang.SurfaceNames.methodSurfaceNames(p, category);
        var sb = new StringBuilder();
        for (var e : byName.entrySet()) {
            for (String surface : e.getValue()) {
                if (!sb.toString().contains(surface)) sb.append(surface).append(' ');
            }
        }
        return sb.toString();
    }

    // ============ COMPLETION ============

    @Test
    void completionOffersPortugueseSurfaceNotEnglishOnly(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("C.ptkf");
        String text = "principal() {\n    es\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(pt, text)), frame(req(1, "textDocument/completion", pt, 1, 6)));
        String r = resultOf(out, 1);
        assertNotNull(r, "completion .ptkf não nulo");
        List<String> lb = labels(r);
        assertTrue(lb.contains("escreva"), "superfície PT no completion: " + lb);
        assertTrue(lb.contains("escrevaln"), "superfície PT no completion: " + lb);
        assertFalse(lb.contains("print"), "Kof interno `print` não vaza na superfície PT: " + lb);
        assertFalse(lb.contains("println"), "Kof interno `println` não vaza na superfície PT: " + lb);
    }

    @Test
    void completionUserSymbolNeverTranslated(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("U.ptkf");
        String text = "principal() {\n    val minhaConta = 1\n    minhaConta\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(pt, text)), frame(req(1, "textDocument/completion", pt, 2, 14)));
        String r = resultOf(out, 1);
        assertTrue(labels(r).contains("minhaConta"), "identificador de usuário preservado: " + labels(r));
    }

    @Test
    void completionStdlibNamespaceMembersOnSurface(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("M.ptkf");
        String text = "principal() {\n    val x = matematica.\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(pt, text)), frame(req(1, "textDocument/completion", pt, 1, 23)));
        String r = resultOf(out, 1);
        assertNotNull(r, "completion de namespace PT");
        assertTrue(labels(r).contains("raizQuadrada"),
                "membro de `matematica` na superfície PT (sqrt→raizQuadrada): " + labels(r));
        assertFalse(labels(r).contains("sqrt"), "canônico `sqrt` não vaza no namespace PT: " + labels(r));
    }

    @Test
    void completionMemberAfterNamespaceWorksOnKofToo(@TempDir Path dir) throws Exception {
        Path kf = dir.resolve("M.kf");
        String text = "main() {\n    val x = math.\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(kf, text)), frame(req(1, "textDocument/completion", kf, 1, 17)));
        List<String> lb = labels(resultOf(out, 1));
        assertTrue(lb.contains("sqrt"), "Kof mantém membros canônicos: " + lb);
        assertFalse(lb.contains("raizQuadrada"), "alias PT não contamina o Kof: " + lb);
    }

    // ============ HOVER ============

    @Test
    void hoverBuiltinSurfaceKeepsCanonicalIdentity(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("H.ptkf");
        String text = "principal() {\n    escreva(\"oi\")\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(pt, text)), frame(req(1, "textDocument/hover", pt, 1, 5)));
        String v = hoverValue(resultOf(out, 1));
        assertNotNull(v, "hover em builtin PT");
        assertTrue(v.startsWith("**escreva**"), "surface label escreva: " + v);
        assertTrue(v.contains("print"), "identidade canônica print visível: " + v);
    }

    @Test
    void hoverMethodReceiverAwareResolvesByCategory(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("R.ptkf");
        String text = "principal() {\n    var s = \"ola\"\n    escrevaln(s.tamanho())\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(pt, text)), frame(req(1, "textDocument/hover", pt, 2, 17)));
        String v = hoverValue(resultOf(out, 1));
        assertNotNull(v, "hover de método receiver-aware (String)");
        assertTrue(v.contains("STRING"), "categoria STRING do receiver: " + v);
        assertTrue(v.contains("length"), "canônico length: " + v);
    }

    @Test
    void hoverMethodOnListSameAliasDifferentCategory(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("R2.ptkf");
        String text = "principal() {\n    var xs = listaDe(1, 2)\n    escrevaln(xs.tamanho())\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(pt, text)), frame(req(1, "textDocument/hover", pt, 2, 18)));
        String v = hoverValue(resultOf(out, 1));
        assertNotNull(v, "hover de método em List");
        assertTrue(v.contains("LIST"), "categoria LIST: " + v);
        assertTrue(v.contains("size"), "canônico size (não length) para coleção: " + v);
    }

    @Test
    void hoverUserIdentifierNeverTranslated(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("HU.ptkf");
        String text = "principal() {\n    val minhaConta = 1\n    escrevaln(minhaConta)\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(pt, text)), frame(req(1, "textDocument/hover", pt, 2, 15)));
        String v = hoverValue(resultOf(out, 1));
        assertNotNull(v, "hover em símbolo do usuário");
        assertTrue(v.contains("minhaConta"), "identificador intacto: " + v);
    }

    // ============ SIGNATURE HELP ============

    @Test
    void signatureHelpSurfaceLabelCanonicalParams(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("S.ptkf");
        String text = "principal() {\n    matematica.raizQuadrada(\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(pt, text)), frame(req(1, "textDocument/signatureHelp", pt, 1, 28)));
        String r = resultOf(out, 1);
        assertNotNull(r, "signatureHelp .ptkf");
        assertTrue(r.contains("raizQuadrada("), "label na superfície PT: " + r);
        assertFalse(r.contains("sqrt("), "canônico `sqrt(` não é mostrado na superfície: " + r);
        assertTrue(r.contains("Double x"), "parâmetro continua canônico: " + r);
    }

    @Test
    void signatureHelpStaysEnglishOnKof(@TempDir Path dir) throws Exception {
        Path kf = dir.resolve("S.kf");
        String text = "main() {\n    math.sqrt(\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(kf, text)), frame(req(1, "textDocument/signatureHelp", kf, 1, 14)));
        assertTrue(resultOf(out, 1).contains("sqrt(Double x)"), "Kof inalterado");
    }

    // ============ DEFINITION / SYMBOLS (source-authority §20) ============

    @Test
    void definitionUsesSurfaceSpellingAndRealSourceRange(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("D.ptkf");
        String text = "principal() {\n    escrevaln(oi())\n}\nInt oi() {\n    retorna 1\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(pt, text)), frame(req(1, "textDocument/definition", pt, 1, 15)));
        String r = resultOf(out, 1);
        assertNotNull(r, "definition em função de usuário");
        assertTrue(r.contains("\"character\":4") && r.contains("\"character\":6"),
                "range da grafia real `oi` (2 chars, col 4..6): " + r);
    }

    @Test
    void documentSymbolShowsSurfaceNameNeverCanonical(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("DS.ptkf");
        String text = "principal() {\n}\nInt minhaFuncao() {\n    retorna 1\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(pt, text)),
                frame("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/documentSymbol\",\"params\":"
                        + "{\"textDocument\":{\"uri\":\"" + uriOf(pt) + "\"}}}"));
        String r = resultOf(out, 1);
        assertNotNull(r, "documentSymbol .ptkf");
        assertTrue(labels(r).contains("minhaFuncao"), "nome do usuário intacto: " + labels(r));
        assertTrue(labels(r).contains("principal"), "entrada na superfície PT: " + labels(r));
        assertFalse(labels(r).contains("main"), "canônico `main` não vaza: " + labels(r));
    }

    // ============ RENAME (§18 — semântico, alias recusado) ============

    @Test
    void renameUserSymbolWorksAcrossSurface(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("RN.ptkf");
        String text = "principal() {\n    val minhaConta = 1\n    escrevaln(minhaConta)\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(pt, text)),
                frame(reqWithNewName(1, pt, 1, 9, "contaPrincipal")));
        String r = resultOf(out, 1);
        assertNotNull(r, "rename de símbolo do usuário");
        assertTrue(r.contains("contaPrincipal"), "novo nome aplicado: " + r);
    }

    @Test
    void renameRefusesPortugueseSurfaceWords(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("RX.ptkf");
        String text = "principal() {\n    escreva(\"oi\")\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(pt, text)),
                frame(reqWithNewName(1, pt, 1, 5, "algo")));
        assertNull(resultOf(out, 1), "rename de builtin PT é recusado (guarda honesta)");
    }

    // ============ MACHINE CONTRACT (§19) — só a etiqueta humana muda ============

    @Test
    void machineFieldsStayCanonicalEnglishForSurfaceProgram(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("MC.ptkf");
        String text = "principal() {\n    val x = (1 + 2\n}\n";
        String out = run(frame(initReq(null)), frame(didOpen(pt, text)));
        String body = DiagHarness.diagnosticsBody(out, uriOf(pt));
        assertFalse(codes(body).isEmpty(), "diagnóstico presente no .ptkf");
        assertTrue(body.contains("\"message\""), "mensagem canônica EN publicada (LSP não localiza)");
        assertTrue(body.contains("\"range\""), "range presente (posição é autoridade do source)");
    }

    // ============ COMPILERIMPORTS — `.ptkf` (§21-23, §38) ============

    @Test
    void siblingPtkfImportResolvesAndCompiles(@TempDir Path dir) throws Exception {
        Path util = dir.resolve("util");
        Files.createDirectories(util);
        Files.writeString(util.resolve("Mat.ptkf"),
                "pacote util\ninteiro dobro(inteiro x) {\n    retorna x * 2\n}\n");
        Path main = dir.resolve("Main.ptkf");
        Files.writeString(main,
                "importa util.Mat\nprincipal() {\n    escrevaln(dobro(21))\n}\n");
        String out = run(frame(initReq(dir)), frame(didOpen(main, Files.readString(main))));
        List<String> c = codes(DiagHarness.diagnosticsBody(out, uriOf(main)));
        assertEquals(List.of(), c, "import .ptkf deve resolver sem diagnóstico: " + c);
    }

    @Test
    void mixedKofPtkfImportResolves(@TempDir Path dir) throws Exception {
        Path util = dir.resolve("util");
        Files.createDirectories(util);
        Files.writeString(util.resolve("Mat.kf"),
                "package util\nInt twice(Int x) {\n    return x * 2\n}\n");
        Path main = dir.resolve("Main.ptkf");
        Files.writeString(main,
                "importa util.Mat\nprincipal() {\n    escrevaln(twice(21))\n}\n");
        String out = run(frame(initReq(dir)), frame(didOpen(main, Files.readString(main))));
        List<String> c = codes(DiagHarness.diagnosticsBody(out, uriOf(main)));
        assertEquals(List.of(), c, "PTKOF→KOF resolve: " + c);
    }

    @Test
    void packageStylePtkfImportResolves(@TempDir Path dir) throws Exception {
        Path util = dir.resolve("util");
        Files.createDirectories(util);
        Files.writeString(util.resolve("textos.ptkf"),
                "pacote util\ntexto grita(texto s) {\n    retorna s\n}\n");
        Path main = dir.resolve("main.ptkf");
        Files.writeString(main,
                "importa util.textos\nprincipal() {\n    escrevaln(grita(\"oi\"))\n}\n");
        String out = run(frame(initReq(dir)), frame(didOpen(main, Files.readString(main))));
        List<String> c = codes(DiagHarness.diagnosticsBody(out, uriOf(main)));
        assertEquals(List.of(), c, "import de pacote .ptkf resolve: " + c);
    }

    @Test
    void importedSymbolDefinitionAndHoverCrossFile(@TempDir Path dir) throws Exception {
        Path util = dir.resolve("util");
        Files.createDirectories(util);
        Files.writeString(util.resolve("Mat.ptkf"),
                "pacote util\ninteiro dobro(inteiro x) {\n    retorna x * 2\n}\n");
        Path main = dir.resolve("Main.ptkf");
        String text = "importa util.Mat\nprincipal() {\n    escrevaln(dobro(21))\n}\n";
        String out = run(frame(initReq(dir)), frame(didOpen(main, text)),
                frame(req(1, "textDocument/definition", main, 2, 15)));
        String r = resultOf(out, 1);
        assertNotNull(r, "definition cross-file em símbolo importado (irmão .ptkf)");
        assertTrue(r.contains("Mat.ptkf"), "aponta para o arquivo real: " + r);
    }

    private static List<String> codes(String body) {
        return DiagHarness.codes(body);
    }

    // O exemplo OFICIAL de import `.ptkf` (examples/portukof/imports) compila
    // limpo em modo projeto — §38 prova ponta-a-ponta na estrutura versionada.
    @Test
    void officialPtkfImportExampleResolves() throws Exception {
        Path root = locateImportExample();
        Path main = root.resolve("main.ptkf");
        String out = run(frame(initReq(root)), frame(didOpen(main, Files.readString(main))));
        List<String> c = codes(DiagHarness.diagnosticsBody(out, uriOf(main)));
        assertEquals(List.of(), c, "exemplo oficial de import .ptkf deve compilar limpo: " + c);
    }

    private static Path locateImportExample() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path cand = dir.resolve("examples").resolve("portukof").resolve("imports");
            if (Files.isDirectory(cand)) return cand;
        }
        throw new IllegalStateException("examples/portukof/imports não encontrado");
    }

    /** Reaproveita o parser de publishDiagnostics do teste F7.1 sem duplicar. */
    private static final class DiagHarness {
        static List<String> codes(String inner) {
            List<String> out = new ArrayList<>();
            var m = Pattern.compile("\"code\":\"([A-Z0-9]+)\"").matcher(inner);
            while (m.find()) out.add(m.group(1));
            return out;
        }

        static String diagnosticsBody(String raw, String uri) {
            String ascii = new String(raw.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
            int pos = 0;
            while (true) {
                int h = ascii.indexOf("Content-Length: ", pos);
                if (h < 0) break;
                int sep = ascii.indexOf("\r\n\r\n", h);
                if (sep < 0) break;
                int len = Integer.parseInt(ascii.substring(h + 16, sep).trim());
                String body = ascii.substring(sep + 4, Math.min(sep + 4 + len, ascii.length()));
                pos = sep + 4 + len;
                if (body.contains("publishDiagnostics") && body.contains("\"" + uri + "\"")) {
                    int start = body.indexOf("\"diagnostics\":[");
                    if (start >= 0) {
                        int close = body.indexOf(']', start);
                        return body.substring(start + "\"diagnostics\":[".length(), close).trim();
                    }
                }
            }
            throw new AssertionError("nenhuma publishDiagnostics para " + uri + ":\n" + raw);
        }
    }
}
