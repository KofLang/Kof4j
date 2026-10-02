package dev.kof.compiler;

/**
 * Programas Kof do teste de resolução semântica ({@code SemanticResolutionTest}), hoisted de inline
 * para constantes. Vive fora da classe de teste (Fase 3 da arquitetura de
 * testes, {@code D-TEST-ARCHITECTURE-GO}) e do harness para que ambos fiquem
 * abaixo do limite de 500 linhas; os testes e o nome da classe seguem no
 * {@code SemanticResolutionTest}.
 */
abstract class SemanticResolutionPrograms {

    static final String SRC_MECHANISM_MODIFIER_WARNS_BUT_STAYS_GREEN = """
                class Contador {
                    Map<String, Int> dados
                    public constructor() {
                        this.dados = mapOf()
                    }
                    synchronized Int somar(String chave) {
                        return 1
                    }
                }
                main() { println(Contador().somar("a")) }
                """;

    static final String SRC_MECHANISM_MODIFIER_WARNS_BUT_STAYS_GREEN_2 = """
                class C {
                    volatile Int x
                    Int f() { return 1 }
                }
                main() { println(0) }
                """;

    static final String SRC_MECHANISM_MODIFIER_WARNS_BUT_STAYS_GREEN_3 = """
                class C {
                    Int x
                    Int f() { return 1 }
                }
                main() { println(C().f()) }
                """;

    static final String SRC_UNKNOWN_METHOD_ON_SUPER = """
                class Base {
                    Int ok() { return 1 }
                }
                class Sub extends Base {
                    Int bad() { return super.naoExiste() }
                }
                main() { println(Sub().bad()) }
                """;

    static final String SRC_UNKNOWN_FIELD_ON_KNOWN_CLASS = """
                class P {
                    Int a
                }
                main() {
                    var p = P()
                    println(p.campoInexistente)
                }
                """;

    static final String SRC_VALID_CALLS_STAY_GREEN = """
                class Base {
                    Int ok() { return 1 }
                }
                class Sub extends Base {
                    Int usa() { return super.ok() }
                }
                main() {
                    var s = Sub()
                    println(s.usa())
                    var l = listOf(1, 2, 3)
                    l.add(4)
                    println(l.size())
                    println(l.contains(2))
                    var m = mapOf("a", 1)
                    m.put("b", 2)
                    println(m.get("a"))
                    var st = setOf("x", "y")
                    println(st.contains("x"))
                    log.info("hello")
                    println(time.now())
                    println("ok")
                }
                """;

    static final String SRC_ABSTRACT_CLASS_INSTANTIATION_FAILS = """
                abstract class Shape {
                    Int area() { return 0 }
                }
                main() {
                    var s = Shape()
                    println(s)
                }
                """;

    static final String SRC_ABSTRACT_CLASS_SUBCLASS_INSTANTIATION_STAYS_GREEN = """
                abstract class Shape {
                    Int area() { return 0 }
                }
                class Circle extends Shape {
                }
                main() {
                    var c = Circle()
                    println(c)
                }
                """;

    static final String SRC_UNKNOWN_METHOD_ON_KNOWN_CLASS = """
                class P {
                    Int a
                }
                main() {
                    var p = P()
                    p.naoExiste()
                }
                """;

    static final String SRC_REDECLARATION_FALSE_POSITIVE_EM_METODO_DE_CLASSE = """
                class Node {
                    Int value
                    Int rest
                    constructor(Int value, Int rest) { this.value = value; this.rest = rest }
                }
                class S {
                    Int total
                    constructor() { this.total = 0 }
                    add(Int v) {
                        return this.add2(Node(v, 0))
                    }
                    add2(Node n) {
                        var q = n.value
                        var r = n.rest
                        total = total + q + r
                        return total
                    }
                }
                main() {
                    var s = S()
                    s.add(3)
                    println(s.total == 3)
                }
                """;

    static final String SRC_REDECLARACAO_MESMO_CORPO_AINDA_ERRO = """
                main() {
                    var q = 1
                    var q = 2
                    println(q)
                }
                """;

    static final String SRC_PRIMITIVE_AS_TYPE_AND_LITERAL_STILL_COMPILE = """
                main() {
                    var x: Int = 2147483647
                    var s = "abc"
                    println(s.length)
                    println("a😀b".length)
                    val big = 3000000000
                    println(x + big)
                }
                """;

    static final String SRC_STRING_METHODS_WITH_STRING_OR_CHAR_ARGS_STILL_COMPILE = """
                main() {
                    var s = "abc"
                    println(s.indexOf("c"))
                    println(s.replace('b', 'x'))
                    println(s.charAt(1))
                    println(s.substring(1))
                    println(s.contains("b"))
                    println(s.compareTo("a"))
                }
                """;

    static final String SRC_STRINGS_FUNCTIONS_AND_REAL_STRING_METHODS_STILL_COMPILE = """
                main() {
                    println(strings.repeat("ab", 3))
                    println(strings.truncate("abcdef", 3))
                    println(strings.padLeft("7", 3, "0"))
                    println(strings.padRight("7", 3, "0"))
                    println(strings.reverse("ab"))
                    println(strings.capitalize("ab"))
                    println(strings.count("abc", "a"))
                    println(strings.isAlpha("a"))
                    var s = "ab"
                    println(s.toUpperCase())
                    println(s.trim())
                    println(s.replace("a", "b"))
                    println(s.substring(1))
                }
                """;

    static final String SRC_STRING_EQUALITY_AND_NUMERIC_ORDERING_STILL_COMPILE = """
                main() {
                    var a = "abc"
                    var b = "abd"
                    println(a == b)
                    println(a != b)
                    println(a == "abc")
                    println(3 < 5)
                    println(3L <= 5L)
                    println(2.5 > 1.5)
                    println(a.compareTo(b) < 0)
                    var n = 0
                    while (n < 10) { n = n + 1 }
                    println(n)
                }
                """;

    static final String SRC_LIST_INDEX_INT_AND_UNKNOWN_STILL_COMPILES = """
                main() {
                    var l = listOf(5, 7)
                    println(l.remove(1))
                    println(l.get(0))
                    l.set(0, 9)
                    println(l.size)
                }
                """;

    static final String SRC_QUERY_SIDE_AND_WIDENING_NOT_REJECTED = """
                main() {
                    var l = listOf("a", "b")
                    println(l.contains(5))          // query miss (não rejeita)
                    var m = mapOf("a", 1)
                    println(m.get(5))              // query miss
                    println(m.size())
                    var s = setOf("a")
                    println(s.contains(5))         // query miss
                    var n = listOf()               // primeiro add PINA
                    n.add(1)
                    n.add(2)                       // homogêneo → ok
                    n.add(3)
                    println(n.get(0))
                    var big = listOf(1, 2)
                    big.add(9)                     // Int→Int, ok (sem widening aqui)
                    println(big.size)
                }
                """;

    static final String SRC_SUBSCRIPT_ON_ARRAYS_STILL_COMPILES = """
                main() {
                    var nums = new Int[3]
                    nums[0] = 5
                    println(nums[0])
                    var words = new String[2]
                    words[1] = "x"
                    println(words[1])
                    var grid = new Int[2][2]
                    grid[0][1] = 7
                    println(grid[0][1])
                }
                """;

    static final String SRC_FOR_IN_LIST_AND_ARRAY_STILL_COMPILES = """
                List<Int> mk() { return listOf(4, 5) }
                main() {
                    for (var x in listOf(1, 2)) { println(x) }
                    var arr = new Int[2]
                    arr[0] = 7
                    for (var a in arr) { println(a) }
                    for (var y in mk()) { println(y) }
                }
                """;
}
