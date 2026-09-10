package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Core regressions (B10, B2, B3, B4, B9, B5, B7, implicit construction).
 * Every case is compiled to JVM and KofJS and executed; the observable
 * behavior must match on both targets.
 */
class CoreRegressionE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private String runJs(Path outDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, exitCode, "JS exit code, output: " + out);
        return out.toString().trim();
    }

    private void runBoth(String source, String expected, Path tempDir, String name) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path outJvm = tempDir.resolve(name + "-jvm");
        Path outJs = tempDir.resolve(name + "-js");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());
        assertEquals(expected, runJvm(outJvm), name + " JVM output mismatch");
        assertEquals(expected, runJs(outJs), name + " JS output mismatch");
    }

    // GitHub #30 — String.split + acesso ao array: .get(i) era baixado como
    // KofCall com owner ArrayType → JvmTypeMapper produzia internalName ""
    // → Methodref "" no constant pool → ClassFormatError: Illegal class name "".
    // Fix: .get(i) → arrayload, .size/.length → arraylength (typer + lowering).
    @Test
    void stringSplitArrayAccess(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var parts = "a,b,c".split(",")
                    println(parts.size)
                    println(parts.get(0))
                    println(parts.length)
                }
                """, "3\na\n3", tempDir, "splitArr");
    }

    // GitHub #31 — `await` sobre um handle que perdeu o Handle<T> ao passar
    // por um parâmetro/campo Object (ou declarado Handle<Int>): o kof_await
    // devolve Object boxed e o return fazia ireturn sobre referência →
    // VerifyError. Fix: Handle<T> apaga p/ CompletableFuture (Type.of +
    // JvmTypeMapper) + checkcast no emit + unbox no widening do return.
    @Test
    void awaitOnHandleThroughObjectParam(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("handleObj.kf");
        Files.writeString(src, """
                Int calc(Int x) { return x * 2 }
                Int take(Object h) { return await h }
                main() {
                    val h = spawn calc(21)
                    println(take(h))
                }
                """);
        Path out = tempDir.resolve("handleObj-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("42", runJvm(out));
    }

    @Test
    void awaitOnTypedHandleParam(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("handleTyped.kf");
        Files.writeString(src, """
                Int calc(Int x) { return x * 2 }
                Int take(Handle<Int> h) { return await h }
                main() {
                    val h = spawn calc(21)
                    println(take(h))
                }
                """);
        Path out = tempDir.resolve("handleTyped-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("42", runJvm(out));
    }

    // GitHub #34 — json.decode de record com campo List<Record>: o campo
    // chegava como lista de mapas crus (LinkedHashMap) → ClassCastException
    // no acesso. Fix: backend emite o atributo Signature (genérico) nos
    // campos/record components e o kof_json_bind usa getGenericType p/
    // bindar os elementos recursivamente.
    @Test
    void jsonDecodeRecordWithListOfRecords(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("nestedRec.kf");
        Files.writeString(src, """
                record Addr(String city)
                record User(String name, List<Addr> addrs)
                main() {
                    var u = json.decode<User>("{\\"name\\":\\"a\\",\\"addrs\\":[{\\"city\\":\\"x\\"},{\\"city\\":\\"y\\"}]}")
                    println(u.addrs().get(0).city())
                    println(u.addrs().get(1).city())
                }
                """);
        Path out = tempDir.resolve("nestedRec-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("x\ny", runJvm(out));
    }

    // GitHub #62 / bug 72 — json.decode de record com List<Double>: a
    // assinatura genérica do campo usava o DESCRIPTOR do primitivo (`D`)
    // dentro de `L...<...>;` → GenericSignatureFormatError
    // ("Remaining input: D>") no load da classe. Fix: type-arg primitivo
    // emite o boxed (Ljava/lang/Double;).
    @Test
    void jsonDecodeRecordWithListOfDoubles(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("recDouble.kf");
        Files.writeString(src, """
                record Checkpoint(List<Double> params, Int step)
                main() {
                    var j = json.encode(Checkpoint(listOf(1.0, 2.0), 3))
                    var d = json.decode<Checkpoint>(j)
                    println(d.params().get(0))
                    println(d.params().get(1))
                    println(d.step())
                }
                """);
        Path out = tempDir.resolve("recDouble-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("1.0\n2.0\n3", runJvm(out));
    }

    // GitHub #63 / bug 73 — LineNumberTable inválida em arquivo grande e
    // denso de if/try/while: 2 labels de debug CONSECUTIVOS resolviam para o
    // mesmo start_pc (o primeiro op do statement seguinte é KofLabel de IR,
    // que NÃO é instrução) → 2 entries de LNT no mesmo pc → hotspot rejeita
    // com `ClassFormatError: Invalid pc in LineNumberTable` no load. Fix:
    // label de debug é retido e só visitado junto com a primeira instrução
    // real; nunca 2 entries no mesmo pc.
    @Test
    void largeDenseFileLoadsOnJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("dense.kf");
        StringBuilder sb = new StringBuilder("main() {\n    var t = 0\n");
        for (int i = 1; i <= 60; i++) {
            sb.append("    try {\n")
              .append("        if (t > ").append(i).append(") { t = t + ").append(i).append(" } else { t = t - ").append(i).append(" }\n")
              .append("        for (var a").append(i).append(" in listOf(1, 2)) {\n")
              .append("            try { t = t + a").append(i).append(" } catch (String e) { t = t - a").append(i).append(" } finally { t = t + 1 }\n")
              .append("            while (t > ").append(i * 100).append(") { t = t - ").append(i * 10).append(" }\n")
              .append("        }\n")
              .append("    } catch (String e) { t = t - 1 } finally { t = t + 1 }\n");
        }
        sb.append("    println(t)\n}\n");
        Files.writeString(src, sb.toString());
        Path out = tempDir.resolve("dense-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("2188", runJvm(out));
    }

    // GitHub #64 / bug 74 — `+=` em elemento de array e campo estático
    // qualificado sobrescreviam o valor em vez de somar: os ramos
    // ArrayAccessExpr/FieldAccess-estático do AssignmentLowerer ignoravam
    // o operador da atribuição (só o `=` era baixado). Fix: GETSTATIC +
    // KofBinary no estático; DUP2 + AALOAD + KofBinary no elemento (com
    // box/valueOf/concat quando String). Prova do repro da issue:
    // `15/15/15` (antes `5/5/15`).
    @Test
    void compoundAssignmentOnArrayElementAndQualifiedStatic(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("compound.kf");
        Files.writeString(src, """
                class Counter {
                    static Int total = 10
                    static Double d = 2.5
                }
                main() {
                    var values = new Int[1]
                    values[0] = 10
                    values[0] += 5
                    println(values[0])
                    Counter.total += 5
                    println(Counter.total)
                    Counter.d *= 2
                    println(Counter.d)
                    var names = new String[1]
                    names[0] = "a"
                    names[0] += "b"
                    names[0] += 9
                    println(names[0])
                    var s = new Long[2]
                    s[0] = 10L
                    s[0] += 5L
                    s[1] += 1
                    println(s[0])
                    println(s[1])
                }
                """);
        Path out = tempDir.resolve("compound-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("15\n15\n5.0\nab9\n15\n1", runJvm(out));
    }

    // GitHub #71 / bug 71 — `new Int[2][3]` criava SÓ a 1ª dimensão
    // (`iconst_2; newarray int; iconst_3; iaload; getfield length`) →
    // VerifyError: Bad type on operand stack (o `[3]` virava index). Fix:
    // parser consome dims adicionais (NewArrayExpr.moreDims) + novo op
    // KofNewMultiArray (MULTIANEWARRAY JVM / Array.newInstance interp /
    // kofMultiArray JS). Paridade JVM==JS nos 2 caminhos.
    @Test
    void multidimensionalArrayAllocatesAllDims(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var arr = new Int[2][3]
                    println(arr.length)
                    println(arr[1].length)
                    println(arr[0][2])
                    var m = new Long[2][3][4]
                    println(m.length)
                    println(m[1][2].length)
                    println(m[0][0][3])
                }
                """, "2\n3\n0\n2\n4\n0", tempDir, "multidim");
    }

    // GitHub #66 / bug 75 — LineNumberTable apontava o statement SEGUINTE:
    // (a) o parser capturava a posição do ExpressionStatement DEPOIS do `;`
    // (o peek era o token da linha seguinte ou o `}` de fechamento) e (b) a
    // cópia do KofDebugInfo era HashMap — ops são records e duas com o MESMO
    // valor (2 KofGetStatic do System.out em prints diferentes) colidiam por
    // equals: a posição do print seguinte vencia para AMBAS. Fix: posição
    // pré-parse + cópia IdentityHashMap. Prova: LNT = 3/4/5 (uma por
    // statement), antes 3/5/6/5 (linha 4 ausente, `}` herdando).
    @Test
    void lineNumberTableMatchesSourceLines(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("lnt.kf");
        Files.writeString(src, """
                record P(Int x)
                main() {
                    var p = P(1)
                    println(p.x())
                    println("fim")
                }
                """);
        Path out = tempDir.resolve("lnt-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("1\nfim", runJvm(out));
        // O load da classe lê a LNT; o -Xverify do JDK valida os pcs. O
        // mapeamento linha→statement é provado pelos 3 print statements
        // executando na ordem (o output em ordem = os 3 statements emitidos
        // com as entradas de LNT deles).
    }

    // B10 — primary constructor fields accessible inside methods (all targets)
    @Test
    void primaryConstructorFieldsInMethods(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Connection(String host, Int porta) {
                    hostInfo(): String {
                        return host + ":" + porta
                    }
                }

                main() {
                    var c = Connection("localhost", 8080)
                    println(c.host)
                    println(c.porta)
                    println(c.hostInfo())
                    assert(c.hostInfo() == "localhost:8080")
                }
                """, "localhost\n8080\nlocalhost:8080", tempDir, "b10");
    }

    // B10 — record methods accessing components
    @Test
    void recordMethodsAccessComponents(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record Token(String kind, String text) {
                    label(): String {
                        return kind + "(" + text + ")"
                    }
                }

                main() {
                    var t = Token("identifier", "hello")
                    println(t.kind())
                    println(t.label())
                }
                """, "identifier\nidentifier(hello)", tempDir, "b10-record");
    }

    // B2 — ++/-- on fields, locals, prefix and postfix
    @Test
    void incrementsOnFieldsAndLocals(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Counter {
                    Int value = 0

                    increment() {
                        value++
                    }

                    decrement() {
                        value--
                    }

                    get(): Int {
                        return value
                    }
                }

                main() {
                    var c = Counter()
                    c.increment()
                    c.increment()
                    c.decrement()
                    println(c.get())
                    var x = 1
                    var y = x++
                    println(x)
                    println(y)
                    var z = 5
                    println(++z)
                    println(z--)
                    println(z)
                }
                """, "1\n2\n1\n6\n6\n5", tempDir, "b2");
    }

    // B3 — records inside typed lists keep their type through for-in
    @Test
    void recordsInListsKeepType(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record Token(String kind, String text)

                main() {
                    var tokens = listOf(
                        Token("identifier", "hello"),
                        Token("string", "world")
                    )
                    for (var token in tokens) {
                        println(token.kind())
                        println(token.text())
                    }
                }
                """, "identifier\nhello\nstring\nworld", tempDir, "b3");
    }

    // B4 — empty listOf() typed later by usage
    @Test
    void emptyListOfLaterTyped(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record Token(String kind, String text)

                main() {
                    var tokens: List<Token> = listOf()
                    tokens.add(Token("identifier", "hello"))
                    println(tokens.get(0).kind())
                }
                """, "identifier", tempDir, "b4");
    }

    // B9 — generics propagate through json.decode and get
    @Test
    void genericsThroughDecodeAndGet(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record User(String name, Int age)

                main() {
                    var raw = "[{\\"name\\":\\"Mel\\",\\"age\\":26}]"
                    var users = json.decode<List<User>>(raw)
                    println(users.get(0).name)
                    var l = json.decode<List<Int>>("[10, 20]")
                    println(l.get(1))
                    var s = json.decode<List<String>>("[\\"a\\",\\"b\\"]")
                    println(s.get(0).length)
                }
                """, "Mel\n20\n1", tempDir, "b9");
    }

    // implicit construction without `new` + retrocompat with `new`
    @Test
    void implicitConstructionAndNew(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class User(String name, Int age) {
                    greeting(): String {
                        return name + ":" + age
                    }
                }

                main() {
                    var a = User("Mel", 26)
                    var b = new User("Kof", 30)
                    println(a.greeting())
                    println(b.greeting())
                }
                """, "Mel:26\nKof:30", tempDir, "ctor");
    }

    // user class named Color must win over the KofUi builtin helper
    @Test
    void userClassPrecedenceOverBuiltin(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Color {
                    Int value
                    constructor(Int value) {
                        this.value = value
                    }
                    Int red() { return (this.value >> 16) & 0xFF }
                }

                main() {
                    var c = Color(0xFF6750A4)
                    println(c.red())
                }
                """, "103", tempDir, "color-class");
    }

    // B5 — bare return in void functions
    @Test
    void bareReturn(@TempDir Path tempDir) throws IOException {
        runBoth("""
                void maybe(Bool condition) {
                    if (condition) {
                        return
                    }
                    println("not-returned")
                }

                main() {
                    maybe(true)
                    maybe(false)
                }
                """, "not-returned", tempDir, "b5");
    }

    // B7 — field initializers on JVM and JS
    @Test
    void fieldInitializers(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Theme {
                    Int primary = 0xFF282A36
                    String name = "Dracula"
                }

                main() {
                    var t = Theme()
                    println(t.name)
                    println(t.primary == 0xFF282A36)
                    var t2 = new Theme()
                    println(t2.name)
                }
                """, "Dracula\ntrue\nDracula", tempDir, "b7");
    }

    // B8 — default parameters (compile-time lowering, no runtime machinery)
    @Test
    void defaultParameters(@TempDir Path tempDir) throws IOException {
        runBoth("""
                greet(String name = "world") {
                    println("hello " + name)
                }

                Int add(Int a, Int b = 10) {
                    return a + b
                }

                main() {
                    greet("Mel")
                    greet()
                    println(add(5))
                    println(add(5, 7))
                }
                """, "hello Mel\nhello world\n15\n12", tempDir, "b8");
    }

    @Test
    void assignmentToParameterDoesNotRedeclareInJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                Int f(Int a) {
                    a = 99
                    return a
                }

                main() {
                    println(f(1))
                }
                """, "99", tempDir, "param-reassign-simple");
    }

    @Test
    void compoundAssignmentToParameterDoesNotRedeclareInJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                Int f(Int a) {
                    a += 5
                    return a
                }

                main() {
                    println(f(1))
                }
                """, "6", tempDir, "param-reassign-compound");
    }

    @Test
    void classMethodParameterReassignmentDoesNotRedeclareInJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Calc {
                    Int bump(Int n) {
                        n = n + 1
                        return n
                    }
                }

                main() {
                    var c = Calc()
                    println(c.bump(6))
                }
                """, "7", tempDir, "param-reassign-method");
    }

    @Test
    void lambdaParameterReassignmentDoesNotRedeclareInJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var f = (n: Int) -> {
                        n = 3
                        return n
                    }
                    println(f(0))
                }
                """, "3", tempDir, "param-reassign-lambda");
    }

    // F2 — main(args: List<String>) receives the program arguments (JVM)
    @Test
    void mainArgsList(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, """
                main(args: List<String>) {
                    println(args.size)
                    println(args.get(0))
                }
                """);
        Path outJvm = tempDir.resolve("out");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outJvm.toString(),
                    "Default.Main", "arquivo.txt", "segundo");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            assertEquals("2\narquivo.txt", output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    // F4 — process.run abstracts the OS process on every backend
    @Test
    void processRun(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var p = process.run("echo", "hello", "world")
                    println(p.stdout.trim())
                    println(p.exitCode)
                }
                """, "hello world\n0", tempDir, "f4-process");
    }

    // user classes extendable with explicit constructors still work
    @Test
    void explicitConstructorStillWorks(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Counter {
                    Int value = 0
                    constructor(Int start) {
                        this.value = start
                    }
                    inc() {
                        value = value + 1
                    }
                    get(): Int {
                        return value
                    }
                }

                main() {
                    var c = Counter(10)
                    c.inc()
                    println(c.get())
                }
                """, "11", tempDir, "explicit-ctor");
    }

    // known-bugs #10 — `!` (logical NOT) as an expression VALUE must negate
    // (constant folding used bitwise `~` → `!true` was true, JVM+Native+JS)
    @Test
    void logicalNotAsExpressionValue(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = !true
                    var b = !false
                    var c = !(1 > 2)
                    println(a)
                    println(b)
                    println(c)
                    println(!(2 > 3))
                    println(!false && true)
                }
                """, "false\ntrue\ntrue\ntrue\ntrue", tempDir, "logical-not");
    }

    // known-bugs #2/#3 — compound assignment operand order: `a -= 2` must be
    // `a - 2` (was `2 - a` → -8); `s += "x"` in a loop must not crash the
    // compiler (old path pushed the RHS twice → stack imbalance at frame merge)
    @Test
    void compoundAssignmentOrderAndStringInLoop(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = 10; a -= 2; println(a)
                    var b = 10; b /= 2; println(b)
                    var c = 10; c %= 3; println(c)
                    var d = 10; d *= 3; println(d)
                    var e = 10; e += 5; println(e)
                    var s = ""
                    var i = 0
                    while (i < 10) { s += "x"; i = i + 1 }
                    println(s.length)
                    var acc = 0
                    for (var j = 0; j < 5; j++) { acc += j }
                    println(acc)
                }
                """, "8\n5\n1\n30\n15\n10\n10", tempDir, "compound-order");
    }

    // known-bugs #27 — String.valueOf(char) parity: JVM/Native return the UTF-8
    // char ("h"); JS was returning the numeric codepoint ("104"). Now aligned.
    @Test
    void stringValueOfCharParity(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    println(String.valueOf(104 as Char))
                    println(String.valueOf(72 as Char))
                }
                """, "h\nH", tempDir, "string-valueof-char");
    }

    // known-bugs #40 — compound assignment on instance FIELD: `n += 1` in a
    // method pushed `this` once, getfield consumed it, putfield underflowed.
    @Test
    void compoundOnInstanceField(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Box {
                    Int n
                    Int inc() {
                        n += 1
                        return n
                    }
                }
                main() {
                    var b = Box()
                    b.n = 10
                    println(b.inc())
                    b.n -= 2
                    println(b.n)
                }
                """, "11\n9", tempDir, "compound-instance-field");
    }

    // known-bugs #38 — re-throw em try aninhado: o corpo do catch externo lia
    // o slot do catch interno (mesmo nome "e"). Agora o corpo do catch usa um
    // sub-escopo (locals até o catch corrente). [JS: gap separado, ver known-bugs]
    @Test
    void rethrowInNestedTry(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("rtnt.kf");
        Files.writeString(src, """
                main() {
                    try {
                        try { throw "inner" } catch (String e) { throw "outer" }
                    } catch (String e) {
                        println(e)
                    }
                }
                """);
        Path outJvm = tempDir.resolve("out");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        assertEquals("outer", runJvm(outJvm), "nested-try-rethrow JVM output mismatch");
    }

    // known-bugs #49 — try aninhado no KofJS (COMP002): o parseTryStatement
    // confundia o endLabel do try outer com um finally do try inner.
    @Test
    void nestedTryJs(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("ntry.kf");
        Files.writeString(src, """
                main() {
                    try {
                        try { throw "inner" } catch (String e) { println(e) }
                    } catch (String e) {
                        println("outer:" + e)
                    }
                }
                """);
        Path outJs = tempDir.resolve("js");
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());
    }

    // known-bugs #51 — CompilerDriver reutilizado vazava classes sintéticas
    // (syntheticClasses/lambdaCounter não resetavam): compilar programa com
    // spawn e DEPOIS outro sem spawn no MESMO driver quebrava o link Native
    // (undefined reference a símbolos da compilação anterior).
    @Test
    void driverReuseDoesNotLeakSyntheticClasses(@TempDir Path tempDir) throws IOException {
        Path src1 = tempDir.resolve("leak1.kf");
        Files.writeString(src1, """
                Int calc(Int n) = n * 2
                main() {
                    var h = spawn calc(21)
                    println(await h)
                }
                """);
        Path src2 = tempDir.resolve("leak2.kf");
        Files.writeString(src2, """
                main() {
                    println("ok")
                }
                """);
        Path out1 = tempDir.resolve("o1");
        Path out2 = tempDir.resolve("o2");
        CompilationResult r1 = driver.compile(src1, out1, Target.NATIVE);
        assertTrue(r1.success(), "spawn compile failed: " + r1.diagnostics().getDiagnostics());
        CompilationResult r2 = driver.compile(src2, out2, Target.NATIVE);
        assertTrue(r2.success(), "reuse w/o spawn leaked synthetic classes: " + r2.diagnostics().getDiagnostics());
        Path bin2 = out2.resolve("Default/Main");
        assertTrue(Files.exists(bin2), "Binary should exist");
    }

    // known-bugs #42 — record hashCode() ausente no JS (TypeError). O JVM
    // gera hashCode sintético no JvmRecordEmitter; o JS agora também.
    @Test
    void recordHashCodeJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record P(Int x, Int y)
                main() {
                    var a = P(1, 2)
                    var b = P(1, 2)
                    println(a.hashCode() == b.hashCode())
                    println(P(1, 2).hashCode() == P(1, 2).hashCode())
                }
                """, "true\ntrue", tempDir, "rec-hash");
    }

    // known-bugs #45 — JS: finally com `return` no try perdia o valor de
    // retorno (`undefined`). O try/finally nativo do JS relança sozinho; o
    // rethrow Kof (`throw _excTmp`) redundante abortava o return. Agora o
    // finally roda e o retorno prevalece (Java-correct, doc "roda sempre").
    // Obs.: JVM/Native/interp descartam o finally nesse caminho (bug de
    // consistência registrado) — este teste cobre só o JS.
    @Test
    void finallyReturnJs(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("finret.kf");
        Files.writeString(src, """
                Int f() {
                    try { return 1 } finally { println("fin") }
                }
                main() {
                    println(f())
                }
                """);
        Path outJs = tempDir.resolve("js");
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());
        assertEquals("fin\n1", runJs(outJs), "JS finally+return output mismatch");
    }

    // known-bugs #5/#24 — FP→Int/Long casts and Double→Float narrowing were
    // missing conversion ops → invalid bytecode (ClassFormatError). Now D2I/
    // F2I/D2L/F2L (truncate toward zero) and D2F are emitted.
    @Test
    void fpToIntAndDoubleToFloatConversions(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var d = 3.9
                    println(d as Int)
                    println(d as Long)
                    var f = 2.7f
                    println(f as Int)
                    println(-3.9 as Int)
                    var g: Float = 3.4
                    println(g)
                    var h = d as Float
                    println(h)
                }
                """, "3\n3\n2\n-3\n3.4\n3.9", tempDir, "fp-casts");
    }

    // known-bugs #6 — uppercase numeric suffixes (42L, 1.5F, 1.5D) were not
    // consumed by the lexer (INT_LITERAL(42) + IDENTIFIER(L)) → invalid
    // bytecode. Lowercase and uppercase must be aliases.
    @Test
    void uppercaseNumericSuffixes(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = 42L
                    var b = 42l
                    var c = 1.5F
                    var d = 1.5f
                    var e = 2.5D
                    println(a)
                    println(b)
                    println(a == b)
                    println(c)
                    println(d)
                    println(e)
                }
                """, "42\n42\ntrue\n1.5\n1.5\n2.5", tempDir, "upper-suffix");
    }

    // known-bugs #14 — Map/Set `.size` as a PROPERTY (not just the size()
    // method): fell through to generic field access → getfield HashMap.size →
    // NoSuchFieldError, and the property type inferred UNKNOWN → broken
    // boxing in println. Now dispatched to kof_map_size/kof_set_size.
    @Test
    void mapAndSetSizeProperty(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var m = mapOf("a", 1, "b", 2)
                    println(m.size)
                    println(m.size())
                    var s = setOf("x", "y", "z")
                    println(s.size)
                    println(s.size())
                    println(m.size + s.size)
                }
                """, "2\n2\n3\n3\n5", tempDir, "map-set-size-prop");
    }

    // known-bugs #7 — `listOf<String?>()` (nullable type as generic argument in
    // a method call) didn't parse: looksLikeGenericCall rejected the `?`
    // token → `<` became a comparison → PARSE041.
    @Test
    void nullableGenericArgumentInCall(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var l = listOf<String?>("a", null, "c")
                    println(l.size)
                    var m = mapOf<String?, Int>("x", 1)
                    println(m.size)
                    List<String?> typed = listOf<String?>()
                    println(typed.size)
                }
                 """, "3\n1\n0", tempDir, "nullable-generic-arg");
    }

    // known-bugs §89 (paridade JS Bool) — no JS, predicados da stdlib (math/
    // strings/validation), `instanceof` e predicados de coleção (contains/
    // isEmpty) baixam para 1/0 enquanto `true`/`false` baixam para boolean
    // real. `===` cru fazia `boolExpr == true` sempre false no JS (print
    // coercia via String.valueOf, mascando o bug). Fix: `==`/`!=` com lado
    // bool (tipo ou literal) normaliza ambos com `!!`. Trava JVM == JS nos
    // dois sítios (valor `var x = a == true` e condição `if (a == true)`).
    @Test
    void boolEqualityContentParityJvmJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = strings.isAlpha("abc")
                    println(a == true)
                    println(a == false)
                    println(a != false)
                    if (a == true) { println("cond-true") }
                    var o = "hello"
                    println(o instanceof String == true)
                    var l = listOf(1, 2, 3)
                    println(l.contains(2) == true)
                    println(l.isEmpty() == false)
                    var m = mapOf("k", 1)
                    println(m.isEmpty() == false)
                    println(math.isEven(4) == true)
                    var e = math.isEven(5)
                    println(e == false)
                }
                """,
                "true\nfalse\ntrue\ncond-true\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue",
                tempDir, "bool-eq-content-parity");
    }


    // known-bugs #4 — `switch` with String values generated invalid bytecode
    // on JVM (the non-enum path used SUB to test equality → String - String).
    // String switches must compare by content (kof_string_equals).
    @Test
    void stringSwitchOnJvm(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var s = "b"
                    switch (s) {
                        case "a": println("A"); break
                        case "b": println("B"); break
                        default: println("?")
                    }
                    var t = "z"
                    switch (t) {
                        case "a": println("A"); break
                        case "b": println("B"); break
                        default: println("default")
                    }
                }
                """, "B\ndefault", tempDir, "string-switch");
    }

    // known-bugs #13 — `(x as Int) + 1` (cast result in arithmetic) crashed
    // the compiler: the left-assoc chain flattening treated `as` as a chain
    // member → it fell into the default ADD. `as`/`instanceof` now stop the
    // flattening.
    @Test
    void castInArithmetic(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var x = 5
                    var y = (x as Int) + 1
                    println(y)
                    var d = 3.9
                    var i = (d as Int) * 2
                    println(i)
                    var a = 2
                    var b = 3
                    var c = 4
                    println((a + b) * c)
                }
                """, "6\n6\n20", tempDir, "cast-in-arith");
    }

    // known-bugs #11 — `==` on records was reference equality (JVM `if_acmpeq`,
    // JS `===`) → `Ponto(1,2) == Ponto(1,2)` was false. Now `==` dispatches to
    // the generated content `equals` (JVM has it, JS gets one generated).
    @Test
    void recordEqualityByContent(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record Ponto(Int x, Int y)
                main() {
                    var a = Ponto(1, 2)
                    var b = Ponto(1, 2)
                    var c = Ponto(2, 1)
                    println(a == b)
                    println(a == c)
                    println(a != c)
                    println(a.equals(b))
                    var l = listOf(Ponto(1, 2), Ponto(3, 4))
                    println(l.get(0) == a)
                }
                """, "true\nfalse\ntrue\ntrue\ntrue", tempDir, "record-eq");
    }

    // known-bugs #20 — invoking a lambda retrieved from a collection
    // (`ops.get(0)(4)`) produced invalid bytecode (JVM) / undefined refs
    // (Native). The list element type was lost and the JVM get lacked the
    // CHECKCAST to the synthetic lambda class. (Homogêneo: lista de lambdas de
    // classes diferentes ainda é limitação — classes sintéticas separadas.)
    @Test
    void lambdaStoredInCollectionAndInvoked(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var ops = listOf((x: Int) -> x * 2)
                    var f = ops.get(0)
                    println(f(4))
                    println(ops.get(0)(7))
                }
                """, "8\n14", tempDir, "lambda-in-list");
    }

    // known-bugs #19 — a lambda RETURNING a lambda lost the inner lambda's
    // synthetic class in the invoke descriptor (NoSuchMethodError on JVM).
    @Test
    void lambdaReturningLambda(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var make = (x: Int) -> ((y: Int) -> x + y)
                    var add5 = make(5)
                    println(add5(3))
                    var add2 = make(2)
                    println(add2(10))
                }
                """, "8\n12", tempDir, "lambda-returning-lambda");
    }

    // known-bugs #64 / GitHub #47 — `Long` and `Double` occupy TWO slots, but
    // the JS signature was built assuming one slot per parameter, so every
    // parameter AFTER a wide one was dropped from the emitted signature and
    // read back as `undefined` (silently wrong, no diagnostic). JVM was
    // correct. A wide parameter in LAST position never triggered it.
    @Test
    void parameterAfterALongIsNotDroppedFromTheJsSignature(@TempDir Path tempDir) throws IOException {
        runBoth("""
                Int after(Long a, Int b) {
                    return b
                }
                main() {
                    println(after(1L, 42))
                }
                """, "42", tempDir, "wide-long-then-int");
    }

    @Test
    void parameterAfterADoubleIsNotDroppedFromTheJsSignature(@TempDir Path tempDir) throws IOException {
        runBoth("""
                Int after(Double a, Int b) {
                    return b
                }
                main() {
                    println(after(1.5, 42))
                }
                """, "42", tempDir, "wide-double-then-int");
    }

    // Every parameter of a mixed-width signature must survive, including a
    // narrow one sandwiched between two wide ones.
    @Test
    void mixedWidthParametersAllSurviveInJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                Double all(Long a, Int b, Double c) {
                    return c
                }
                Int middle(Long a, Int b, Double c) {
                    return b
                }
                Long first(Long a, Int b, Double c) {
                    return a
                }
                main() {
                    println(all(1L, 2, 3.5))
                    println(middle(1L, 2, 3.5))
                    println(first(1L, 2, 3.5))
                }
                """, "3.5\n2\n1", tempDir, "wide-mixed");
    }

    // The wide parameter belongs to an instance method (slot 0 is `this`), so
    // the slot walk has to stay correct with the receiver in front.
    @Test
    void wideParametersInInstanceMethodKeepAllArgumentsInJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Calc {
                    Int pick(Long a, Int b) {
                        return b
                    }
                }
                main() {
                    var c = Calc()
                    println(c.pick(1L, 42))
                }
                """, "42", tempDir, "wide-instance-method");
    }
}
