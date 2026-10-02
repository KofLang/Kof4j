package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suporte do E2E de switch-expression ({@code KofSwitchExprE2ETest}): os
 * runners JVM/Native/JS e os programas Kof (hoisted de inline para constantes).
 * Vive fora da classe de teste para mantê-la abaixo do limite de 500 linhas de
 * teste (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os
 * testes e o nome da classe seguem no {@code KofSwitchExprE2ETest} — zero drift
 * de citação.
 */
abstract class KofSwitchExprSupport {

    protected final CompilerDriver driver = new CompilerDriver();

    protected String runJvm(Path tempDir, String source, String expected) throws Exception {
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

    protected String runNative(Path tempDir, String source, String expected) throws Exception {
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

    protected String runJs(Path tempDir, String source, String expected) throws Exception {
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

    static final String SRC_INT_JVM = """
                main() {
                    var n = 2
                    var r = switch (n) {
                        case 1 -> "um"
                        case 2 -> "dois"
                        default -> "outro"
                    }
                    println(r)
                }
                """;

    static final String SRC_INT_NATIVE = """
                main() {
                    var n = 2
                    var r = switch (n) {
                        case 1 -> "um"
                        case 2 -> "dois"
                        default -> "outro"
                    }
                    println(r)
                }
                """;

    static final String SRC_INT_JS = """
                main() {
                    var n = 2
                    var r = switch (n) {
                        case 1 -> "um"
                        case 2 -> "dois"
                        default -> "outro"
                    }
                    println(r)
                }
                """;

    static final String SRC_INT_DEFAULT_JVM = """
                main() {
                    var n = 99
                    var r = switch (n) {
                        case 1 -> "um"
                        case 2 -> "dois"
                        default -> "outro"
                    }
                    println(r)
                }
                """;

    static final String SRC_STRING_JVM = """
                main() {
                    var op = "GET"
                    var r = switch (op) {
                        case "GET" -> "buscar"
                        case "POST" -> "criar"
                        default -> "desconhecido"
                    }
                    println(r)
                }
                """;

    static final String SRC_STRING_NATIVE = """
                main() {
                    var op = "GET"
                    var r = switch (op) {
                        case "GET" -> "buscar"
                        case "POST" -> "criar"
                        default -> "desconhecido"
                    }
                    println(r)
                }
                """;

    static final String SRC_STRING_JS = """
                main() {
                    var op = "GET"
                    var r = switch (op) {
                        case "GET" -> "buscar"
                        case "POST" -> "criar"
                        default -> "desconhecido"
                    }
                    println(r)
                }
                """;

    static final String SRC_PATTERN_SIMPLE_JVM = """
                main() {
                    var x: Object = "hello"
                    var r = switch (x) {
                        case String s -> "str:" + s
                        default -> "other"
                    }
                    println(r)
                }
                """;

    static final String SRC_PATTERN_SIMPLE_NATIVE = """
                main() {
                    var x: Object = "hello"
                    var r = switch (x) {
                        case String s -> "str:" + s
                        default -> "other"
                    }
                    println(r)
                }
                """;

    static final String SRC_PATTERN_SIMPLE_JS = """
                main() {
                    var x: Object = "hello"
                    var r = switch (x) {
                        case String s -> "str:" + s
                        default -> "other"
                    }
                    println(r)
                }
                """;

    static final String SRC_DESTRUCTURE_JVM = """
                record Point(Int x, Int y)
                main() {
                    var p = Point(3, 4)
                    var r = switch (p) {
                        case Point(var x, var y) -> "pt:" + x + "," + y
                        default -> "other"
                    }
                    println(r)
                }
                """;

    static final String SRC_DESTRUCTURE_NATIVE = """
                record Point(Int x, Int y)
                main() {
                    var p = Point(3, 4)
                    var r = switch (p) {
                        case Point(var x, var y) -> "pt:" + x + "," + y
                        default -> "other"
                    }
                    println(r)
                }
                """;

    static final String SRC_DESTRUCTURE_JS = """
                record Point(Int x, Int y)
                main() {
                    var p = Point(3, 4)
                    var r = switch (p) {
                        case Point(var x, var y) -> "pt:" + x + "," + y
                        default -> "other"
                    }
                    println(r)
                }
                """;

    static final String SRC_AS_RETURN_JVM = """
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
                """;

    static final String SRC_NESTED_JVM = """
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
                """;

    static final String SRC_NESTED_JS = """
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
                """;

    static final String SRC_STATEMENT_STILL_WORKS_JVM = """
                main() {
                    var op = "GET"
                    switch (op) {
                        case "GET":
                            println("buscar")
                        default:
                            println("x")
                    }
                }
                """;

    static final String SRC_MIXED_STATEMENT_AND_EXPR_JVM = """
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
                """;

    static final String SRC_MISSING_DEFAULT_FAILS_TO_COMPILE = """
                main() {
                    var n = 1
                    var r = switch (n) {
                        case 1 -> "um"
                    }
                    println(r)
                }
                """;

    static final String SRC_BLOCK_CASE_BODY_FAILS_WITH_DIAGNOSTIC = """
                enum E { A, B }
                main() {
                    var e = E.A
                    var x = switch (e) {
                        case A -> { println("a"); "aa" }
                        default -> "other"
                    }
                    println(x)
                }
                """;

    static final String SRC_ENUM_EXHAUSTIVE_JVM = """
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
                """;

    static final String SRC_ENUM_EXHAUSTIVE_VAR_SUBJECT_JVM = """
                enum Color { Red, Green, Blue }
                main() {
                    var c = Color.Blue
                    var r = switch (c) {
                        case Color.Red -> "vermelho"
                        case Color.Green -> "verde"
                        case Color.Blue -> "azul"
                    }
                    println(r)
                }
                """;

    static final String SRC_ENUM_EXHAUSTIVE_LITERAL_SUBJECT_JVM = """
                enum Color { Red, Green, Blue }
                main() {
                    var r = switch (Color.Green) {
                        case Color.Red -> "vermelho"
                        case Color.Green -> "verde"
                        case Color.Blue -> "azul"
                    }
                    println(r)
                }
                """;

    static final String SRC_ENUM_EXHAUSTIVE_VAR_SUBJECT_NATIVE = """
                enum Color { Red, Green, Blue }
                main() {
                    var c = Color.Red
                    var r = switch (c) {
                        case Color.Red -> "vermelho"
                        case Color.Green -> "verde"
                        case Color.Blue -> "azul"
                    }
                    println(r)
                }
                """;

    static final String SRC_ENUM_EXHAUSTIVE_INT_BODY_JVM = """
                enum Color { Red, Green, Blue }
                main() {
                    var c = Color.Blue
                    var r = switch (c) {
                        case Color.Red -> 1
                        case Color.Green -> 2
                        case Color.Blue -> 3
                    }
                    println(r)
                }
                """;

    static final String SRC_ENUM_EXHAUSTIVE_DOUBLE_BODY_JVM = """
                enum Color { Red, Green, Blue }
                main() {
                    var c = Color.Blue
                    var r = switch (c) {
                        case Color.Red -> 1.5
                        case Color.Green -> 2.5
                        case Color.Blue -> 3.5
                    }
                    println(r)
                }
                """;

    static final String SRC_ENUM_EXHAUSTIVE_LONG_BODY_JVM = """
                enum Color { Red, Green, Blue }
                main() {
                    var c = Color.Blue
                    var r = switch (c) {
                        case Color.Red -> 1L
                        case Color.Green -> 2L
                        case Color.Blue -> 3L
                    }
                    println(r)
                }
                """;

    static final String SRC_ENUM_EXHAUSTIVE_BOOL_BODY_JVM = """
                enum Color { Red, Green, Blue }
                main() {
                    var c = Color.Green
                    var r = switch (c) {
                        case Color.Red -> true
                        case Color.Green -> false
                        case Color.Blue -> true
                    }
                    println(r)
                }
                """;

    static final String SRC_ENUM_EXHAUSTIVE_INT_BODY_NATIVE = """
                enum Color { Red, Green, Blue }
                main() {
                    var c = Color.Green
                    var r = switch (c) {
                        case Color.Red -> 1
                        case Color.Green -> 2
                        case Color.Blue -> 3
                    }
                    println(r)
                }
                """;

    static final String SRC_ENUM_EXHAUSTIVE_NATIVE = """
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
                """;

    static final String SRC_ENUM_EXHAUSTIVE_JS = """
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
                """;

    static final String SRC_ENUM_NON_EXHAUSTIVE_FAILS_TO_COMPILE = """
                enum Color { Red, Green, Blue }
                main() {
                    var c = Color.Red
                    var r = switch (c) {
                        case Color.Red -> "vermelho"
                    }
                    println(r)
                }
                """;
}
