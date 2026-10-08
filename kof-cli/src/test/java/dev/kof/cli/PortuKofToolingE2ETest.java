package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-PORTUKOF F7 (07/10) — tooling de primeira classe para `.ptkf`.
 *
 * Regra de ouro: PortuKof muda a SUPERFÍCIE, nunca a SEMÂNTICA nem o CONTRATO
 * DE MÁQUINA. Este teste trava três coisas:
 *
 *   (1) A EXTENSÃO `.ptkf` decide o perfil em TODO a cadeia de análise do LSP
 *       (mesma pipeline canônica do `kof check`; o `analyze` não pode mais
 *       colapsar `.ptkf` → `LspMain.kf` e lexear inglês).
 *   (2) O CONTRATO DE MÁQUINA (code / severity / range / mensagem canônica EN)
 *       é IDÊNTICO entre o par Kof ↔ PortuKof semanticamente equivalente — só a
 *       apresentação humana pode divergir, e o LSP/publicar usa o EN canônico.
 *   (3) O formatter NUNCA transpila: sobre `.ptkf` o AST-printer (superfície EN
 *       hardcoded) recusa e o resultado preserva a superfície PT byte-a-byte;
 *       round-trip estável; strings/comentários/identificadores intocados.
 */
class PortuKofToolingE2ETest {

    // ---------------- harness LSP (byte-safe, espelha LspProjectDiagnosticsE2ETest) ----
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

    private static String str(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    /** Corpo `publishDiagnostics` da URI pedida (null se ausente). */
    private static String diagnosticsBody(String raw, String uri) {
        byte[] all = raw.getBytes(StandardCharsets.UTF_8);
        String ascii = new String(all, StandardCharsets.ISO_8859_1);
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

    private static List<String> codes(String inner) {
        List<String> out = new ArrayList<>();
        var m = java.util.regex.Pattern.compile("\"code\":\"([A-Z0-9]+)\"").matcher(inner);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static List<String> messages(String inner) {
        List<String> out = new ArrayList<>();
        var m = java.util.regex.Pattern
                .compile("\"message\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(inner);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static List<String> severities(String inner) {
        List<String> out = new ArrayList<>();
        var m = java.util.regex.Pattern.compile("\"severity\":(\\d+)").matcher(inner);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    // (1) A EXTENSÃO `.ptkf` escolhe o perfil: programa PortuKof VÁLIDO não dá
    //     diagnóstico na superfície `.ptkf`, mas os MESMOS bytes numa `.kf` dão
    //     (prova de que a análise lexe PortuKOF por extensão, não inglês).
    private static final String PT_VALID = "principal() {\n    se (verdadeiro) { escreva(\"oi\") }\n}\n";

    @Test
    void ptkfIsValidOnItsOwnSurfaceButNotAsEnglishKof(@TempDir Path dir) throws Exception {
        Path pt = dir.resolve("Main.ptkf");
        String ptUri = uriOf(pt);
        String outPt = run(frame(initReq(null)), frame(didOpen(pt, PT_VALID)));
        assertEquals(List.of(), codes(diagnosticsBody(outPt, ptUri)),
                ".ptkf deve ser analisado como PortuKof (zero diagnóstico no programa válido)");

        Path kf = dir.resolve("Main.kf");
        String kfUri = uriOf(kf);
        String outKf = run(frame(initReq(null)), frame(didOpen(kf, PT_VALID)));
        assertFalse(codes(diagnosticsBody(outKf, kfUri)).isEmpty(),
                "os mesmos bytes como .kf NÃO são Kof válido — logo é a extensão que decide o perfil");
    }

    // (2) CONTRATO DE MÁQUINA idêntico no par Kof ↔ PortuKof equivalente: mesmo
    //     código, mesma severidade, mesma mensagem CANÔNICA EN (o LSP nunca
    //     publica PT — §16/§17), e um range presente.
    @Test
    void machineContractIsIdenticalAcrossSurfaces(@TempDir Path dir) throws Exception {
        String kfErr = "main() {\n    var x = (1 + 2\n}\n";
        String ptErr = "principal() {\n    var x = (1 + 2\n}\n";

        Path kf = dir.resolve("M.kf");
        String kfBody = diagnosticsBody(run(frame(initReq(null)), frame(didOpen(kf, kfErr))), uriOf(kf));
        Path pt = dir.resolve("M.ptkf");
        String ptBody = diagnosticsBody(run(frame(initReq(null)), frame(didOpen(pt, ptErr))), uriOf(pt));

        assertFalse(codes(kfBody).isEmpty(), "pré-condição: o .kf dá diagnóstico");
        assertEquals(codes(kfBody), codes(ptBody), "código canônico idêntico nas duas superfícies");
        assertEquals(severities(kfBody), severities(ptBody), "severidade idêntica");
        assertEquals(messages(kfBody), messages(ptBody), "mensagem CANÔNICA (EN) idêntica — LSP não localiza");
        assertTrue(messages(ptBody).stream().allMatch(s -> !s.isBlank()), "mensagem canônica presente");
        assertTrue(kfBody.contains("\"range\"") && ptBody.contains("\"range\""),
                "range presente nas duas faces (posição é autoridade do SOURCE)");
    }

    // (3) FORMATTER profile-aware (F7.3): o AST-printer agora formata .ptkf
    //     DIRETO pela AST na superfície do perfil (sem transpilar para EN).
    //     CLI e LSP usam a AST como caminho principal; strings/comentários/
    //     identificadores intactos; round-trip idempotente.
    @Test
    void astPrinterRendersPortukofSurfaceDirectly() {
        String out = dev.kof.compiler.KofFormatter.format(PT_VALID, "Main.ptkf");
        assertNotNull(out, "F7.3: KofFormatter formata .ptkf diretamente pela AST");
        assertTrue(out.contains("principal"), "superfície PT preservada: " + out);
        assertTrue(out.contains("escreva"), out);
        assertFalse(out.contains("main"), "AST-printer não transipila principal→main: " + out);
        assertFalse(out.contains("print("), "AST-printer não transipila escreva→print: " + out);
        // EN segue o caminho AST normal (não regrediu)
        assertTrue(dev.kof.compiler.KofFormatter.format("main() {\n}\n", "Main.kf") != null,
                "Kof .kf continua pelo AST-printer");
    }

    // (3.1) LSP textDocument/formatting sobre `.ptkf`: o LSP chama direto
    //       KofFormatter.format com o fileName real (F7.1 devolvia null →
    //       respondia sem edit; F7.3 devolve o edit na superfície PT).
    @Test
    void lspFormattingReturnsSurfaceEditForPtkf(@TempDir Path dir) throws Exception {
        Path main = dir.resolve("Main.ptkf");
        String messy = "principal() {\n  se (verdadeiro) { escreva( 1 ) }\n}\n";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"textDocument/formatting\",\"params\":{\"textDocument\":{\"uri\":\""
                + uriOf(main) + "\"}}}";
        String out = run(frame(initReq(dir)), frame(didOpen(main, messy)), frame(req));
        assertTrue(out.contains("\"newText\""), "LSP emite edit de formatação para .ptkf: " + out);
        assertTrue(out.contains("principal()"), out);
        assertTrue(out.contains("escreva(1)"), "normalização de espaçamento pela AST: " + out);
        assertFalse(out.contains("main"), "nunca transpila para EN: " + out);
    }

    @Test
    void formatterPreservesPortugueseSurfaceBytes() {
        String src = "principal() {\nescreva(\"Oi João tamanho\")\n}\n";
        String once = Fmt.format(src, "Main.ptkf");
        // superfície PT preservada (nunca virou main/print)
        assertTrue(once.contains("principal"), once);
        assertTrue(once.contains("escreva"), once);
        assertFalse(once.contains("print("), "formatter NÃO pode transpilar escreva→print: " + once);
        assertFalse(once.contains("main"), "formatter NÃO pode transpilar principal→main: " + once);
        // string byte-a-byte (conteúdo, inclusive 'João'/'tamanho', intocado)
        assertTrue(once.contains("\"Oi João tamanho\""), once);
        // round-trip estável F(F(x)) == F(x)
        String twice = Fmt.format(once, "Main.ptkf");
        assertEquals(once, twice, "formatter .ptkf deve ser idempotente");
    }

    @Test
    void formatterPreservesCommentsAndUserIdentifiers() {
        String src = "// principal chama escreva\nprincipal() {\nval minhaConta=1\nescreva(minhaConta)\n}\n";
        String out = Fmt.format(src, "Main.ptkf");
        assertTrue(out.contains("// principal chama escreva"), "comentário nunca traduzido: " + out);
        assertTrue(out.contains("minhaConta"), "identificador de usuário preservado: " + out);
        assertTrue(out.contains("escreva"), "builtin PT preservado: " + out);
    }

    // (4) MODO PROJETO (espelho): um `.ptkf` VÁLIDO e autossuficiente dentro de
    //     um projeto (com `kof.toml`) é espelhado e analisado como PortuKof — a
    //     causa era o `analyze` escrever o buffer como `LspMain.kf` e lexear
    //     inglês. Zero diagnóstico prova que o espelho + a extensão preservam o
    //     perfil. (Import de irmão `.ptkf` depende de `CompilerImports` — F7.2.)
    @Test
    void ptkfValidatedAsPortuKofInProjectMirrorMode(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("kof.toml"), "[project]\nname = \"pk\"\n");
        Path src = Files.createDirectories(dir.resolve("src"));
        Path main = src.resolve("Main.ptkf");
        String text = "principal() {\n    se (verdadeiro) { escreva(\"oi\") }\n}\n";
        String out = run(frame(initReq(dir)), frame(didOpen(main, text)));
        List<String> c = codes(diagnosticsBody(out, uriOf(main)));
        assertEquals(List.of(), c,
                ".ptkf no modo projeto deve ser analisado como PortuKof (zero diagnóstico): " + c);
    }

    // (5) Os exemplos oficiais `.ptkf` são VÁLIDOS: cada arquivo sob
    //     examples/portukof/*.ptkf compila com zero erro na análise LSP — prova
    //     de que a documentação não afirma sintaxe inexistente (§44/§45).
    @Test
    void officialPtkfExamplesHaveNoDiagnostics() throws Exception {
        Path root = locateExamples();
        List<Path> ex;
        try (var s = Files.list(root)) {
            ex = s.filter(p -> p.toString().endsWith(".ptkf")).sorted().toList();
        }
        assertFalse(ex.isEmpty(), "há exemplos .ptkf em " + root);
        for (Path f : ex) {
            String text = Files.readString(f);
            String out = run(frame(initReq(null)), frame(didOpen(f, text)));
            List<String> c = codes(diagnosticsBody(out, uriOf(f)));
            assertEquals(List.of(), c, "exemplo .ptkf deve compilar limpo como PortuKof: " + f);
        }
    }

    private static Path locateExamples() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path cand = dir.resolve("examples").resolve("portukof");
            if (Files.isDirectory(cand)) return cand;
        }
        throw new IllegalStateException("examples/portukof não encontrado a partir de "
                + System.getProperty("user.dir"));
    }
}
