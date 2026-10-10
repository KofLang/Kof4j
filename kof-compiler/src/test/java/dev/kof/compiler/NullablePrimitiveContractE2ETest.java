package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * #278 (D-NULL-INTENT, atomic) — corpus de regressão do contrato
 * {@code Nullable(primitivo) = Absent | Present(T)} (I1-I8, ver
 * KOF_278_D_NULL_INTENT_SOLUCAO_2026-09-18.md). Casos anti-falso-verde
 * exigidos pelo documento (§8.3): cache de {@code Integer} não pode virar
 * identidade de wrapper (I6), zero/false não são ausência (I2/I5), e
 * Map.get/put/remove não podem inventar valor (I7).
 *
 * <p><b>Q0 MEDIDO (18/09, tip {@code c39a4406} de beta-0.4.0, mvn -pl
 * kof-compiler -am test -Dtest=NullablePrimitiveContractE2ETest) — 4/8
 * vermelho no JVM:</b>
 * <ul>
 *   <li>{@code returnNullPreservesRealNull}: esperado {@code true\nnull},
 *       medido {@code false\n0} — confirma o fold {@code null→0} ainda vivo
 *       no retorno (I2/I5), já catalogado no DOING.md (sweep 18/09,
 *       "no tip o fold unificou p/ 0 nos 4 alvos").</li>
 *   <li>{@code mapGetMissingKeyIsNullDistinctFromPresentZero}: esperado
 *       {@code true\nfalse\n0}, medido {@code false\nfalse\n0} — a chave
 *       ausente não distingue de {@code Present(0)} (I7).</li>
 *   <li>{@code mapPutOnNewKeyReturnsNullNotZero}/
 *       {@code mapRemoveOnAbsentKeyReturnsNullNotZero}: mesmo padrão —
 *       {@code put}/{@code remove} sem entrada anterior não retornam
 *       {@code null} (I7; raiz confirmada em código:
 *       {@code MemberCallTyper.java:232-233} tipa os dois como {@code V},
 *       não {@code V?}, ao contrário de {@code get} na linha 230, que já
 *       tem o SG-008/bug-87 correto).</li>
 * </ul>
 * <p>Os outros 4 ({@code returnZeroStaysDistinctFromNull},
 * {@code returnFalseStaysDistinctFromNull}, {@code integerCacheDoesNotFakeEquality},
 * {@code twoGenuineNullsAreEqualToEachOther}) já passam hoje, mas por
 * acidente: sem boxing real ainda implementado, {@code Int?} de retorno é só
 * um {@code int} primitivo na pilha — não há wrapper para colidir no cache
 * do {@code Integer} nem para descolar identidade de valor. Ficam como rede
 * de regressão para quando o boxing de verdade entrar (Commit B): se algum
 * deles ficar RED depois disso, é sinal de identidade de wrapper vazando
 * pro `==`, não do contrato sendo cumprido.
 *
 * <p>Alvos cobertos aqui: JVM/Script/JS (fase 1 da fila do D-NULL-INTENT em
 * DECISIONS.md — Native é fase 2, separada). {@code runAll3} para no
 * primeiro alvo que falhar (fail-fast), então o Q0 acima é só JVM; Script/JS
 * dos 4 casos vermelhos ficam pendentes de medição até o fix do JVM não
 * quebrar mais cedo na cadeia.
 */
class NullablePrimitiveContractE2ETest extends NullablePrimitiveContractSupport {


    // ---- I2/I5: zero/false/0.0 não são ausência --------------------------

    @Test
    void returnNullPreservesRealNull(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? f() { return null }
                main() {
                    println(f() == null)
                    println(f())
                }
                """, "true\nnull");
    }

    @Test
    void returnZeroStaysDistinctFromNull(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? zero() { return 0 }
                main() {
                    println(zero() == null)
                    println(zero())
                }
                """, "false\n0");
    }

    @Test
    void returnFalseStaysDistinctFromNull(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Troolean no() { return false }
                main() {
                    println(no() == null)
                    println(no())
                }
                """, "false\nfalse");
    }

    // ---- I6: igualdade lifted, NUNCA identidade de wrapper ----------------
    // Caso obrigatório do §5/I6 e §8.3 do documento: 10000 escapa do cache de
    // Integer (-128..127) do JDK — se o `==` comparar REFERÊNCIA do wrapper
    // em vez de desempacotar e comparar valor, dois retornos de 10000 (dois
    // objetos Integer DIFERENTES) dão `false`, quando o contrato exige `true`.

    @Test
    void integerCacheDoesNotFakeEquality(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? a() { return 10000 }
                Int? b() { return 10000 }
                main() {
                    println(a() == b())
                }
                """, "true");
    }

    @Test
    void twoGenuineNullsAreEqualToEachOther(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? ni() { return null }
                main() {
                    var a = ni()
                    var b = ni()
                    println(a == b)
                }
                """, "true");
    }

    // ---- I7: Map.get/put/remove não inventam valor -------------------------
    // MemberCallTyper.java:232-233 tipa put()/remove() como V (não V?) hoje —
    // contradiz Java Map (retorna o valor anterior/removido OU null) e o I7.

    @Test
    void mapGetMissingKeyIsNullDistinctFromPresentZero(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m = new Map<String, Int>()
                    m.put("zero", 0)
                    println(m.get("missing") == null)
                    println(m.get("zero") == null)
                    println(m.get("zero"))
                }
                """, "true\nfalse\n0");
    }

    @Test
    void mapPutOnNewKeyReturnsNullNotZero(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m = new Map<String, Int>()
                    println(m.put("k", 1) == null)
                    println(m.put("k", 2))
                }
                """, "true\n1");
    }

    @Test
    void mapRemoveOnAbsentKeyReturnsNullNotZero(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m = new Map<String, Int>()
                    m.put("k", 7)
                    println(m.remove("missing") == null)
                    println(m.remove("k"))
                }
                """, "true\n7");
    }

    // ---- §294-2a: join heterogêneo Nullable(primitivo) × primitivo NÃO pode
    // re-boxar o ramo já-boxed (boxPrimitiveBranch usava isPrimitiveType, que
    // OLHA DENTRO do Nullable → emitia `valueOf:(Ljava/lang/Integer;)` sobre
    // slot já Integer = NoSuchMethodError; script/JS passavam = divergência
    // cross-target). Guard cru (Type.PrimitiveType) fecha as 3 faces.

    @Test
    void ternaryOnNarrowedNullableFromMapGet(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m: Map<String, Int> = mapOf("a", 1)
                    var a = m.get("a")
                    println(if (a != null) a else -1)
                }
                """, "1");
    }

    @Test
    void ternaryOnAbsentNullableFromMapGet(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m: Map<String, Int> = mapOf("a", 1)
                    var z = m.get("z")
                    println(if (z != null) z else 9)
                }
                """, "9");
    }

    @Test
    void ternaryLongPresentAndAbsent(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m: Map<String, Long> = mapOf("a", 2L)
                    var a = m.get("a")
                    var z = m.get("z")
                    println(if (a != null) a else -1L)
                    println(if (z != null) z else -1L)
                }
                """, "2\n-1");
    }

    @Test
    void ternaryBoolNarrowed(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m: Map<String, Bool> = mapOf("a", true)
                    var a = m.get("a")
                    println(if (a != null) a else false)
                }
                """, "true");
    }

    @Test
    void switchExprBranchOnNullableNarrowedNotReboxed(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m: Map<String, Int> = mapOf("a", 5)
                    var a = m.get("a")
                    var flag = true
                    println(switch (flag) { case true -> a default -> -1 })
                }
                """, "5");
    }

    @Test
    void ternaryWithNullBranchStillJoinsAsReference(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m: Map<String, Int> = mapOf("a", 5)
                    var a = m.get("a")
                    var z = m.get("z")
                    if (a != null) { println(a) }
                    println(if (z != null) z else null)
                }
                """, "5\nnull");
    }

    // ---- I2/I5 estendido: ramo null de if/switch/var explícito não é default ----
    // §125-ext (opção A) foldava CADA ramo null de if/switch p/ o default do
    // primitivo antes do fix — sobrevivia mesmo depois do Commit A/B tratarem
    // o `return null` DIRETO, porque o fold interceptava ANTES do join
    // heterogêneo (#57/§70) rodar. `foldNullablePrimBranches` agora só
    // dispara no Native (fase 2); JVM/Script/JS reusam o boxing in-branch já
    // testado (ExpressionLowerer#IfExpr, boxesOwnBranches).

    // I6 confirmado também na posição de CONDIÇÃO direta de `if` (não só
    // como valor em `println`) — o shortcut `emitComparisonShortcut`/
    // `KofConditionalJump` já é excluído p/ Nullable(primitivo) genuíno
    // desde o Commit C (`CompilerComparisons.isComparisonShortcut`), que
    // força o caminho de VALOR (`.equals()` do I6) mesmo em posição de
    // condição — não era um gap residual, só não tinha teste dedicado.
    @Test
    void ifStatementConditionUsesEqualsNotIdentityShortcut(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m = new Map<String, Int>()
                    m.put("zero", 0)
                    if (m.get("missing") == null) {
                        println("miss-is-null")
                    } else {
                        println("miss-not-null")
                    }
                    if (m.get("zero") == null) {
                        println("zero-is-null")
                    } else {
                        println("zero-not-null")
                    }
                }
                """, "miss-is-null\nzero-not-null");
    }

    @Test
    void ifExprNullBranchPreservesRealNull(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? en(Int x) = if (x > 0) x else null
                main() {
                    println(en(7))
                    println(en(-7))
                }
                """, "7\nnull");
    }

    @Test
    void switchExprNullBranchPreservesRealNull(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? sw(Int x) = switch (x) { case 1 -> 10 default -> null }
                main() {
                    println(sw(1))
                    println(sw(2))
                }
                """, "10\nnull");
    }

    @Test
    void explicitTypedLocalNullBranchPreservesRealNull(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    Int? v = if (false) 9 else null
                    println(v)
                    Troolean bn = if (false) true else null
                    println(bn)
                }
                """, "null\nnull");
    }

    // ---- I2/I5 estendido: concatenação de String com Nullable(primitivo) null ----
    // `emitOperandToString` usava `TypeMetrics.isPrimitiveType` (desembrulha
    // Nullable) p/ decidir se precisa boxPrimitive — um `Int?` GENUÍNO
    // (Commit B: já chega boxed/aconst_null) caía no mesmo ramo que um
    // primitivo CRU e levava um segundo `Integer.valueOf(int)` sobre uma
    // REFERÊNCIA (VerifyError JVM; NPE silenciosa no interpretador).

    @Test
    void stringConcatWithNullNullablePrimitive(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? ni() { return null }
                main() {
                    println("a" + ni())
                    println(ni() + "b")
                }
                """, "anull\nnullb");
    }

    // ---- §295(b): o LADO ESCRITOR do slot Nullable(primitivo) boxado -------
    // O Commit B (#278) virou o local `Int?` em slot de referência
    // (storeVarOpcode/loadVarOpcode → ASTORE/ALOAD) e o #438 consertou o
    // READ (checkcast+intValue no comparando, face (a)), mas os ESCRITORES
    // boxavam primitivo cru só via `erasesToReference` — false p/
    // NullableType. Resultado: `iconst_5; astore_1` → VerifyError no LOAD da
    // classe (rosto "JavaFX ausente", regra AGENTS: nunca benigno). Faces:
    // literal-init (verbatim do ledger), default sem-init, `=`, `+=`, `++`.
    // O gate novo espelha o do return (D-NULL-INTENT já no ReturnValue-
    // Lowerer): boxa só primitivo CRÚ — init já Nullable (`m.get`) chega
    // fisicamente boxed; re-box = NoSuchMethodError (lesson §294-2a).

    @Test
    void nullablePrimLocalLiteralInitLedgerVerbatim(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    Int? v = 5
                    Bool big = v > 100
                    Bool small = v > 0
                    println(if (big) "T" else "F")
                    println(if (small) "T" else "F")
                }
                """, "F\nT");
    }

    @Test
    void nullablePrimLocalNoInitDefaultsToNull(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    Int? w
                    println(if (w == null) "N" else "V")
                    w = 3
                    println(if (w == null) "N" else w)
                }
                """, "N\n3");
    }

    @Test
    void nullablePrimSimpleAssignBoxesLiteral(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m: Map<String, Int> = mapOf("z", 7)
                    Int? g = m.get("z")
                    g = 9
                    println(g)
                    Int? h = g
                    println(h)
                }
                """, "9\n9");
    }

    @Test
    void nullablePrimCompoundAssignUnboxRebox(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m: Map<String, Int> = mapOf("z", 7)
                    Int? g = m.get("z")
                    g += 1
                    println(g)
                    g -= 2
                    println(g)
                }
                """, "8\n6");
    }

    @Test
    void nullablePrimIncrementPostfixAndPrefix(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    Int? v = 5
                    v++
                    println(v)
                    ++v
                    println(v)
                }
                """, "6\n7");
    }

    @Test
    void nullablePrimLongLiteralCompoundAndPass(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? id(Int? x) { return x }
                main() {
                    Long? x = 5000000000
                    x += 1
                    println(x)
                    println(if (x > 100) "T" else "F")
                }
                """, "5000000001\nT");
    }

    // §295(b): slot Nullable(Bool) — o ESCRITOR (init literal + assign) boxa
    // e o println lê a referência (JVM). Faces LEITORAS pré-existentes ficam
    // no §306: JVM truthiness `if (b)` (if_icmpne sobre Boolean) e o Script
    // que imprime `Troolean` local como 1/0 (bool canônico do interpretador é
    // Int; medido no jar pré-fix — não é regressão deste pino). §306 FECHADO
    // 19/09 (`.22`): truthiness `if (b)` → açucar `b == true` (null-seguro,
    // JVM/JS/Script) + `kof_box`/`kof_unbox` canonizam Boolean no
    // interpretador; faces pinadas por `NullableBoolTruthinessE2ETest` 8/8 runAll3.
    @Test
    void nullablePrimBoolWriterFacesStoreBoxed(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    Troolean b = true
                    println(b)
                    b = false
                    println(b)
                }
                """, "true\nfalse");
    }

}
