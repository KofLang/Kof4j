package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate de codegen JVM — "kitchen sink" com ORACLE de verificação (plano de
 * estabilização, parte 4). Cada construto que desce até o codegen é
 * compilado para JVM e executado com {@code -Xverify:all} (verificador
 * ESTREITO). O oracle: exit 0, saída esperada, e NENHUM
 * VerifyError/ClassFormatError/ClassNotFoundError/ClassCastException — os
 * quatro modos pelos quais bytecode inválido escapa do "compilou" e estoura
 * no load/verify/run.
 *
 * Por que importa: bugs 30/31/56/57/58 (GitHub) COMPIAVAM e falhavam no
 * verificador/na carga — sem {@code -Xverify:all} o bug passa silencioso.
 * Este teste NÃO caça bugs novos; trava os já corrigidos e garante que
 * nenhum refactor do codegen (ex.: REFACTOR-500) re-introduza bytecode
 * inválido por construto.
 */
class CodegenKitchenSinkTest {

    @Test
    void allCodegenConstructsSurviveStrictVerifier(@TempDir Path tempDir) throws IOException {
        for (Case c : cases()) {
            Path src = tempDir.resolve(c.name + ".kf");
            Files.writeString(src, c.source);
            Path out = tempDir.resolve(c.name + "-jvm");
            // driver FRESH por caso (bug 51): reutilizar vaza LambdaTask
            // sintética e quebra a 2ª compilação com lambda (CLI é
            // 1 processo/compilação — espelha isso aqui).
            CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
            assertTrue(r.success(), c.name + " — compilação JVM deve passar: "
                    + r.diagnostics().getDiagnostics());

            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-Xverify:all", "-cp", out.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int code = 0;
            try { code = p.waitFor(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            boolean verifierFailure = output.contains("VerifyError")
                    || output.contains("ClassFormatError")
                    || output.contains("ClassNotFoundException")
                    || output.contains("ClassCastException");
            assertTrue(code == 0 && !verifierFailure && output.equals(c.expected),
                    c.name + " — oracle strict-verifier: exit=" + code
                            + " verifierFailure=" + verifierFailure
                            + " esperado=[" + c.expected + "] obtido=[" + output + "]");
        }
    }

    record Case(String name, String source, String expected) {}

    private java.util.List<Case> cases() {
        java.util.List<Case> l = new java.util.ArrayList<>();
        l.add(new Case("strings", """
                main() {
                    var s = "Hello World"
                    println(s.length)
                    println(s.charAt(1))
                    println(s.substring(6))
                    println(s.contains("World"))
                    println(s.startsWith("Hello"))
                    var parts = "a,b,c".split(",")
                    println(parts.size)
                    println(parts.get(0))
                }
                """, "11\n101\nWorld\ntrue\ntrue\n3\na"));
        l.add(new Case("collections", """
                main() {
                    var l = listOf(1, 2, 3)
                    l.add(4)
                    l.set(0, 9)
                    println(l.get(0))
                    println(l.size)
                    println(l.contains(4))
                    var m = mapOf("a", 1)
                    m.put("b", 2)
                    println(m.get("b"))
                    var s = setOf("x", "y")
                    println(s.contains("x"))
                }
                """, "9\n4\ntrue\n2\ntrue"));
        l.add(new Case("higherOrder", """
                main() {
                    var nums = listOf(1, 2, 3, 4)
                    var doubled = nums.map((n: Int) -> n * 2)
                    println(doubled.get(2))
                    var evens = nums.filter((n: Int) -> n % 2 == 0)
                    println(evens.size)
                    var total = nums.reduce((a: Int, b: Int) -> a + b, 0)
                    println(total)
                }
                """, "6\n2\n10"));
        l.add(new Case("records", """
                record Point(Int x, Int y)
                record Pair(Int v, Point p)
                main() {
                    var p = Point(10, 20)
                    println(p.x())
                    var q = Pair(5, p)
                    println(q.p().y())
                }
                """, "10\n20"));
        // bug 34 (GitHub #34) — record com campo List<Record>
        l.add(new Case("recordsWithList", """
                record Item(String name, Int qty)
                record Order(String id, List<Item> items)
                main() {
                    var items = listOf(Item("a", 1), Item("b", 2))
                    var o = Order("o1", items)
                    println(o.items().get(1).name())
                    println(o.items().get(1).qty())
                }
                """, "b\n2"));
        l.add(new Case("classesMutable", """
                class User {
                    String name
                    Int age
                    public constructor(String name, Int age) {
                        this.name = name
                        this.age = age
                    }
                    String greeting() { return "Hi " + name }
                }
                main() {
                    var u = User("Mel", 26)
                    u.age = 27
                    println(u.greeting())
                    println(u.age)
                }
                """, "Hi Mel\n27"));
        l.add(new Case("ifExpression", """
                main() {
                    var ativo = true
                    var status = if (ativo) "on" else "off"
                    println(status)
                    var n = 3
                    var big = if (n > 5) "big" else "small"
                    println(big)
                }
                """, "on\nsmall"));
        l.add(new Case("switchExpression", """
                record Point(Int x, Int y)
                main() {
                    var obj = Point(1, 2)
                    var desc = switch (obj) {
                        case String s -> "str:" + s
                        case Point(var x, var y) -> x + "," + y
                        default -> "outro"
                    }
                    println(desc)
                }
                """, "1,2"));
        l.add(new Case("tryCatchFinally", """
                main() {
                    try {
                        throw "not found: 42"
                    } catch (String e) {
                        println("erro: " + e)
                    } finally {
                        println("fim")
                    }
                }
                """, "erro: not found: 42\nfim"));
        // bug 49 (lane JS corrigido) — try aninhado também no JVM
        l.add(new Case("nestedTry", """
                main() {
                    try {
                        try {
                            throw "inner"
                        } catch (String e) {
                            println("inner: " + e)
                        }
                    } catch (String e) {
                        println("outer: " + e)
                    }
                }
                """, "inner: inner"));
        l.add(new Case("nullNarrowing", """
                String? find(Int k) { return if (k == 1) "um" else null }
                main() {
                    var a: String? = find(1)
                    if (a != null) { println("a=" + a) }
                    var b: String? = find(9)
                    if (b != null) { println("b=" + b) } else { println("b=nulo") }
                }
                """, "a=um\nb=nulo"));
        l.add(new Case("spawnAwait", """
                Int compute(Int n) { return n * 2 }
                main() {
                    val h = spawn compute(21)
                    var v = await h
                    println(v)
                }
                """, "42"));
        // bug 31 (GitHub #31, bug 57) — await sobre handle via Object e Handle<T>
        l.add(new Case("handleObjectParam", """
                Int calc(Int x) { return x * 2 }
                Int take(Object h) { return await h }
                main() {
                    val h = spawn calc(21)
                    println(take(h))
                }
                """, "42"));
        l.add(new Case("handleTypedParam", """
                Int calc(Int x) { return x * 2 }
                Int take(Handle<Int> h) { return await h }
                main() {
                    val h = spawn calc(21)
                    println(take(h))
                }
                """, "42"));
        l.add(new Case("castAndBox", """
                main() {
                    var big = 1000000L
                    var i = big as Int
                    println(i)
                    var s = "7"
                    println(s.length)
                }
                """, "1000000\n1"));
        // bug 30 (GitHub #30, bug 56) — split + acesso ao array
        l.add(new Case("splitArrayAccess", """
                main() {
                    var parts = "a,b,c".split(",")
                    println(parts.size)
                    println(parts.get(0))
                    println(parts.length)
                }
                """, "3\na\n3"));
        l.add(new Case("cStyleLoop", """
                main() {
                    var acc = ""
                    for (var i = 1; i <= 3; i = i + 1) {
                        acc = acc + i
                    }
                    println(acc)
                }
                """, "123"));
        l.add(new Case("whileLoop", """
                main() {
                    var i = 0
                    var s = ""
                    while (i < 3) { s = s + "x"; i = i + 1 }
                    println(s)
                }
                """, "xxx"));
        return l;
    }
}
