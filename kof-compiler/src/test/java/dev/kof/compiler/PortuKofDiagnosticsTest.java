package dev.kof.compiler;

import dev.kof.compiler.lang.PortuKofDiagnostics;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-PORTUKOF F6 (07/10) — diagnósticos localizados em pt-BR. Regra de ouro: o
 * CÓDIGO canônico (LEX/PARSE/SEM…) NUNCA muda; `severity`, linha, coluna e o
 * `message` canônico em inglês são preservados. Somente a forma HUMANA
 * (`localizedMessage`/`format`) muda, e só para a superfície `.ptkf` (detecção
 * por EXTENSÃO, nunca por conteúdo). Machine output (`message`/`code`/`--json`/
 * LSP) permanece canônico.
 */
class PortuKofDiagnosticsTest {

    private static Diagnostic byCode(List<Diagnostic> ds, String code) {
        return ds.stream().filter(d -> code.equals(d.code())).findFirst()
                .orElseThrow(() -> new AssertionError(
                        "sem diagnóstico " + code + "; obtidos=" + codes(ds)));
    }

    private static List<String> codes(List<Diagnostic> ds) {
        return ds.stream().map(Diagnostic::code).toList();
    }

    private static List<Diagnostic> diagsOf(String src, String fileName) throws Exception {
        Path root = Files.createTempDirectory("ptkf-diag-");
        Path f = root.resolve(fileName);
        Files.writeString(f, src);
        CompilationResult r = new CompilerDriver().compileSources(List.of(f),
                Files.createTempDirectory("ptkf-diag-out-"), Target.SCRIPT, root);
        return r.diagnostics().getDiagnostics();
    }

    /** Mesma semântica de erro nas duas superfícies: código/severidade/posição
     *  idênticos; `message` canônico idêntico (EN); só `localizedMessage` difere. */
    private static void assertParity(String kofSrc, String ptkfSrc, String code) throws Exception {
        Diagnostic en = byCode(diagsOf(kofSrc, "Main.kf"), code);
        Diagnostic pt = byCode(diagsOf(ptkfSrc, "Main.ptkf"), code);
        assertEquals(code, en.code(), "código EN");
        assertEquals(code, pt.code(), "código PT deve ser o MESMO canônico");
        assertEquals(en.severity(), pt.severity(), "severidade preservada");
        assertEquals(en.line(), pt.line(), "linha preservada");
        assertEquals(en.column(), pt.column(), "coluna preservada");
        assertEquals(en.message(), pt.message(), "message canônico (EN) idêntico nas duas faces");
        assertEquals(en.args(), pt.args(), "argumentos estruturados idênticos");
        assertEquals(en.message(), en.localizedMessage(), "Kof permanece em inglês");
        assertNotEquals(pt.message(), pt.localizedMessage(),
                "PortuKof deve localizar a forma humana de " + code);
        assertFalse(pt.localizedMessage().isEmpty());
    }

    @Test
    void lexicalUnexpectedCharacterLocalized() throws Exception {
        assertParity("main() {\n    #\n}\n", "principal() {\n    #\n}\n", "LEX005");
    }

    @Test
    void lexicalUnterminatedStringLocalized() throws Exception {
        assertParity("main() {\n    var s = \"abc\n}\n",
                "principal() {\n    var s = \"abc\n}\n", "LEX002");
    }

    @Test
    void dynamicArgIsPreservedInPortuguese() throws Exception {
        // LEX005 carrega o caractere como ARGUMENTO ESTRUTURADO; a forma PT contém
        // '#' (conteúdo preservado) — sem regex, sem traduzir a mensagem final.
        Diagnostic pt = byCode(diagsOf("principal() {\n    #\n}\n", "Main.ptkf"), "LEX005");
        assertEquals(List.of("#"), pt.args(), "argumento estruturado capturado");
        assertEquals("caractere inesperado: '#'", pt.localizedMessage());
        assertEquals("Unexpected character: '#'", pt.message());
    }

    @Test
    void kofSurfaceNeverLocalizesEvenForMappedCodes() throws Exception {
        Diagnostic en = byCode(diagsOf("main() {\n    #\n}\n", "Main.kf"), "LEX005");
        assertEquals("Unexpected character: '#'", en.localizedMessage());
        assertTrue(en.format().contains("Unexpected character"), en.format());
    }

    @Test
    void formatRendersPortugueseOnlyForPtKf() throws Exception {
        Diagnostic pt = byCode(diagsOf("principal() {\n    #\n}\n", "Main.ptkf"), "LEX005");
        assertTrue(pt.format().contains("caractere inesperado"), pt.format());
        assertTrue(pt.format().contains("[LEX005]"), "código preservado no format: " + pt.format());
    }

    @Test
    void catalogIsCodeKeyedCompleteAndPlaceholderSafe() {
        var catalog = PortuKofDiagnostics.catalog();
        assertFalse(catalog.isEmpty(), "catálogo não vazio");
        var ph = java.util.regex.Pattern.compile("\\{(\\d+)\\}");
        for (var e : catalog.entrySet()) {
            assertTrue(e.getKey().matches("(LEX|PARSE|SEM)[0-9]+"),
                    "chave deve ser código canônico: " + e.getKey());
            assertFalse(e.getValue().isEmpty(), "sem variantes: " + e.getKey());
            for (String[] v : e.getValue()) {
                assertFalse(v[1].isBlank(), "template PT vazio em " + e.getKey());
                var en = ph.matcher(v[0]);
                var pt = ph.matcher(v[1]);
                var enIdx = new java.util.TreeSet<String>();
                var ptIdx = new java.util.TreeSet<String>();
                while (en.find()) enIdx.add(en.group());
                while (pt.find()) ptIdx.add(pt.group());
                assertEquals(enIdx, ptIdx, "paridade de placeholders " + e.getKey());
            }
        }
    }

    @Test
    void localizeKeysOnCanonicalMessageNotProsa() {
        // chave = código + mensagem EN canônica + args; PT só sai quando a variante
        // EN, renderizada com os MESMOS args, reproduz exatamente a mensagem.
        assertEquals("caractere inesperado: 'x'",
                PortuKofDiagnostics.localize("LEX005", "M.ptkf", "Unexpected character: 'x'", List.of("x")));
        // superfície KOF => null (fica EN); conteúdo do arg nunca é tocado
        assertEquals(null,
                PortuKofDiagnostics.localize("LEX005", "M.kf", "Unexpected character: 'x'", List.of("x")));
        // mensagem que nenhuma variante reproduz => null (NUNCA tradução aproximada)
        assertEquals(null,
                PortuKofDiagnostics.localize("LEX005", "M.ptkf", "Some message no template renders", List.of("x")));
    }

    @Test
    void staticSemTemplateLocalizesThroughCatalog() {
        String en = "List.zip takes exactly one List argument";
        // superfície KOF => null (o chamador mantém a EN canônica)
        assertEquals(null, PortuKofDiagnostics.localize("SEM025", "M.kf", en, List.of()),
                "Kof não localiza (null => mantém EN)");
        String pt = PortuKofDiagnostics.localize("SEM025", "M.ptkf", en, List.of());
        assertNotEquals(en, pt, "PortuKof localiza a forma humana");
        assertTrue(pt.contains("List.zip"), pt);
    }

    @Test
    void parseDiagnosticLocalizedEndToEnd() throws Exception {
        // PARSE real (não só LEX): mesma posição/código/message EN nas duas faces;
        // só a forma humana PT aparece na superfície .ptkf.
        assertParity("main() {\n    var x = (1 + 2\n}\n",
                "principal() {\n    var x = (1 + 2\n}\n", "PARSE040");
    }
}
