package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate de paridade PERMANENTE: o KofInterpreter (target de execução direta)
 * deve produzir saída byte-idêntica ao backend JVM compilado + fork, para um
 * corpus que cobre os construtores da linguagem. Paridade por construção —
 * mesma IR, dois executores. Qualquer divergência é bug do interpretador.
 */
class KofInterpreterParityTest extends KofInterpreterParitySupport {


    @Test
    void arithmeticAndComparisons() throws IOException {
        parity("arith", """
                main() {
                    println(10 + 20 * 3)
                    println(100 / 7)
                    println(17 % 5)
                    println(2 < 3)
                    println(3 <= 3)
                    println(4 > 5)
                    println(-7)
                    println(!true)
                    println(6 & 3)
                    println(6 | 3)
                    println(6 ^ 3)
                    println(1 << 4)
                    println(256 >> 2)
                }
                """);
    }

    @Test
    void floatsAndLongs() throws IOException {
        parity("float", """
                main() {
                    var a = 1.5
                    var b = 2.5
                    println(a + b)
                    println(a * b)
                    println(a < b)
                    var big: Long = 9000000000
                    println(big + 1)
                    println(big > 100)
                    var c: Float = 3.25
                    println(c * 2)
                }
                """);
    }

    @Test
    void wideParametersOccupyTwoSlots() throws IOException {
        // §163: parâmetros largos (Double/Long) ocupam DOIS slots no layout da
        // IR. O interpretador copiava `args` compacto nos locais, então o 2º
        // parâmetro largo era lido como `null` (NPE) — divergência silenciosa
        // de JVM/Native/JS. Cobre função, método de instância, construtor,
        // parâmetro largo não lido (limite do array de locais) e ordem mista.
        parity("wide-params", SRC_WIDE_PARAMETERS_OCCUPY_TWO_SLOTS);
    }

    @Test
    void doubleIeeeEquality() throws IOException {
        // §94: EQ/NE de Double/Float no interpretador usava Double.compare
        // (ordenação total) — NaN == NaN dava true e +0.0 == -0.0 dava false,
        // divergindo dos 3 compilados (IEEE). Agora usa == primitivo.
        parity("double-ieee", """
                main() {
                    println(math.sqrt(-1.0) == math.sqrt(-1.0))
                    println(math.sqrt(-1.0) != math.sqrt(-1.0))
                    if (math.sqrt(-1.0) == math.sqrt(-1.0)) { println("eq") } else { println("ne") }
                    var x = math.sqrt(-1.0)
                    var y = math.sqrt(-1.0)
                    println(x == y)
                    println(x != y)
                    println(0.0 == -0.0)
                    println(0.0 != -0.0)
                }
                """);
    }

    @Test
    void stringsAndChars() throws IOException {
        parity("string", SRC_STRINGS_AND_CHARS);
    }

    @Test
    void collectionsAndHigherOrder() throws IOException {
        parity("coll", SRC_COLLECTIONS_AND_HIGHER_ORDER);
    }

    // §108: o interpretador guarda Bool como Integer 0/1 na fronteira da
    // coleção → println(listOf(true)) dava [1, 0] vs JVM [true, false].
    // Box/unbox espelhando JvmOpCollections (Boolean ↔ Integer) na inclusão
    // e extração, nos 3 contêineres. Char NÃO precisa (JVM imprime [97,98]).
    // O discriminador é o toString do contêiner (println(l)) — println do
    // ELEMENTO individual passa pelo normalizeReturn do IR (dá "true" de
    // qualquer forma); o ArrayList.toString usa o toString do objeto cru.
    @Test
    void boolInCollectionsPrintsLikeJvm() throws IOException {
        parity("boolcoll", """
                main() {
                    var l = listOf(true, false)
                    println(l)
                    println(l.get(0))
                    println(l.contains(true))
                    var m = mapOf("yes", true)
                    println(m)
                    println(m.get("yes"))
                    println(m.containsKey("yes"))
                    var s = setOf(true)
                    println(s)
                    println(s.contains(true))
                    l.set(0, false)
                    println(l)
                }
                """);
    }

    @Test
    void recordsAndClasses() throws IOException {
        parity("rec", SRC_RECORDS_AND_CLASSES);
    }

    @Test
    void recordsInCollectionsUseContentEquals() throws IOException {
        // bug 104a: KofObj de record precisa de equals/hashCode/toString VIRTUAIS
        // — o JDK chama Object.* dentro de ArrayList.contains, HashMap e
        // List.toString; sem o override o interpretador batia por identidade.
        parity("reccoll", """
                record Point(Int x, Int y)
                main() {
                    var p1 = Point(1, 2)
                    var p2 = Point(1, 2)
                    println(listOf(p1).contains(p2))
                    println(setOf(p1).contains(p2))
                    println(mapOf(p1, 7).get(p2))
                    println(listOf(p1))
                    println(listOf(p1, Point(9, 9)).contains(p2))
                }
                """);
    }

    @Test
    void controlFlow() throws IOException {
        parity("flow", SRC_CONTROL_FLOW);
    }

    @Test
    void patternMatching() throws IOException {
        parity("match", """
                record Point(Int x, Int y)
                describe(o: Object): String {
                    switch (o) {
                        case String s: return "str:" + s
                        case Point(var px, var py): return "pt:" + px + "," + py
                        default: return "other"
                    }
                }
                main() {
                    println(describe("hi"))
                    println(describe(Point(3, 4)))
                    println(describe(42))
                }
                """);
    }

    @Test
    void nullSafety() throws IOException {
        parity("null", """
                find(k: String): String? {
                    if (k == "ok") { return "achou" }
                    return null
                }
                main() {
                    var v = find("ok")
                    if (v != null) { println(v) }
                    var w = find("no")
                    if (w == null) { println("nada") }
                    else { println(w) }
                }
                """);
    }

    @Test
    void closuresCapture() throws IOException {
        parity("closure", """
                main() {
                    var base = 10
                    var addN = (x: Int) -> x + base
                    println(addN(5))
                    base = 20
                    println(addN(5))
                    var make = (n: Int) -> (x: Int) -> x * n
                    var dbl = make(2)
                    println(dbl(21))
                }
                """);
    }

    @Test
    void tryCatchFinallyThrow() throws IOException {
        parity("try", SRC_TRY_CATCH_FINALLY_THROW);
    }

    @Test
    void recursion() throws IOException {
        parity("recur", """
                fib(n: Int): Int {
                    if (n < 2) { return n }
                    return fib(n - 1) + fib(n - 2)
                }
                fact(n: Int): Int {
                    if (n <= 1) { return 1 }
                    return n * fact(n - 1)
                }
                main() {
                    println(fib(10))
                    println(fact(6))
                }
                """);
    }

    @Test
    void arrays() throws IOException {
        parity("array", """
                main() {
                    var arr = new Int[5]
                    var i = 0
                    while (i < 5) { arr[i] = i * i; i = i + 1 }
                    println(arr.length)
                    println(arr[4])
                    var names = new String[2]
                    names[0] = "a"
                    names[1] = "b"
                    println(names[0] + names[1])
                }
                """);
    }

    @Test
    void enums() throws IOException {
        parity("enum", """
                enum Color { RED, GREEN, BLUE }
                main() {
                    println(Color.RED)
                    println(Color.values().size())
                }
                """);
    }

    @Test
    void spawnAwait() throws IOException {
        parity("spawn", """
                compute(): Int { return 21 }
                main() {
                    val h = spawn compute()
                    println(await h * 2)
                    var i = 0
                    while (i < 3) {
                        val hh = spawn compute()
                        println(await hh)
                        i = i + 1
                    }
                }
                """);
    }

    @Test
    void staticFieldInitializer() throws IOException {
        // Regressão: campo estático com valor inicial (constante do campo no
        // bytecode JVM) precisa ser semeado no interpretador — sem isso o
        // REPL (var de topo → static) imprimia 0 em vez do valor.
        parity("static", """
                class G {
                    static Int counter = 41
                    static String label = "kof"
                }
                main() {
                    println(G.counter)
                    println(G.label)
                    G.counter = G.counter + 1
                    println(G.counter)
                }
                """);
    }

    @Test
    void jsonEncode() throws IOException {
        parity("json", """
                import kof.json
                record User(String name, Int age)
                main() {
                    println(json.encode(User("mel", 26)))
                    println(json.encode(listOf(1, 2, 3)))
                    println(json.encode(mapOf("k", 9)))
                }
                """);
    }

    // known-bugs #48 — json.decode<List<Record>> no interpretador: o
    // kof_json_decode_object_list (2 args) não era tratado (NoSuchMethodError).
    @Test
    void jsonDecodeListOfRecord() throws IOException {
        parity("json-decode-list", """
                import kof.json
                record P(Int x)
                main() {
                    var l = json.decode<List<P>>("[{\"x\":1},{\"x\":2}]")
                    println(l.size())
                    println(l.get(0).x)
                    println(l.get(1).x)
                }
                """);
    }

    @Test
    void printNullableStringNull() throws IOException {
        // §124: println(String? null) NPEava no interpretador (o scorer de
        // assinatura do invokeExternal empatava valueOf(char[]) com
        // valueOf(Object) p/ arg null e a ordem do getMethods() escolhia o
        // array). JVM/Native imprimem "null" — o interpretador tem de imprimir.
        parity("print-null-str", """
                String? nd() { return null }
                main() {
                    println(nd())
                }
                """);
        parity("map-miss-print", """
                main() {
                    var m = mapOf(1, "um")
                    m.remove(1)
                    println(m.get(1))
                }
                """);
    }

    @Test
    void printNullablePrimitiveNull() throws IOException {
        // §125 (decisão da mantenedora 12/09, opção A): println(<primitivo>?
        // null) imprime o DEFAULT do primitivo (precedente do map-miss,
        // SG-008), nunca "null". Antes o return de Int? crashava o bytecode
        // (VerifyError no JVM) e o interpretador (NoSuchMethodError
        // Integer.valueOf/1); agora o IR emite o default direto no return-site.
        parity("print-null-int", """
                Int? ni() { return null }
                main() {
                    println(ni())
                }
                """);
        parity("print-null-bool", """
                Troolean nb() { return null }
                main() {
                    println(nb())
                }
                """);
        parity("print-null-double", """
                Double? nd() { return null }
                main() {
                    println(nd())
                }
                """);
        parity("print-int-nullable-value", """
                Int? ni() { return 5 }
                main() {
                    println(ni() + 1)
                }
                """);
        // §125(A) extensão (12/09): expression-body e slot anotado — o null
        // mora num RAMO do if/switch, não no topo do return; o fold colapsa
        // o ramo p/ default do primitivo (mesmo contrato da forma block).
        parity("expr-body-null-branch", """
                Int? en(Int x) = if (x > 0) x else null
                main() {
                    println(en(7))
                    println(en(-7))
                }
                """);
        parity("expr-body-switch-null-branch", """
                Int? sw(Int x) = switch (x) { case 1 -> 10 default -> null }
                main() {
                    println(sw(1))
                    println(sw(2))
                }
                """);
        parity("annotated-slot-null-branch", """
                main() {
                    Int? v = if (false) 9 else null
                    println(v)
                }
                """);
    }

    @Test
    void longBitwiseShiftMixed() throws IOException {
        // §167: bitwise/shift com Int e Long misturados + overflow/wrap de
        // Long. O interpretador já estava correto; o JVM emitia VerifyError
        // (inferência INT p/ `int & long` + `land` sobre int). Paridade
        // interpretado×JVM byte-a-byte (o JS tem cobertura em BackendParityTest).
        parity("longbitshift", SRC_LONG_BITWISE_SHIFT_MIXED);
    }

    @Test
    void incrementWideTypesAndArrayElement() throws IOException {
        // §168: `++`/`--`/compound em long/double + incremento de elemento de
        // array. O JVM emitia VerifyError (literal INT 1 em binário de 2 slots,
        // DUP de 1 slot, arraystore sem [array,index]); o interpretador era o
        // oracle. Paridade interpretado×JVM byte-a-byte (JS/native em
        // BackendParityTest/conformance).
        parity("incrwide", SRC_INCREMENT_WIDE_TYPES_AND_ARRAY_ELEMENT);
    }

    @Test
    void charBoolArrayStore() throws IOException {
        // §185: o interpretador crashava ("argument type mismatch") ao gravar
        // em Char[]/Bool[] — coerceFor devolvia Integer para o slot
        // char[]/boolean[] e o Array.set do reflect rejeitava. Fix: a coerção
        // produz o tipo REAL do slot (Character/Boolean). Paridade byte-a-byte
        // interpretado×JVM cobre store de literal, char/bool/número e leitura.
        parity("charboolarr", """
                main() {
                    var c = new Char[2]
                    c[0] = 'A'
                    println(c[0])
                    c[1] = 66
                    println(c[1])
                    var b = new Bool[2]
                    b[0] = true
                    println(b[0])
                    b[1] = false
                    println(b[1])
                }
                """);
    }
}
