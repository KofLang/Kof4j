package dev.kof.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofScriptTest {

    @Test
    void evalPrintsHello() throws Exception {
        var r = KofScript.eval("""
                println("hello from script")
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("hello from script", r.stdout().trim());
    }

    @Test
    void evalWithTopLevelFnAndVar() throws Exception {
        // KofScript = Kof puro: função de topo com forma idiomática + var de
        // topo (vira global via KofScriptGlobals). Sem sugar de outra língua.
        var r = KofScript.eval("""
                add(a: Int, b: Int): Int = a + b
                main() {
                    var x = add(2, 3)
                    println(x)
                }
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("5", r.stdout().trim());
    }

    @Test
    void evalPatternMatching() throws Exception {
        var r = KofScript.eval("""
                main() {
                    var x: Object = "hello"
                    switch (x) {
                        case String s:
                            println("str:" + s)
                        default:
                            println("other")
                    }
                }
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("str:hello", r.stdout().trim());
    }

    @Test
    void evalInstanceofAndAs() throws Exception {
        var r = KofScript.eval("""
                main() {
                    var a: Object = "world"
                    if (a instanceof String) {
                        println("is string")
                    }
                    var b: Object = "test" as String
                    println(b)
                }
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("is string\ntest", r.stdout().trim().replace("\r\n","\n"));
    }

    @Test
    void runFileDirect(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("prog.kf");
        Files.writeString(f, """
                main() {
                    println(42)
                }
                """);
        var r = KofScript.runFile(f);
        assertTrue(r.success(), r.stderr());
        assertEquals("42", r.stdout().trim());
    }

    @Test
    void evalJsTarget() throws Exception {
        var r = dev.kof.compiler.Target.JS != null ? KofScript.eval("println(7)", dev.kof.compiler.Target.JS) : null;
        // JS eval uses embedded GraalJS, stdout is captured via KofJsRunner (which prints to System.out, not RunResult.stdout for JS)
        // For MVP, we just check success (JS stdout goes to System.out, not RunResult for JS path)
        // Instead test via runFile JS direct
        Path tmp = Files.createTempDirectory("jstest");
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, "main() { println(7) }");
        var r2 = KofScript.runFile(f, dev.kof.compiler.Target.JS);
        assertTrue(r2.success(), r2.stderr() + r2.stdout());
    }

    @Test
    void evalNativeTarget() throws Exception {
        Path tmp = Files.createTempDirectory("nativetest");
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, "main() { println(7) }");
        var r = KofScript.runFile(f, dev.kof.compiler.Target.NATIVE);
        assertTrue(r.success(), r.stderr() + r.stdout());
        assertEquals("7", r.stdout().trim());
    }

    @Test
    void evalPureKofWithTopLevelVarAndSpawn() throws Exception {
        // KofScript = Kof puro: modo script (sem main) — var de topo vira
        // global; concorrência é spawn/await (não `async`). Sem sugar.
        var r = KofScript.eval("""
                var counter = 0
                bump(): Int {
                    counter = counter + 1
                    return counter
                }
                println(bump())
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("1", r.stdout().trim());
    }

    @Test
    void jsSugarIsRejected() throws Exception {
        // let/const/async/fn NÃO existem no KofScript (não é JavaScript):
        // falham com o diagnóstico normal do parser Kof (R6: nunca silencioso).
        var r = KofScript.eval("""
                fn foo(a: Int): Int = a + 1
                main() {
                    let x = 5
                    println(foo(x))
                }
                """);
        assertFalse(r.success(), "fn/let não devem compilar em KofScript");
        assertTrue(r.stderr().contains("PARSE085"), "esperava PARSE085, veio: " + r.stderr());
    }

    @Test
    void interpreterRunsCollectionsAndRecords() throws Exception {
        // KofScript roda pelo interpretador da IR (sem fork de JVM): coleções,
        // records (== de conteúdo + toString), higher-order.
        var r = KofScript.eval("""
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
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("12\n9\ntrue\nPoint[x=1, y=2]", r.stdout().trim().replace("\r\n", "\n"));
    }

    @Test
    void interpreterRunsSpawnAwaitAndTryFinally() throws Exception {
        // spawn/await + try/catch/finally com exceção-as-String pelo
        // interpretador — mesma semântica do caminho compilado.
        var r = KofScript.eval("""
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
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("42\ncaught:boom\nfin", r.stdout().trim().replace("\r\n", "\n"));
    }

    @Test
    void interpreterMatchesCompiledJvmOutput(@TempDir Path tmp) throws Exception {
        // Paridade por construção: o MESMO programa, interpretado vs compilado
        // + fork de JVM, produz saída idêntica.
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, """
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
                """);
        var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
        assertTrue(interp.success(), interp.stderr());
        // caminho compilado (bytecode + JVM real) para comparação
        var compiled = KofScript.runFileCompiled(f, dev.kof.compiler.Target.JVM, new String[0]);
        assertTrue(compiled.success(), compiled.stderr());
        assertEquals(compiled.stdout(), interp.stdout(), "interpretado deve casar o JVM compilado");
    }

    @Test
    void interpreterNullSafetyMatchesJvm(@TempDir Path tmp) throws Exception {
        // Regressão do null na pilha (função que retorna null): o interpretador
        // precisa empilhar null (LinkedList, não ArrayDeque) — paridade com JVM.
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, """
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
                """);
        var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
        assertTrue(interp.success(), interp.stderr());
        assertEquals("achou\nnada", interp.stdout().trim().replace("\r\n", "\n"));
        var compiled = KofScript.runFileCompiled(f, dev.kof.compiler.Target.JVM, new String[0]);
        assertEquals(compiled.stdout(), interp.stdout(), "null-safety: interpretado == JVM");
    }

    @Test
    void interpreterClosureCaptureMatchesJvm(@TempDir Path tmp) throws Exception {
        // Closure capturando variável mutável — mesma semântica de referência.
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, """
                main() {
                    var base = 10
                    var addN = (x: Int) -> x + base
                    println(addN(5))
                    base = 20
                    println(addN(5))
                }
                """);
        var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
        assertTrue(interp.success(), interp.stderr());
        var compiled = KofScript.runFileCompiled(f, dev.kof.compiler.Target.JVM, new String[0]);
        assertEquals(compiled.stdout(), interp.stdout(), "closure capture: interpretado == JVM");
    }

    @Test
    void interpreterChannelMatchesJvm(@TempDir Path tmp) throws Exception {
        // channel<T>() com spawn de closure capturando o canal — receive
        // bloqueia até send, mesma ordem no interpretador e no JVM.
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, """
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
                """);
        var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
        assertTrue(interp.success(), interp.stderr());
        String out = interp.stdout().replace("\r\n", "\n");
        // receive bloqueia até send: pre-send ANTES de recv:42 é garantido;
        // recv:42 vs post-send é corrida (o thread acorda no send e imprime
        // concorrente com o main) — não se pode exigir ordem entre eles.
        for (String line : new String[]{"recv-wait", "pre-send", "post-send", "recv:42"}) {
            assertTrue(out.contains(line), "faltou '" + line + "' em: " + out);
        }
        assertTrue(out.indexOf("pre-send") < out.indexOf("recv:42"),
                "receive deve bloquear até o send: " + out);
    }

    /**
     * Varredura de paridade interpretado vs JVM compilado sobre uma bateria
     * de edge-cases determinísticos (sem tempo/concorrência/I/O externo).
     * Cada caso roda nos dois caminhos e exige stdout+exitCode idênticos —
     * é a prova de que o refactor ≤500 e o interpretador preservam a
     * semântica do bytecode em superfícies além dos 16 casos do gate.
     *
     * GRUPO B (complementar): casos onde o caminho COMPILADO tem bug
     * pré-existente registrado em docs/known-bugs.md (VerifyError do
     * emitter/lowering) — aqui o interpretador é o oráculo e o teste trava
     * a saída CORRETA dele, documentando o bug do compilado.
     */
    @Test
    void interpreterParitySweep(@TempDir Path tmp) throws Exception {
        String[][] cases = {
            {"int-overflow", "main() {\n var a = 2147483647\n println(a + 1)\n}"},
            {"mod-neg", "main() {\n println(-7 % 3)\n println(7 % -3)\n}"},
            {"long-div", "main() {\n var a = 10000000000L\n println(a / 3L)\n println(a % 7L)\n}"},
            {"cast-chain", "main() {\n var d = 9.9\n println(d as Int)\n var l = 70000L\n println(l as Int)\n println(66 as Char)\n}"},
            {"float-print", "main() {\n println(1.0 / 3.0)\n println(2.5 * 2.0)\n println(7.0 / 2.0)\n}"},
            {"unicode-str", "main() {\n var s = \"café\"\n println(s.length)\n println(s.charAt(3))\n println(s + \"!\")\n}"},
            {"str-ops", "main() {\n var s = \"a,b,,c\"\n println(s.split(\",\").length)\n println(\"Hello World\".toLowerCase())\n println(\"  x  \".trim() + \"|\")\n}"},
            {"map-null-val", "main() {\n var m = mapOf(\"a\", 1)\n m.put(\"b\", 2)\n println(m.get(\"a\"))\n println(m.size)\n}"},
            {"empty-list", "main() {\n var l = listOf()\n println(l.isEmpty())\n println(l.size)\n println(l.contains(1))\n}"},
            {"null-eq", "main() {\n var a = null\n var b = null\n println(a == b)\n println(a != b)\n}"},
            {"null-eq-shortcut", "main() {\n var a = null\n var b = null\n if (a == b) { println(\"iguais\") } else { println(\"dif\") }\n if (a != b) { println(\"ne\") } else { println(\"nao-ne\") }\n}"},
            {"set-dedup", "main() {\n var s = setOf(1, 2, 2, 3, 3, 3)\n println(s.size)\n println(s.contains(2))\n println(s.contains(9))\n}"},
            {"nested-if-expr", "main() {\n var x = 5\n var r = if (x > 0) if (x > 10) \"big\" else \"small\" else \"neg\"\n println(r)\n}"},
            {"switch-expr", "main() {\n var v = 3\n var d = switch (v) {\n case 1 -> \"one\"\n case 2 -> \"two\"\n case 3 -> \"three\"\n default -> \"other\"\n }\n println(d)\n}"},
            {"break-continue", "main() {\n var sum = 0\n for (var i in listOf(1,2,3,4,5)) {\n if (i == 2) { continue }\n if (i == 4) { break }\n sum = sum + i\n }\n println(sum)\n}"},
            {"record-eq-hash", "record P(Int x, Int y)\nmain() {\n var a = P(1,2)\n var b = P(1,2)\n println(a == b)\n println(a)\n println(a.x())\n println(a.hashCode() == b.hashCode())\n}"},
            {"finally-return", "Int f() {\n try {\n return 1\n } finally {\n println(\"fin\")\n }\n}\nmain() {\n println(f())\n}"},
            {"lambda-chain", "main() {\n var l = listOf(1,2,3,4)\n var r = l.filter((x: Int) -> x > 1).map((x: Int) -> x * 10).reduce((a: Int, b: Int) -> a + b, 0)\n println(r)\n}"},
            {"lambda-capture-mut", "main() {\n var n = 0\n var inc = () -> { n = n + 1 }\n inc()\n inc()\n inc()\n println(n)\n}"},
            {"array-2d", "main() {\n var a = new Int[3]\n a[0] = 10\n a[1] = 20\n a[2] = 30\n println(a[0] + a[1] + a[2])\n println(a.length)\n}"},
            {"static-field", "class Counter {\n static Int count = 0\n static Int bump() {\n count = count + 1\n return count\n }\n}\nmain() {\n println(Counter.bump())\n println(Counter.bump())\n println(Counter.count)\n}"},
            {"static-field-plus-eq", "class Counter2 {\n static Int count = 0\n static Int bump() {\n count += 2\n return count\n }\n}\nmain() {\n println(Counter2.bump())\n println(Counter2.bump())\n println(Counter2.count)\n}"},
            {"string-num-concat", "main() {\n println(\"n=\" + 42)\n println(1 + 2 + \"x\")\n println(\"x\" + 1 + 2)\n}"},
            {"bool-logic", "main() {\n println(true && false)\n println(true || false)\n println(!true)\n println((1 < 2) == (3 > 2))\n}"},
            {"bitwise", "main() {\n println(6 & 3)\n println(6 | 3)\n println(6 ^ 3)\n println(1 << 4)\n println(256 >> 2)\n}"},
            {"deep-recursion", "Int fact(Int n) {\n if (n <= 1) {\n return 1\n }\n return n * fact(n - 1)\n}\nmain() {\n println(fact(10))\n}"},
            {"list-of-mixed", "main() {\n var l = listOf(1, 2, 3)\n l.add(4)\n l.set(0, 99)\n println(l.get(0))\n println(l.size)\n println(l.remove(1))\n println(l.size)\n}"},
            {"map-iter", "main() {\n var m = mapOf(\"x\", 1)\n m.put(\"y\", 2)\n m.put(\"z\", 3)\n var ks = m.keys()\n var sum = 0\n for (var k in ks) {\n sum = sum + m.get(k)\n }\n println(sum)\n}"},
        };
        var divergentes = new StringBuilder();
        for (String[] c : cases) {
            Path dir = tmp.resolve(c[0]);
            Files.createDirectories(dir);
            Path f = dir.resolve("Main.kf");
            Files.writeString(f, c[1]);
            var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
            assertTrue(interp.success(), "[" + c[0] + "] interpretador falhou: " + interp.stderr());
            var comp = KofScript.runFileCompiled(f, dev.kof.compiler.Target.JVM, new String[0]);
            assertTrue(comp.success(), "[" + c[0] + "] compilado falhou: " + comp.stderr());
            if (interp.exitCode() != comp.exitCode()
                    || !norm(interp.stdout()).equals(norm(comp.stdout()))) {
                divergentes.append("\n[").append(c[0]).append("] interp(exit=")
                        .append(interp.exitCode()).append(")=<").append(norm(interp.stdout()))
                        .append("> comp(exit=").append(comp.exitCode()).append(")=<")
                        .append(norm(comp.stdout())).append(">");
            }
        }
        assertEquals("", divergentes.toString().trim(),
                "varredura de paridade interpretado vs JVM compilado:");
    }

    /**
     * Grupo B (estrutura pronta p/ regressões): casos onde o caminho
     * COMPILADO tem bug pré-existente (VerifyError do emitter/lowering,
     * docs/known-bugs.md) e o interpretador é o oráculo — trava a saída
     * correta enquanto o compilado espera correção. VAZIO desde 06/09:
     * empty-list (bug 35) e null-eq (bug 36) foram CORRIGIDOS no compilado
     * e promovidos ao grupo A (paridade total).
     */
    @Test
    void interpreterCorrectWhereCompiledCrashes(@TempDir Path tmp) throws Exception {
        String[][] cases = {
            // (vazio — ver comentário)
        };
        for (String[] c : cases) {
            Path dir = tmp.resolve(c[0]);
            Files.createDirectories(dir);
            Path f = dir.resolve("Main.kf");
            Files.writeString(f, c[1]);
            var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
            assertTrue(interp.success(), "[" + c[0] + "] " + interp.stderr());
            assertEquals(c[2], norm(interp.stdout()),
                    "[" + c[0] + "] interpretador deve dar a saída correta (compilado: bug registrado):");
        }
    }

    /**
     * Target.SCRIPT (fase 2 do plano de plataforma): runFile(f, SCRIPT) é o
     * nome EXPLÍCITO do modo de execução direta — mesma coisa que JVM no
     * KofScript (interpretador de IR, sem emitir artefato). Regressão: antes
     * caía no caminho compilado e dava COMP003.
     */
    @Test
    void scriptTargetRunsDirectly(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, """
                record Point(Int x, Int y)
                main() {
                    var o = Point(3, 4)
                    var r = switch (o) {
                        case Point(var x, var y) -> x + "," + y
                        default -> "other"
                    }
                    println(r)
                }
                """);
        var script = KofScript.runFile(f, dev.kof.compiler.Target.SCRIPT);
        assertTrue(script.success(), script.stderr());
        assertEquals("3,4", norm(script.stdout()));
        var jvm = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
        assertEquals(norm(jvm.stdout()), norm(script.stdout()),
                "SCRIPT e JVM rodam o mesmo interpretador");
    }

    /**
     * UI002 (R6): kof.ui no interpretador é no-op silencioso — mas avisa
     * UMA vez no stderr (warning, nunca erro: quebrar seria retrocompat).
     * kof.ui é KofJS; o apontamento é para o target com UI real.
     */
    @Test
    void ui002WarnsOnceOnUiCalls(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("Ui002.kf");
        Files.writeString(f, """
                main() {
                    var w = Window("App")
                    var l = Label("oi")
                    var c = Column(listOf(l))
                    w.bind(c)
                    w.show()
                    println("ok")
                }
                """);
        var script = KofScript.runFile(f, dev.kof.compiler.Target.SCRIPT);
        assertTrue(script.success(), script.stderr());
        assertEquals("ok", norm(script.stdout()));
        assertTrue(script.stderr().contains("UI002"),
                "warning UI002 ausente no stderr: " + script.stderr());
        assertTrue(script.stderr().contains("kof_ui_window_new"),
                "warning deve citar a primeira fn kof_ui_*: " + script.stderr());
        long count = script.stderr().lines().filter(s -> s.contains("UI002")).count();
        assertEquals(1, count, "UI002 deve avisar UMA vez, não por chamada: " + script.stderr());
    }

    /**
     * Regressão (gap "regex multiline-fragil" do roadmap-audit): wrapPureKof
     * qualificava globais com replaceAll(\b), que reescrevia o nome DENTRO de
     * string literal — `println("my name is here")` virava
     * `"my KofScriptGlobals.name is here"`. Agora o scanner respeita strings,
     * chars e comentários.
     */
    @Test
    void globalQualificationSkipsStringLiterals() throws Exception {
        var script = KofScript.eval("""
                var name = "mel"
                println(name)
                println("my name is here")
                """);
        assertTrue(script.success(), script.stderr());
        assertEquals("mel\nmy name is here", norm(script.stdout()));
    }

    @Test
    void qualifyGlobalsLeavesMembersAndComments() {
        String w = KofScript.qualifyGlobals(
                "total = total + p.total // total aqui nao\nprintln(\"total\")",
                java.util.List.of("total"));
        assertTrue(w.contains("KofScriptGlobals.total = KofScriptGlobals.total"),
                "uso real qualificado: " + w);
        assertTrue(w.contains("p.total"), "membro nao qualificado: " + w);
        assertTrue(w.contains("// total aqui nao"), "comentario intacto: " + w);
        assertTrue(w.contains("println(\"total\")"), "string intacta: " + w);
    }

    /**
     * Regressão (bug 47, 07/09): o cache do eval usava a chave
     * `target:hashCode():length()` — dois programas DIFERENTES com o mesmo
     * hash int + mesmo length colidiam e o 2º eval devolvia o resultado
     * CACHADO do 1º (R6 silencioso). A chave é agora SHA-256.
     */
    @Test
    void evalCacheKeyDoesNotCollide() throws Exception {
        // 1008+2009=3017 e 1560+1340=2900: MESMO hashCode() e MESMO length()
        String a = "main() {\n println(1008 + 2009)\n}";
        String b = "main() {\n println(1560 + 1340)\n}";
        assertEquals(a.length(), b.length(), "pré-condição: mesmo length");
        assertEquals(a.hashCode(), b.hashCode(), "pré-condição: mesmo hashCode");
        var ra = KofScript.eval(a);
        var rb = KofScript.eval(b);
        assertTrue(ra.success(), ra.stderr());
        assertTrue(rb.success(), rb.stderr());
        assertEquals("3017", norm(ra.stdout()), "programa A");
        assertEquals("2900", norm(rb.stdout()),
                "programa B não pode herdar o cache do programa A (bug 47)");
    }

    /**
     * Regressão (07/09): json.decode<Record> no interpretador (target Script)
     * dava exit 1 com stderr só "Point" (R6 silencioso) — o método gerado
     * kof_json_decode_Point faz Class.forName("Point"), mas no interpretador
     * a classe Kof é KofObj (nunca vira classe JVM). Corrigido em
     * KofInterpreterRuntime.decodeKofValue (espelha encodeKof). Paridade com
     * JVM/Native/JS que já funcionavam.
     */
    @Test
    void jsonDecodeRecordRunsOnInterpreter(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, """
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
                """);
        var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
        assertTrue(interp.success(), interp.stderr());
        assertEquals("10\n20\n3\n4", norm(interp.stdout()),
                "interpretador deve dar a saída correta (decode<Record>): " + interp.stderr());
        var comp = KofScript.runFileCompiled(f, dev.kof.compiler.Target.JVM, new String[0]);
        assertEquals(norm(interp.stdout()), norm(comp.stdout()),
                "paridade interpretado vs compilado em json.decode<Record>");
    }

    private static String norm(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }

    /**
     * Regressão (07/09): race no interpretador — `KofInterpreter.lastReturned`
     * era um ÚNICO campo de instância sobrescrito por cada `KofReturn`; com 2
     * `spawn` concorrentes (virtual threads) o `await` do handle 2 podia ler o
     * retorno do handle 1 (reproduzido 3/120: `11|11`/`2|2` em vez de
     * `2|11`). Correção: o retorno vive na `Frame` (per-invocação/per-thread)
     * — `KofInterpreterFrame.Frame.returnValue`. Com 25 tasks concorrentes,
     * antes do fix a probabilidade de colisão por run era alta; depois do fix
     * cada await devolve SEMPRE o valor da sua task (determinístico).
     */
    @Test
    void concurrentAwaitReturnsOwnTaskResult(@TempDir Path tmp) throws Exception {
        for (int i = 0; i < 8; i++) {
            Path dir = Files.createDirectories(tmp.resolve("run" + i));
            StringBuilder tasks = new StringBuilder();
            StringBuilder awaits = new StringBuilder();
            for (int t = 0; t < 25; t++) {
                tasks.append("    var h").append(t).append(" = spawn calc(").append(t).append(")\n");
                awaits.append("    if (await h").append(t).append(" != ").append(t * 100 + 7)
                        .append(") { throw \"race: h").append(t).append(" != ").append(t * 100 + 7)
                        .append("\" }\n");
            }
            Path f = dir.resolve("Main.kf");
            Files.writeString(f, """
                    Int calc(Int x) {
                        return x * 100 + 7
                    }
                    main() {
                    %s
                    %s
                        println("ok")
                    }
                    """.formatted(tasks, awaits));
            var r = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
            assertTrue(r.success(), "run " + i + " falhou (await devolveu valor da task errada?): " + r.stderr());
            assertEquals("ok", r.stdout().trim(), "run " + i + ": " + r.stdout());
        }
    }

    /**
     * Regressão (07/09): json.decode&lt;List&lt;Record&gt;&gt; no interpretador
     * dava exit 1 + stderr só "P" (R6) — kof_json_decode_object_list faz
     * Class.forName + kof_json_bind, mas no interpretador a classe Kof é
     * KofObj. Corrigido em KofInterpreterRuntime (mapeia cada item para
     * KofObj). bug 48 (a metade Native — não compila — segue ABERTA, outra
     * lane). Paridade com JVM/JS que já funcionavam.
     */
    @Test
    void jsonDecodeListOfRecordRunsOnInterpreter(@TempDir Path tmp) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("main"));
        Path f = dir.resolve("Main.kf");
        Files.writeString(f, """
                record P(Int x, Int y)
                main() {
                    var l = json.decode<List<P>>("[{\\"x\\":1,\\"y\\":10},{\\"x\\":2,\\"y\\":20}]")
                    println(l.size())
                    println(l.get(1).x() + "/" + l.get(1).y())
                }
                """);
        var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
        assertTrue(interp.success(), interp.stderr());
        assertEquals("2\n2/20", norm(interp.stdout()),
                "interpretador deve dar a saída correta (decode<List<Record>>): " + interp.stderr());
    }
}
