package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-TROOL (19/09, DECISIONS.md d6cf5042) — a LEI tres-estado de {@code Troolean}
 * em {@code &&}/{@code ||}/{@code !}.
 *
 * <p>{@code Bool} voltou a ser dois-valores: {@code Bool?} nao existe mais —
 * quem precisa de verdadeiro/falso/desconhecido escreve {@code Troolean}
 * (SEM095 força a migração). O lowering dobra cada {@code &&}/{@code ||} com
 * lado {@code Troolean} numa cadeia {@code IfExpr} com temporarios
 * {@code $klt} (nao em IR, para o JS dobrar o short-circuit junto), e o
 * tipo do resultado passa a ser {@code Nullable(BOOL)} — Kleene de verdade:
 * {@code true && null = null}, {@code false || null = null}.
 *
 * <p>Os goldens desta matrix vem do ORACULO MEDIDO nos probes
 * {@code /tmp/opencode/trool/{and9,or9}} (T/F/U via {@code show3}) com o jar
 * da unidade landed (kof-cli-0.4.7-beta, 19/09 ~20:05) — IDENTICOS em
 * JVM+JS+Script+Native-x86-64. Aqui se provam JVM+Script+JS (oNative fica com
 * as E2Es nativas existentes, §306).
 */
class TrooleanLawE2ETest extends NullablePrimitiveContractSupport {

    private static final String TROOLS = """
            Troolean nb() { return null }
            Troolean fb() { return false }
            Troolean tb() { return true }
            """;

    private CompilationResult compileOnly(Path tempDir, String name, String source) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, source);
        return driver.compile(file, tempDir.resolve("out-" + System.nanoTime()), Target.JVM);
    }

    private static String show3(List<String> exprs) {
        StringBuilder sb = new StringBuilder(TROOLS).append("""
                show3(x: Troolean) {
                    if (x == null) { println("U") } else if (x) { println("T") } else { println("F") }
                }
                main() {
                """);
        for (String e : exprs) {
            sb.append("    show3(").append(e).append(")\n");
        }
        return sb.append("}\n").toString();
    }

    // ---- a matriz de Kleene (oraculo medido nos 4 targets) -----------------

    @Test
    void andTableIsKleene(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, show3(List.of(
                "tb() && tb()", "tb() && fb()", "tb() && nb()",
                "fb() && tb()", "fb() && fb()", "fb() && nb()",
                "nb() && tb()", "nb() && fb()", "nb() && nb()")),
                "T\nF\nU\nF\nF\nF\nU\nF\nU");
    }

    @Test
    void orTableIsKleene(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, show3(List.of(
                "tb() || tb()", "tb() || fb()", "tb() || nb()",
                "fb() || tb()", "fb() || fb()", "fb() || nb()",
                "nb() || tb()", "nb() || fb()", "nb() || nb()")),
                "T\nT\nT\nT\nF\nU\nT\nU\nU");
    }

    @Test
    void notTableIsKleene(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, show3(List.of(
                "!tb()", "!fb()", "!nb()")),
                "F\nT\nU");
    }

    @Test
    void logicalResultPrintsNullWhenUnknown(@TempDir Path tempDir) throws IOException {
        // A face impressa do U (medida igual em JVM/Script/JS): println de
        // resultado tres-estado = "null", nao "false" (a materializacao do
        // #486 morreu com o D-TROOL).
        runAll3(tempDir, TROOLS + """
                main() {
                    println(true && fb())
                    println(false || tb())
                    println(true && nb())
                    println(false || nb())
                }
                """, "false\ntrue\nnull\nnull");
    }

    // ----消费者 legitim: narrowing, nunca cegueira ---------------------------

    @Test
    void equalsNullDiscriminatesUnknown(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, TROOLS + """
                main() {
                    println(fb() == null)
                    println(nb() == null)
                    println(tb() == null)
                }
                """, "false\ntrue\nfalse");
    }

    @Test
    void unknownIsFalsyInConditionals(@TempDir Path tempDir) throws IOException {
        // if/while/if-expr leem o slot com identidade Boolean.TRUE:
        // null e FALSE caem no ramo falso (mesma leitura do §306).
        runAll3(tempDir, TROOLS + """
                main() {
                    if (nb()) { println("T") } else { println("F") }
                    val v = if (nb()) "T" else "F"
                    println(v)
                }
                """, "F\nF");
    }

    // ---- o curto-circuito onde a tabela permite -----------------------------

    @Test
    void decisiveSideShortCircuitsEvenWithUnknown(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, TROOLS + """
                Bool rhs() { println("RHS"); return true }
                main() {
                    var a = fb() && rhs()
                    var b = tb() || rhs()
                    println("done")
                }
                """, "done");
    }

    @Test
    void unknownLeftSideMustEvaluateRightSide(@TempDir Path tempDir) throws IOException {
        // Medido (P.kf): null && rhs()=true avalia o RHS mas fica U (Kleene);
        // null || rhs()=true decide T. O RHS roda nos dois.
        runAll3(tempDir, TROOLS + """
                Bool rhs() { println("RHS"); return true }
                main() {
                    var a = nb() && rhs()
                    var b = nb() || rhs()
                    show(a)
                    show(b)
                }
                show(x: Troolean) {
                    if (x == null) { println("U") } else if (x) { println("T") } else { println("F") }
                }
                """, "RHS\nRHS\nU\nT");
    }

    @Test
    void nestedChainsFollowKleene(@TempDir Path tempDir) throws IOException {
        // Medido (NC.kf): cadeias e parenteses dobram pela tabela —
        // (T&&U)||F = U||F = U; (T&&U)||T = T; !(U&&T) = !U = U.
        runAll3(tempDir, show3(List.of(
                "tb() && nb() || fb()",
                "(tb() && nb()) || tb()",
                "!(nb() && tb())")),
                "U\nT\nU");
    }

    @Test
    void conditionSugarEqualsCompareTrue(@TempDir Path tempDir) throws IOException {
        // `if (t)` e o acucar de `if (t == true)`: U cai no ramo falso; as
        // comparacoes == true / == false separam os tres estados.
        runAll3(tempDir, TROOLS + """
                main() {
                    if (tb()) { println("ifT") }
                    if (nb()) { println("ifU") } else { println("elseU") }
                    println(tb() == true)
                    println(nb() == true)
                    println(nb() == false)
                }
                """, "ifT\nelseU\ntrue\nfalse\nfalse");
    }

    // ---- SEM095: Bool? nao existe mais (as duas grafias) ---------------------

    @Test
    void boolOptionalSyntaxRejectedWithSem095(@TempDir Path tempDir) throws IOException {
        for (String spelling : List.of("Bool?", "Boolean?")) {
            CompilationResult r = compileOnly(tempDir,
                    "Sem095-" + spelling.charAt(0) + ".kf",
                    "main() { var x: " + spelling + " = true }\n");
            assertFalse(r.success(), spelling + " deve ser rejeitado");
            String codes = r.diagnostics().getDiagnostics().toString();
            assertTrue(codes.contains("SEM095"), spelling + " -> " + codes);
            assertTrue(codes.toLowerCase().contains("troolean"), spelling + " -> " + codes);
        }
    }

    @Test
    void boolOptionalReturnAndParamRejectedWithSem095(@TempDir Path tempDir) throws IOException {
        CompilationResult fn = compileOnly(tempDir, "Sem095Fn.kf",
                "Bool? f() { return true }\nmain() { println(f()) }\n");
        assertFalse(fn.success());
        assertTrue(fn.diagnostics().getDiagnostics().toString().contains("SEM095"),
                fn.diagnostics().getDiagnostics().toString());

        CompilationResult par = compileOnly(tempDir, "Sem095Par.kf",
                "void handle(Bool? flag) { }\nmain() { handle(true) }\n");
        assertFalse(par.success());
        assertTrue(par.diagnostics().getDiagnostics().toString().contains("SEM095"),
                par.diagnostics().getDiagnostics().toString());
    }

    @Test
    void plainBoolKeepsTwoValueContract(@TempDir Path tempDir) throws IOException {
        // A face #487 sobrevive INTEIRA para operandos Bool: resultado Bool,
        // atribuivel, retornavel, sem caixa.
        runAll3(tempDir, """
                Bool f() { return false }
                Bool g() { return f() && true }
                main() {
                    Bool x = f() && true
                    println(x)
                    println(g())
                    println(!f())
                }
                """, "false\nfalse\ntrue");
    }
}
