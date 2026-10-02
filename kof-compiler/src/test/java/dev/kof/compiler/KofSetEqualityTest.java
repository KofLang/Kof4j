package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;

/**
 * Corretude de {@code equals}/{@code hashCode} no {@code Set<T>} (o "HashSet"
 * do Kof). Contrato de {@code java.util.HashSet}: dois elementos são o mesmo
 * quando {@code a.equals(b)} — e, para o contrato ser respeitado, quando
 * {@code a.hashCode() == b.hashCode()}.
 *
 * <p>Os testes "comuns" rodam o MESMO programa em JVM (HashSet real) e JS
 * (varredura linear com {@code kofValEq}) e exigem saída idêntica. Os testes
 * {@code ...Jvm}/{@code ...Js} isolados cobrem o que só faz sentido (ou só
 * diverge) em um backend — cada um explica o porquê no comentário.
 *
 * <p>Native não é coberto aqui: o backend gera x86_64 e o Set nativo compara
 * por tag/valor (sem despacho para {@code equals} de usuário).
 *
 * <p>As fontes de apoio e os runners JVM/JS vivem em
 * {@link KofSetEqualitySupport} (Fase 3, {@code D-TEST-ARCHITECTURE-GO}); os 21
 * casos seguem aqui.
 */
class KofSetEqualityTest extends KofSetEqualitySupport {

    // ------------------------------------------------------------------
    // Contrato comum: JVM e JS têm de concordar
    // ------------------------------------------------------------------

    @Test
    void userEqualsAndHashCodeDeduplicate(@TempDir Path tmp) throws Exception {
        both(tmp, PT + """
            main() {
                var s = setOf()
                println(s.add(Pt(1, 2)))
                println(s.add(Pt(1, 2)))
                println(s.size())
                println(s.contains(Pt(1, 2)))
                println(s.contains(Pt(2, 1)))
                println(s.add(Pt(2, 1)))
                println(s.size())
                println(s.remove(Pt(1, 2)))
                println(s.remove(Pt(1, 2)))
                println(s.size())
            }
            """, "true\nfalse\n1\ntrue\nfalse\ntrue\n2\ntrue\nfalse\n1");
    }

    @Test
    void setOfInitialDuplicatesAreCollapsed(@TempDir Path tmp) throws Exception {
        both(tmp, PT + """
            main() {
                var s = setOf(Pt(1, 2), Pt(1, 2), Pt(3, 4), Pt(3, 4), Pt(1, 2))
                println(s.size())
                println(s.contains(Pt(3, 4)))
            }
            """, "2\ntrue");
    }

    @Test
    void equalsAndHashCodeContractCalledDirectly(@TempDir Path tmp) throws Exception {
        // reflexiva, simétrica, falsa para null/outro tipo e hashCode igual
        // para iguais. `==` em classe é IDENTIDADE (type-system.md §10).
        both(tmp, PT + PLAIN + """
            main() {
                var a = Pt(1, 2)
                var b = Pt(1, 2)
                var c = Pt(9, 9)
                println(a.equals(a))
                println(a.equals(b))
                println(b.equals(a))
                println(a.equals(c))
                println(a.equals(null))
                println(a.equals(Plain(1)))
                println(a.hashCode() == b.hashCode())
                println(a == b)
                println(a == a)
            }
            """, "true\ntrue\ntrue\nfalse\nfalse\nfalse\ntrue\nfalse\ntrue");
    }

    @Test
    void classWithoutEqualsUsesIdentity(@TempDir Path tmp) throws Exception {
        both(tmp, PLAIN + """
            main() {
                var s = setOf()
                var p1 = Plain(1)
                println(s.add(p1))
                println(s.add(Plain(1)))
                println(s.add(p1))
                println(s.size())
                println(s.contains(p1))
                println(s.contains(Plain(1)))
                println(s.remove(Plain(1)))
                println(s.remove(p1))
                println(s.size())
            }
            """, "true\ntrue\nfalse\n2\ntrue\nfalse\nfalse\ntrue\n1");
    }

    @Test
    void recordsAreComparedByContent(@TempDir Path tmp) throws Exception {
        both(tmp, """
            record R(Int a, String b)
            main() {
                var s = setOf()
                println(s.add(R(1, "a")))
                println(s.add(R(1, "a")))
                println(s.add(R(1, "b")))
                println(s.add(R(2, "a")))
                println(s.size())
                println(s.contains(R(1, "b")))
                println(s.contains(R(3, "a")))
                println(s.remove(R(1, "a")))
                println(s.size())
                println(R(1, "a").hashCode() == R(1, "a").hashCode())
            }
            """, "true\nfalse\ntrue\ntrue\n3\ntrue\nfalse\ntrue\n2\ntrue");
    }

    @Test
    void nestedRecordsAreComparedDeeplyOnJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            record P(Int x, Int y)
            record Line(P from, P to)
            main() {
                var s = setOf()
                println(s.add(Line(P(0, 0), P(1, 1))))
                println(s.add(Line(P(0, 0), P(1, 1))))
                println(s.add(Line(P(0, 0), P(1, 2))))
                println(s.size())
                println(s.contains(Line(P(0, 0), P(1, 2))))
                println(Line(P(0, 0), P(1, 1)).hashCode() == Line(P(0, 0), P(1, 1)).hashCode())
            }
            """, "true\nfalse\ntrue\n2\ntrue\ntrue");
    }

    @Test
    void nestedRecordsAreComparedDeeplyOnJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            record P(Int x, Int y)
            record Line(P from, P to)
            main() {
                var s = setOf()
                println(s.add(Line(P(0, 0), P(1, 1))))
                println(s.add(Line(P(0, 0), P(1, 1))))
                println(s.add(Line(P(0, 0), P(1, 2))))
                println(s.size())
                println(s.contains(Line(P(0, 0), P(1, 2))))
                println(Line(P(0, 0), P(1, 1)).hashCode() == Line(P(0, 0), P(1, 1)).hashCode())
            }
            """, "true\nfalse\ntrue\n2\ntrue\ntrue");
    }

    @Test
    void equalHashCodesAreSeparatedByEquals(@TempDir Path tmp) throws Exception {
        // Todos com o MESMO hashCode (7): o Set precisa cair no equals.
        both(tmp, HC + """
            main() {
                var s = setOf()
                s.add(Hc(1, 7))
                s.add(Hc(2, 7))
                s.add(Hc(1, 7))
                println(s.size())
                println(s.contains(Hc(2, 7)))
                println(s.contains(Hc(3, 7)))
                println(s.remove(Hc(1, 7)))
                println(s.contains(Hc(1, 7)))
                println(s.contains(Hc(2, 7)))
                println(s.size())
            }
            """, "2\ntrue\nfalse\ntrue\nfalse\ntrue\n1");
    }

    @Test
    void manyCollidingElements(@TempDir Path tmp) throws Exception {
        both(tmp, HC + """
            main() {
                var s = setOf()
                var k = 0
                while (k < 100) {
                    s.add(Hc(k, 42))
                    k = k + 1
                }
                var dup = 0
                k = 0
                while (k < 100) {
                    if (!s.add(Hc(k, 42))) {
                        dup = dup + 1
                    }
                    k = k + 1
                }
                println(s.size())
                println(dup)
                var removed = 0
                k = 0
                while (k < 100) {
                    if (s.remove(Hc(k, 42))) {
                        removed = removed + 1
                    }
                    k = k + 1
                }
                println(removed)
                println(s.isEmpty())
            }
            """, "100\n100\n100\ntrue");
    }

    @Test
    void negativeAndExtremeHashCodes(@TempDir Path tmp) throws Exception {
        // hashCode negativo/grande não pode quebrar o bucket nem o lookup.
        both(tmp, HC + """
            main() {
                var s = setOf()
                s.add(Hc(1, -5))
                s.add(Hc(2, -5))
                s.add(Hc(3, 2000000000))
                s.add(Hc(4, -2000000000))
                s.add(Hc(5, 0))
                println(s.size())
                println(s.contains(Hc(2, -5)))
                println(s.contains(Hc(3, 2000000000)))
                println(s.contains(Hc(4, -2000000000)))
                println(s.contains(Hc(9, -5)))
                println(s.add(Hc(5, 0)))
            }
            """, "5\ntrue\ntrue\ntrue\nfalse\nfalse");
    }

    @Test
    void userClassAsDeclaredSetTypeInClassAndFunction(@TempDir Path tmp) throws Exception {
        both(tmp, PT + """
            class Board {
                Set<Pt> cells
                public constructor() {
                    cells = setOf()
                }
                Bool mark(Int x, Int y) {
                    return cells.add(Pt(x, y))
                }
                Bool marked(Int x, Int y) {
                    return cells.contains(Pt(x, y))
                }
                Int count() {
                    return cells.size()
                }
            }
            Set<Pt> twice(Pt a) {
                Set<Pt> r = setOf()
                r.add(a)
                r.add(Pt(a.x, a.y))
                return r
            }
            main() {
                var b = Board()
                println(b.mark(1, 1))
                println(b.mark(1, 1))
                println(b.mark(2, 2))
                println(b.marked(2, 2))
                println(b.marked(3, 3))
                println(b.count())
                println(twice(Pt(5, 5)).size())
            }
            """, "true\nfalse\ntrue\ntrue\nfalse\n2\n1");
    }

    @Test
    void builtinValueTypesUseValueEquality(@TempDir Path tmp) throws Exception {
        both(tmp, """
            main() {
                var st = setOf("ab")
                println(st.add("a" + "b"))
                println(st.contains("ab"))
                println("ab".hashCode() == ("a" + "b").hashCode())
                var i = setOf(1)
                println(i.add(1))
                var l = setOf(1L)
                println(l.add(1L))
                var d = setOf(1.5)
                println(d.add(1.5))
                var b = setOf(true)
                println(b.add(true))
                println(b.add(false))
                var c = setOf('a')
                println(c.add('a'))
                println(c.add('b'))
            }
            """, "false\ntrue\ntrue\nfalse\nfalse\nfalse\nfalse\ntrue\nfalse\ntrue");
    }

    @Test
    void nanEqualsItselfInsideASet(@TempDir Path tmp) throws Exception {
        // Double.equals(NaN, NaN) == true (≠ do operador ==): HashSet acha o NaN.
        both(tmp, """
            main() {
                var s = setOf(0.0 / 0.0)
                println(s.contains(0.0 / 0.0))
                println(s.add(0.0 / 0.0))
                println(s.size())
            }
            """, "true\nfalse\n1");
    }

    @Test
    void intAndLongAreDistinctElements(@TempDir Path tmp) throws Exception {
        // Integer.equals(Long) é false na JVM; o JS (Long = BigInt) concorda.
        both(tmp, """
            main() {
                var i = setOf(1)
                println(i.contains(1L))
                var l = setOf(1L)
                println(l.contains(1))
            }
            """, "false\nfalse");
    }

    @Test
    void enumConstantsAreSingletons(@TempDir Path tmp) throws Exception {
        both(tmp, """
            enum Color { RED, GREEN }
            main() {
                var s = setOf(Color.RED)
                println(s.add(Color.RED))
                println(s.add(Color.GREEN))
                println(s.size())
                println(s.contains(Color.GREEN))
            }
            """, "false\ntrue\n2\ntrue");
    }

    // ------------------------------------------------------------------
    // Violações do contrato — comportamento depende do backend
    // ------------------------------------------------------------------

    @Test
    void equalsWithoutHashCodeIsNotDeduplicatedOnJvm(@TempDir Path tmp) throws Exception {
        // HashSet real: hashCode de identidade => baldes diferentes => o
        // equals nem é consultado e o "duplicado" entra. É o motivo do
        // contrato "equals ⇒ mesmo hashCode".
        runJvm(tmp, ONLY_EQ + """
            main() {
                var s = setOf()
                println(s.add(OnlyEq(1)))
                println(s.add(OnlyEq(1)))
                println(s.size())
            }
            """, "true\ntrue\n2");
    }

    @Test
    void equalsWithoutHashCodeIsDeduplicatedOnJs(@TempDir Path tmp) throws Exception {
        // CARACTERIZAÇÃO (diverge da JVM): o Set do JS é varredura linear
        // por equals e ignora hashCode. Se o JS passar a usar hashCode,
        // este teste deve passar a esperar "true\ntrue\n2" como na JVM.
        runJs(tmp, ONLY_EQ + """
            main() {
                var s = setOf()
                println(s.add(OnlyEq(1)))
                println(s.add(OnlyEq(1)))
                println(s.size())
            }
            """, "true\nfalse\n1");
    }

    @Test
    void mutatingAKeyAfterInsertionLosesItOnJvm(@TempDir Path tmp) throws Exception {
        // Clássico do HashSet: mudar campo usado no hashCode depois do add
        // deixa o elemento no balde antigo. contains(m) procura no balde
        // novo (vazio); contains(Pt(5,5)) acha o balde antigo mas equals
        // compara com o m já mutado (6,5) => false.
        runJvm(tmp, PT + """
            main() {
                var m = Pt(5, 5)
                var s = setOf()
                s.add(m)
                m.x = 6
                println(s.contains(m))
                println(s.contains(Pt(6, 5)))
                println(s.contains(Pt(5, 5)))
                println(s.size())
            }
            """, "false\nfalse\nfalse\n1");
    }

    @Test
    void mutatingAKeyAfterInsertionStillFindsItOnJs(@TempDir Path tmp) throws Exception {
        // CARACTERIZAÇÃO (diverge da JVM): sem hashing, o JS acha o elemento
        // mutado por equals na varredura.
        runJs(tmp, PT + """
            main() {
                var m = Pt(5, 5)
                var s = setOf()
                s.add(m)
                m.x = 6
                println(s.contains(m))
                println(s.contains(Pt(6, 5)))
                println(s.contains(Pt(5, 5)))
                println(s.size())
            }
            """, "true\ntrue\nfalse\n1");
    }

    // ------------------------------------------------------------------
    // Coleções como elemento: conteúdo (equals de List/Set)
    // ------------------------------------------------------------------

    @Test
    void collectionsAsElementsCompareByContentOnJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                var ls = setOf(listOf(1, 2))
                println(ls.add(listOf(1, 2)))
                println(ls.contains(listOf(1, 2)))
                println(ls.contains(listOf(2, 1)))
                var ss = setOf(setOf(1))
                println(ss.add(setOf(1)))
                println(ss.size())
            }
            """, "false\ntrue\nfalse\nfalse\n1");
    }

    @Test
    void collectionsAsElementsCompareByContentOnJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                var ls = setOf(listOf(1, 2))
                println(ls.add(listOf(1, 2)))
                println(ls.contains(listOf(1, 2)))
                println(ls.contains(listOf(2, 1)))
                var ss = setOf(setOf(1))
                println(ss.add(setOf(1)))
                println(ss.size())
            }
            """, "false\ntrue\nfalse\nfalse\n1");
    }
}
