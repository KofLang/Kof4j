package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SYN001 — switch como expressão. Pattern matching via expressão
 * ({@code case X -> body}), usável como valor. Forma aditiva: o switch
 * statement ({@code case X:}) continua válido (KofPatternMatchingTest é o
 * gate de retrocompatibilidade).
 */
class KofSwitchExprE2ETest {
    private final CompilerDriver driver = new CompilerDriver();

    // ── valor: Int ─────────────────────────────────────────────────

    @Test
    void intJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var n = 2
                    var r = switch (n) {
                        case 1 -> "um"
                        case 2 -> "dois"
                        default -> "outro"
                    }
                    println(r)
                }
                """, "dois");
    }

    @Test
    void intNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
                main() {
                    var n = 2
                    var r = switch (n) {
                        case 1 -> "um"
                        case 2 -> "dois"
                        default -> "outro"
                    }
                    println(r)
                }
                """, "dois");
    }

    @Test
    void intJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                main() {
                    var n = 2
                    var r = switch (n) {
                        case 1 -> "um"
                        case 2 -> "dois"
                        default -> "outro"
                    }
                    println(r)
                }
                """, "dois");
    }

    @Test
    void intDefaultJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var n = 99
                    var r = switch (n) {
                        case 1 -> "um"
                        case 2 -> "dois"
                        default -> "outro"
                    }
                    println(r)
                }
                """, "outro");
    }

    // ── valor: String (igualdade por conteúdo — bug 4) ──────────────

    @Test
    void stringJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var op = "GET"
                    var r = switch (op) {
                        case "GET" -> "buscar"
                        case "POST" -> "criar"
                        default -> "desconhecido"
                    }
                    println(r)
                }
                """, "buscar");
    }

    @Test
    void stringNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
                main() {
                    var op = "GET"
                    var r = switch (op) {
                        case "GET" -> "buscar"
                        case "POST" -> "criar"
                        default -> "desconhecido"
                    }
                    println(r)
                }
                """, "buscar");
    }

    @Test
    void stringJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                main() {
                    var op = "GET"
                    var r = switch (op) {
                        case "GET" -> "buscar"
                        case "POST" -> "criar"
                        default -> "desconhecido"
                    }
                    println(r)
                }
                """, "buscar");
    }

    // ── pattern: case String s -> ───────────────────────────────────

    @Test
    void patternSimpleJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var x: Object = "hello"
                    var r = switch (x) {
                        case String s -> "str:" + s
                        default -> "other"
                    }
                    println(r)
                }
                """, "str:hello");
    }

    @Test
    void patternSimpleNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
                main() {
                    var x: Object = "hello"
                    var r = switch (x) {
                        case String s -> "str:" + s
                        default -> "other"
                    }
                    println(r)
                }
                """, "str:hello");
    }

    @Test
    void patternSimpleJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                main() {
                    var x: Object = "hello"
                    var r = switch (x) {
                        case String s -> "str:" + s
                        default -> "other"
                    }
                    println(r)
                }
                """, "str:hello");
    }

    // ── pattern: destructuring case Point(var x, var y) -> ─────────

    @Test
    void destructureJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                record Point(Int x, Int y)
                main() {
                    var p = Point(3, 4)
                    var r = switch (p) {
                        case Point(var x, var y) -> "pt:" + x + "," + y
                        default -> "other"
                    }
                    println(r)
                }
                """, "pt:3,4");
    }

    @Test
    void destructureNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
                record Point(Int x, Int y)
                main() {
                    var p = Point(3, 4)
                    var r = switch (p) {
                        case Point(var x, var y) -> "pt:" + x + "," + y
                        default -> "other"
                    }
                    println(r)
                }
                """, "pt:3,4");
    }

    @Test
    void destructureJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                record Point(Int x, Int y)
                main() {
                    var p = Point(3, 4)
                    var r = switch (p) {
                        case Point(var x, var y) -> "pt:" + x + "," + y
                        default -> "other"
                    }
                    println(r)
                }
                """, "pt:3,4");
    }

    // ── como return + aninhado ─────────────────────────────────────

    @Test
    void asReturnJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                String nome(Int n) {
                    return switch (n) {
                        case 0 -> "zero"
                        case 1 -> "um"
                        default -> "muitos"
                    }
                }
                main() {
                    println(nome(1))
                    println(nome(7))
                }
                """, "um\nmuitos");
    }

    @Test
    void nestedJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var a = 1
                    var b = 2
                    var r = switch (a) {
                        case 1 -> switch (b) {
                            case 2 -> "a1b2"
                            default -> "a1"
                        }
                        default -> "outro"
                    }
                    println(r)
                }
                """, "a1b2");
    }

    @Test
    void nestedJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                main() {
                    var a = 1
                    var b = 2
                    var r = switch (a) {
                        case 1 -> switch (b) {
                            case 2 -> "a1b2"
                            default -> "a1"
                        }
                        default -> "outro"
                    }
                    println(r)
                }
                """, "a1b2");
    }

    // ── retrocompatibilidade: statement segue funcionando ───────────

    @Test
    void statementStillWorksJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var op = "GET"
                    switch (op) {
                        case "GET":
                            println("buscar")
                        default:
                            println("x")
                    }
                }
                """, "buscar");
    }

    @Test
    void mixedStatementAndExprJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var n = 2
                    var r = switch (n) {
                        case 2 -> "dois"
                        default -> "outro"
                    }
                    switch (n) {
                        case 2:
                            println("stmt-dois")
                        default:
                            println("stmt-x")
                    }
                    println(r)
                }
                """, "stmt-dois\ndois");
    }

    // ── sem default nem exaustão → erro SEM032 ─────────────────────

    @Test
    void missingDefaultFailsToCompile(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, """
                main() {
                    var n = 1
                    var r = switch (n) {
                        case 1 -> "um"
                    }
                    println(r)
                }
                """);
        Path outDir = tmp.resolve("out");
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertFalse(result.success(), "deveria falhar sem default");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("SEM032"),
                "deveria reportar SEM032: " + result.diagnostics().getDiagnostics());
    }

    // ── enum: exaustivo sem default (SEM031/SEM032) ─────────────────

    @Test
    void enumExhaustiveJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                enum Color { Red, Green, Blue }
                String cor(Color c) {
                    return switch (c) {
                        case Color.Red -> "vermelho"
                        case Color.Green -> "verde"
                        case Color.Blue -> "azul"
                    }
                }
                main() {
                    println(cor(Color.Red))
                    println(cor(Color.Green))
                    println(cor(Color.Blue))
                }
                """, "vermelho\nverde\nazul");
    }

    @Test
    void enumExhaustiveNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
                enum Color { Red, Green, Blue }
                String cor(Color c) {
                    return switch (c) {
                        case Color.Red -> "vermelho"
                        case Color.Green -> "verde"
                        case Color.Blue -> "azul"
                    }
                }
                main() {
                    println(cor(Color.Green))
                }
                """, "verde");
    }

    @Test
    void enumExhaustiveJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                enum Color { Red, Green, Blue }
                String cor(Color c) {
                    return switch (c) {
                        case Color.Red -> "vermelho"
                        case Color.Green -> "verde"
                        case Color.Blue -> "azul"
                    }
                }
                main() {
                    println(cor(Color.Blue))
                }
                """, "azul");
    }

    @Test
    void enumNonExhaustiveFailsToCompile(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, """
                enum Color { Red, Green, Blue }
                main() {
                    var c = Color.Red
                    var r = switch (c) {
                        case Color.Red -> "vermelho"
                    }
                    println(r)
                }
                """);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JVM);
        assertFalse(result.success(), "deveria falhar sem cobrir Green/Blue");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("SEM032"),
                "deveria reportar SEM032: " + result.diagnostics().getDiagnostics());
    }

    // ── harness ────────────────────────────────────────────────────

    private String runJvm(Path tempDir, String source, String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", outDir.toString() + ":kof-runtime/target/classes", "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code, output: " + output);
        assertEquals(expected, output, "JVM output");
        return output;
    }

    private String runNative(Path tempDir, String source, String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: " + output);
        assertEquals(expected, output, "Native output");
        return output;
    }

    private String runJs(Path tempDir, String source, String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        Path mjs = outDir.resolve("Default.mjs");
        Process p = new ProcessBuilder("node", mjs.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JS exit code, output: " + output);
        assertEquals(expected, output, "JS output");
        return output;
    }
}
