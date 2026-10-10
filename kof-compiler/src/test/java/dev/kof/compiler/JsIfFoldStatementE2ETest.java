package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

/**
 * §267 (KofJS, R6): o dispatcher de statements tentava dobrar TODO if (inclusive
 * o de STATEMENT) em if-expressao (tryParseIfExpr). O IR de um if de statement
 * e de uma if-expressao e IDENTICO pos-hoc ([cond], CJump, Label, ramo, Jump,
 * Label, ramo, Label) — a diferenca so existia na INTENCAO do lowering. Quando
 * a dobra acertava um if de statement cujos ramos sao atribuicoes, a ternaria
 * passava a engolir o statement SEGUINTE e o `let` dele era emitido ANTES da
 * ternaria rodar → leitura obsoleta, VALOR ERRADO SILENCIOSO (JVM/Script/Native
 * corretos; regra 5/6). Fix = o mesmo padrao do §266: info estrutural no IR —
 * `KofStatementIf()` emitido por StatementLowerer.IfStmt, lido-e-limpo pelo
 * dispatcher de statements (ctx.statementIf), que pula a dobra e cai em
 * parseIfBody; as if-expressoes REAIS (IfExpr, sem marcador) continuam dobrando.
 * Estes testes cobrem o Q1 do registro: o exato repro, ternarias de expressao
 * NAO-regredindo, condicao composta (&&), if dentro de loop com leitura na
 * cauda, if/else aninhado, if sem else, e o valor-dobra-no-init-de-var.
 * runBoth JVM+JS com valores exatos.
 */
class JsIfFoldStatementE2ETest extends JsParityRunSupport {

    @Test
    void statementIfElseReadNextParity(@TempDir Path tempDir) throws IOException {
        // o repro exato do registro: statement-if/else ambos-assign + leitura
        assertBoth("""
                main() {
                    var t = 2
                    var gv = 0
                    if (t % 2 == 0) { gv = t * 10 } else { gv = t * 10 + 1 }
                    var gt = gv + 1
                    println(gt)
                }
                """, "21", tempDir, "foldReadNext");
    }

    @Test
    void realIfExpressionStillFoldsParity(@TempDir Path tempDir) throws IOException {
        // NAO-regressao do Q1: if-expressao REAL (valor consumido) continua
        // dobrando em ternaria e funcionando no statement dispatcher.
        assertBoth("""
                main() {
                    var z = 0
                    z = if (z == 0) { 7 } else { 9 }
                    var p = if (z == 7) { 100 } else { 200 }
                    println(z + "," + (p + 1))
                }
                """, "7,101", tempDir, "foldRealExpr");
    }

    @Test
    void compoundConditionStatementIfParity(@TempDir Path tempDir) throws IOException {
        // condicao composta (&&) — o dispatcher ve o CJump final e nao pode
        // deixar o marcador vazar p/ uma dobra alheia.
        assertBoth("""
                main() {
                    var flag = true
                    var n = 3
                    var w = 0
                    if (flag && n > 2) { w = 5 } else { w = 6 }
                    println(w + 100)
                }
                """, "105", tempDir, "foldCompound");
    }

    @Test
    void statementIfInsideLoopWithTailReadParity(@TempDir Path tempDir) throws IOException {
        // o caso-G do harness do §266: statement-if/else dentro de while +
        // leitura na cauda. §266 consertou a FRONTIERA DO LOOP; este cobre a
        // dobra ternaria dentro do corpo (a face que sobrou e virou §267).
        assertBoth("""
                main() {
                    var g = new List<Int>()
                    var q = 0
                    while (q < 4) {
                        var gv = 0
                        if (q % 2 == 0) { gv = q * 10 } else { gv = q * 10 + 1 }
                        var tail = gv + 1
                        g.add(tail)
                        q = q + 1
                    }
                    println(g.get(0) + "," + g.get(1) + "," + g.get(2) + "," + g.get(3))
                }
                """, "1,12,21,32", tempDir, "foldInLoop");
    }

    @Test
    void nestedStatementIfParity(@TempDir Path tempDir) throws IOException {
        assertBoth("""
                main() {
                    var a = 1
                    var b = 0
                    if (a == 1) { if (b == 0) { b = 5 } else { b = 6 } } else { b = 9 }
                    println(b + 1)
                }
                """, "6", tempDir, "foldNested");
    }

    @Test
    void ifWithoutElseReadNextParity(@TempDir Path tempDir) throws IOException {
        assertBoth("""
                main() {
                    var s = 0
                    if (s == 0) { s = 4 }
                    println(s + 1)
                }
                """, "5", tempDir, "foldNoElse");
    }

    @Test
    void nestedIfExpressionInStatementIfConditionParity(@TempDir Path tempDir) throws IOException {
        // O marcador KofStatementIf deve AMARRAR ao CJump do RAMO do if de
        // statement — nao ao CJump de uma if-expressao embutida na CONDICA.
        // Um booleano one-shot no dispatcher falharia aqui (o primeiro CJump
        // visto nao eh o do ramo); o trueLabel do ramo sim.
        assertBoth("""
                main() {
                    var c = 1
                    var w = 0
                    if (if (c == 1) { true } else { false }) { w = 7 } else { w = 8 }
                    var z = 0
                    if ((if (c == 1) { 3 } else { 4 }) + 1 == 4) { z = 9 } else { z = 11 }
                    var p = 0
                    if (c == 1) { p = if (w == 7) { 100 } else { 200 } } else { p = 5 }
                    println(w + 10)
                    println(z + 10)
                    println(p)
                }
                """, "17\n19\n100", tempDir, "nestedIfInCond");
    }

    @Test
    void ifElseBothAssignsDifferentVarsReadNextParity(@TempDir Path tempDir) throws IOException {
        // ramo de atribuicao + leitura de AMBAS as vars no statement seguinte
        // (a forma que o dobrador ternario engolia)
        assertBoth("""
                main() {
                    var c = 1
                    var x = 0
                    var y = 0
                    if (c == 1) { x = 10; y = 20 } else { x = 1; y = 2 }
                    println(x + 1)
                    println(y + 1)
                }
                """, "11\n21", tempDir, "multiAssign");
    }
}
