package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


class JvmE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path classFile = outDir.resolve("Default/Main.class");
        assertTrue(Files.exists(classFile), "Class file should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    @Test
    void execHelloWorld(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, "main() { println(\"Hello, JVM!\") }");
        runJvm(source, tempDir.resolve("out"), "Hello, JVM!");
    }

    @Test
    void execArithmetic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(10 + 20 * 3)
                println(100 / 7)
                println(17 % 5)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "70\n14\n2");
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
                var y = 1
                if (y > 5) {
                    println("greater2")
                } else {
                    println("smaller2")
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "greater\nsmaller2");
    }

    @Test
    void execIfElseNested(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 7
                if (x > 5) {
                    if (x > 8) {
                        println("high")
                    } else {
                        println("mid")
                    }
                } else {
                    println("low")
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "mid");
    }

    @Test
    void execWhileLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                var sum = 0
                while (i < 5) {
                    sum = sum + i
                    i = i + 1
                }
                println(sum)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "10");
    }

    @Test
    void execForLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var sum = 0
                for (var i = 0; i < 10; i++) {
                    sum = sum + i
                }
                println(sum)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "45");
    }

    @Test
    void execDoWhile(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                do {
                    i = i + 1
                } while (i < 3)
                println(i)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "3");
    }

    @Test
    void execBreakContinue(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var sum = 0
                for (var i = 0; i < 10; i++) {
                    if (i == 2) { continue }
                    if (i == 5) { break }
                    sum = sum + i
                }
                println(sum)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "8");
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
                    default: println("many")
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "two");
    }

    @Test
    void execStringConcatEquals(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "Hello"
                var t = s + " World"
                println(t)
                println(s == "Hello")
                println(s != "Hello")
                println(s != "World")
                println(s.length)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "Hello World\ntrue\nfalse\ntrue\n5");
    }

    @Test
    void execStringMethods(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "Hello World"
                println(s.charAt(1))
                println(s.substring(6))
                println(s.substring(0, 5))
                println(s.contains("World"))
                println(s.startsWith("Hello"))
                println(s.endsWith("orld"))
                println(s.indexOf("W"))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "101\nWorld\nHello\ntrue\ntrue\ntrue\n6");
    }

    @Test
    void execStringComparison(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = "x"
                var b = "y"
                var ne = a != b
                var ne2 = a != "x"
                if (ne) { println("ne-true") } else { println("ne-false") }
                if (ne2) { println("ne2-true") } else { println("ne2-false") }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "ne-true\nne2-false");
    }

    @Test
    void execListOperations(@TempDir Path tempDir) throws IOException {
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
                l.set(0, 100)
                println(l.get(0))
                println(l.size)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "45\n100\n10");
    }

    @Test
    void execListRichApi(@TempDir Path tempDir) throws IOException {
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
                var e = listOf<Int>()
                println(e.size)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "4\ntrue\nfalse\nfalse\n2\n3\n3\ntrue\ntrue\n0");
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
        runJvm(source, tempDir.resolve("out"), "c\n3");
    }

    @Test
    void execArrays(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[3]
                a[0] = 1
                a[1] = 2
                a[2] = 3
                println(a[0] + a[1] + a[2])
                println(a.length)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "6\n3");
    }

    @Test
    void execFunctions(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fib(Int n): Int {
                if (n < 2) { return n }
                return fib(n - 1) + fib(n - 2)
            }
            main() {
                println(fib(10))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "55");
    }

    @Test
    void execRecords(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            main() {
                var p = Point(10, 20)
                println(p.x())
                println(p.y())
            }
            """);
        runJvm(source, tempDir.resolve("out"), "10\n20");
    }

    @Test
    void execClasses(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Counter {
                Int value = 0
                public inc() {
                    value = value + 1
                }
                public get(): Int {
                    return value
                }
            }
            main() {
                var c = new Counter()
                c.inc()
                c.inc()
                c.inc()
                println(c.get())
            }
            """);
        runJvm(source, tempDir.resolve("out"), "3");
    }

    @Test
    void execInheritance(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                String name
                public constructor(String name) {
                    this.name = name
                }
                speak(): String = "animal"
            }
            class Dog extends Animal {
                public constructor(String name) {
                    super(name)
                }
                speak(): String = "dog"
            }
            main() {
                var d = new Dog("Rex")
                println(d.speak())
                println(d.name)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "dog\nRex");
    }

    @Test
    void execVirtualDispatch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                speak(): String = "animal"
            }
            class Dog extends Animal {
                speak(): String = "dog"
            }
            class Cat extends Animal {
                speak(): String = "cat"
            }
            main() {
                var a = new Dog()
                println(a.speak())
                var b = new Cat()
                println(b.speak())
            }
            """);
        runJvm(source, tempDir.resolve("out"), "dog\ncat");
    }

    @Test
    void execInterfaces(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Dog implements Speaker {
                speak(): String = "woof"
            }
            main() {
                var d = new Dog()
                println(d.speak())
            }
            """);
        runJvm(source, tempDir.resolve("out"), "woof");
    }

    @Test
    void execGenericFunction(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            identity<T>(T x): T {
                return x
            }
            main() {
                println(identity<Int>(42))
                println(identity<String>("hi"))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "42\nhi");
    }

    @Test
    void execGenericClass(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Box<T> {
                T value
                public constructor(T value) {
                    this.value = value
                }
                get(): T {
                    return value
                }
            }
            main() {
                var b = new Box<Int>(7)
                println(b.get())
            }
            """);
        runJvm(source, tempDir.resolve("out"), "7");
    }

    @Test
    void execLongArithmetic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = 10000000000l
                var b = 5000000000l
                println(a + b)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "15000000000");
    }

    @Test
    void execCastInstanceof(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal { }
            class Dog extends Animal { }
            main() {
                var d = new Dog()
                if (d instanceof Dog) {
                    println("is-dog")
                }
                if (d instanceof Animal) {
                    println("is-animal")
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "is-dog\nis-animal");
    }

    @Test
    void execBooleanOperators(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = true
                var b = false
                var c = a && b
                var d = a || b
                println(c)
                println(d)
                println(!c)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "false\ntrue\ntrue");
    }

    @Test
    void execFunctionDeclarationForms(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            String saudacao() {
                return "oi"
            }
            Int soma(Int a, Int b): Int {
                return a + b
            }
            Bool positivo(Int x) = x > 0
            class Usuario {
                String nome
                public constructor(String nome) { this.nome = nome }
                String buscaNomeDeUsuario() {
                    return nome
                }
            }
            main() {
                println(saudacao())
                println(soma(2, 3))
                println(positivo(5))
                var u = new Usuario("Mel")
                println(u.buscaNomeDeUsuario())
            }
            """);
        runJvm(source, tempDir.resolve("out"), "oi\n5\ntrue\nMel");
    }

    @Test
    void execRecordValueMethods(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Ponto(Int x, Int y)
            main() {
                var p = Ponto(3, 7)
                println(p)
                var q = Ponto(3, 7)
                println(p == p)
                println(p == q)
                println(p.x() == q.x() && p.y() == q.y())
                println(p.hashCode() == q.hashCode())
            }
            """);
        // p == q é igualdade de CONTEÚDO (record gera equals) — corrigido
        // 03/09 (known-bugs #11); antes era referência (false).
        runJvm(source, tempDir.resolve("out"), "Ponto[x=3, y=7]\ntrue\ntrue\ntrue\ntrue");
    }
}