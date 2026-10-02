package dev.kof.compiler;

/**
 * Programas Kof do gate de paridade do interpretador
 * ({@code KofInterpreterParityTest}), hoisted de inline para constantes. Vive
 * fora da classe de teste para mantê-la abaixo do limite de 500 linhas de teste
 * (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os testes e
 * o nome da classe seguem no {@code KofInterpreterParityTest}.
 */
abstract class KofInterpreterParityPrograms {

    static final String SRC_INCREMENT_WIDE_TYPES_AND_ARRAY_ELEMENT = """
                main() {
                    var c = 1L
                    c++
                    println(c)
                    ++c
                    println(c)
                    c--
                    println(c)
                    var d = 1.5
                    d++
                    println(d)
                    ++d
                    println(d)
                    d--
                    println(d)
                    var f = 1.5f
                    f++
                    println(f)
                    var i = 5
                    i++
                    println(i)
                    var l = 100L
                    l /= 3
                    println(l)
                    l += 2L
                    println(l)
                    d /= 2.0
                    println(d)
                    var max = 9223372036854775807L
                    max++
                    println(max)
                    var a = new Long[2]
                    a[0] = 7L
                    a[0]++
                    println(a[0])
                    println(++a[0])
                    a[1] = 40L
                    a[1]--
                    println(a[1])
                    var b = new Int[2]
                    b[0] = 7
                    b[0]++
                    println(b[0])
                    println(b[0]--)
                    println(b[0])
                }
                """;

    static final String SRC_LONG_BITWISE_SHIFT_MIXED = """
                main() {
                    var l = 5L
                    println(l & 3)
                    println(l | 3)
                    println(l ^ 3)
                    var i = 5
                    println(i & l)
                    var neg = -1
                    var big = 4294967295L
                    println(neg & big)
                    println(neg | big)
                    println(neg ^ big)
                    println(l << 2L)
                    println(l << 70)
                    println(l << 70L)
                    println(l >> 65L)
                    var one = 1
                    println(one << 40L)
                    println(one >> 40L)
                    println(one >>> 40L)
                    var n = -1L
                    println(n >>> 1)
                    println(n >>> 64L)
                    println(n >>> 65L)
                    var max = 9223372036854775807L
                    println(max + 1L)
                    println(max * 2L)
                    var min = -9223372036854775807L - 1L
                    println(-min)
                    var w = 5000000000L
                    var t = w as Int
                    println(t)
                    println(t + 1)
                    println((l as Int) & 3)
                }
                """;

    static final String SRC_CONTROL_FLOW = """
                classify(n: Int): String {
                    if (n < 0) { return "neg" }
                    else if (n == 0) { return "zero" }
                    else { return "pos" }
                }
                main() {
                    println(classify(-5))
                    println(classify(0))
                    println(classify(7))
                    var i = 0
                    while (i < 5) { println(i); i = i + 1 }
                    for (var it in listOf("a", "b", "c")) { println(it) }
                    var sum = 0
                    for (var k in listOf(10, 20, 30)) { sum = sum + k }
                    println(sum)
                    var x = 2
                    var desc = switch (x) {
                        case 1 -> "um"
                        case 2 -> "dois"
                        default -> "outro"
                    }
                    println(desc)
                }
                """;

    static final String SRC_RECORDS_AND_CLASSES = """
                record Point(Int x, Int y)
                class Counter {
                    Int count
                    public constructor() { this.count = 0 }
                    void inc() { this.count = this.count + 1 }
                    Int get() { return this.count }
                }
                main() {
                    var p1 = Point(1, 2)
                    var p2 = Point(1, 2)
                    var p3 = Point(9, 9)
                    println(p1 == p2)
                    println(p1 == p3)
                    println(p1)
                    println(p1.x())
                    var c = Counter()
                    c.inc()
                    c.inc()
                    c.inc()
                    println(c.get())
                }
                """;

    static final String SRC_TRY_CATCH_FINALLY_THROW = """
                risky(n: Int): Int {
                    if (n < 0) { throw "negativo" }
                    return n * 2
                }
                main() {
                    try {
                        println(risky(5))
                    } catch (String e) {
                        println("caught:" + e)
                    } finally {
                        println("fin1")
                    }
                    try {
                        println(risky(-1))
                    } catch (String e) {
                        println("caught:" + e)
                    } finally {
                        println("fin2")
                    }
                }
                """;

    static final String SRC_WIDE_PARAMETERS_OCCUPY_TWO_SLOTS = """
                Double g(Double a, Double b) { return a + b }
                Double unread(Double a, Double b) { return a }
                Double mixed(Int a, Double b, Double c) { return b + c }
                Long wideLong(Long a, Long b, Long c) { return a + c }
                class Box {
                    Double v
                    public constructor(Double v) { this.v = v }
                    Double add(Double x) { return this.v + x }
                    Long addLong(Long x) { return 10000000000L + x }
                }
                main() {
                    println(g(1.5, 2.5))
                    println(unread(1.5, 2.5))
                    println(mixed(9, 1.25, 2.25))
                    println(wideLong(10000000000L, 5L, 7L))
                    var b = Box(10.5)
                    println(b.add(0.5))
                    println(b.addLong(1L))
                }
                """;

    static final String SRC_COLLECTIONS_AND_HIGHER_ORDER = """
                main() {
                    var l = listOf(1, 2, 3, 4, 5)
                    println(l.size())
                    println(l.get(0))
                    println(l.contains(3))
                    println(l.isEmpty())
                    println(l.map((x: Int) -> x * 2).reduce((a: Int, b: Int) -> a + b, 0))
                    println(l.filter((x: Int) -> x % 2 == 0).size())
                    var m = mapOf("a", 1)
                    m.put("b", 2)
                    println(m.get("a"))
                    println(m.size())
                    println(m.containsKey("b"))
                    var s = setOf("x", "y", "z")
                    println(s.size())
                    println(s.contains("y"))
                    s.remove("y")
                    println(s.contains("y"))
                }
                """;

    static final String SRC_STRINGS_AND_CHARS = """
                main() {
                    var s = "Hello World"
                    println(s.length)
                    println(s.substring(6))
                    println(s.contains("World"))
                    println(s.startsWith("He"))
                    println(s.endsWith("ld"))
                    println(s.indexOf("o"))
                    println(s.toUpperCase())
                    println(s.split(" ").length)
                    println(s.charAt(1))
                    println("a" + "b" + "c")
                    println("ab" == "ab")
                    println("42".toInt() + 1)
                    var c = 'A'
                    println(c)
                    println(c + 1)
                }
                """;
}
