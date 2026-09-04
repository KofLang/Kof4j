package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NATIVE002 — E2E riscv64 (qemu). O backend cross emite o {@code kof_main} em
 * asm (stack machine, mesma semântica do x86_64) + runtime em asm puro
 * (raw syscalls, sem C — binário estático, {@code riscv64-linux-gnu-as} +
 * {@code riscv64-linux-gnu-ld}); o binário roda em {@code qemu-riscv64}.
 *
 * Pula (assume) quando a toolchain cruzada ou o qemu não existem, como
 * {@code NativeE2ETest} faz quando o assembler nativo falta.
 */
class NativeRiscv64E2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private void assumeToolchain() {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
    }

    private String runRiscv64(Path tempDir, String source) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(src, outDir, Target.NATIVE_RISCV64);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        ProcessBuilder pb = new ProcessBuilder("qemu-riscv64", binFile.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running riscv64 binary", e);
        }
        assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
        return output;
    }

    @Test
    void riscv64HelloWorld(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, "main() { println(\"Hello, Kof!\") }");
        assertEquals("Hello, Kof!", out);
    }

    @Test
    void riscv64ArithmeticAndLocal(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                println("Hello")
                var x = 10
                println(x + 5)
            }
            """);
        assertEquals("Hello\n15", out);
    }

    @Test
    void riscv64IfElseComparisonsAndArithmetic(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var x = 10
                if (x > 5) {
                    println("greater")
                } else {
                    println("smaller")
                }
                var a = 7
                var b = 3
                println(a - b)
                println(a * b)
                if (a == b) { println("eq") } else { println("ne") }
            }
            """);
        assertEquals("greater\n4\n21\nne", out);
    }

    @Test
    void riscv64DivisionModuloNegative(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                println(20 / 4)
                println(17 % 5)
                println(-7)
            }
            """);
        assertEquals("5\n2\n-7", out);
    }

    @Test
    void riscv64VirtualDispatch(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            class Animal {
                speak(): String = "animal"
            }
            class Dog extends Animal {
                speak(): String = "dog"
            }
            main() {
                var a = new Dog()
                println(a.speak())
                var b = new Animal()
                println(b.speak())
            }
            """);
        assertEquals("dog\nanimal", out);
    }

    @Test
    void riscv64FieldsAndMethods(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            class User {
                String name
                greet(): String = "oi " + name
            }
            main() {
                var u = new User()
                u.name = "Mel"
                println(u.greet())
            }
            """);
        assertEquals("oi Mel", out);
    }

    @Test
    void riscv64Arrays(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var arr = new Int[3]
                arr[0] = 10
                arr[1] = 20
                arr[2] = 30
                println(arr[0] + arr[1] + arr[2])
                println(arr.length)
            }
            """);
        assertEquals("60\n3", out);
    }

    @Test
    void riscv64List(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var l = listOf(1, 2, 3)
                l.add(4)
                println(l.size)
                println(l.get(2))
            }
            """);
        assertEquals("4\n3", out);
    }

    @Test
    void riscv64SwitchInt(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var x = 2
                switch (x) {
                    case 1: println("um")
                    case 2: println("dois")
                    default: println("outro")
                }
            }
            """);
        assertEquals("dois", out);
    }

    @Test
    void riscv64TryCatchThrow(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                try {
                    throw "boom"
                } catch (String e) {
                    println("caught: " + e)
                }
                println("done")
            }
            """);
        assertEquals("caught: boom\ndone", out);
    }

    @Test
    void riscv64PatternMatching(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var x: Object = "hello"
                switch (x) {
                    case String s:
                        println("str:" + s)
                    default:
                        println("other")
                }
                var a: Object = "world"
                if (a instanceof String) {
                    println("is string")
                }
                var b: Object = "test" as String
                println(b)
            }
            """);
        assertEquals("str:hello\nis string\ntest", out);
    }

    @Test
    void riscv64SwitchExpression(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            record Point(Int x, Int y)
            main() {
                var op = "GET"
                var r = switch (op) {
                    case "GET" -> "buscar"
                    case "POST" -> "criar"
                    default -> "x"
                }
                println(r)
                var p = Point(3, 4)
                var s = switch (p) {
                    case Point(var x, var y) -> "pt:" + x + "," + y
                    default -> "other"
                }
                println(s)
            }
            """);
        assertEquals("buscar\npt:3,4", out);
    }

    @Test
    void riscv64JsonEncode(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            record Pessoa(String nome, Int idade)
            main() {
                var p = Pessoa("Ana", 30)
                println(json.encode(p))
                var q = Pessoa("a\\"b", 1)
                println(json.encode(q))
            }
            """);
        assertEquals("{\"nome\":\"Ana\",\"idade\":30}\n{\"nome\":\"a\\\"b\",\"idade\":1}", out);
    }

    @Test
    void riscv64StringMethods(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var s = "Hello, Kof"
                println(s.length)
                println(s.substring(7))
                println(s.contains("Kof"))
                println(s.startsWith("He"))
                println(s.charAt(0))
            }
            """);
        assertEquals("10\nKof\ntrue\ntrue\n72", out);
    }

    @Test
    void riscv64Recursion(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            Int fib(Int n) {
                if (n < 2) { return n }
                return fib(n - 1) + fib(n - 2)
            }
            main() {
                println(fib(10))
            }
            """);
        assertEquals("55", out);
    }
}
