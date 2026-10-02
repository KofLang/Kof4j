package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #686 — a `switch` used as a STATEMENT never checked exhaustiveness (sealed /
 * Bool / enum); only the switch-EXPRESSION form did (SEM081 / SEM032). A
 * missing case with no `default` was silently accepted and became a runtime
 * no-op. These tests pin the statement form to the same contract.
 */
class SwitchStmtExhaustivenessTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SHAPES = """
            sealed interface Forma
            record Circulo(Int r) implements Forma
            record Quadrado(Int l) implements Forma
            record Triangulo(Int b, Int h) implements Forma
            """;

    private CompilationResult compile(Path dir, String name, String body) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, body);
        return driver.compileSources(List.of(f), dir.resolve(name + "-out"), Target.JVM, dir);
    }

    private static boolean has(CompilationResult r, String code) {
        return r.diagnostics().getDiagnostics().stream().anyMatch(d -> code.equals(d.code()));
    }

    @Test
    void sealedStatementMissingCaseIsSem081(@TempDir Path t) throws Exception {
        CompilationResult r = compile(t, "Sealed.kf", SHAPES + """
                main() {
                    var f: Forma = Circulo(1)
                    switch (f) {
                        case Circulo c:
                            println("c")
                        case Quadrado q:
                            println("q")
                    }
                }
                """);
        assertFalse(r.success(), "faltando Triangulo sem default deve falhar");
        assertTrue(has(r, "SEM081"), "SEM081 esperado, veio: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void sealedStatementAllCasesCompiles(@TempDir Path t) throws Exception {
        CompilationResult r = compile(t, "SealedOk.kf", SHAPES + """
                main() {
                    var f: Forma = Circulo(1)
                    switch (f) {
                        case Circulo c:
                            println("c")
                        case Quadrado q:
                            println("q")
                        case Triangulo tr:
                            println("t")
                    }
                }
                """);
        assertTrue(r.success(), "todos os casos cobertos deve compilar: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void sealedStatementWithDefaultCompiles(@TempDir Path t) throws Exception {
        CompilationResult r = compile(t, "SealedDef.kf", SHAPES + """
                main() {
                    var f: Forma = Circulo(1)
                    switch (f) {
                        case Circulo c:
                            println("c")
                        default:
                            println("outro")
                    }
                }
                """);
        assertTrue(r.success(), "com default deve compilar: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void boolStatementMissingValueIsSem032(@TempDir Path t) throws Exception {
        CompilationResult r = compile(t, "Bool.kf", """
                main() {
                    var b = true
                    switch (b) {
                        case true:
                            println("t")
                    }
                }
                """);
        assertFalse(r.success(), "Bool sem cobrir false/null sem default deve falhar");
        assertTrue(has(r, "SEM032"), "SEM032 esperado, veio: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void boolStatementBothValuesCompiles(@TempDir Path t) throws Exception {
        CompilationResult r = compile(t, "BoolOk.kf", """
                main() {
                    var b = true
                    switch (b) {
                        case true:
                            println("t")
                        case false:
                            println("f")
                    }
                }
                """);
        assertTrue(r.success(), "true+false deve compilar: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void emptyDefaultCountsAsPresent(@TempDir Path t) throws Exception {
        // `default: }` (corpo vazio) é um default PRESENTE — não deve virar SEM032.
        CompilationResult r = compile(t, "BoolEmptyDef.kf", """
                main() {
                    var b = true
                    switch (b) {
                        case true:
                            println("t")
                        default:
                    }
                }
                """);
        assertTrue(r.success(), "default vazio conta como exaustivo: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void enumStatementMissingConstantStillSem031(@TempDir Path t) throws Exception {
        // Enum já era checado no lowering (SEM031) — o #686 NÃO deve roubar
        // essa autoridade (senão a análise aborta e SEM031 some).
        CompilationResult r = compile(t, "Enum.kf", """
                enum Cor { VERMELHO, VERDE, AZUL }

                main() {
                    var c = Cor.VERMELHO
                    switch (c) {
                        case Cor.VERMELHO:
                            println("v")
                        case Cor.VERDE:
                            println("g")
                    }
                }
                """);
        assertFalse(r.success(), "enum sem cobrir AZUL nem default deve falhar");
        assertTrue(has(r, "SEM031"), "SEM031 esperado, veio: " + r.diagnostics().getDiagnostics());
    }
}
