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
}
