package dev.kof.script;

/**
 * Programas Kof do E2E do interpretador ({@code KofScriptTest}), hoisted de
 * inline para constantes. Vive fora da classe de teste para mantê-la abaixo do
 * limite de 500 linhas de teste (Fase 3 da arquitetura de testes,
 * {@code D-TEST-ARCHITECTURE-GO}).
 */
abstract class KofScriptPrograms {

    static final String SRC_EVAL_PATTERN_MATCHING = """
                main() {
                    var x: Object = "hello"
                    switch (x) {
                        case String s:
                            println("str:" + s)
                        default:
                            println("other")
                    }
                }
                """;

    static final String SRC_INTERPRETER_RUNS_COLLECTIONS_AND_RECORDS = """
                record Point(Int x, Int y)
                main() {
                    var l = listOf(1, 2, 3)
                    println(l.map((v: Int) -> v * 2).reduce((a: Int, b: Int) -> a + b, 0))
                    var m = mapOf("k", 9)
                    println(m.get("k"))
                    var p1 = Point(1, 2)
                    var p2 = Point(1, 2)
                    println(p1 == p2)
                    println(p1)
                }
                """;

    static final String SRC_INTERPRETER_RUNS_SPAWN_AWAIT_AND_TRY_FINALLY = """
                work(): Int { return 21 }
                main() {
                    val h = spawn work()
                    println(await h * 2)
                    try {
                        throw "boom"
                    } catch (String e) {
                        println("caught:" + e)
                    } finally {
                        println("fin")
                    }
                }
                """;

    static final String SRC_INTERPRETER_MATCHES_COMPILED_JVM_OUTPUT = """
                record Point(Int x, Int y)
                add(a: Int, b: Int): Int { return a + b }
                main() {
                    println(add(2, 3))
                    println("a" + "b")
                    println(Point(1, 2) == Point(1, 2))
                    println(listOf(1, 2, 3).size())
                    var i = 0
                    while (i < 3) { println(i); i = i + 1 }
                    for (var it in listOf("x", "y")) { println(it) }
                }
                """;

    static final String SRC_INTERPRETER_NULL_SAFETY_MATCHES_JVM = """
                find(k: String): String? {
                    if (k == "ok") { return "achou" }
                    return null
                }
                main() {
                    var v = find("ok")
                    if (v != null) { println(v) }
                    var w = find("no")
                    if (w == null) { println("nada") }
                }
                """;

    static final String SRC_INTERPRETER_CHANNEL_MATCHES_JVM = """
                import kof.time
                main() {
                    val c = channel<Int>()
                    spawn {
                        println("recv-wait")
                        val v = c.receive()
                        println("recv:" + v)
                    }
                    time.sleep(30)
                    println("pre-send")
                    c.send(42)
                    println("post-send")
                }
                """;

    static final String SRC_SCRIPT_TARGET_RUNS_DIRECTLY = """
                record Point(Int x, Int y)
                main() {
                    var o = Point(3, 4)
                    var r = switch (o) {
                        case Point(var x, var y) -> x + "," + y
                        default -> "other"
                    }
                    println(r)
                }
                """;

    static final String SRC_JSON_DECODE_RECORD_RUNS_ON_INTERPRETER = """
                record Point(Int x, Int y)
                main() {
                    var dp = json.decode<Point>("{\\"x\\": 10, \\"y\\": 20}")
                    println(dp.x)
                    println(dp.y)
                    var s = json.encode(Point(3, 4))
                    var rt = json.decode<Point>(s)
                    println(rt.x)
                    println(rt.y)
                }
                """;
}
