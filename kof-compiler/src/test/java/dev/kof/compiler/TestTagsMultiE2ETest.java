package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §7.1 multi-tag ({@code kof-testing-platform-plan}, 06/10): o valor de
 * {@code --tag} é uma lista separada por vírgula e casa por DISJUNÇÃO (OR) —
 * {@code --tag smoke,ui} mantém todo teste que carregue qualquer uma das tags.
 * Um valor simples (sem vírgula) é o caso de uma tag só e mantém o contrato
 * histórico byte a byte (coberto por {@link TestTagsE2ETest}).
 *
 * <p>Separado de {@code TestTagsE2ETest} por responsabilidade e para manter o
 * teto de 500 linhas do gate de higiene de testes.</p>
 */
class TestTagsMultiE2ETest {

    @TempDir Path tmp;

    private record Run(boolean success, String diags, String output) {}

    private Run compileAndRunJvm(String code, String filterTag) throws Exception {
        System.clearProperty("kof.test.tag");
        System.setProperty("kof.test.tag", filterTag);
        try {
            Path src = tmp.resolve("T" + System.nanoTime() + ".kf");
            Files.writeString(src, code);
            Path out = Files.createTempDirectory(tmp, "o");
            CompilerDriver driver = new CompilerDriver();
            CompilationResult r = driver.compileForTests(src, out, Target.JVM);
            StringBuilder diags = new StringBuilder();
            r.diagnostics().getDiagnostics().forEach(d -> diags.append(d.code()).append(' ')
                    .append(d.message()).append('\n'));
            if (!r.success()) return new Run(false, diags.toString(), "");
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
            int ec = proc.waitFor();
            return new Run(ec == 0, diags.toString() + (ec == 0 ? "" : "exit=" + ec + "\n"), output);
        } finally {
            System.clearProperty("kof.test.tag");
        }
    }

    private static final String MULTI = """
            test "soma", "smoke" {
                assert(2 + 2 == 4)
            }
            test "janela", "ui" {
                assert("kof" == "kof")
            }
            test "banco", "db" {
                assert(true)
            }
            """;

    @Test
    void multiTagFilterKeepsTheUnionOfMatchingTests() throws Exception {
        Run r = compileAndRunJvm(MULTI, "smoke,ui");
        assertTrue(r.success(), "must compile: " + r.diags());
        assertEquals("kof test: tag 'smoke,ui' (2 of 3)\nPASS soma\nPASS janela\n────────\n"
                + "0 failed of 2 tests", r.output().trim(),
                "§7.1 multi-tag: disjunção (OR) — smoke e ui casam, db não");
    }

    @Test
    void multiTagFilterIgnoresSpacesAroundEachTag() throws Exception {
        Run r = compileAndRunJvm(MULTI, "smoke, ui");
        assertTrue(r.success(), "must compile: " + r.diags());
        assertEquals("kof test: tag 'smoke, ui' (2 of 3)\nPASS soma\nPASS janela\n────────\n"
                + "0 failed of 2 tests", r.output().trim(),
                "espaço em volta da tag não muda o veredito (trim)");
    }

    @Test
    void multiTagFilterKeepsTheKnownOnesEvenWithAnUnknownInTheList() throws Exception {
        Run r = compileAndRunJvm(MULTI, "ui,db,nope");
        assertTrue(r.success(), "must compile: " + r.diags());
        assertEquals("kof test: tag 'ui,db,nope' (2 of 3)\nPASS janela\nPASS banco\n────────\n"
                + "0 failed of 2 tests", r.output().trim(),
                "uma tag desconhecida na lista não invalida as que casam (OR)");
    }

    @Test
    void multiTagFilterAllUnknownIsAnHonestNoOp() throws Exception {
        Run r = compileAndRunJvm(MULTI, "alpha,beta");
        assertTrue(r.success(), "must compile: " + r.diags());
        assertEquals("kof test: tag 'alpha,beta' (0 of 3)\nno tests with tag 'alpha,beta' (of 3)",
                r.output().trim(), "R6: nenhuma casa = recusa nomeada, nunca silêncio");
    }
}
