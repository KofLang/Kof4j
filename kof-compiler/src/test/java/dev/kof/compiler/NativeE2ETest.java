package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NativeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void nativeHelloWorld(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, "main() { println(\"Hello, Kof!\") }");
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");

        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
    }

    @Test
    void nativeArithmetic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 10
                var y = 20
                println(x + y)
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void nativeIfElse(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 10
                if (x > 5) {
                    println("greater")
                } else {
                    println("smaller")
                }
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void nativeWhileLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                while (i < 3) {
                    println(i)
                    i = i + 1
                }
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void nativeForLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                for (var i = 0; i < 3; i++) {
                    println(i)
                }
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void nativeFunctionCall(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            add(Int a, Int b): Int {
                return a + b
            }
            main() {
                println(add(2, 3))
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void nativeRecordInstantiation(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            main() {
                var p = Point(10, 20)
                println(p.x())
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
    }





    private String runNative(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void execVirtualDispatchOverride(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                speak(): String = "animal"
            }
            class Dog extends Animal {
                speak(): String = "dog"
            }
            main() {
                var a = new Dog()
                println(a.speak())
            }
            """);
        runNative(source, tempDir.resolve("out"), "dog");
    }

    @Test
    void execVirtualDispatchNoOverride(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                speak(): String = "animal"
            }
            class Dog extends Animal {
            }
            main() {
                var a = new Dog()
                println(a.speak())
            }
            """);
        runNative(source, tempDir.resolve("out"), "animal");
    }

    @Test
    void execInstanceMethod(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class User {
                name(): String = "Mel"
            }
            main() {
                var user = new User()
                println(user.name())
            }
            """);
        runNative(source, tempDir.resolve("out"), "Mel");
    }

    @Test
    void execFieldAssignment(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class User {
                String name
            }
            main() {
                var u = new User()
                u.name = "Mel"
                println(u.name)
            }
            """);
        runNative(source, tempDir.resolve("out"), "Mel");
    }

    @Test
    void execVirtualDispatchWithArg(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                describe(Int n): String = "animal"
            }
            class Dog extends Animal {
                describe(Int n): String = "dog"
            }
            main() {
                var a = new Dog()
                println(a.describe(7))
            }
            """);
        runNative(source, tempDir.resolve("out"), "dog");
    }

    @Test
    void execStringLength(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "Hello"
                println(s.length)
            }
            """);
        runNative(source, tempDir.resolve("out"), "5");
    }

    @Test
    void execStringCharAt(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "Hello"
                println(s.charAt(0))
                println(s.charAt(4))
            }
            """);
        runNative(source, tempDir.resolve("out"), "72\n111");
    }

    @Test
    void execStringSubstring(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "Hello"
                println(s.substring(1, 4))
            }
            """);
        runNative(source, tempDir.resolve("out"), "ell");
    }

    @Test
    void execStringContains(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "Hello"
                println(s.contains("ell"))
            }
            """);
        runNative(source, tempDir.resolve("out"), "true");
    }

    @Test
    void execStringStartsWith(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "Hello"
                println(s.startsWith("He"))
            }
            """);
        runNative(source, tempDir.resolve("out"), "true");
    }

    @Test
    void execStringEndsWith(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "Hello"
                println(s.endsWith("lo"))
            }
            """);
        runNative(source, tempDir.resolve("out"), "true");
    }

    @Test
    void execStringConcat(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "Hello"
                println(s.concat(" World"))
            }
            """);
        runNative(source, tempDir.resolve("out"), "Hello World");
    }

    @Test
    void execNegativeInt(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(-42)
            }
            """);
        runNative(source, tempDir.resolve("out"), "-42");
    }

    @Test
    void execInstanceOf(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
            }
            class Dog extends Animal {
            }
            main() {
                var a = new Dog()
                println(a instanceof Dog)
                println(a instanceof Animal)
            }
            """);
        runNative(source, tempDir.resolve("out"), "true\ntrue");
    }

    @Test
    void execIfElse(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 10
                if (x > 5) {
                    println("greater")
                } else {
                    println("smaller")
                }
            }
            """);
        runNative(source, tempDir.resolve("out"), "greater");
    }

    @Test
    void execWhileLoopRuns(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                while (i < 3) {
                    println(i)
                    i = i + 1
                }
            }
            """);
        runNative(source, tempDir.resolve("out"), "0\n1\n2");
    }

    @Test
    void execForLoopRuns(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                for (var i = 0; i < 3; i++) {
                    println(i)
                }
            }
            """);
        runNative(source, tempDir.resolve("out"), "0\n1\n2");
    }

    @Test
    void execBreak(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                while (true) {
                    if (i == 3) { break }
                    println(i)
                    i = i + 1
                }
            }
            """);
        runNative(source, tempDir.resolve("out"), "0\n1\n2");
    }

    @Test
    void execContinue(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                while (i < 5) {
                    i = i + 1
                    if (i == 2) { continue }
                    println(i)
                }
            }
            """);
        runNative(source, tempDir.resolve("out"), "1\n3\n4\n5");
    }

    @Test
    void execStringEquals(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = "Hello"
                var b = "Hello"
                println(a == b)
            }
            """);
        runNative(source, tempDir.resolve("out"), "true");
    }

    @Test
    void execIntComparison(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(3 < 5)
                println(3 > 5)
                println(3 == 3)
                println(3 != 4)
            }
            """);
        runNative(source, tempDir.resolve("out"), "true\nfalse\ntrue\ntrue");
    }

    @Test
    void execSwitch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 2
                switch (x) {
                    case 1: println("one")
                    case 2: println("two")
                    default: println("other")
                }
            }
            """);
        runNative(source, tempDir.resolve("out"), "two");
    }

    @Test
    void execLongPrint(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(10000000000l)
                var big = 5000000000l
                println(big + big)
                println(10000000000l + 1)
            }
            """);
        runNative(source, tempDir.resolve("out"), "10000000000\n10000000000\n10000000001");
    }

    @Test
    void execBitwise(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(5 & 3)
                println(5 | 3)
                println(5 ^ 3)
                println(1 << 4)
                println(256 >> 4)
                println(-1 >>> 1 != 0)
            }
            """);
        runNative(source, tempDir.resolve("out"), "1\n7\n6\n16\n16\ntrue");
    }

    @Test
    void execStringIndexOf(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "Hello World"
                println(s.indexOf("W"))
                println(s.indexOf("o"))
                println(s.indexOf("zz"))
            }
            """);
        runNative(source, tempDir.resolve("out"), "6\n4\n-1");
    }

    @Test
    void execStringTrimCase(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println("  padded  ".trim())
                println("hello".toUpperCase())
                println("HELLO".toLowerCase())
                println("a-b-c".replace(45, 95))
                println("abc".equalsIgnoreCase("ABC"))
            }
            """);
        runNative(source, tempDir.resolve("out"), "padded\nHELLO\nhello\na_b_c\ntrue");
    }

    @Test
    void execStringSplit(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var parts = "a,b,c".split(",")
                println(parts.length)
                println(parts[0])
                println(parts[1])
                println(parts[2])
            }
            """);
        runNative(source, tempDir.resolve("out"), "3\na\nb\nc");
    }

    @Test
    void execListContains(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var l = listOf(1, 2, 3, 4)
                println(l.size)
                println(l.contains(3))
                println(l.contains(99))
                println(l.isEmpty())
                var removed = l.remove(1)
                println(removed)
                println(l.size)
                println(l.get(1))
                l.clear()
                println(l.isEmpty())
                var s = listOf("a", "b")
                println(s.contains("b"))
                println(s.contains("zz"))
                var e = listOf<Int>()
                println(e.size)
            }
            """);
        runNative(source, tempDir.resolve("out"), "4\ntrue\nfalse\nfalse\n2\n3\n3\ntrue\ntrue\nfalse\n0");
    }

    @Test
    void execDoWhile(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                do {
                    println(i)
                    i = i + 1
                } while (i < 3)
            }
            """);
        runNative(source, tempDir.resolve("out"), "0\n1\n2");
    }

    @Test
    void execArray(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var arr = new Int[3]
                arr[0] = 10
                arr[1] = 20
                arr[2] = 30
                println(arr[1])
                println(arr.length)
            }
            """);
        runNative(source, tempDir.resolve("out"), "20\n3");
    }

    @Test
    void execArrayArithmetic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var arr = new Int[3]
                arr[0] = 10
                arr[1] = 20
                arr[2] = 30
                println(arr[1] + arr[2])
                println(arr[0] + arr[1] + arr[2])
                var small = new Byte[2]
                small[0] = 5
                small[1] = 7
                println(small[0] + small[1])
            }
            """);
        runNative(source, tempDir.resolve("out"), "50\n60\n12");
    }

    @Test
    void execRecursion(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fib(Int n): Int {
                if (n <= 1) { return n }
                return fib(n - 1) + fib(n - 2)
            }
            main() {
                println(fib(10))
            }
            """);
        runNative(source, tempDir.resolve("out"), "55");
    }

    @Test
    void execSubtraction(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(10 - 3)
                println(3 - 10)
                println(2 - 1 - 1)
            }
            """);
        runNative(source, tempDir.resolve("out"), "7\n-7\n0");
    }

    @Test
    void execConstructor(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class User {
                String name
                constructor(String name) {
                    this.name = name
                }
                greet(): String {
                    return "hi " + name
                }
            }
            main() {
                var u = new User("Mel")
                println(u.greet())
            }
            """);
        runNative(source, tempDir.resolve("out"), "hi Mel");
    }

    @Test
    void execSuperConstructor(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                String name
                constructor(String name) {
                    this.name = name
                }
            }
            class Dog extends Animal {
                constructor(String name) {
                    super(name)
                }
                speak(): String {
                    return "dog " + name
                }
            }
            main() {
                var d = new Dog("Rex")
                println(d.speak())
            }
            """);
        runNative(source, tempDir.resolve("out"), "dog Rex");
    }

    @Test
    void execThreeLevelInheritance(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                speak(): String = "a"
            }
            class Dog extends Animal {
                speak(): String = "d"
            }
            class Golden extends Dog {
                speak(): String = "g"
            }
            main() {
                var g = new Golden()
                println(g.speak())
                var d = new Dog()
                println(d.speak())
            }
            """);
        runNative(source, tempDir.resolve("out"), "g\nd");
    }

    @Test
    void execCast(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                speak(): String = "a"
            }
            class Dog extends Animal {
                speak(): String = "d"
            }
            main() {
                var a = new Dog()
                var d = a as Dog
                println(d.speak())
            }
            """);
        runNative(source, tempDir.resolve("out"), "d");
    }

    @Test
    void execGenericFunction(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            identity<T>(T x): T {
                return x
            }
            main() {
                println(identity(42))
                println(identity("hi"))
            }
            """);
        runNative(source, tempDir.resolve("out"), "42\nhi");
    }

    @Test
    void execGenericClass(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Box<T> {
                T value
                set(T v) {
                    value = v
                }
                get(): T {
                    return value
                }
            }
            main() {
                var b = new Box<Int>()
                b.set(7)
                println(b.get())
            }
            """);
        runNative(source, tempDir.resolve("out"), "7");
    }

    @Test
    void execListInt(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var l = new List<Int>()
                l.add(10)
                l.add(20)
                l.add(30)
                println(l.get(1))
                println(l.size)
            }
            """);
        runNative(source, tempDir.resolve("out"), "20\n3");
    }

    @Test
    void execListGrow(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var l = new List<Int>()
                for (var i = 0; i < 10; i++) {
                    l.add(i)
                }
                var sum = 0
                for (var i = 0; i < l.size; i++) {
                    sum = sum + l.get(i)
                }
                println(sum)
            }
            """);
        runNative(source, tempDir.resolve("out"), "45");
    }

    @Test
    void execListString(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var l = new List<String>()
                l.add("a")
                l.add("b")
                l.add("c")
                println(l.get(2))
                println(l.size)
            }
            """);
        runNative(source, tempDir.resolve("out"), "c\n3");
    }

    @Test
    void execFunctionDeclarationPrefixForm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            String saudacao() {
                return "oi"
            }
            main() {
                println(saudacao())
            }
            """);
        runNative(source, tempDir.resolve("out"), "oi");
    }

    // known-bugs #22 — native: constructor call to a class from ANOTHER
    // package produced `undefined reference to 'C_init_0'` (call site used the
    // simple name; the definition uses the internal name com_acme_C_init_0).
    @Test
    void nativeConstructorFromImportedPackage(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src.resolve("com/acme"));
        Files.writeString(src.resolve("Main.kf"), """
                import com.acme.C

                main() {
                    var c = C()
                    println(c.msg())
                }
                """);
        Files.writeString(src.resolve("com/acme/C.kf"), """
                package com.acme

                class C {
                    String msg() { return "de C" }
                }
                """);
        Path outDir = tempDir.resolve("out");
        // coletado como o CLI (`collect` não-recursivo): só o Main.kf na raiz;
        // o import com.acme.C puxa com/acme/C.kf via moduleRoot.
        CompilationResult result = driver.compileSources(
                java.util.List.of(src.resolve("Main.kf")),
                outDir, Target.NATIVE, src);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "Exit code, output: '" + output + "'");
            assertEquals("de C", output, "Unexpected output");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    // known-bugs #9 — native lambda capture of a MUTABLE variable returned
    // garbage: the prologue treated the capture as an incoming register arg
    // (consuming rsi), so the real param got rdx (uninitialized). The prologue
    // now only assigns registers to PARAM slots; captures load from fields.
    @Test
    void nativeLambdaMutableCapture(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var offset = 10
                var f = (x: Int) -> x + offset
                println(f(5))
                offset = 20
                println(f(5))
                var a = 1
                var b = 2
                var g = (y: Int) -> y + a + b
                println(g(0))
            }
            """);
        runNative(source, tempDir.resolve("out"), "15\n25\n3");
    }

    // known-bugs #41 — campo estático no Native dava lixo (stub KofGetStatic/
    // KofPutStatic vazio). Int/String/bool estáticos agora residem no .data
    // com initialValue; o acesso usa o slot global.
    @Test
    void nativeStaticFields(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class C {
                static Int count = 0
                static String name = "mel"
                static Bool ok = true
                static Int bump() {
                    count = count + 1
                    return count
                }
            }
            main() {
                println(C.name)
                println(C.ok)
                println(C.bump())
                println(C.bump())
                println(C.count)
            }
            """);
        runNative(source, tempDir.resolve("out"), "mel\ntrue\n1\n2\n2");
    }

    // known-bugs #43 — String.length no Native contava bytes UTF-8 (café=5)
    // vs code units do JVM/JS (café=4). kof_string_length agora percorre o
    // UTF-8 contando code units UTF-16 (astral → 2, surrogate pair).
    @Test
    void nativeStringLengthUtf16(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var s = "café"
                    println(s.length)
                    var e = "a😀b"
                    println(e.length)
                }
                """);
        runNative(source, tempDir.resolve("out"), "4\n4");
    }

    // bug 43 (metade char_at, 10/09): charAt conta code units UTF-16 no Native
    // x86_64 — igual ao JVM/JS. café.charAt(3)=233 (é), astral: charAt(1)=
    // 55357 (surrogate high), charAt(2)=56832 (surrogate low).
    @Test
    void nativeStringCharAtUtf16(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var s = "café"
                    println(s.charAt(3))
                    var e = "a😀b"
                    println(e.charAt(1))
                    println(e.charAt(2))
                    println(e.charAt(3))
                }
                """);
        runNative(source, tempDir.resolve("out"), "233\n55357\n56832\n98");
    }

    // bug 43 (metade substring, 10/09): substring conta code units UTF-16 no
    // Native — igual ao JVM/JS. Testes SEM cortar par astral ao meio (corte de
    // surrogate exige storage WTF-8 — sub-residual registrado, §43). Verificado
    // contra o oracle JVM no mesmo programa (3/1/bc/cd/é idênticos).
    @Test
    void nativeStringSubstringUtf16(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var s = "café"
                    println(s.substring(1))
                    println(s.substring(1).length)
                    println(s.substring(3))
                    var e = "a😀b"
                    println(e.substring(0, 3).length)
                    println(e.substring(1, 3))
                    println(e.substring(3))
                }
                """);
        runNative(source, tempDir.resolve("out"), "afé\n3\né\n3\n😀\nb");
    }

    // bug 43 (face indexOf/lastIndexOf, 10/09): índice em CODE UNITS UTF-16 no
    // Native — igual ao JVM/Script (byte-based dava `a😀b.indexOf("c")`=10 vs
    // 6). Needle vazio, needle maior, corte de par e casos-borda cobertos.
    @Test
    void nativeStringIndexOfUtf16(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var e = "a😀b😀c"
                    println(e.indexOf("c"))
                    println(e.indexOf("b"))
                    println(e.indexOf("😀c"))
                    println(e.indexOf("z"))
                    println(e.lastIndexOf("😀"))
                    println(e.indexOf(""))
                    println(e.lastIndexOf(""))
                    println("café".indexOf("é"))
                    println("abcdef".indexOf("abcdef"))
                    println("abcdef".indexOf("abcdefg"))
                }
                """);
        runNative(source, tempDir.resolve("out"), "6\n3\n4\n-1\n4\n0\n7\n3\n0\n-1");
    }

    // bug 95: o ramo inline do split usava labels FIXAS (.Lkof_split_empty_sep/
    // _call) — um 2º split no mesmo programa redefinía o símbolo → "already
    // defined" no assembler (COMP001). Qualquer programa com 2+ splits (parsear
    // 2 CSV) era INCOMPILÁVEL no Native x86_64.
    @Test
    void nativeTwoSplitsInOneProgram(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var a = "x,y".split(",").length
                    var b = "p,q,r".split(",").length
                    println(a + b)
                    println("m,n".split(",").get(1))
                }
                """);
        runNative(source, tempDir.resolve("out"), "5\nn");
    }

    // bug 97: String.compareTo/String.hashCode eram declarados no
    // type-system.md + aceitos pelo typer, mas nenhum nativo os emitia →
    // undefined reference java_lang_String_compareTo/_hashCode no link. Os 2
    // agora andam por CODE UNITS UTF-16 (não memcmp/byte-sum — paridade falsa
    // em astrais era a armadilha, lição bug 43). Golden = saída JVM/Script.
    @Test
    void nativeStringCompareToAndHashCodeUtf16(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    println("ab".compareTo("aX"))
                    println("a\\u00e9".compareTo("a"))
                    println("abc".compareTo("abd"))
                    println("ab".compareTo("abc"))
                    println("\\uD83D\\uDE00".compareTo("a"))
                    println("a\\uD83D\\uDE00".compareTo("a\\uFFFD"))
                    println("a\\uFFFD".compareTo("a\\uD83D\\uDE00"))
                    println("abc".hashCode())
                    println("a\\u00e9".hashCode())
                    println("\\uD83D\\uDE00".hashCode())
                    println("".hashCode())
                }
                """);
        runNative(source, tempDir.resolve("out"),
                "10\n1\n-1\n-1\n55260\n-10176\n10176\n96354\n3240\n1772899\n0");
    }

    // bug 97 (continuação): String.equals caiu no caminho genérico →
    // undefined reference java_lang_String_equals. Agora roteado p/
    // kof_string_equals (mesmo conteúdo do `==`). Guard isString é essencial:
    // record.equals (equals gerado campo-a-campo) NÃO pode ser hijackado —
    // o teste cobre os DOIS lado a lado no MESMO programa.
    @Test
    void nativeStringEqualsVsRecordEquals(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                record P(Int x, Int y)
                main() {
                    println("a\\u00e9".equals("a\\u00e9"))
                    println("caf\\u00e9".equals("cafe"))
                    println("hi".equals("hi" + ""))
                    var a = P(1,2)
                    var b = P(1,2)
                    var c = P(1,3)
                    println(a.equals(b))
                    println(a.equals(c))
                    var l = listOf(1,2,3)
                    println(l.contains(2))
                }
                """);
        runNative(source, tempDir.resolve("out"), "true\nfalse\ntrue\ntrue\nfalse\ntrue");
    }

    // bug 100: hijack de método de usuário. 14 dos 16 ramos INSTANCE do
    // NativeX86StringCalls casavam SÓ por nome — `p.trim()` numa classe do
    // usuário era roteado p/ o intrínseco String (deref do receiver como
    // KofString) → LIXO silencioso (ex.: -103849952), não crash. JVM despacha
    // pela classe; JS gateia isStringOp; riscv gateia isString. Fix: guard
    // BuiltinTypes.isString(ownerType) nos 14 ramos (FUNCTION kof_string_to_*
    // / kof_json_* não colidem — prefixo não-atingível). Guard isString já
    // existia só em length/equals (e charAt, meio-guardado).
    @Test
    void nativeUserClassMethodsNotHijackedByStringOps(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                class P {
                    Int trim() { return 42 }
                    Int indexOf(Int n) { return n + 1 }
                    String split(Int n) { return "s" + n }
                    Int toUpperCase() { return 7 }
                }
                main() {
                    var p = P()
                    println(p.trim())
                    println(p.indexOf(1))
                    println(p.split(9))
                    println(p.toUpperCase())
                    println(" x ".trim() + "|")
                    println("abc".indexOf("c"))
                    println("a,b".split(",").get(1))
                    println("ab".toUpperCase())
                }
                """);
        runNative(source, tempDir.resolve("out"),
                "42\n2\ns9\n7\nx|\n2\nb\nAB");
    }

    // §102 (paridade absoluta): indexOf/lastIndexOf/startsWith com índice
    // inicial — o helper de aridade 1 IGNORAVA o 2º arg (o roteador já
    // empilhava em %rdx). `"aXb".indexOf("X",2)` dava 1 no Native vs -1 no
    // JVM/Script. Agora: kof_string_index_of2/_last_index_of2/_starts_with2
    // (UTF-16 code units + clampagens do JDK 21, oracle travado neste teste;
    // astrais cobrem o corte de par). Faces riscv/aarch: residuais honestos
    // (env cross ausente aqui; bug 59).
    @Test
    void nativeStringSearchFromIndex(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    println("aXb".indexOf("X",2))
                    println("aXb".indexOf("X",1))
                    println("aXb".indexOf("X",-3))
                    println("aXb".indexOf("X",4))
                    println("abc".indexOf("",2))
                    println("abc".indexOf("",5))
                    println("abc".indexOf("",-1))
                    println("aXa".lastIndexOf("a",1))
                    println("aXa".lastIndexOf("a",-1))
                    println("aXa".lastIndexOf("a",9))
                    println("abc".lastIndexOf("",2))
                    println("abc".lastIndexOf("",5))
                    println("abc".lastIndexOf("",-1))
                    println("aXb".startsWith("X",1))
                    println("aXb".startsWith("X",2))
                    println("aXb".startsWith("X",-1))
                    println("abc".startsWith("",3))
                    println("abc".startsWith("",4))
                    var s = "a😀b"
                    println(s.indexOf("b",2))
                    println(s.indexOf("b",1))
                    println(s.indexOf("😀",1))
                    println(s.indexOf("😀",2))
                    println(s.indexOf("",2))
                    println(s.lastIndexOf("b",2))
                    println(s.lastIndexOf("b",3))
                    println(s.lastIndexOf("a",2))
                    println(s.lastIndexOf("😀",1))
                    println(s.startsWith("b",2))
                    println(s.startsWith("b",3))
                    println(s.startsWith("😀",1))
                    println(s.startsWith("😀",2))
                }
                """);
        runNative(source, tempDir.resolve("out"),
                "-1\n1\n1\n-1\n2\n3\n0\n0\n-1\n2\n2\n3\n-1\ntrue\nfalse\nfalse\ntrue\nfalse"
                + "\n3\n3\n1\n-1\n2\n-1\n3\n0\n1\nfalse\ntrue\ntrue\nfalse");
    }

    @Test
    void nativeMultiDimArray(@TempDir Path tempDir) throws IOException {
        // §113: `new Int[a][b]` não alocava NADA no Native (KofNewMultiArray
        // caía no default -> {} → SIGSEGV). JVM/Script/JS: 2 / 7.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var m = new Int[2][3]
                m[1][2] = 7
                println(m.length)
                println(m[1][2])
            }
            """);
        runNative(source, tempDir.resolve("out"), "2\n7");
    }
}
