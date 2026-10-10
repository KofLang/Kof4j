package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

/**
 * §306 — faces LEITORAS de {@code Nullable(Bool)} deixadas pelo Commit B
 * (#278/#438, fechado no cluster escritor §295(b)).
 *
 * <p><b>Face (a) — JVM truthiness.</b> O slot {@code Troolean} é fisicamente
 * boxed ({@code java/lang/Boolean}) desde o Commit B, mas os sítios de
 * truthiness ({@code if}/{@code while}/{@code if-expr}/{@code ?:}) emitiam
 * {@code LOAD; INT 0; IF_ICMPNE} — {@code if_icmpne} sobre referência =
 * {@code VerifyError: Bad type on operand stack} no LOAD da classe (compila
 * limpo, morre ao carregar — o launcher JVM mascara de "JavaFX", §149).
 * O fix espelha o padrão de leitura do §295(a)/retorno: comparação de
 * identidade com {@code Boolean.TRUE} (null/FALSE → falso — mesma semântica
 * falsy de {@code null} no JS e do {@code 0}/{@code null} no interpretador),
 * auto-portada ao JVM ({@code needsErasureBoxing}); Script/JS/Native não
 * veem a mudança.
 *
 * <p><b>Face (b) — representação no Script.</b> {@code println} de um local
 * {@code Troolean} imprimia {@code 1}/{@code 0} (JVM/JS imprimem {@code true}/
 * {@code false}) — divergência cross-target (freeze rule 5).
 */
class NullableBoolTruthinessE2ETest extends NullablePrimitiveContractSupport {

    // ---- face (a): truthiness de Troolean em if/while/if-expr/ternário -------

    @Test
    void ifStmtOverNullableBoolTrue(@TempDir Path tempDir) throws IOException {
        // Verbatim do repro catalogado em §306(a).
        runAll3(tempDir, """
                main() {
                    Troolean b = true
                    if (b) { println("Y") } else { println("N") }
                }
                """, "Y");
    }

    @Test
    void ifStmtOverNullableBoolFalse(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    Troolean b = false
                    if (b) { println("Y") } else { println("N") }
                }
                """, "N");
    }

    @Test
    void mapGetBoolPresentAndMissing(@TempDir Path tempDir) throws IOException {
        // Face reachable SEM o fix do escritor (bug 87): o valor chega como
        // referência; chave ausente = null → falsy (mesma semântica JS).
        runAll3(tempDir, """
                main() {
                    var m = mapOf("flag", true)
                    var hit: Troolean = m.get("flag")
                    var miss: Troolean = m.get("nope")
                    if (hit) { println("HIT") } else { println("NOHIT") }
                    if (miss) { println("MISS") } else { println("NO") }
                }
                """, "HIT\nNO");
    }

    @Test
    void ifExprOverNullableBool(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    Troolean b = true
                    var s = if (b) "A" else "B"
                    println(s)
                }
                """, "A");
    }

    @Test
    void whileLoopOverNullableBool(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    Troolean go = true
                    var n = 0
                    while (go) {
                        n = n + 1
                        if (n >= 3) { go = false }
                    }
                    println(n)
                }
                """, "3");
    }

    // ---- face (b): mesma representação impressa nos 3 targets -------------

    @Test
    void printNullableBoolLocalMatchesTargets(@TempDir Path tempDir) throws IOException {
        // §306(a)+(b): mesma representação impressa nos 3 alvos. A face (b)
        // fechou junto com o fix do box: o kof_box do interpretador coerz
        // Number→Boolean (alinhado com o slot boxed da JVM e o coerceFor do
        // get) — antes o println do slot Nullable(Bool) imprimia o Integer
        // cru do ofBool ("1/0") enquanto JVM/JS imprimiam "true/false".
        runAll3(tempDir, """
                main() {
                    Troolean b = true
                    println(b)
                    b = false
                    println(b)
                }
                """, "true\nfalse");
    }

    // ---- controles: Bool plano e narrowing não mudam de comportamento -----

    @Test
    void plainBoolTruthinessUnchanged(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    Bool b = true
                    if (b) { println("P") } else { println("Q") }
                    println(b)
                }
                """, "P\ntrue");
    }

    @Test
    void nullableBoolNarrowingStillWorks(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m = mapOf("flag", true)
                    var b: Troolean = m.get("nope")
                    if (b != null) { println("X") } else { println("Z") }
                }
                """, "Z");
    }

    // ---------------------------------------------------------------------
    // #462 — a fronteira CONDICAO -> VALOR. O §306 fechou a truthiness nos
    // sitios de CONDICAO (if/while/if-expr); aqui `&&`/`||` aparecem em
    // POSICAO DE VALOR (`println(x && y)`, `Bool b = ...`, `return ...`).
    //
    // Duas causas: (A) o `ExpressionTyper` do lowering nao tinha regra para
    // `&&`/`||` e herdava o tipo do operando ESQUERDO (`Troolean`), enquanto o
    // `TypeChecker` semantico ja diz `Bool` — o consumidor entao acreditava
    // num `Troolean` boxed e recebia um int primitivo; (B) so o LHS passava pelo
    // `nullableBoolTruthinessRewrite`, entao um RHS `Troolean` chegava ao join
    // como referencia enquanto o outro arco deixava int.
    // ---------------------------------------------------------------------

    /** T1 — value position, LHS `Troolean` (D-TROOL: Kleene — mesmo golden do #487). */
    @Test
    void logicalValuePositionWithNullableLhs(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Troolean fb() { return false }
                Troolean nb() { return null }
                Troolean tb() { return true }
                main() {
                    println(fb() && true)
                    println(tb() && true)
                    println(nb() || true)
                    println(fb() || false)
                }
                """, "false\ntrue\ntrue\nfalse");
    }

    /**
     * T2 — RHS tambem nullable (prova a causa B: RHS canonicalizado).
     * JVM + Script; a face JS vive no teste abaixo, por ser um gap ANTERIOR
     * e de outro subsistema (o JS nao passa por este bloco de IR).
     */
    @Test
    void logicalValuePositionWithNullableRhs(@TempDir Path tempDir) throws IOException {
        String src = """
                Troolean fb() { return false }
                Troolean nb() { return null }
                Troolean tb() { return true }
                main() {
                    println(true && fb())
                    println(false || tb())
                    println(true && nb())
                    println(false || nb())
                }
                """;
        String golden = "false\ntrue\nnull\nnull";
        runJvm(tempDir, src, golden);
        runScript(tempDir, src, golden);
    }

    /**
     * T2 face JS — o backend JS NAO passa pelo bloco de IR do short-circuit
     * (`ExpressionBinaryLowerer`: `&& driver.target != Target.JS`), entao o
     * `&&`/`||` sai CRU. A semantica do JavaScript devolve o OPERANDO
     * (`true && null` → `null`), nao um `Bool` — e o tipo KOF da expressao e
     * `Bool`. #486: o backend preserva o short-circuit nativo, mas materializa
     * o resultado logico como `Bool` para nao vazar o operando nullable.
     */
    @Test
    void logicalValuePositionWithNullableRhsJsMatchesKofContract(
            @TempDir Path tempDir) throws IOException {
        runJs(tempDir, """
                Troolean fb() { return false }
                Troolean nb() { return null }
                Troolean tb() { return true }
                main() {
                    println(true && fb())
                    println(false || tb())
                    println(true && nb())
                    println(false || nb())
                }
                """, "false\ntrue\nnull\nnull");
    }

    /** T3 — consumidor diferente de print (impede fix oportunista no println). */
    @Test
    void logicalResultAssignedToBoolLocal(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Bool fb() { return false }
                main() {
                    Bool x = fb() && true
                    println(x)
                }
                """, "false");
    }

    /** T4 — retorno tipado `Bool`. */
    @Test
    void logicalResultReturnedAsBool(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Bool fb() { return false }
                Bool g() { return fb() && true }
                main() { println(g()) }
                """, "false");
    }

    /** T5 — short-circuit preservado: o RHS nao pode ser avaliado. */
    @Test
    void logicalShortCircuitStillLazy(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Troolean rhs() {
                    println("RHS")
                    return true
                }
                main() {
                    println(false && rhs())
                    println(true || rhs())
                }
                """, "false\ntrue");
    }

    /** T6 — controle: `Bool` puro nao muda. */
    @Test
    void plainBoolLogicalValuePositionUnchanged(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    println(false && true)
                    println(true || false)
                }
                """, "false\ntrue");
    }
}
