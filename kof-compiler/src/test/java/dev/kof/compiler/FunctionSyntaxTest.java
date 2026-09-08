package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


class FunctionSyntaxTest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
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

    private static final String ALL_FORMS = """
            String saudacao() {
                return "oi"
            }
            despedida(): String {
                return "tchau"
            }
            void fazIsso() {
                println("feito")
            }
            Bool positivo(Int x) = x > 0
            int dobro(int x) {
                return x * 2
            }
            main() {
                println(saudacao())
                println(despedida())
                fazIsso()
                println(positivo(3))
                println(dobro(21))
            }
            """;

    @Test
    void allFunctionFormsJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ALL_FORMS);
        runJvm(source, tempDir.resolve("out"), "oi\ntchau\nfeito\ntrue\n42");
    }

    @Test
    void allFunctionFormsNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ALL_FORMS);
        runNative(source, tempDir.resolve("out"), "oi\ntchau\nfeito\ntrue\n42");
    }

    private static final String CLASS_METHOD_FORMS = """
            class Calc {
                Int value
                void reset() {
                    value = 0
                }
                Int getValue() {
                    return value
                }
                Bool positivo(Int x) = x > 0
                String nome() {
                    return "calc"
                }
                emDobro(): Int {
                    return value * 2
                }
            }
            main() {
                var c = new Calc()
                c.reset()
                c.value = 10
                println(c.getValue())
                println(c.positivo(3))
                println(c.nome())
                println(c.emDobro())
            }
            """;

    @Test
    void classMethodFormsJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, CLASS_METHOD_FORMS);
        runJvm(source, tempDir.resolve("out"), "10\ntrue\ncalc\n20");
    }

    @Test
    void classMethodFormsNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, CLASS_METHOD_FORMS);
        runNative(source, tempDir.resolve("out"), "10\ntrue\ncalc\n20");
    }

    private void assertParse085(Path tempDir, String code) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, code);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "não deve compilar: " + code);
        assertTrue(result.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "PARSE085".equals(d.code())),
                "esperava PARSE085, veio: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void funKeywordIsRejected(@TempDir Path tempDir) throws IOException {
        assertParse085(tempDir, "fun main() {\n    println(\"x\")\n}\n");
    }

    @Test
    void fnKeywordIsRejected(@TempDir Path tempDir) throws IOException {
        assertParse085(tempDir, "fn main() {\n    println(\"x\")\n}\n");
    }

    @Test
    void funcKeywordIsRejected(@TempDir Path tempDir) throws IOException {
        assertParse085(tempDir, "func main() {\n    println(\"x\")\n}\n");
    }

    @Test
    void fnWithReturnTypeIsRejected(@TempDir Path tempDir) throws IOException {
        assertParse085(tempDir, "fn calc(): Int {\n    return 1\n}\nmain() {\n    println(calc())\n}\n");
    }

    @Test
    void fnAsFunctionNameIsReserved(@TempDir Path tempDir) throws IOException {
        // `fn`/`fun`/`func` são RESERVADAS (SG-001): nem como nome de função.
        assertParse085(tempDir, "fn() {\n    println(\"x\")\n}\nmain() {\n    fn()\n}\n");
    }

    @Test
    void funAsVariableNameIsReserved(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, "main() {\n    var fun = 1\n    println(fun)\n}\n");
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "var fun = 1 não deve compilar: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void fnAsParamNameIsReserved(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, "main(fn: Int) {\n    println(fn)\n}\n");
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "param fn não deve compilar: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void funInsideClassMemberIsReserved(@TempDir Path tempDir) throws IOException {
        assertParse085(tempDir, "class C {\n    fun foo() {\n        println(\"x\")\n    }\n}\nmain() {\n    C().foo()\n}\n");
    }
}