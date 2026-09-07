package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backend parity tests: the same Kof source compiled to JVM and KofJS must
 * produce the same observable behavior (stdout, exit code).
 *
 * Native is added when the toolchain is available and the compiled binary
 * runs (see NativeDebugTest conventions).
 */
class BackendParityTest {

    private final CompilerDriver driver = new CompilerDriver();

    private record RunResult(int exitCode, String output) {
    }

    private RunResult runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            return new RunResult(ec, output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private RunResult runJs(Path outDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        return new RunResult(exitCode, out.toString().trim());
    }

    private void assertParity(Path source, Path outJvm, Path outJs, String expected) throws IOException {
        CompilationResult rjvm = driver.compile(source, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(source, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());

        RunResult jvm = runJvm(outJvm);
        RunResult js = runJs(outJs);

        assertEquals(0, jvm.exitCode(), "JVM exit code, output: " + jvm.output());
        assertEquals(0, js.exitCode(), "JS exit code, output: " + js.output());
        assertEquals(jvm.output(), js.output(), "stdout parity JVM vs JS");
        assertEquals(expected, jvm.output(), "expected output");
    }

    private void runParity(String program, String expected, Path tempDir, String name) throws IOException {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        assertParity(source, tempDir.resolve(name + "-jvm"), tempDir.resolve(name + "-js"), expected);
    }

    @Test
    void parityHelloWorld(@TempDir Path tempDir) throws IOException {
        runParity("main() { println(\"Hello, Kof!\") }", "Hello, Kof!", tempDir, "hello");
    }

    @Test
    void parityStringDoubleConcat(@TempDir Path tempDir) throws IOException {
        // 02/09: "str" + double descartava o operando FP (yield incondicional
        // no guard de concat) → saída vazia. Agora concatena em JVM/JS/Native.
        runParity("""
                main() {
                    println("r=" + 2.5)
                    println("d=" + 1.5 + 2.5)
                    println("pi=" + 3.14159)
                    println("f=" + 1.5f)
                }
                """, "r=2.5\nd=1.52.5\npi=3.14159\nf=1.5", tempDir, "concat");
    }

    @Test
    void parityArithmetic(@TempDir Path tempDir) throws IOException {
        runParity("""
                main() {
                    println(10 + 20 * 3)
                    println(100 / 7)
                    println(17 % 5)
                    println(2.5 * 3)
                    println(2147483647 + 1)
                }
                """, "70\n14\n2\n7.5\n-2147483648", tempDir, "arith");
    }

    @Test
    void parityControlFlow(@TempDir Path tempDir) throws IOException {
        runParity("""
                main() {
                    var sum = 0
                    for (var i = 0; i < 5; i++) {
                        if (i == 2) {
                            continue
                        }
                        sum = sum + i
                    }
                    println(sum)
                    var i = 0
                    while (i < 3) {
                        i = i + 1
                    }
                    println(i)
                    do {
                        i = i - 1
                    } while (i > 0)
                    println(i)
                }
                """, "8\n3\n0", tempDir, "flow");
    }

    @Test
    void parityFunctions(@TempDir Path tempDir) throws IOException {
        runParity("""
                Int factorial(Int n) {
                    if (n <= 1) {
                        return 1
                    }
                    return n * factorial(n - 1)
                }

                main() {
                    println(factorial(6))
                }
                """, "720", tempDir, "funcs");
    }

    @Test
    void parityClassesAndList(@TempDir Path tempDir) throws IOException {
        runParity("""
                class User {
                    String name
                    Int age

                    constructor(String name, Int age) {
                        this.name = name
                        this.age = age
                    }
                }

                main() {
                    var users = listOf(User("Mel", 30), User("Kof", 25))
                    for (var i = 0; i < users.size; i++) {
                        println(users.get(i).name)
                        println(users.get(i).age)
                    }
                    println(users.size)
                }
                """, "Mel\n30\nKof\n25\n2", tempDir, "classes");
    }

    @Test
    void parityRecordsAndJson(@TempDir Path tempDir) throws IOException {
        runParity("""
                class User(
                    String name
                )

                main() {
                    var users = listOf(User("Mel"), User("Kof"))
                    println(json.encode(users))
                }
                """, "[{\"name\":\"Mel\"},{\"name\":\"Kof\"}]", tempDir, "json");
    }

    @Test
    void parityStrings(@TempDir Path tempDir) throws IOException {
        runParity("""
                main() {
                    var s = "Hello World"
                    println(s.length)
                    println(s.toUpperCase())
                    println(s.substring(6))
                    println(s.indexOf("World"))
                    println(s.startsWith("He"))
                    println(s.replace('l', 'L'))
                    println("a" + "b" + 1)
                    println("abc" == "abc")
                    println("abc" != "abd")
                }
                """, "11\nHELLO WORLD\nWorld\n6\ntrue\nHeLLo WorLd\nab1\ntrue\ntrue", tempDir, "strings");
    }

    @Test
    void parityExceptions(@TempDir Path tempDir) throws IOException {
        runParity("""
                main() {
                    try {
                        throw "boom"
                    } catch (String e) {
                        println("caught: " + e)
                    } finally {
                        println("done")
                    }
                }
                """, "caught: boom\ndone", tempDir, "exceptions");
    }

    @Test
    void parityColor32Bit(@TempDir Path tempDir) throws IOException {
        // 32-bit ARGB color type + named palette — no hex/ANSI conversion by hand
        runParity("""
                class Color {
                    Int value

                    constructor(Int value) {
                        this.value = value
                    }

                    Int red() { return (this.value >> 16) & 0xFF }
                    Int green() { return (this.value >> 8) & 0xFF }
                    Int blue() { return this.value & 0xFF }
                    Int alpha() { return (this.value >> 24) & 0xFF }
                    String ansi() {
                        return "\\u001b[38;2;" + this.red() + ";" + this.green() + ";" + this.blue() + "m"
                    }
                }

                class Colors {
                    static Int primary = 0xFF6750A4
                    static Int success = 0xFF4CAF50
                }

                main() {
                    var c = Color(Colors.primary)
                    println(c.red())
                    println(c.green())
                    println(c.blue())
                    println(c.alpha())
                    var s = Color(Colors.success)
                    println(s.red())
                    println(s.green())
                    println(s.blue())
                    println(c.ansi() == "\\u001b[38;2;103;80;164m")
                }
                """, "103\n80\n164\n255\n76\n175\n80\ntrue", tempDir, "color");
    }

    @Test
    void parityArrayAndSwitch(@TempDir Path tempDir) throws IOException {
        runParity("""
                main() {
                    var arr = new Int[4]
                    arr[0] = 7
                    arr[1] = 3
                    println(arr.length)
                    println(arr[0] + arr[1])
                    var x = 2
                    switch (x) {
                        case 1:
                            println("one")
                        case 2:
                            println("two")
                        default:
                            println("other")
                    }
                }
                """, "4\n10\ntwo", tempDir, "array");
    }

    // ── paridade cross-target dos bugs corrigidos na varredura do
    //    interpretador (06/09, regra 5): os mesmos casos que o
    //    KofScriptTest.interpreterParitySweep trava JVM×interpretado
    //    agora travam JVM×JS.

    @Test
    void parityStaticFieldSimpleName(@TempDir Path tempDir) throws IOException {
        // bug (lowering): campo estático por nome simples em método estático
        // baixava this inexistente (VerifyError no JVM compilado).
        runParity("""
                class Counter {
                    static Int count = 0
                    static Int bump() {
                        count = count + 1
                        return count
                    }
                }
                main() {
                    println(Counter.bump())
                    println(Counter.bump())
                    println(Counter.count)
                }
                """, "1\n2\n2", tempDir, "staticfield");
    }

    @Test
    void parityListContainsUnknownElement(@TempDir Path tempDir) throws IOException {
        // bug 35: contains(int) em List de elemento Unknown não boxeava o
        // argumento → VerifyError no JVM compilado.
        runParity("""
                main() {
                    var l = listOf()
                    println(l.isEmpty())
                    println(l.size)
                    println(l.contains(1))
                }
                """, "true\n0\nfalse", tempDir, "contains");
    }

    @Test
    void parityNullEquality(@TempDir Path tempDir) throws IOException {
        // bug 36: null == null baixava if_icmpeq (UnknownType→primitivo) →
        // VerifyError no JVM compilado.
        runParity("""
                main() {
                    var a = null
                    var b = null
                    println(a == b)
                    println(a != b)
                }
                """, "true\nfalse", tempDir, "nulleq");
    }
}